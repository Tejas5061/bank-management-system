package com.bankms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI bankOpenApi(AppProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.bank().name() + " - Bank Management System API")
                        .version("v1")
                        .description("""
                                REST API for customers, tellers and administrators.

                                1. Call `POST /api/v1/auth/login` with a demo user (see README).
                                2. Click **Authorize** and paste the `accessToken`.
                                3. Money-moving endpoints require an `Idempotency-Key` header (any unique string, e.g. a UUID).
                                """))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
