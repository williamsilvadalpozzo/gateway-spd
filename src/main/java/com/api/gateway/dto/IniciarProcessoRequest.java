package com.api.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Pedido para chamar um fluxo web do Maker")
public class IniciarProcessoRequest {

    @NotBlank
    @Schema(example = "http://servidor/fluxo", required = true)
    private String url;

    @NotBlank
    @Schema(description = "Texto enviado ao fluxo. O gateway nao interpreta o conteudo.", example = "{\"devolucao\":754}", required = true)
    private String texto;

    @Schema(description = "Tempo maximo em segundos. Sem este campo, vale 4 horas. O teto e 24 horas.", example = "14400")
    private Long tempoExecucaoSegundos;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Long getTempoExecucaoSegundos() {
        return tempoExecucaoSegundos;
    }

    public void setTempoExecucaoSegundos(Long tempoExecucaoSegundos) {
        this.tempoExecucaoSegundos = tempoExecucaoSegundos;
    }
}
