package com.api.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class SqlRequisicaoHttpTest {

    @Test
    void filaUsaSkipLockedEMensagemDeInterrupcao() throws Exception {
        assertTrue(SqlRequisicaoHttp.REIVINDICAR.contains("SKIP LOCKED"));
        assertTrue(SqlRequisicaoHttp.REIVINDICAR.contains("ASSINCRONO = 1"));
        assertTrue(SqlRequisicaoHttp.MARCAR_INTERROMPIDAS.contains(SqlRequisicaoHttp.PREFIXO_INTERROMPIDA));

        String script = new String(Files.readAllBytes(Paths.get("sql", "GATEWAY_REQUISICAO_HTTP.sql")),
                StandardCharsets.UTF_8);
        assertTrue(script.contains("GATEWAY_REQUISICAO_HTTP"));
        assertTrue(script.contains("CHAVE_IDEMPOTENCIA"));
        assertTrue(script.contains("CERTIFICADOS_DESTINO"));
    }
}
