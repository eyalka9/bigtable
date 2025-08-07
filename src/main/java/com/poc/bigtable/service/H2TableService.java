package com.poc.bigtable.service;

import com.poc.bigtable.model.*;
import com.poc.bigtable.repository.TableRowRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class H2TableService implements TableService {
    
    @Autowired
    private TableRowRepository repository;
    
    @Autowired
    private OpenTelemetry openTelemetry;
    
    private Tracer tracer;
    private final Map<String, List<ColumnDefinition>> sessionSchemas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> queryTimes = new ConcurrentHashMap<>();
    
    private Tracer getTracer() {
        if (tracer == null) {
            tracer = openTelemetry.getTracer("bigtable-poc", "1.0.0");
        }
        return tracer;
    }
    
    @Override
    @Transactional
    public void loadData(String sessionId, List<Map<String, Object>> data, List<ColumnDefinition> schema) {
        Span span = getTracer().spanBuilder("h2.loadData")
                .setAttribute("sessionId", sessionId)
                .setAttribute("rowCount", data.size())
                .setAttribute("columnCount", schema.size())
                .setAttribute("implementation", "H2")
                .startSpan();
        
        try {
            long startTime = System.currentTimeMillis();
            System.out.println("H2: Starting loadData - " + data.size() + " rows");
            
            // Clear existing data for session
            Span clearSpan = getTracer().spanBuilder("h2.clearSession").startSpan();
            try {
                long clearStart = System.currentTimeMillis();
                repository.deleteBySessionId(sessionId);
                System.out.println("H2: Cleared session in " + (System.currentTimeMillis() - clearStart) + " ms");
            } finally {
                clearSpan.end();
            }
            
            // Store schema
            Span schemaSpan = getTracer().spanBuilder("h2.storeSchema").startSpan();
            try {
                long schemaStart = System.currentTimeMillis();
                sessionSchemas.put(sessionId, schema);
                System.out.println("H2: Stored schema in " + (System.currentTimeMillis() - schemaStart) + " ms");
            } finally {
                schemaSpan.end();
            }
            
            // Convert and save data in batches of 2000
            Span convertSpan = getTracer().spanBuilder("h2.convertRows").startSpan();
            List<TableRow> rows;
            try {
                long convertStart = System.currentTimeMillis();
                rows = data.stream()
                    .map(row -> convertToTableRow(sessionId, row))
                    .collect(Collectors.toList());
                System.out.println("H2: Converted " + rows.size() + " rows in " + (System.currentTimeMillis() - convertStart) + " ms");
            } finally {
                convertSpan.end();
            }
            
            Span saveSpan = getTracer().spanBuilder("h2.saveBatches")
                    .setAttribute("batchSize", 2000)
                    .startSpan();
            try {
                long saveStart = System.currentTimeMillis();
                int batchSize = 2000;
                for (int i = 0; i < rows.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, rows.size());
                    List<TableRow> batch = rows.subList(i, endIndex);
                    repository.saveAll(batch);
                    System.out.println("H2: Saved batch " + (i/batchSize + 1) + " - rows " + (i + 1) + " to " + endIndex + " of " + rows.size());
                }
                System.out.println("H2: All batches saved in " + (System.currentTimeMillis() - saveStart) + " ms");
            } finally {
                saveSpan.end();
            }
            
            long loadTime = System.currentTimeMillis() - startTime;
            System.out.println("H2: Total loadData time: " + loadTime + " ms");
            span.setAttribute("loadTimeMs", loadTime);
            performanceMetrics.put(sessionId, Map.of(
                "loadTimeMs", loadTime,
                "rowCount", data.size(),
                "implementation", "H2"
            ));
        } finally {
            span.end();
        }
    }
    
    @Override
    public TableQueryResponse query(TableQueryRequest request) {
        Span span = getTracer().spanBuilder("h2.query")
                .setAttribute("sessionId", request.getSessionId())
                .setAttribute("page", request.getPage())
                .setAttribute("pageSize", request.getPageSize())
                .setAttribute("hasSearch", request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty())
                .setAttribute("filterCount", request.getFilters() != null ? request.getFilters().size() : 0)
                .setAttribute("sortCount", request.getSorts() != null ? request.getSorts().size() : 0)
                .setAttribute("implementation", "H2")
                .startSpan();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Get all data for the session first
            Span dbSpan = getTracer().spanBuilder("h2.database.findAll").startSpan();
            List<TableRow> allRows;
            try {
                Specification<TableRow> spec = buildSpecification(request);
                allRows = repository.findAll(spec);
            } finally {
                dbSpan.end();
            }
            
            // Convert to data format
            Span convertSpan = getTracer().spanBuilder("h2.convertFromTableRow").startSpan();
            List<Map<String, Object>> allData;
            try {
                allData = allRows.stream()
                    .map(this::convertFromTableRow)
                    .collect(Collectors.toList());
            } finally {
                convertSpan.end();
            }
            
            // Apply search filter if provided
            if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
                Span searchSpan = getTracer().spanBuilder("h2.applySearch")
                        .setAttribute("searchTerm", request.getSearchTerm())
                        .startSpan();
                try {
                    allData = applySearchFilter(allData, request.getSearchTerm(), request.getSessionId());
                } finally {
                    searchSpan.end();
                }
            }
            
            // Apply filters if provided
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                Span filterSpan = getTracer().spanBuilder("h2.applyFilters")
                        .setAttribute("filterCount", request.getFilters().size())
                        .startSpan();
                try {
                    allData = applyFilters(allData, request.getFilters());
                } finally {
                    filterSpan.end();
                }
            }
            
            // Apply sorting if provided
            if (request.getSorts() != null && !request.getSorts().isEmpty()) {
                Span sortSpan = getTracer().spanBuilder("h2.applySorting")
                        .setAttribute("sortCount", request.getSorts().size())
                        .startSpan();
                try {
                    allData = applySorting(allData, request.getSorts());
                } finally {
                    sortSpan.end();
                }
            }
            
            // Apply pagination
            Span paginationSpan = getTracer().spanBuilder("h2.applyPagination").startSpan();
            int totalRows = allData.size();
            int totalPages = (int) Math.ceil((double) totalRows / request.getPageSize());
            int startIndex = request.getPage() * request.getPageSize();
            int endIndex = Math.min(startIndex + request.getPageSize(), totalRows);
            
            List<Map<String, Object>> pageData = allData.subList(startIndex, endIndex);
            paginationSpan.end();
            
            long queryTime = System.currentTimeMillis() - startTime;
            span.setAttribute("queryTimeMs", queryTime);
            span.setAttribute("totalRows", totalRows);
            span.setAttribute("returnedRows", pageData.size());
            
            // Track query time for statistics
            queryTimes.computeIfAbsent(request.getSessionId(), k -> new ArrayList<>()).add(queryTime);
            
            return new TableQueryResponse(
                pageData,
                (long) totalRows,
                totalPages,
                request.getPage(),
                request.getPageSize(),
                queryTime,
                "H2"
            );
        } finally {
            span.end();
        }
    }
    
    @Override
    public List<ColumnDefinition> getSchema(String sessionId) {
        return sessionSchemas.getOrDefault(sessionId, new ArrayList<>());
    }
    
    @Override
    @Transactional
    public void clearSession(String sessionId) {
        repository.deleteBySessionId(sessionId);
        sessionSchemas.remove(sessionId);
        performanceMetrics.remove(sessionId);
        queryTimes.remove(sessionId);
    }
    
    @Override
    public Map<String, Object> getPerformanceMetrics(String sessionId) {
        Map<String, Object> metrics = new HashMap<>(performanceMetrics.getOrDefault(sessionId, new HashMap<>()));
        
        // Query time statistics
        List<Long> times = queryTimes.get(sessionId);
        if (times != null && !times.isEmpty()) {
            double avgQueryTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = times.stream()
                .mapToDouble(time -> Math.pow(time - avgQueryTime, 2))
                .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            
            metrics.put("totalQueries", times.size());
            metrics.put("avgQueryTimeMs", Math.round(avgQueryTime * 100.0) / 100.0);
            metrics.put("stdDevQueryTimeMs", Math.round(stdDev * 100.0) / 100.0);
            metrics.put("minQueryTimeMs", Collections.min(times));
            metrics.put("maxQueryTimeMs", Collections.max(times));
        }
        
        // Memory statistics
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        metrics.put("totalMemoryMB", Math.round(totalMemory / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("usedMemoryMB", Math.round(usedMemory / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("freeMemoryMB", Math.round(freeMemory / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("maxMemoryMB", Math.round(maxMemory / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("memoryUsagePercent", Math.round((double) usedMemory / maxMemory * 100.0 * 100.0) / 100.0);
        
        return metrics;
    }
    
    @Override
    public String getImplementationType() {
        return "H2";
    }
    
    private TableRow convertToTableRow(String sessionId, Map<String, Object> data) {
        TableRow row = new TableRow(UUID.randomUUID().toString(), sessionId);
        
        Map<String, String> columnData = data.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() != null ? entry.getValue().toString() : ""
            ));
        
        row.setColumnData(columnData);
        return row;
    }
    
    private Map<String, Object> convertFromTableRow(TableRow row) {
        Map<String, Object> result = new HashMap<>(row.getColumnData());
        result.put("_id", row.getId());
        result.put("_sessionId", row.getSessionId());
        return result;
    }
    
    private Specification<TableRow> buildSpecification(TableQueryRequest request) {
        return (root, query, criteriaBuilder) -> {
            // Session filter only - search will be handled separately
            return criteriaBuilder.equal(root.get("sessionId"), request.getSessionId());
        };
    }
    
    private List<Map<String, Object>> applySearchFilter(List<Map<String, Object>> data, String searchTerm, String sessionId) {
        String lowerSearchTerm = searchTerm.toLowerCase();
        List<ColumnDefinition> schema = sessionSchemas.get(sessionId);
        
        if (schema == null) {
            return data;
        }
        
        // Get searchable column names
        Set<String> searchableColumns = schema.stream()
            .filter(ColumnDefinition::isSearchable)
            .map(ColumnDefinition::getName)
            .collect(Collectors.toSet());
        
        return data.stream()
            .filter(row -> {
                // Check if any searchable column contains the search term
                return searchableColumns.stream()
                    .anyMatch(columnName -> {
                        Object value = row.get(columnName);
                        return value != null && 
                               value.toString().toLowerCase().contains(lowerSearchTerm);
                    });
            })
            .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> data, List<FilterCriteria> filters) {
        return data.stream()
            .filter(row -> filters.stream().allMatch(filter -> matchesFilter(row, filter)))
            .collect(Collectors.toList());
    }
    
    private boolean matchesFilter(Map<String, Object> row, FilterCriteria filter) {
        Object value = row.get(filter.getColumn());
        List<Object> filterValues = filter.getValues();
        
        if (filterValues == null || filterValues.isEmpty()) {
            return filter.getOperation() == FilterOperation.IS_NULL ? value == null :
                   filter.getOperation() == FilterOperation.IS_NOT_NULL ? value != null : true;
        }
        
        Object filterValue = filterValues.get(0);
        
        switch (filter.getOperation()) {
            case EQUALS:
                return Objects.equals(value, filterValue) || 
                       (value != null && value.toString().equals(filterValue.toString()));
            case NOT_EQUALS:
                return !Objects.equals(value, filterValue) && 
                       (value == null || !value.toString().equals(filterValue.toString()));
            case CONTAINS:
                return value != null && value.toString().toLowerCase()
                    .contains(filterValue.toString().toLowerCase());
            case STARTS_WITH:
                return value != null && value.toString().toLowerCase()
                    .startsWith(filterValue.toString().toLowerCase());
            case ENDS_WITH:
                return value != null && value.toString().toLowerCase()
                    .endsWith(filterValue.toString().toLowerCase());
            case GREATER_THAN:
                return compareValues(value, filterValue) > 0;
            case GREATER_THAN_OR_EQUAL:
                return compareValues(value, filterValue) >= 0;
            case LESS_THAN:
                return compareValues(value, filterValue) < 0;
            case LESS_THAN_OR_EQUAL:
                return compareValues(value, filterValue) <= 0;
            case IS_NULL:
                return value == null;
            case IS_NOT_NULL:
                return value != null;
            case IN:
                return filterValues.stream().anyMatch(fv -> Objects.equals(value, fv) || 
                    (value != null && value.toString().equals(fv.toString())));
            case NOT_IN:
                return filterValues.stream().noneMatch(fv -> Objects.equals(value, fv) || 
                    (value != null && value.toString().equals(fv.toString())));
            default:
                return true;
        }
    }
    
    private int compareValues(Object value1, Object value2) {
        if (value1 == null && value2 == null) return 0;
        if (value1 == null) return -1;
        if (value2 == null) return 1;
        
        try {
            // Try numeric comparison
            double d1 = Double.parseDouble(value1.toString());
            double d2 = Double.parseDouble(value2.toString());
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            return value1.toString().compareTo(value2.toString());
        }
    }
    
    private List<Map<String, Object>> applySorting(List<Map<String, Object>> data, List<SortSpecification> sorts) {
        return data.stream()
            .sorted((row1, row2) -> {
                for (SortSpecification sort : sorts.stream()
                    .sorted(Comparator.comparingInt(SortSpecification::getPriority))
                    .collect(Collectors.toList())) {
                    
                    Object value1 = row1.get(sort.getColumn());
                    Object value2 = row2.get(sort.getColumn());
                    
                    int comparison = compareValues(value1, value2);
                    if (comparison != 0) {
                        return sort.getDirection() == SortDirection.DESC ? -comparison : comparison;
                    }
                }
                return 0;
            })
            .collect(Collectors.toList());
    }
}