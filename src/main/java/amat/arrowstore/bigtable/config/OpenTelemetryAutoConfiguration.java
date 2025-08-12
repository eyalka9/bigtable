package amat.arrowstore.bigtable.config;

import io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.annotations.InstrumentationAnnotationsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

@Configuration
@AutoConfigureBefore(InstrumentationAnnotationsAutoConfiguration.class)
public class OpenTelemetryAutoConfiguration {

    @Bean
    @Primary
    public ParameterNameDiscoverer otelParameterNameDiscoverer() {
        return new DefaultParameterNameDiscoverer();
    }
}