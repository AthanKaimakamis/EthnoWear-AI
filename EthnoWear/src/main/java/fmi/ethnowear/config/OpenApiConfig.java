package fmi.ethnowear.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI ethnoWearOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .info(new Info()
                        .title("EthnoWear API")
                        .description("Ontology-driven API for Bulgarian embroidery interpretation and exploration.")
                        .version("0.0.1"));
    }

    @Bean
    public OperationCustomizer adminBearerSecurity() {
        return (operation, handlerMethod) -> {
            if(handlerMethod.getBeanType().getPackageName().contains(".admin"))
                operation.addSecurityItem(
                        new SecurityRequirement().addList(BEARER_AUTH)
                );

            return operation;
        };
    }
}
