package com.api.gateway.http;

import com.api.gateway.config.GatewayProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContextoTls {

    private final GatewayProperties properties;

    public ContextoTls(GatewayProperties properties) {
        this.properties = properties;
    }

    public SSLContext criar(File certificadoCliente, List<String> pems) {
        boolean temCliente = certificadoCliente != null;
        boolean temDestino = pems != null && !pems.isEmpty();
        if (!temCliente && !temDestino) {
            return null;
        }
        try {
            KeyManager[] chaves = temCliente ? chaves(certificadoCliente) : null;
            TrustManager[] confianca = temDestino ? confianca(pems) : confiancaPadrao();
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(chaves, confianca, null);
            return contexto;
        } catch (FalhaCertificadoException e) {
            throw e;
        } catch (Exception e) {
            throw falha(certificadoCliente, e);
        }
    }

    private KeyManager[] chaves(File arquivo) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            char[] senha = senha();
            InputStream entrada = new FileInputStream(arquivo);
            try {
                store.load(entrada, senha);
            } finally {
                entrada.close();
            }
            javax.net.ssl.KeyManagerFactory fabrica = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            fabrica.init(store, senha);
            return fabrica.getKeyManagers();
        } catch (Exception e) {
            throw falha(arquivo, e);
        }
    }

    private TrustManager[] confianca(List<String> pems) throws Exception {
        KeyStore store = trustPadrao();
        int indice = 0;
        for (int i = 0; i < pems.size(); i++) {
            List<Certificate> certificados = CertificadoPem.ler(pems.get(i));
            for (int c = 0; c < certificados.size(); c++) {
                store.setCertificateEntry("destino-" + indice, certificados.get(c));
                indice++;
            }
        }
        TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init(store);
        return fabrica.getTrustManagers();
    }

    private TrustManager[] confiancaPadrao() throws Exception {
        TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init((KeyStore) null);
        return fabrica.getTrustManagers();
    }

    private KeyStore trustPadrao() throws Exception {
        String caminho = System.getProperty("javax.net.ssl.trustStore");
        String senhaTrust = System.getProperty("javax.net.ssl.trustStorePassword", "changeit");
        if (caminho == null || caminho.trim().isEmpty()) {
            caminho = System.getProperty("java.home") + File.separator + "lib" + File.separator
                    + "security" + File.separator + "cacerts";
        }
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream entrada = new FileInputStream(caminho);
        try {
            store.load(entrada, senhaTrust.toCharArray());
        } finally {
            entrada.close();
        }
        return store;
    }

    private char[] senha() {
        String senha = properties.getCertificados().getSenha();
        return senha == null ? new char[0] : senha.toCharArray();
    }

    private FalhaCertificadoException falha(File arquivo, Exception causa) {
        String nome = arquivo == null ? "destino" : arquivo.getName();
        String detalhe = causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
        if (detalhe.length() > 300) {
            detalhe = detalhe.substring(0, 300);
        }
        return new FalhaCertificadoException("nao foi possivel abrir o certificado " + nome + ": " + detalhe, causa);
    }
}
