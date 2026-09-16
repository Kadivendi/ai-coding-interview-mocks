# Mock 3 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Score yourself first, then read.

Six planted bugs — all logic bugs. The code compiles, runs, and returns
confident, wrong answers. For each: where, why it's subtle, the fix, what to
narrate, and the L5-level commentary.

---

## Bug 1 — Rules sorted lowest-priority-first

**Location:** `FlagEvaluator.java:99`
```java
ordered.sort(Comparator.comparingInt(Rule::priority));
```

**Why it's subtle:** `comparingInt(Rule::priority)` reads completely
naturally, and nothing at the call site hints at direction. The class javadoc
says "highest-priority-first", but the sort is ascending — so the
*lowest*-priority rule wins. In the `main` demo, rule 10 (`country=IN → true`)
beats rule 90 (`country=IN → false @ 50%`): every IN user gets `true`,
inverting the flag's intent. Any test with a single rule passes.

**Ideal fix:**
```java
ordered.sort(Comparator.comparingInt(Rule::priority).reversed());
```

**Narrate aloud:** "The docs say highest priority first, but the sort is
ascending — the lowest-priority rule wins. The demo flag literally inverts
its own intent for every IN user."

**L5 commentary:** When docs and code disagree, you have a *spec* bug with
production blast radius: every evaluation of every multi-rule flag is wrong.
The senior response isn't just the one-word fix — it's "we need an evaluation
dry-run/preview tool, because no human will eyeball priority numbers
correctly at 2 AM."

---

## Bug 2 — Rollout buckets on identity hashCode (users flip buckets)

**Location:** `FlagEvaluator.java:118`
```java
int bucket = Math.abs(System.identityHashCode(userId)) % 100;
```

**Why it's subtle:** "Hash the user id" sounds stable — and `String.hashCode()`
*is* stable, which is what your brain pattern-matches to. But
`System.identityHashCode` is the object's identity hash: a new `String`
holding `"u-123"` after a restart, a redeploy, or deserialization hashes
differently. Users flip rollout buckets on every deploy. (Bonus: `Math.abs`
of `Integer.MIN_VALUE` stays negative — the bucket math has a second,
smaller wart.)

**Ideal fix:** bucket on a stable digest of the id *bytes*:
```java
private boolean inRollout(String userId, int pct) {
    byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(userId.getBytes(StandardCharsets.UTF_8));
    int bucket = (digest[0] & 0xFF) % 100; // or full-int mod for uniformity
    return bucket < pct;
}
```

**Narrate aloud:** "identityHashCode is per-object, not per-value — the same
user id in a new JVM lands in a different bucket. Rollout membership must be
a pure function of the id bytes, or it isn't stable."

**L5 commentary:** Rollout stickiness is a *correctness property of
experimentation*. Flipping buckets mid-experiment corrupts A/B results —
treatment and control bleed into each other silently, and every downstream
decision built on that experiment is suspect. This is how you get phantom
experiment outcomes that nobody can reproduce.

---

## Bug 3 — Config published without volatile (stale reads forever)

**Location:** `ConfigStore.java:15`
```java
private Map<String, FlagEvaluator.Flag> flags = Map.of();
```

**Why it's subtle:** The javadoc is *technically correct* — reference
assignment is atomic on the JVM — which makes the conclusion ("readers need
no locking") persuasive. But atomicity ≠ visibility. Under the Java Memory
Model, without `volatile` (or synchronization), request threads are not
guaranteed to ever see the poller's update: they can serve stale flags
indefinitely after a config push.

**Ideal fix:**
```java
private volatile Map<String, FlagEvaluator.Flag> flags = Map.of();
```

**Narrate aloud:** "Assignment is atomic, yes — but the JMM doesn't guarantee
*visibility* without volatile. Reader threads can serve stale flags forever
after the poller publishes."

**L5 commentary:** Know the safe-publication menu: `volatile`, `final`,
synchronized, concurrent collections. And note the inconsistency that gives
it away: `updateDefault` *is* synchronized (so its write is visible) while
`publish` is not. Two publication paths with different visibility guarantees
for the same field — that's the tell.

---

## Bug 4 — Audit written before the decision commits (phantom audits)

**Location:** `FlagApi.java:28`
```java
audit.recordChange(flagName, value, actor);
store.updateDefault(flagName, value);
```

**Why it's subtle:** The javadoc frames audit-first as a *compliance
requirement* — "recorded even if the write fails" — which sounds
authoritative and security-conscious. But `updateDefault` throws for unknown
flags, so a typo'd flag name writes an audit record for a change that never
happened. The compliance trail becomes fiction.

**Ideal fix:** audit the effect, not the intent:
```java
store.updateDefault(flagName, value);
cache.invalidate(flagName);
audit.recordChange(flagName, value, actor);
```
(or audit in a `finally` with an outcome status).

**Narrate aloud:** "If `updateDefault` throws, the audit log claims a change
that never happened. An audit trail must record effects, not intentions —
phantom audits are worse than missing ones in a compliance context."

**L5 commentary:** Audit integrity: the entry should capture before/after and
outcome, not just the attempt. In regulated contexts, a log that confidently
records non-events destroys trust in the entire trail — you'd rather have a
gap you can explain than a lie you can't.

---

## Bug 5 — Cache TTL refresh stampede

**Location:** `FlagCache.java:48`
```java
boolean value = loader.get();
```

**Why it's subtle:** The comment is true — `ConcurrentHashMap` *is* safe
under contention — so the code feels reviewed. Memory-safety isn't the bug:
every thread that observes an expired entry runs the (potentially expensive)
loader simultaneously. On every TTL boundary, a hot flag triggers N
redundant evaluations.

**Ideal fix:** singleflight — one thread computes, the rest wait:
```java
private final ConcurrentHashMap<String, FutureTask<Boolean>> inFlight = new ConcurrentHashMap<>();
// computeIfAbsent a FutureTask per key; losers call .get() on the winner's future
```
(or async refresh / stale-while-revalidate / jittered TTLs).

**Narrate aloud:** "The map is thread-safe, but there's no suppression of
duplicate computation — N threads see the expired entry and all N run the
loader. That's a stampede on every TTL boundary."

**L5 commentary:** This is Mock 1's cache-aside thundering herd in different
clothes — recognizing the same bug across codebases is the senior signal.
Mitigation menu: singleflight, stale-while-revalidate, jittered TTLs so hot
keys don't expire in lockstep.

---

## Bug 6 — Environment override applied last, clobbering request overrides

**Location:** `FlagApi.java:50`
```java
value = Boolean.parseBoolean(envDefault);
```

**Why it's subtle:** The method reads top-to-bottom like a precedence chain,
and the comment declares environment "the most specific configuration
source". But the documented precedence is **request > env > global** — and
*order of application is the precedence*. Applying env last means env wins:
an explicit request override of `false` (your kill-switch!) is silently
clobbered by env `true`.

**Ideal fix:** apply in documented order:
```java
boolean value = evaluator.evaluate(flagName, attrs);   // global path
String envDefault = System.getenv("FLAG_" + flagName.toUpperCase());
if (envDefault != null) value = Boolean.parseBoolean(envDefault);  // env beats global
if (requestOverride != null) value = requestOverride;              // request beats all
return value;
```

**Narrate aloud:** "The documented precedence is request > env > global, but
the code applies environment last — so env silently overrides an explicit
request kill-switch. Order of application *is* the precedence."

**L5 commentary:** Config-precedence bugs are silent and total: the system
behaves exactly as coded, confidently wrong, and every layer looks reasonable
alone. Override chains need a test per precedence *pair* — that's the
durable fix, not just reordering lines.

---

## Scoring

- 6/6 with concrete triggering inputs: exceptional.
- 4–5/6: solid pass.
- ≤3/6: redo Phase 1 with the docs-first discipline. These bugs hide behind
  confident comments — the skill is reading the spec before the code.
