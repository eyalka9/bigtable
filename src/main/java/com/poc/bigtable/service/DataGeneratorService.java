package com.poc.bigtable.service;

import com.poc.bigtable.model.ColumnDefinition;
import com.poc.bigtable.model.DataType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class DataGeneratorService {
    
    private static final String[] SAMPLE_STRINGS = {
        // Common words
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "had", "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how", "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did", "its", "let", "put", "say", "she", "too", "use",
        
        // Technology terms
        "database", "query", "table", "column", "index", "schema", "primary", "foreign", "join", "select", "insert", "update", "delete", "create", "alter", "drop", "server", "client", "network", "protocol", "HTTP", "API", "REST", "JSON", "XML", "cache", "memory", "storage", "backup", "recovery", "transaction", "commit", "rollback",
        
        // Business terms
        "customer", "product", "service", "order", "invoice", "payment", "account", "profile", "user", "admin", "manager", "employee", "department", "company", "organization", "project", "task", "deadline", "budget", "revenue", "profit", "loss", "growth", "strategy", "marketing", "sales", "support", "quality", "performance", "efficiency",
        
        // Descriptive words
        "active", "inactive", "pending", "complete", "failed", "success", "error", "warning", "critical", "high", "medium", "low", "urgent", "normal", "draft", "published", "archived", "deleted", "visible", "hidden", "public", "private", "secure", "open", "closed", "locked", "unlocked", "enabled", "disabled", "available", "unavailable",
        
        // Colors and adjectives
        "red", "blue", "green", "yellow", "orange", "purple", "black", "white", "gray", "brown", "pink", "cyan", "magenta", "silver", "gold", "bright", "dark", "light", "heavy", "fast", "slow", "big", "small", "large", "tiny", "huge", "mini", "wide", "narrow", "thick", "thin", "deep", "shallow",
        
        // Categories
        "electronics", "furniture", "clothing", "books", "music", "movies", "games", "sports", "travel", "food", "health", "education", "finance", "insurance", "automotive", "real-estate", "construction", "manufacturing", "retail", "wholesale", "logistics", "shipping", "delivery", "warehouse", "inventory", "stock", "supply", "demand",
        
        // Actions
        "create", "read", "update", "delete", "search", "find", "filter", "sort", "group", "count", "sum", "average", "minimum", "maximum", "calculate", "process", "execute", "run", "start", "stop", "pause", "resume", "cancel", "submit", "approve", "reject", "review", "validate", "verify", "confirm", "send", "receive", "upload", "download",
        
        // Status words
        "initialized", "processing", "completed", "cancelled", "expired", "renewed", "activated", "deactivated", "suspended", "restored", "migrated", "synchronized", "optimized", "compressed", "encrypted", "decrypted", "validated", "verified", "authenticated", "authorized", "logged", "tracked", "monitored", "analyzed", "reported", "scheduled", "executed"
    };
    
    public List<ColumnDefinition> generateSchema(int columnCount) {
        List<ColumnDefinition> schema = new ArrayList<>();
        
        // Fixed schema: 50 columns total
        // 10 string, 30 double, 5 boolean, 5 int, 2 very long string (50-70 chars)
        
        // 10 string columns
        for (int i = 0; i < 10; i++) {
            schema.add(new ColumnDefinition(
                "string_" + (i + 1),
                DataType.STRING,
                true,  // sortable
                true,  // filterable
                true   // searchable
            ));
        }
        
        // 30 double columns
        for (int i = 0; i < 30; i++) {
            schema.add(new ColumnDefinition(
                "double_" + (i + 1),
                DataType.DOUBLE,
                true,  // sortable
                true,  // filterable
                false  // not searchable
            ));
        }
        
        // 5 boolean columns
        for (int i = 0; i < 5; i++) {
            schema.add(new ColumnDefinition(
                "boolean_" + (i + 1),
                DataType.BOOLEAN,
                true,  // sortable
                true,  // filterable
                false  // not searchable
            ));
        }
        
        // 5 int columns
        for (int i = 0; i < 5; i++) {
            schema.add(new ColumnDefinition(
                "int_" + (i + 1),
                DataType.INTEGER,
                true,  // sortable
                true,  // filterable
                false  // not searchable
            ));
        }
        
        // 2 very long string columns (50-70 characters)
        for (int i = 0; i < 2; i++) {
            schema.add(new ColumnDefinition(
                "long_string_" + (i + 1),
                DataType.STRING,
                true,  // sortable
                true,  // filterable
                true   // searchable
            ));
        }
        
        return schema;
    }
    
    public List<Map<String, Object>> generateData(int rowCount, List<ColumnDefinition> schema) {
        List<Map<String, Object>> data = new ArrayList<>();
        Random random = new Random();
        
        for (int row = 0; row < rowCount; row++) {
            Map<String, Object> rowData = new HashMap<>();
            
            for (ColumnDefinition column : schema) {
                Object value = generateValueForColumn(column, random);
                rowData.put(column.getName(), value);
            }
            
            data.add(rowData);
        }
        
        return data;
    }
    
    private Object generateValueForColumn(ColumnDefinition column, Random random) {
        switch (column.getType()) {
            case INTEGER:
                return random.nextInt(10000);
                
            case DOUBLE:
                return Math.round(random.nextDouble() * 1000 * 100.0) / 100.0;
                
            case BOOLEAN:
                return random.nextBoolean();
                
            default: // STRING
                // Check if this is a very long string column
                if (column.getName().startsWith("long_string_")) {
                    return generateLongString(random, 50, 70);
                } else {
                    int wordCount = random.nextInt(3) + 1;
                    List<String> words = new ArrayList<>();
                    for (int w = 0; w < wordCount; w++) {
                        words.add(SAMPLE_STRINGS[random.nextInt(SAMPLE_STRINGS.length)]);
                    }
                    return String.join(" ", words);
                }
        }
    }
    
    private String generateLongString(Random random, int minLength, int maxLength) {
        int targetLength = random.nextInt(maxLength - minLength + 1) + minLength;
        StringBuilder sb = new StringBuilder();
        
        while (sb.length() < targetLength) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            String word = SAMPLE_STRINGS[random.nextInt(SAMPLE_STRINGS.length)];
            if (sb.length() + word.length() + 1 <= targetLength) {
                sb.append(word);
            } else {
                // Fill remaining space with shorter words or truncate
                String remaining = word.substring(0, Math.min(word.length(), targetLength - sb.length()));
                sb.append(remaining);
                break;
            }
        }
        
        return sb.toString();
    }
}