package com.poc.bigtable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.poc.bigtable.MemoryTestUtils.*;

@SpringBootTest(classes = {TestConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "bigtable.implementation=arrow", 
    "server.servlet.context-path=",
    "bigtable.data.rowCount=10000"
})
public class ArrowTableControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testUploadDataAndQuery_Arrow() throws Exception {
        forceGarbageCollection();
        MemorySnapshot beforeTest = takeSnapshot("Before Arrow Test");
        printMemoryUsage("Arrow Data Upload & Query", "START");
        
        String sessionId = "test-session-arrow";
        
        // Prepare test data
        Map<String, Object> payload = createTestPayload();
        
        // Test data upload
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Data uploaded successfully"))
                .andExpect(jsonPath("$.implementation").value("Arrow"))
                .andExpect(jsonPath("$.rowCount").value("4"));

        // Test schema retrieval
        mockMvc.perform(get("/v1/sessions/{sessionId}/schema", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("id"))
                .andExpect(jsonPath("$[0].type").value("INTEGER"));

        // Test data query
        Map<String, Object> queryRequest = Map.of(
            "sessionId", sessionId,
            "filters", List.of(),
            "sorts", List.of(),
            "searchTerm", "",
            "page", 0,
            "pageSize", 10
        );

        mockMvc.perform(post("/v1/sessions/{sessionId}/query", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.implementation").value("Arrow"))
                .andExpect(jsonPath("$.queryTimeMs").exists());

        // Test performance metrics
        mockMvc.perform(get("/v1/sessions/{sessionId}/metrics", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadTimeMs").exists())
                .andExpect(jsonPath("$.rowCount").value(4))
                .andExpect(jsonPath("$.implementation").value("Arrow"))
                .andExpect(jsonPath("$.totalQueries").exists())
                .andExpect(jsonPath("$.avgQueryTimeMs").exists());

        // Test pagination
        Map<String, Object> paginationRequest = Map.of(
            "sessionId", sessionId,
            "filters", List.of(),
            "sorts", List.of(),
            "searchTerm", "",
            "page", 0,
            "pageSize", 2
        );

        mockMvc.perform(post("/v1/sessions/{sessionId}/query", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paginationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(2));

        // Test clear session
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session data cleared"));
        
        MemorySnapshot afterTest = takeSnapshot("After Arrow Test");
        printMemoryUsage("Arrow Data Upload & Query", "END");
        compareSnapshots(beforeTest, afterTest);
    }

    private Map<String, Object> createTestPayload() {
        // Create schema with different data types
        List<Map<String, Object>> schema = List.of(
            Map.of("name", "id", "type", "INTEGER", "sortable", true, "filterable", true, "searchable", false),
            Map.of("name", "name", "type", "STRING", "sortable", true, "filterable", true, "searchable", true),
            Map.of("name", "score", "type", "DOUBLE", "sortable", true, "filterable", true, "searchable", false),
            Map.of("name", "active", "type", "BOOLEAN", "sortable", true, "filterable", true, "searchable", false)
        );

        // Create data with different data types
        List<Map<String, Object>> data = List.of(
            Map.of("id", 1, "name", "Alice", "score", 95.5, "active", true),
            Map.of("id", 2, "name", "Bob", "score", 87.2, "active", false),
            Map.of("id", 3, "name", "Charlie", "score", 92.8, "active", true),
            Map.of("id", 4, "name", "Diana", "score", 88.9, "active", true)
        );

        return Map.of("schema", schema, "data", data);
    }
}