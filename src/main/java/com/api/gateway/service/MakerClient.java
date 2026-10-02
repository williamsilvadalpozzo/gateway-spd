package com.api.gateway.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MakerClient {

    private static final Logger logger = LoggerFactory.getLogger(MakerClient.class);
    private static final int CONNECT_TIMEOUT_MS = 10000;

    public void chamar(String urlDestino, String texto, String token, int readTimeoutMs) {
        HttpURLConnection connection = null;
        boolean enviado = false;
        try {
            String corpo = "texto=" + URLEncoder.encode(texto == null ? "" : texto, "UTF-8")
                    + "&token=" + URLEncoder.encode(token == null ? "" : token, "UTF-8");
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(urlDestino).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(Math.max(readTimeoutMs, 1));
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Connection", "close");
            connection.setFixedLengthStreamingMode(bytes.length);

            OutputStream out = connection.getOutputStream();
            try {
                out.write(bytes);
                out.flush();
            } finally {
                out.close();
            }
            enviado = true;

            int status = connection.getResponseCode();
            discard(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status >= 300) {
                throw new ChamadaMakerException("Maker respondeu HTTP " + status);
            }
        } catch (ChamadaMakerException e) {
            throw e;
        } catch (Exception e) {
            if (enviado) {
                logger.warn("Chamada ao Maker enviada sem resposta util. O processo segue ate o callback. causa={}",
                        e.getMessage());
                return;
            }
            throw new ChamadaMakerException(mensagem(e, "Falha ao chamar o Maker"), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String mensagem(Exception e, String padrao) {
        if (e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return padrao;
        }
        return e.getMessage();
    }

    private void discard(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            try {
                while (reader.readLine() != null) {
                    // A resposta do Maker nao encerra o processo.
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
            // O aviso de conclusao chega em outro endpoint.
        }
    }
}
