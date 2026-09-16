# Mock 1 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Reading early trains exactly the habit the interview punishes: reaching for
> the answer instead of reasoning. Score yourself first, then read.

Six planted bugs. For each: where, why it's subtle, the fix, what to narrate,
and the L5-level commentary.

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

## Bug 2 — Cache-aside thundering herd on miss

**Location:** `UrlStore.java:53`
```java
Models.ShortLink link = slowLoad(code);
```

**Why it's subtle:** The cache-aside pattern looks textbook, and the 50ms
`slowLoad` is invisible in unit tests. But nothing serializes concurrent
misses: 100 threads requesting an expired hot key each pay the 50ms load and
each write the cache. DB QPS multiplies by the thread count exactly when the
system is hottest.

**Ideal fix:** singleflight per key:
```java
private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();
// in get(): synchronized (keyLocks.computeIfAbsent(code, k -> new Object())) { ... double-checked load ... }
```
(or a `FutureTask`-based singleflight so only one thread loads).

**Narrate aloud:** "On a miss every thread pays the slow load and they all
repopulate the cache — a hot key expiring under load multiplies backend QPS
by the worker count."

**L5 commentary:** Know the mitigation menu: singleflight, probabilistic
early refresh, stale-while-revalidate. This is the same bug as Mock 3's cache
stampede in different clothes — recognizing the pattern across codebases is
the senior signal.

---

## Bug 3 — Short-code collisions silently overwrite links

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

## Bug 4 — Click counter read-modify-write race

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

## Bug 5 — Open redirect via unvalidated scheme

**Location:** `App.java:50`
```java
return target != null && target.contains("://");
```

**Why it's subtle:** It blocks the obvious bad input (relative URLs like
`example.com`) and the comment claims equivalence with full parsing. But
`javascript://x%0aalert(1)` contains `"://"` — the check validates *shape*,
not *scheme*. The redirect endpoint then serves attacker-controlled
JavaScript: a stored-XSS vector.

**Ideal fix:** allowlist the scheme:
```java
static boolean isAllowedTarget(String target) {
    try {
        String scheme = new URI(target).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (URISyntaxException e) {
        return false;
    }
}
```

**Narrate aloud:** "The scheme is never validated — `javascript://…` passes
the check, so the redirect becomes an XSS vector. Shape checks aren't scheme
checks."

**L5 commentary:** Allowlists over denylists, and validate at the trust
boundary — which means at *creation* time, not just redirect time, since
stored targets may predate any fix (defense in depth). This is the one bug
here with direct security impact; in the real round, finding the security bug
unprompted is a strong positive signal.

---

## Bug 6 — The reaper that doesn't exist (unbounded growth)

**Location:** `UrlStore.java:82` (javadoc: "Invoked periodically by the
background reaper") and `App.java:71` (`// NOTE: the reaper thread is started
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

## Scoring

- 6/6 with interleavings/inputs for each: exceptional.
- 4–5/6: solid pass — the misses tell you what to drill.
- ≤3/6: redo Phase 1 slower. The skill being tested is *reading*, not fixing.
