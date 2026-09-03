# Headless-student study

Agents that use AALP the way a student would, get confused, learn (or
don't), and are then examined — so the platform's teaching can be
measured and its gaps located before real students hit them.

## What it is

- **`aalp_platform.py`** — a text-mode client of the app. It walks the
  same path as the browser in guided mode (walkthrough episodes →
  level tutorial reading → quiz → practice drill with the 8-of-10 /
  5-streak round mechanics), renders the same copy (pulled from
  `episodes.cljs` and `tutorials.cljs` by `dump_content.clj`), and
  calls the same endpoints with the same payloads as `api.cljs`.
  Disclosure mirrors the UI: a line's rule text only when the line is
  opened, the walkthrough's `then` only after the step is done, the
  derived entry in a drill only after commit.
- **`llm.py`** — a sealed model session over `claude -p`: no tools, a
  replaced system prompt, a working directory outside the repo, so the
  student sees nothing but the screen. One session per student, so
  confusion, learning and the post-test are one conversation.
- **`student.py`** — one student: persona → pre-test → learning loop
  (screen in, `{think_aloud, action}` out, one action per turn) →
  post-test → rubric grading by a separate grader model → gap analysis
  of the think-aloud transcript.
- **`assess.py`** — the items, rubrics and grader/analyst briefs.
  Pre-tested items (double-entry basics, a deferred-revenue transfer, a
  novel transfer) are asked before and after; platform-specific items
  (why Owner's Capital, what `allows` does, inherited Raw Materials,
  recorded-but-not-reflected, cost flow, production without a
  counterparty) are post-only.
- **`report.py`** — aggregates a cohort into `runs/report-<cohort>.md`.

Personas (in `student.py`): `novice` (never took accounting), `trad`
(passed a traditional intro course, thinks in accounts), `hasty`
(skims, tries things).

## Running

Backend must be up (choochoo by default; `--base` to point elsewhere).
Students log in as `<id>@study.test`, so they never touch a real user.

    bb study/dump_content.clj          # refresh copy from the cljs sources (repo root)
    cd study
    python3 student.py --id s01 --persona novice --model haiku \
        --stages walkthrough,tutorial:0,tutorial:1 --max-turns 240
    ./run_cohort.sh c1 "s01:novice:haiku s02:trad:haiku s03:hasty:sonnet" --max-turns 240

Stages: `walkthrough`, `tutorial:N` (gate → reading → quiz → drill),
`drill:N` (straight to the drill). About 7 s a turn on haiku; a full
default path is ~200 turns, ~35 minutes with assessment.

Outputs per student in `runs/<id>/`: `transcript.md` (readable
think-aloud), `transcript.jsonl` (with every screen), `events.jsonl`
(platform events: submits with correctness and missing assertions,
worked examples, quiz results), `pretest.json`, `posttest.json`,
`grades.json`, `gaps.json`, `summary.json`.

`smoke_walk.py` drives the client with a scripted perfect student and no
model — the fastest way to check the client after a platform change.

## Caveats, honestly

- It measures the platform's **content and feedback**, not its visual
  UI. Layout, affordance discoverability and rendering bugs need a
  browser pass.
- The student is a language model told to know nothing beyond the
  screen. That firewall is a prompt, not a guarantee; the `trad`
  persona is *allowed* debit/credit knowledge. Read the think-aloud
  before trusting a score.
- The rubric grader and gap analyst are models too. They are strict
  and literal by instruction; spot-check `grades.json` against
  `posttest.json`.
- Legacy vocabulary entries the sentence builder never renders
  (`consumes-inventory` etc.) are hidden, as in the UI.
