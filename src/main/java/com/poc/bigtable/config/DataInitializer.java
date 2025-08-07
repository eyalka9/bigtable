package com.poc.bigtable.config;

import com.poc.bigtable.model.ColumnDefinition;
import com.poc.bigtable.service.DataGeneratorService;
import com.poc.bigtable.service.TableService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_SESSION_ID = "default-session";
    
    @Value("${bigtable.data.rowCount:100000}")
    private int rowCount;
    
    @Autowired
    private TableService tableService;
    
    @Autowired
    private DataGeneratorService dataGeneratorService;
    
    @Autowired
    private OpenTelemetry openTelemetry;
    
    private Tracer tracer;
    
    private Tracer getTracer() {
        if (tracer == null) {
            tracer = openTelemetry.getTracer("bigtable-poc", "1.0.0");
        }
        return tracer;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        Span span = getTracer().spanBuilder("dataInitializer.run")
                .setAttribute("sessionId", DEFAULT_SESSION_ID)
                .setAttribute("rowCount", rowCount)
                .setAttribute("implementation", tableService.getImplementationType())
                .startSpan();
        
        try {
            logger.info("Starting data initialization...");
            long startTime = System.currentTimeMillis();
            
            // Generate schema
            Span schemaSpan = getTracer().spanBuilder("dataInitializer.generateSchema").startSpan();
            List<ColumnDefinition> schema;
            try {
                long schemaStart = System.currentTimeMillis();
                schema = dataGeneratorService.generateSchema();
                logger.info("Generated schema with {} columns in {} ms", 50, (System.currentTimeMillis() - schemaStart));
            } finally {
                schemaSpan.end();
            }
            
            // Generate sample data
            Span dataGenSpan = getTracer().spanBuilder("dataInitializer.generateData")
                    .setAttribute("rowCount", rowCount)
                    .setAttribute("columnCount", schema.size())
                    .startSpan();
            List<Map<String, Object>> data;
            try {
                long dataGenStart = System.currentTimeMillis();
                logger.info("Generating {} rows of sample data...", rowCount);
                data = dataGeneratorService.generateData(rowCount, schema);
                logger.info("Generated {} rows in {} ms", rowCount, (System.currentTimeMillis() - dataGenStart));
            } finally {
                dataGenSpan.end();
            }
            
            // Load data into the table service
            Span loadSpan = getTracer().spanBuilder("dataInitializer.loadData")
                    .setAttribute("implementation", tableService.getImplementationType())
                    .startSpan();
            try {
                long loadStart = System.currentTimeMillis();
                logger.info("Loading data into {} implementation...", tableService.getImplementationType());
                tableService.loadData(DEFAULT_SESSION_ID, data, schema);
                logger.info("Loaded data in {} ms", (System.currentTimeMillis() - loadStart));
            } finally {
                loadSpan.end();
            }
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            span.setAttribute("totalTimeMs", totalTime);
            logger.info("Data initialization completed in {} ms. Implementation: {}", 
                       totalTime, tableService.getImplementationType());
            logger.info("Dataset ready: {} rows × {} columns", rowCount, 50);
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Failed to initialize data", e);
            throw e;
        } finally {
            span.end();
        }
    }
}