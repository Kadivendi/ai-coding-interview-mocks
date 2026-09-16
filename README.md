# AI-Assisted Coding Interview — Mock Setups

Three mock interview codebases for Google's AI-assisted Code Comprehension
round, easy → hard. Each mock is a self-contained Java codebase with planted,
subtle bugs — no dependencies, no build tools, plain `javac`.

| Mock | Domain | Files | Bugs | Focus |
|---|---|---|---|---|
| `mock1-url-shortener` | URL shortener (warm-up) | 5 | 6 | Reading code, misleading comments, one security bug |
| `mock2-notification-fanout` | Notification fan-out worker | 6 | 7 | Concurrency: threads, locks, backpressure, shutdown |
| `mock3-feature-flag-service` | Feature-flag evaluation | 5 | 6 | Correctness: logic bugs where docs and code disagree |

## How to use

**Prerequisites:** a JDK (17+ works) and Gemini access. That's it.

```bash
cd mock1-url-shortener
javac *.java        # verify it compiles; do NOT "fix" anything yet
```

Then per mock:

1. Read `INTERVIEW.md` — the brief, the rules, the 60-minute phase timers.
2. Start a 60-minute timer. Work through `TASKS.md` phase by phase.
3. **Phase 1 is strictly no-AI.** No Gemini, no Copilot. This is the real
   round's hardest constraint — train it.
4. **Phases 2–4: use Gemini** (the real round mandates it — don't practice
   with another model). Narrate aloud constantly: your prompting strategy
   *before* each prompt, your reasoning while reading, every bet you make.
   Silence while the model generates is a negative signal.
5. When the timer ends, open `ANSWERS.md` and score yourself against the
   checklist. **Never peek early** — peeking trains the exact habit the
   interview punishes.

## Suggested 2-week rotation

You have ~40 days. Don't burn all three mocks in week one — spaced
repetition beats cramming, and re-running a mock after a week measures real
retention.

- **Week 1:** Mock 1 (twice — second run should be near-clean), then Mock 2.
- **Week 2:** Mock 2 again, then Mock 3 (twice — it's the hardest).
- **Final week:** re-run your weakest mock. Aim: 5+ of 6/7 bugs found in
  Phase 3, one clean fix + passing test in Phase 4, zero quiet minutes.

Daily (all weeks): 20–30 min of Gemini practice on an unfamiliar open-source
repo, narrating aloud — the mocks train bug-hunting; the daily reps train
the narration muscle.

## Ground rules

1. **The timer is sacred.** 60 minutes, phases in order, no "just five more
   minutes" on Phase 1.
2. **Narrate or fail.** If you catch yourself thinking silently for a minute,
   say it out loud retroactively and keep going.
3. **One fix done well beats three sketched.** Phase 4 scores a compiling
   fix plus a test that fails before and passes after — not a description
   of a fix.
4. **Verify AI output independently.** Every Gemini suggestion you accept
   without checking is a rehearsal for failing Phase 3.
5. **Track your scores.** After each run, note: bugs found in Phase 3 (x/6),
   Phase 2 prompts used, and one thing to do differently. Improvement you can
   see is motivation you don't have to manufacture.

## Layout of each mock

```
mockN-<name>/
  INTERVIEW.md   # brief, rules, timers, scoring checklist
  TASKS.md       # concrete phase-by-phase tasks
  *.java         # the codebase (planted bugs included)
  ANSWERS.md     # every bug: location, why subtle, fix, narration, L5 notes
```
