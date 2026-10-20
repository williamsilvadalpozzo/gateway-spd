package com.api.gateway.http;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CertificadoPem {

    private CertificadoPem() {
    }

    public static void validar(String pem) {
        ler(pem);
    }

    public static List<Certificate> ler(String pem) {
        if (pem == null || !pem.contains("BEGIN CERTIFICATE") || !pem.contains("END CERTIFICATE")) {
            throw new IllegalArgumentException("Certificado de destino invalido");
        }
        try {
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificados = fabrica.generateCertificates(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            if (certificados.isEmpty()) {
                throw new IllegalArgumentException("Certificado de destino invalido");
            }
            return Collections.unmodifiableList(new ArrayList<Certificate>(certificados));
        } catch (CertificateException e) {
            throw new IllegalArgumentException("Certificado de destino invalido");
        }
    }
}
