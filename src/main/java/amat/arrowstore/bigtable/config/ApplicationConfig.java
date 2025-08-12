package amat.arrowstore.bigtable.config;

import amat.arrowstore.bigtable.service.TableService;
import amat.arrowstore.bigtable.service.ArrowTableService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ApplicationConfig {
    
    @Value("${bigtable.implementation:h2}")
    private String implementation;
    
    @Bean
    @Primary
    public TableService tableService() {
        switch (implementation.toLowerCase()) {
            case "arrow":
            default:
                return new ArrowTableService();
        }
    }
}