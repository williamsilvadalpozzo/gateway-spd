package com.api.gateway.job;

import com.api.gateway.http.CertificadosCliente;
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
            logger.warn("Pasta de certificados nao encontrada");
            return;
        }
        StringBuilder nomes = new StringBuilder();
        for (int i = 0; i < arquivos.size(); i++) {
            if (i > 0) {
                nomes.append(", ");
            }
            nomes.append(arquivos.get(i).getName());
        }
        logger.info("Certificados encontrados: {}", nomes.toString());
    }

    @Scheduled(fixedDelayString = "${gateway.http.fila-intervalo-ms:1000}",
            initialDelayString = "${gateway.http.fila-intervalo-ms:1000}")
    public void consumir() {
        try {
            while (service.consumirUm()) {
                // segue enquanto houver pendente nesta instancia
            }
        } catch (DataAccessException e) {
            String mensagem = e.getMostSpecificCause() == null ? e.getClass().getSimpleName()
                    : e.getMostSpecificCause().getMessage();
            logger.warn("Fila HTTP indisponivel: {}", mensagem);
        } catch (RuntimeException e) {
            logger.error("Falha ao consumir a fila HTTP", e);
        }
    }

    @Scheduled(fixedDelayString = "${gateway.http.heartbeat-intervalo-ms:15000}",
            initialDelayString = "${gateway.http.heartbeat-intervalo-ms:15000}")
    public void pulsar() {
        try {
            service.pulsar();
        } catch (DataAccessException e) {
            logger.warn("Heartbeat HTTP indisponivel");
        } catch (RuntimeException e) {
            logger.error("Falha no heartbeat das requisicoes HTTP", e);
        }
    }

    @Scheduled(fixedDelayString = "${gateway.http.varredura-intervalo-ms:300000}",
            initialDelayString = "${gateway.http.varredura-intervalo-ms:300000}")
    public void varrer() {
        try {
            int marcadas = service.marcarInterrompidas();
            if (marcadas > 0) {
                logger.warn("{} requisicoes HTTP marcadas como interrompidas", Integer.valueOf(marcadas));
            }
        } catch (DataAccessException e) {
            logger.warn("Varredura HTTP indisponivel");
        } catch (RuntimeException e) {
            logger.error("Falha na varredura de requisicoes HTTP", e);
        }
    }
}
