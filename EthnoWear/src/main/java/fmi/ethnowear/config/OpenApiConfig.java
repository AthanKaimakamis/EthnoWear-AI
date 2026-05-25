package fmi.ethnowear.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ethnoWearOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EthnoWear API")
                        .description("Ontology-driven API for Bulgarian embroidery interpretation and exploration.")
                        .version("0.0.1"));
    }
}
