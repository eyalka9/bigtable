package amat.arrowstore.bigtable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import amat.arrowstore.bigtable.model.DataType;
import amat.arrowstore.bigtable.service.ArrowTableService;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ArrowTablePerformanceComparisonTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArrowTableService arrowTableService;

    @Test
    public void testJavaVsDuckDBPerformance() throws Exception {
        System.out.println("\n=== JAVA VS DUCKDB PERFORMANCE COMPARISON ===");

        String sessionId = "perf-test-session";
        Random random = new Random(42); // Fixed seed for repeatability

        // Create session with key and value columns
        Map<String, Object> createSessionRequest = Map.of(
            "columns", List.of(
                Map.of("name", "key", "type", "INTEGER"),
                Map.of("name", "value", "type", "INTEGER")
            )
        );

        mockMvc.perform(post("/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createSessionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        // Generate 150,000 rows with key (1-1000) and value (1-10000)
        System.out.println("Generating 150,000 rows...");
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 150000; i++) {
            int key = random.nextInt(1000) + 1; // 1-1000
            int value = random.nextInt(10000) + 1; // 1-10000
            data.add(Map.of("key", key, "value", value));
        }

        // Upload data
        System.out.println("Uploading data...");
        long uploadStart = System.currentTimeMillis();
        mockMvc.perform(post("/v1/sessions/{sessionId}/upload", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk());
        long uploadEnd = System.currentTimeMillis();
        System.out.println("Data upload time: " + (uploadEnd - uploadStart) + " ms");

        // Test 1: Pure Java approach
        System.out.println("\n--- Pure Java Approach ---");
        long javaStart = System.nanoTime();

        // Query all data
        Map<String, Object> queryRequest = Map.of(
            "sessionId", sessionId,
            "filters", List.of(),
            "sorts", List.of(),
            "searchTerm", "",
            "page", 0,
            "pageSize", 150000
        );

        String queryResponse = mockMvc.perform(post("/v1/sessions/{sessionId}/query", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> queryResult = objectMapper.readValue(queryResponse, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) queryResult.get("data");

        // Apply transformation in Java
        int updatedCount = 0;
        for (Map<String, Object> row : rows) {
            Integer key = (Integer) row.get("key");
            Integer value = (Integer) row.get("value");
            if (key != null && key > 500) {
                row.put("value", value * 2);
                updatedCount++;
            }
        }

        long javaEnd = System.nanoTime();
        double javaTimeMs = (javaEnd - javaStart) / 1_000_000.0;
        System.out.println("Java processing time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("Rows updated: " + updatedCount);

        // Test 2: DuckDB approach
        System.out.println("\n--- DuckDB Query Approach ---");

        // Re-upload original data to reset
        mockMvc.perform(post("/v1/sessions/{sessionId}/upload", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk());

        long duckdbStart = System.nanoTime();

        // Execute DuckDB query
        String updateQuery = "UPDATE arrow_table SET value = value * 2 WHERE key > 500";
        Map<String, Object> duckdbRequest = Map.of(
            "sessionId", sessionId,
            "query", updateQuery
        );

        mockMvc.perform(post("/v1/sessions/{sessionId}/duckdb/execute", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duckdbRequest)))
                .andExpect(status().isOk());

        long duckdbEnd = System.nanoTime();
        double duckdbTimeMs = (duckdbEnd - duckdbStart) / 1_000_000.0;
        System.out.println("DuckDB processing time: " + String.format("%.2f", duckdbTimeMs) + " ms");

        // Performance comparison
        System.out.println("\n=== PERFORMANCE COMPARISON ===");
        System.out.println("Java time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("DuckDB time: " + String.format("%.2f", duckdbTimeMs) + " ms");
        double speedup = javaTimeMs / duckdbTimeMs;
        System.out.println("Speedup: " + String.format("%.2fx", speedup) + " (" +
            (speedup > 1 ? "DuckDB faster" : "Java faster") + ")");

        // Cleanup
        mockMvc.perform(delete("/v1/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk());

        System.out.println("\n=== TEST COMPLETED ===");
    }
}
