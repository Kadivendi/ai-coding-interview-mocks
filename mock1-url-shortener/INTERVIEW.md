# Mock 1 — URL Shortener (warm-up · easy)

A production-looking Java URL shortener, ~450 lines across 5 files:

| File | Owns |
|---|---|
| `App.java` | Request wiring: create-link and redirect flows |
| `UrlStore.java` | Link storage, read-through cache, code generation, expiry |
| `RateLimiter.java` | Per-API-key token-bucket rate limiting |
| `Analytics.java` | Click counting and event log |
| `Models.java` | `ShortLink`, `ClickEvent` domain models |

You inherited this codebase. It "works" — every endpoint returns 200 in the
demo. Your job is to read it like a senior engineer and find where it lies.

## Rules

- **60 minutes total**, strict timer. Phases below.
- **Phase A: absolutely no AI.** No Gemini, no Copilot, no autocomplete
  beyond what your editor already does. This mirrors the real round.
- **Phases B–D: AI allowed.** But in Phase B, finding the bugs is *your*
  job — do not prompt "find the bugs". Use AI for mechanical help only.
- **Narrate aloud constantly**, even when alone. If you go quiet for 60
  seconds, you've failed the phase regardless of output.
- **Do not open `ANSWERS.md`** until your 60-minute timer ends. No exceptions.
- **Verification over completion.** Not finishing Phase D is explicitly
  *not* a fail — a strong A–C with an unfinished D still passes.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| A. Read & map | 0:00–0:10 | No | Map the architecture, data flow, and trust boundaries. List 3 clarifying questions you'd ask the author. Mark 2–3 places that "smell" — no fixing yet. |
| B. Fix | 0:10–0:25 | Assisted | Fix the **5 planted bugs** yourself. AI may help with mechanics (syntax, API lookup) but not with finding. Target: 4+ of 5. |
| C. Build | 0:25–0:45 | Yes | Implement **one new feature with AI** (~80–120 lines, see `TASKS.md`). Narrate your prompting strategy before each prompt. **Ask clarifying questions about the vague requirement before coding** — that *is* the test. |
| D. Scale | 0:45–1:00 | Optional | Discussion: "traffic 10x's overnight — what breaks first and what do you change?" Name the first bottleneck, the fix, and its tradeoff. |

## Scoring checklist

Be honest — check what you actually did, not what you meant to do.

**Phase A — Comprehension (no AI)**
- [ ] Drew the component map and request data flow from memory, without re-reading
- [ ] Identified the trust boundary (user-supplied `target` URL)
- [ ] Asked at least one question the code can't answer (e.g. "who starts the reaper?")
- [ ] Flagged a misleading comment rather than trusting it

**Phase B — Bug fixing**
- [ ] Found 4+ of the 5 planted bugs without AI finding them for you
- [ ] For each bug, gave a concrete trigger (an input or a thread interleaving)
- [ ] Said *why it's subtle* (not just "it's wrong")

**Phase C — AI fluency (this is the graded skill)**
- [ ] Asked clarifying questions about the vague requirement *before* writing code
- [ ] Stated your prompting strategy aloud *before* each prompt
- [ ] Prompted narrowly (one task per prompt), not "build the feature"
- [ ] **Rejected at least one AI suggestion with a stated reason**
- [ ] Compiled and ran at least one check of AI-generated code before trusting it

**Phase D — Scale thinking**
- [ ] Named what breaks *first* (not fifth) with a reason tied to the code
- [ ] Proposed a fix and named its tradeoff
- [ ] Left Phase D unfinished without guilt — verification beats completion

**L5 stretch:** for any bug you fixed, explain the blast radius in production
and what you'd monitor to detect it. If you can't, that's the gap to close.
