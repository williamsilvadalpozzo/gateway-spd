package com.api.gateway.job;

import com.api.gateway.http.CertificadosCliente;
import com.api.gateway.http.DiagnosticoErro;
import com.api.gateway.service.NomeInstancia;
import com.api.gateway.service.RequisicaoHttpService;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RotinaRequisicaoHttp implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RotinaRequisicaoHttp.class);

    private final RequisicaoHttpService service;
    private final CertificadosCliente certificados;
    private final NomeInstancia nomeInstancia;

    public RotinaRequisicaoHttp(RequisicaoHttpService service, CertificadosCliente certificados,
            NomeInstancia nomeInstancia) {
        this.service = service;
        this.certificados = certificados;
        this.nomeInstancia = nomeInstancia;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Instancia do redirecionador HTTP {}", nomeInstancia.get());
        if (!certificados.pastaConfigurada()) {
            logger.info("Pasta de certificados nao configurada");
            return;
        }
        List<File> arquivos = certificados.listar();
        if (!new File(certificados.pasta()).isDirectory()) {
            logger.warn("Pasta de certificados nao encontrada path={}", certificados.pasta());
            return;
        }
        if (arquivos.isEmpty()) {
            logger.warn("Nenhum certificado .pfx ou .p12 na pasta path={}", certificados.pasta());
            return;
        }
        StringBuilder nomes = new StringBuilder();
        for (int i = 0; i < arquivos.size(); i++) {
            if (i > 0) {
                nomes.append(", ");
            }
            nomes.append(arquivos.get(i).getName());
        }
        logger.info("Certificados encontrados path={} arquivos={}", certificados.pasta(), nomes.toString());
    }

    private String ultimaFalhaFila;
    private String ultimaFalhaPulso;
    private String ultimaFalhaVarredura;

    @Scheduled(fixedDelayString = "${gateway.http.fila-intervalo-ms:1000}",
            initialDelayString = "${gateway.http.fila-intervalo-ms:1000}")
    public void consumir() {
        try {
            while (service.consumirUm()) {
                // segue enquanto houver pendente nesta instancia
            }
            ultimaFalhaFila = null;
        } catch (DataAccessException e) {
            registrar("Fila HTTP", "reivindicar", e, true);
        } catch (RuntimeException e) {
            logger.error("Falha ao consumir a fila HTTP {}", DiagnosticoErro.cadeia(e), e);
        }
    }

    @Scheduled(fixedDelayString = "${gateway.http.heartbeat-intervalo-ms:15000}",
            initialDelayString = "${gateway.http.heartbeat-intervalo-ms:15000}")
    public void pulsar() {
        try {
            service.pulsar();
            ultimaFalhaPulso = null;
        } catch (DataAccessException e) {
            registrar("Heartbeat HTTP", "pulsar", e, false);
        } catch (RuntimeException e) {
            logger.error("Falha no heartbeat das requisicoes HTTP {}", DiagnosticoErro.cadeia(e), e);
        }
    }

    @Scheduled(fixedDelayString = "${gateway.http.varredura-intervalo-ms:300000}",
            initialDelayString = "${gateway.http.varredura-intervalo-ms:300000}")
    public void varrer() {
        try {
            int marcadas = service.marcarInterrompidas();
            ultimaFalhaVarredura = null;
            if (marcadas > 0) {
                logger.warn("{} requisicoes HTTP marcadas como interrompidas por timeout sem conclusao",
                        Integer.valueOf(marcadas));
            }
        } catch (DataAccessException e) {
            registrar("Varredura HTTP", "interromper", e, false);
        } catch (RuntimeException e) {
            logger.error("Falha na varredura de requisicoes HTTP {}", DiagnosticoErro.cadeia(e), e);
        }
    }

    private void registrar(String rotina, String operacao, DataAccessException e, boolean fila) {
        String detalhe = DiagnosticoErro.cadeia(e.getMostSpecificCause() == null ? e : e.getMostSpecificCause());
        String chave = operacao + " " + detalhe;
        String anterior = fila ? ultimaFalhaFila : operacao.startsWith("pulsar") ? ultimaFalhaPulso : ultimaFalhaVarredura;
        if (chave.equals(anterior)) {
            logger.warn("{} ainda indisponivel operacao={} {}", rotina, operacao, detalhe);
            return;
        }
        if (fila) {
            ultimaFalhaFila = chave;
        } else if ("pulsar".equals(operacao)) {
            ultimaFalhaPulso = chave;
        } else {
            ultimaFalhaVarredura = chave;
        }
        logger.error("{} indisponivel operacao={} {}", rotina, operacao, detalhe, e);
    }
}
