package com.api.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Erro da API")
public class ErroResponse {

    @Schema(example = "Processo nao encontrado")
    private String mensagem;

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}
