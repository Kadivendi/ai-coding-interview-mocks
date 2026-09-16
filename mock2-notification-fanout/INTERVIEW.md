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
- **Phase A: absolutely no AI.**
- **Phases B–D: AI allowed.** In Phase B, finding the bugs is *your* job —
  do not prompt "find the bugs". Use AI for mechanical help only.
- **Narrate aloud constantly.** Silence while code generates is a negative signal.
- **Do not open `ANSWERS.md`** until your 60-minute timer ends.
- **Verification over completion.** Not finishing Phase D is explicitly
  *not* a fail.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| A. Read & map | 0:00–0:10 | No | Map the threading model: who creates threads, who owns their lifecycle, what happens on SIGTERM. List 3 clarifying questions (e.g. at-least-once or exactly-once? ordering guarantees?). Mark the concurrency smells. |
| B. Fix | 0:10–0:25 | Assisted | Fix the **5 planted bugs** yourself. Target: 4+ of 5. For each concurrency bug, name the interleaving that triggers it. |
| C. Build | 0:25–0:45 | Yes | Implement **per-tenant rate limiting in the dispatcher, with AI** (~80–120 lines, see `TASKS.md`). Narrate your prompting strategy before each prompt. **Ask clarifying questions about the vague requirement before coding.** |
| D. Scale | 0:45–1:00 | Optional | Discussion: "traffic 10x's overnight — what breaks first and what do you change?" Name the first bottleneck, the fix, and its tradeoff. |

## Scoring checklist

**Phase A — Comprehension (no AI)**
- [ ] Traced every thread from creation to termination (or noted the absence)
- [ ] Stated the delivery semantics the code actually provides (not the ones it claims)
- [ ] Identified which shared state is/isn't properly synchronized — from the code, not the comments
- [ ] Asked about backpressure behavior under a slow provider

**Phase B — Bug fixing**
- [ ] Found 4+ of the 5 planted bugs without AI finding them for you
- [ ] For each concurrency bug, named the interleaving that triggers it
- [ ] For the logic bug, gave the concrete input/config that triggers it
- [ ] Did not trust any comment at face value

**Phase C — AI fluency (this is the graded skill)**
- [ ] Asked clarifying questions about the vague requirement *before* writing code
- [ ] Stated prompting strategy aloud before each prompt
- [ ] Constrained the model with the existing semantics (dedup, retry budget, interrupt discipline)
- [ ] **Rejected at least one AI suggestion with a stated reason**
- [ ] Compiled and exercised AI-generated code before trusting it

**Phase D — Scale thinking**
- [ ] Named what breaks *first* with a reason tied to the code
- [ ] Proposed a fix and named its tradeoff (and who decides — it's a product call)
- [ ] Left Phase D unfinished without guilt

**L5 stretch:** for the retry path, sketch the backoff curve under the
incident-era config and explain what the downstream provider experiences.
Then explain your graceful-shutdown design: drain order, what happens to
in-flight sends, and how you'd verify it.
