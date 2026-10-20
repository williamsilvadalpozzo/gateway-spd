package com.api.gateway.service;

import com.api.gateway.domain.RequisicaoHttp;

public class ResultadoCriacao {

    private final RequisicaoHttp requisicao;
    private final boolean aceita;

    public ResultadoCriacao(RequisicaoHttp requisicao, boolean aceita) {
        this.requisicao = requisicao;
        this.aceita = aceita;
    }

    public RequisicaoHttp getRequisicao() {
        return requisicao;
    }

    public boolean isAceita() {
        return aceita;
    }
}
