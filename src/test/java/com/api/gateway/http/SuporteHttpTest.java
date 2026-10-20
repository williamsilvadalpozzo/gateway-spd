package com.api.gateway.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.api.gateway.config.GatewayProperties;
import com.api.gateway.dto.ParNomeValor;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuporteHttpTest {

    @Test
    void montaUrlEBloqueiaHeader() {
        String url = MontagemUrl.montar("https://api.exemplo.com/recurso",
                Collections.singletonList(new ParNomeValor("cnpjConsulta", "80213705001289")));
        assertEquals("https://api.exemplo.com/recurso?cnpjConsulta=80213705001289", url);

        String comQuery = MontagemUrl.montar("https://api.exemplo.com/recurso?pagina=1",
                Collections.singletonList(new ParNomeValor("q", "a b")));
        assertEquals("https://api.exemplo.com/recurso?pagina=1&q=a+b", comQuery);
        assertTrue(HeadersHttp.bloqueado("Host"));
        assertTrue(HeadersHttp.bloqueado("content-length"));
        assertFalse(HeadersHttp.bloqueado("Authorization"));
    }

    @Test
    void listaSomentePfxEP12EmOrdem() throws Exception {
        File pasta = Files.createTempDirectory("lista-certs").toFile();
        Files.write(new File(pasta, "b.P12").toPath(), new byte[] {1});
        Files.write(new File(pasta, "a.pfx").toPath(), new byte[] {1});
        Files.write(new File(pasta, "nota.txt").toPath(), new byte[] {1});
        GatewayProperties properties = new GatewayProperties();
        properties.getCertificados().setPath(pasta.getAbsolutePath());

        List<File> arquivos = new CertificadosCliente(properties).listar();

        assertEquals(2, arquivos.size());
        assertEquals("a.pfx", arquivos.get(0).getName());
        assertEquals("b.P12", arquivos.get(1).getName());
    }

    @Test
    void pemInvalidoERejeitado() {
        assertThrows(IllegalArgumentException.class, () -> CertificadoPem.validar("nao e certificado"));
    }

    @Test
    void abreCertificadoGerado() throws Exception {
        File pasta = Files.createTempDirectory("pfx-valido").toFile();
        File p12 = new File(pasta, "teste.p12");
        File pem = new File(pasta, "teste.pem");
        File keytool = keytool();
        assertEquals(0, comando(keytool, "-genkeypair", "-alias", "teste", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-storetype", "PKCS12", "-keystore", p12.getAbsolutePath(),
                "-storepass", "segredo", "-dname", "CN=teste", "-noprompt"));
        assertEquals(0, comando(keytool, "-exportcert", "-alias", "teste", "-keystore", p12.getAbsolutePath(),
                "-storepass", "segredo", "-rfc", "-file", pem.getAbsolutePath()));

        GatewayProperties properties = new GatewayProperties();
        properties.getCertificados().setSenha("segredo");
        String texto = new String(Files.readAllBytes(pem.toPath()), "UTF-8");
        CertificadoPem.validar(texto);
        ContextoTls contexto = new ContextoTls(properties);
        assertTrue(contexto.criar(p12, Collections.<String>emptyList()) != null);
        assertTrue(contexto.criar(null, Arrays.asList(texto)) != null);
    }

    private static int comando(File keytool, String... args) throws Exception {
        String[] linha = new String[args.length + 1];
        linha[0] = keytool.getAbsolutePath();
        System.arraycopy(args, 0, linha, 1, args.length);
        ProcessBuilder builder = new ProcessBuilder(linha);
        builder.redirectErrorStream(true);
        Process processo = builder.start();
        InputStream saida = processo.getInputStream();
        byte[] buffer = new byte[256];
        while (saida.read(buffer) >= 0) {
            // descarta a saida para o processo nao travar
        }
        return processo.waitFor();
    }

    private File keytool() {
        String home = System.getProperty("java.home");
        File[] candidatos = new File[] {
                new File(home, "bin/keytool.exe"),
                new File(home, "bin/keytool"),
                new File(home, "../bin/keytool.exe"),
                new File(home, "../bin/keytool")
        };
        for (int i = 0; i < candidatos.length; i++) {
            if (candidatos[i].isFile()) {
                return candidatos[i];
            }
        }
        throw new IllegalStateException("keytool nao encontrado");
    }
}
