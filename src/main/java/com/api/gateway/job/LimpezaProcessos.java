package com.api.gateway.job;

import com.api.gateway.service.ProcessoService;
import com.api.gateway.store.ProcessoMemoria;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LimpezaProcessos {

    private static final Logger logger = LoggerFactory.getLogger(LimpezaProcessos.class);

    private final ProcessoMemoria memoria;
    private final ProcessoService processoService;
    private final Clock clock;

    public LimpezaProcessos(ProcessoMemoria memoria, ProcessoService processoService, Clock clock) {
        this.memoria = memoria;
        this.processoService = processoService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gateway.limpeza-intervalo-ms:15000}")
    public void limpar() {
        try {
            processoService.registrarPrazos(clock.instant());
            memoria.removerExpirados(clock.instant());
        } catch (RuntimeException e) {
            logger.error("Falha na limpeza de processos", e);
        }
    }
}
