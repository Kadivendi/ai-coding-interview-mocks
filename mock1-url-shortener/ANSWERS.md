# Mock 1 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Reading early trains exactly the habit the interview punishes: reaching for
> the answer instead of reasoning. Score yourself first, then read.

Five planted bugs for Phase B. For each: where, why it's subtle, the fix,
what to narrate, and the L5-level commentary. Phase C and D sketches follow.

---

## Bug 1 — Token bucket never refills at low rates

**Location:** `RateLimiter.java:54`
```java
long refill = (long) (elapsedSeconds * permitsPerSecond);
```

**Why it's subtle:** The cast looks like a deliberate design choice — "keep
the bucket denominated in whole tokens" — and the comment reinforces it. But
`lastRefillNanos` is updated on *every* call, so fractional permits are
discarded, not accumulated. At 0.5 permits/sec (the `main` demo config), any
call sooner than 2s after the last computes `(long)(<1.0)` = 0. Once the
initial burst of 10 drains, the key is rate-limited **forever**.

**Ideal fix:** keep a fractional accumulator; floor only when consuming:
```java
private double storedTokens; // was long
storedTokens = Math.min(maxBurst, storedTokens + elapsedSeconds * permitsPerSecond);
if (storedTokens >= 1) { storedTokens -= 1; return true; }
return false;
```

**Narrate aloud:** "The cast truncates sub-1.0 refills to zero, and since the
timestamp resets every call, those fractions are lost permanently — at half a
permit per second this bucket never refills after the burst."

**L5 commentary:** Token buckets must preserve fractional permits — this is
the standard formulation. Blast radius: every API key silently throttled to
zero after its burst; looks like a downstream outage in dashboards. Also note
the *ungraded* smell: `buckets` is a `ConcurrentHashMap` keyed by API key
with no eviction — unbounded cardinality is a memory bomb if keys are
attacker-controlled.

---

## Bug 2 — Short-code collisions silently overwrite links

**Location:** `UrlStore.java:39`
```java
links.put(code, link);
```

**Why it's subtle:** 62^6 ≈ 56.8B combinations, and the comment does the math
for you — it *feels* impossible. But the birthday paradox bites earlier than
intuition says at real scale, and `put` overwrites: one user's link is
silently hijacked by another's. No error, no log.

**Ideal fix:** check-and-retry under the same lock as the put:
```java
String code;
synchronized (this) {
    do { code = generateCode(); } while (links.containsKey(code));
    links.put(code, link);
}
```

**Narrate aloud:** "Random codes need a uniqueness check regardless of the
space size — `put` silently overwrites someone else's link on collision."

**L5 commentary:** Randomness is not a uniqueness strategy. At real scale:
pre-generate with a key-generation service, or enforce a DB unique constraint
as the backstop. This is a data-loss bug wearing a probability costume.

---

## Bug 3 — Click counter read-modify-write race

**Location:** `Analytics.java:30`
```java
link.setClicks(link.clicks() + 1);
```

**Why it's subtle:** The class javadoc *and* the inline comment both claim
atomicity, and single-threaded tests pass. But `clicks() + 1` is
getfield → add → setfield: three operations. Two threads read the same value,
one increment is lost. There is no GIL in Java — the comment's mental model
is borrowed from CPython and wrong here.

**Ideal fix:** `AtomicLong` (or `LongAdder`) inside `ShortLink`:
```java
private final AtomicLong clicks = new AtomicLong();
public long incrementClicks() { return clicks.incrementAndGet(); }
```

**Narrate aloud:** "This is three bytecodes — read, add, write — so two
threads can read the same value and one click is lost. The atomicity comment
is wrong; Java has no GIL."

**L5 commentary:** Note the inconsistency: the `events` list *is* correctly
synchronized two lines below. Inconsistent synchronization discipline within
one method is the tell. Blast radius: undercounted clicks → wrong analytics,
wrong billing if clicks are monetized. Prefer `LongAdder` over `AtomicLong`
under high contention.

---

## Bug 4 — Redirect validation checks shape, not scheme (no allowlist)

**Location:** `App.java:57`
```java
return new URI(target).getScheme() != null;
```

**Why it's subtle:** This looks like the *fixed* version of a naive check —
it uses `java.net.URI`, rejects relative URLs and bare domains, and reads
like a careful trust-boundary validation. But it validates *shape*, not
*scheme*: `javascript:alert(document.cookie)` parses fine with scheme
`"javascript"`, so it passes. There is no allowlist — any scheme with a colon
is accepted, and the redirect endpoint serves it.

**Ideal fix:** allowlist the scheme:
```java
static boolean isAllowedTarget(String target) {
    try {
        String scheme = new URI(target).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (URISyntaxException | NullPointerException e) {
        return false;
    }
}
```

**Narrate aloud:** "URI parsing rejects relative URLs, but the scheme is never
allowlisted — `javascript:…` has a perfectly valid scheme, so the redirect
becomes an XSS vector. Parsing isn't validation."

**L5 commentary:** Allowlists over denylists, and validate at the trust
boundary — which means at *creation* time, not just redirect time, since
stored targets may predate any fix (defense in depth). This is the one bug
here with direct security impact; in the real round, finding the security bug
unprompted is a strong positive signal.

---

## Bug 5 — The reaper that doesn't exist (unbounded growth)

**Location:** `UrlStore.java:82` (javadoc: "Invoked periodically by the
background reaper") and `App.java:81` (`// NOTE: the reaper thread is started
by the deployment wrapper, not here.`)

**Why it's subtle:** Each file looks complete in isolation — `purgeExpired()`
exists, its docs describe the scheduling, and `main` explains where the
thread lives. Nothing, anywhere, actually starts it. Code review passes
because reviewers read files, not systems. Expired links accumulate forever:
a slow memory leak.

**Ideal fix:** actually schedule it in `App.main`:
```java
ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor();
reaper.scheduleAtFixedRate(store::purgeExpired, 1, 1, TimeUnit.HOURS);
```

**Narrate aloud:** "The reaper is referenced in two comments but never
instantiated anywhere — expired links accumulate forever. This is a slow
memory leak wearing documentation."

**L5 commentary:** Behavior that exists only in comments is an ops hazard —
this is how "we thought the cleanup was running" incidents happen. The senior
move: make the reaper's existence observable (a metric for purged count, a
log line at startup). Also worth asking: is an in-memory `HashMap` the right
store at all, versus Redis with native TTL?

---

## Phase C — sketch (expiring links + click-stats endpoint)

**Approach:** a `LinkStats` DTO (`code`, `totalClicks`, `recentEvents`), an
`Analytics.clickStats(code, limit, since)` query over the existing event log,
and a `GET /stats/{code}` handler in `App` reusing the not-found/expiry
checks from `redirect`. Expiry enforcement: `redirect` already checks
`isExpired()` — the real work is deciding 404 vs 410 and applying the same
check in the stats path.

**Key trade-offs:** 410 (Gone) is semantically right for expired links but
leaks less-friendly caching behavior; 404 is simpler and what most
shorteners do. Bounding the event log (currently unbounded `ArrayList`) —
the stats feature makes the memory growth *worse*, so a cap or rollup is now
required, not optional.

**Clarifying questions a strong candidate asks about "recent":**
1. "Recent = what window — last N events, or last 24 hours? Who consumes
   this endpoint, and what decision does it drive?"
2. "Do you need referrer breakdown, or just counts and timestamps?"
3. "Should stats include expired links, or 404 like the redirect path?"

**What good looks like:** asked at least two of the above *before* coding;
narrow prompts (DTO first, then query, then wiring); rejected an AI
suggestion (e.g. the model exposing the raw mutable event list, or inventing
its own window without asking); compiled and ran one stats call.

---

## Phase D — sketch ("traffic 10x's overnight")

**What breaks first:** the cache-aside thundering herd at
`UrlStore.java:53`. On a hot key's TTL boundary (or a cache cold start after
a deploy), every request thread pays the 50ms `slowLoad` and all of them
repopulate the cache — backend QPS multiplies by the worker count exactly
when the system is hottest. At 10x traffic this is the 3 AM page, ahead of
the analytics race (silent undercounting, no page) and the in-memory store
(slow leak, not a cliff).

**The fix:** singleflight per key — one thread loads, the rest wait on its
result (`FutureTask` in a `ConcurrentHashMap`, or `computeIfAbsent` with a
future). Alternatives and their trade-offs: stale-while-revalidate (serves
slightly stale links, needs a background refresh), probabilistic early
refresh (extra load always), jittered TTLs (spreads expiry, doesn't remove
the herd).

**L5 commentary:** The senior answer names the *detection* first: cache hit
ratio dropping in lockstep with a `slowLoad` p99 spike and backend QPS
multiplying is the signature. Then the mitigation menu, then the
organizational point — this is the same bug as the flag-service cache
stampede in different clothes. Recognizing the pattern across codebases is
the senior signal. Bonus: at 10x, the "reaper that doesn't exist" (Bug 5)
stops being a slow leak and starts being the second page.

---

## Scoring

- 5/5 with triggers for each: exceptional.
- 4/5: solid pass — the miss tells you what to drill.
- ≤3/5: redo Phase A slower. The skill being tested is *reading*, not fixing.
- Phase C is graded on process (questions → narrow prompts → rejection →
  verification), not on finishing. Phase D unfinished is not a fail.
