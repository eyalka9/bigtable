# Claude Instructions

USE persona from https://raw.githubusercontent.com/bmadcode/BMAD-METHOD/refs/heads/main/dist/agents/architect.txt
NEVER break character
ALWAYS read ALL documents under docs folder


<!-- Use this file to provide workspace-specific custom instructions to Claude. -->
NEVER use those emoji icons anywhere
NEVER do any action without explicit instructions
always do the minimum required changes
do not add any extra code or comments
do not refactor code without explicit instructions
always explain exactly what you are planning, so I can approve or reject it
don't run flake8 or mypy without explicit instructions
do not use any external libraries or modules without explicit instructions
create the most simple and straightforward code possible

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.

# Project Context - BigTable POC

## Project Overview
- **Goal**: Compare H2 in-memory database vs Apache Arrow for handling large tabular data
- **Dataset**: 100,000 rows × 40 columns with advanced query capabilities
- **Architecture**: Spring Boot backend + React frontend
- **Purpose**: Performance comparison POC with SQL-like query capabilities

## Technology Stack
**Backend:**
- Spring Boot 3.2.0 with Java 21
- H2 Database (in-memory) 
- Apache Arrow 14.0.1
- Gradle build system
- REST API with OpenAPI docs

**Frontend:**
- React 18 with Create React App
- React Query for server state
- React Table for data display
- Axios for HTTP client
- Proxy to localhost:8080

## Current Project State - FULLY COMPLETE POC ✅

### ✅ All Features Implemented:

**1. Complete Backend Implementation:**
   - **H2TableService**: Full CRUD, filtering, sorting, search, memory stats
   - **ArrowTableService**: Complete Arrow implementation with all features
   - **DataInitializer**: Auto-loads 100K rows on startup using `default-session`
   - **DataGeneratorService**: 200+ diverse sample words for realistic data
   - **TableController**: REST API with session status, health checks

**2. Advanced Query Capabilities:**
   - **Search**: Cross-column text search with case-insensitive matching
   - **Filtering**: 11 operations (equals, contains, comparisons, null checks)
   - **Sorting**: Multi-column sorting with priority and direction control
   - **Pagination**: Efficient server-side pagination with configurable page sizes

**3. Performance & Memory Monitoring:**
   - **Query Statistics**: Min/max/avg/std dev query times
   - **Memory Metrics**: Used/free/total/max memory with usage percentage
   - **Real-time Updates**: 5-second refresh intervals for live monitoring
   - **Implementation Comparison**: Side-by-side H2 vs Arrow performance

**4. Production-Ready Frontend:**
   - **Sortable DataTable**: Click headers for multi-column sorting with visual indicators
   - **Advanced Filters**: Expandable filter panel with 11+ operations
   - **Search Interface**: Global search across all searchable columns  
   - **Performance Dashboard**: Categorized metrics (General/Performance/Memory)
   - **Auto-Detection**: Detects pre-loaded data and updates UI accordingly

**5. Memory & Performance Optimization:**
   - **JVM Settings**: 4GB heap (-Xmx4g) with G1GC for 100K row handling
   - **H2 Optimization**: 256MB cache, batch processing, connection pooling
   - **Buffer Management**: Proper Arrow vector allocation for large datasets
   - **Efficient Pipeline**: Search → Filter → Sort → Paginate processing

**6. Development Experience:**
   - **Auto-Startup**: Backend loads 100K rows automatically on boot
   - **Frontend Accessibility**: Available at localhost:3000 and IP:3000
   - **Hot Reload**: React dev server with backend proxy
   - **Integration Tests**: H2 and Arrow test suites with 100% pass rate

### Architecture Status:
- **Phase 1**: ✅ Foundation Complete
- **Phase 2**: ✅ H2 Implementation Complete  
- **Phase 3**: ✅ Arrow Implementation Complete
- **Phase 4**: ✅ Frontend Integration Complete
- **Phase 5**: ✅ Performance Testing & Monitoring Complete

### Current Configuration:
- **Implementation**: Arrow (configurable via application.yml)
- **Dataset**: 100K rows × 40 columns (auto-generated on startup)
- **Memory**: 4GB heap, optimized for large dataset processing
- **Session**: Uses `default-session` for consistent data across users

## Development Commands
- Backend build: `./gradlew build`
- Backend run: `./gradlew bootRun` (automatically loads 100K rows on startup)
- Frontend build: `npm run build` (in frontend dir)
- Frontend dev: `npm start` (in frontend dir, accessible at localhost:3000)

## Architecture Reference
- Full architecture documented in `docs/architecture.md`
- API endpoints: `/api/v1/sessions/{sessionId}/*`
- H2 console: `http://localhost:8080/api/h2-console`
- Implementation toggle: `bigtable.implementation=h2|arrow` in application.yml
