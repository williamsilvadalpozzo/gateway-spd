package com.api.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.gateway.config.ApiExceptionHandler;
import com.api.gateway.domain.RequisicaoHttp;
import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.service.CorpoResposta;
import com.api.gateway.service.NaoEncontradoException;
import com.api.gateway.service.RequisicaoHttpService;
import com.api.gateway.service.ResultadoCriacao;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequisicaoHttpControllerTest {

    private RequisicaoHttpService service;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        service = Mockito.mock(RequisicaoHttpService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RequisicaoHttpController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void aceitaRequisicaoAssincrona() throws Exception {
        RequisicaoHttp requisicao = new RequisicaoHttp();
        requisicao.setId(7L);
        requisicao.setStatus(StatusRequisicaoHttp.PENDENTE);
        requisicao.setAssincrono(true);
        requisicao.setCriadoEm(Instant.parse("2026-10-15T16:00:00Z"));
        when(service.criar(any())).thenReturn(new ResultadoCriacao(requisicao, true));

        mvc.perform(post("/requisicoes-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"POST\",\"url\":\"https://api.exemplo.com\",\"assincrono\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void respondeResultadoSincrono() throws Exception {
        RequisicaoHttp requisicao = new RequisicaoHttp();
        requisicao.setId(8L);
        requisicao.setStatus(StatusRequisicaoHttp.FINALIZADO);
        requisicao.setHttpStatus(Integer.valueOf(200));
        when(service.criar(any())).thenReturn(new ResultadoCriacao(requisicao, false));

        mvc.perform(post("/requisicoes-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADO"))
                .andExpect(jsonPath("$.httpStatus").value(200));
    }

    @Test
    void rejeitaPedidoInvalido() throws Exception {
        when(service.criar(any())).thenThrow(new IllegalArgumentException("A url deve usar http ou https"));

        mvc.perform(post("/requisicoes-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A url deve usar http ou https"));
    }

    @Test
    void consultaInexistente() throws Exception {
        when(service.consultar(9L)).thenThrow(new NaoEncontradoException("Requisicao nao encontrada"));

        mvc.perform(get("/requisicoes-http/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Requisicao nao encontrada"));
    }

    @Test
    void baixaRespostaBinaria() throws Exception {
        byte[] zip = new byte[] {1, 2, 3};
        when(service.baixar(4L)).thenReturn(new CorpoResposta(StatusRequisicaoHttp.FINALIZADO, "application/zip", zip));

        mvc.perform(get("/requisicoes-http/4/resposta"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(content().bytes(zip));
    }

    @Test
    void respostaAindaIndisponivel() throws Exception {
        when(service.baixar(4L)).thenThrow(new NaoEncontradoException("Resposta nao disponivel"));

        mvc.perform(get("/requisicoes-http/4/resposta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Resposta nao disponivel"));
    }
}
