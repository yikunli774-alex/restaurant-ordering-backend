package io.github.yikunli774.ordering.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Names and versions the auto-generated API documentation. springdoc serves it
 * at /v3/api-docs (JSON) and /swagger-ui.html (interactive page) for the frontend.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI restaurantOrderingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Restaurant Ordering API")
                .version("v1")
                .description("Concurrency-safe restaurant ordering backend"));
    }
}
