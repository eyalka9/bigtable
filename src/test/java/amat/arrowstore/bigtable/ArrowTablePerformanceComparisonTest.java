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
import org.apache.arrow.vector.ipc.ArrowFileWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
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
    public void testJavaVsDuckDBPerformance() throws Exception {
        System.out.println("\n=== PURE JAVA VS DUCKDB PERFORMANCE COMPARISON ===");

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

        // Test 2: DuckDB SQL approach
        System.out.println("\n--- DuckDB SQL Query Approach ---");

        // Re-upload data to reset
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        long duckdbStart = System.nanoTime();

        // Get Arrow data
        VectorSchemaRoot duckdbRoot = arrowTableService.getVectorSchemaRoot(sessionId);

        // Write Arrow data to temporary file
        File tempArrowFile = File.createTempFile("arrow_data", ".arrow");
        tempArrowFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempArrowFile);
             ArrowFileWriter writer = new ArrowFileWriter(duckdbRoot, null, fos.getChannel())) {
            writer.start();
            writer.writeBatch();
            writer.end();
        }

        // Create DuckDB connection and load Arrow file
        Connection conn = DriverManager.getConnection("jdbc:duckdb:");
        Statement stmt = conn.createStatement();

        // Install and load arrow extension
        stmt.execute("INSTALL arrow");
        stmt.execute("LOAD arrow");

        // Create table from Arrow file
        String arrowPath = tempArrowFile.getAbsolutePath().replace("\\", "/");
        stmt.execute("CREATE TABLE arrow_table AS SELECT * FROM read_arrow_file('" + arrowPath + "')");

        // Execute UPDATE query
        int duckdbUpdatedCount = stmt.executeUpdate("UPDATE arrow_table SET value = value * 2 WHERE key > 500");

        // Read results back into Arrow vectors
        IntVector duckdbValueVector = (IntVector) duckdbRoot.getVector("value");
        ResultSet rs = stmt.executeQuery("SELECT key, value FROM arrow_table ORDER BY ROWID");
        int rowIdx = 0;
        while (rs.next()) {
            duckdbValueVector.set(rowIdx, rs.getInt("value"));
            rowIdx++;
        }
        rs.close();

        stmt.execute("DROP TABLE arrow_table");
        stmt.close();
        conn.close();
        tempArrowFile.delete();

        long duckdbEnd = System.nanoTime();
        double duckdbTimeMs = (duckdbEnd - duckdbStart) / 1_000_000.0;
        System.out.println("DuckDB processing time: " + String.format("%.2f", duckdbTimeMs) + " ms");
        System.out.println("Rows updated: " + duckdbUpdatedCount);

        // Performance comparison
        System.out.println("\n=== PERFORMANCE COMPARISON ===");
        System.out.println("Pure Java time: " + String.format("%.2f", javaTimeMs) + " ms");
        System.out.println("DuckDB time: " + String.format("%.2f", duckdbTimeMs) + " ms");
        double speedup = javaTimeMs / duckdbTimeMs;
        System.out.println("Speedup: " + String.format("%.2fx", speedup) + " (" +
            (speedup > 1 ? "DuckDB faster" : "Java faster") + ")");

        // Cleanup
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk());

        System.out.println("\n=== TEST COMPLETED ===");
    }
}
