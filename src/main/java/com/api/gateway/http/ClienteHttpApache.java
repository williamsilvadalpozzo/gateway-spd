package com.api.gateway.http;

import com.api.gateway.dto.ParNomeValor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClienteHttpApache implements ClienteHttpDestino {

    private static final Logger logger = LoggerFactory.getLogger(ClienteHttpApache.class);

    private final CertificadosCliente certificados;
    private final ContextoTls contextoTls;

    public ClienteHttpApache(CertificadosCliente certificados, ContextoTls contextoTls) {
        this.certificados = certificados;
        this.contextoTls = contextoTls;
    }

    @Override
    public ResultadoChamada executar(ChamadaHttp chamada) {
        try {
            if (chamada.isUsarCertificado()) {
                return comCertificado(chamada);
            }
            return umaVez(chamada, null);
        } catch (FalhaCertificadoException e) {
            return erro(chamada, "falha de certificado", null, e);
        } catch (Throwable e) {
            return erro(chamada, "falha interna", null, e);
        }
    }

    private ResultadoChamada comCertificado(ChamadaHttp chamada) {
        if (!certificados.pastaConfigurada()) {
            return erro(chamada, "pasta de certificados nao configurada", null, null);
        }
        if (!new File(certificados.pasta()).isDirectory()) {
            return erro(chamada, "pasta de certificados nao encontrada path=" + certificados.pasta(), null, null);
        }
        List<File> arquivos = certificados.listar();
        if (arquivos.isEmpty()) {
            return erro(chamada, "nenhum certificado .pfx ou .p12 na pasta path=" + certificados.pasta(), null, null);
        }
        int inicio = (int) Math.floorMod(chamada.getId(), (long) arquivos.size());
        StringBuilder tentativas = new StringBuilder();
        ResultadoChamada ultimo = null;
        for (int i = 0; i < arquivos.size(); i++) {
            File arquivo = arquivos.get((inicio + i) % arquivos.size());
            try {
                return umaVez(chamada, arquivo);
            } catch (FalhaCertificadoException e) {
                ultimo = erro(chamada, e.getMessage(), arquivo.getName(), e);
                if (tentativas.length() > 0) {
                    tentativas.append(" || ");
                }
                tentativas.append(ultimo.getMensagem());
            }
        }
        String mensagem = tentativas.length() <= 4000 ? tentativas.toString() : tentativas.substring(0, 4000);
        return ResultadoChamada.erro(mensagem, ultimo.getCertificadoArquivo());
    }

    private ResultadoChamada umaVez(ChamadaHttp chamada, File certificado) {
        String nome = certificado == null ? null : certificado.getName();
        try {
            SSLContext ssl = contextoTls.criar(certificado, chamada.getCertificadosDestino());
            ResultadoChamada resultado = chamar(chamada, ssl, nome);
            if (resultado.getCertificadoArquivo() == null) {
                return resultado.comCertificado(nome);
            }
            return resultado;
        } catch (FalhaCertificadoException e) {
            throw e;
        } catch (IOException e) {
            if (certificado != null && handshake(e)) {
                throw new FalhaCertificadoException("falha de handshake", e);
            }
            return classificar(chamada, e, nome);
        }
    }

    private ResultadoChamada chamar(ChamadaHttp chamada, SSLContext ssl, String nomeCertificado) throws IOException {
        String metodo = chamada.getMetodo();
        String url = MontagemUrl.montar(chamada.getUrl(), chamada.getParametros());
        byte[] corpo = chamada.getCorpo();
        String contentType = chamada.getContentType();
        List<ParNomeValor> headers = chamada.getHeaders();
        int restantes = Math.max(0, chamada.getMaxRedirects());
        CloseableHttpClient cliente = cliente(ssl, chamada.getTimeoutSegundos());
        try {
            while (true) {
                HttpRequestBase request = criarRequest(metodo, URI.create(url), corpo, contentType, headers);
                org.apache.http.client.methods.CloseableHttpResponse response = cliente.execute(request);
                try {
                    RespostaLida lida = ler(response);
                    if (lida.timeout) {
                        String mensagem = DiagnosticoErro.comContexto(
                                "tempo limite de " + chamada.getTimeoutSegundos() + " segundos",
                                chamada, nomeCertificado, lida.causa);
                        logger.warn("Requisicao HTTP id={} {}", Long.valueOf(chamada.getId()), mensagem, lida.causa);
                        return ResultadoChamada.timeout(Integer.valueOf(lida.httpStatus), lida.corpo, lida.contentType,
                                mensagem, nomeCertificado);
                    }
                    if (restantes > 0 && redireciona(lida.httpStatus)) {
                        String location = header(lida.headers, "Location");
                        URI destino = location == null ? null : URI.create(url).resolve(location.trim());
                        if (destino != null && http(destino)) {
                            if (!mesmoHost(URI.create(url), destino)) {
                                headers = semSegredos(headers);
                            }
                            if (lida.httpStatus == 301 || lida.httpStatus == 302 || lida.httpStatus == 303) {
                                metodo = "GET";
                                corpo = null;
                                contentType = null;
                            }
                            url = destino.toASCIIString();
                            restantes--;
                            continue;
                        }
                    }
                    String mensagem = lida.httpStatus >= 400
                            ? mensagemHttp(lida.httpStatus, lida.contentType, lida.corpo) : null;
                    if (lida.httpStatus >= 400) {
                        logger.warn("Requisicao HTTP id={} destino HTTP {} {} corpo={}",
                                Long.valueOf(chamada.getId()), Integer.valueOf(lida.httpStatus),
                                DiagnosticoErro.comContexto("HTTP " + lida.httpStatus, chamada, nomeCertificado, null),
                                limitar(mensagem, 500));
                    }
                    return ResultadoChamada.finalizado(lida.httpStatus, lida.corpo, lida.contentType, lida.charset,
                            lida.headers, mensagem, nomeCertificado);
                } finally {
                    response.close();
                }
            }
        } finally {
            cliente.close();
        }
    }

    private CloseableHttpClient cliente(SSLContext ssl, int timeoutSegundos) {
        long ms = Math.max(1, timeoutSegundos) * 1000L;
        int timeoutMs = ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .setRedirectsEnabled(false)
                .setAuthenticationEnabled(false)
                .setExpectContinueEnabled(false)
                .build();
        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableContentCompression()
                .disableCookieManagement();
        if (ssl != null) {
            builder.setSSLSocketFactory(new SSLConnectionSocketFactory(ssl, new DefaultHostnameVerifier()));
        }
        return builder.build();
    }

    private HttpRequestBase criarRequest(String metodo, URI uri, byte[] corpo, String contentType,
            List<ParNomeValor> headers) {
        boolean comCorpo = corpo != null && !"GET".equals(metodo) && !"HEAD".equals(metodo);
        HttpRequestBase request;
        if ("GET".equals(metodo)) {
            request = new HttpGet(uri);
        } else if ("POST".equals(metodo)) {
            request = new HttpPost(uri);
        } else if ("PUT".equals(metodo)) {
            request = new HttpPut(uri);
        } else if ("PATCH".equals(metodo)) {
            request = new HttpPatch(uri);
        } else if ("DELETE".equals(metodo)) {
            request = comCorpo ? new HttpDeleteComCorpo(uri) : new HttpDelete(uri);
        } else if ("HEAD".equals(metodo)) {
            request = new HttpHead(uri);
        } else if ("OPTIONS".equals(metodo)) {
            request = new HttpOptions(uri);
        } else {
            throw new IllegalArgumentException("metodo HTTP invalido");
        }
        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                ParNomeValor header = headers.get(i);
                if (header.getNome() == null || HeadersHttp.bloqueado(header.getNome())) {
                    continue;
                }
                if ("Content-Type".equalsIgnoreCase(header.getNome())) {
                    continue;
                }
                request.addHeader(header.getNome(), header.getValor() == null ? "" : header.getValor());
            }
        }
        if (comCorpo && request instanceof HttpEntityEnclosingRequestBase) {
            if (contentType != null) {
                request.setHeader("Content-Type", contentType);
            }
            ((HttpEntityEnclosingRequestBase) request).setEntity(new ByteArrayEntity(corpo));
        }
        return request;
    }

    private RespostaLida ler(org.apache.http.client.methods.CloseableHttpResponse response) throws IOException {
        int status = response.getStatusLine().getStatusCode();
        Header tipo = response.getFirstHeader("Content-Type");
        String contentType = tipo == null ? null : tipo.getValue();
        List<ParNomeValor> headers = new ArrayList<ParNomeValor>();
        Header[] todos = response.getAllHeaders();
        for (int i = 0; i < todos.length; i++) {
            headers.add(new ParNomeValor(todos[i].getName(), todos[i].getValue()));
        }
        byte[] corpo = null;
        boolean timeout = false;
        Throwable causaTimeout = null;
        if (response.getEntity() != null) {
            InputStream entrada = response.getEntity().getContent();
            try {
                Leitura leitura = lerBytes(entrada);
                corpo = leitura.bytes;
                timeout = leitura.timeout;
                causaTimeout = leitura.causa;
            } finally {
                entrada.close();
            }
        }
        RespostaLida lida = new RespostaLida();
        lida.httpStatus = status;
        lida.contentType = limitar(contentType, 255);
        lida.charset = TextoResposta.charsetDe(contentType);
        lida.headers = headers;
        lida.corpo = corpo;
        lida.timeout = timeout;
        lida.causa = causaTimeout;
        return lida;
    }

    private Leitura lerBytes(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int lido;
            while ((lido = entrada.read(buffer)) >= 0) {
                saida.write(buffer, 0, lido);
            }
            return new Leitura(saida.toByteArray(), false, null);
        } catch (SocketTimeoutException e) {
            return new Leitura(saida.toByteArray(), true, e);
        }
    }

    private ResultadoChamada classificar(ChamadaHttp chamada, IOException e, String certificado) {
        if (timeout(e)) {
            return timeout(chamada, certificado, e);
        }
        if (causa(e, UnknownHostException.class)) {
            return erro(chamada, "falha de DNS", certificado, e);
        }
        if (causa(e, ConnectException.class) || causa(e, NoRouteToHostException.class)) {
            return erro(chamada, "falha de conexao", certificado, e);
        }
        if (handshake(e)) {
            return erro(chamada, "falha de handshake", certificado, e);
        }
        return erro(chamada, "falha na chamada", certificado, e);
    }

    private ResultadoChamada timeout(ChamadaHttp chamada, String certificado, Throwable causa) {
        String mensagem = DiagnosticoErro.comContexto(
                "tempo limite de " + chamada.getTimeoutSegundos() + " segundos", chamada, certificado, causa);
        logger.warn("Requisicao HTTP id={} {}", Long.valueOf(chamada.getId()), mensagem, causa);
        return ResultadoChamada.timeout(null, null, null, mensagem, certificado);
    }

    private ResultadoChamada erro(ChamadaHttp chamada, String resumo, String certificado, Throwable causa) {
        String mensagem = DiagnosticoErro.comContexto(resumo, chamada, certificado, causa);
        if (causa == null || causa instanceof FalhaCertificadoException || resumo.startsWith("falha de")
                || resumo.startsWith("pasta ") || resumo.startsWith("nenhum certificado")) {
            logger.warn("Requisicao HTTP id={} {}", Long.valueOf(chamada.getId()), mensagem, causa);
        } else {
            logger.error("Requisicao HTTP id={} {}", Long.valueOf(chamada.getId()), mensagem, causa);
        }
        return ResultadoChamada.erro(mensagem, certificado);
    }

    private boolean redireciona(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean http(URI uri) {
        String esquema = uri.getScheme();
        return "http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema);
    }

    private boolean mesmoHost(URI atual, URI destino) {
        String hostAtual = atual.getHost() == null ? "" : atual.getHost();
        String hostDestino = destino.getHost() == null ? "" : destino.getHost();
        return hostAtual.equalsIgnoreCase(hostDestino) && atual.getPort() == destino.getPort();
    }

    private List<ParNomeValor> semSegredos(List<ParNomeValor> headers) {
        List<ParNomeValor> limpos = new ArrayList<ParNomeValor>();
        for (int i = 0; i < headers.size(); i++) {
            ParNomeValor header = headers.get(i);
            String nome = header.getNome() == null ? "" : header.getNome();
            if ("Authorization".equalsIgnoreCase(nome) || "Cookie".equalsIgnoreCase(nome)
                    || "Proxy-Authorization".equalsIgnoreCase(nome)) {
                continue;
            }
            limpos.add(header);
        }
        return limpos;
    }

    private String header(List<ParNomeValor> headers, String nome) {
        for (int i = 0; i < headers.size(); i++) {
            if (nome.equalsIgnoreCase(headers.get(i).getNome())) {
                return headers.get(i).getValor();
            }
        }
        return null;
    }

    private String mensagemHttp(int status, String contentType, byte[] corpo) {
        String texto = TextoResposta.comoTexto(corpo, contentType, null);
        if (texto != null) {
            texto = texto.trim();
            if (!texto.isEmpty()) {
                return limitar(texto, 4000);
            }
        }
        return "HTTP " + status;
    }

    private boolean timeout(Throwable e) {
        return causa(e, SocketTimeoutException.class) || causa(e, ConnectTimeoutException.class);
    }

    private boolean handshake(Throwable e) {
        return causa(e, SSLHandshakeException.class) || causa(e, SSLPeerUnverifiedException.class)
                || causa(e, CertificateException.class);
    }

    private boolean causa(Throwable e, Class<? extends Throwable> tipo) {
        Throwable atual = e;
        while (atual != null) {
            if (tipo.isInstance(atual)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private String limitar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replace("\u0000", "");
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }

    private static final class RespostaLida {
        private int httpStatus;
        private String contentType;
        private String charset;
        private List<ParNomeValor> headers;
        private byte[] corpo;
        private boolean timeout;
        private Throwable causa;
    }

    private static final class Leitura {
        private final byte[] bytes;
        private final boolean timeout;
        private final Throwable causa;

        private Leitura(byte[] bytes, boolean timeout, Throwable causa) {
            this.bytes = bytes;
            this.timeout = timeout;
            this.causa = causa;
        }
    }

    private static final class HttpDeleteComCorpo extends HttpEntityEnclosingRequestBase {
        private HttpDeleteComCorpo(URI uri) {
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return "DELETE";
        }
    }
}
