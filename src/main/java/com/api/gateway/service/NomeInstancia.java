package com.api.gateway.service;

import com.api.gateway.config.GatewayProperties;
import java.net.InetAddress;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NomeInstancia {

    private final String nome;

    public NomeInstancia(GatewayProperties properties) {
        String configurado = properties.getInstancia();
        if (configurado != null && !configurado.trim().isEmpty()) {
            nome = configurado.trim();
            return;
        }
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "gateway";
        }
        nome = host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String get() {
        return nome;
    }
}
