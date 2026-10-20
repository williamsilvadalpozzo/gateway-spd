package com.api.gateway.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LimiteCorpoHttp implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(new org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                connector.setMaxPostSize(-1);
                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol) {
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setMaxSwallowSize(-1);
                }
            }
        });
    }
}
