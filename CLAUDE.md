# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with the Assertive Accounting Learning Platform (AALP).

## Project Overview

This is the **Assertive Accounting Learning Platform (AALP)** - a curriculum-spanning educational web application that teaches accounting through logical assertions rather than traditional classification-based approaches.

### What is Assertive Accounting?

Assertive accounting represents a paradigm shift in how business events are recorded:

**Evolution:**
1. **Double-entry**: Uses predetermined accounts (Assets, Liabilities, Revenue, etc.)
2. **REA**: Uses predetermined entity types (Resources, Events, Agents)
3. **Assertive accounting**: Uses predetermined assertion vocabularies with emergent type structures

**Key insight:** Instead of forcing events into predetermined categories, assertive accounting describes events through composable logical assertions like `provides`, `receives`, `expects`, `allows`, `requires`, `consumes`, and `creates`. Classifications emerge naturally from patterns of assertions.

**Core principles:**
- **Temporal**: Record past AND future (expectations, requirements, permissions)
- **Ontological**: Properties precede categories; categories emerge from patterns
- **Epistemological**: Truth-preservation over balance-maintenance
- **Representational**: Articulation over classification
- **Scope**: All decision-relevant dimensions, not just economic

## How Assertive Accounting Works: The SP Example

The research behind this platform demonstrates assertive accounting through **SP's t-shirt company** - a comprehensive example that illustrates how assertion chains work in practice.

SP starts a t-shirt printing business that purchases blank t-shirts, prints them with custom designs, and sells them. The example demonstrates how assertive accounting handles:
- Business formation and legal compliance
- Equipment and inventory acquisition
- Intellectual property creation
- Production and sales
- Financial and environmental reporting

### Core Assertion Types

**Fundamental assertions:**
- `event` - Indicates a recordable event
- `has-identifier` - Unique identifier for the event
- `date` - When the event occurred
- `is-asserted-by` - Entity making the assertion
- `has-counterparty` - Other party to the transaction

**Economic exchange assertions:**
- `provides` - What the entity gives in an exchange
- `receives` - What the entity gets in an exchange
- `consumes` - Internal transformation (inputs)
- `creates` - Internal transformation (outputs)

**Forward-looking assertions:**
- `expects` - Anticipated future event (with confidence level 0-1)
- `requires` - Obligated future event
- `allows` - Permitted future event

**Legal/regulatory assertions:**
- `is-allowed-by` - References enabling legal framework
- `is-required-by` - References mandating legal framework
- `is-protected-by` - References protective legal framework
- `reference` - Specific code section or standard

**Modification assertions:**
- `modifies` / `fulfills` - Links actual event to prior expectation/requirement
- `is-restricted-by` - Limitations (quantity, date, etc.)

**Reporting assertions:**
- `collects` / `includes` / `excludes` - Event-based queries
- `reports` - Calculated/aggregated result
- `sums`, `multiplies`, `divides` - Arithmetic operations

### Hierarchical Structure

Assertions are **nested hierarchically** to create composable semantic chains:

```
event
├── has-identifier = EquipmentPurchase-001
├── provides
│   ├── has-quantity = 12000
│   └── is-denominated-in-unit
│       └── is-denominated-in-monetary-unit = USD
└── receives
    ├── has-quantity = 1
    └── is-denominated-in-unit
        └── is-denominated-in-physical-unit = t-shirt-printer
```

**Key insight:** Nesting creates queryable dimensions from general (unit) → specific (monetary-unit) → concrete (USD). This allows queries at any level of granularity.

### Multi-dimensional Event Recording

A single equipment purchase event captures multiple dimensions simultaneously:

1. **The exchange itself** (`provides` USD, `receives` printer)
2. **Legal context** (`is-allowed-by` State-UCC)
3. **Future capabilities** (`allows` future events to `create` printed shirts)
4. **Production recipe** (what it `consumes`: blank shirts, ink, time, effort)
5. **Constraints** (`is-restricted-by` 10,000 shirt limit)
6. **Obligations** (`requires` monthly maintenance)

**Paradigm shift:** One event participates in multiple causal chains simultaneously without forcing categorical choice.

### Forward-Looking Information

**Expectations with confidence:**
```
expects
├── has-confidence-level = 0.5
├── has-identifier = ExpectedSale-001
└── event
    └── includes
        └── receives
            └── has-quantity = 25
```

**Later fulfillment:**
```
event
├── has-identifier = Sale-001
├── modifies
│   └── fulfills = ExpectedSale-001
```

Creates **traceable chains** from expectation → fulfillment, enabling variance analysis natively in the accounting system.

### Legal Frameworks as First-Class Entities

Laws and standards are **recorded as events**, not external references:

```
event
├── has-identifier = State-UCC
├── is-asserted-by = State Legislature
├── reference = State Code Title 11
└── allows
    └── event
        └── includes
            ├── provides = is-denominated-in-unit
            └── receives = is-denominated-in-unit
```

Later events reference this: `is-allowed-by = State-UCC`

This makes regulatory frameworks **queryable** and **traceable**.

### Calculations as Events

Revenue calculation is **recorded, not just performed**:

```
event
├── has-identifier = AccrualRevenue
├── is-allowed-by = ASC-606
├── collects
│   ├── includes = [events matching pattern]
│   └── excludes = [future-oriented events]
└── reports
    └── has-quantity
        └── sums = quantity
```

**Critical insight:** Calculation logic is **preserved with provenance**, making it:
- Auditable (trace back to ASC-606)
- Reconstructable (see exact inclusion/exclusion criteria)
- Comparable (multiple calculations can coexist)

## Reference Implementation Files

The research includes working Clojure code implementing the assertive accounting system. These files serve as reference material for the AALP application (located elsewhere in the research repository, not in this `aalp/` directory).

### Schema Definition (`schema.clj`)

Defines the formal **DAG (Directed Acyclic Graph)** structure of the assertion vocabulary using the Loom graph library.

**Key components:**
1. **Event Context** - `has-identifier`, `date`, `is-asserted-by`, `reference`
2. **Event Relationships** - `is-allowed-by`, `allows`, `requires`, `expects`, etc.
3. **Counterparties and Exchanges** - `has-counterparty`, `provides`, `receives`
4. **Resource Quantities** - `has-quantity`, `is-denominated-in-unit`
5. **Denomination Hierarchies** - `is-denominated-in-monetary-unit`, `is-denominated-in-physical-unit`, etc.
6. **Forward-looking Assertions** - `expects` with `has-confidence-level` (0.0-1.0)
7. **State Modifications** - `modifies`, `fulfills`

**Purpose:** Machine-readable specification of valid assertion vocabulary structure.

### SP Example Implementation (`example.clj`)

Complete implementation of SP's t-shirt company as Clojure data structures (maps and vectors). Includes the full event sequence:

1. **Business Formation** - UCC, LLC statute, business filing, employee hire
2. **Membership Issuance** - Equity investment with reporting requirement
3. **Equipment Acquisition** - Printer purchase with capabilities, restrictions, obligations
4. **Inventory Acquisition** - Vendor relationships, contracts, purchases
5. **IP Creation** - Copyright law, design creation
6. **Production and Sales** - Printing shirts, sales, recurring agreements
7. **Performance Reporting** - ASC-606 revenue calculation, annual report
8. **Tax Reporting** - IRS requirements, IRC §451, cash basis revenue
9. **Environmental Reporting** - Emissions calculations, customer reports

**Data structure pattern:**
```clojure
{:event
 {:has-identifier "EventID"
  :date "YYYY-MM-DD"
  :is-asserted-by "Entity"
  :provides {:has-quantity 100
             :is-denominated-in-unit {:is-denominated-in-monetary-unit :USD}}
  :receives {:has-quantity 1
             :is-denominated-in-unit {:is-denominated-in-physical-unit :printer}}}}
```

## Educational Platform Implementation

**Location:** This `aalp/` directory contains the web application implementation.

**Getting Started:** See `README-app.md` for installation and development instructions.

### Platform Overview

**Technology Stack:**
- Frontend: ClojureScript + Reagent (React wrapper) + shadow-cljs
- Backend: Clojure + Ring + Compojure
- Database: PostgreSQL (JSON support, full-text search)
- State Management: Reagent atoms (pilot), re-frame (production)
- Analytics: Vega/Vega-Lite for visualizations

**Scope:**
- Introduction to Financial Accounting through Advanced Analytics
- Progressive assertion mastery across multiple courses
- Longitudinal learning analytics and research data collection

### Core Pedagogical Model

**Transaction → Assertions → Classification Flow:**

1. **Student reads transaction narrative** (e.g., "SP purchases equipment for $5,000, agreeing to pay in 30 days")
2. **Student selects relevant assertions** from available set
   - Examples: `:asset-existence`, `:asset-control`, `:liability-obligation-present`
   - Assertions grouped by domain (Existence, Control, Temporal, Recognition, etc.)
3. **System provides real-time feedback**:
   - Correct: Shows classification and journal entry
   - Incorrect: Shows nearest classification match with hints
   - Highlights which assertions differ from correct answer
4. **Students drill down** on financial statements to see underlying transactions and assertions

**Key insight:** Students learn that assertions → classifications → journal entries, reversing the traditional "memorize the journal entry" approach.

### Educational Assertion Categories

These are simplified versions of the research assertions, adapted for pedagogy:

**Existence & Control:**
- `asset-existence` - Resource exists at a point in time
- `asset-control` - Entity controls the resource
- `consideration-given` - Something was provided in exchange

**Temporal:**
- `temporal-present` - Event has occurred
- `temporal-future` - Event will occur (obligation)
- `temporal-future-uncertain` - Event may occur (expectation)

**Recognition:**
- `performance-obligation-satisfied` - Delivery/service completed
- `revenue-earned` - Revenue recognition criteria met
- `benefit-consumed` - Asset consumed in operations

**Measurement:**
- `historical-cost` - Original transaction price
- `fair-value` - Current market value
- `impairment` - Value decline below carrying amount
- `estimation-required` - Requires judgment/estimation

**Advanced (Consolidation):**
- `control-relationship` - One entity controls another
- `variable-interest` - Economic interest without voting control

**Managerial:**
- `variable-cost` - Cost varies with activity
- `fixed-cost` - Cost remains constant

### Progressive Unlocking System

**Anchor Problems + Generated Variations:**
- Hand-crafted "anchor" problems introduce each assertion level
- System-generated variations provide practice (randomized amounts/entities, same assertion structure)
- 5 successes required to unlock next level (within 2 attempts)

**Branching Progression:**
```
Level 0 (Basic Existence & Control)
    ├─> Temporal Branch (future obligations, expectations)
    ├─> Recognition Branch (revenue, expense recognition)
    └─> Convergence (requires mastery of both branches)
        └─> Advanced levels (measurement, consolidation)
```

**Unlock Conditions:**
- Standard: 5 problems correct within first 2 attempts
- Alternative strict mode: 5 correct on first attempt only
- Diagnostic checks before convergence levels
- Students can revisit earlier problems with newly unlocked assertions

### UI Design (Three-Column Layout)

**Left Column - Transaction Narrative:**
- Scrollable text describing the business event
- Students can refer back while selecting assertions
- May include relevant context (dates, amounts, parties)

**Middle Column - Assertion Selection:**
- Grouped by category (collapsible sections):
  - Existence & Control
  - Temporal
  - Recognition
  - Measurement
  - etc.
- Visual indicators: selected, available, locked
- Shows assertion descriptions on hover

**Right Column - Real-time Feedback:**
- Current classification based on selected assertions
- States:
  - "Need more information to classify"
  - "Current assertions suggest: [Classification]"
  - "Contradictory assertions detected"
- After submission: detailed feedback with hints

### Classification Engine

**Core matching logic:**
```clojure
(def classifications
  {:asset-purchase
   {:required #{:asset-existence :asset-control :consideration-given}
    :prohibited #{:revenue-earned}
    :description "Acquisition of asset for consideration"
    :journal-entry [{:debit "Asset" :credit "Cash/Payable"}]}

   :expense
   {:required #{:benefit-consumed :consideration-given}
    :prohibited #{:asset-control-future}
    :description "Consumption of benefits in current period"
    :journal-entry [{:debit "Expense" :credit "Cash/Payable"}]}})

(defn match-classification [student-assertions]
  (let [matches (for [[class-key {:keys [required prohibited]}] classifications
                      :when (and (set/subset? required student-assertions)
                                 (empty? (set/intersection prohibited student-assertions)))]
                  class-key)]
    matches))
```

**Distance calculation for hints:**
1. Exact matching first
2. Calculate minimal edit distance: `|missing required| + |incorrectly included|`
3. Generate directed hints:
   - "Your assertions would classify this as [X]. Does that seem right?"
   - "You're missing an assertion about [category]. Does this transaction create an obligation?"
   - "You included [assertion Y]. Re-read - is that actually present?"

### Problem Generation

**Template-based approach:**
```clojure
(def transaction-templates
  {:credit-purchase
   {:narrative-template "SP purchases {asset-type} for ${amount}, agreeing to pay in {days} days"
    :required-assertions #{:asset-existence :asset-control :liability-obligation-present}
    :unlocked-at-level 2
    :variables {:asset-type ["equipment" "inventory" "supplies"]
                :amount (range 1000 50000 1000)
                :days [30 60 90]}}})

(defn generate-problem [template]
  (let [vars (into {} (for [[k options] (:variables template)]
                        [k (rand-nth options)]))
        narrative (apply-template (:narrative-template template) vars)]
    {:narrative narrative
     :correct-assertions (:required-assertions template)
     :variables vars}))
```

**Problem types:**
- **Anchor problems**: Hand-crafted, pedagogically designed
- **Generated variations**: Same assertion structure, randomized details
- **Transfer problems**: Novel contexts requiring assertion application

### Data Schema

**Core tables:**

**users**: id, email, name, institution, role

**courses**: id, code, name, level, institution, term

**enrollments**: id, user_id, course_id, enrolled_at

**assertions**:
- id, code (keyword), domain, level
- natural_language (display text)
- formal_notation (optional)
- introduced_in_course, version

**transaction_templates**:
- id, type (anchor/variation), course_level, branch
- narrative_template, variables (jsonb)
- correct_assertions (array of UUIDs)
- difficulty, prerequisites (array)

**problems** (generated instances):
- id, template_id, narrative
- variables (jsonb - actual values)
- correct_assertions (array)
- course_id, assigned_at

**student_progress**:
- id, user_id, course_id
- unlocked_assertions (array), unlocked_branches (array)
- completed_branches (array), current_level

**problem_attempts**:
- id, user_id, problem_id
- selected_assertions (array)
- correct (boolean)
- attempt_number, time_spent_seconds
- started_at, completed_at, hint_used (boolean)

**Analytics extension tables:**

**transaction_datasets**:
- id, name, description, course_id
- entity_context (jsonb), period_start, period_end
- transaction_count

**transactions** (for analytics courses):
- id, dataset_id, date, narrative
- entity, amount
- assertions (array of UUIDs)
- accounts_affected (jsonb - bridge to traditional)
- metadata (jsonb)

**analytics_assignments**:
- id, course_id, dataset_id
- objective (text), required_assertions (array)
- query_template, due_date

**analytics_submissions**:
- id, assignment_id, user_id
- query (student's SQL), results (jsonb)
- assertion_filters (array), submitted_at, grade

### Implementation Phases

**Phase 1 - Pilot (20-24 hours development time):**
- Scope: Introduction to Financial Accounting, single section
- Core classification engine and assertion matching logic
- Three-column UI with Reagent
- 3 assertion levels, 2 branches, 15-18 problems total
- Simple data logging (flat files or basic PostgreSQL)
- Focus: Pedagogical validation and user feedback

**Pilot structure:**
1. Transaction 1: Pure tutorial with guidance
2. Transactions 2-3: Scaffolded practice
3. Transaction 4: First challenge (credit vs. prepaid)
4. Transaction 5: Transfer test (novel situation)

**Data collection during pilot:**
- Assertions selected (including order)
- Time spent per transaction
- Attempt count before correct
- Post-pilot survey (1-5 scale + open-ended)

**Phase 2 - Multi-Course Expansion:**
- Multiple intro sections
- Intermediate course integration
- Expanded assertion library (measurement, disclosure)
- Longitudinal tracking across courses
- Instructor dashboard for diagnostics

**Phase 3 - Analytics Integration:**
- Transaction datasets (hundreds/thousands of transactions)
- SQL query interface for students
- Pattern analysis tools
- Assertion-based filtering and analysis
- Example: "Find all revenue transactions with no estimation involved"

**Phase 4 - Research Platform:**
- Public anonymized datasets
- API for external researchers
- Cross-institution comparisons
- LMS integration (Canvas, Blackboard)
- Publication-ready analytics

### Extension to Advanced Courses

**Financial Statement Analysis:**
- Students drill down on account balances to view transactions and assertions
- Filter by assertion patterns (`:estimation-required`, `:related-party`, etc.)
- Compare assertion patterns across periods/entities
- Calculate adjusted ratios excluding high-judgment items
- Make "accounting quality" visible and operational

**Data Analytics:**
- Assertions become queryable attributes for large-scale analysis
- Query transactions by logical properties, not just account classifications
- Anomaly detection (unusual assertion combinations)
- Predictive modeling using assertions as features
- Test accounting quality hypotheses at scale

**Example analytics query:**
```sql
SELECT * FROM transactions
WHERE assertions @> ARRAY['revenue-earned']::uuid[]
  AND NOT assertions && ARRAY['estimation-required', 'fair-value']::uuid[]
```
(Revenue transactions with no estimation or fair value measurement)

### Design Principles

1. **Assertions as structured, queryable attributes** - Not hidden logic
2. **Progressive disclosure** - Unlock assertions gradually to prevent overwhelm
3. **Mistakes as learning opportunities** - Helpful hints, not just "wrong"
4. **Mastery over speed** - Require demonstration of understanding to progress
5. **Student agency** - Branching paths, choice in learning sequence
6. **Curriculum-spanning** - Single framework from intro through analytics
7. **Bridge to traditional** - Preserve connection to double-entry for validation
8. **Extensibility** - Clean separation of concerns, data-driven configuration

### Pedagogical Goals

**Immediate (Pilot):**
- Test whether assertion reasoning is intuitive to students
- Validate that progressive unlocking creates effective scaffolding
- Gather evidence on common misconceptions
- Provide instructors with diagnostic data

**Medium-term (Multi-course):**
- Demonstrate stronger conceptual understanding vs. traditional instruction
- Show better transfer to novel transaction types
- Track which assertion types students master easily vs. struggle with
- Build longitudinal learning analytics

**Long-term (Curriculum-wide):**
- Establish assertive accounting as viable pedagogical framework
- Provide empirical evidence for framework superiority
- Create reference implementation for practitioners/regulators
- Enable assertion-based financial statement analysis and data analytics

### Research Context

The platform serves multiple purposes:
1. **Demonstration**: Concrete proof that assertive accounting can work in practice
2. **Validation**: Empirical evidence for pedagogical effectiveness
3. **Implementation reference**: Shows how assertion vocabulary translates to educational context
4. **Research tool**: Generates data for studying accounting education and cognition

**Connection to SP example:**
- SP example (in the research paper) demonstrates theoretical completeness - can represent complex business scenarios
- Educational platform demonstrates pedagogical effectiveness - can teach accounting concepts
- Together they provide both technical and educational validation of the assertive accounting framework
