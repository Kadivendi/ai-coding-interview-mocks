# Mock 2 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase A.

## Phase A — Read & map (0:00–0:10, NO AI)

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

## Phase B — Fix (0:10–0:25, AI assisted)

There are **5 planted bugs**: a lifecycle bug, an interrupt-handling bug, a
shared-state bug, an off-by-one in the retry budget, and a numeric bug that
only appears under the incident-era config.

1. Hunt them **yourself** — no "find the bugs" prompting. AI for mechanics
   only.
2. For each concurrency bug, name the **interleaving** that triggers it.
   For the logic/numeric bugs, give the concrete config or input.
3. Fix all five with real, compiling code. Verify with `javac`.

## Phase C — Build (0:25–0:45, AI on)

Implement **with AI**: per-tenant rate limiting in the dispatcher.

Spec:
1. Each notification belongs to a tenant (you'll need to carry a tenant id
   on `Notification`).
2. Each tenant gets its own send budget (e.g. N sends/second, burst M).
3. Tenants that exceed their quota are **"deprioritized"**.
4. "Deprioritized" is deliberately under-specified. **Before writing any
   code, ask clarifying questions** (out loud): does it mean delayed,
   dropped, DLQ'd, or a separate slow lane? What happens to the
   over-quota notification — and is dropping ever acceptable for
   notifications? Are tenants known upfront or dynamic?

Rules for this phase:
- **Before prompting**, say aloud your strategy and the semantic constraints
  the model must respect (dedup: is a deprioritized id "sent"? retry budget:
  does waiting consume attempts? interrupt discipline).
- Prompt narrowly: tenant id plumbing first, then the limiter, then the
  dispatcher wiring.
- Review each diff as the on-call owner: thread-safety, what happens on
  shutdown with a full slow lane, and whether the limiter itself can lose
  data.
- **Reject at least one AI suggestion on the record**, with a reason.
- Compile and exercise the result (~80–120 lines is the right size).

## Phase D — Scale (0:45–1:00, discussion)

"Traffic 10x's overnight. What breaks first, and what do you change?"

1. Name the **first** bottleneck — the 3 AM page. Tie it to a specific line.
2. Propose the fix and name its tradeoff — and say *who* decides, because
   backpressure strategy is a product call, not just engineering.
3. Bonus: the retry policy has no jitter. Under 10x traffic with a flaky
   provider, what does the retry pattern look like — and what does the
   provider experience?

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
