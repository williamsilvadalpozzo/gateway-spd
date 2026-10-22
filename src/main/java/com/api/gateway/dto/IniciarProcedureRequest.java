package com.api.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Pedido para executar uma procedure Oracle")
public class IniciarProcedureRequest {

    @NotBlank
    @Schema(description = "Nome, schema.nome ou schema.pacote.nome", example = "DALPOZZO.MINHA_PROCEDURE", required = true)
    private String procedure;

    @Schema(description = "Parametros de entrada, como texto, na ordem da procedure", example = "[\"1\",\"2026-09-30\"]")
    private List<String> parametros = new ArrayList<String>();

    @Schema(description = "Tempo maximo em segundos. Sem este campo, vale 4 horas. O teto e 24 horas.", example = "14400")
    private Long tempoExecucaoSegundos;

    public String getProcedure() {
        return procedure;
    }

    public void setProcedure(String procedure) {
        this.procedure = procedure;
    }

    public List<String> getParametros() {
        return parametros;
    }

    public void setParametros(List<String> parametros) {
        this.parametros = parametros == null ? new ArrayList<String>() : parametros;
    }

    public Long getTempoExecucaoSegundos() {
        return tempoExecucaoSegundos;
    }

    public void setTempoExecucaoSegundos(Long tempoExecucaoSegundos) {
        this.tempoExecucaoSegundos = tempoExecucaoSegundos;
    }
}
