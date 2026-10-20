package com.api.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Par de nome e valor, usado em parametro e header")
public class ParNomeValor {

    @Schema(example = "pagina")
    private String nome;

    @Schema(example = "1")
    private String valor;

    public ParNomeValor() {
    }

    public ParNomeValor(String nome, String valor) {
        this.nome = nome;
        this.valor = valor;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
