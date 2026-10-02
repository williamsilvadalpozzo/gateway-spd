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
                .description("Redirecionamento assincrono de fluxo do Maker e execucao de procedure. "
                        + "Saude e versao em /actuator/health e /actuator/info.")
                .version("1.0.0"));
    }
}
