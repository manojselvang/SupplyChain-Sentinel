package com.supplychainsentinel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration.
 * Swagger UI available at /swagger-ui.html
 */
@Configuration
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
