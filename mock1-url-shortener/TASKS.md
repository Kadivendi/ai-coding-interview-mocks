# Mock 1 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase A.

## Phase A — Read & map (0:00–0:10, NO AI)

1. Read all five files. On paper (or a blank doc), draw the component map:
   which class calls which, and what data flows between them on a
   `POST /links` and a `GET /{code}`.
2. Write down the public API surface: every public method, its inputs, and
   what it promises.
3. Write **3 clarifying questions** you would ask the original author before
   changing anything. At least one must be something the code cannot answer.
4. Mark 2–3 places that "smell". One line each: file, line, and what feels
   off. Do not investigate further — just mark.

Checkpoint: close the files. Can you describe the redirect flow from memory?
If not, your map isn't done.

## Phase B — Fix (0:10–0:25, AI assisted)

There are **5 planted bugs**, obvious-to-moderate: an off-by-smell in the
rate limiter, a uniqueness assumption, a concurrency slip, a trust-boundary
gap, and something the comments promise but the code never does.

1. Hunt them **yourself**. You may use AI for mechanical help (syntax, JDK
   API lookup) but do not prompt it to find bugs — the finding is the test.
2. For each: file:line, what's wrong, and a concrete input or interleaving
   that triggers it.
3. Fix all five with real, compiling code. Verify with `javac` in this
   directory. (In the real round, fixing everything isn't required — but this
   mock is the warm-up, so finish it.)

## Phase C — Build (0:25–0:45, AI on)

Implement **with AI**: expiring-link enforcement plus a click-stats endpoint.

Spec:
1. `GET /{code}` on an expired link must stop redirecting (decide: 404 or
   410 — and be ready to defend the choice).
2. New endpoint `GET /stats/{code}` returning, for a link: total clicks and
   **recent click activity**.
3. "Recent click activity" is deliberately under-specified. **Before writing
   any code, ask clarifying questions** (out loud): recent = what window?
   Last N events? Include referrer breakdown? What does the caller actually
   need this for?

Rules for this phase:
- **Before prompting**, say aloud your plan: how many prompts, what each
  covers, what context the model needs.
- Prompt narrowly — one task per prompt.
- After each response, read the diff like a reviewer: expiry handling, cache
  interaction, thread-safety consistent with the class.
- **Reject at least one AI suggestion on the record**, with a reason.
- Compile and smoke-test the result before the timer ends (~80–120 lines
  total is the right size).

## Phase D — Scale (0:45–1:00, discussion)

"Traffic 10x's overnight. What breaks first, and what do you change?"

1. Name the **first** bottleneck — the one that pages you at 3 AM, not the
   fifth. Tie it to a specific line or structure in the code.
2. Propose the fix and name its tradeoff out loud.
3. Bonus: what metric or alert would have warned you *before* the 10x?

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
