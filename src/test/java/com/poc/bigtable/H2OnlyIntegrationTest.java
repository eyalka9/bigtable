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
    "bigtable.implementation=h2", 
    "server.servlet.context-path=",
    "bigtable.data.rowCount=10000"
})
public class H2OnlyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    //@Test
    public void testHealthEndpoint() throws Exception {
        forceGarbageCollection();
        MemorySnapshot beforeTest = takeSnapshot("Before H2 Health Test");
        printMemoryUsage("H2 Health Test", "START");
        
        mockMvc.perform(get("/v1/sessions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.implementation").value("H2"));
        
        MemorySnapshot afterTest = takeSnapshot("After H2 Health Test");
        printMemoryUsage("H2 Health Test", "END");
        compareSnapshots(beforeTest, afterTest);
    }

    @Test
    public void testH2DataUploadAndQuery() throws Exception {
        forceGarbageCollection();
        MemorySnapshot beforeTest = takeSnapshot("Before H2 Data Test");
        printMemoryUsage("H2 Data Upload & Query", "START");
        
        String sessionId = "test-session-h2";
        
        // Prepare test data
        Map<String, Object> payload = createTestPayload();
        
        // Test data upload
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Data uploaded successfully"))
                .andExpect(jsonPath("$.implementation").value("H2"))
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
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.data").isArray())
                .andExpected(jsonPath("$.data.length()").value(4))
                .andExpected(jsonPath("$.totalElements").value(4))
                .andExpected(jsonPath("$.implementation").value("H2"))
                .andExpected(jsonPath("$.queryTimeMs").exists());

        // Test performance metrics
        mockMvc.perform(get("/v1/sessions/{sessionId}/metrics", sessionId))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.loadTimeMs").exists())
                .andExpected(jsonPath("$.rowCount").value(4))
                .andExpected(jsonPath("$.implementation").value("H2"))
                .andExpected(jsonPath("$.totalQueries").exists())
                .andExpected(jsonPath("$.avgQueryTimeMs").exists());

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
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.data.length()").value(2))
                .andExpected(jsonPath("$.totalElements").value(4))
                .andExpected(jsonPath("$.totalPages").value(2))
                .andExpected(jsonPath("$.currentPage").value(0))
                .andExpected(jsonPath("$.pageSize").value(2));

        // Test clear session
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.message").value("Session data cleared"));
        
        MemorySnapshot afterTest = takeSnapshot("After H2 Data Test");
        printMemoryUsage("H2 Data Upload & Query", "END");
        compareSnapshots(beforeTest, afterTest);
    }
}