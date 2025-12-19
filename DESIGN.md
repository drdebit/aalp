# AALP Design Document

This document captures design decisions, philosophy, and future plans for the Assertive Accounting Learning Platform.

## Core Philosophy

### Assertive Accounting in Education

The platform teaches accounting through **assertions** rather than memorized journal entries. Students learn to describe *what happened* in a transaction, and the accounting treatment emerges from those descriptions.

**Key insight:** Assertions precede classifications. Students select assertions that describe the economic reality, and the system shows how those assertions map to accounts and journal entries.

### Progressive Revelation

Students build understanding layer by layer. Earlier concepts get richer meaning as new assertions are introduced. This creates "aha" moments where prior transactions suddenly make more sense.

**Design principle:** Plant seeds early (through hints and descriptions), pay them off later (through explicit assertions).

---

## Level Progression

### Current Structure (as of 2025-12)

| Level | Focus | Key Assertions | Teaching Goal |
|-------|-------|----------------|---------------|
| 0 | Cash Purchases | provides, receives, has-counterparty, has-date | Basic exchange mechanics |
| 1 | Credit Purchases | + requires, expects, allows | Future obligations and expectations |
| 2 | Transformations | + consumes, creates, is-allowed-by | Internal value transformation |
| 3 | Sales & Recognition | + reports | Revenue/COGS recognition |
| 4 | Legal/Regulatory | + is-required-by, is-protected-by | Legal frameworks |

### Level Design Rationale

**Level 0 - Cash Purchases:**
- Simplest transactions: give cash, get asset
- Establishes provides/receives as core exchange pattern
- Physical-item selection hints at future purpose ("raw materials for production", "equipment enabling production")

**Level 1 - Credit Purchases:**
- Introduces time dimension: obligations and expectations
- `requires` = certain future obligation (accounts payable)
- `expects` = uncertain future receipt (accounts receivable)
- `allows` assertion exists but not actively used yet (see Progressive Revelation of Allows below)

**Level 2 - Transformations:**
- Internal events (no counterparty)
- `consumes` inputs, `creates` outputs
- Production transforms raw materials into finished goods
- Equipment enables production (`is-allowed-by` connects back to L0 purchases)

**Level 3 - Sales & Recognition:**
- Most complex exchange type
- Requires `reports` assertion for revenue and COGS recognition
- Two journal entries: revenue recognition + cost recognition
- Students must understand both physical exchange AND reporting

**Level 4 - Legal/Regulatory:**
- Overlay on other transactions
- `is-allowed-by` - legal frameworks enabling transactions
- `is-required-by` - legal frameworks mandating actions
- `is-protected-by` - legal frameworks protecting assets/rights

---

## Key Design Decisions

### Progressive Revelation of `allows` / `is-allowed-by`

**Problem:** The `allows` assertion captures capability (equipment allows production, inventory allows sales), but at Level 0 students don't know what production or sales are yet.

**Solution:** Progressive revelation with foreshadowing:

1. **L0 (Foreshadowing):** Physical-item descriptions hint at purpose
   - "Blank T-Shirts (raw materials for production)"
   - "T-shirt Printer (equipment enabling production)"

2. **L1:** `allows` assertion exists in forward-looking domain but isn't actively required

3. **L2 (Payoff):** Production transactions require `is-allowed-by` to reference equipment
   - Narrative: "This production is allowed by having the t-shirt printer you purchased earlier."
   - Students must select `is-allowed-by` with "T-shirt Printer" from dropdown
   - Creates the "aha" moment connecting L0 equipment purchase to its purpose

4. **L3 (Full picture):** Sales complete the cycle
   - Finished goods (from L2) allow sales (L3)
   - The full purchase → production → sale chain becomes clear

**Current Implementation (2025-12):**
- `is-allowed-by` assertion is in the transformation domain at Level 2
- Parameter: `capacity` (dropdown labeled "Enabled by")
- Options derive from equipment items in `physical-items`
- Production classifications (`production-direct`, `production-full`, etc.) require `is-allowed-by`

**Future:** When Level 4 is fleshed out, we can expand `is-allowed-by` options to include legal frameworks, or split into separate assertions if needed (Option 3).

**Implementation note:** Add brief text tutorials between levels explaining new assertions and how they connect to prior transactions.

### Dual Nature of `allows`

The `allows` assertion serves two purposes:
1. **Physical capacity:** Equipment allows production, inventory allows sales
2. **Legal capacity:** UCC allows commerce, state law allows LLC formation

Currently both use cases exist. Physical capacity is more relevant to intro accounting; legal capacity is Level 4 content.

### Revenue and COGS Recognition

**Problem:** Traditional double-entry records revenue at transaction time, but assertive accounting separates the physical event from reporting.

**Solution:** For a sale, students assert:
- `provides` (physical-unit, printed-tshirts) - physical goods leave
- `receives` (monetary-unit) - cash arrives
- `reports` (revenue, cash-received) - recognize revenue
- `reports` (expense, cost-of-goods) - recognize COGS

The `reports` assertion explicitly captures the recognition decision, making it visible rather than implicit.

**Journal entries for sales:**
1. Revenue Recognition: DR Cash, CR Revenue
2. Cost Recognition: DR Cost of Goods Sold, CR Finished Goods Inventory

### Single Source of Truth for Physical Items

All physical items (inventory, equipment, finished goods) are defined in `physical-items` map in classification.clj. This single definition controls:
- Dropdown options in the UI
- Account mappings
- Simulation pricing
- What can be provided vs received

**Design principle:** Define once, derive everywhere.

---

## Future Plans

### Text Tutorials Between Levels

Add brief instructional content when students advance to a new level:
- Explain new assertions available
- Connect to prior transactions ("Remember when you bought equipment? Now you'll see what it enables...")
- Set expectations for new transaction types

### Practice Mode as Guided Simulation

**Concept:** Practice mode could follow a set path (like simulation) but with unlimited variations at each step. This would:
- Establish cost history for COGS calculations
- Create natural progression through transaction types
- Allow "retcon" moments where earlier purchases gain meaning

**Current state:** Practice mode generates random problems at the selected level. This works for drilling but doesn't build a coherent business narrative.

### Capability/Allows at Earlier Levels

Consider whether `allows` should be introduced earlier with scaffolding:
- Optional at L0-1, required at L2+
- Good hints explain the purpose without requiring the assertion
- Helps distinguish inventory from equipment

**Open question:** How explicit should the capability concept be at L0?

---

## Open Questions

### Where Does `allows` Best Fit? (RESOLVED)

**Decision:** We use a unified approach:
- `allows` remains in forward-looking domain (L1) for capability creation
- `is-allowed-by` is in transformation domain (L2) for referencing prior capabilities
- When L4 legal content is added, we can either expand `is-allowed-by` options or create separate assertions

This creates a natural vocabulary pair: equipment purchases `allow` future production, and production `is-allowed-by` having that equipment.

### Service Transactions

Services were removed from physical-items since they're not "physical units." Service transactions would use:
- `provides` (effort-unit) - provide labor/effort
- `receives` (monetary-unit) - receive payment

**Open question:** Do we need service transaction templates? What level?

### Simulation vs Practice Mode Integration

Should practice mode become more simulation-like (building a coherent business)?
Or keep them separate (simulation = narrative, practice = drilling)?

### Assessment and Grading

How do we measure student understanding?
- Correct assertions on first try?
- Improvement over time?
- Ability to handle novel transactions?

### Equity Transactions

Equity transactions (capital contributions, stock issuance, dividends, etc.) need to be incorporated into the platform. These likely belong at Level 4 with Legal/Regulatory assertions since:

- LLC member contributions are `is-allowed-by` state business law
- Stock issuance may be `is-required-by` SEC regulations (for public companies)
- Dividend declarations follow corporate governance rules

**From the SP Example (example.clj):**

The research example shows equity issuance as:
```
equity-issuance:
  is-allowed-by: BusinessFormation-001 (the LLC formation event)
  has-counterparty: ParentMember
  receives: 20000 USD (cash from investor)
  provides: 20 ownership-percentage (membership interest)
  requires: Annual report to member (is-required-by BusinessFormation-001)
```

Key insights from the example:
- Equity issuance references the business formation event (not a generic "state law")
- Uses `ownership-percentage` as the unit type for membership interests
- The reporting obligation is tied to the business formation, not a separate requirement
- Entity `receives` cash and `provides` ownership (from entity's perspective)

**Implementation approach:**
- Add `ownership-unit` or `ownership-percentage` as a unit type
- Equity transactions `is-allowed-by` the business formation event
- May also create `requires` for ongoing reporting obligations

**Open questions:**
- Should business formation be a prerequisite transaction in simulation mode?
- How do we handle the chain: state law → business formation → equity issuance?
- Do we need to track member/shareholder identities for dividend distributions?

---

## Terminology Decisions

| Use This | Not This | Rationale |
|----------|----------|-----------|
| Physical Units | Goods/Services | More accurate for the unit type |
| Observation | Row | Dataset terminology |
| Attribute | Column | Dataset terminology |
| Reports | Recognizes | Assertive accounting language |

---

## References

- Main project documentation: `CLAUDE.md`
- Research implementation: `schema.clj`, `example.clj` (in research repo)
- Classification engine: `src/clj/assertive_app/classification.clj`
