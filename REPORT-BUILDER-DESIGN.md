# Report Builder — Design

*Decided 2026-07-07 (author + Claude Fable 5). Backend shipped in
`c8f6d54` (aalp) and `bb07657` (assertive-engine). Frontend not yet
built. This document records the decided design; see
SENTENCE-BUILDER-DESIGN.md Phase 4 and the TAR Round 2
platform-design-notes for the underlying pedagogy.*

## What it is

Students compose report definitions — collects/includes/excludes
patterns plus an aggregation — over **their own** simulation ledger,
preview the results freely, and then **record** the composition, at
which point the report becomes a first-class event in their ledger
carrying its selection logic, result, authorizing standard, and their
name as asserter. Reporting is taught downstream of recording: the
same events, walked differently, yield different reports, and the walk
itself is an accountable, recorded thing.

This is the platform twin of the paper's operator-composition claim
(A17): rules are user-composed queries recorded with provenance, not
built-in inference.

## Decided design (three forks resolved)

1. **Composer surface: sentence builder + read-only DSL mirror.**
   Students assemble the query from chips/dropdowns in the existing
   sentence-builder aesthetic:

   ```
   Collect events where I [provides ×] [receives ×]
     measured in [monetary ▾], between [Jan 1] and [Dec 31]
     excluding events with [requires ×] [expects ×] [allows ×]
   then [sum ▾] the [receives ▾] amounts
   ```

   Beneath it, a read-only pipe-DSL rendering updates live:

   ```
   events |> filter(provides, receives)
          |> exclude(requires, expects, allows)
          |> sum(receives)
   ```

   Reading before writing (per the platform notes): the DSL is met as
   a description of what the student built, long before anyone types
   it. Direct DSL editing is a later-level unlock, out of scope for v1.

2. **Placement: simulation mode, unlocks at Level 3.** The first
   moment a student's ledger contains sales worth reporting and
   forward-looking assertions worth excluding. Unlock moment framed as
   a question: "You have N events. What was your revenue?" Practice
   mode stays recording-focused.

3. **Preview policy: free preview, deliberate record.** Iterating a
   report against counterexamples IS the lesson
   (standards-as-refinement) — unlike journal entries, previewing a
   report leaks no answer. Recording is a distinct act with a
   confirmation moment: *"This becomes part of your record, with your
   name on it."* Graded exercises can later use hidden test ledgers
   (grading by extension), which makes preview-fishing pointless
   anyway.

## The three-stage pedagogy

- **Stage 1 — Read.** Accrual revenue and cash revenue appear as
  filled-in builder forms over the student's own ledger, with live
  results. Clicking a result reveals the collected events (the
  pattern's extension). Students discover the two revenues differ only
  in their exclude chips, over identical events — the
  multiple-definitions demonstration, experienced before explained.
- **Stage 2 — Modify.** Copy a built-in composition, change one thing,
  preview. Start permissive, meet counterexamples, tighten.
- **Stage 3 — Compose and record.** From blank. The recorded report
  appears in the ledger as an expandable event: collects/includes/
  excludes chain, `is-allowed-by` authority, result, and click-through
  to every input event (engine back-refs).

## API contract (shipped)

- `POST /api/engine/compose/preview`
  body: `{spec: {includes: {...}, excludes: {...}},
          aggregate-type: "receives", op: "sum"}`
  → `{result: {value, unit}, count, events: [...]}`
  Pattern keys are whitelisted server-side (declarative only; nothing
  executable crosses the wire) and `asserted-by` is force-scoped to
  the authenticated student.
- `POST /api/engine/compose/record`
  body: preview body + `{event-id?, date?, allowed-by?, category?,
  basis?}` (enums coerced with safe fallbacks)
  → `{event-id, result, count, input-ids}`
- Existing `GET /api/engine/event/:id` and `/api/engine/chain/:id`
  serve the ledger view and click-through.

## Frontend work plan (not yet built)

1. State + API: composition atom (includes/excludes chips, unit
   filter, date range, op/aggregate), preview debounce, record action
   (`api.cljs`, `state.cljs`).
2. Builder component in the sentence-builder visual idiom; DSL mirror
   as a pure render of the composition atom (`views.cljs`).
3. Stage-1 exemplars: accrual/cash compositions as read-only presets
   loaded into the builder ("open as copy" enables Stage 2).
4. Ledger integration: recorded reports listed with an expandable
   chain view; input-event click-through via back-refs.
5. Level gating: unlock at L3; the unlock prompt uses the student's
   live event count.
6. Later (out of v1): direct DSL editing; instructor hidden-test
   grading; `is-allowed-by` chooser tied to a standards panel (the
   three-layer standard/firm/transactions view from the platform
   notes).

## Dependencies / notes

- `deps.edn` expects `../assertive-engine` at or after `bb07657`
  (derive-and-record).
- Tutorials currently scaffold only L0–L2; L3 content exists in
  `classification.clj` but is unscaffolded — the Stage-1 reading
  exercise is a natural first piece of L3 tutorial content.
- Datomic-backed deployments need `DATOMIC_DB_PASSWORD`; the engine
  store also runs in-memory (`engine/init!` default) for development.
