# Mock 3 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase A.

## Phase A — Read & map (0:00–0:10, NO AI)

1. **Before reading the implementation**, read only the class-level javadoc
   of each file and write down the documented evaluation model:
   - rule precedence (which rule wins when several match?),
   - what "priority" means numerically,
   - rollout bucketing and its stability promise,
   - the override precedence chain,
   - cache behavior on expiry,
   - what the audit log guarantees.
2. Now read the implementation. For each documented claim, mark **agree** or
   **contradicted** with file:line.
3. Write **3 clarifying questions** for the author. At least one must be
   unanswerable from the code (e.g. "what does the rollout need to be stable
   across?").
4. Mark the logic smells — places where the code is confident but the docs
   disagree. Don't fix yet.

Checkpoint: without re-reading, state the override precedence chain and the
rule that should win in the `main` demo. You'll verify against `ANSWERS.md`
later.

## Phase B — Fix (0:10–0:25, AI assisted)

There are **5 planted bugs**, all logic bugs — the code runs fine and returns
wrong answers.

1. Hunt them **yourself** — no "find the bugs" prompting. AI for mechanics
   only.
2. For each, construct the **concrete input** that produces the wrong output
   (use the `main` demo config as a starting point).
3. For each bug, decide: is the code wrong, or are the docs wrong? Say which,
   and why.
4. Fix all five with real, compiling code. Verify with `javac`.

## Phase C — Build (0:25–0:45, AI on)

Implement **with AI**: sticky gradual rollout plus an audit-query API.

Spec:
1. **Sticky gradual rollout:** when a rule's `rolloutPct` ramps (say 10% →
   50%), users in the first 10% must *stay* in — no flipping. Implement the
   bucketing so that increasing the percentage only *adds* users, never
   moves existing ones.
2. **Audit-query API:** `queryAudits(...)` supporting **"flexible filtering"**
   over the audit trail.
3. "Flexible filtering" is deliberately under-specified. **Before writing any
   code, ask clarifying questions** (out loud): filter by which fields —
   flag name? actor? time range? value? Combinations? Who calls this — a
   human in an admin UI, or an automated compliance job? What does "flexible"
   cost in API surface?

Rules for this phase:
- **Before prompting**, say aloud your strategy and give the model the
  precedence chain and rollout-stability requirement as hard constraints.
- Prompt narrowly: bucketing first, then the query API, then wiring.
- Review each diff against the override chain: does anything you added
  change precedence? Does the sticky rollout interact with the cache TTL?
- **Reject at least one AI suggestion on the record**, with a reason.
- Compile and run a check: ramp 10% → 50% and assert no user flips
  (~80–120 lines is the right size).

## Phase D — Scale (0:45–1:00, discussion)

"Traffic 10x's overnight. What breaks first, and what do you change?"

1. Name the **first** bottleneck — the 3 AM page. Tie it to a specific line.
2. Propose the fix and name its tradeoff out loud.
3. Bonus: the poller reloads the *entire* config on every tick. At 10x
   flags or 10x poll frequency, what breaks — and would you keep polling at
   all?

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
