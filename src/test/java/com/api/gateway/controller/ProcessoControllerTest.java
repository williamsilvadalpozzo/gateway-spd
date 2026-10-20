package com.api.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:oracle:thin:@//127.0.0.1:1/NONE",
        "spring.datasource.username=teste",
        "spring.datasource.password=teste",
        "spring.datasource.driver-class-name=oracle.jdbc.OracleDriver",
        "spring.datasource.hikari.jdbc-url=jdbc:oracle:thin:@//127.0.0.1:1/NONE",
        "spring.datasource.hikari.username=teste",
        "spring.datasource.hikari.password=teste",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=1000",
        "gateway.limpeza-intervalo-ms=600000",
        "gateway.http.fila-intervalo-ms=600000",
        "gateway.http.heartbeat-intervalo-ms=600000",
        "gateway.http.varredura-intervalo-ms=600000"
})
class ProcessoControllerTest {

    static {
        System.setProperty("catalina.base", new File("target").getAbsolutePath());
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void publicaActuatorESwagger() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("gateway-spd"));

        int saude = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertTrue(saude == 200 || saude == 503);

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/processos']").exists())
                .andExpect(jsonPath("$.paths['/processos/procedure']").exists())
                .andExpect(jsonPath("$.paths['/requisicoes-http']").exists())
                .andExpect(jsonPath("$.paths['/requisicoes-http/{id}']").exists())
                .andExpect(jsonPath("$.paths['/requisicoes-http/{id}/resposta']").exists())
                .andExpect(jsonPath("$.info.title").value("gateway-spd"));

        int swagger = mvc.perform(get("/swagger/index.html")).andReturn().getResponse().getStatus();
        assertTrue(swagger == 200 || swagger == 302, "swagger status " + swagger);
    }

    @Test
    void rejeitaPedidoInvalido() throws Exception {
        mvc.perform(post("/processos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"nota-url\",\"texto\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());

        mvc.perform(post("/processos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/processos/procedure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"procedure\":\"1invalida\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/processos/procedure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"procedure\":\"DALPOZZO.PROC\",\"tempoExecucaoSegundos\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fluxoAceitaCallbackEProcedureFalhaFicaNoLog() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread aceite = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = server.accept();
                    try {
                        InputStream in = socket.getInputStream();
                        StringBuilder pedido = new StringBuilder();
                        byte[] buffer = new byte[1024];
                        while (pedido.indexOf("\r\n\r\n") < 0) {
                            int lido = in.read(buffer);
                            if (lido < 0) {
                                break;
                            }
                            pedido.append(new String(buffer, 0, lido, StandardCharsets.ISO_8859_1));
                        }
                        OutputStream out = socket.getOutputStream();
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                        Thread.sleep(300L);
                    } finally {
                        socket.close();
                    }
                } catch (Exception ignored) {
                    // O teste encerra o socket ao terminar.
                }
            }
        });
        aceite.setDaemon(true);
        aceite.start();

        try {
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/fluxo";
            MvcResult criado = mvc.perform(post("/processos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + url + "\",\"texto\":\"{\\\"devolucao\\\":754}\",\"tempoExecucaoSegundos\":30}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();
            String token = tokenDe(criado.getResponse().getContentAsString());

            mvc.perform(post("/processos/" + token + "/conclusao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"FINALIZADO\",\"mensagem\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINALIZADO"));

            mvc.perform(get("/processos/" + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINALIZADO"));
        } finally {
            server.close();
        }

        MvcResult procedure = mvc.perform(post("/processos/procedure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"procedure\":\"DALPOZZO.PROC_TESTE\",\"parametros\":[\"1\",null],\"tempoExecucaoSegundos\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String tokenProcedure = tokenDe(procedure.getResponse().getContentAsString());
        String corpo = esperarStatus(tokenProcedure, "ERRO");
        assertTrue(corpo.contains("ERRO"), corpo);
        assertTrue(lerLog().contains(tokenProcedure));
        assertTrue(lerLog().contains("Processo em erro"));
    }

    private String esperarStatus(String token, String status) throws Exception {
        String corpo = "";
        for (int i = 0; i < 40; i++) {
            corpo = mvc.perform(get("/processos/" + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (corpo.contains("\"" + status + "\"")) {
                return corpo;
            }
            Thread.sleep(250L);
        }
        return corpo;
    }

    private String tokenDe(String json) {
        int inicio = json.indexOf("\"token\":\"") + "\"token\":\"".length();
        return json.substring(inicio, json.indexOf('"', inicio));
    }

    private String lerLog() throws Exception {
        File arquivo = new File("target/logs/gateway-spd.log");
        if (!arquivo.isFile()) {
            arquivo = new File("logs/gateway-spd.log");
        }
        if (!arquivo.isFile()) {
            return "";
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(arquivo.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
