package com.api.gateway.service;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.Processo;
import com.api.gateway.domain.StatusProcesso;
import com.api.gateway.store.ProcessoMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProcessoService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessoService.class);
    private static final int MAX_PARAMETROS = 100;

    private final ProcessoMemoria memoria;
    private final MakerClient makerClient;
    private final ProcedureClient procedureClient;
    private final GatewayProperties properties;
    private final Clock clock;
    private final Executor processoExecutor;

    public ProcessoService(ProcessoMemoria memoria, MakerClient makerClient, ProcedureClient procedureClient,
            GatewayProperties properties, Clock clock, @Qualifier("processoExecutor") Executor processoExecutor) {
        this.memoria = memoria;
        this.makerClient = makerClient;
        this.procedureClient = procedureClient;
        this.properties = properties;
        this.clock = clock;
        this.processoExecutor = processoExecutor;
    }

    public Processo iniciar(String url, String texto, Long tempoExecucaoSegundos) {
        Duration prazo = resolverPrazo(tempoExecucaoSegundos);
        Instant agora = clock.instant();
        String token = UUID.randomUUID().toString();
        Processo processo = new Processo(token, agora, agora.plus(prazo));
        processo.definirDestino("fluxo", url);
        memoria.salvar(processo);
        logger.info("Processo aceito token={} origem=fluxo destino={} limite={}", token, url, processo.getLimiteExecucao());
        agendar(processo, "fluxo", new Runnable() {
            @Override
            public void run() {
                int readTimeoutMs = restanteMs(processo);
                if (readTimeoutMs <= 0) {
                    return;
                }
                makerClient.chamar(url, texto, token, readTimeoutMs);
            }
        });
        return processo;
    }

    public Processo iniciarProcedure(String procedure, List<String> parametros, Long tempoExecucaoSegundos) {
        List<String> valores = copiarParametros(parametros);
        Duration prazo = resolverPrazo(tempoExecucaoSegundos);
        Instant agora = clock.instant();
        String token = UUID.randomUUID().toString();
        Processo processo = new Processo(token, agora, agora.plus(prazo));
        processo.definirDestino("procedure", procedure);
        memoria.salvar(processo);
        logger.info("Processo aceito token={} origem=procedure destino={} parametros={} limite={}",
                token, procedure, valores.size(), processo.getLimiteExecucao());
        final List<String> informados = valores;
        agendar(processo, "procedure", new Runnable() {
            @Override
            public void run() {
                int timeoutSegundos = restanteSegundos(processo);
                if (timeoutSegundos <= 0) {
                    return;
                }
                procedureClient.executar(procedure, informados, timeoutSegundos);
                concluir(token, StatusProcesso.FINALIZADO, null, true, false);
            }
        });
        return processo;
    }

    public Optional<Processo> consultar(String token) {
        return memoria.buscar(token);
    }

    public Optional<Processo> concluir(String token, StatusProcesso status, String mensagem) {
        return concluir(token, status, mensagem, true, true);
    }

    public void registrarPrazos(Instant agora) {
        List<Processo> encerrados = memoria.encerrarPrazos(agora);
        for (Processo processo : encerrados) {
            logger.error("Processo em erro token={} contexto=prazo origem={} destino={} mensagem={}",
                    processo.getToken(), processo.getOrigem(), processo.getDestino(), Processo.MENSAGEM_PRAZO);
        }
    }

    private void agendar(final Processo processo, final String contexto, final Runnable tarefa) {
        try {
            processoExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Map<String, String> anterior = MDC.getCopyOfContextMap();
                    MDC.put("requestId", processo.getToken());
                    try {
                        tarefa.run();
                    } catch (Throwable erro) {
                        falhar(processo.getToken(), contexto, mensagemDe(erro), erro);
                    } finally {
                        if (anterior == null) {
                            MDC.clear();
                        } else {
                            MDC.setContextMap(anterior);
                        }
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            falhar(processo.getToken(), contexto, "Fila de execucao cheia", e);
        }
    }

    private void falhar(String token, String contexto, String mensagem, Throwable erro) {
        logger.error("Processo em erro token={} contexto={} mensagem={}", token, contexto, resumir(mensagem), erro);
        concluir(token, StatusProcesso.ERRO, mensagem, true, false);
    }

    private Optional<Processo> concluir(String token, StatusProcesso status, String mensagem, boolean substituirPrazo,
            boolean logarErroCallback) {
        Optional<Processo> encontrado = memoria.buscar(token);
        if (!encontrado.isPresent()) {
            logger.warn("Conclusao para token ausente token={} status={}", token, status);
            return encontrado;
        }
        Processo processo = encontrado.get();
        boolean aplicado = false;
        boolean ignorado = false;
        synchronized (processo) {
            boolean aberto = processo.getStatus() == StatusProcesso.EM_EXECUCAO;
            boolean prazo = Processo.MENSAGEM_PRAZO.equals(processo.getMensagem());
            if (!aberto && !(substituirPrazo && prazo)) {
                ignorado = true;
            } else {
                processo.concluir(status, mensagem, clock.instant());
                aplicado = true;
            }
        }
        if (ignorado) {
            logger.warn("Conclusao ignorada token={} statusAtual={} statusRecebido={}",
                    token, processo.getStatus(), status);
            return Optional.of(processo);
        }
        if (aplicado && status == StatusProcesso.ERRO && logarErroCallback) {
            logger.error("Processo em erro token={} contexto=callback origem={} destino={} mensagem={}",
                    token, processo.getOrigem(), processo.getDestino(), resumir(mensagem));
        }
        if (aplicado && status == StatusProcesso.FINALIZADO) {
            logger.info("Processo finalizado token={} origem={} destino={}",
                    token, processo.getOrigem(), processo.getDestino());
        }
        return Optional.of(processo);
    }

    private Duration resolverPrazo(Long tempoExecucaoSegundos) {
        if (tempoExecucaoSegundos == null) {
            return properties.getTempoExecucaoPadrao();
        }
        long segundos = tempoExecucaoSegundos.longValue();
        if (segundos <= 0L) {
            throw new IllegalArgumentException("O tempo de execucao deve ser maior que zero");
        }
        long maximo = properties.getTempoExecucaoMaximo().getSeconds();
        if (segundos > maximo) {
            throw new IllegalArgumentException("O tempo de execucao excede o limite de " + maximo + " segundos");
        }
        return Duration.ofSeconds(segundos);
    }

    private List<String> copiarParametros(List<String> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            return Collections.emptyList();
        }
        if (parametros.size() > MAX_PARAMETROS) {
            throw new IllegalArgumentException("A procedure aceita no maximo " + MAX_PARAMETROS + " parametros");
        }
        return Collections.unmodifiableList(new ArrayList<String>(parametros));
    }

    private int restanteMs(Processo processo) {
        long restante = Duration.between(clock.instant(), processo.getLimiteExecucao()).toMillis();
        if (restante <= 0L) {
            return 0;
        }
        return (int) Math.min(restante, Integer.MAX_VALUE);
    }

    private int restanteSegundos(Processo processo) {
        long restante = Duration.between(clock.instant(), processo.getLimiteExecucao()).getSeconds();
        if (restante <= 0L) {
            return 0;
        }
        return (int) Math.min(restante, Integer.MAX_VALUE);
    }

    private String mensagemDe(Throwable erro) {
        if (erro == null || erro.getMessage() == null || erro.getMessage().trim().isEmpty()) {
            return erro == null ? "Falha na execucao" : erro.getClass().getSimpleName();
        }
        return erro.getMessage();
    }

    private String resumir(String mensagem) {
        if (mensagem == null || mensagem.trim().isEmpty()) {
            return "sem mensagem";
        }
        String texto = mensagem.replace('\r', ' ').replace('\n', ' ').trim();
        if (texto.length() <= 2000) {
            return texto;
        }
        return texto.substring(0, 2000) + "...";
    }
}
