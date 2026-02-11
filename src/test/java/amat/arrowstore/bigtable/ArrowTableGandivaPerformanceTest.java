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
import org.apache.arrow.gandiva.evaluator.*;
import org.apache.arrow.gandiva.exceptions.GandivaException;
import org.apache.arrow.gandiva.expression.*;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {BigTableApplication.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "bigtable.implementation=arrow",
    "server.servlet.context-path="
})
public class ArrowTableGandivaPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArrowTableService arrowTableService;

    @Test
    public void testJavaVsGandivaPerformance() throws Exception {
        System.out.println("\n=== PURE JAVA VS GANDIVA PERFORMANCE COMPARISON ===");

        String sessionId = "gandiva-perf-test-session";
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

        // Test 1: Pure Java approach (direct Arrow vector manipulation)
        System.out.println("\n--- Pure Java Approach (Direct Arrow Vectors) ---");
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

        // Test 2: Gandiva approach (LLVM-compiled expression)
        System.out.println("\n--- Gandiva LLVM-Compiled Expression Approach ---");

        // Re-upload data to reset
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        long gandivaStart = System.nanoTime();

        try {
            VectorSchemaRoot gandivaRoot = arrowTableService.getVectorSchemaRoot(sessionId);

            // Build Gandiva expression: if (key > 500) then value * 2 else value
            Field keyField = gandivaRoot.getSchema().findField("key");
            Field valueField = gandivaRoot.getSchema().findField("value");
            Field resultField = Field.nullable("result", new ArrowType.Int(32, true));

            TreeNode keyNode = TreeBuilder.makeField(keyField);
            TreeNode valueNode = TreeBuilder.makeField(valueField);
            TreeNode constantNode = TreeBuilder.makeLiteral(500);
            TreeNode twoNode = TreeBuilder.makeLiteral(2);

            // Build: key > 500
            TreeNode condition = TreeBuilder.makeFunction("greater_than",
                Arrays.asList(keyNode, constantNode), new ArrowType.Bool());

            // Build: value * 2
            TreeNode valueTimesTwo = TreeBuilder.makeFunction("multiply",
                Arrays.asList(valueNode, twoNode), new ArrowType.Int(32, true));

            // Build: if (key > 500) then value * 2 else value
            TreeNode ifNode = TreeBuilder.makeIf(condition, valueTimesTwo, valueNode,
                new ArrowType.Int(32, true));

            ExpressionTree expr = TreeBuilder.makeExpression(ifNode, resultField);

            // Build projector and evaluate
            Projector projector = Projector.make(gandivaRoot.getSchema(), Arrays.asList(expr));

            // Create output vector for results
            IntVector resultVector = new IntVector("result", gandivaRoot.getAllocator());
            resultVector.allocateNew(gandivaRoot.getRowCount());

            // Evaluate expression
            projector.evaluate(gandivaRoot.getRowCount(), Arrays.asList(resultVector));

            // Count updated rows and copy results back to value vector
            int gandivaUpdatedCount = 0;
            IntVector gandivaValueVector = (IntVector) gandivaRoot.getVector("value");
            for (int i = 0; i < resultVector.getValueCount(); i++) {
                int oldValue = gandivaValueVector.get(i);
                int newValue = resultVector.get(i);
                if (oldValue != newValue) {
                    gandivaUpdatedCount++;
                }
                gandivaValueVector.set(i, newValue);
            }

            // Cleanup
            resultVector.close();
            projector.close();

            long gandivaEnd = System.nanoTime();
            double gandivaTimeMs = (gandivaEnd - gandivaStart) / 1_000_000.0;
            System.out.println("Gandiva processing time: " + String.format("%.2f", gandivaTimeMs) + " ms");
            System.out.println("Rows updated: " + gandivaUpdatedCount);

            // Performance comparison
            System.out.println("\n=== PERFORMANCE COMPARISON ===");
            System.out.println("Pure Java time: " + String.format("%.2f", javaTimeMs) + " ms");
            System.out.println("Gandiva time: " + String.format("%.2f", gandivaTimeMs) + " ms");
            double speedup = javaTimeMs / gandivaTimeMs;
            System.out.println("Speedup: " + String.format("%.2fx", speedup) + " (" +
                (speedup > 1 ? "Gandiva faster" : "Java faster") + ")");

        } catch (GandivaException e) {
            System.err.println("Gandiva error: " + e.getMessage());
            throw new RuntimeException("Gandiva evaluation failed", e);
        }

        // Cleanup
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk());

        System.out.println("\n=== TEST COMPLETED ===");
    }
}
