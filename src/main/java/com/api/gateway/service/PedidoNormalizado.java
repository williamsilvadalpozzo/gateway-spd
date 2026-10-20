package com.api.gateway.service;

import com.api.gateway.domain.TipoCorpoHttp;
import com.api.gateway.domain.TipoRetornoHttp;
import com.api.gateway.dto.ParNomeValor;
import java.util.List;

public class PedidoNormalizado {

    private final String metodo;
    private final String url;
    private final List<ParNomeValor> parametros;
    private final List<ParNomeValor> headers;
    private final TipoCorpoHttp tipoCorpo;
    private final byte[] corpo;
    private final String contentType;
    private final TipoRetornoHttp tipoRetorno;
    private final int timeoutSegundos;
    private final String chave;
    private final boolean assincrono;
    private final String processo;
    private final String lote;
    private final boolean usarCertificado;
    private final List<String> certificadosDestino;

    public PedidoNormalizado(String metodo, String url, List<ParNomeValor> parametros, List<ParNomeValor> headers,
            TipoCorpoHttp tipoCorpo, byte[] corpo, String contentType, TipoRetornoHttp tipoRetorno,
            int timeoutSegundos, String chave, boolean assincrono, String processo, String lote,
            boolean usarCertificado, List<String> certificadosDestino) {
        this.metodo = metodo;
        this.url = url;
        this.parametros = parametros;
        this.headers = headers;
        this.tipoCorpo = tipoCorpo;
        this.corpo = corpo;
        this.contentType = contentType;
        this.tipoRetorno = tipoRetorno;
        this.timeoutSegundos = timeoutSegundos;
        this.chave = chave;
        this.assincrono = assincrono;
        this.processo = processo;
        this.lote = lote;
        this.usarCertificado = usarCertificado;
        this.certificadosDestino = certificadosDestino;
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

    public TipoCorpoHttp getTipoCorpo() {
        return tipoCorpo;
    }

    public byte[] getCorpo() {
        return corpo;
    }

    public String getContentType() {
        return contentType;
    }

    public TipoRetornoHttp getTipoRetorno() {
        return tipoRetorno;
    }

    public int getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public String getChave() {
        return chave;
    }

    public boolean isAssincrono() {
        return assincrono;
    }

    public String getProcesso() {
        return processo;
    }

    public String getLote() {
        return lote;
    }

    public boolean isUsarCertificado() {
        return usarCertificado;
    }

    public List<String> getCertificadosDestino() {
        return certificadosDestino;
    }
}
