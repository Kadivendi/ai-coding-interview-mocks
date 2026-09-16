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
- **Phase 1: absolutely no AI.**
- **Phases 2–4: Gemini allowed.**
- **Narrate aloud constantly.**
- **Do not open `ANSWERS.md`** until your 60-minute timer ends.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| 1. Analyze | 0:00–0:20 | No | Write down the evaluation model *from the docs*: rule precedence, rollout semantics, override chain, cache behavior, audit guarantees. Then read the code and mark every place it disagrees with the docs. (20 min — this mock rewards slow reading.) |
| 2. Collaborate | 0:20–0:32 | Yes | Prompt Gemini to add a kill-switch API that bypasses the cache. Narrate your prompting strategy first. |
| 3. Validate | 0:32–0:47 | Yes | Verify the AI's kill-switch against the override precedence chain. Then hunt the planted bugs yourself. Target: 4+ of 6 — these are subtle. |
| 4. Optimize & test | 0:47–1:00 | Yes | Fix **one** bug properly and write a test proving it (e.g. bucket stability across "restarts" using distinct-but-equal `String` instances). |

## Scoring checklist

**Phase 1 — Comprehension (no AI)**
- [ ] Wrote the documented semantics *before* reading the implementation
- [ ] Found at least one doc-vs-code disagreement from reading alone
- [ ] Could explain the rollout bucketing scheme's intended stability property
- [ ] Listed the full override precedence chain from the docs

**Phase 2 — AI fluency**
- [ ] Stated prompting strategy aloud before prompting
- [ ] Gave Gemini the precedence chain as a constraint, not a suggestion
- [ ] Caught Gemini violating (or ignoring) the precedence chain

**Phase 3 — Validation**
- [ ] Found 4+ of the 6 planted bugs without AI assistance
- [ ] For each logic bug, constructed the concrete input that produces the wrong answer
- [ ] Distinguished "code is wrong" from "docs are wrong" — and said which

**Phase 4 — Rigor**
- [ ] One bug fixed with real, compiling code
- [ ] A test that fails before the fix and passes after
- [ ] Explained the production blast radius of the bug you fixed

**L5 stretch:** pick any bug and explain how you'd detect it in production
*without* reading the code — what metric, log, or experiment anomaly would
surface it? If you can't answer, that's the senior gap: detection design.
