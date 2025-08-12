package amat.arrowstore.bigtable.config;

import amat.arrowstore.bigtable.model.ColumnDefinition;
import amat.arrowstore.bigtable.service.DataGeneratorService;
import amat.arrowstore.bigtable.service.TableService;
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
    private static final String SMALL_SESSION_ID = "small-session";
    
    @Value("${bigtable.data.rowCount:150000}")
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
                logger.info("Generated schema with {} columns in {} ms", schema.size(), (System.currentTimeMillis() - schemaStart));
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
            
            // Create schema first
            Span schemaCreateSpan = getTracer().spanBuilder("dataInitializer.createSchema")
                    .setAttribute("implementation", tableService.getImplementationType())
                    .startSpan();
            try {
                long schemaCreateStart = System.currentTimeMillis();
                logger.info("Creating schema in {} implementation...", tableService.getImplementationType());
                tableService.createSchema(DEFAULT_SESSION_ID, schema);
                logger.info("Created schema in {} ms", (System.currentTimeMillis() - schemaCreateStart));
            } finally {
                schemaCreateSpan.end();
            }
            
            // Then populate data in chunks
            Span populateSpan = getTracer().spanBuilder("dataInitializer.populateData")
                    .setAttribute("implementation", tableService.getImplementationType())
                    .setAttribute("totalRows", data.size())
                    .startSpan();
            try {
                long populateStart = System.currentTimeMillis();
                logger.info("Populating {} rows into {} implementation in 50K chunks...", 
                           data.size(), tableService.getImplementationType());
                
                int chunkSize = 10000;
                int totalChunks = (int) Math.ceil((double) data.size() / chunkSize);
                
                for (int chunk = 0; chunk < totalChunks; chunk++) {
                    int startIndex = chunk * chunkSize;
                    int endIndex = Math.min(startIndex + chunkSize, data.size());
                    
                    List<Map<String, Object>> chunkData = data.subList(startIndex, endIndex);
                    long chunkStart = System.currentTimeMillis();
                    
                    tableService.populateData(DEFAULT_SESSION_ID, chunkData);
                    
                    long chunkTime = System.currentTimeMillis() - chunkStart;
                    logger.info("Populated chunk {}/{}: rows {}-{} ({} rows) in {} ms", 
                               chunk + 1, totalChunks, startIndex, endIndex - 1, 
                               chunkData.size(), chunkTime);
                }
                
                long totalPopulateTime = System.currentTimeMillis() - populateStart;
                logger.info("Populated all {} rows in {} ms", data.size(), totalPopulateTime);
            } finally {
                populateSpan.end();
            }
            
            // Create a smaller session with 1000 rows
            logger.info("Creating small session with 1000 rows...");
            long smallSessionStart = System.currentTimeMillis();
            
            // Generate 1000 rows of data using the same schema
            List<Map<String, Object>> smallData = dataGeneratorService.generateData(1000, schema);
            
            // Create schema for small session
            tableService.createSchema(SMALL_SESSION_ID, schema);
            
            // Populate small session data
            tableService.populateData(SMALL_SESSION_ID, smallData);
            
            long smallSessionTime = System.currentTimeMillis() - smallSessionStart;
            logger.info("Small session created with 1000 rows in {} ms", smallSessionTime);
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            span.setAttribute("totalTimeMs", totalTime);
            logger.info("Data initialization completed in {} ms. Implementation: {}", 
                       totalTime, tableService.getImplementationType());
            logger.info("Main dataset ready: {} rows × {} columns", rowCount, schema.size());
            logger.info("Small dataset ready: 1000 rows × {} columns", schema.size());
            
        } catch (Exception e) {
            span.recordException(e);
            logger.error("Failed to initialize data", e);
            throw e;
        } finally {
            span.end();
        }
    }
}