package com.api.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("gateway-spd")
                .description("Redirecionamento de fluxo do Maker, execucao de procedure e chamada HTTP com fila na base do Flow. "
                        + "A versao publicada fica em GET /versao. Saude em /actuator/health.")
                .version("1.0.4"));
    }
}
