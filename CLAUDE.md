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
- Database: Datomic (shared transactor with Accrue system)
- State Management: Reagent atoms
- Build: shadow-cljs with hot reloading

**Scope:**
- Introduction to Financial Accounting through Advanced Analytics
- Progressive assertion mastery across multiple courses
- Longitudinal learning analytics and research data collection

## Business Simulation Mode (NEW)

AALP now supports a **Business Simulation Mode** that transforms the platform from disposable practice exercises into a persistent business simulation game.

### Vision

Students manage "SP's t-shirt company" by:
- Building their own transaction ledger through correctly-classified transactions
- Selecting actions from available choices ("What should SP do next?")
- Retrying failed transactions until correct (authentic to real accounting)
- Facing realistic dependency constraints (can't sell inventory before purchasing it)
- Progressing through a time/move-based simulation with unlockable actions

### Key Differences from Practice Mode

| Aspect | Practice Mode | Simulation Mode |
|--------|--------------|-----------------|
| Problems | Disposable - new random problem each time | Persistent - must complete current transaction |
| State | No business state | Tracks cash, inventory, A/P, A/R |
| Ledger | No history | Personal transaction ledger |
| Wrong answers | Get feedback, move on | Must retry same transaction |
| Progression | Level-based unlocking | Level + business state prerequisites |

### Simulation Actions

**Level 0-1 (Purchases):**
- `purchase-materials-cash` - Buy raw materials for cash
- `purchase-materials-credit` - Buy raw materials on account
- `purchase-equipment-cash` - Buy equipment for cash
- `purchase-equipment-credit` - Buy equipment on account
- `pay-vendor` - Pay down accounts payable (when A/P exists)

**Level 2 (Production & Sales):**
- `produce-tshirts` - Create finished goods (requires materials + equipment)
- `sell-tshirts-cash` - Sell for cash (requires finished goods)
- `sell-tshirts-credit` - Sell on account (requires finished goods)
- `collect-receivable` - Collect accounts receivable (when A/R exists)

### Business State Tracking

The simulation tracks:
- **Cash**: Starting capital of $10,000
- **Raw Materials**: Units of blank t-shirts
- **Finished Goods**: Units of printed t-shirts
- **Equipment**: Set of owned equipment (e.g., t-shirt printer)
- **Accounts Payable**: Map of vendor -> amount owed
- **Accounts Receivable**: Map of customer -> amount owed
- **Simulation Date**: Current in-game date

### Action Prerequisites

Actions become available based on:
1. **User Level**: Must have unlocked the required level
2. **Cash**: Minimum cash for purchases (e.g., $100 for materials)
3. **Materials**: Raw materials needed for production
4. **Finished Goods**: Inventory needed for sales
5. **Equipment**: Specific equipment for production
6. **Obligations**: A/P or A/R must exist for payment/collection

### Retry Until Correct

When a student submits an incorrect classification:
1. Feedback shows what was wrong
2. The same transaction remains pending
3. Attempt count increments
4. Student must try again with same transaction
5. Only correct classification advances the business

### Personal Ledger

Correctly-classified transactions are recorded in the student's ledger with:
- Transaction date
- Action type
- Narrative
- Variables used
- Assertion selections
- Resulting journal entry

### Implementation Files

**Backend:**
- `simulation.clj` - Business simulation engine
  - Action definitions with prerequisites and effects
  - Business state management
  - Transaction generation
  - Ledger persistence

**Frontend:**
- `state.cljs` - Added simulation state and mode toggle
- `api.cljs` - Added simulation API functions
- `views.cljs` - Added business dashboard, action panel, ledger view

**Database (schema.clj):**
- `:business-state/*` - Business state entity
- `:pending-tx/*` - Pending transaction entity
- `:ledger-entry/*` - Ledger entry entity

### API Endpoints

```
GET  /api/simulation/state          - Get business state and available actions
POST /api/simulation/start-action   - Start a new action
POST /api/simulation/classify       - Submit classification (retry if wrong)
GET  /api/simulation/ledger         - Get transaction history
POST /api/simulation/reset          - Reset simulation to initial state
POST /api/simulation/advance-period - Advance to next period
```

## Development Setup

### Starting the Services

```bash
# From aalp/ directory:

# 1. Start the backend (requires Datomic transactor to be running)
./start-backend.sh > /tmp/aalp.log 2>&1 &

# Or restart (kills existing processes first)
./restart-backend.sh > /tmp/aalp.log 2>&1 &

# 2. Start the frontend with hot reloading
npx shadow-cljs watch app
```

**CRITICAL: Always use `start-backend.sh` or `restart-backend.sh` to start the backend.**

Do NOT start the backend manually with:
```bash
# WRONG - shell escaping issues with ! character cause URISyntaxException
DATOMIC_DB_PASSWORD='ms&MWh@!8@70' clojure -M -m assertive-app.server
```

The `!` character triggers shell history expansion in some contexts, causing the password to be mangled (e.g., `\%21` instead of `%21`). The `start-backend.sh` script properly handles this.

### Development Ports

| Service | Port | URL |
|---------|------|-----|
| Backend API | 3000 | http://localhost:3000/api |
| Frontend (shadow-cljs) | 8081 | http://localhost:8081 |
| shadow-cljs dashboard | 9630 | http://localhost:9630 |
| nREPL | 7888 | For Emacs/CIDER connection |

### Remote Access (via nginx)

- **AALP App:** http://arcweb01.rs.gsu.edu/aalp/
- **API:** http://arcweb01.rs.gsu.edu/aalp/api/
- **WebSocket (hot reload):** ws://arcweb01.rs.gsu.edu/aalp-ws/

### Known Issues & Fixes

#### nginx IPv6 Resolution Issue
nginx may resolve `localhost` to IPv6 (`[::1]`) but the backend only listens on IPv4. The nginx config in `/etc/nginx/nginx.conf` uses explicit `127.0.0.1` for AALP routes:
```nginx
location /aalp/api/ {
    proxy_pass http://127.0.0.1:3000/api/;  # NOT localhost!
    ...
}
```

#### Database Password URL Encoding
The Datomic password contains special characters (`ms&MWh@!8@70`). In `schema.clj`, these are URL-encoded:
- `&` → `%26`
- `@` → `%40`
- `!` → `%21`

If you see `URISyntaxException` errors about illegal characters, check that all special characters are properly encoded.

#### Backend Route Handler Pattern
**AVOID** this pattern - it returns `nil` for authenticated users:
```clojure
;; WRONG - two separate when expressions
(when-let [user (:user request)]
  (response/response {...}))
(when-not (:user request)
  (response/response {...}))
```

**USE** `if-let` instead:
```clojure
;; CORRECT - single if-let/else
(if-let [user (:user request)]
  (response/response {...with-progress...})
  (response/response {...without-progress...}))
```

#### Frontend JSON Key Lookup
When cljs-ajax parses JSON with `keywords? true`, numeric string keys like `"0"` become keywords like `:0`. Use `(keyword (str level))` for lookups:
```clojure
;; WRONG
(get-in progress [:level-stats level] {})        ; level is integer
(get-in progress [:level-stats (str level)] {})  ; string doesn't match

;; CORRECT
(get-in progress [:level-stats (keyword (str level))] {})  ; :0, :1, etc.
```

#### Session Token Testing Warning
**Do not test the `/api/login` endpoint with a real user's email** - it replaces their session token and invalidates their browser session. Use a test email like `test@test.com` instead.

### Memory Constraints

The server has limited RAM (~4GB) with no swap. When running multiple Java processes (Datomic transactor, Accrue backend, AALP backend, shadow-cljs), memory can become constrained.

**Diagnostic commands:**
```bash
free -m                           # Check available memory
ps aux --sort=-%mem | head -10    # Top memory consumers
pgrep -f "java" | xargs ps -o pid,rss,cmd -p  # Java process memory
```

**If backend won't start:**
1. Check if another instance is already running: `pgrep -f assertive`
2. Check memory availability: `free -m`
3. Kill orphan processes if needed: `pkill -f assertive`

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

### Data Schema (Datomic)

The platform uses Datomic for persistence, shared with the Accrue system.

**User Entity:**
- `:user/id` - UUID (unique identity)
- `:user/email` - Email address (unique identity)
- `:user/current-level` - Current level (0-based)
- `:user/unlocked-levels` - EDN string of unlocked level set
- `:user/level-stats` - EDN string of per-level statistics
- `:user/session-token` - Current session token (UUID)
- `:user/problem-history` - EDN string of attempt history

**Business State Entity (Simulation Mode):**
- `:business-state/user` - Reference to user (unique identity)
- `:business-state/cash` - BigDecimal cash balance
- `:business-state/raw-materials` - Long units count
- `:business-state/finished-goods` - Long units count
- `:business-state/equipment` - EDN string of equipment set
- `:business-state/accounts-payable` - EDN string map (vendor -> amount)
- `:business-state/accounts-receivable` - EDN string map (customer -> amount)
- `:business-state/simulation-date` - String date in simulation

**Pending Transaction Entity:**
- `:pending-tx/user` - Reference to user (unique identity)
- `:pending-tx/action-type` - Keyword action type
- `:pending-tx/narrative` - String transaction narrative
- `:pending-tx/variables` - EDN string of variable values
- `:pending-tx/correct-assertions` - EDN string of correct assertions
- `:pending-tx/attempts` - Long attempt count
- `:pending-tx/template-key` - Keyword template reference
- `:pending-tx/problem-id` - UUID for tracking

**Ledger Entry Entity:**
- `:ledger-entry/id` - UUID (unique identity)
- `:ledger-entry/user` - Reference to user
- `:ledger-entry/date` - String in-simulation date
- `:ledger-entry/action-type` - Keyword action type
- `:ledger-entry/narrative` - String transaction narrative
- `:ledger-entry/variables` - EDN string of variable values
- `:ledger-entry/assertions` - EDN string of assertion selections
- `:ledger-entry/journal-entry` - EDN string (debit, credit, amount)
- `:ledger-entry/template-key` - Keyword template reference

**Note:** Complex data types (maps, sets) are stored as EDN strings due to Datomic's type system.

**Future Analytics Tables (Planned):**

**transaction_datasets**:
- id, name, description, course_id
- entity_context, period_start, period_end
- transaction_count

**transactions** (for analytics courses):
- id, dataset_id, date, narrative
- entity, amount
- assertions (array of UUIDs)
- accounts_affected (bridge to traditional)

**analytics_assignments**:
- id, course_id, dataset_id
- objective, required_assertions
- query_template, due_date

**analytics_submissions**:
- id, assignment_id, user_id
- query (student's SQL), results
- assertion_filters, submitted_at, grade

### Implementation Phases

**Phase 1 - Core Platform (COMPLETE):**
- [x] Core classification engine and assertion matching logic
- [x] Three-column UI with Reagent
- [x] 3 assertion levels (0, 1, 2)
- [x] User authentication with email and session tokens
- [x] Progress tracking with level unlocking
- [x] Datomic database integration
- [x] Problem templates with variable substitution
- [x] Hints system for incorrect answers

**Phase 1.5 - Business Simulation (COMPLETE):**
- [x] Business simulation mode with action selection
- [x] Personal transaction ledger
- [x] Business state tracking (cash, inventory, A/P, A/R)
- [x] Action prerequisites based on business state
- [x] Retry-until-correct for pending transactions
- [x] Mode toggle between Practice and Simulation

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

**Phase 2 - Simulation Enhancements (In Progress):**
- [ ] Period advancement with automatic events
- [ ] Production constraints and recipes
- [ ] More sophisticated action prerequisites
- [ ] Multiple assertion levels (3+)
- [ ] Branching progression paths
- [ ] Instructor dashboard for diagnostics

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
