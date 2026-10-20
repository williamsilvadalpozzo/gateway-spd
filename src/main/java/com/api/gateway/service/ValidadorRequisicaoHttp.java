package com.api.gateway.service;

import com.api.gateway.domain.TipoCorpoHttp;
import com.api.gateway.domain.TipoRetornoHttp;
import com.api.gateway.dto.CriarRequisicaoHttpRequest;
import com.api.gateway.dto.ParNomeValor;
import com.api.gateway.http.CertificadoPem;
import com.api.gateway.http.HeadersHttp;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ValidadorRequisicaoHttp {

    private static final int URL_MAX = 4000;
    private static final int CHAVE_MAX = 200;
    private static final int PROCESSO_MAX = 200;
    private static final int LOTE_MAX = 400;

    public PedidoNormalizado validar(CriarRequisicaoHttpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requisicao invalida");
        }
        String metodo = metodo(request.getMetodo());
        String url = url(request.getUrl());
        if (request.getAssincrono() == null) {
            throw new IllegalArgumentException("assincrono e obrigatorio");
        }
        TipoCorpoHttp tipoCorpo = enumCorpo(request.getTipoCorpo());
        TipoRetornoHttp tipoRetorno = enumRetorno(request.getTipoRetorno());
        if (request.getTimeoutSegundos() == null) {
            throw new IllegalArgumentException("timeoutSegundos e obrigatorio");
        }
        if (request.getTimeoutSegundos().intValue() <= 0) {
            throw new IllegalArgumentException("timeoutSegundos deve ser maior que zero");
        }
        List<ParNomeValor> parametros = pares(request.getParametros(), "parametro");
        List<ParNomeValor> headers = headers(request.getHeaders());
        if (("GET".equals(metodo) || "HEAD".equals(metodo)) && tipoCorpo != TipoCorpoHttp.NENHUM) {
            throw new IllegalArgumentException("GET e HEAD nao enviam corpo");
        }
        byte[] corpo = corpo(tipoCorpo, request.getCorpoTexto(), request.getCorpoBase64());
        String contentType = corpo == null ? null : contentType(headers, tipoCorpo);
        return new PedidoNormalizado(
                metodo,
                url,
                parametros,
                headers,
                tipoCorpo,
                corpo,
                contentType,
                tipoRetorno,
                request.getTimeoutSegundos().intValue(),
                texto(request.getChaveIdempotencia(), CHAVE_MAX, "chaveIdempotencia"),
                request.getAssincrono().booleanValue(),
                texto(request.getProcesso(), PROCESSO_MAX, "processo"),
                texto(request.getLote(), LOTE_MAX, "lote"),
                request.getUsarCertificado() != null && request.getUsarCertificado().booleanValue(),
                pems(request.getCertificadosDestino()));
    }

    private String metodo(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("metodo e obrigatorio");
        }
        String metodo = valor.trim().toUpperCase(Locale.ROOT);
        if (!"GET".equals(metodo) && !"POST".equals(metodo) && !"PUT".equals(metodo)
                && !"PATCH".equals(metodo) && !"DELETE".equals(metodo) && !"HEAD".equals(metodo)
                && !"OPTIONS".equals(metodo)) {
            throw new IllegalArgumentException("metodo HTTP invalido");
        }
        return metodo;
    }

    private String url(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("url e obrigatoria");
        }
        String url = valor.trim();
        if (url.length() > URL_MAX || url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0 || url.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("A url deve usar http ou https");
        }
        try {
            URL analisada = new URL(url);
            String protocolo = analisada.getProtocol();
            if (!"http".equalsIgnoreCase(protocolo) && !"https".equalsIgnoreCase(protocolo)) {
                throw new IllegalArgumentException("A url deve usar http ou https");
            }
            if (analisada.getHost() == null || analisada.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException("A url deve usar http ou https");
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("A url deve usar http ou https");
        }
        return url;
    }

    private TipoCorpoHttp enumCorpo(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("tipoCorpo e obrigatorio");
        }
        try {
            return TipoCorpoHttp.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipoCorpo invalido");
        }
    }

    private TipoRetornoHttp enumRetorno(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return TipoRetornoHttp.AUTO;
        }
        try {
            return TipoRetornoHttp.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipoRetorno invalido");
        }
    }

    private List<ParNomeValor> pares(List<ParNomeValor> origem, String rotulo) {
        if (origem == null || origem.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParNomeValor> pares = new ArrayList<ParNomeValor>();
        for (int i = 0; i < origem.size(); i++) {
            ParNomeValor par = origem.get(i);
            if (par == null || par.getNome() == null || par.getNome().trim().isEmpty()) {
                throw new IllegalArgumentException("nome do " + rotulo + " e obrigatorio");
            }
            String nome = par.getNome().trim();
            if (nome.indexOf('\r') >= 0 || nome.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(rotulo + " invalido");
            }
            String valor = par.getValor();
            if (valor != null && (valor.indexOf('\r') >= 0 || valor.indexOf('\n') >= 0)) {
                throw new IllegalArgumentException(rotulo + " invalido");
            }
            pares.add(new ParNomeValor(nome, valor));
        }
        return pares;
    }

    private List<ParNomeValor> headers(List<ParNomeValor> origem) {
        List<ParNomeValor> headers = pares(origem, "header");
        for (int i = 0; i < headers.size(); i++) {
            String nome = headers.get(i).getNome();
            if (HeadersHttp.bloqueado(nome)) {
                throw new IllegalArgumentException("O header " + nome + " nao pode ser enviado");
            }
        }
        return headers;
    }

    private byte[] corpo(TipoCorpoHttp tipo, String texto, String base64) {
        if (tipo == TipoCorpoHttp.NENHUM) {
            return null;
        }
        if (tipo == TipoCorpoHttp.BINARIO_BASE64) {
            if (base64 == null || base64.trim().isEmpty()) {
                throw new IllegalArgumentException("corpoBase64 e obrigatorio");
            }
            try {
                return Base64.getMimeDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("corpoBase64 invalido");
            }
        }
        String conteudo = texto == null ? "" : texto;
        return conteudo.getBytes(StandardCharsets.UTF_8);
    }

    private String contentType(List<ParNomeValor> headers, TipoCorpoHttp tipo) {
        for (int i = 0; i < headers.size(); i++) {
            if ("Content-Type".equalsIgnoreCase(headers.get(i).getNome())) {
                String valor = headers.get(i).getValor();
                return valor == null || valor.trim().isEmpty() ? padrao(tipo) : valor.trim();
            }
        }
        return padrao(tipo);
    }

    private String padrao(TipoCorpoHttp tipo) {
        if (tipo == TipoCorpoHttp.JSON) {
            return "application/json; charset=UTF-8";
        }
        if (tipo == TipoCorpoHttp.TEXTO) {
            return "text/plain; charset=UTF-8";
        }
        if (tipo == TipoCorpoHttp.XML) {
            return "application/xml; charset=UTF-8";
        }
        if (tipo == TipoCorpoHttp.FORM_URLENCODED) {
            return "application/x-www-form-urlencoded; charset=UTF-8";
        }
        return "application/octet-stream";
    }

    private String texto(String valor, int maximo, String campo) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String texto = valor.trim();
        if (texto.length() > maximo) {
            throw new IllegalArgumentException(campo + " excede o tamanho");
        }
        return texto;
    }

    private List<String> pems(List<String> origem) {
        if (origem == null || origem.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> pems = new ArrayList<String>();
        for (int i = 0; i < origem.size(); i++) {
            String pem = origem.get(i);
            if (pem == null || pem.trim().isEmpty()) {
                throw new IllegalArgumentException("Certificado de destino invalido");
            }
            CertificadoPem.validar(pem);
            pems.add(pem.trim());
        }
        return pems;
    }
}
