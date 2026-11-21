package com.example.reconciliation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI reconciliationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Reconciliation API")
                        .version("0.1.0")
                        .description("""
                                File-driven reconciliation with manual maker-checker-signer approval.
                                Upload or schedule a CSV, inspect the transaction queue, find candidates,
                                then submit, check, and sign a match.
                                """))
                .components(new Components()
                        .addSecuritySchemes("X-User", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User")
                                .description("Demo user identity"))
                        .addSecuritySchemes("X-Role", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Role")
                                .description("MAKER, CHECKER, or SIGNER")));
    }
}
