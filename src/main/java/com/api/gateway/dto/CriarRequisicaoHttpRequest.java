package com.api.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Descricao de uma chamada HTTP a executar")
public class CriarRequisicaoHttpRequest {

    @Schema(example = "POST", required = true)
    private String metodo;

    @Schema(example = "https://api.exemplo.com/recurso/123", required = true)
    private String url;

    private List<ParNomeValor> parametros;
    private List<ParNomeValor> headers;

    @Schema(example = "JSON", required = true)
    private String tipoCorpo;

    @Schema(example = "{\"campo\":\"valor\"}")
    private String corpoTexto;

    private String corpoBase64;

    @Schema(example = "AUTO")
    private String tipoRetorno;

    @Schema(example = "120", required = true)
    private Integer timeoutSegundos;

    @Schema(example = "pedido-123")
    private String chaveIdempotencia;

    @Schema(example = "true", required = true)
    private Boolean assincrono;

    @Schema(example = "NFSE_ADN")
    private String processo;

    @Schema(example = "filial-002-nsu-123456")
    private String lote;

    @Schema(example = "false")
    private Boolean usarCertificado;

    private List<String> certificadosDestino;

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<ParNomeValor> getParametros() {
        return parametros;
    }

    public void setParametros(List<ParNomeValor> parametros) {
        this.parametros = parametros;
    }

    public List<ParNomeValor> getHeaders() {
        return headers;
    }

    public void setHeaders(List<ParNomeValor> headers) {
        this.headers = headers;
    }

    public String getTipoCorpo() {
        return tipoCorpo;
    }

    public void setTipoCorpo(String tipoCorpo) {
        this.tipoCorpo = tipoCorpo;
    }

    public String getCorpoTexto() {
        return corpoTexto;
    }

    public void setCorpoTexto(String corpoTexto) {
        this.corpoTexto = corpoTexto;
    }

    public String getCorpoBase64() {
        return corpoBase64;
    }

    public void setCorpoBase64(String corpoBase64) {
        this.corpoBase64 = corpoBase64;
    }

    public String getTipoRetorno() {
        return tipoRetorno;
    }

    public void setTipoRetorno(String tipoRetorno) {
        this.tipoRetorno = tipoRetorno;
    }

    public Integer getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public void setTimeoutSegundos(Integer timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public Boolean getAssincrono() {
        return assincrono;
    }

    public void setAssincrono(Boolean assincrono) {
        this.assincrono = assincrono;
    }

    public String getProcesso() {
        return processo;
    }

    public void setProcesso(String processo) {
        this.processo = processo;
    }

    public String getLote() {
        return lote;
    }

    public void setLote(String lote) {
        this.lote = lote;
    }

    public Boolean getUsarCertificado() {
        return usarCertificado;
    }

    public void setUsarCertificado(Boolean usarCertificado) {
        this.usarCertificado = usarCertificado;
    }

    public List<String> getCertificadosDestino() {
        return certificadosDestino;
    }

    public void setCertificadosDestino(List<String> certificadosDestino) {
        this.certificadosDestino = certificadosDestino;
    }
}
