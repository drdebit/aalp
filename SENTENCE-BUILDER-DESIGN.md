# Sentence-Builder UI Design

## Overview

This document outlines the design for refactoring AALP's assertion system to:
1. Use the full nested assertion structure from assertive accounting
2. Present assertions via a dynamic sentence-builder UI
3. Support the requires/expects pattern for credit transactions and bad debt

## Current vs. Target Assertion Structure

### Current (Simplified)
```clojure
;; Student selects:
{:requires {:action "provides" :unit "monetary-unit" :quantity 500}}
```

### Target (Full Nested Structure)
```clojure
;; System builds:
{:requires
 {:event
  {:has-identifier "auto-generated-uuid"
   :date "2025-04-01"
   :provides {:has-quantity 500
              :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}}}}

{:expects
 {:has-confidence-level 0.95
  :event {:fulfills "auto-generated-uuid"}}}
```

## Sentence-Builder Concept

Students build assertions by constructing a natural language sentence. As they add assertions, new sentence fragments appear.

### Example: Credit Sale

**Step 1:** Student starts with base event
> "On **[Jan 15, 2025]**, SP..."

**Step 2:** Student adds `provides` assertion
> "On **[Jan 15, 2025]**, SP **provides** **[10]** **[Printed T-Shirts]**..."

**Step 3:** Student adds `has-counterparty`
> "On **[Jan 15, 2025]**, SP **provides** **[10]** **[Printed T-Shirts]** **to** **[Customer B]**..."

**Step 4:** Student adds `requires` (creates obligation)
> "On **[Jan 15, 2025]**, SP **provides** **[10]** **[Printed T-Shirts]** **to** **[Customer B]**,
> **which requires** **[Customer B]** **to provide** **[$250]** **[Cash]** **by** **[Feb 15, 2025]**."

**Step 5:** Student adds `expects` with confidence
> "SP **expects** this **with** **[0.95]** **confidence**."

### Visual Mockup

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Build Your Assertion                                              [+ Add]   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  On [Jan 15, 2025 ▼], SP provides [10    ] [Printed T-Shirts ▼]            │
│                                                                             │
│  to [Customer B____]                                                        │
│                                                                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│  This creates an obligation:                                                │
│                                                                             │
│  [Customer B] must provide [$250    ] [Cash ▼] by [Feb 15, 2025 ▼]         │
│                                                                             │
│  ─────────────────────────────────────────────────────────────────────────  │
│  Expectation of fulfillment:                                                │
│                                                                             │
│  SP expects this with [====●====] 95% confidence                           │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ Available assertions: [requires] [expects] [consumes] [creates] ...         │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Assertion Definition Format

Each assertion needs:
- Code and metadata (existing)
- Sentence template for rendering
- Nested structure specification
- Parameter definitions with UI hints

### Proposed Format

```clojure
{:code :provides
 :label "Provides"
 :level 0
 :domain :exchange

 ;; Sentence rendering
 :sentence {:fragment "provides"
            :pattern [:quantity :unit]  ; What follows the verb
            :connectors {:next "and"    ; Before next assertion
                        :counterparty "to"}}  ; Before counterparty

 ;; Nested structure - maps to full assertive accounting format
 :structure {:has-quantity :quantity-param
             :is-denominated-in-unit {:is-denominated-in-monetary-unit :unit-param
                                      :is-denominated-in-physical-unit :physical-item-param}}

 ;; UI parameters
 :parameters {:quantity {:type :number :label "Quantity"}
              :unit {:type :dropdown :label "Unit type"
                     :options [...]}
              :physical-item {:type :dropdown :label "Item"
                              :conditional {:unit "physical-unit"}
                              :options [...]}}}

{:code :requires
 :label "Requires"
 :level 1
 :domain :forward-looking

 :sentence {:fragment "which requires"
            :pattern [:counterparty "to" :action :quantity :unit "by" :date]
            :section-break true  ; Start new line/section
            :section-label "This creates an obligation:"}

 ;; Contains a nested event
 :contains {:type :event
            :auto-identifier true  ; Generate UUID
            :allowed-assertions [:provides :receives :has-date]}

 :parameters {:date {:type :date :label "Due date"}}}

{:code :expects
 :label "Expects"
 :level 1
 :domain :forward-looking

 :sentence {:fragment "SP expects this with"
            :pattern [:confidence "confidence"]
            :section-break true
            :section-label "Expectation of fulfillment:"}

 ;; References another assertion via fulfills
 :references {:via :fulfills
              :target :requires  ; Auto-link to the requires assertion
              :auto-link true}   ; System handles linking

 :parameters {:confidence {:type :slider
                           :label "Confidence level"
                           :min 0 :max 1 :step 0.05
                           :default 0.95
                           :display :percentage}}}
```

## Transaction Type Examples

### Cash Purchase (Level 0)
Sentence: "On [date], SP provides [$500] [Cash] to [Vendor A] and receives [100] [Blank T-Shirts]."

Required assertions:
- has-date
- provides (monetary-unit)
- receives (physical-unit)
- has-counterparty

### Credit Sale (Level 1)
Sentence: "On [date], SP provides [10] [Printed T-Shirts] to [Customer B], which requires [Customer B] to provide [$250] [Cash] by [due date]. SP expects this with [95%] confidence."

Required assertions:
- has-date
- provides (physical-unit)
- has-counterparty
- requires (nested event with provides monetary-unit)
- expects (with confidence, auto-linked to requires)

### Production (Level 2)
Sentence: "On [date], SP consumes [5] [Blank T-Shirts] and [0.5] [Ink Cartridges] and [2] [hours labor], and creates [5] [Printed T-Shirts]. This is enabled by [T-Shirt Printer]."

Required assertions:
- has-date
- consumes (multiple)
- creates
- is-allowed-by

### Adjusting Entry - Bad Debt (Level 5)
Sentence: "On [date], SP reports [expense] calculated by [estimation of uncollectible amounts]."

Required assertions:
- has-date
- reports (category: expense, basis: estimation)

Note: Conceptually derived from confidence levels on outstanding receivables, but students select simplified reports assertion.

### Future: Reports with Calculation Assembly
For advanced levels, we may allow students to build the calculation:

Sentence: "SP reports [revenue] by collecting events where [SP provides physical-unit] and [SP receives monetary-unit], excluding events with [requires] or [expects], then summing [quantity received]."

This would introduce:
- collects
- includes/excludes patterns
- aggregation (sums)

## Implementation Plan

### Phase 1: Backend Structure
1. Define new assertion format with sentence templates
2. Update classification engine to work with nested structures
3. Create functions to:
   - Build nested structure from UI selections
   - Match nested structures against classification requirements
   - Auto-generate identifiers and link expects to requires

### Phase 2: Frontend Sentence Builder
1. Create `sentence-builder` component
   - Renders current sentence from selected assertions
   - Shows available assertions to add
   - Handles parameter input inline
2. Update state management for nested selections
3. Add section breaks for requires/expects

### Phase 3: Transaction Updates
1. Update credit sale/purchase templates
2. Verify classification matching works
3. Update tutorials to explain sentence-builder

### Phase 4: Reports Calculation (Future)
1. Design calculation assembly UI
2. Implement collects/includes/excludes for advanced levels
3. Create tutorials for calculation building

## Design Decisions

1. **Assertion order:** Flexible ordering allowed. The sentence-builder should gracefully handle different orderings without confusing students. Grammar adapts to order (e.g., "provides X to Y" vs "to Y provides X" → normalized rendering).

2. **Counterparty inheritance:** Yes - when `requires` involves the same party as the main event, auto-fill from the counterparty. This is expected for all current transaction types.

3. **Confidence with context:** No default value - students must think about it. Provide contextual data to inform their decision:

   > "**Customer B** has paid on time for 18 of 20 previous orders (90% historical rate).
   > Industry average for similar customers is 85%."
   >
   > Confidence: [________] %

   This teaches that confidence levels derive from real information (customer history, industry data), not arbitrary guesses. For bad debt calculation, these confidence levels have mathematical consequences.

4. **Multiple requires:** Yes - support adding assertions at will, including multiple `requires` for installment payments or complex obligations. Each `requires` creates a separate nested event with its own identifier, and each can have its own `expects` with different confidence levels.

## Open Questions (Remaining)

1. **Validation timing:** Validate as-you-type or on submit?

2. **Confidence data generation:** How sophisticated should the contextual data be? Options:
   - Simple: Random percentage for customer history
   - Medium: Customer "profiles" with consistent history across transactions
   - Rich: Industry benchmarks, aging of receivables, economic conditions
