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

    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/v1/sessions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.implementation").value("H2"));
    }

    @Test
    public void testH2DataUploadAndQuery() throws Exception {
        String sessionId = "test-h2-only";
        
        // Create simple test data
        Map<String, Object> payload = Map.of(
            "schema", List.of(
                Map.of("name", "id", "type", "INTEGER", "sortable", true, "filterable", true, "searchable", false),
                Map.of("name", "name", "type", "STRING", "sortable", true, "filterable", true, "searchable", true)
            ),
            "data", List.of(
                Map.of("id", 1, "name", "Test 1"),
                Map.of("id", 2, "name", "Test 2")
            )
        );
        
        // Upload data
        mockMvc.perform(post("/v1/sessions/{sessionId}/data", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Data uploaded successfully"))
                .andExpect(jsonPath("$.implementation").value("H2"))
                .andExpect(jsonPath("$.rowCount").value("2"));

        // Query data
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
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.implementation").value("H2"))
                .andExpect(jsonPath("$.queryTimeMs").exists());

        // Get metrics
        mockMvc.perform(get("/v1/sessions/{sessionId}/metrics", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadTimeMs").exists())
                .andExpect(jsonPath("$.implementation").value("H2"))
                .andExpect(jsonPath("$.totalQueries").exists());

        // Clear data
        mockMvc.perform(delete("/v1/sessions/{sessionId}/data", sessionId))
                .andExpect(status().isOk());
    }
}