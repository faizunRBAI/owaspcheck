package com.enterprise.idp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger metadata and the bearer-token security scheme. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI idpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Internal Developer Portal API")
                        .description("Catalog of projects, teams, environments and deployments "
                                + "for the enterprise developer platform.")
                        .version("1.0.0")
                        .contact(new Contact().name("Platform Engineering")
                                .email("platform@enterprise.example"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token returned by /api/v1/auth/login")));
    }
}
