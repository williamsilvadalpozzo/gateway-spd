package com.api.gateway.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.dto.ParNomeValor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;

class ClienteHttpApacheTest {

    @Test
    void enviaJsonEIgnoraHostInformado() throws Exception {
        AtomicReference<String> host = new AtomicReference<String>();
        AtomicReference<String> token = new AtomicReference<String>();
        AtomicReference<String> corpo = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            token.set(exchange.getRequestHeaders().getFirst("X-Token"));
            corpo.set(ler(exchange.getRequestBody()));
            byte[] resposta = "pronto".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, resposta.length);
            OutputStream saida = exchange.getResponseBody();
            saida.write(resposta);
            saida.close();
        });
        server.start();
        try {
            int porta = server.getAddress().getPort();
            ChamadaHttp chamada = new ChamadaHttp(1L, "POST", "http://127.0.0.1:" + porta + "/echo",
                    Collections.singletonList(new ParNomeValor("pagina", "1")),
                    java.util.Arrays.asList(new ParNomeValor("Host", "evil"), new ParNomeValor("X-Token", "abc")),
                    "{\"a\":1}".getBytes(StandardCharsets.UTF_8),
                    "application/json", 5, false, null, 0);
            ResultadoChamada resultado = cliente(new GatewayProperties()).executar(chamada);

            assertEquals(StatusRequisicaoHttp.FINALIZADO, resultado.getStatus());
            assertEquals(Integer.valueOf(200), resultado.getHttpStatus());
            assertEquals("pronto", new String(resultado.getCorpo(), StandardCharsets.UTF_8));
            assertEquals("{\"a\":1}", corpo.get());
            assertEquals("abc", token.get());
            assertTrue(host.get() == null || !host.get().contains("evil"));
            assertTrue(resultado.getHeaders().size() > 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void guardaErroHttpComoFinalizado() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/erro", exchange -> {
            byte[] resposta = "ausente".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, resposta.length);
            OutputStream saida = exchange.getResponseBody();
            saida.write(resposta);
            saida.close();
        });
        server.start();
        try {
            int porta = server.getAddress().getPort();
            ChamadaHttp chamada = new ChamadaHttp(2L, "GET", "http://127.0.0.1:" + porta + "/erro",
                    null, null, null, null, 5, false, null, 0);
            ResultadoChamada resultado = cliente(new GatewayProperties()).executar(chamada);

            assertEquals(StatusRequisicaoHttp.FINALIZADO, resultado.getStatus());
            assertEquals(Integer.valueOf(404), resultado.getHttpStatus());
            assertEquals("ausente", resultado.getMensagem());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void naoSegueRedirect() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vai", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/outro");
            exchange.sendResponseHeaders(302, -1);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            int porta = server.getAddress().getPort();
            ChamadaHttp chamada = new ChamadaHttp(3L, "GET", "http://127.0.0.1:" + porta + "/vai",
                    null, null, null, null, 5, false, null, 0);
            ResultadoChamada resultado = cliente(new GatewayProperties()).executar(chamada);

            assertEquals(StatusRequisicaoHttp.FINALIZADO, resultado.getStatus());
            assertEquals(Integer.valueOf(302), resultado.getHttpStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutViraTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lento", exchange -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            int porta = server.getAddress().getPort();
            ChamadaHttp chamada = new ChamadaHttp(4L, "GET", "http://127.0.0.1:" + porta + "/lento",
                    null, null, null, null, 1, false, null, 0);
            ResultadoChamada resultado = cliente(new GatewayProperties()).executar(chamada);

            assertEquals(StatusRequisicaoHttp.TIMEOUT, resultado.getStatus());
            assertEquals("tempo limite de 1 segundos", resultado.getMensagem());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void falhaDeCertificadoTentaOProximoArquivo() throws Exception {
        File pasta = Files.createTempDirectory("certs").toFile();
        Files.write(new File(pasta, "b.pfx").toPath(), new byte[] {1});
        Files.write(new File(pasta, "a.pfx").toPath(), new byte[] {1});
        GatewayProperties properties = new GatewayProperties();
        properties.getCertificados().setPath(pasta.getAbsolutePath());
        properties.getCertificados().setSenha("segredo");

        ChamadaHttp primeira = new ChamadaHttp(0L, "GET", "https://127.0.0.1/nada",
                null, null, null, null, 5, true, null, 0);
        ResultadoChamada idZero = cliente(properties).executar(primeira);
        ChamadaHttp segunda = new ChamadaHttp(1L, "GET", "https://127.0.0.1/nada",
                null, null, null, null, 5, true, null, 0);
        ResultadoChamada idUm = cliente(properties).executar(segunda);

        assertEquals(StatusRequisicaoHttp.ERRO, idZero.getStatus());
        assertEquals("b.pfx", idZero.getCertificadoArquivo());
        assertEquals("a.pfx", idUm.getCertificadoArquivo());
    }

    @Test
    void conexaoRecusadaViraErro() {
        ChamadaHttp chamada = new ChamadaHttp(5L, "GET", "http://127.0.0.1:1/nada",
                null, null, null, null, 2, false, null, 0);
        ResultadoChamada resultado = cliente(new GatewayProperties()).executar(chamada);

        assertEquals(StatusRequisicaoHttp.ERRO, resultado.getStatus());
        assertEquals("falha de conexao", resultado.getMensagem());
    }

    private ClienteHttpApache cliente(GatewayProperties properties) {
        return new ClienteHttpApache(new CertificadosCliente(properties), new ContextoTls(properties));
    }

    private String ler(InputStream entrada) throws java.io.IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int lido;
        while ((lido = entrada.read(buffer)) >= 0) {
            saida.write(buffer, 0, lido);
        }
        return new String(saida.toByteArray(), StandardCharsets.UTF_8);
    }
}
