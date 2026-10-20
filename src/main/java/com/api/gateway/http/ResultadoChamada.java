package com.api.gateway.http;

import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.dto.ParNomeValor;
import java.util.Collections;
import java.util.List;

public class ResultadoChamada {

    private final StatusRequisicaoHttp status;
    private final Integer httpStatus;
    private final byte[] corpo;
    private final String contentType;
    private final String charset;
    private final List<ParNomeValor> headers;
    private final String mensagem;
    private final String certificadoArquivo;

    public ResultadoChamada(StatusRequisicaoHttp status, Integer httpStatus, byte[] corpo,
            String contentType, String charset, List<ParNomeValor> headers, String mensagem,
            String certificadoArquivo) {
        this.status = status;
        this.httpStatus = httpStatus;
        this.corpo = corpo;
        this.contentType = contentType;
        this.charset = charset;
        this.headers = headers == null ? Collections.<ParNomeValor>emptyList() : headers;
        this.mensagem = mensagem;
        this.certificadoArquivo = certificadoArquivo;
    }

    public static ResultadoChamada finalizado(int httpStatus, byte[] corpo, String contentType,
            String charset, List<ParNomeValor> headers, String mensagem, String certificadoArquivo) {
        byte[] bytes = corpo == null ? new byte[0] : corpo;
        return new ResultadoChamada(StatusRequisicaoHttp.FINALIZADO, Integer.valueOf(httpStatus), bytes,
                contentType, charset, headers, mensagem, certificadoArquivo);
    }

    public static ResultadoChamada erro(String mensagem, String certificadoArquivo) {
        return new ResultadoChamada(StatusRequisicaoHttp.ERRO, null, null, null, null, null,
                mensagem, certificadoArquivo);
    }

    public static ResultadoChamada timeout(Integer httpStatus, byte[] parcial, String contentType,
            String mensagem, String certificadoArquivo) {
        byte[] gravar = parcial == null || parcial.length == 0 ? null : parcial;
        return new ResultadoChamada(StatusRequisicaoHttp.TIMEOUT, httpStatus, gravar, contentType, null,
                null, mensagem, certificadoArquivo);
    }

    public ResultadoChamada comCertificado(String certificadoArquivo) {
        return new ResultadoChamada(status, httpStatus, corpo, contentType, charset, headers, mensagem,
                certificadoArquivo);
    }

    public StatusRequisicaoHttp getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public byte[] getCorpo() {
        return corpo;
    }

    public String getContentType() {
        return contentType;
    }

    public String getCharset() {
        return charset;
    }

    public List<ParNomeValor> getHeaders() {
        return headers;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getCertificadoArquivo() {
        return certificadoArquivo;
    }
}
