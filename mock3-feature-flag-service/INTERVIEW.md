# Mock 3 — Feature-Flag Evaluation Service (hard · correctness-focused)

A Java feature-flag service, ~500 lines across 5 files. Unlike the first two
mocks, these bugs are **logic bugs, not crashes**: the code runs fine and
returns confident, wrong answers. This is the hardest skill the round tests —
spotting where the code disagrees with its own specification.

| File | Owns |
|---|---|
| `FlagEvaluator.java` | Rule evaluation: priority ordering, rollout bucketing; `Rule`/`Flag` models |
| `ConfigStore.java` | Live config, background polling publisher |
| `FlagApi.java` | Admin + evaluation API, override precedence |
| `FlagCache.java` | TTL cache for evaluated values |
| `AuditLog.java` | Append-only audit trail |

## Rules

- **60 minutes total**, strict timer. Phases below.
- **Phase A: absolutely no AI.**
- **Phases B–D: AI allowed.** In Phase B, finding the bugs is *your* job —
  do not prompt "find the bugs".
- **Narrate aloud constantly.**
- **Do not open `ANSWERS.md`** until your 60-minute timer ends.
- **Verification over completion.** Not finishing Phase D is explicitly
  *not* a fail.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| A. Read & map | 0:00–0:10 | No | Write down the evaluation model *from the docs*: rule precedence, rollout semantics, override chain, cache behavior, audit guarantees. Then read the code and mark every place it disagrees with the docs. |
| B. Fix | 0:10–0:25 | Assisted | Fix the **5 planted bugs** yourself — all logic bugs, so construct the concrete input that produces the wrong answer for each. Target: 4+ of 5. |
| C. Build | 0:25–0:45 | Yes | Implement **sticky gradual rollout + an audit-query API, with AI** (~80–120 lines, see `TASKS.md`). Give the model the precedence chain as a hard constraint. **Ask clarifying questions about the vague requirement before coding.** |
| D. Scale | 0:45–1:00 | Optional | Discussion: "traffic 10x's overnight — what breaks first and what do you change?" Name the first bottleneck, the fix, and its tradeoff. |

## Scoring checklist

**Phase A — Comprehension (no AI)**
- [ ] Wrote the documented semantics *before* reading the implementation
- [ ] Found at least one doc-vs-code disagreement from reading alone
- [ ] Could explain the rollout bucketing scheme's intended stability property
- [ ] Listed the full override precedence chain from the docs

**Phase B — Bug fixing**
- [ ] Found 4+ of the 5 planted bugs without AI finding them for you
- [ ] For each logic bug, constructed the concrete input that produces the wrong answer
- [ ] Distinguished "code is wrong" from "docs are wrong" — and said which

**Phase C — AI fluency (this is the graded skill)**
- [ ] Asked clarifying questions about the vague requirement *before* writing code
- [ ] Stated prompting strategy aloud before each prompt
- [ ] Gave the model the precedence chain and rollout stability as hard constraints
- [ ] **Rejected at least one AI suggestion with a stated reason**
- [ ] Compiled and ran a check of AI-generated code before trusting it

**Phase D — Scale thinking**
- [ ] Named what breaks *first* with a reason tied to the code
- [ ] Proposed a fix and named its tradeoff
- [ ] Left Phase D unfinished without guilt

**L5 stretch:** pick any bug and explain how you'd detect it in production
*without* reading the code — what metric, log, or experiment anomaly would
surface it? If you can't answer, that's the senior gap: detection design.
