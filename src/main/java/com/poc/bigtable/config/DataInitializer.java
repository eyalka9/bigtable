package com.poc.bigtable.config;

import com.poc.bigtable.model.ColumnDefinition;
import com.poc.bigtable.service.DataGeneratorService;
import com.poc.bigtable.service.TableService;
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
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting data initialization...");
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate schema
            long schemaStart = System.currentTimeMillis();
            List<ColumnDefinition> schema = dataGeneratorService.generateSchema();
            logger.info("Generated schema with {} columns in {} ms", 50, (System.currentTimeMillis() - schemaStart));
            
            // Generate sample data
            long dataGenStart = System.currentTimeMillis();
            logger.info("Generating {} rows of sample data...", rowCount);
            List<Map<String, Object>> data = dataGeneratorService.generateData(rowCount, schema);
            logger.info("Generated {} rows in {} ms", rowCount, (System.currentTimeMillis() - dataGenStart));
            
            // Load data into the table service
            long loadStart = System.currentTimeMillis();
            logger.info("Loading data into {} implementation...", tableService.getImplementationType());
            tableService.loadData(DEFAULT_SESSION_ID, data, schema);
            logger.info("Loaded data in {} ms", (System.currentTimeMillis() - loadStart));
            
            long endTime = System.currentTimeMillis();
            logger.info("Data initialization completed in {} ms. Implementation: {}", 
                       endTime - startTime, tableService.getImplementationType());
            logger.info("Dataset ready: {} rows × {} columns", rowCount, 50);
            
        } catch (Exception e) {
            logger.error("Failed to initialize data", e);
            throw e;
        }
    }
}