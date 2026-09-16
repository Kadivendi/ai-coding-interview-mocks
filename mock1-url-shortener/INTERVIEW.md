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
- **Phase 1: absolutely no AI.** No Gemini, no Copilot, no autocomplete beyond
  what your editor already does. This mirrors the real round.
- **Phases 2–4: Gemini allowed** (the real round mandates Gemini — practice
  with it, not another model).
- **Narrate aloud constantly**, even when alone. If you go quiet for 60
  seconds, you've failed the phase regardless of output.
- **Do not open `ANSWERS.md`** until your 60-minute timer ends. No exceptions.

## Phase timers

| Phase | Time | AI? | Goal |
|---|---|---|---|
| 1. Analyze | 0:00–0:15 | No | Map the architecture, data flow, and trust boundaries. List 3 clarifying questions you'd ask the author. Mark 2–3 places that "smell" — no fixing yet. |
| 2. Collaborate | 0:15–0:30 | Yes | Prompt Gemini to add a `DELETE /links/{code}` endpoint. Narrate your prompting strategy before each prompt: what you're asking and why. |
| 3. Validate | 0:30–0:45 | Yes | Verify every AI claim against the actual code. Then hunt the planted bugs yourself — no asking Gemini "find the bugs". Target: 4+ of 6. |
| 4. Optimize & test | 0:45–1:00 | Yes | Fix **one** bug properly (not a sketch — real code) and write a small test proving the fix (a `main` driver or plain asserts is fine). Propose one performance improvement out loud. |

## Scoring checklist

Be honest — check what you actually did, not what you meant to do.

**Phase 1 — Comprehension (no AI)**
- [ ] Drew the component map and request data flow from memory, without re-reading
- [ ] Identified the trust boundary (user-supplied `target` URL)
- [ ] Asked at least one question the code can't answer (e.g. "who starts the reaper?")
- [ ] Flagged a misleading comment rather than trusting it

**Phase 2 — AI fluency**
- [ ] Stated your prompting strategy aloud *before* prompting
- [ ] Prompted narrowly (one task per prompt) instead of "add the endpoint"
- [ ] Caught at least one thing Gemini got wrong or hand-waved

**Phase 3 — Validation**
- [ ] Found 4+ of the 6 planted bugs without AI assistance
- [ ] For each bug, said *why it's subtle* (not just "it's wrong")
- [ ] Did not accept any AI suggestion you couldn't explain

**Phase 4 — Rigor**
- [ ] One bug fixed with real, compiling code
- [ ] A test that fails before the fix and passes after
- [ ] Articulated a tradeoff in your fix (what you gave up)

**L5 stretch:** for any bug you found, explain the blast radius in production
and what you'd monitor to detect it. If you can't, that's the gap to close.
