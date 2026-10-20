package com.api.gateway.http;

import com.api.gateway.dto.ParNomeValor;
import java.util.Collections;
import java.util.List;

public class ChamadaHttp {

    private final long id;
    private final String metodo;
    private final String url;
    private final List<ParNomeValor> parametros;
    private final List<ParNomeValor> headers;
    private final byte[] corpo;
    private final String contentType;
    private final int timeoutSegundos;
    private final boolean usarCertificado;
    private final List<String> certificadosDestino;
    private final int maxRedirects;

    public ChamadaHttp(long id, String metodo, String url, List<ParNomeValor> parametros,
            List<ParNomeValor> headers, byte[] corpo, String contentType, int timeoutSegundos,
            boolean usarCertificado, List<String> certificadosDestino, int maxRedirects) {
        this.id = id;
        this.metodo = metodo;
        this.url = url;
        this.parametros = parametros == null ? Collections.<ParNomeValor>emptyList() : parametros;
        this.headers = headers == null ? Collections.<ParNomeValor>emptyList() : headers;
        this.corpo = corpo;
        this.contentType = contentType;
        this.timeoutSegundos = timeoutSegundos;
        this.usarCertificado = usarCertificado;
        this.certificadosDestino = certificadosDestino == null
                ? Collections.<String>emptyList() : certificadosDestino;
        this.maxRedirects = maxRedirects;
    }

    public long getId() {
        return id;
    }

    public String getMetodo() {
        return metodo;
    }

    public String getUrl() {
        return url;
    }

    public List<ParNomeValor> getParametros() {
        return parametros;
    }

    public List<ParNomeValor> getHeaders() {
        return headers;
    }

    public byte[] getCorpo() {
        return corpo;
    }

    public String getContentType() {
        return contentType;
    }

    public int getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public boolean isUsarCertificado() {
        return usarCertificado;
    }

    public List<String> getCertificadosDestino() {
        return certificadosDestino;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }
}
