package com.api.gateway.http;

import com.api.gateway.dto.ParNomeValor;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public final class MontagemUrl {

    private MontagemUrl() {
    }

    public static String montar(String url, List<ParNomeValor> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            return url;
        }
        String base = url;
        String fragmento = "";
        int hash = base.indexOf('#');
        if (hash >= 0) {
            fragmento = base.substring(hash);
            base = base.substring(0, hash);
        }
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < parametros.size(); i++) {
            ParNomeValor par = parametros.get(i);
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(codificar(par.getNome()));
            query.append('=');
            query.append(codificar(par.getValor() == null ? "" : par.getValor()));
        }
        String separador = base.contains("?") ? "&" : "?";
        return base + separador + query + fragmento;
    }

    private static String codificar(String valor) {
        try {
            return URLEncoder.encode(valor, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
