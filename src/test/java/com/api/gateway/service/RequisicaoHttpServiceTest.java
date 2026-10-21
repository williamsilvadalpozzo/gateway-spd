package com.api.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.RequisicaoHttp;
import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.dto.CriarRequisicaoHttpRequest;
import com.api.gateway.dto.ParNomeValor;
import com.api.gateway.http.ClienteHttpDestino;
import com.api.gateway.http.ResultadoChamada;
import com.api.gateway.repository.RequisicaoHttpRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RequisicaoHttpServiceTest {

    private RequisicaoHttpRepository repository;
    private ClienteHttpDestino cliente;
    private RequisicaoHttpService service;

    @BeforeEach
    void preparar() {
        repository = Mockito.mock(RequisicaoHttpRepository.class);
        cliente = Mockito.mock(ClienteHttpDestino.class);
        GatewayProperties properties = new GatewayProperties();
        properties.setInstancia("instancia-teste");
        service = new RequisicaoHttpService(
                repository,
                cliente,
                new ValidadorRequisicaoHttp(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-15T16:00:00Z"), ZoneOffset.UTC),
                new ExecucoesAtivas(),
                new NomeInstancia(properties),
                properties);
    }

    @Test
    void registraAssincronaSemExecutar() {
        when(repository.proximoId()).thenReturn(10L);

        ResultadoCriacao resultado = service.criar(pedido(Boolean.TRUE));

        assertTrue(resultado.isAceita());
        assertEquals(10L, resultado.getRequisicao().getId());
        assertEquals(StatusRequisicaoHttp.PENDENTE, resultado.getRequisicao().getStatus());
        verify(cliente, never()).executar(any());
        verify(repository).inserir(any(RequisicaoHttp.class));
    }

    @Test
    void executaSincronaEDevolveEstadoFinal() {
        when(repository.proximoId()).thenReturn(11L);
        when(repository.buscarParaExecucao(11L)).thenAnswer(invocation -> {
            RequisicaoHttp salva = new RequisicaoHttp();
            salva.setId(11L);
            salva.setStatus(StatusRequisicaoHttp.EM_EXECUCAO);
            salva.setMetodo("POST");
            salva.setUrl("https://api.exemplo.com/recurso");
            salva.setTimeoutSegundos(30);
            salva.setHeadersJson("[]");
            salva.setParametrosJson("[]");
            return Optional.of(salva);
        });
        when(cliente.executar(any())).thenReturn(ResultadoChamada.finalizado(
                201, "ok".getBytes(StandardCharsets.UTF_8), "text/plain", "UTF-8", null, null, null));
        when(repository.concluir(eq(11L), any(ResultadoChamada.class), isNull(), any(Instant.class))).thenReturn(true);
        RequisicaoHttp finalizada = new RequisicaoHttp();
        finalizada.setId(11L);
        finalizada.setStatus(StatusRequisicaoHttp.FINALIZADO);
        finalizada.setHttpStatus(Integer.valueOf(201));
        when(repository.buscarResumo(11L)).thenReturn(Optional.of(finalizada));

        ResultadoCriacao resultado = service.criar(pedido(Boolean.FALSE));

        assertFalse(resultado.isAceita());
        assertEquals(StatusRequisicaoHttp.FINALIZADO, resultado.getRequisicao().getStatus());
        assertEquals(Integer.valueOf(201), resultado.getRequisicao().getHttpStatus());
        verify(cliente).executar(any());
    }

    @Test
    void chaveExistenteNaoExecutaDeNovo() {
        RequisicaoHttp existente = new RequisicaoHttp();
        existente.setId(4L);
        existente.setStatus(StatusRequisicaoHttp.FINALIZADO);
        when(repository.buscarPorChave("pedido-1")).thenReturn(Optional.of(existente));
        CriarRequisicaoHttpRequest request = pedido(Boolean.TRUE);
        request.setChaveIdempotencia("pedido-1");

        ResultadoCriacao resultado = service.criar(request);

        assertFalse(resultado.isAceita());
        assertEquals(4L, resultado.getRequisicao().getId());
        verify(repository, never()).inserir(any());
        verify(cliente, never()).executar(any());
    }

    @Test
    void rejeitaUrlEHeaderBloqueado() {
        CriarRequisicaoHttpRequest invalida = pedido(Boolean.TRUE);
        invalida.setUrl("ftp://arquivos");
        assertThrows(IllegalArgumentException.class, () -> service.criar(invalida));

        CriarRequisicaoHttpRequest header = pedido(Boolean.TRUE);
        header.setHeaders(Collections.singletonList(new ParNomeValor("Host", "evil")));
        assertThrows(IllegalArgumentException.class, () -> service.criar(header));
        verify(repository, never()).inserir(any());
    }

    @Test
    void gravaErroHttpComoFinalizado() {
        when(repository.proximoId()).thenReturn(12L);
        RequisicaoHttp salva = new RequisicaoHttp();
        salva.setId(12L);
        salva.setStatus(StatusRequisicaoHttp.EM_EXECUCAO);
        salva.setMetodo("GET");
        salva.setUrl("https://api.exemplo.com/recurso");
        salva.setTimeoutSegundos(30);
        when(repository.buscarParaExecucao(12L)).thenReturn(Optional.of(salva));
        when(cliente.executar(any())).thenReturn(ResultadoChamada.finalizado(
                404, "ausente".getBytes(StandardCharsets.UTF_8), "text/plain", null, null, "ausente", null));
        when(repository.concluir(anyLong(), any(ResultadoChamada.class), isNull(), any(Instant.class))).thenReturn(true);
        when(repository.buscarResumo(12L)).thenReturn(Optional.of(salva));
        CriarRequisicaoHttpRequest request = pedido(Boolean.FALSE);
        request.setMetodo("GET");
        request.setTipoCorpo("NENHUM");

        service.criar(request);

        ArgumentCaptor<ResultadoChamada> captor = ArgumentCaptor.forClass(ResultadoChamada.class);
        verify(repository).concluir(eq(12L), captor.capture(), isNull(), any(Instant.class));
        assertEquals(StatusRequisicaoHttp.FINALIZADO, captor.getValue().getStatus());
        assertEquals(Integer.valueOf(404), captor.getValue().getHttpStatus());
        assertEquals("ausente", captor.getValue().getMensagem());
    }

    @Test
    void filaVaziaNaoExecuta() {
        when(repository.reivindicar(eq("instancia-teste"), any(Instant.class))).thenReturn(Optional.empty());

        assertFalse(service.consumirUm());
        verify(cliente, never()).executar(any());
    }

    @Test
    void gravaFalhaInternaNoCampoDeErro() {
        when(repository.proximoId()).thenReturn(13L);
        RequisicaoHttp salva = new RequisicaoHttp();
        salva.setId(13L);
        salva.setStatus(StatusRequisicaoHttp.EM_EXECUCAO);
        salva.setMetodo("GET");
        salva.setUrl("https://adn.nfse.gov.br/contribuintes/DFe/1");
        salva.setTimeoutSegundos(120);
        salva.setUsarCertificado(true);
        when(repository.buscarParaExecucao(13L)).thenReturn(Optional.of(salva));
        when(cliente.executar(any())).thenThrow(new NoSuchMethodError("java.lang.Math.floorMod(JI)I"));
        when(repository.concluir(anyLong(), any(ResultadoChamada.class), isNull(), any(Instant.class))).thenReturn(true);
        when(repository.buscarResumo(13L)).thenReturn(Optional.of(salva));
        CriarRequisicaoHttpRequest request = pedido(Boolean.FALSE);
        request.setMetodo("GET");
        request.setTipoCorpo("NENHUM");
        request.setUsarCertificado(Boolean.TRUE);
        request.setTimeoutSegundos(Integer.valueOf(120));

        service.criar(request);

        ArgumentCaptor<ResultadoChamada> captor = ArgumentCaptor.forClass(ResultadoChamada.class);
        verify(repository).concluir(eq(13L), captor.capture(), isNull(), any(Instant.class));
        assertEquals(StatusRequisicaoHttp.ERRO, captor.getValue().getStatus());
        String mensagem = captor.getValue().getMensagem();
        assertTrue(mensagem.startsWith("falha interna"));
        assertTrue(mensagem.contains("GET https://adn.nfse.gov.br/contribuintes/DFe/1"));
        assertTrue(mensagem.contains("timeout 120s"));
        assertTrue(mensagem.contains("usarCertificado true"));
        assertTrue(mensagem.contains("NoSuchMethodError"));
        assertTrue(mensagem.contains("java.lang.Math.floorMod(JI)I"));
    }

    private CriarRequisicaoHttpRequest pedido(Boolean assincrono) {
        CriarRequisicaoHttpRequest request = new CriarRequisicaoHttpRequest();
        request.setMetodo("POST");
        request.setUrl("https://api.exemplo.com/recurso");
        request.setTipoCorpo("JSON");
        request.setCorpoTexto("{\"campo\":\"valor\"}");
        request.setTimeoutSegundos(Integer.valueOf(30));
        request.setAssincrono(assincrono);
        request.setProcesso("NFSE_ADN");
        return request;
    }
}
