package com.smartlearnly.backend.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Smart Learnly Backend API",
                version = "v1",
                description = "OpenAPI contract for Smart Learnly backend services using bearer JWT authentication.",
                contact = @Contact(name = "Smart Learnly Backend Team")
        )
)
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "Temporary development authentication for protected endpoints."
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Production-ready bearer JWT authentication for protected endpoints."
)
public class OpenApiConfig {

    @Bean
    OpenAPI smartLearnlyOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("Smart Learnly Backend API")
                        .version("v1")
                        .description("Smart Learnly backend contract for authentication, profile, and shared platform APIs.")
                        .license(new License().name("Internal project use")));
    }

    @Bean
    GroupedOpenApi allApiGroup() {
        return GroupedOpenApi.builder()
                .group("all")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    GroupedOpenApi authApiGroup() {
        return GroupedOpenApi.builder()
                .group("auth")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    @Bean
    GroupedOpenApi courseContentApiGroup() {
        return GroupedOpenApi.builder()
                .group("course-content")
                .pathsToMatch(
                        "/api/v1/admin/courses/**",
                        "/api/v1/admin/sections/**",
                        "/api/v1/admin/lessons/**",
                        "/api/v1/courses/**"
                )
                .build();
    }
}
