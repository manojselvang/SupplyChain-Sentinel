package com.supplychainsentinel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration.
 *
 * <p>The bean is only registered when {@code springdoc.api-docs.enabled=true}
 * (the default for all non-production profiles).  In the {@code prod} profile
 * {@code application-prod.yml} sets {@code springdoc.api-docs.enabled=false},
 * which prevents this bean from loading and means the {@code /api-docs},
 * {@code /v3/api-docs}, and {@code /swagger-ui} endpoints return 404.</p>
 *
 * <p>The {@code SecurityConfig} {@code permitAll()} entries for Swagger paths
 * become dead routes in production (no servlet is registered for them), so
 * they are harmless but should be understood as development scaffolding only.</p>
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    @Bean
    public OpenAPI supplyChainSentinelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SupplyChain Sentinel API")
                        .description("Software Supply Chain Risk and Release Assurance Platform")
                        .version("v1")
                        .contact(new Contact()
                                .name("SupplyChain Sentinel")
                                .url("https://github.com/supplychainsentinel"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
