# Assertive Accounting Learning Platform

Educational web application that teaches accounting through logical assertions rather than traditional classification-based approaches.

## Project Structure

This application is located in the `aalp/` directory within the assertive accounting research repository.

```
aalp/
├── deps.edn                 # Clojure dependencies
├── shadow-cljs.edn          # ClojureScript build configuration
├── package.json             # NPM dependencies (React, shadow-cljs)
├── .gitignore              # Build artifacts, node_modules, etc.
├── src/
│   ├── clj/                 # Backend Clojure code
│   │   └── assertive_app/
│   │       ├── server.clj           # Ring server, API routes
│   │       └── classification.clj   # Classification engine
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
- **Build**: shadow-cljs (ClojureScript compilation)
- **Database**: PostgreSQL (planned, not yet implemented)

## Getting Started

### Prerequisites

- Java 11+ (for Clojure)
- Node.js and npm (for shadow-cljs)
- Clojure CLI tools

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

**Terminal 1 - Frontend (ClojureScript):**
```bash
npm run watch
```
This starts shadow-cljs in watch mode. The app will be available at http://localhost:8080

**Terminal 2 - Backend (Clojure API server):**
```bash
clojure -M:dev -m assertive-app.server
```
This starts the Ring server on http://localhost:3000

**Note:** For development, you'll access the app via the shadow-cljs dev server (port 8080), which proxies API requests to the backend (port 3000).

### Production Build

```bash
npm run release
```

This creates an optimized production build in `resources/public/js/`

## Current Features (Pilot Phase)

### Core Functionality
- **Transaction presentation**: Display business event narratives
- **Assertion selection**: Students select relevant assertions
- **Classification engine**: Matches assertions to transaction types
- **Real-time feedback**: Shows current classification state
- **Hints system**: Provides directed feedback on incorrect answers
- **Problem generation**: Creates variations of transaction templates

### Assertion Types (Level 0-1)

**Existence & Control (Level 0):**
- Asset Existence
- Asset Control
- Consideration Given

**Temporal (Level 1):**
- Event Occurred (Past/Present)
- Future Obligation
- Future Expectation

**Recognition (Level 1):**
- Performance Obligation Satisfied
- Revenue Earned
- Benefit Consumed

### Classification Types

Current transaction types:
- Cash asset purchase
- Credit asset purchase (creates liability)
- Expense (benefit consumed)
- Revenue recognition

## Architecture

### Backend (`src/clj/`)

**server.clj**: Ring HTTP server with API endpoints
- `GET /api/assertions` - Returns available assertions
- `POST /api/classify` - Classifies based on selected assertions
- `POST /api/generate-problem` - Generates problem at specified level

**classification.clj**: Core logic
- Assertion definitions with levels and domains
- Classification rules (required/prohibited assertions)
- Matching algorithm with distance calculation
- Template-based problem generation

### Frontend (`src/cljs/`)

**core.cljs**: Entry point, initialization

**state.cljs**: Global state management using Reagent atoms
- Current problem
- Available assertions
- Selected assertions
- Feedback
- Current level

**api.cljs**: API client (ajax calls to backend)

**views.cljs**: UI components (three-column layout)
- Narrative panel (transaction description)
- Assertion panel (selection interface)
- Feedback panel (real-time classification + hints)

## Next Steps

### Phase 1 (Current - Pilot)
- [x] Basic three-column UI
- [x] Core classification engine
- [x] Problem generation
- [ ] Progress tracking (student state persistence)
- [ ] Progressive unlocking system
- [ ] Add 10-15 more problems across 3 levels
- [ ] Database integration (PostgreSQL)
- [ ] Student attempt logging

### Phase 2 (Multi-Course Expansion)
- [ ] User authentication
- [ ] Multiple assertion levels
- [ ] Branching progression paths
- [ ] Instructor dashboard
- [ ] Analytics on student performance

### Phase 3 (Analytics Integration)
- [ ] Transaction datasets
- [ ] SQL query interface
- [ ] Pattern analysis tools

## Related Files

- **CLAUDE.md**: Comprehensive project documentation
- **schema.clj**: Research DAG schema (theoretical framework)
- **example.clj**: SP t-shirt company example (research demonstration)
- **assertive-accountingV2.org**: Main research paper

## Development Notes

- Uses Reagent atoms for state management (simple, suitable for pilot)
- Will migrate to re-frame for production (more structured state management)
- Currently no database - all data in-memory
- Problem generation is template-based with randomization
- Classification uses set-based matching (required/prohibited assertions)

## Research Context

This application is part of academic research on assertive accounting, a novel accounting paradigm that uses logical assertions instead of traditional classification systems. The platform serves dual purposes:

1. **Pedagogical tool**: Teaching accounting through assertion reasoning
2. **Research instrument**: Gathering empirical evidence on learning effectiveness

See CLAUDE.md for complete research context and theoretical framework.
