package com.api.gateway.dto;

import com.api.gateway.domain.Processo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado do processo em memoria")
public class ProcessoResponse {

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String token;

    @Schema(description = "EM_EXECUCAO, FINALIZADO ou ERRO", example = "EM_EXECUCAO")
    private String status;

    private String mensagem;

    public static ProcessoResponse de(Processo processo) {
        ProcessoResponse response = new ProcessoResponse();
        response.token = processo.getToken();
        response.status = processo.getStatus().name();
        response.mensagem = processo.getMensagem();
        return response;
    }

    public String getToken() {
        return token;
    }

    public String getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }
}
