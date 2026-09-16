# Mock 2 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Score yourself first, then read.

Five planted bugs for Phase B — a lifecycle bug, an interrupt bug, a
shared-state bug, an off-by-one, and a numeric bug that only bites under the
incident-era config. For each: where, why it's subtle, the fix, what to
narrate, and the L5-level commentary. Phase C and D sketches follow.

---

## Bug 1 — ExecutorService never shut down (thread leak)

**Location:** `Dispatcher.java:19`
```java
private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
```

**Why it's subtle:** The comment directly above claims "pool threads are
daemons, so the JVM exits cleanly" — and `newFixedThreadPool` *sounds*
managed. But the default thread factory creates **non-daemon** threads, and
nothing anywhere calls `shutdown()`. On SIGTERM the JVM hangs with 16 idle
workers; the container eventually `kill -9`s it, losing in-flight
notifications.

**Ideal fix:** own the lifecycle explicitly:
```java
public void stop() {
    running = false;
    pool.shutdown();
    try {
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) pool.shutdownNow();
    } catch (InterruptedException e) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
// + Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
```

**Narrate aloud:** "The comment says daemon threads, but the default factory
makes non-daemon threads — and nothing calls shutdown, so this process can
never exit cleanly."

**L5 commentary:** Lifecycle ownership is the real question: who owns the
pool's lifecycle? Production answer: graceful drain — stop taking work, await
in-flight sends, then exit — tied to a health check. Also note `running` is
`volatile` but never cleared: a dead flag performing safety theater.

---

## Bug 2 — Swallowed interrupt: workers can never stop

**Location:** `Dispatcher.java:41–42`
```java
} catch (InterruptedException e) {
    // Transient interrupt while polling; keep serving the queue.
}
```

**Why it's subtle:** Catch-and-continue reads as resilience — "don't let a
transient interrupt kill the worker." But swallowing the interrupt clears the
thread's interrupt status without restoring it, so `shutdownNow()` and
container SIGTERM handling can never stop a worker blocked in `take()`. With
Bug 1, shutdown is doubly impossible.

**Ideal fix:** restore and exit:
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // restore the status
    return;                             // and stop the worker
}
```

**Narrate aloud:** "Catching InterruptedException and looping swallows the
shutdown signal — the worker can never be stopped. You must re-interrupt or
return."

**L5 commentary:** Note the inconsistency *within the same class*:
`sendWithRetry` handles interruption correctly (re-interrupts and returns)
while `workerLoop` doesn't. Inconsistent interrupt discipline is the tell.
Uniform conventions beat local cleverness.

---

## Bug 3 — HashMap shared across threads (the dedup that doesn't)

**Location:** `DedupStore.java:23`
```java
return seen.containsKey(id);
```

**Why it's subtle:** `markSent` *is* synchronized, and the comment
rationalizes the unlocked read ("reads don't mutate"). Single-threaded tests
pass. But `HashMap` is unsafe for *any* concurrent read/write mix: a `put`
that triggers a resize while another thread reads can loop forever or corrupt
the table. Worst case, the failure mode is **duplicate sends** — precisely the
invariant this class exists to protect.

**Ideal fix:** `private final Map<String, Long> seen = new ConcurrentHashMap<>();`
(or synchronize the read — but CHM is the idiomatic answer).

**Narrate aloud:** "Synchronizing only the writer doesn't help — HashMap
isn't safe for concurrent read/write at all. A resize during a read can spin
forever, and the failure mode is duplicate sends."

**L5 commentary:** Concurrency bugs that defeat a component's *raison d'être*
are the most expensive kind. The pattern to internalize: when the comment
explains why the lock *isn't* needed, that's exactly where to look hardest.

---

## Bug 4 — Off-by-one: the retry budget allows one attempt too many

**Location:** `RetryPolicy.java:30`
```java
return attempt <= maxAttempts;
```

**Why it's subtle:** The javadoc does the arithmetic for you — "attempts are
0-based, so the valid attempt numbers run 0..maxAttempts inclusive" — and it
*sounds* right. But the dispatcher's loop treats `maxAttempts` as the *count*
of retries: with `maxAttempts = 100`, attempts 0..100 is **101 tries**, one
more than the configured budget. The comment makes the fencepost error look
like a deliberate spec.

**Trigger:** `new RetryPolicy(1000, 100)` (the `Main` demo config) —
`shouldRetry(100)` returns true, so a permanently-failing notification gets
101 attempts instead of 100.

**Ideal fix:**
```java
public boolean shouldRetry(int attempt) {
    return attempt < maxAttempts; // 0-based: exactly maxAttempts tries
}
```

**Narrate aloud:** "Zero-based attempt numbering with `<=` gives maxAttempts
+ 1 tries — the comment's 'inclusive' framing is the fencepost error wearing
a spec costume."

**L5 commentary:** Off-by-ones in retry budgets are how you exceed downstream
rate limits and SLO budgets by "just one more" per notification — at scale,
that's a multiplier on provider load. Retry budgets deserve a unit test with
the exact boundary values, not just "retries a few times".

---

## Bug 5 — Backoff shift overflows to negative

**Location:** `RetryPolicy.java:35`
```java
return baseDelayMs << attempt;
```

**Why it's subtle:** `<<` reads as a fast `2^attempt`, and with the default
mental model of "a handful of retries" it never misbehaves. But the class
javadoc notes ops raised the ceiling to **100 attempts** — and
`1000 << 53` exceeds `Long.MAX_VALUE`, wrapping **negative**. `Thread.sleep`
with a negative argument throws `IllegalArgumentException`, breaking the
retry loop in a way no test with 3 attempts will ever show. (Bug 4's extra
attempt makes this reachable one step sooner.)

**Ideal fix:** saturating, capped backoff:
```java
private static final long MAX_DELAY_MS = 60_000;
public long delayForAttempt(int attempt) {
    long delay = baseDelayMs;
    for (int i = 0; i < attempt && delay < MAX_DELAY_MS; i++) {
        delay = Math.min(MAX_DELAY_MS, delay * 2);
    }
    return delay;
}
```

**Narrate aloud:** "Left shift on a long wraps at 63 bits — past attempt ~53
this goes negative and `sleep` throws. The incident-era config of 100
attempts makes this reachable, not theoretical."

**L5 commentary:** Every exponential needs a cap, and usually a *deadline*
too: bounding attempts without bounding total elapsed time still lets one
notification occupy a worker for hours. Ask: what's the worst-case time a
single notification can hold a worker?

---

## Phase C — sketch (per-tenant rate limiting)

**Approach:** add `tenantId` to `Notification`; a `TenantLimiter` holding a
per-tenant token bucket (`ConcurrentHashMap<String, Bucket>`, buckets created
on demand); in `workerLoop`, acquire the tenant's permit before
`sendWithRetry` — or better, gate at dequeue time so an over-quota tenant
doesn't starve others' workers. ~80–120 lines.

**Key trade-offs:** where the wait happens matters. Blocking the worker on an
over-quota tenant couples tenants through the shared pool (one hot tenant
parks all 16 workers) — the senior design is a separate slow lane or
defer-and-requeue with backoff. Permit accounting vs. the retry budget: does
time spent waiting consume retry attempts? The dedup question: is a
deprioritized-but-unsent id "sent"? (No — but say it out loud.)

**Clarifying questions a strong candidate asks about "deprioritized":**
1. "Deprioritized means what, operationally — delayed, dropped, DLQ'd, or a
   separate slow lane? And is dropping *ever* acceptable for notifications?"
2. "Are tenants known upfront or dynamic — can anyone show up with a new
   tenant id?" (Unbounded tenant cardinality → the bucket map needs eviction,
   same lesson as Mock 1's API-key map.)
3. "Is the quota about downstream protection or fairness — i.e., do we shed
   load or just smooth it?"

**What good looks like:** asked at least two of the above *before* coding;
gave the model the dedup/retry/interrupt constraints as hard requirements;
rejected an AI suggestion (e.g. the model blocking the worker thread
indefinitely, or using `Thread.sleep` instead of a proper bucket); compiled
and ran a two-tenant scenario showing isolation.

---

## Phase D — sketch ("traffic 10x's overnight")

**What breaks first:** the unbounded queue at `WorkQueue.java:37`. The
no-arg `LinkedBlockingQueue` is effectively `Integer.MAX_VALUE` capacity, so
`put` never blocks despite the backpressure comment — under a slow provider
at 10x intake, producers enqueue forever until the heap dies. This is the 3
AM page: OOM, not gradual degradation.

**Second:** the retry policy has no jitter. At 10x with a flaky provider,
every failed notification across all workers sleeps the *identical* backoff
and wakes in lockstep — a synchronized retry wave hammering a recovering
provider. Your retry policy and the provider's recovery become phase-locked:
a self-inflicted DDoS after someone else's outage.

**The fixes and their trade-offs:** bound the queue (`new
LinkedBlockingQueue<>(10_000)`) and choose the overflow policy explicitly —
block (adds latency, pushes backpressure to callers), drop-oldest with a
metric (loss, but bounded), or spill to durable storage (Kafka/SQS, the real
answer for notifications). Backpressure strategy is a *product* decision:
for notifications the usual answer is bounded + drop-oldest + DLQ + a
non-negotiable metric, because silent drops are silent data loss. Add equal
jitter to the backoff (half fixed, half random).

**L5 commentary:** The senior answer sequences the failures — OOM first
(cliff), retry storm second (amplifier), worker pool sizing third (16 fixed
workers at 10x = queueing delay even when healthy). Then the detection
story: queue depth and heap growth rate are the leading indicators; by the
time downstream latency spikes, you're already paging. And the organizational
point: the "backpressure" comment described a design that was never built —
comments describing nonexistent behavior are how capacity planning goes
wrong.

---

## Scoring

- 5/5 with interleavings/inputs for each: exceptional.
- 4/5: solid pass — the miss tells you what to drill.
- ≤3/5: drill concurrency reading — for each shared field, ask "who writes,
  who reads, under what lock" before moving on.
- Phase C is graded on process (questions → constraints → rejection →
  verification), not on finishing. Phase D unfinished is not a fail.
