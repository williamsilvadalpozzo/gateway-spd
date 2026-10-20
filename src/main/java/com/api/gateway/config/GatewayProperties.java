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
    private String instancia = "";
    private Certificados certificados = new Certificados();
    private Http http = new Http();

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

    public String getInstancia() {
        return instancia;
    }

    public void setInstancia(String instancia) {
        this.instancia = instancia;
    }

    public Certificados getCertificados() {
        return certificados;
    }

    public void setCertificados(Certificados certificados) {
        this.certificados = certificados;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public static class Certificados {
        private String path = "";
        private String senha = "";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSenha() {
            return senha;
        }

        public void setSenha(String senha) {
            this.senha = senha;
        }
    }

    public static class Http {
        private long filaIntervaloMs = 1000L;
        private long heartbeatIntervaloMs = 15000L;
        private long varreduraIntervaloMs = 300000L;
        private int maxRedirects = 0;

        public long getFilaIntervaloMs() {
            return filaIntervaloMs;
        }

        public void setFilaIntervaloMs(long filaIntervaloMs) {
            this.filaIntervaloMs = filaIntervaloMs;
        }

        public long getHeartbeatIntervaloMs() {
            return heartbeatIntervaloMs;
        }

        public void setHeartbeatIntervaloMs(long heartbeatIntervaloMs) {
            this.heartbeatIntervaloMs = heartbeatIntervaloMs;
        }

        public long getVarreduraIntervaloMs() {
            return varreduraIntervaloMs;
        }

        public void setVarreduraIntervaloMs(long varreduraIntervaloMs) {
            this.varreduraIntervaloMs = varreduraIntervaloMs;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }
    }
}
