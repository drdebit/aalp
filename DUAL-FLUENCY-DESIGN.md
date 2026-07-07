# Dual Fluency — Live JE Derivation

*Shipped v1 2026-07-08 (author + Claude Fable 5). Implements the
platform-design-notes' dual-fluency redesign: students see which
assertions are load-bearing for which line of the journal entry.*

## What it is

After a student commits their assertions (practice or simulation
classify), the feedback panel gains **"What your assertions
produce"**: the journal entry derived *from the student's own
selections* by SP's firm rulebook — faithfully, silently, line by
line:

- **Per-line provenance.** Every line shows the assertions that
  produced it (`← receives`, `← provides + has-counterparty`).
  Clicking a line reveals the rulebook rule in plain language and
  highlights the producing assertion buttons (amber ring).
- **Faithful, not corrective.** Wrong assertions produce wrong entries
  without comment (the auditor's lesson). The derivation never
  consults the correct classification, so it leaks nothing —
  commit-then-reveal is preserved (the panel only appears with
  feedback, post-commit).
- **Partial entries are prompts, not errors.** `receives cash` alone
  derives `DR Cash 100 / CR ??? — "Something must balance this. What
  did SP give up, or come to owe? The assertions do not say yet."`
- **Recorded — but not reflected.** Selected assertions that produce
  no line and serve no context (`expects`, `is-allowed-by`, `allows`,
  `reports`) appear in an amber strip with per-assertion explanations
  ("Double-entry has no place for a probability-weighted expectation.
  The assertion stays in the record; the journal entry cannot see
  it."). This is the recording-vs-reporting distinction as a UI
  element — the design notes' highest-leverage piece.
- **Explore mode.** After the reveal, an Explore toggle lets students
  flip assertions on and off and watch the entry change live
  (debounced re-derivation). Iteration happens *after* commitment, so
  assessment integrity holds while the learning loop runs free.

## The rulebook (backend)

`src/clj/assertive_app/je_derive.clj` — a data table of pattern→line
rules using classification.clj's exact account vocabulary. Highlights:

- Context-dependent rules are the pedagogy: the **same** `requires
  (provides, monetary)` assertion derives **DR Accounts Receivable**
  when goods flowed out (`provides physical` co-selected) and **CR
  Accounts Payable** when goods flowed in — each rule's text says so
  explicitly. Revenue requires a counterparty; drop the counterparty
  chip and the Revenue credit disappears.
- Amount resolution: the matched flow's own quantity, else the
  monetary flow among selections, else the problem's `:amount`, else
  `?` (COGS lines are deliberately `?`: "at cost, which the assertions
  about THIS exchange do not carry — it lives in the production
  events").
- Coverage v1: money flows, goods in (raw materials / equipment /
  finished goods by item), sales with revenue + COGS pair, credit
  obligations both directions, cash-in-advance (deferred revenue),
  simple production. Prepaids, wages, and adjusting-entry derivation
  are known gaps — add rules, not machinery.

`POST /api/derive-je {selected-assertions, variables}` → `{lines,
placeholders, context, not-reflected, totals}`. Stateless; auth
required; nothing about the correct answer in or out.

## Firm-rulebook framing

Rule texts are written as *SP's rulebook* speaking ("SP's rulebook:
blank shirts and ink are inputs to production...") — the
classification engine reframed as the firm's own recorded policy
rather than the platform's truth, per the platform notes. A future
level can expose the rulebook as a browsable panel and let students
write rules (grading by extension); the rule table is already data.

## Future work

- Live re-derivation on parameter edits (currently: toggles re-derive
  in explore mode; param changes need a re-toggle).
- Rulebook browser panel (L2: click any JE line anywhere → the rule).
- Student-authored rules against hidden test narratives (L3+).
- Derived-JE display on the simulation success path (currently the
  confirmation shows the ledger JE; provenance could join it).
