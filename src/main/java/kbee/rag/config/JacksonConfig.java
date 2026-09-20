package kbee.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Boot 4 auto-configures Jackson 3
 * (tools.jackson.databind.ObjectMapper) and no longer
 * exposes a Jackson 2 ObjectMapper bean.
 *
 * The application code still uses Jackson 2
 * (com.fasterxml.jackson.databind.ObjectMapper),
 * so we define the bean explicitly here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {

        return new ObjectMapper()
                .configure(
                        DeserializationFeature
                                .FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                );
    }
}
