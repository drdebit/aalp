# ALEKS-Derived Pedagogy — Planned Work for AALP

> **Resolved 2026-08-07:** the evaluation this handoff asked for is done —
> verdicts and design sketches live in `ALEKS-DERIVED-MECHANICS.md`, with a
> summary section in `CURRICULUM-ROADMAP.md`. Retained for context (ALEKS
> account details, the disposable class still to archive). Note one stale
> fact below: the drill bar is now 8-of-10 (d192d00), not 4-of-5.

*Written 2026-08-07 from the Accrue side (Matt + Claude), as a handoff for a
fresh session working in this repo. The ALEKS exploration happened in August
2026 while designing Accrue's adaptive learning; the conclusion was that most
of what ALEKS does well belongs HERE, not in Accrue.*

## Why aalp is the fit (and Accrue wasn't)

Accrue's students optimise for the next exam, so prerequisite gating was
explicitly rejected there — mastery stays guidance and telemetry, never a
block. AALP is the opposite case: the Guided Year is already a **gated
mastery progression** (per-level tutorial + practice drill, pass 4 of 5 to
unlock recording; scripted year with corridor decisions; errors-persist
ledger with consequences at period close). ALEKS's mechanics map onto that
architecture directly instead of fighting it.

## What was mined from ALEKS

Explored via Matt's GSU instructor account (login MDEANGELIS102). A
disposable class **"DELETE ME - Feature Exploration"** (Business Statistics,
code K9LWA-GJRKJ) still exists there — archive it from Instructor
Administration → Class List when done. Matt can re-authenticate a Claude
browser session into ALEKS if a fresh look is useful.

Concepts worth adapting:

1. **Initial Knowledge Check** — ~25 adaptive free-response items with an
   explicit "I Don't Know" option; places the student before instruction
   begins.
2. **The Pie** — a mastery map: one visual, sliced by topic area, filled by
   demonstrated mastery. Students always know where they stand and what's
   left.
3. **Ready-to-learn selection** — the learning path only offers topics whose
   prerequisites are mastered; the student picks among currently-ready
   topics rather than following a fixed sequence.
4. **Mastery = streak, not average** — 5 correct in a row masters a topic;
   wrong answers cost nothing except the streak, and worked explanations
   are always available without penalty.
5. **Periodic progress knowledge checks** — scheduled re-checks that can
   *unmaster* forgotten topics, pushing them back into the path (retention,
   not just forward progress).
6. **Dated modules vs self-paced** — instructor-set deadlines validated
   against the prerequisite graph, vs pure self-pacing.

## Candidate mappings onto the Guided Year

*Proposals from the Accrue side — the aalp session should validate each
against DESIGN.md and CURRICULUM-ROADMAP.md, which it should read in full
first. The session writing this had only skimmed them.*

- **Initial knowledge check → placement.** An entering student who already
  reads journal entries or knows debits/credits could place past parts of
  L0/L1 tutorials (or skip straight to their drills). Free-response with "I
  Don't Know" fits the assertion-builder UI naturally.
- **Pie → assertion-level mastery map.** The Guided Year has levels and
  topics; a single at-a-glance mastery visual (which assertion families are
  solid, which are shaky) would give aalp what the Pie gives ALEKS. Grading
  as per-assertion distance (roadmap consequence #2) supplies the fill data.
- **Ready-to-learn → next-drill selection.** Where the script allows any
  freedom, offer the student the set of ready topics instead of one fixed
  next thing.
- **Streak mastery vs the 4/5 drill bar.** Compare: ALEKS uses consecutive
  correct (5-in-a-row) rather than 4-of-5. Consecutive-correct is harsher
  on careless errors but cleaner as a mastery signal. Worth an explicit
  decision rather than an inherited default.
- **Progress knowledge checks → retention re-checks.** aalp's errors-persist
  ledger surfaces consequences at period close; a periodic *unledgered*
  re-check (like the tutorial drills, but sampling mastered material) would
  add the retention loop ALEKS gets from progress checks. This is the same
  "exam readiness, not forward march" principle that shaped Accrue's 20%
  review sampling.
- **Dated modules** — probably later; relevant once aalp runs a real intro
  section on a calendar.

## Suggested first steps for the aalp session

1. Read `CLAUDE.md`, `DESIGN.md`, `CURRICULUM-ROADMAP.md`,
   `DUAL-FLUENCY-DESIGN.md` fully.
2. Evaluate the mappings above against the actual Guided Year mechanics;
   sort into: parameter tweak / new mechanic / doesn't fit.
3. Write the outcome into the roadmap (or a design doc per feature, matching
   repo convention).
4. If specific ALEKS behavior needs re-checking, ask Matt to re-authenticate
   a browser session — go in with concrete questions.

## Repo/state notes (as of 2026-08-07)

- This local clone (`~/Dropbox/clojure/aalp`) and the running copy on
  `choochoo` (`~/clojure/aalp`) were both at `d192d00`; choochoo had one
  untracked file (`assertive-accountingV2-JAR.org`). Remote:
  `github-drdebit:drdebit/aalp`.
- Accrue context, if needed: `~/Dropbox/clojure/accrue-backend/STATUS.md`
  (authoritative) and `doc/EXAMS-DESIGN.md` there. Accrue's Rasch staircase
  (1PL difficulty, ability solved from recent attempts, 70% target, 20%
  uniform review) is deployed and could inform aalp's drill selection
  eventually.
