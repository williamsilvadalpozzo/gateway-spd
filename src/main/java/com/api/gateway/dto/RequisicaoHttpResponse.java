package com.api.gateway.dto;

import com.api.gateway.domain.RequisicaoHttp;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id", "status", "assincrono", "processo", "lote", "chaveIdempotencia",
        "metodo", "url", "tipoRetorno", "httpStatus", "mensagem", "contentType",
        "tamanho", "certificadoArquivo", "criadoEm", "iniciadoEm", "finalizadoEm"
})
@Schema(description = "Estado de uma requisicao HTTP")
public class RequisicaoHttpResponse {

    private Long id;
    private String status;
    private Boolean assincrono;
    private String processo;
    private String lote;
    private String chaveIdempotencia;
    private String metodo;
    private String url;
    private String tipoRetorno;
    private Integer httpStatus;
    private String mensagem;
    private String contentType;
    private Long tamanho;
    private String certificadoArquivo;
    private OffsetDateTime criadoEm;
    private OffsetDateTime iniciadoEm;
    private OffsetDateTime finalizadoEm;

    public static RequisicaoHttpResponse de(RequisicaoHttp requisicao) {
        RequisicaoHttpResponse resposta = new RequisicaoHttpResponse();
        resposta.id = requisicao.getId();
        resposta.status = requisicao.getStatus() == null ? null : requisicao.getStatus().name();
        resposta.assincrono = requisicao.isAssincrono();
        resposta.processo = requisicao.getProcesso();
        resposta.lote = requisicao.getLote();
        resposta.chaveIdempotencia = requisicao.getChaveIdempotencia();
        resposta.metodo = requisicao.getMetodo();
        resposta.url = requisicao.getUrl();
        resposta.tipoRetorno = requisicao.getTipoRetorno() == null ? null : requisicao.getTipoRetorno().name();
        resposta.httpStatus = requisicao.getHttpStatus();
        resposta.mensagem = requisicao.getMensagemErro();
        resposta.contentType = requisicao.getContentTypeResposta();
        resposta.tamanho = requisicao.getTamanhoResposta();
        resposta.certificadoArquivo = requisicao.getCertificadoArquivo();
        resposta.criadoEm = offset(requisicao.getCriadoEm());
        resposta.iniciadoEm = offset(requisicao.getIniciadoEm());
        resposta.finalizadoEm = offset(requisicao.getFinalizadoEm());
        return resposta;
    }

    private static OffsetDateTime offset(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getAssincrono() {
        return assincrono;
    }

    public String getProcesso() {
        return processo;
    }

    public String getLote() {
        return lote;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public String getMetodo() {
        return metodo;
    }

    public String getUrl() {
        return url;
    }

    public String getTipoRetorno() {
        return tipoRetorno;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getTamanho() {
        return tamanho;
    }

    public String getCertificadoArquivo() {
        return certificadoArquivo;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getIniciadoEm() {
        return iniciadoEm;
    }

    public OffsetDateTime getFinalizadoEm() {
        return finalizadoEm;
    }
}
