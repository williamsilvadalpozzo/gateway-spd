package com.api.gateway.dto;

import com.api.gateway.domain.StatusProcesso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Aviso do fluxo Maker quando o processo termina")
public class ConclusaoRequest {

    @NotNull
    @Schema(description = "FINALIZADO ou ERRO", example = "FINALIZADO", required = true)
    private StatusProcesso status;

    @Schema(example = "Nota gerada")
    private String mensagem;

    public StatusProcesso getStatus() {
        return status;
    }

    public void setStatus(StatusProcesso status) {
        this.status = status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}
