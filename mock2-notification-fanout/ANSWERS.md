# Mock 2 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Score yourself first, then read.

Seven planted bugs — this mock has one more than the others. For each: where,
why it's subtle, the fix, what to narrate, and the L5-level commentary.

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

## Bug 2 — Backoff shift overflows to negative

**Location:** `RetryPolicy.java:32`
```java
return baseDelayMs << attempt;
```

**Why it's subtle:** `<<` reads as a fast `2^attempt`, and with the default
mental model of "a handful of retries" it never misbehaves. But the class
javadoc notes ops raised the ceiling to **100 attempts** — and
`1000 << 53` exceeds `Long.MAX_VALUE`, wrapping **negative**. `Thread.sleep`
with a negative argument throws `IllegalArgumentException`, breaking the
retry loop in a way no test with 3 attempts will ever show.

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

## Bug 3 — No jitter: coordinated retry stampede

**Location:** `RetryPolicy.java:41`
```java
Thread.sleep(delayForAttempt(attempt));
```

**Why it's subtle:** The comment frames the fixed schedule as a virtue —
"deterministic and easy to reason about in dashboards". But 16 workers that
failed together sleep the *identical* duration and wake in lockstep, hammering
a recovering provider with a synchronized wave. Deterministic retries are how
you DDoS yourself after someone else's outage.

**Ideal fix:** equal jitter (AWS-architecture-blog style):
```java
long delay = delayForAttempt(attempt);
long sleep = delay / 2 + ThreadLocalRandom.current().nextLong(delay / 2 + 1);
Thread.sleep(sleep);
```

**Narrate aloud:** "All 16 workers sleep the exact same duration, so they wake
and hit the provider in lockstep — a self-inflicted thundering herd aimed at
a provider that's already down."

**L5 commentary:** This is the highest-leverage one-line class of fix in
distributed systems. The senior version of this answer cites decorrelated
jitter and explains *why* the provider's recovery curve demands it: without
jitter, your retry policy and the provider's recovery are phase-locked.

---

## Bug 4 — HashMap shared across threads (the dedup that doesn't)

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

## Bug 5 — Unbounded queue, fictional backpressure

**Location:** `WorkQueue.java:37`
```java
private final BlockingQueue<Notification> queue = new LinkedBlockingQueue<>();
```

**Why it's subtle:** `LinkedBlockingQueue` *sounds* bounded-capable, the
comment describes blocking producers, and `put()` (which blocks *if* bounded)
is used. But the no-arg constructor means `Integer.MAX_VALUE` capacity —
`put` never blocks. Under a slow provider, producers enqueue forever until
the heap dies.

**Ideal fix:** bound it and choose the overflow policy explicitly:
```java
private final BlockingQueue<Notification> queue = new LinkedBlockingQueue<>(10_000);
```
…plus a decision: block (latency), drop-oldest with a metric (loss), or
spill to durable storage.

**Narrate aloud:** "The no-arg LinkedBlockingQueue is effectively unbounded,
so `put` never blocks — the backpressure comment is fiction. A slow
downstream means unbounded memory growth."

**L5 commentary:** Backpressure strategy is a *product* decision, not just
engineering: block (latency), drop (loss), persist (Kafka/SQS). For
notifications the usual answer is bounded + drop-oldest + DLQ + a metric —
and the metric is non-negotiable, because silent drops are silent data loss.

---

## Bug 6 — Shared SimpleDateFormat across worker threads

**Location:** `Metrics.java:20`
```java
String ts = TIMESTAMP.format(new Date());
```

**Why it's subtle:** `static final` suggests safe reuse, and the class looks
immutable. But `SimpleDateFormat` keeps mutable `Calendar` state internally —
concurrent `format()` calls corrupt each other, producing garbled timestamps
or `NumberFormatException`/`ArrayIndexOutOfBoundsException` under load. It
only manifests in production, never in a unit test.

**Ideal fix:** `DateTimeFormatter` — immutable and thread-safe:
```java
private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
String ts = TIMESTAMP.format(LocalDateTime.now());
```

**Narrate aloud:** "SimpleDateFormat is famously not thread-safe — its
internal calendar is mutated by `format()`. Under 16 workers you get garbled
timestamps or exceptions, and only under load."

**L5 commentary:** The cruelest part: this corrupts *observability* data —
the timestamps you'd use to debug everything else. Bugs in the telemetry path
are the most expensive because they blind you during the incident they'd help
explain. `static final` mutable objects deserve a second look, always.

---

## Bug 7 — Swallowed interrupt: workers can never stop

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
while `workerLoop` doesn't. Inconsistent interrupt discipline is the tell —
and contrast with Mock 3's `ConfigStore` poller, which does it right.
Uniform conventions beat local cleverness.

---

## Scoring

- 7/7 with interleavings named: exceptional.
- 5–6/7: solid pass.
- ≤4/7: drill concurrency reading — for each shared field, ask "who writes,
  who reads, under what lock" before moving on.
