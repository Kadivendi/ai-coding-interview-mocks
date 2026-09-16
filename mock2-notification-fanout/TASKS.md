# Mock 2 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase 1.

## Phase 1 — Analyze (0:00–0:15, NO AI)

1. Read all six files. Draw the threading model on paper:
   - Which threads exist, who creates them, and what each one loops on.
   - Every piece of state shared between threads, and how (or whether) it
     is synchronized.
   - What happens, step by step, when the process receives SIGTERM.
2. Write down the delivery semantics the code **actually** provides
   (at-least-once? at-most-once? exactly-once?) — from the code, not the
   comments.
3. Write **3 clarifying questions** for the author. Good candidates: ordering
   guarantees, what "maxAttempts" counts, expected provider latency that the
   queue sizing assumes.
4. Mark the concurrency smells: file, line, one line on what's off. Don't fix.

Checkpoint: can you explain, without re-reading, why a worker might never
stop? If not, re-read `Dispatcher` before moving on.

## Phase 2 — Collaborate (0:15–0:30, Gemini on)

Task: add a dead-letter queue. After a notification exhausts its attempts,
route it to a `DeadLetterQueue` (in-memory list is fine) instead of silently
dropping it, and record a metric.

1. **Before prompting**, say aloud your prompting strategy and the semantic
   constraints Gemini must respect (dedup: is a DLQ'd id "sent"? retry
   budget: who counts attempts?).
2. Prompt narrowly: first the `DeadLetterQueue` class, then the wiring into
   `sendWithRetry`, then the metric.
3. Review each diff as the on-call owner: thread-safety, interrupt behavior,
   and whether the DLQ path can itself lose data.

## Phase 3 — Validate (0:30–0:45, Gemini on)

1. Verify every AI-generated line against the real code, aloud.
2. Hunt the planted bugs **yourself**. There are 7. For each concurrency bug,
   name the **interleaving** that triggers it — "two threads do X while a
   third does Y". For each logic bug, give the concrete input.
3. Verify any AI-suggested fix independently before accepting it.

## Phase 4 — Optimize & test (0:45–1:00, Gemini on)

1. Pick **one** bug and fix it with real, compiling code. Verify with
   `javac` in this directory. Suggested: cap the backoff with jitter in
   `RetryPolicy`.
2. Write a driver that **fails before the fix and passes after**. Suggested:
   call `delayForAttempt` for attempts 0–100 and assert every value is
   within `[0, MAX]`.
3. Out loud: propose one throughput improvement (not a bug fix) and name its
   tradeoff — e.g. batching sends vs. per-notification latency.

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
