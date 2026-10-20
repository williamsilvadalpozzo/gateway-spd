package com.api.gateway.http;

import com.api.gateway.config.GatewayProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CertificadosCliente {

    private final GatewayProperties properties;

    public CertificadosCliente(GatewayProperties properties) {
        this.properties = properties;
    }

    public String pasta() {
        String path = properties.getCertificados().getPath();
        return path == null ? "" : path.trim();
    }

    public boolean pastaConfigurada() {
        return !pasta().isEmpty();
    }

    public List<File> listar() {
        if (!pastaConfigurada()) {
            return Collections.emptyList();
        }
        File diretorio = new File(pasta());
        if (!diretorio.isDirectory()) {
            return Collections.emptyList();
        }
        File[] encontrados = diretorio.listFiles();
        if (encontrados == null) {
            return Collections.emptyList();
        }
        List<File> certificados = new ArrayList<File>();
        for (int i = 0; i < encontrados.length; i++) {
            File arquivo = encontrados[i];
            if (!arquivo.isFile()) {
                continue;
            }
            String nome = arquivo.getName().toLowerCase(Locale.ROOT);
            if (nome.endsWith(".pfx") || nome.endsWith(".p12")) {
                certificados.add(arquivo);
            }
        }
        Collections.sort(certificados, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return certificados;
    }
}
