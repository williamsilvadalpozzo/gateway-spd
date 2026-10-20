package com.api.gateway.http;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class HeadersHttp {

    private static final Set<String> BLOQUEADOS = new HashSet<String>();

    static {
        Collections.addAll(BLOQUEADOS,
                "host",
                "content-length",
                "connection",
                "keep-alive",
                "proxy-connection",
                "transfer-encoding",
                "upgrade",
                "te",
                "trailer");
    }

    private HeadersHttp() {
    }

    public static boolean bloqueado(String nome) {
        if (nome == null) {
            return false;
        }
        return BLOQUEADOS.contains(nome.trim().toLowerCase(Locale.ROOT));
    }
}
