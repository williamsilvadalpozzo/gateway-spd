package com.api.gateway.http;

import java.sql.SQLException;

public final class DiagnosticoErro {

    private static final int LIMITE = 4000;

    private DiagnosticoErro() {
    }

    public static String comContexto(String resumo, ChamadaHttp chamada, String certificado, Throwable erro) {
        StringBuilder texto = new StringBuilder();
        texto.append(resumo == null || resumo.trim().isEmpty() ? "falha na chamada" : resumo.trim());
        texto.append(" | ").append(condicoes(chamada, certificado));
        String causas = cadeia(erro);
        if (!causas.isEmpty()) {
            texto.append(" | ").append(causas);
        }
        return limitar(texto.toString());
    }

    public static String cadeia(Throwable erro) {
        if (erro == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        Throwable atual = erro;
        int nivel = 0;
        while (atual != null && nivel < 6) {
            if (nivel > 0) {
                texto.append(" > ");
            }
            texto.append(atual.getClass().getSimpleName());
            if (atual instanceof SQLException) {
                texto.append(" codigo=").append(((SQLException) atual).getErrorCode());
            }
            String detalhe = limpar(atual.getMessage());
            if (!detalhe.isEmpty()) {
                texto.append(": ").append(detalhe);
            }
            Throwable causa = atual.getCause();
            if (causa == atual) {
                break;
            }
            atual = causa;
            nivel++;
        }
        return limitar(texto.toString());
    }

    private static String condicoes(ChamadaHttp chamada, String certificado) {
        StringBuilder texto = new StringBuilder();
        if (chamada == null) {
            texto.append("chamada indisponivel");
        } else {
            texto.append(chamada.getMetodo() == null ? "?" : chamada.getMetodo());
            texto.append(' ').append(urlSemConsulta(chamada.getUrl()));
            texto.append(" | timeout ").append(chamada.getTimeoutSegundos()).append("s");
            texto.append(" | usarCertificado ").append(chamada.isUsarCertificado());
            texto.append(" | pems ").append(chamada.getCertificadosDestino().size());
            texto.append(" | parametros ").append(nomes(chamada));
        }
        texto.append(" | certificado ").append(certificado == null || certificado.trim().isEmpty()
                ? "nenhum" : certificado.trim());
        return texto.toString();
    }

    private static String nomes(ChamadaHttp chamada) {
        if (chamada.getParametros().isEmpty()) {
            return "nenhum";
        }
        StringBuilder nomes = new StringBuilder();
        for (int i = 0; i < chamada.getParametros().size(); i++) {
            if (i > 0) {
                nomes.append(',');
            }
            String nome = chamada.getParametros().get(i).getNome();
            nomes.append(nome == null || nome.trim().isEmpty() ? "?" : nome.trim());
        }
        return nomes.toString();
    }

    static String urlSemConsulta(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "url ausente";
        }
        String limpa = url.trim();
        int consulta = limpa.indexOf('?');
        if (consulta >= 0) {
            limpa = limpa.substring(0, consulta);
        }
        return limitar(limpa, 1000);
    }

    private static String limpar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto.replace('\r', ' ').replace('\n', ' ').replace("\u0000", "").trim();
        int consulta = limpo.indexOf('?');
        if (consulta >= 0) {
            limpo = limpo.substring(0, consulta).trim();
        }
        return limitar(limpo, 800);
    }

    private static String limitar(String texto) {
        return limitar(texto, LIMITE);
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
