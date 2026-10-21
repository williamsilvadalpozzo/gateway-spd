package com.api.gateway.domain;

import java.time.Instant;

public class RequisicaoHttp {

    private long id;
    private String chaveIdempotencia;
    private boolean assincrono;
    private String processo;
    private String lote;
    private StatusRequisicaoHttp status;
    private String instancia;
    private String metodo;
    private String url;
    private String parametrosJson;
    private String headersJson;
    private TipoCorpoHttp tipoCorpo;
    private String contentTypeRequisicao;
    private byte[] corpoRequisicao;
    private TipoRetornoHttp tipoRetorno;
    private int timeoutSegundos;
    private String certificadoArquivo;
    private boolean usarCertificado;
    private String certificadosDestino;
    private Integer httpStatus;
    private String contentTypeResposta;
    private String charsetResposta;
    private Long tamanhoResposta;
    private String mensagemErro;
    private String corpoErro;
    private Instant criadoEm;
    private Instant iniciadoEm;
    private Instant finalizadoEm;
    private Instant heartbeatEm;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public boolean isAssincrono() {
        return assincrono;
    }

    public void setAssincrono(boolean assincrono) {
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

    public StatusRequisicaoHttp getStatus() {
        return status;
    }

    public void setStatus(StatusRequisicaoHttp status) {
        this.status = status;
    }

    public String getInstancia() {
        return instancia;
    }

    public void setInstancia(String instancia) {
        this.instancia = instancia;
    }

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

    public String getParametrosJson() {
        return parametrosJson;
    }

    public void setParametrosJson(String parametrosJson) {
        this.parametrosJson = parametrosJson;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public TipoCorpoHttp getTipoCorpo() {
        return tipoCorpo;
    }

    public void setTipoCorpo(TipoCorpoHttp tipoCorpo) {
        this.tipoCorpo = tipoCorpo;
    }

    public String getContentTypeRequisicao() {
        return contentTypeRequisicao;
    }

    public void setContentTypeRequisicao(String contentTypeRequisicao) {
        this.contentTypeRequisicao = contentTypeRequisicao;
    }

    public byte[] getCorpoRequisicao() {
        return corpoRequisicao;
    }

    public void setCorpoRequisicao(byte[] corpoRequisicao) {
        this.corpoRequisicao = corpoRequisicao;
    }

    public TipoRetornoHttp getTipoRetorno() {
        return tipoRetorno;
    }

    public void setTipoRetorno(TipoRetornoHttp tipoRetorno) {
        this.tipoRetorno = tipoRetorno;
    }

    public int getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public void setTimeoutSegundos(int timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }

    public String getCertificadoArquivo() {
        return certificadoArquivo;
    }

    public void setCertificadoArquivo(String certificadoArquivo) {
        this.certificadoArquivo = certificadoArquivo;
    }

    public boolean isUsarCertificado() {
        return usarCertificado;
    }

    public void setUsarCertificado(boolean usarCertificado) {
        this.usarCertificado = usarCertificado;
    }

    public String getCertificadosDestino() {
        return certificadosDestino;
    }

    public void setCertificadosDestino(String certificadosDestino) {
        this.certificadosDestino = certificadosDestino;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getContentTypeResposta() {
        return contentTypeResposta;
    }

    public void setContentTypeResposta(String contentTypeResposta) {
        this.contentTypeResposta = contentTypeResposta;
    }

    public String getCharsetResposta() {
        return charsetResposta;
    }

    public void setCharsetResposta(String charsetResposta) {
        this.charsetResposta = charsetResposta;
    }

    public Long getTamanhoResposta() {
        return tamanhoResposta;
    }

    public void setTamanhoResposta(Long tamanhoResposta) {
        this.tamanhoResposta = tamanhoResposta;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public void setMensagemErro(String mensagemErro) {
        this.mensagemErro = mensagemErro;
    }

    public String getCorpoErro() {
        return corpoErro;
    }

    public void setCorpoErro(String corpoErro) {
        this.corpoErro = corpoErro;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getIniciadoEm() {
        return iniciadoEm;
    }

    public void setIniciadoEm(Instant iniciadoEm) {
        this.iniciadoEm = iniciadoEm;
    }

    public Instant getFinalizadoEm() {
        return finalizadoEm;
    }

    public void setFinalizadoEm(Instant finalizadoEm) {
        this.finalizadoEm = finalizadoEm;
    }

    public Instant getHeartbeatEm() {
        return heartbeatEm;
    }

    public void setHeartbeatEm(Instant heartbeatEm) {
        this.heartbeatEm = heartbeatEm;
    }
}
