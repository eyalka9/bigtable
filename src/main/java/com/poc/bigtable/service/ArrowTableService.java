package com.poc.bigtable.service;

import com.poc.bigtable.model.*;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ArrowTableService implements TableService {
    
    private final RootAllocator allocator = new RootAllocator(Long.MAX_VALUE);
    private final Map<String, VectorSchemaRoot> sessionTables = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnDefinition>> sessionSchemas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> queryTimes = new ConcurrentHashMap<>();
    
    @Override
    public void loadData(String sessionId, List<Map<String, Object>> data, List<ColumnDefinition> schema) {
        long startTime = System.currentTimeMillis();
        
        clearSession(sessionId);
        sessionSchemas.put(sessionId, schema);
        
        if (data.isEmpty()) {
            return;
        }
        
        Schema arrowSchema = createArrowSchema(schema);
        VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, allocator);
        
        // Allocate enough space for all rows
        for (FieldVector vector : root.getFieldVectors()) {
            if (vector instanceof BaseVariableWidthVector) {
                ((BaseVariableWidthVector) vector).allocateNew(data.size() * 50, data.size()); // 50 bytes avg per string
            } else {
                vector.allocateNew();
                vector.setValueCount(data.size());
            }
        }
        
        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            Map<String, Object> row = data.get(rowIndex);
            
            for (int colIndex = 0; colIndex < schema.size(); colIndex++) {
                ColumnDefinition colDef = schema.get(colIndex);
                String columnName = colDef.getName();
                Object value = row.get(columnName);
                
                FieldVector vector = root.getVector(colIndex);
                setVectorValue(vector, rowIndex, value, colDef.getType());
            }
        }
        
        root.setRowCount(data.size());
        sessionTables.put(sessionId, root);
        
        long loadTime = System.currentTimeMillis() - startTime;
        performanceMetrics.put(sessionId, Map.of(
            "loadTimeMs", loadTime,
            "rowCount", data.size(),
            "implementation", "Arrow"
        ));
    }
    
    @Override
    public TableQueryResponse query(TableQueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        VectorSchemaRoot root = sessionTables.get(request.getSessionId());
        if (root == null) {
            return new TableQueryResponse(
                Collections.emptyList(), 0L, 0, 0, 0, 0L, "Arrow"
            );
        }
        
        List<Map<String, Object>> results = extractData(root);
        
        // Apply search filter if provided
        if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
            results = applySearchFilter(results, request.getSearchTerm(), request.getSessionId());
        }
        
        // Apply filters if provided
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            results = applyFilters(results, request.getFilters());
        }
        
        // Apply sorting if provided
        if (request.getSorts() != null && !request.getSorts().isEmpty()) {
            results = applySorting(results, request.getSorts());
        }
        
        int totalRows = results.size();
        int totalPages = (int) Math.ceil((double) totalRows / request.getPageSize());
        int startIndex = request.getPage() * request.getPageSize();
        int endIndex = Math.min(startIndex + request.getPageSize(), totalRows);
        
        List<Map<String, Object>> pageData = results.subList(startIndex, endIndex);
        
        long queryTime = System.currentTimeMillis() - startTime;
        
        // Track query time for statistics
        queryTimes.computeIfAbsent(request.getSessionId(), k -> new ArrayList<>()).add(queryTime);
        
        return new TableQueryResponse(
            pageData,
            (long) totalRows,
            totalPages,
            request.getPage(),
            request.getPageSize(),
            queryTime,
            "Arrow"
        );
    }
    
    @Override
    public List<ColumnDefinition> getSchema(String sessionId) {
        return sessionSchemas.getOrDefault(sessionId, new ArrayList<>());
    }
    
    @Override
    public void clearSession(String sessionId) {
        VectorSchemaRoot root = sessionTables.remove(sessionId);
        if (root != null) {
            root.close();
        }
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
        return "Arrow";
    }
    
    private Schema createArrowSchema(List<ColumnDefinition> schema) {
        List<Field> fields = schema.stream()
            .map(this::createArrowField)
            .collect(Collectors.toList());
        return new Schema(fields);
    }
    
    private Field createArrowField(ColumnDefinition colDef) {
        org.apache.arrow.vector.types.pojo.ArrowType arrowType;
        
        switch (colDef.getType()) {
            case INTEGER:
                arrowType = new org.apache.arrow.vector.types.pojo.ArrowType.Int(32, true);
                break;
            case DOUBLE:
                arrowType = new org.apache.arrow.vector.types.pojo.ArrowType.FloatingPoint(org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE);
                break;
            case BOOLEAN:
                arrowType = new org.apache.arrow.vector.types.pojo.ArrowType.Bool();
                break;
            default:
                arrowType = new org.apache.arrow.vector.types.pojo.ArrowType.Utf8();
        }
        
        return new Field(colDef.getName(), new FieldType(true, arrowType, null), null);
    }
    
    private void setVectorValue(FieldVector vector, int index, Object value, DataType dataType) {
        if (value == null) {
            vector.setNull(index);
            return;
        }
        
        switch (dataType) {
            case INTEGER:
                ((IntVector) vector).set(index, Integer.parseInt(value.toString()));
                break;
            case DOUBLE:
                ((Float8Vector) vector).set(index, Double.parseDouble(value.toString()));
                break;
            case BOOLEAN:
                ((BitVector) vector).set(index, Boolean.parseBoolean(value.toString()) ? 1 : 0);
                break;
            default:
                ((VarCharVector) vector).set(index, value.toString().getBytes());
        }
    }
    
    private List<Map<String, Object>> extractData(VectorSchemaRoot root) {
        List<Map<String, Object>> results = new ArrayList<>();
        int rowCount = root.getRowCount();
        
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, Object> row = new HashMap<>();
            
            for (int colIndex = 0; colIndex < root.getFieldVectors().size(); colIndex++) {
                FieldVector vector = root.getVector(colIndex);
                String columnName = vector.getField().getName();
                Object value = extractVectorValue(vector, rowIndex);
                row.put(columnName, value);
            }
            
            results.add(row);
        }
        
        return results;
    }
    
    private Object extractVectorValue(FieldVector vector, int index) {
        if (vector.isNull(index)) {
            return null;
        }
        
        if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        } else if (vector instanceof Float8Vector) {
            return ((Float8Vector) vector).get(index);
        } else if (vector instanceof BitVector) {
            return ((BitVector) vector).get(index) == 1;
        } else if (vector instanceof VarCharVector) {
            return new String(((VarCharVector) vector).get(index));
        }
        
        return vector.getObject(index);
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