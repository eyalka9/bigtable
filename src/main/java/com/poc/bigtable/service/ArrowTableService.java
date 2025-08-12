package com.poc.bigtable.service;

import com.poc.bigtable.model.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ArrowTableService implements TableService {
    
    @Autowired
    private OpenTelemetry openTelemetry;
    
    private Tracer tracer;
    private final RootAllocator allocator = new RootAllocator(Long.MAX_VALUE);
    private final Map<String, VectorSchemaRoot> sessionTables = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnDefinition>> sessionSchemas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> queryTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionRowCounts = new ConcurrentHashMap<>();
    
    private static final int CHUNK_SIZE = 10000; // 50K rows per chunk
    
    private Tracer getTracer() {
        if (tracer == null) {
            tracer = openTelemetry.getTracer("bigtable-poc", "1.0.0");
        }
        return tracer;
    }
    
    @Override
    public void createSchema(String sessionId, List<ColumnDefinition> schema) {
        Span span = getTracer().spanBuilder("arrow.createSchema")
                .setAttribute("sessionId", sessionId)
                .setAttribute("columnCount", schema.size())
                .setAttribute("implementation", "Arrow")
                .startSpan();
        
        try {
            clearSession(sessionId);
            sessionSchemas.put(sessionId, schema);
            sessionRowCounts.put(sessionId, 0);
            
            Schema arrowSchema = createArrowSchema(schema);
            VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, allocator);
            
            // Allocate initial chunk capacity
            for (FieldVector vector : root.getFieldVectors()) {
                if (vector instanceof BaseVariableWidthVector) {
                    // Find the corresponding column definition to get the width
                    String fieldName = vector.getField().getName();
                    ColumnDefinition colDef = schema.stream()
                        .filter(col -> col.getName().equals(fieldName))
                        .findFirst()
                        .orElse(null);
                    
                    int width = (colDef != null && colDef.hasWidth()) ? colDef.getWidth() : 80;
                    ((BaseVariableWidthVector) vector).allocateNew(CHUNK_SIZE * width, CHUNK_SIZE);
                } else {
                    vector.allocateNew();
                    vector.setValueCount(CHUNK_SIZE);
                }
            }
            
            root.setRowCount(0); // Schema created but no data yet
            sessionTables.put(sessionId, root);
        } finally {
            span.end();
        }
    }
    
    @Override
    public void populateData(String sessionId, List<Map<String, Object>> data) {
        Span span = getTracer().spanBuilder("arrow.populateData")
                .setAttribute("sessionId", sessionId)
                .setAttribute("rowCount", data.size())
                .setAttribute("implementation", "Arrow")
                .startSpan();
        
        try {
            long startTime = System.currentTimeMillis();
            
            VectorSchemaRoot root = sessionTables.get(sessionId);
            if (root == null) {
                throw new RuntimeException("Schema must be created before populating data for session: " + sessionId);
            }
            
            List<ColumnDefinition> schema = sessionSchemas.get(sessionId);
            if (schema == null) {
                throw new RuntimeException("Schema definition not found for session: " + sessionId);
            }
            
            if (data.isEmpty()) {
                return;
            }
            
            int currentRowCount = sessionRowCounts.getOrDefault(sessionId, 0);
            int newRowCount = currentRowCount + data.size();
            
            // Check if we need to grow vectors
            Span growSpan = getTracer().spanBuilder("arrow.growVectors").startSpan();
            try {
                for (FieldVector vector : root.getFieldVectors()) {
                    if (newRowCount > vector.getValueCapacity()) {
                        // Calculate new capacity in chunks
                        int newCapacity = ((newRowCount / CHUNK_SIZE) + 1) * CHUNK_SIZE;
                        
                        // Use Arrow's reallocation method which preserves data
                        if (vector instanceof BaseVariableWidthVector) {
                            BaseVariableWidthVector varVector = (BaseVariableWidthVector) vector;
                            // Use reAlloc() which preserves existing data and grows capacity
                            varVector.reAlloc();
                        } else {
                            vector.reAlloc();
                            if (vector.getValueCapacity() < newCapacity) {
                                vector.setValueCount(newCapacity);
                                System.out.println(newCapacity);
                            }
                        }
                    }
                }
            } finally {
                growSpan.end();
            }
            
            // Append new data starting from currentRowCount
            Span appendSpan = getTracer().spanBuilder("arrow.appendData")
                    .setAttribute("startRowIndex", currentRowCount)
                    .setAttribute("vectorOperations", data.size() * schema.size())
                    .startSpan();
            try {
                for (int dataRowIndex = 0; dataRowIndex < data.size(); dataRowIndex++) {
                    Map<String, Object> row = data.get(dataRowIndex);
                    int vectorRowIndex = currentRowCount + dataRowIndex;
                    
                    for (int colIndex = 0; colIndex < schema.size(); colIndex++) {
                        ColumnDefinition colDef = schema.get(colIndex);
                        String columnName = colDef.getName();
                        Object value = row.get(columnName);
                        
                        FieldVector vector = root.getVector(colIndex);
                        setVectorValue(vector, vectorRowIndex, value, colDef.getType());
                    }
                }
            } finally {
                appendSpan.end();
            }
            
            // Update row counts
            sessionRowCounts.put(sessionId, newRowCount);
            root.setRowCount(newRowCount);
            
            long loadTime = System.currentTimeMillis() - startTime;
            span.setAttribute("populateTimeMs", loadTime);
            span.setAttribute("currentRowCount", currentRowCount);
            span.setAttribute("newRowCount", newRowCount);
            
            // Update performance metrics with cumulative data
            performanceMetrics.put(sessionId, Map.of(
                "loadTimeMs", loadTime,
                "rowCount", newRowCount,
                "implementation", "Arrow"
            ));
        } finally {
            span.end();
        }
    }
    
    @Override
    public TableQueryResponse query(TableQueryRequest request) {
        Span span = getTracer().spanBuilder("arrow.query")
                .setAttribute("sessionId", request.getSessionId())
                .setAttribute("page", request.getPage())
                .setAttribute("pageSize", request.getPageSize())
                .setAttribute("hasSearch", request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty())
                .setAttribute("filterCount", request.getFilters() != null ? request.getFilters().size() : 0)
                .setAttribute("sortCount", request.getSorts() != null ? request.getSorts().size() : 0)
                .setAttribute("implementation", "Arrow")
                .startSpan();
        
        try {
            long startTime = System.currentTimeMillis();
            
            VectorSchemaRoot root = sessionTables.get(request.getSessionId());
            if (root == null) {
                span.setAttribute("dataFound", false);
                return new TableQueryResponse(
                    Collections.emptyList(), 0L, 0, 0, 0, 0L, "Arrow"
                );
            }
            
            span.setAttribute("dataFound", true);
            span.setAttribute("vectorRowCount", root.getRowCount());
            
            // Generate row indices that match filters/search (Arrow-native filtering)
            Span filterSpan = getTracer().spanBuilder("arrow.generateMatchingIndices").startSpan();
            List<Integer> matchingIndices;
            try {
                matchingIndices = generateMatchingIndices(root, request);
            } finally {
                filterSpan.end();
            }
            
            // Apply sorting if provided (sort indices, not data)
            if (request.getSorts() != null && !request.getSorts().isEmpty()) {
                Span sortSpan = getTracer().spanBuilder("arrow.sortIndices")
                        .setAttribute("sortCount", request.getSorts().size())
                        .startSpan();
                try {
                    matchingIndices = sortIndices(root, matchingIndices, request.getSorts());
                } finally {
                    sortSpan.end();
                }
            }
            
            // Apply pagination to indices
            Span paginationSpan = getTracer().spanBuilder("arrow.applyPagination").startSpan();
            int totalRows = matchingIndices.size();
            int totalPages = (int) Math.ceil((double) totalRows / request.getPageSize());
            int startIndex = request.getPage() * request.getPageSize();
            int endIndex = Math.min(startIndex + request.getPageSize(), totalRows);
            
            List<Integer> pageIndices = matchingIndices.subList(startIndex, endIndex);
            paginationSpan.end();
            
            // Extract only the data for the paginated indices
            Span extractSpan = getTracer().spanBuilder("arrow.extractPageData").startSpan();
            List<Map<String, Object>> pageData;
            try {
                pageData = extractDataForIndices(root, pageIndices);
            } finally {
                extractSpan.end();
            }
            
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
                "Arrow"
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
    public void clearSession(String sessionId) {
        VectorSchemaRoot root = sessionTables.remove(sessionId);
        if (root != null) {
            root.close();
        }
        sessionSchemas.remove(sessionId);
        sessionRowCounts.remove(sessionId);
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
        
        // Arrow off-heap memory statistics
        long arrowAllocated = allocator.getAllocatedMemory();
        long arrowPeak = allocator.getPeakMemoryAllocation();
        long arrowLimit = allocator.getLimit();
        
        metrics.put("arrowAllocatedMB", Math.round(arrowAllocated / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("arrowPeakMB", Math.round(arrowPeak / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("arrowLimitMB", arrowLimit == Long.MAX_VALUE ? -1 : Math.round(arrowLimit / (1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.put("totalMemoryUsedMB", Math.round((usedMemory + arrowAllocated) / (1024.0 * 1024.0) * 100.0) / 100.0);
        
        return metrics;
    }
    
    @Override
    public String getImplementationType() {
        return "Arrow";
    }
    
    public RootAllocator getAllocator() {
        return allocator;
    }
    
    @Override
    public void exportTableToFile(String sessionId, String filePath) {
        VectorSchemaRoot root = sessionTables.get(sessionId);
        if (root == null) {
            throw new RuntimeException("No data found for session: " + sessionId);
        }
        
        try {
            // Export the entire table directly using ArrowFileWriter
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath);
                 ArrowFileWriter writer = new ArrowFileWriter(root, null, fos.getChannel())) {
                
                writer.start();
                writer.writeBatch();
                writer.end();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to export table: " + e.getMessage(), e);
        }
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
            case BINARY:
                arrowType = new org.apache.arrow.vector.types.pojo.ArrowType.Binary();
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
            case BINARY:
                ((VarBinaryVector) vector).set(index, (byte[]) value);
                break;
            default:
                ((VarCharVector) vector).set(index, value.toString().getBytes());
        }
    }
    
    private List<Integer> generateMatchingIndices(VectorSchemaRoot root, TableQueryRequest request) {
        int rowCount = root.getRowCount();
        List<Integer> matchingIndices = new ArrayList<>();
        
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            boolean matches = true;
            
            // Apply search filter
            if (request.getSearchTerm() != null && !request.getSearchTerm().trim().isEmpty()) {
                matches = matchesSearchTerm(root, rowIndex, request.getSearchTerm(), request.getSessionId());
            }
            
            // Apply filters
            if (matches && request.getFilters() != null && !request.getFilters().isEmpty()) {
                matches = matchesAllFilters(root, rowIndex, request.getFilters());
            }
            
            if (matches) {
                matchingIndices.add(rowIndex);
            }
        }
        
        return matchingIndices;
    }
    
    private boolean matchesSearchTerm(VectorSchemaRoot root, int rowIndex, String searchTerm, String sessionId) {
        String lowerSearchTerm = searchTerm.toLowerCase();
        List<ColumnDefinition> schema = sessionSchemas.get(sessionId);
        
        if (schema == null) {
            return true;
        }
        
        // Cache searchable vectors to avoid repeated lookups
        for (ColumnDefinition colDef : schema) {
            if (colDef.isSearchable()) {
                FieldVector vector = root.getVector(colDef.getName());
                if (vector != null && !vector.isNull(rowIndex)) {
                    // Direct vector access without object creation for strings
                    if (vector instanceof VarCharVector) {
                        String value = new String(((VarCharVector) vector).get(rowIndex));
                        if (value.toLowerCase().contains(lowerSearchTerm)) {
                            return true;
                        }
                    } else {
                        Object value = extractVectorValue(vector, rowIndex);
                        if (value != null && value.toString().toLowerCase().contains(lowerSearchTerm)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    private boolean matchesAllFilters(VectorSchemaRoot root, int rowIndex, List<FilterCriteria> filters) {
        for (FilterCriteria filter : filters) {
            if (!matchesFilter(root, rowIndex, filter)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean matchesFilter(VectorSchemaRoot root, int rowIndex, FilterCriteria filter) {
        FieldVector vector = root.getVector(filter.getColumn());
        if (vector == null) {
            return true;
        }
        
        if (vector.isNull(rowIndex)) {
            return filter.getOperation() == FilterOperation.IS_NULL;
        }
        
        List<Object> filterValues = filter.getValues();
        
        if (filterValues == null || filterValues.isEmpty()) {
            return filter.getOperation() == FilterOperation.IS_NOT_NULL;
        }
        
        Object filterValue = filterValues.get(0);
        
        // Optimize for common vector types to avoid object allocation
        switch (filter.getOperation()) {
            case EQUALS:
                return compareVectorValue(vector, rowIndex, filterValue) == 0;
            case NOT_EQUALS:
                return compareVectorValue(vector, rowIndex, filterValue) != 0;
            case GREATER_THAN:
                return compareVectorValue(vector, rowIndex, filterValue) > 0;
            case GREATER_THAN_OR_EQUAL:
                return compareVectorValue(vector, rowIndex, filterValue) >= 0;
            case LESS_THAN:
                return compareVectorValue(vector, rowIndex, filterValue) < 0;
            case LESS_THAN_OR_EQUAL:
                return compareVectorValue(vector, rowIndex, filterValue) <= 0;
            case IS_NULL:
                return false; // Already checked null above
            case IS_NOT_NULL:
                return true; // Already checked null above
            case CONTAINS:
            case STARTS_WITH:
            case ENDS_WITH:
                // For string operations, extract value
                Object value = extractVectorValue(vector, rowIndex);
                if (value == null) return false;
                String strValue = value.toString().toLowerCase();
                String filterStr = filterValue.toString().toLowerCase();
                switch (filter.getOperation()) {
                    case CONTAINS: return strValue.contains(filterStr);
                    case STARTS_WITH: return strValue.startsWith(filterStr);
                    case ENDS_WITH: return strValue.endsWith(filterStr);
                }
                return false;
            case IN:
                Object value2 = extractVectorValue(vector, rowIndex);
                return filterValues.stream().anyMatch(fv -> Objects.equals(value2, fv) || 
                    (value2 != null && value2.toString().equals(fv.toString())));
            case NOT_IN:
                Object value3 = extractVectorValue(vector, rowIndex);
                return filterValues.stream().noneMatch(fv -> Objects.equals(value3, fv) || 
                    (value3 != null && value3.toString().equals(fv.toString())));
            default:
                return true;
        }
    }
    
    private int compareVectorValue(FieldVector vector, int index, Object filterValue) {
        // Direct comparison without object allocation for primitive types
        if (vector instanceof IntVector && filterValue instanceof Number) {
            int vectorValue = ((IntVector) vector).get(index);
            int filterInt = ((Number) filterValue).intValue();
            return Integer.compare(vectorValue, filterInt);
        } else if (vector instanceof Float8Vector && filterValue instanceof Number) {
            double vectorValue = ((Float8Vector) vector).get(index);
            double filterDouble = ((Number) filterValue).doubleValue();
            return Double.compare(vectorValue, filterDouble);
        } else if (vector instanceof BitVector && filterValue instanceof Boolean) {
            boolean vectorValue = ((BitVector) vector).get(index) == 1;
            boolean filterBool = (Boolean) filterValue;
            return Boolean.compare(vectorValue, filterBool);
        } else {
            // Fall back to object comparison
            Object value = extractVectorValue(vector, index);
            return compareValues(value, filterValue);
        }
    }
    
    private List<Integer> sortIndices(VectorSchemaRoot root, List<Integer> indices, List<SortSpecification> sorts) {
        List<SortSpecification> sortedSorts = sorts.stream()
            .sorted(Comparator.comparingInt(SortSpecification::getPriority))
            .collect(Collectors.toList());
            
        return indices.stream()
            .sorted((idx1, idx2) -> {
                for (SortSpecification sort : sortedSorts) {
                    FieldVector vector = root.getVector(sort.getColumn());
                    if (vector == null) continue;
                    
                    int comparison;
                    
                    // Handle null values efficiently
                    boolean isNull1 = vector.isNull(idx1);
                    boolean isNull2 = vector.isNull(idx2);
                    
                    if (isNull1 && isNull2) {
                        comparison = 0;
                    } else if (isNull1) {
                        comparison = -1;
                    } else if (isNull2) {
                        comparison = 1;
                    } else {
                        // Direct comparison for primitive types to avoid object allocation
                        if (vector instanceof IntVector) {
                            int val1 = ((IntVector) vector).get(idx1);
                            int val2 = ((IntVector) vector).get(idx2);
                            comparison = Integer.compare(val1, val2);
                        } else if (vector instanceof Float8Vector) {
                            double val1 = ((Float8Vector) vector).get(idx1);
                            double val2 = ((Float8Vector) vector).get(idx2);
                            comparison = Double.compare(val1, val2);
                        } else if (vector instanceof BitVector) {
                            boolean val1 = ((BitVector) vector).get(idx1) == 1;
                            boolean val2 = ((BitVector) vector).get(idx2) == 1;
                            comparison = Boolean.compare(val1, val2);
                        } else if (vector instanceof VarCharVector) {
                            String val1 = new String(((VarCharVector) vector).get(idx1));
                            String val2 = new String(((VarCharVector) vector).get(idx2));
                            comparison = val1.compareTo(val2);
                        } else {
                            // Fall back to object comparison
                            Object value1 = extractVectorValue(vector, idx1);
                            Object value2 = extractVectorValue(vector, idx2);
                            comparison = compareValues(value1, value2);
                        }
                    }
                    
                    if (comparison != 0) {
                        return sort.getDirection() == SortDirection.DESC ? -comparison : comparison;
                    }
                }
                return 0;
            })
            .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> extractDataForIndices(VectorSchemaRoot root, List<Integer> indices) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Integer rowIndex : indices) {
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
        } else if (vector instanceof VarBinaryVector) {
            return ((VarBinaryVector) vector).get(index);
        }
        
        return vector.getObject(index);
    }
    
    
    @Override
    public boolean updateFieldValue(String sessionId, String recordId, String fieldName, Object newValue) {
        VectorSchemaRoot root = sessionTables.get(sessionId);
        if (root == null) {
            return false;
        }
        
        // Find the field vector
        FieldVector fieldVector = root.getVector(fieldName);
        if (fieldVector == null) {
            return false;
        }
        
        // Find the record by ID
        FieldVector idVector = root.getVector("id");
        if (idVector == null) {
            return false;
        }
        
        int recordIndex = -1;
        for (int i = 0; i < idVector.getValueCount(); i++) {
            Object currentId = idVector.getObject(i);
            if (currentId != null && currentId.toString().equals(recordId)) {
                recordIndex = i;
                break;
            }
        }
        
        if (recordIndex == -1) {
            return false;
        }
        
        try {
            // Update the field value based on its type
            if (fieldVector instanceof VarCharVector) {
                ((VarCharVector) fieldVector).setSafe(recordIndex, newValue.toString().getBytes());
            } else if (fieldVector instanceof IntVector) {
                ((IntVector) fieldVector).setSafe(recordIndex, Integer.parseInt(newValue.toString()));
            } else if (fieldVector instanceof BigIntVector) {
                ((BigIntVector) fieldVector).setSafe(recordIndex, Long.parseLong(newValue.toString()));
            } else if (fieldVector instanceof Float8Vector) {
                ((Float8Vector) fieldVector).setSafe(recordIndex, Double.parseDouble(newValue.toString()));
            } else if (fieldVector instanceof Float4Vector) {
                ((Float4Vector) fieldVector).setSafe(recordIndex, Float.parseFloat(newValue.toString()));
            } else {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
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
}