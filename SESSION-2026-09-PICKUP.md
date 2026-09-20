# Pick-up list — the Alison / measurement-choice sessions

*Closed 2026-09-18. Work done across 2026-09-09 → 2026-09-18 in a session
that deliberately made **no code changes** (the other session owned the
tree). Everything below is documentation, investigation, or a decision —
nothing was edited in `src/`. This file exists so the other session can
pick all of it up cold.*

## What changed on disk

| File | Status | What it is |
|---|---|---|
| `CLOSING-ENTRY-STUDY.md` | new, §1–5 | Alison's *Closing Entry* app, played end to end; what to borrow; §4 the orientation-page design; §5 postscript on the paper managerial escape room |
| `MEASUREMENT-CHOICE-DESIGN.md` | new | "One record, many defensible answers" — three exercise families, mostly assembly of shipped machinery |
| `ENGINE-STORE-DIVERGENCE.md` | new | **URGENT.** Engine store can fall back to memory silently; history, severity, five ranked fixes, live check results |
| `SESSION-2026-09-PICKUP.md` | new | this file |
| `CURRICULUM-ROADMAP.md` | edited | two bullets — *Measurement choice* under Medium-term, a pointer added to *Cross-course case continuity* |
| `HANDOFF.md` | edited | urgent pointer block inserted at the top of *Start here* |
| `~/Dropbox/Analytics/escape-room/` | extracted | Alison's 7 `.docx` unzipped in place beside the zip |

Nothing else was touched. No commits were made.

## Do these first, in this order

> **All four are done or moot as of 2026-09-20.** Items 1, 2 and 3 were
> answered by removing the engine store rather than guarding it — see
> the resolution header on `ENGINE-STORE-DIVERGENCE.md`. Item 4 (the
> stale memory figure) is corrected in `CLAUDE.md`. The orientation
> work items below are built: the renderer, `:level` on every episode,
> and orientations for Levels 0, 1, 2, 5, 6 and 7. Still open from this
> list: `energy-unit` and the kWh recipe line (Family B), Report
> Builder Stage 4, Family C, and the four questions under *Open,
> needing Matt* — of which the first, the per-episode reading protocol,
> is the one the orientation now depends on for Levels 3, 4 and 8.
>
> Kept below as filed, because the reasoning is why the decisions went
> the way they did.

1. **Fix `init-engine!`'s docstring** (`server.clj:678`). One minute. Its
   claim that "the teaching flow never depends on it" is false and is the
   sentence that will talk the next reader out of caring. Item 1 in
   `ENGINE-STORE-DIVERGENCE.md`.
2. **Decide on the fallback** — fail loudly vs. keep it. Item 2 there.
   Everything else in that doc can wait; this one is a one-line change
   and a judgement call only you can make.
3. **Run the reconciliation check** (item 4). The one thing that could
   not be verified from here: whether any `:ledger-entry` row carries an
   `engine-event-id` whose event is absent from the engine. Needs a short
   Clojure process on choochoo. Turns "no evidence of loss" into
   "confirmed no loss."
4. **Correct CLAUDE.md's memory note.** It says ~4 GB, no swap, and
   treats memory as a binding constraint. Choochoo reports 15.7 GB total,
   9.9 GB available with everything running. The stale figure shaped a
   wrong theory during this investigation and will shape others.

## Decisions taken, so they are not re-litigated

- **Look and feel stays ours.** AALP does not conform to Closing Entry.
  Borrow its *structure*, keep Editorial Ledger. A department-wide app
  brand is a separate conversation Matt has flagged to Alison, and it
  supersedes the open question left in `CLOSING-ENTRY-STUDY.md` §3.
- **The orientation page is the tutorial gate's front page** — not a
  screen before or after it. It then detaches and persists, pinned and
  collapsible, for the whole episode. §4.
- **Unit of analysis is the episode**, not the level — but the renderer
  serves both scopes, because practice and simulation modes have no
  episodes at all.
- **Vocabulary panel shows the whole cumulative lexicon**, always, with
  today's additions marked and sometimes empty. Unlocked-but-unmet words
  are counted, not named.
- **Minimal pairs are historical where a prior episode exists,
  counterfactual otherwise.** Episode 1 already contains both kinds.
- **Family B's reveal is an act in the world**, not a system trapdoor:
  the utility furnishes interval data on request (undergraduate,
  clean-period comparison); submetering is purchased (graduate, and the
  period confounding is the exercise).

## Work items this produced

**Cheap, high value:**

- `:level` on each episode map in `episodes.cljs` — prerequisite for the
  level-scoped orientation; the only structural change §4 needs.
- `energy-unit` in `classification.clj`'s `unit-type-options` — adopts an
  existing branch of the research vocabulary (`schema.clj:57`), so it
  stays inside the frozen-vocabulary rule. The only vocabulary-touching
  step in the whole measurement-choice design.
- A kWh line in the production recipe's `consumes`. Data, not schema.

**Larger:**

- The orientation renderer itself (§4 anatomy, six slots).
- Report Builder "Stage 4" — compositions differing in scope, shown side
  by side, difference as the object of study.
- Family C is the cheapest of the three families to build and probably
  the best demonstration: every `expects` already carries
  `has-confidence-level`, so a confidence threshold is one more
  `excludes` chip.

## Things worth knowing that are not written elsewhere

- **Practice mode is clean.** `/api/classify` writes to neither store.
  `save-ledger-entry!` and `store-classified-event!` have exactly two
  callers each, both guided/simulation. The one-off-problem refactor
  holds — this was checked, not assumed.
- **`stage-progress-indicator` already exists** (`views.cljs:2807`) and
  renders current/complete/locked with dots and checks. The "build a
  stepper" item in `CLOSING-ENTRY-STUDY.md` §3 is restyling and
  placement, not construction.
- **Alison's BrewCo numbers all verify exactly** — every figure, no
  rounding. Preserved in `MEASUREMENT-CHOICE-DESIGN.md` as a ready-made
  managerial case. Her paper version is being retired and she was *not*
  sent the production bug found in it (riddle answers printed inside the
  sealed cards), because it is moot.
- **The SP example already models a plantwide-rate proxy on energy**
  without anyone having meant to: `emissions-per-shirt-calc`
  (`example.clj:620`) is total kWh received ÷ total shirts created.
- **The emissions conversion factor is an event, not a constant**
  (`example.clj:606`) — a third party's dated, revisable assertion with
  provenance. This is the strongest single demonstration in the whole
  measurement-choice design and it already exists.

## Open, needing Matt

- Wording of the reading protocol (orientation slot 3) per episode. Must
  not resolve to an account before the assertions are made.
- Whether the before/after panel's forward-looking row renders as prose
  or a list. Episode 2 (`allows`, the printer) is the test case.
- Display-vs-composer placement for Family C's confidence threshold.
- Whether Family B's withholding, once built, leaks granular figures in
  API responses — now largely moot under the act-in-the-world reveal, but
  worth a look when it is built.
