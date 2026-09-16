# Mock 1 — Tasks

Work strictly in phase order. Start the 60-minute timer before Phase 1.

## Phase 1 — Analyze (0:00–0:15, NO AI)

1. Read all five files. On paper (or a blank doc), draw the component map:
   which class calls which, and what data flows between them on a
   `POST /links` and a `GET /{code}`.
2. Write down the public API surface: every public method, its inputs, and
   what it promises.
3. Write **3 clarifying questions** you would ask the original author before
   changing anything. At least one must be something the code cannot answer.
4. Mark 2–3 places that "smell". One line each: file, line, and what feels
   off. Do not investigate further — just mark.

Checkpoint: close the files. Can you describe the redirect flow from memory?
If not, your map isn't done.

## Phase 2 — Collaborate (0:15–0:30, Gemini on)

Task: add a `DELETE /links/{code}` endpoint to `App.java` that removes the
link from the store and the cache and returns whether anything was deleted.

1. **Before prompting**, say aloud: your plan for the prompts (how many, what
   each covers, what context Gemini needs).
2. Prompt Gemini narrowly — one task per prompt. Suggested split:
   - Prompt 1: "add a `remove(code)` method to `UrlStore`" (paste the class).
   - Prompt 2: "wire it into `App` as a delete handler with the existing
     exception style".
3. After each response, read the diff like a reviewer: does it handle expiry?
   the cache? thread-safety consistent with the class?

## Phase 3 — Validate (0:30–0:45, Gemini on)

1. Go through every AI-generated line from Phase 2 and verify it against the
   real code. Say aloud for each: "this is correct because…".
2. Now hunt the planted bugs **yourself** — do not ask Gemini to find them.
   There are 6. For each candidate, write: file:line, what's wrong, and a
   concrete input or interleaving that triggers it.
3. If Gemini offers a fix for anything, verify the fix independently before
   accepting it.

## Phase 4 — Optimize & test (0:45–1:00, Gemini on)

1. Pick **one** bug and fix it with real, compiling code. Verify with
   `javac` in this directory.
2. Write a small driver (a `main` method or plain `assert`s) that **fails
   before your fix and passes after**. Suggested: the token-bucket refill —
   drain the burst, sleep, and show whether permits come back.
3. Out loud: propose one performance improvement to the codebase (not a bug
   fix) and name its tradeoff.

Timer ends. Only now open `ANSWERS.md` and score yourself against the
checklist in `INTERVIEW.md`.
