# AI-Assisted Coding Interview — Mock Setups

Three mock interview codebases for the AI-assisted code-comprehension round,
easy → hard. Each mock is a self-contained Java codebase with 5 planted,
obvious-to-moderate bugs — no dependencies, no build tools, plain `javac`.

| Mock | Domain | Files | Bugs | Focus |
|---|---|---|---|---|
| `mock1-url-shortener` | URL shortener (warm-up) | 5 | 5 | Reading code, misleading comments, one security bug |
| `mock2-notification-fanout` | Notification fan-out worker | 6 | 5 | Concurrency: threads, locks, shutdown, retry arithmetic |
| `mock3-feature-flag-service` | Feature-flag evaluation | 5 | 5 | Correctness: logic bugs where docs and code disagree |

Each mock runs **60 minutes in 4 phases**: A (10 min) read & map, no AI →
B (15 min) fix the 5 bugs → C (20 min) implement a feature *with* AI →
D (15 min) scale follow-up discussion. This mirrors the reported real-round
shape: bug fix → feature implementation → "traffic 10x's, what breaks?"

> **Calibration note (Sep 2026):** Google's Gemini round is a pilot with
> **zero firsthand candidate reports** — these mocks are calibrated to
> Meta/LinkedIn candidate-report patterns plus Google's *published* rubric
> (four phases, AI fluency, verification over completion). Treat
> Google-specific expectations as **provisional**. The bug-fix phases here
> deliberately use obvious-to-moderate bugs (off-by-ones, wrong conditionals,
> racy counters) because that's what the reports describe — the difficulty
> lives in the Phase C implementation and the Phase D scale discussion, not
> in trivia gotchas.

## How to use

**Prerequisites:** a JDK (17+ works) and Gemini access. That's it.

```bash
cd mock1-url-shortener
javac *.java        # verify it compiles; do NOT "fix" anything yet
```

Then per mock:

1. Read `INTERVIEW.md` — the brief, the rules, the 60-minute phase timers.
2. Start a 60-minute timer. Work through `TASKS.md` phase by phase.
3. **Phase A is strictly no-AI.** No Gemini, no Copilot. This is the real
   round's hardest constraint — train it.
4. **Phases B–D: AI allowed** (in B, finding the bugs is your job — don't
   prompt "find the bugs"). Narrate aloud constantly: your prompting strategy
   *before* each prompt, your reasoning while reading, every bet you make.
   Silence while the model generates is a negative signal.
5. When the timer ends, open `ANSWERS.md` and score yourself against the
   checklist. **Never peek early** — peeking trains the exact habit the
   interview punishes.

## Practice tips that actually matter

1. **Use a weaker model than your daily driver for Phase C.** If you can
   steer a weak model to a correct implementation, steering Gemini is easy.
   A strong model hides your weak prompting; a weak one exposes it.
2. **Narrating while prompting is the hardest part — drill it separately.**
   Before *every* prompt, say aloud: what you're asking, why, and what
   context the model needs. If you can't say it, you don't understand the
   task well enough to delegate it.
3. **Reject something on the record every run.** Phase C is graded on the
   rejection: "no, because…" said aloud, with a reason tied to the code.
   Accepting everything the model suggests is the failure mode the rubric
   punishes.
4. **Ask the clarifying question.** Every Phase C has one deliberately vague
   requirement. The test is whether you ask *before* coding. In the real
   round, building the wrong thing confidently is worse than building
   nothing.

## Suggested 2-week rotation

Spaced repetition beats cramming, and re-running a mock after a week
measures real retention.

- **Week 1:** Mock 1 (twice — second run should be near-clean), then Mock 2.
- **Week 2:** Mock 2 again, then Mock 3 (twice — it's the hardest).
- **Final week:** re-run your weakest mock. Aim: 4+ of 5 bugs in Phase B,
  clarifying questions asked before any Phase C code, one on-the-record AI
  rejection per run, zero quiet minutes.

Daily (all weeks): 20–30 min of AI-assisted coding on an unfamiliar
open-source repo, narrating aloud — the mocks train the interview shape; the
daily reps train the narration muscle.

## Ground rules

1. **The timer is sacred.** 60 minutes, phases in order, no "just five more
   minutes" on Phase A.
2. **Narrate or fail.** If you catch yourself thinking silently for a minute,
   say it out loud retroactively and keep going.
3. **Verification over completion.** Not finishing Phase D is not a fail. A
   strong A–C with an unfinished D still passes — in the real round too.
4. **In Phase B, the bugs are yours to find.** AI for syntax and API lookup
   only.
5. **Track your scores.** After each run, note: bugs found in Phase B (x/5),
   clarifying questions asked in Phase C, AI suggestions rejected, and one
   thing to do differently. Improvement you can see is motivation you don't
   have to manufacture.

## Layout of each mock

```
mockN-<name>/
  INTERVIEW.md   # brief, rules, timers, scoring checklist
  TASKS.md       # concrete phase-by-phase tasks (A: read, B: fix, C: build, D: scale)
  *.java         # the codebase (planted bugs included)
  ANSWERS.md     # bugs: location, why subtle, fix, narration, L5 notes
                 # + Phase C sketch and Phase D scale discussion
```
