package com.api.gateway.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class TextoResposta {

    private TextoResposta() {
    }

    public static boolean textual(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return true;
        }
        String tipo = contentType.toLowerCase(Locale.ROOT);
        return tipo.contains("json") || tipo.contains("xml") || tipo.contains("text")
                || tipo.contains("html") || tipo.contains("javascript") || tipo.contains("yaml")
                || tipo.contains("csv") || tipo.contains("urlencoded");
    }

    public static String comoTexto(byte[] corpo, String contentType, String charsetInformado) {
        if (corpo == null || !textual(contentType)) {
            return null;
        }
        return new String(corpo, charset(contentType, charsetInformado));
    }

    public static String charsetDe(String contentType) {
        if (contentType == null) {
            return null;
        }
        String[] partes = contentType.split(";");
        for (int i = 1; i < partes.length; i++) {
            String parte = partes[i].trim();
            if (parte.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                String charset = parte.substring("charset=".length()).trim().replace("\"", "");
                return charset.length() <= 40 ? charset : charset.substring(0, 40);
            }
        }
        return null;
    }

    public static Charset charset(String contentType, String charsetInformado) {
        String nome = charsetInformado == null || charsetInformado.trim().isEmpty()
                ? charsetDe(contentType) : charsetInformado.trim();
        if (nome == null || nome.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(nome);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
