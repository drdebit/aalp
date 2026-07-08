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

## The Guided Year architecture (decided 2026-07-08)

Practice and Simulation merge into **one two-act arc**, replacing the
mode toggle. Everything a student does accumulates in one persistent
record — their own database.

**Year 1 (guided).** A scripted, instructor-authored business year,
delivered as narrative transactions the student records in assertions
(assertions-first; journal entries are view-only derived artifacts
throughout). *Errors persist*: transactions commit as asserted, the
dual-fluency panel gives immediate visibility without blocking, and
consequences surface at period closes (the statement diff traces every
discrepancy to its originating event — error archaeology). Each level
opens with a **tutorial + practice drill** (decided 2026-07-08):
instruction, a comprehension quiz, then a round of *unledgered*
practice problems with complete feedback — pass 4 of 5 to unlock
recording at that level. The drill is the mastery gate; the ledger
stays errors-persist. The sandbox/books distinction is itself taught:
mistakes in practice are free, mistakes in your books follow you to
period close. The script is punctuated by **corridor decisions** — bounded
choice gates ("blank shirts are $3.00 each; how many?") whose
resulting transactions are **entered automatically**, each shown with
its derived record. Decisions stay about deciding; recording practice
lives in the scripted transactions; auto-entries add view-only JE
exposure. Two or three corridors in round one; the script's coherence
(ascending dates, sane balances, inventory sufficient for later sales)
is guaranteed by authorship, and comparability across students is
preserved because divergence is bounded.

**Year 2 (autonomous).** Opens with the student reviewing Year 1's
financial statements — the statements-discovery exercise (build Year
1's income statement in the Report Builder) IS the bridge between the
years. Then: "Year 1, you kept the books. Year 2, you run the
company." The simulation inherits Year 1's closing state (capital,
equipment, inventory, customers), so autonomous play starts rich, and
the walk-your-record capstone gets a two-act arc.

**Scheduling is a parameter, not a design.** The guided year is built
as a schedule-independent artifact — a sequence of scripted days, each
with transactions and/or a gate — so the same artifact runs compressed
or semester-long.

## Pilot staging: Fall 2026 compressed → Spring 2027 semester-long

- **Fall 2026 (feasibility pilot):** the whole two-act exercise
  compressed into ~2 weeks late in the course, assigned as a bounded
  "complete the simulation + post-questions" credit assignment. Moves
  the content deadline from August to ~November; bounded IRB scope;
  produces the first cohort dataset, usability findings, and the
  hardening pass for the app.
- **Spring 2027 (effectiveness study):** semester-long progression
  with daily transactions or choices — the version consistent with the
  Accrue philosophy of frequent engagement — assigned as a full
  syllabus component, run as the A/B effectiveness study.

**Instrumentation is the study.** Classification diffs (missing/extra
assertions per attempt), per-assertion difficulty, attempt counts, and
level progression are already recorded. Add time-on-task and the
outcome instruments below.

**Critical path: IRB.** Consent flow, anonymization plan, and outcome
measures settled before the fall assignment window. Primary outcome
(spring study): **transfer** — performance on *novel* transaction
types not drilled in class. Secondaries: adjusting-entry performance,
retention, assertion-fluency measures. Fall pilot outcomes: usability,
completion, instrumentation validation.

## Summer work plan (near-term; each item: pedagogy → build)

1. **The Guided Year (replaces "pilot mode configuration").**
   Merge practice into the persistent record: scripted-day sequencing
   over the existing template machinery; committed-as-asserted
   persistence through the simulation ledger path; narrative as the
   primary transaction display (the structured detail list retires —
   it pre-parses the transaction for the student); corridor-decision
   gates with unit-price × quantity as the prototype (show the total
   at the decision step; auto-enter the resulting transactions with
   derived records visible); Year 2 unlock inheriting Year 1 state;
   rolling-accuracy advancement; L0/L1 auto-fill scaffolding review.
   *Build: moderate (flow rewiring over existing machinery). Content:
   authoring the scripted year — the largest single item on this
   plan and the binding constraint.*
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
- **Accrue coordination (position settled 2026-07-08).** The durable
  division is *item bank vs. environment*, not MC vs. assertions:
  Accrue delivers, sequences, and measures discrete items at scale;
  AALP runs the persistent world where records are made, queried, and
  reported. Neither absorbs the other (environments are bad at drill;
  item banks can't carry consequence or transfer).
  - *Pilot term:* zero runtime coupling. Accrue's 2101 content defers
    JE-construction items (or presents them assertion-mediated) so the
    two apps do not embody contradictory pedagogies for the pilot
    cohort — this is also a study-validity requirement.
  - *Year 2:* Accrue gains an assertion-response item type whose
    grader is AALP's assertion-distance service (derive-je +
    distance endpoints) — MC persists only where free-response
    grading is the bottleneck, and the distance metric removes the
    bottleneck. Accrue keeps delivery, spacing, coverage, and
    psychometrics; the assertion representation and its graders
    become shared infrastructure.
  - *Later:* shared analytics feed (AALP per-assertion difficulty +
    Accrue per-topic mastery) behind one instructor guidance view.

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
