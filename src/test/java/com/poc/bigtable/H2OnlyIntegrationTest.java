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
    "bigtable.data.rowCount=100000"
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
        
        
      
        MemorySnapshot afterTest = takeSnapshot("After H2 Data Test");
        printMemoryUsage("H2 Data Upload & Query", "END");
        compareSnapshots(beforeTest, afterTest);
    }
}