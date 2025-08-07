package com.poc.bigtable.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

@Entity
@Table(name = "table_rows", indexes = {
    @Index(name = "idx_session_id", columnList = "sessionId"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class TableRow {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String sessionId;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "table_row_data", joinColumns = @JoinColumn(name = "row_id"))
    @MapKeyColumn(name = "column_name")
    @Column(name = "column_value", length = 4000)
    private Map<String, String> columnData = new HashMap<>();
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    public TableRow() {
        this.createdAt = LocalDateTime.now();
    }
    
    public TableRow(String id, String sessionId) {
        this();
        this.id = id;
        this.sessionId = sessionId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Map<String, String> getColumnData() { return columnData; }
    public void setColumnData(Map<String, String> columnData) { this.columnData = columnData; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}