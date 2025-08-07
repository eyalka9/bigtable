# BigTable POC - Architectural Blueprint

## 1. Introduction

### Project Overview
This document outlines the architecture for a Proof of Concept (POC) comparing two technical approaches for handling large tabular data (10,000 rows × 40 columns) with advanced query capabilities in a web application.

### Problem Statement
We need to efficiently handle large datasets in a React frontend with complex sorting, filtering, and searching capabilities, while maintaining responsive user experience. The data is session-scoped and temporary, requiring no persistence.

### Architecture Goals
- Compare performance characteristics of H2 in-memory database vs Apache Arrow
- Evaluate ease of implementation and maintenance
- Provide SQL-like query capabilities for complex filtering
- Maintain responsive user experience with large datasets
- Enable data-driven decision making between approaches

## 2. High-Level Architecture

```
┌─────────────────┐    HTTP/REST    ┌─────────────────┐
│   React Client  │ ◄──────────────► │  Spring Boot    │
│                 │                  │   Backend       │
│ - Data Table    │                  │                 │
│ - Filters       │                  │ ┌─────────────┐ │
│ - Sort Controls │                  │ │   Option A  │ │
│ - Search        │                  │ │ H2 Database │ │
└─────────────────┘                  │ └─────────────┘ │
                                     │ ┌─────────────┐ │
                                     │ │   Option B  │ │
                                     │ │Apache Arrow │ │
                                     │ └─────────────┘ │
                                     └─────────────────┘
```

## 3. Technology Stack

### Frontend Stack
- **Framework**: React 18+
- **State Management**: React Query + Context API
- **UI Components**: Material-UI or Ant Design for table components
- **HTTP Client**: Axios
- **Build Tool**: Vite or Create React App

### Backend Stack
- **Framework**: Spring Boot 3.x
- **Java Version**: 17+
- **Build Tool**: Gradle
- **API Documentation**: OpenAPI/Swagger

### Data Processing Options

#### Option A: H2 Database
- **Database**: H2 In-Memory Database
- **ORM**: Spring Data JPA / Hibernate
- **Query Language**: SQL via JPQL/native queries
- **Connection Pooling**: HikariCP

#### Option B: Apache Arrow
- **Engine**: Apache Arrow Java
- **Query Engine**: Arrow Flight SQL or custom query processor
- **Memory Management**: Arrow's columnar format
- **Data Manipulation**: Arrow Dataset API

## 4. Data Models

### Core Data Structure
```java
// Generic table row representation
public class TableRow {
    private String id;
    private String sessionId;
    private Map<String, Object> columnData;
    private LocalDateTime createdAt;
}

// Column metadata
public class ColumnDefinition {
    private String name;
    private DataType type;
    private boolean sortable;
    private boolean filterable;
    private boolean searchable;
}
```

### Query Models
```java
// Filter criteria
public class FilterCriteria {
    private String column;
    private FilterOperation operation;
    private List<Object> values;
    private LogicalOperator logicalOperator;
}

// Sort specification
public class SortSpecification {
    private String column;
    private SortDirection direction;
    private int priority;
}

// Query request
public class TableQueryRequest {
    private String sessionId;
    private List<FilterCriteria> filters;
    private List<SortSpecification> sorts;
    private String searchTerm;
    private int page;
    private int pageSize;
}
```

## 5. API Specification

### REST Endpoints

#### Data Management
```http
POST /api/v1/sessions/{sessionId}/data
Content-Type: application/json
# Upload initial dataset

GET /api/v1/sessions/{sessionId}/data/schema
# Get column definitions and metadata

DELETE /api/v1/sessions/{sessionId}/data
# Clear session data
```

#### Query Operations
```http
POST /api/v1/sessions/{sessionId}/query
Content-Type: application/json
{
  "filters": [...],
  "sorts": [...],
  "searchTerm": "string",
  "page": 1,
  "pageSize": 100
}
# Execute complex query with pagination
```

#### Performance Metrics
```http
GET /api/v1/sessions/{sessionId}/metrics
# Get performance metrics for comparison
```

## 6. Frontend Architecture

### Component Hierarchy
```
App
├── SessionProvider
├── DataTableContainer
│   ├── TableControls
│   │   ├── FilterPanel
│   │   ├── SortControls
│   │   └── SearchBar
│   ├── DataTable
│   │   ├── TableHeader
│   │   ├── TableBody
│   │   └── TablePagination
│   └── PerformanceMetrics
└── ComparisonDashboard
```

### State Management Strategy
```javascript
// React Query for server state
const useTableData = (sessionId, queryParams) => {
  return useQuery({
    queryKey: ['tableData', sessionId, queryParams],
    queryFn: () => fetchTableData(sessionId, queryParams),
    staleTime: 30000,
  });
};

// Context for UI state
const TableStateContext = createContext({
  filters: [],
  sorts: [],
  searchTerm: '',
  currentImplementation: 'h2', // or 'arrow'
});
```

## 7. Backend Architecture

### Option A: H2 Database Implementation

#### Service Layer
```java
@Service
public class H2TableService implements TableService {
    
    @Autowired
    private TableRowRepository repository;
    
    public TableQueryResponse query(String sessionId, TableQueryRequest request) {
        Specification<TableRow> spec = buildSpecification(request);
        Page<TableRow> results = repository.findAll(spec, buildPageable(request));
        return mapToResponse(results);
    }
    
    private Specification<TableRow> buildSpecification(TableQueryRequest request) {
        // Convert filters to JPA Specifications
        // Handle complex multi-column filtering
    }
}
```

#### Repository Layer
```java
@Repository
public interface TableRowRepository extends JpaRepository<TableRow, String>, 
                                           JpaSpecificationExecutor<TableRow> {
    
    @Query("SELECT tr FROM TableRow tr WHERE tr.sessionId = :sessionId")
    Page<TableRow> findBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
}
```

### Option B: Apache Arrow Implementation

#### Service Layer
```java
@Service
public class ArrowTableService implements TableService {
    
    private final Map<String, Table> sessionTables = new ConcurrentHashMap<>();
    
    public TableQueryResponse query(String sessionId, TableQueryRequest request) {
        Table table = sessionTables.get(sessionId);
        
        // Apply filters using Arrow's compute functions
        Table filtered = applyFilters(table, request.getFilters());
        
        // Apply sorting
        Table sorted = applySorts(filtered, request.getSorts());
        
        // Apply pagination
        return paginateResults(sorted, request.getPage(), request.getPageSize());
    }
    
    private Table applyFilters(Table table, List<FilterCriteria> filters) {
        // Use Arrow's compute module for filtering
        // Leverage vectorized operations
    }
}
```

## 8. Project Structure

```
bigtable-poc/
├── backend/
│   ├── src/main/java/com/poc/bigtable/
│   │   ├── config/
│   │   │   ├── H2Config.java
│   │   │   └── ArrowConfig.java
│   │   ├── controller/
│   │   │   └── TableController.java
│   │   ├── service/
│   │   │   ├── TableService.java
│   │   │   ├── H2TableService.java
│   │   │   └── ArrowTableService.java
│   │   ├── model/
│   │   │   ├── TableRow.java
│   │   │   ├── FilterCriteria.java
│   │   │   └── TableQueryRequest.java
│   │   └── repository/
│   │       └── TableRowRepository.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── schema.sql
│   └── build.gradle
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── DataTable/
│   │   │   ├── Filters/
│   │   │   └── Performance/
│   │   ├── hooks/
│   │   │   └── useTableData.js
│   │   ├── services/
│   │   │   └── api.js
│   │   └── utils/
│   │       └── queryBuilder.js
│   ├── package.json
│   └── vite.config.js
└── docs/
    ├── architecture.md
    ├── problem.md
    └── performance-results.md
```

## 9. Development Workflow

### Phase 1: Foundation Setup
1. **Environment Setup**
   - Configure Spring Boot with both H2 and Arrow dependencies
   - Set up React frontend with table components
   - Implement basic REST API structure

2. **Data Model Implementation**
   - Define common interfaces for both approaches
   - Implement basic CRUD operations
   - Set up session management

### Phase 2: H2 Implementation
1. **Database Schema Design**
   - Create flexible schema for dynamic columns
   - Implement efficient indexing strategy
   - Set up connection pooling

2. **Query Implementation**
   - Build dynamic query generation
   - Implement multi-column sorting
   - Add complex filtering logic

### Phase 3: Arrow Implementation
1. **Arrow Integration**
   - Set up Arrow memory management
   - Implement columnar data loading
   - Create query processing pipeline

2. **Performance Optimization**
   - Leverage vectorized operations
   - Implement efficient filtering
   - Optimize memory usage

### Phase 4: Frontend Integration
1. **Table Component Development**
   - Build responsive data table
   - Implement sorting controls
   - Add filtering interface

2. **Performance Monitoring**
   - Add client-side metrics
   - Implement query timing
   - Create comparison dashboard

### Phase 5: Testing & Benchmarking
1. **Performance Testing**
   - Load test with full dataset
   - Measure query response times
   - Monitor memory usage

2. **Comparative Analysis**
   - Document performance characteristics
   - Analyze ease of implementation
   - Create recommendation report

## 10. Deployment Architecture

### Development Environment
```yaml
# docker-compose.yml for local development
version: '3.8'
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - IMPLEMENTATION=h2  # or arrow
    
  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:8080
```

### Performance Testing Setup
- **Load Generation**: JMeter or k6 for backend testing
- **Frontend Metrics**: Web Vitals and custom performance markers
- **Resource Monitoring**: Docker stats and JVM metrics

## 11. Success Criteria

### Performance Metrics
- **Query Response Time**: < 500ms for complex queries
- **Memory Usage**: Efficient memory utilization for 10k rows
- **Concurrent Users**: Support for multiple simultaneous sessions

### Implementation Metrics
- **Development Time**: Time to implement each approach
- **Code Complexity**: Lines of code and maintainability
- **Learning Curve**: Ease of understanding and modification

### Comparison Framework
| Metric | H2 Database | Apache Arrow | Winner |
|--------|-------------|--------------|---------|
| Query Performance | TBD | TBD | TBD |
| Memory Efficiency | TBD | TBD | TBD |
| Implementation Complexity | TBD | TBD | TBD |
| Development Speed | TBD | TBD | TBD |
| Maintenance Overhead | TBD | TBD | TBD |

## 12. Next Steps

1. **Immediate Actions**
   - Set up development environment
   - Initialize Spring Boot project with dual configuration
   - Create React frontend boilerplate

2. **Week 1 Goals**
   - Implement basic data loading for both approaches
   - Create simple table display
   - Set up performance monitoring

3. **Week 2-3 Goals**
   - Complete H2 implementation
   - Complete Arrow implementation
   - Implement comprehensive testing

4. **Week 4 Goals**
   - Performance benchmarking
   - Documentation of results
   - Architecture recommendation

This architectural blueprint provides a comprehensive roadmap for implementing and comparing both approaches while maintaining clear separation of concerns and enabling data-driven decision making.



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