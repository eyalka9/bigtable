package com.poc.bigtable.service;

import com.poc.bigtable.model.*;
import java.util.List;
import java.util.Map;

public interface TableService {
    
    void loadData(String sessionId, List<Map<String, Object>> data, List<ColumnDefinition> schema);
    
    TableQueryResponse query(TableQueryRequest request);
    
    List<ColumnDefinition> getSchema(String sessionId);
    
    void clearSession(String sessionId);
    
    Map<String, Object> getPerformanceMetrics(String sessionId);
    
    String getImplementationType();
}