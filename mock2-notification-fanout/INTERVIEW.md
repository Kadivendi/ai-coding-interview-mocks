# Mock 2 — Notification Fan-out Worker (medium)

A Java notification fan-out pipeline, ~500 lines across 6 files. This one
mirrors real notifications-infra work: intake queue, a worker pool, retries
against a flaky downstream provider, dedup, and metrics.

| File | Owns |
|---|---|
| `Main.java` | Wiring and process lifetime |
| `Dispatcher.java` | `ExecutorService` worker pool, send-with-retry loop |
| `WorkQueue.java` | Intake queue + `Notification` model |
| `RetryPolicy.java` | Exponential backoff policy |
| `DedupStore.java` | Exactly-once guard |
| `Metrics.java` | Counters and timestamped send markers |

The code is concurrent, which means the bugs don't show up in single-threaded
mental execution. Read it like the on-call engineer who'll get paged.

## Rules

- **60 minutes total**, strict timer. Phases below.
- **Phase 1: absolutely no AI.**
- **Phases 2–4: Gemini allowed.**
- **Narrate aloud constantly.** Silence while code generates is a negative signal.
- **Do not open `ANSWERS.md`** until your 60-minute timer ends.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| 1. Analyze | 0:00–0:15 | No | Map the threading model: who creates threads, who owns their lifecycle, what happens on SIGTERM. List 3 clarifying questions (e.g. at-least-once or exactly-once? ordering guarantees?). Mark the concurrency smells. |
| 2. Collaborate | 0:15–0:30 | Yes | Prompt Gemini to add a dead-letter queue: after max attempts, route the notification to a DLQ instead of dropping it. Narrate your prompting strategy first. |
| 3. Validate | 0:30–0:45 | Yes | Verify the AI's DLQ design against the existing retry/dedup semantics. Then hunt the planted bugs yourself. Target: 5+ of 7. |
| 4. Optimize & test | 0:45–1:00 | Yes | Fix **one** bug properly and write a test proving it (e.g. a `RetryPolicy` test showing the delay is capped). Propose one throughput improvement. |

## Scoring checklist

**Phase 1 — Comprehension (no AI)**
- [ ] Traced every thread from creation to termination (or noted the absence)
- [ ] Stated the delivery semantics the code actually provides (not the ones it claims)
- [ ] Identified which shared state is/ isn't properly synchronized — from the code, not the comments
- [ ] Asked about backpressure behavior under a slow provider

**Phase 2 — AI fluency**
- [ ] Stated prompting strategy aloud before prompting
- [ ] Constrained Gemini with the existing semantics (dedup, retry budget)
- [ ] Caught something Gemini got wrong about the threading model

**Phase 3 — Validation**
- [ ] Found 5+ of the 7 planted bugs without AI assistance
- [ ] For each concurrency bug, named the interleaving that triggers it
- [ ] Did not trust any comment at face value

**Phase 4 — Rigor**
- [ ] One bug fixed with real, compiling code
- [ ] A test that fails before the fix and passes after
- [ ] Named what your fix does *not* cover

**L5 stretch:** for the retry path, sketch the backoff curve under the
incident-era config and explain what the downstream provider experiences.
Then explain your graceful-shutdown design: drain order, what happens to
in-flight sends, and how you'd verify it.
