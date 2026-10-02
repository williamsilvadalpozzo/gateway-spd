package com.api.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Duration retencaoAposFim = Duration.ofMinutes(5);
    private Duration tempoExecucaoPadrao = Duration.ofHours(4);
    private Duration tempoExecucaoMaximo = Duration.ofHours(24);

    public Duration getRetencaoAposFim() {
        return retencaoAposFim;
    }

    public void setRetencaoAposFim(Duration retencaoAposFim) {
        this.retencaoAposFim = retencaoAposFim;
    }

    public Duration getTempoExecucaoPadrao() {
        return tempoExecucaoPadrao;
    }

    public void setTempoExecucaoPadrao(Duration tempoExecucaoPadrao) {
        this.tempoExecucaoPadrao = tempoExecucaoPadrao;
    }

    public Duration getTempoExecucaoMaximo() {
        return tempoExecucaoMaximo;
    }

    public void setTempoExecucaoMaximo(Duration tempoExecucaoMaximo) {
        this.tempoExecucaoMaximo = tempoExecucaoMaximo;
    }

}
