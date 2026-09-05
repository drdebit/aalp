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
- **`adapter.py`** — the learner-lab adapter: the text-mode client
  presented through the lab's interface.
- **`items.yaml`** — the test items and rubrics, and the platform blurb
  the learner is given. Pre-tested items (double-entry basics, two
  transfers) are asked before and after; platform-specific items are
  post-only, and every post-test claim must be quoted from a screen.
- **`profiles/`** — knowledge profiles: `novice-business-undergrad`
  (general knowledge, nothing specialised), `traditional-intro-accounting`
  (thinks in accounts, has never seen assertions), `hasty-sophomore`.

The learners themselves, the gatekeeper, calibration, grading and the
report are the **`learner-lab` skill** (`~/.claude/skills/learner-lab`,
from `system-configs`). Read its SKILL.md for the procedure and for how
to read the integrity line.

## Running

Backend must be up (choochoo by default; `--base` to point elsewhere).
Students log in as `<id>@study.test`, so they never touch a real user.

    bb study/dump_content.clj          # refresh copy from the cljs sources (repo root)
    cd study
    ./run_cohort.sh c7 "s71:novice-business-undergrad s72:traditional-intro-accounting s73:hasty-sophomore" --max-turns 260
    # one learner by hand:
    python3 ~/.claude/skills/learner-lab/learnerlab/cli.py run --adapter adapter.py:Adapter \
        --adapter-args '{"learner_id":"s01"}' --profile profiles/novice-business-undergrad.yaml \
        --items items.yaml --id s01 --out runs
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
- The learner is a language model. The lab's firewall (calibration,
  gatekeeper every turn, cited answers) makes the knowledge constraint
  structural rather than a request; read the report's Firewall section
  before its numbers, and the think-aloud before trusting a score.
- Cohorts c1-c6 (Sept 3-4, 2026) predate the firewall; their pre/post
  gains on general items are not trustworthy, their drill outcomes and
  think-alouds are.
- Legacy vocabulary entries the sentence builder never renders
  (`consumes-inventory` etc.) are hidden, as in the UI.
