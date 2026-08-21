package fmi.ethnowear.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String WORKER_AUTH = "workerAuth";

    @Bean
    public OpenAPI ethnoWearOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                )
                .addSecuritySchemes(
                        WORKER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("Internal worker authentication using: Worker <token>")
                );

        return new OpenAPI()
                .components(components)
                .info(new Info()
                        .title("EthnoWear API")
                        .description("Ontology-driven API for Bulgarian embroidery interpretation and exploration.")
                        .version("0.0.1"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/internal/worker/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalWorkerApi() {
        return GroupedOpenApi.builder()
                .group("internal-worker")
                .pathsToMatch("/api/internal/worker/**")
                .addOpenApiCustomizer(openApi -> openApi.info(
                        new Info()
                                .title("EthnoWear Internal Worker API")
                                .description("Protected API used by the containerized document-processing worker.")
                                .version("1.0")
                ))
                .build();
    }

    @Bean
    public OperationCustomizer endpointSecurity() {
        return (operation, handlerMethod) -> {
            String packageName = handlerMethod
                    .getBeanType()
                    .getPackageName();

            if (packageName.contains(".admin"))
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));

            if (packageName.contains(".worker.internal"))
                operation.addSecurityItem(new SecurityRequirement().addList(WORKER_AUTH));

            return operation;
        };
    }
}