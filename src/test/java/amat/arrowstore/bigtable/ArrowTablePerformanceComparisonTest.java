package amat.arrowstore.bigtable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import amat.arrowstore.bigtable.service.ArrowTableService;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {BigTableApplication.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "bigtable.implementation=arrow",
    "server.servlet.context-path="
})
public class ArrowTablePerformanceComparisonTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArrowTableService arrowTableService;

    @Test
    public void testJavaVsArrowNativePerformance() throws Exception {
        System.out.println("\n=== JAVA VS ARROW NATIVE PERFORMANCE COMPARISON ===");

        String sessionId = "perf-test-session";
        Random random = new Random(42);

        // Create schema
        List<Map<String, Object>> schema = List.of(
            Map.of("name", "key", "type", "INTEGER"),
            Map.of("name", "value", "type", "INTEGER")
        );

        // Generate 150,000 rows with key (1-1000) and value (1-10000)
        System.out.println("Generating 150,000 rows...");
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 150000; i++) {
            int key = random.nextInt(1000) + 1;
            int value = random.nextInt(10000) + 1;
            data.add(Map.of("key", key, "value", value));
        }

        // Create payload with schema and data
        Map<String, Object> payload = Map.of("schema", schema, "data", data);

        // Upload data
        System.out.println("Uploading data...");
        long uploadStart = System.currentTimeMillis();
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        long uploadEnd = System.currentTimeMillis();
        System.out.println("Data upload time: " + (uploadEnd - uploadStart) + " ms");

        // Test 1: Pure Java approach
        System.out.println("\n--- Pure Java In-Memory Approach ---");
        long javaStart = System.nanoTime();

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

        int javaUpdatedCount = 0;
        for (Map<String, Object> row : rows) {
            Integer key = (Integer) row.get("key");
            Integer value = (Integer) row.get("value");
            if (key != null && key > 500) {
                row.put("value", value * 2);
                javaUpdatedCount++;
            }
        }

        long javaEnd = System.nanoTime();
        double javaTimeMs = (javaEnd - javaStart) / 1_000_000.0;
        System.out.println("Java processing time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("Rows updated: " + javaUpdatedCount);

        // Test 2: Arrow Native approach
        System.out.println("\n--- Arrow Native Vector Approach ---");

        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        long arrowStart = System.nanoTime();

        VectorSchemaRoot root = arrowTableService.getVectorSchemaRoot(sessionId);
        IntVector keyVector = (IntVector) root.getVector("key");
        IntVector valueVector = (IntVector) root.getVector("value");

        int arrowUpdatedCount = 0;
        for (int i = 0; i < keyVector.getValueCount(); i++) {
            if (!keyVector.isNull(i) && keyVector.get(i) > 500) {
                int currentValue = valueVector.get(i);
                valueVector.set(i, currentValue * 2);
                arrowUpdatedCount++;
            }
        }

        long arrowEnd = System.nanoTime();
        double arrowTimeMs = (arrowEnd - arrowStart) / 1_000_000.0;
        System.out.println("Arrow native processing time: " + String.format("%.2f", arrowTimeMs) + " ms");
        System.out.println("Rows updated: " + arrowUpdatedCount);

        // Performance comparison
        System.out.println("\n=== PERFORMANCE COMPARISON ===");
        System.out.println("Java in-memory time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("Arrow native time: " + String.format("%.2f", arrowTimeMs) + " ms");
        double speedup = javaTimeMs / arrowTimeMs;
        System.out.println("Speedup: " + String.format("%.2fx", speedup) + " (" +
            (speedup > 1 ? "Arrow faster" : "Java faster") + ")");

        // Cleanup
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk());

        System.out.println("\n=== TEST COMPLETED ===");
    }
}
