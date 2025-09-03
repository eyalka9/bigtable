package amat.arrowstore.bigtable.controller;

import amat.arrowstore.bigtable.model.*;
import amat.arrowstore.bigtable.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/sessions")
@CrossOrigin(origins = "*")
public class TableController {
    
    @Autowired
    private TableService tableService;
    
    @PostMapping("/{sessionId}/data")
    public ResponseEntity<Map<String, String>> uploadData(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> payload) {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) payload.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schemaRaw = (List<Map<String, Object>>) payload.get("schema");
        
        List<ColumnDefinition> schema = schemaRaw.stream()
            .map(this::convertToColumnDefinition)
            .collect(Collectors.toList());
        
        tableService.createSchema(sessionId, schema);
        tableService.populateData(sessionId, data);
        
        return ResponseEntity.ok(Map.of(
            "message", "Data uploaded successfully",
            "implementation", tableService.getImplementationType(),
            "rowCount", String.valueOf(data.size())
        ));
    }
    
    @PostMapping("/{sessionId}/query")
    public ResponseEntity<TableQueryResponse> queryData(
            @PathVariable String sessionId,
            @Valid @RequestBody TableQueryRequest request) {
        
        TableQueryResponse response = tableService.query(request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{sessionId}/schema")
    public ResponseEntity<List<ColumnDefinition>> getSchema(@PathVariable String sessionId) {
        List<ColumnDefinition> schema = tableService.getSchema(sessionId);
        return ResponseEntity.ok(schema);
    }
    
    @DeleteMapping("/{sessionId}/data")
    public ResponseEntity<Map<String, String>> clearData(@PathVariable String sessionId) {
        tableService.clearSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session data cleared"));
    }
    
    @GetMapping("/{sessionId}/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(@PathVariable String sessionId) {
        Map<String, Object> metrics = tableService.getPerformanceMetrics(sessionId);
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        List<ColumnDefinition> schema = tableService.getSchema(sessionId);
        boolean hasData = schema != null && !schema.isEmpty();
        
        return ResponseEntity.ok(Map.of(
            "hasData", hasData,
            "implementation", tableService.getImplementationType(),
            "columnCount", (schema != null && hasData) ? schema.size() : 0
        ));
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "implementation", tableService.getImplementationType(),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/")
    public ResponseEntity<List<String>> getAllSessions() {
        List<String> sessionIds = tableService.getAllSessionIds();
        return ResponseEntity.ok(sessionIds);
    }
    
    @PostMapping("/{sessionId}/export")
    public ResponseEntity<Map<String, Object>> exportTable(@PathVariable String sessionId) {
        try {
            String implementation = tableService.getImplementationType();
            String fileExtension = implementation.equals("Arrow") ? ".arrow" : ".csv";
            String fileName = "table_export_" + System.currentTimeMillis() + fileExtension;
            String filePath = fileName;
            
            tableService.exportTableToFile(sessionId, filePath);
            
            return ResponseEntity.ok(Map.of(
                "message", "Table exported successfully",
                "fileName", fileName,
                "filePath", filePath,
                "implementation", implementation,
                "format", implementation.equals("Arrow") ? "Arrow IPC" : "CSV"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Export failed",
                "message", e.getMessage()
            ));
        }
    }
    
    @PutMapping("/{sessionId}/record/{recordId}/field/{fieldName}")
    public ResponseEntity<Map<String, Object>> updateFieldValue(
            @PathVariable String sessionId,
            @PathVariable String recordId,
            @PathVariable String fieldName,
            @RequestBody Map<String, Object> payload) {
        
        Object newValue = payload.get("value");
        
        try {
            boolean success = tableService.updateFieldValue(sessionId, recordId, fieldName, newValue);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "message", "Field updated successfully",
                    "recordId", recordId,
                    "fieldName", fieldName,
                    "newValue", newValue
                ));
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Record not found or update failed",
                    "recordId", recordId,
                    "fieldName", fieldName
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Update failed",
                "message", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/{sessionId}/delete")
    public ResponseEntity<Map<String, Object>> deleteByQuery(
            @PathVariable String sessionId,
            @Valid @RequestBody TableQueryRequest queryRequest) {
        
        try {
            // Create a new query request with the correct sessionId
            TableQueryRequest deleteQuery = new TableQueryRequest(
                sessionId,
                queryRequest.getFilters(),
                queryRequest.getSorts(),
                queryRequest.getSearchTerm(),
                queryRequest.getPage(),
                queryRequest.getPageSize()
            );
            
            int deletedCount = tableService.deleteByQuery(sessionId, deleteQuery);
            
            return ResponseEntity.ok(Map.of(
                "message", "Records deleted successfully",
                "deletedCount", deletedCount,
                "implementation", tableService.getImplementationType()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Delete operation failed",
                "message", e.getMessage()
            ));
        }
    }
    
    private ColumnDefinition convertToColumnDefinition(Map<String, Object> map) {
        String name = (String) map.get("name");
        String typeStr = (String) map.get("type");
        DataType type = DataType.valueOf(typeStr.toUpperCase());
        boolean sortable = (Boolean) map.getOrDefault("sortable", true);
        boolean filterable = (Boolean) map.getOrDefault("filterable", true);
        boolean searchable = (Boolean) map.getOrDefault("searchable", true);
        
        Integer width = (Integer) map.get("width");
        return new ColumnDefinition(name, type, sortable, filterable, searchable, width);
    }
}