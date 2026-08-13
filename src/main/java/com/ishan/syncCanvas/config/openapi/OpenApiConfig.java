package com.ishan.syncCanvas.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI syncEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SyncCanvas API")
                        .description("REST + STOMP API for SyncCanvas's real-time collaborative canvas. "
                                + "STOMP endpoints (/ws, /app/boards/{boardId}/operations, /topic/boards/{boardId}) "
                                + "aren't representable here — see the collaboration module for the operation contract.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
