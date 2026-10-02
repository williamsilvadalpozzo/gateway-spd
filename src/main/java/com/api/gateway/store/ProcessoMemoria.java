package com.api.gateway.store;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.domain.Processo;
import com.api.gateway.domain.StatusProcesso;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ProcessoMemoria {

    static final Duration FOLGA_PRAZO = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Processo> processos = new ConcurrentHashMap<String, Processo>();
    private final GatewayProperties properties;

    public ProcessoMemoria(GatewayProperties properties) {
        this.properties = properties;
    }

    public void salvar(Processo processo) {
        processos.put(processo.getToken(), processo);
    }

    public Optional<Processo> buscar(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(processos.get(token));
    }

    public List<Processo> encerrarPrazos(Instant agora) {
        List<Processo> encerrados = new ArrayList<Processo>();
        for (Processo processo : processos.values()) {
            synchronized (processo) {
                if (processo.getStatus() != StatusProcesso.EM_EXECUCAO) {
                    continue;
                }
                if (agora.isBefore(processo.getLimiteExecucao().plus(FOLGA_PRAZO))) {
                    continue;
                }
                processo.concluir(StatusProcesso.ERRO, Processo.MENSAGEM_PRAZO, agora);
                encerrados.add(processo);
            }
        }
        return encerrados;
    }

    public List<Processo> removerExpirados(Instant agora) {
        List<Processo> removidos = new ArrayList<Processo>();
        for (Processo processo : processos.values()) {
            synchronized (processo) {
                if (!expirado(processo, agora)) {
                    continue;
                }
                if (processos.remove(processo.getToken(), processo)) {
                    removidos.add(processo);
                }
            }
        }
        return removidos;
    }

    private boolean expirado(Processo processo, Instant agora) {
        if (processo.getStatus() == StatusProcesso.EM_EXECUCAO) {
            return false;
        }
        Instant fim = processo.getFinalizadoEm();
        if (fim == null) {
            return true;
        }
        return !agora.isBefore(fim.plus(properties.getRetencaoAposFim()));
    }
}
