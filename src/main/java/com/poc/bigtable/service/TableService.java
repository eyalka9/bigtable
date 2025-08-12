package com.poc.bigtable.service;

import com.poc.bigtable.model.*;
import java.util.List;
import java.util.Map;

public interface TableService {
    
    void createSchema(String sessionId, List<ColumnDefinition> schema);
    
    void populateData(String sessionId, List<Map<String, Object>> data);
    
    TableQueryResponse query(TableQueryRequest request);
    
    List<ColumnDefinition> getSchema(String sessionId);
    
    void clearSession(String sessionId);
    
    Map<String, Object> getPerformanceMetrics(String sessionId);
    
    String getImplementationType();
    
    void exportTableToFile(String sessionId, String filePath);
    
    boolean updateFieldValue(String sessionId, String recordId, String fieldName, Object newValue);
}