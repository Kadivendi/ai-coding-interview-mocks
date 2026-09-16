# Mock 3 — Answers

> **STOP. Do not read this file until your 60-minute timer has ended.**
> Score yourself first, then read.

Five planted bugs for Phase B — all logic bugs. The code compiles, runs, and
returns confident, wrong answers. For each: where, why it's subtle, the fix,
what to narrate, and the L5-level commentary. Phase C and D sketches follow.

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

## Bug 5 — Environment override applied last, clobbering request overrides

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

## Phase C — sketch (sticky gradual rollout + audit-query API)

**Approach:** replace the bucket function with a stable digest of the user-id
bytes (this is also the natural way to fix Bug 2 — Phase C forces the
confrontation if you missed it in Phase B). Stickiness across ramps: with
`bucket = digest % 100`, a user is in the rollout iff `bucket < pct` — as
`pct` grows, membership only grows; nobody flips. That's the whole trick, and
saying *why* it holds is the interview signal. For the query API: a
`queryAudits` taking an immutable filter object (flag, actor, since, until)
returning a snapshot list — and the discipline to *not* build a query DSL.

**Key trade-offs:** digest choice (SHA-256 is overkill per evaluation —
`String.hashCode` is actually stable across JVMs and far cheaper; the bug was
*identity* hash, not `hashCode`. A strong candidate says this out loud).
Cache interaction: the sticky rollout changes evaluation results, so cached
entries keyed on the old bucketing must be invalidated on rollout change —
otherwise "sticky" is a lie for `ttlMs`. The audit trail is in-memory and
unbounded: a query API makes the growth *visible* instead of fixing it.

**Clarifying questions a strong candidate asks about "flexible filtering":**
1. "Flexible = which fields — flag, actor, time range, value? And
   combinations, or single-field filters?"
2. "Who calls this — a human in an admin UI paginating, or a compliance job
   sweeping everything? That decides pagination vs. full snapshots."
3. "Does the query need to see uncommitted/attempted changes, or only
   committed ones?" (Ties back to Bug 4 — the candidate who asks this is
   connecting the phases.)

**What good looks like:** asked at least two of the above *before* coding;
gave the model the precedence chain as a hard constraint; caught the model
violating precedence or proposing a non-stable bucket; rejected a suggestion
on the record; ran the 10% → 50% ramp check showing zero flips.

---

## Phase D — sketch ("traffic 10x's overnight")

**What breaks first:** the cache TTL refresh stampede at
`FlagCache.java:48`. `ConcurrentHashMap` is thread-safe, but nothing
suppresses duplicate computation: every thread that observes an expired
entry runs the loader simultaneously. On every TTL boundary, a hot flag
triggers N redundant evaluations — at 10x traffic with a 5s TTL, that's a
sawtooth of load spikes, each one a mini-thundering-herd.

**Second:** the poller. It reloads the *entire* config on every tick and
replaces the map wholesale — at 10x flags, each poll is 10x the garbage and
10x the parse cost, and every poller wake-up briefly contends on the same
field readers are hitting.

**The fixes and their trade-offs:** singleflight per key (one thread
computes, the rest wait on its future); stale-while-revalidate (serve the
stale value, refresh async — trades freshness for smoothness); jittered TTLs
(spreads expiry, doesn't remove the herd). For the poller: switch from poll
to push (or long-poll / watch), or at minimum diff-and-patch instead of
wholesale replace.

**L5 commentary:** The senior answer starts with detection: evaluation p99
latency spiking in lockstep with TTL boundaries is the stampede's signature
— you'd see it in the latency histogram before any error rate moves. Then
the mitigation menu, then the pattern recognition: this is Mock 1's
cache-aside herd in different clothes. And the experimentation angle: at 10x
traffic with Bug 2 unfixed, every redeploy reshuffles experiment buckets —
your 10x traffic is generating 10x the *corrupted* experiment data.

---

## Scoring

- 5/5 with concrete triggering inputs: exceptional.
- 4/5: solid pass.
- ≤3/5: redo Phase A with the docs-first discipline. These bugs hide behind
  confident comments — the skill is reading the spec before the code.
- Phase C is graded on process (questions → constraints → rejection →
  verification), not on finishing. Phase D unfinished is not a fail.
