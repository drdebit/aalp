# Assertive Accounting Learning Platform

Educational web application that teaches accounting through logical assertions rather than traditional classification-based approaches.

## Project Structure

This application is located in the `aalp/` directory within the assertive accounting research repository.

```
aalp/
├── deps.edn                 # Clojure dependencies
├── shadow-cljs.edn          # ClojureScript build configuration
├── package.json             # NPM dependencies (React, shadow-cljs)
├── start-backend.sh         # Backend startup script (handles password escaping)
├── restart-backend.sh       # Restart script (kills existing, then starts)
├── src/
│   ├── clj/                 # Backend Clojure code
│   │   └── assertive_app/
│   │       ├── server.clj           # Ring server, API routes
│   │       ├── schema.clj           # Datomic database schema
│   │       ├── classification.clj   # Classification engine & templates
│   │       └── simulation.clj       # Business simulation engine (NEW)
│   └── cljs/                # Frontend ClojureScript code
│       └── assertive_app/
│           ├── core.cljs      # Entry point
│           ├── state.cljs     # Application state (Reagent atoms)
│           ├── api.cljs       # Backend API client
│           └── views.cljs     # UI components
└── resources/
    └── public/
        ├── index.html       # HTML entry point
        └── css/
            └── style.css    # Styles
```

## Technology Stack

- **Frontend**: ClojureScript + Reagent (React wrapper)
- **Backend**: Clojure + Ring + Compojure
- **Database**: Datomic (shared transactor with Accrue system)
- **Build**: shadow-cljs (ClojureScript compilation)

## Getting Started

### Prerequisites

- Java 11+ (for Clojure)
- Node.js and npm (for shadow-cljs)
- Clojure CLI tools
- Datomic transactor running (shared with Accrue system)

### Installation

1. Navigate to the application directory:
   ```bash
   cd aalp
   ```

2. Install npm dependencies:
   ```bash
   npm install
   ```

3. Install Clojure dependencies (handled automatically by Clojure CLI)

### Development

Run the frontend and backend in separate terminals (from within the `aalp/` directory):

**Terminal 1 - Backend (Clojure API server):**
```bash
./start-backend.sh > /tmp/aalp.log 2>&1 &
# Or to restart (kills existing first):
./restart-backend.sh > /tmp/aalp.log 2>&1 &
```

**IMPORTANT:** Always use the startup scripts. Do NOT run `clojure -M -m assertive-app.server` directly - the password contains special characters that cause shell escaping issues.

**Terminal 2 - Frontend (ClojureScript):**
```bash
npx shadow-cljs watch app
```

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

### Production Build

```bash
npm run release
```

This creates an optimized production build in `resources/public/js/`

## Application Modes

AALP supports two modes of operation:

### Practice Mode (Default)

Traditional practice with generated problems:
- Problems are disposable - correct or not, get a new random problem
- Progress tracked by level and success rate
- No persistent business state
- Good for drilling specific transaction types

### Simulation Mode (NEW)

Persistent business simulation where students build their own transaction ledger:
- Students manage "SP's t-shirt company"
- Select actions from available choices ("What should SP do next?")
- Must retry failed transactions until correct
- Actions unlock based on business state and user level
- Ledger persists across sessions

**Simulation Features:**
- **Personal Ledger**: Correctly-classified transactions become part of your business history
- **Dependency Constraints**: Can't sell inventory before purchasing it
- **Action Prerequisites**: Equipment required for production, materials required for goods
- **Period-based Progression**: Business operates in time periods with moves per period

## Current Features

### Core Functionality
- **Transaction presentation**: Display business event narratives
- **Assertion selection**: Students select relevant assertions
- **Classification engine**: Matches assertions to transaction types
- **Real-time feedback**: Shows current classification state
- **Hints system**: Provides directed feedback on incorrect answers
- **Problem generation**: Creates variations of transaction templates
- **User authentication**: Email-based login with session persistence
- **Progress tracking**: Unlockable levels, per-level statistics

### Assertion Types (Levels 0-2)

**Level 0 - Basic:**
- Asset Existence
- Asset Control
- Consideration Given
- Liability Existence (Present Obligation)

**Level 1 - Temporal:**
- Has Date (temporal anchor)
- Has Counterparty

**Level 2 - Production & Sales:**
- Raw Material Consumption
- Finished Goods Creation
- Revenue Recognition

### Classification Types

Current transaction types:
- Cash inventory purchase
- Credit inventory purchase (creates A/P)
- Cash equipment purchase
- Credit equipment purchase
- Production (consumes materials, creates finished goods)
- Cash sales
- Credit sales (creates A/R)
- Vendor payment (pays down A/P)
- Customer collection (collects A/R)

### Business Simulation Actions

**Level 0-1 (Purchases):**
- Purchase Raw Materials (Cash)
- Purchase Raw Materials (Credit)
- Purchase Equipment (Cash)
- Purchase Equipment (Credit)
- Pay Vendor (when A/P exists)

**Level 2 (Production & Sales):**
- Produce T-Shirts (requires materials + equipment)
- Sell T-Shirts (Cash)
- Sell T-Shirts (Credit)
- Collect from Customer (when A/R exists)

## Architecture

### Backend (`src/clj/`)

**server.clj**: Ring HTTP server with API endpoints

Practice Mode:
- `GET /api/assertions` - Returns available assertions for level
- `POST /api/classify` - Classifies based on selected assertions
- `POST /api/generate-problem` - Generates problem at specified level
- `POST /api/login` - Email-based authentication
- `GET /api/progress` - Get user's progress and stats
- `POST /api/validate-je` - Validate constructed journal entry

Simulation Mode:
- `GET /api/simulation/state` - Get business state and available actions
- `POST /api/simulation/start-action` - Start a new action (generates transaction)
- `POST /api/simulation/classify` - Submit classification (must be correct to proceed)
- `GET /api/simulation/ledger` - Get transaction history
- `POST /api/simulation/reset` - Reset simulation to initial state
- `POST /api/simulation/advance-period` - Advance to next period

**schema.clj**: Datomic database schema
- User entities with email, progress tracking
- Business state (cash, inventory, A/P, A/R)
- Pending transactions
- Ledger entries

**classification.clj**: Core logic
- Assertion definitions with levels and domains
- Classification rules (required/prohibited assertions)
- Matching algorithm with distance calculation
- Template-based problem generation with variable substitution

**simulation.clj**: Business simulation engine (NEW)
- Action definitions with prerequisites and effects
- Business state management
- Transaction generation from templates
- Ledger persistence
- Dependency checking (materials, equipment, etc.)

### Frontend (`src/cljs/`)

**core.cljs**: Entry point, initialization, session restoration

**state.cljs**: Global state management using Reagent atoms
- Authentication state (user, session token)
- Practice mode state (problem, assertions, feedback)
- Simulation mode state (business state, pending tx, ledger)
- App mode toggle (:practice / :simulation)

**api.cljs**: API client
- Authentication (login, logout, session restore)
- Practice mode (fetch assertions, generate problems, submit answers)
- Simulation mode (fetch state, start actions, submit, ledger)

**views.cljs**: UI components
- Mode toggle (Practice / Simulation)
- Practice mode: three-column layout (narrative, assertions, feedback)
- Simulation mode: business dashboard, action panel, ledger view
- Level selector, problem type selector
- Progress display with stats

## Data Model

### Datomic Entities

**User:**
- `:user/id` - UUID
- `:user/email` - Email address
- `:user/current-level` - Current level (0-based)
- `:user/unlocked-levels` - Set of unlocked levels
- `:user/level-stats` - EDN map of per-level statistics

**Business State:**
- `:business-state/user` - Reference to user
- `:business-state/cash` - BigDecimal cash balance
- `:business-state/raw-materials` - Integer units
- `:business-state/finished-goods` - Integer units
- `:business-state/equipment` - EDN set of owned equipment
- `:business-state/accounts-payable` - EDN map vendor -> amount
- `:business-state/accounts-receivable` - EDN map customer -> amount
- `:business-state/simulation-date` - Current in-simulation date

**Pending Transaction:**
- `:pending-tx/user` - Reference to user
- `:pending-tx/action-type` - Keyword (:purchase-materials-cash, etc.)
- `:pending-tx/narrative` - Transaction narrative text
- `:pending-tx/variables` - EDN map of variable values
- `:pending-tx/correct-assertions` - EDN map of correct assertion selections
- `:pending-tx/attempts` - Attempt count
- `:pending-tx/problem-id` - UUID for tracking

**Ledger Entry:**
- `:ledger-entry/id` - UUID
- `:ledger-entry/user` - Reference to user
- `:ledger-entry/date` - In-simulation date
- `:ledger-entry/action-type` - Transaction type keyword
- `:ledger-entry/narrative` - Transaction narrative
- `:ledger-entry/variables` - EDN map of values
- `:ledger-entry/assertions` - EDN map of assertion selections
- `:ledger-entry/journal-entry` - EDN map (debit, credit, amount)
- `:ledger-entry/template-key` - Template used

## Next Steps

### Completed
- [x] Basic three-column UI
- [x] Core classification engine
- [x] Problem generation with templates
- [x] User authentication (email-based)
- [x] Progress tracking with level unlocking
- [x] Datomic database integration
- [x] Student attempt logging
- [x] Business simulation mode
- [x] Personal ledger persistence
- [x] Action-based transaction selection
- [x] Business state tracking

### In Progress
- [ ] Period advancement with automatic events
- [ ] Production constraints and recipes
- [ ] More sophisticated action prerequisites

### Phase 2 (Multi-Course Expansion)
- [ ] Multiple assertion levels (3+)
- [ ] Branching progression paths
- [ ] Instructor dashboard
- [ ] Analytics on student performance
- [ ] Class/section management

### Phase 3 (Analytics Integration)
- [ ] Transaction datasets
- [ ] SQL query interface
- [ ] Pattern analysis tools

## Related Files

- **CLAUDE.md**: Comprehensive project documentation and theory
- **schema.clj** (research): DAG schema for assertive accounting theory
- **example.clj** (research): SP t-shirt company example
- **Plan file**: `/home/accrue/.claude/plans/tranquil-growing-flamingo.md`

## Development Notes

- Uses Reagent atoms for state management
- Database is Datomic (shared transactor with Accrue on port 4334)
- Always use start-backend.sh (password has special chars)
- Server has limited RAM - avoid running multiple shadow-cljs instances
- Check /tmp/aalp.log for backend output

## Research Context

This application is part of academic research on assertive accounting, a novel accounting paradigm that uses logical assertions instead of traditional classification systems. The platform serves dual purposes:

1. **Pedagogical tool**: Teaching accounting through assertion reasoning
2. **Research instrument**: Gathering empirical evidence on learning effectiveness

The business simulation mode adds a third dimension:

3. **Experiential learning**: Students experience realistic business decision-making with accounting consequences

See CLAUDE.md for complete research context and theoretical framework.
