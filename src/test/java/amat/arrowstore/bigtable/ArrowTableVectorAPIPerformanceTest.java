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

import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorMask;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {BigTableApplication.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "bigtable.implementation=arrow",
    "server.servlet.context-path="
})
public class ArrowTableVectorAPIPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArrowTableService arrowTableService;

    private static final VectorSpecies<Integer> SPECIES = jdk.incubator.vector.IntVector.SPECIES_PREFERRED;

    @Test
    public void testJavaVsVectorAPIPerformance() throws Exception {
        // Check if Vector API is available
        try {
            Class.forName("jdk.incubator.vector.IntVector");
        } catch (ClassNotFoundException e) {
            System.out.println("Vector API not available in this JDK, skipping test");
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Vector API not available");
            return;
        }

        System.out.println("\n=== PURE JAVA VS VECTOR API (SIMD) PERFORMANCE COMPARISON ===");

        String sessionId = "vector-api-perf-test-session";
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

        // Test 1: Pure Java approach (scalar operations)
        System.out.println("\n--- Pure Java Approach (Scalar) ---");
        long javaStart = System.nanoTime();

        VectorSchemaRoot root = arrowTableService.getVectorSchemaRoot(sessionId);
        IntVector keyVector = (IntVector) root.getVector("key");
        IntVector valueVector = (IntVector) root.getVector("value");

        int javaUpdatedCount = 0;
        for (int i = 0; i < keyVector.getValueCount(); i++) {
            if (!keyVector.isNull(i) && keyVector.get(i) > 500) {
                int currentValue = valueVector.get(i);
                valueVector.set(i, currentValue * 2);
                javaUpdatedCount++;
            }
        }

        long javaEnd = System.nanoTime();
        double javaTimeMs = (javaEnd - javaStart) / 1_000_000.0;
        System.out.println("Pure Java processing time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("Rows updated: " + javaUpdatedCount);

        // Test 2: Java Vector API approach (SIMD operations)
        System.out.println("\n--- Java Vector API Approach (SIMD) ---");

        // Re-upload data to reset
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        long vectorStart = System.nanoTime();

        VectorSchemaRoot vectorRoot = arrowTableService.getVectorSchemaRoot(sessionId);
        IntVector vectorKeyVector = (IntVector) vectorRoot.getVector("key");
        IntVector vectorValueVector = (IntVector) vectorRoot.getVector("value");

        int rowCount = vectorKeyVector.getValueCount();
        int vectorUpdatedCount = 0;

        // Extract data to arrays for SIMD processing
        int[] keys = new int[rowCount];
        int[] values = new int[rowCount];
        for (int i = 0; i < rowCount; i++) {
            keys[i] = vectorKeyVector.get(i);
            values[i] = vectorValueVector.get(i);
        }

        // Process using Vector API with SIMD
        int upperBound = SPECIES.loopBound(rowCount);
        int i = 0;

        // Vectorized loop
        for (; i < upperBound; i += SPECIES.length()) {
            jdk.incubator.vector.IntVector keyVec = jdk.incubator.vector.IntVector.fromArray(SPECIES, keys, i);
            jdk.incubator.vector.IntVector valueVec = jdk.incubator.vector.IntVector.fromArray(SPECIES, values, i);

            // Create mask for key > 500
            VectorMask<Integer> mask = keyVec.compare(jdk.incubator.vector.VectorOperators.GT, 500);

            // Multiply by 2 where mask is true
            jdk.incubator.vector.IntVector doubledVec = valueVec.mul(2);

            // Blend: use doubled value where mask is true, original otherwise
            jdk.incubator.vector.IntVector resultVec = doubledVec.blend(valueVec, mask.not());

            // Store back to array
            resultVec.intoArray(values, i);

            // Count updates
            vectorUpdatedCount += mask.trueCount();
        }

        // Handle remaining elements (tail)
        for (; i < rowCount; i++) {
            if (keys[i] > 500) {
                values[i] = values[i] * 2;
                vectorUpdatedCount++;
            }
        }

        // Write results back to Arrow vectors
        for (i = 0; i < rowCount; i++) {
            vectorValueVector.set(i, values[i]);
        }

        long vectorEnd = System.nanoTime();
        double vectorTimeMs = (vectorEnd - vectorStart) / 1_000_000.0;
        System.out.println("Vector API processing time: " + String.format("%.2f", vectorTimeMs) + " ms");
        System.out.println("Rows updated: " + vectorUpdatedCount);

        // Performance comparison
        System.out.println("\n=== PERFORMANCE COMPARISON ===");
        System.out.println("Pure Java time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("Vector API (SIMD) time: " + String.format("%.2f", vectorTimeMs) + " ms");
        double speedup = javaTimeMs / vectorTimeMs;
        System.out.println("Speedup: " + String.format("%.2fx", speedup) + " (" +
            (speedup > 1 ? "Vector API faster" : "Java faster") + ")");
        System.out.println("SIMD vector width: " + SPECIES.length() + " elements (" +
            SPECIES.vectorBitSize() + " bits)");

        // Cleanup
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk());

        System.out.println("\n=== TEST COMPLETED ===");
    }
}
