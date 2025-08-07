package com.poc.bigtable.config;

import com.poc.bigtable.service.TableService;
import com.poc.bigtable.service.H2TableService;
import com.poc.bigtable.service.ArrowTableService;
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
                return new ArrowTableService();
            case "h2":
            default:
                return new H2TableService();
        }
    }
}