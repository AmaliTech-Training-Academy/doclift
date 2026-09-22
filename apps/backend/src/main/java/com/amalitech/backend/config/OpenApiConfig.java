package com.amalitech.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docLiftOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DocLift API")
                        .version("v1")
                        .description(
                                "REST API for uploading and converting PDF documents."
                        ));
    }
}