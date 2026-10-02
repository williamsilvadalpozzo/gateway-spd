package com.api.gateway.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.Processo;
import com.api.gateway.domain.StatusProcesso;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessoMemoriaTest {

    private ProcessoMemoria memoria;
    private Instant agora;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setRetencaoAposFim(Duration.ofMinutes(5));
        memoria = new ProcessoMemoria(properties);
        agora = Instant.parse("2026-09-30T12:00:00Z");
    }

    @Test
    void marcaErroQuandoEstouraOPrazoEMantemPorCincoMinutos() {
        Processo processo = new Processo("t1", agora.minus(Duration.ofHours(4)), agora.minus(ProcessoMemoria.FOLGA_PRAZO));
        processo.definirDestino("procedure", "DALPOZZO.MINHA_PROCEDURE");
        memoria.salvar(processo);

        assertEquals(1, memoria.encerrarPrazos(agora).size());
        assertEquals(StatusProcesso.ERRO, memoria.buscar("t1").get().getStatus());
        assertEquals(Processo.MENSAGEM_PRAZO, memoria.buscar("t1").get().getMensagem());
        assertEquals(0, memoria.removerExpirados(agora).size());
        assertTrue(memoria.buscar("t1").isPresent());

        assertEquals(1, memoria.removerExpirados(agora.plus(Duration.ofMinutes(5))).size());
        assertFalse(memoria.buscar("t1").isPresent());
    }

    @Test
    void naoEncerraNoInstanteExatoDoLimite() {
        Processo processo = new Processo("t4", agora.minus(Duration.ofHours(1)), agora);
        memoria.salvar(processo);

        assertEquals(0, memoria.encerrarPrazos(agora).size());
        assertEquals(StatusProcesso.EM_EXECUCAO, memoria.buscar("t4").get().getStatus());
        assertEquals(0, memoria.removerExpirados(agora).size());
    }

    @Test
    void mantemFinalizadoDentroDeCincoMinutos() {
        Processo processo = new Processo("t2", agora.minus(Duration.ofMinutes(10)), agora.plus(Duration.ofHours(4)));
        processo.concluir(StatusProcesso.FINALIZADO, null, agora.minus(Duration.ofMinutes(4)));
        memoria.salvar(processo);

        assertEquals(0, memoria.removerExpirados(agora).size());
        assertTrue(memoria.buscar("t2").isPresent());
    }

    @Test
    void removeErroDepoisDeCincoMinutos() {
        Processo processo = new Processo("t3", agora.minus(Duration.ofMinutes(20)), agora.plus(Duration.ofHours(4)));
        processo.concluir(StatusProcesso.ERRO, "falhou", agora.minus(Duration.ofMinutes(5)));
        memoria.salvar(processo);

        assertEquals(1, memoria.removerExpirados(agora).size());
        assertFalse(memoria.buscar("t3").isPresent());
    }
}
