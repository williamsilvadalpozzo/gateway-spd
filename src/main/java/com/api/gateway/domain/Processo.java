package com.api.gateway.domain;

import java.time.Instant;

public class Processo {

    public static final String MENSAGEM_PRAZO = "expirou sem conclusao";

    private final String token;
    private final Instant criadoEm;
    private final Instant limiteExecucao;
    private String origem;
    private String destino;
    private volatile StatusProcesso status;
    private volatile String mensagem;
    private volatile Instant finalizadoEm;

    public Processo(String token, Instant criadoEm, Instant limiteExecucao) {
        this.token = token;
        this.criadoEm = criadoEm;
        this.limiteExecucao = limiteExecucao;
        this.status = StatusProcesso.EM_EXECUCAO;
    }

    public String getToken() {
        return token;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getLimiteExecucao() {
        return limiteExecucao;
    }

    public String getOrigem() {
        return origem;
    }

    public String getDestino() {
        return destino;
    }

    public void definirDestino(String origem, String destino) {
        this.origem = origem;
        this.destino = destino;
    }

    public StatusProcesso getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getFinalizadoEm() {
        return finalizadoEm;
    }

    public void concluir(StatusProcesso status, String mensagem, Instant quando) {
        this.status = status;
        this.mensagem = mensagem;
        this.finalizadoEm = quando;
    }
}
