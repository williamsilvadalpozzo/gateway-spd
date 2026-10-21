package com.api.gateway.service;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.RequisicaoHttp;
import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.dto.CriarRequisicaoHttpRequest;
import com.api.gateway.dto.ParNomeValor;
import com.api.gateway.http.ChamadaHttp;
import com.api.gateway.http.ClienteHttpDestino;
import com.api.gateway.http.DiagnosticoErro;
import com.api.gateway.http.ResultadoChamada;
import com.api.gateway.repository.RequisicaoHttpRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class RequisicaoHttpService {

    private static final Logger logger = LoggerFactory.getLogger(RequisicaoHttpService.class);

    private final RequisicaoHttpRepository repository;
    private final ClienteHttpDestino cliente;
    private final ValidadorRequisicaoHttp validador;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExecucoesAtivas execucoes;
    private final NomeInstancia nomeInstancia;
    private final GatewayProperties properties;

    public RequisicaoHttpService(RequisicaoHttpRepository repository, ClienteHttpDestino cliente,
            ValidadorRequisicaoHttp validador, ObjectMapper objectMapper, Clock clock,
            ExecucoesAtivas execucoes, NomeInstancia nomeInstancia, GatewayProperties properties) {
        this.repository = repository;
        this.cliente = cliente;
        this.validador = validador;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.execucoes = execucoes;
        this.nomeInstancia = nomeInstancia;
        this.properties = properties;
    }

    public ResultadoCriacao criar(CriarRequisicaoHttpRequest request) {
        PedidoNormalizado pedido = validador.validar(request);
        if (pedido.getChave() != null) {
            Optional<RequisicaoHttp> existente = repository.buscarPorChave(pedido.getChave());
            if (existente.isPresent()) {
                return new ResultadoCriacao(existente.get(), false);
            }
        }
        RequisicaoHttp nova = montar(pedido);
        try {
            repository.inserir(nova);
        } catch (DuplicateKeyException e) {
            Optional<RequisicaoHttp> existente = repository.buscarPorChave(pedido.getChave());
            if (existente.isPresent()) {
                return new ResultadoCriacao(existente.get(), false);
            }
            throw e;
        }
        logger.info("Requisicao HTTP id={} registrada assincrono={}", Long.valueOf(nova.getId()),
                Boolean.valueOf(nova.isAssincrono()));
        if (nova.isAssincrono()) {
            return new ResultadoCriacao(nova, true);
        }
        executar(nova.getId());
        return new ResultadoCriacao(obrigatoria(nova.getId()), false);
    }

    public RequisicaoHttp consultar(long id) {
        return obrigatoria(id);
    }

    public CorpoResposta baixar(long id) {
        Optional<CorpoResposta> corpo = repository.buscarCorpo(id);
        if (!corpo.isPresent()) {
            throw new NaoEncontradoException("Requisicao nao encontrada");
        }
        CorpoResposta resposta = corpo.get();
        if (resposta.getStatus() == StatusRequisicaoHttp.PENDENTE
                || resposta.getStatus() == StatusRequisicaoHttp.EM_EXECUCAO
                || resposta.getBytes() == null) {
            throw new NaoEncontradoException("Resposta nao disponivel");
        }
        return resposta;
    }

    public boolean consumirUm() {
        Optional<Long> id = repository.reivindicar(nomeInstancia.get(), clock.instant());
        if (!id.isPresent()) {
            return false;
        }
        executar(id.get().longValue());
        return true;
    }

    public void executar(long id) {
        Optional<RequisicaoHttp> encontrada = repository.buscarParaExecucao(id);
        if (!encontrada.isPresent() || encontrada.get().getStatus() != StatusRequisicaoHttp.EM_EXECUCAO) {
            return;
        }
        execucoes.iniciar(id);
        RequisicaoHttp requisicao = encontrada.get();
        ChamadaHttp pedido = chamada(requisicao);
        try {
            ResultadoChamada resultado = cliente.executar(pedido);
            boolean gravou = repository.concluir(id, resultado, jsonHeaders(resultado), clock.instant());
            if (gravou) {
                if (resultado.getStatus() == StatusRequisicaoHttp.ERRO
                        || resultado.getStatus() == StatusRequisicaoHttp.TIMEOUT
                        || (resultado.getHttpStatus() != null && resultado.getHttpStatus().intValue() >= 400)) {
                    logger.warn("Requisicao HTTP id={} concluida status={} http={} certificado={} mensagem={}",
                            Long.valueOf(id), resultado.getStatus(), resultado.getHttpStatus(),
                            resultado.getCertificadoArquivo(), resultado.getMensagem());
                } else {
                    logger.info("Requisicao HTTP id={} concluida status={} http={}", Long.valueOf(id),
                            resultado.getStatus(), resultado.getHttpStatus());
                }
            } else {
                logger.info("Requisicao HTTP id={} ja encerrada", Long.valueOf(id));
            }
        } catch (Throwable erro) {
            String mensagem = DiagnosticoErro.comContexto("falha interna", pedido, null, erro);
            logger.error("Requisicao HTTP id={} {}", Long.valueOf(id), mensagem, erro);
            try {
                repository.concluir(id, ResultadoChamada.erro(mensagem, null), null, clock.instant());
            } catch (RuntimeException gravacao) {
                logger.error("Falha ao gravar o erro da requisicao HTTP id={}", Long.valueOf(id), gravacao);
            }
        } finally {
            execucoes.encerrar(id);
        }
    }

    public void pulsar() {
        repository.pulsar(execucoes.atuais(), nomeInstancia.get(), clock.instant());
    }

    public int marcarInterrompidas() {
        return repository.marcarInterrompidas(clock.instant());
    }

    private RequisicaoHttp obrigatoria(long id) {
        Optional<RequisicaoHttp> requisicao = repository.buscarResumo(id);
        if (!requisicao.isPresent()) {
            throw new NaoEncontradoException("Requisicao nao encontrada");
        }
        return requisicao.get();
    }

    private RequisicaoHttp montar(PedidoNormalizado pedido) {
        Instant agora = clock.instant();
        RequisicaoHttp requisicao = new RequisicaoHttp();
        requisicao.setId(repository.proximoId());
        requisicao.setChaveIdempotencia(pedido.getChave());
        requisicao.setAssincrono(pedido.isAssincrono());
        requisicao.setProcesso(pedido.getProcesso());
        requisicao.setLote(pedido.getLote());
        requisicao.setStatus(pedido.isAssincrono() ? StatusRequisicaoHttp.PENDENTE : StatusRequisicaoHttp.EM_EXECUCAO);
        requisicao.setMetodo(pedido.getMetodo());
        requisicao.setUrl(pedido.getUrl());
        requisicao.setParametrosJson(json(pedido.getParametros()));
        requisicao.setHeadersJson(json(pedido.getHeaders()));
        requisicao.setTipoCorpo(pedido.getTipoCorpo());
        requisicao.setContentTypeRequisicao(pedido.getContentType());
        requisicao.setCorpoRequisicao(pedido.getCorpo());
        requisicao.setTipoRetorno(pedido.getTipoRetorno());
        requisicao.setTimeoutSegundos(pedido.getTimeoutSegundos());
        requisicao.setUsarCertificado(pedido.isUsarCertificado());
        requisicao.setCertificadosDestino(pedido.getCertificadosDestino().isEmpty()
                ? null : json(pedido.getCertificadosDestino()));
        requisicao.setCriadoEm(agora);
        if (!pedido.isAssincrono()) {
            requisicao.setInstancia(nomeInstancia.get());
            requisicao.setIniciadoEm(agora);
            requisicao.setHeartbeatEm(agora);
        }
        return requisicao;
    }

    private ChamadaHttp chamada(RequisicaoHttp requisicao) {
        return new ChamadaHttp(
                requisicao.getId(),
                requisicao.getMetodo(),
                requisicao.getUrl(),
                lerPares(requisicao.getParametrosJson()),
                lerPares(requisicao.getHeadersJson()),
                requisicao.getCorpoRequisicao(),
                requisicao.getContentTypeRequisicao(),
                requisicao.getTimeoutSegundos(),
                requisicao.isUsarCertificado(),
                lerTextos(requisicao.getCertificadosDestino()),
                properties.getHttp().getMaxRedirects());
    }

    private List<ParNomeValor> lerPares(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ParNomeValor>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("requisicao armazenada invalida");
        }
    }

    private List<String> lerTextos(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("requisicao armazenada invalida");
        }
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao gravar a requisicao");
        }
    }

    private String jsonHeaders(ResultadoChamada resultado) {
        if (resultado.getHeaders() == null || resultado.getHeaders().isEmpty()) {
            return null;
        }
        return json(resultado.getHeaders());
    }
}
