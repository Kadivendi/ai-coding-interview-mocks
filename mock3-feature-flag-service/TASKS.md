# Mock 3 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase 1.
This mock gives Phase 1 twenty minutes — slow reading is the skill.

## Phase 1 — Analyze (0:00–0:20, NO AI)

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

## Phase 2 — Collaborate (0:20–0:32, Gemini on)

Task: add a kill-switch API — `setKillSwitch(flagName, actor)` — that forces
a flag to `false` for all requests and bypasses the cache.

1. **Before prompting**, say aloud your strategy and give Gemini the
   precedence chain as a hard constraint.
2. Prompt narrowly: storage of the kill-switch state, then the evaluation
   path, then cache interaction.
3. Review: does the kill-switch actually take precedence over *everything*,
   including environment overrides? Where does it sit in the chain, and is
   that where it belongs?

## Phase 3 — Validate (0:32–0:47, Gemini on)

1. Verify every AI-generated line against the real precedence chain, aloud.
2. Hunt the planted bugs **yourself**. There are 6, all logic bugs — the code
   runs fine and returns wrong answers. For each, construct the **concrete
   input** that produces the wrong output (use the `main` demo config as a
   starting point).
3. For each bug, decide: is the code wrong, or are the docs wrong? Say which,
   and why.

## Phase 4 — Optimize & test (0:47–1:00, Gemini on)

1. Pick **one** bug and fix it with real, compiling code. Verify with
   `javac` in this directory. Suggested: the rollout bucketing.
2. Write a driver that **fails before the fix and passes after**. Suggested:
   create two distinct-but-equal `String` objects for the same user id
   (`new String("u-123")` twice) and assert they land in the same bucket —
   simulating a restart.
3. Out loud: name the production blast radius of the bug you fixed, and one
   metric or alert that would have caught it without reading the code.

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
