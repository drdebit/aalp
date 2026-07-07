# AALP Curriculum Roadmap

*Working roadmap, 2026-07-08 (author + Claude Fable 5). AALP is the
platform basis for a curriculum-wide initiative: teaching Intro
Financial (later Managerial) more effectively through assertions, and
making students conversant in assertions as a way of understanding
accounting information — feeding intermediate courses (richer
treatments) and analytics courses (assertion-enabled datasets).*

## The mechanism, stated once

Intro students fail at journal entries because the narrative→JE
mapping is a black box they pattern-match. Assertions insert an
articulable intermediate representation — subgoal labels for the JE, a
forced self-explanation step, a bridge between linguistic descriptions
of transactions and journal entries. **We make the judgment visible;
visible judgment can be taught, diagnosed, and graded.** Every item
below exploits this mechanism a different way.

Three structural consequences drive the pipeline:

1. **Students build their own transaction databases.** Query them
   by attribute (not just account and transaction ID) and produce
   individual reports from their own base — including their errors.
2. **All attributes visible → grading as distance.** The gap between
   a student's answer and the correct one is measurable per-assertion,
   enabling automated partial credit and frequent, fine-grained
   guidance instead of two or three coarse assessments a term.
3. **Structured records → controllable AI.** Assertion data can be
   transformed (with AI assistance) into narratives and tabulated
   reports, and — because the record is structured — AI feedback can be
   grounded in the student's own ledger rather than free-floating.

## Pilot: ACCT 2101, Fall 2026 (decided)

**Design: assertions-first, view-only journal entries.** Students
operate the simulation and record events in assertions from day one.
Journal entries appear only as *derived artifacts* — the dual-fluency
panel and the auto-derived ledger — that students read but do not
construct. Debits and credits are introduced explicitly as a summary
of the record students already keep. JE-construction problems stay
off for the pilot cohort.

**Instrumentation is the study.** Classification diffs (missing/extra
assertions per attempt), per-assertion difficulty, attempt counts, and
level progression are already recorded. Add time-on-task and the
outcome instruments below.

**Critical path: IRB.** Consent flow, anonymization plan, and outcome
measures must be settled before week 1. Primary outcome candidate:
**transfer** — performance on *novel* transaction types not drilled in
class (the framework's distinctive claim). Secondaries: adjusting-entry
performance (traditionally the hardest topic; assertions should show
the largest gap), retention, and assertion-fluency measures.

## Summer work plan (near-term; each item: pedagogy → build)

1. **Pilot mode configuration.** Assertions-first sequencing: hide
   construct-mode problems; L0–L4 progression tuned for the 2101
   syllabus; dual-fluency panel as the JE-viewing surface.
   *Build: small (config/gating). Content: L3+ tutorial material —
   the larger share of the work.*
2. **Minimal-pair problem mode.** Present paired narratives differing
   by exactly one assertion ("what changed, and which JE line does it
   move?"). Contrasting cases build expert discrimination; the
   A/R-vs-A/P flip is the flagship pair and already derives correctly.
   *Build: moderate (pair generator over existing templates + a
   two-panel presentation).*
3. **Period-close consequence report + error archaeology.** At period
   close, the student's statements are diffed against the
   correct-ledger counterfactual, and every discrepancy traces (via
   provenance/back-refs) to the originating event: "find the event
   that broke your balance sheet." Productive failure with a built-in
   resolution path.
   *Build: moderate (counterfactual ledger already derivable from
   canonical answers per transaction; diff + trace UI).*
4. **Statements-discovery sequence.** "You have 14 events. What was
   your revenue?" — students build an income statement in the Report
   Builder before ever seeing the form; GAAP's version revealed as one
   composition; the standards-as-refinement loop (permissive rule →
   counterexamples → tighten) as a structured assignment.
   *Build: small (Report Builder shipped; this is assignment flow +
   Stage-1 content).*
5. **Assertion-distance grading (partial credit).** Formalize the
   distance metric from the already-recorded diffs (missing, extra,
   mis-parameterized assertions, weighted by pedagogical importance);
   expose as partial-credit scores and weekly guidance summaries.
   *Build: moderate backend (metric over existing attempt data),
   small UI. High leverage: this is the frequent-feedback engine.*
6. **Misconception-adaptive generation.** Target each student's weak
   assertions (per-assertion difficulty analytics exist) with minimal
   pairs on exactly those; instructor heatmap by assertion type.
   *Build: moderate (selection policy over existing analytics).*
7. **Deployment and verification.** Pull aalp + assertive-engine on
   the production host; browser-verify Report Builder and dual
   fluency; class-scale smoke (30–60 concurrent students); backup
   plan for the Datomic store.
   *Build: ops; do first.*

Deliberately deferred from summer: anything below.

## Medium-term (spring / year 2)

- **Perspective flip.** The same transaction recorded as SP and as
  the vendor: A/R↔A/P as one event from two chairs.
- **Walk-your-record capstone.** End of term, generate the narrative
  of the student's own business — formation → capital → capacity →
  production → sales → statements — from their own chains (engine
  path traversal). The value-accretion arc as a personal artifact.
- **Vocabulary-designer assignments.** Propose assertions for
  phenomena the vocabulary can't express (gift cards, warranties,
  loyalty points); argue pre-order placement; debate termination
  depth. Standard-setting as judgment practice; no software required.
- **Forensic mode.** "These statements contain one misstatement —
  find the event," via back-references and pattern queries.
- **Managerial entry point.** Budgets as modal subgraphs; variance as
  expects-vs-fulfills walks; job costing as chain traversal; goal-
  aligned budgeting (recording actions that are, and are not, aligned
  with stated goals). Same record, no new system.
- **AI narrative pipeline.** Instructor-side: generate large
  transaction sets and have AI transform assertion data into
  narratives and tabulated reports (structured source = controllable,
  checkable generation). Student-side, via the school AI portal (see
  below): feedback grounded in the student's own ledger.
- **Accrue coordination.** Accrue 3.0 plans 2101 material and
  JE-style problems; AALP owns the recording environment and the
  simulation. Proposed division of labor: Accrue = spaced assessment
  bank and topic coverage; AALP = the environment where records are
  made, queried, and reported; shared topic taxonomy and, later, a
  shared gradebook feed. Decide before both apps build overlapping
  2101 content.

## Long-term (year 2+)

- **Paired-firm marketplace.** Classmates' businesses transact with
  each other: your sale is my purchase; your `requires` is my
  receivable; referencing the counterparty's articulation of an item
  is the termination doctrine, lived. The most novel item on this
  roadmap and a paper in its own right.
- **Cohort datasets ("the dataset is us").** Each cohort's
  simulations become the analytics course's dataset — anonymized,
  assertion-queryable, generated by students who already speak the
  vocabulary. Complements (not replaces) existing BI cases: same
  skills, plus the logic layer underneath.
- **Synthetic scale with an answer key.** The engine's generator
  produces businesses at 100K–1M events with instructor-held ground
  truth; seeded anomalies yield labeled anomaly-detection labs.
- **DSL → SQL/Datalog progression.** Read the pipe DSL in intro
  (Report Builder mirror), write it in analytics, meet its SQL and
  Datalog equivalents in the capstone.
- **AI portal integration.** Student AI use routed through a
  school-controlled interface (model control, prompt+response capture,
  API-cost economics). AALP integration: assertion-grounded tutoring
  over the student's own record — retrieval-augmented feedback where
  the retrieval source is the ledger the student built.
- **Cross-course case continuity.** The same simulated businesses
  revisited across intermediate (richer treatments: leases,
  held-to-maturity), audit (assertion-sample verification; student
  rules graded by extension against hidden ledgers), tax (second
  compositions over the same events).

## Research program

1. **Effectiveness study** (Issues in Accounting Education / J. of
   Accounting Education): assertions-first vs. traditional sections;
   transfer to novel transaction types as the primary outcome;
   adjusting entries as the predicted largest gap; instrumentation
   largely built.
2. **Assessment papers**: assertion-distance partial credit; grading
   rules by extension.
3. **The marketplace/interoperability paper** (multi-entity assertion
   records referencing each other's articulations).
4. Alignment principle throughout: each paper section corresponds to
   a platform capability (recording ↔ sentence builder; rules ↔ dual
   fluency; reporting ↔ Report Builder; interop ↔ marketplace).

## Constraints and method

One person, part-time, alongside a major R&R. Consequences: the
summer list is scoped to lifts on existing machinery; content
(tutorials, assignments) is the binding constraint more than code;
AI-assisted development is the working method (this roadmap and the
platform's recent features were built that way); and anything not on
the summer list waits, however attractive. The vocabulary is frozen
per the research program — platform features follow the paper's
vocabulary, never lead it.
