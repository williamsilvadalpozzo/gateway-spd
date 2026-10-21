package com.api.gateway.repository;

import com.api.gateway.domain.RequisicaoHttp;
import com.api.gateway.domain.StatusRequisicaoHttp;
import com.api.gateway.domain.TipoCorpoHttp;
import com.api.gateway.domain.TipoRetornoHttp;
import com.api.gateway.http.ResultadoChamada;
import com.api.gateway.http.TextoResposta;
import com.api.gateway.service.CorpoResposta;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RequisicaoHttpRepository {

    private static final String INSERIR =
            "INSERT INTO GATEWAY_REQUISICAO_HTTP ("
                    + "ID, CHAVE_IDEMPOTENCIA, ASSINCRONO, PROCESSO, LOTE, STATUS, INSTANCIA, HEARTBEAT_EM, "
                    + "METODO, URL, PARAMETROS_JSON, HEADERS_JSON, TIPO_CORPO, CONTENT_TYPE_REQUISICAO, "
                    + "CORPO_REQUISICAO, TIPO_RETORNO, TIMEOUT_SEGUNDOS, USAR_CERTIFICADO, CERTIFICADOS_DESTINO, "
                    + "CRIADO_EM, INICIADO_EM"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String RESUMO =
            "SELECT ID, CHAVE_IDEMPOTENCIA, ASSINCRONO, PROCESSO, LOTE, STATUS, INSTANCIA, "
                    + "METODO, URL, TIPO_CORPO, TIPO_RETORNO, TIMEOUT_SEGUNDOS, CERTIFICADO_ARQUIVO, "
                    + "USAR_CERTIFICADO, HTTP_STATUS, CONTENT_TYPE_RESPOSTA, CHARSET_RESPOSTA, "
                    + "TAMANHO_RESPOSTA, MENSAGEM_ERRO, CRIADO_EM, INICIADO_EM, FINALIZADO_EM, "
                    + "CASE WHEN STATUS IN ('ERRO', 'TIMEOUT') OR NVL(HTTP_STATUS, 0) >= 400 "
                    + "THEN CORPO_RESPOSTA END AS CORPO_ERRO "
                    + "FROM GATEWAY_REQUISICAO_HTTP ";

    private static final String EXECUCAO =
            "SELECT ID, CHAVE_IDEMPOTENCIA, ASSINCRONO, PROCESSO, LOTE, STATUS, INSTANCIA, "
                    + "METODO, URL, PARAMETROS_JSON, HEADERS_JSON, TIPO_CORPO, CONTENT_TYPE_REQUISICAO, "
                    + "CORPO_REQUISICAO, TIPO_RETORNO, TIMEOUT_SEGUNDOS, USAR_CERTIFICADO, CERTIFICADOS_DESTINO, "
                    + "CRIADO_EM, INICIADO_EM "
                    + "FROM GATEWAY_REQUISICAO_HTTP ";

    private static final String CONCLUIR =
            "UPDATE GATEWAY_REQUISICAO_HTTP SET STATUS = ?, HTTP_STATUS = ?, HEADERS_RESPOSTA_JSON = ?, "
                    + "CONTENT_TYPE_RESPOSTA = ?, CHARSET_RESPOSTA = ?, CORPO_RESPOSTA = ?, "
                    + "CORPO_RESPOSTA_TEXTO = ?, TAMANHO_RESPOSTA = ?, "
                    + "MENSAGEM_ERRO = ?, CERTIFICADO_ARQUIVO = ?, FINALIZADO_EM = ? "
                    + "WHERE ID = ? AND STATUS = 'EM_EXECUCAO'";

    private static final String HEARTBEAT =
            "UPDATE GATEWAY_REQUISICAO_HTTP SET HEARTBEAT_EM = ? "
                    + "WHERE ID = ? AND STATUS = 'EM_EXECUCAO' AND INSTANCIA = ?";

    private final JdbcTemplate jdbc;

    public RequisicaoHttpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long proximoId() {
        Long id = jdbc.queryForObject("SELECT SQ_GATEWAY_REQUISICAO_HTTP.NEXTVAL FROM DUAL", Long.class);
        if (id == null) {
            throw new IllegalStateException("Falha ao gerar o ID da requisicao");
        }
        return id.longValue();
    }

    @Transactional
    public void inserir(final RequisicaoHttp requisicao) {
        jdbc.update(INSERIR, new org.springframework.jdbc.core.PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                int i = 1;
                ps.setLong(i++, requisicao.getId());
                texto(ps, i++, requisicao.getChaveIdempotencia());
                ps.setInt(i++, requisicao.isAssincrono() ? 1 : 0);
                texto(ps, i++, requisicao.getProcesso());
                texto(ps, i++, requisicao.getLote());
                ps.setString(i++, requisicao.getStatus().name());
                texto(ps, i++, requisicao.getInstancia());
                instante(ps, i++, requisicao.getHeartbeatEm());
                ps.setString(i++, requisicao.getMetodo());
                ps.setString(i++, requisicao.getUrl());
                clob(ps, i++, requisicao.getParametrosJson());
                clob(ps, i++, requisicao.getHeadersJson());
                ps.setString(i++, requisicao.getTipoCorpo().name());
                texto(ps, i++, requisicao.getContentTypeRequisicao());
                blob(ps, i++, requisicao.getCorpoRequisicao());
                ps.setString(i++, requisicao.getTipoRetorno().name());
                ps.setInt(i++, requisicao.getTimeoutSegundos());
                ps.setInt(i++, requisicao.isUsarCertificado() ? 1 : 0);
                clob(ps, i++, requisicao.getCertificadosDestino());
                instante(ps, i++, requisicao.getCriadoEm());
                instante(ps, i, requisicao.getIniciadoEm());
            }
        });
    }

    /**
     * A reivindicacao e a marcacao ficam na mesma transacao. O lock do
     * SKIP LOCKED so protege as duas instancias enquanto a transacao esta aberta.
     */
    @Transactional
    public Optional<Long> reivindicar(String instancia, Instant agora) {
        List<Long> ids = jdbc.query(SqlRequisicaoHttp.REIVINDICAR, new RowMapper<Long>() {
            @Override
            public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
                return Long.valueOf(rs.getLong("ID"));
            }
        });
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        Long id = ids.get(0);
        Timestamp momento = Timestamp.from(agora);
        int atualizados = jdbc.update(SqlRequisicaoHttp.MARCAR_EXECUCAO, instancia, momento, momento, id);
        if (atualizados == 0) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    public Optional<RequisicaoHttp> buscarResumo(long id) {
        return um(RESUMO + "WHERE ID = ?", Long.valueOf(id), resumo());
    }

    public Optional<RequisicaoHttp> buscarPorChave(String chave) {
        return um(RESUMO + "WHERE CHAVE_IDEMPOTENCIA = ?", chave, resumo());
    }

    public Optional<RequisicaoHttp> buscarParaExecucao(long id) {
        return um(EXECUCAO + "WHERE ID = ?", Long.valueOf(id), execucao());
    }

    public Optional<CorpoResposta> buscarCorpo(long id) {
        List<CorpoResposta> linhas = jdbc.query(
                "SELECT STATUS, CONTENT_TYPE_RESPOSTA, CORPO_RESPOSTA FROM GATEWAY_REQUISICAO_HTTP WHERE ID = ?",
                new RowMapper<CorpoResposta>() {
                    @Override
                    public CorpoResposta mapRow(ResultSet rs, int rowNum) throws SQLException {
                        byte[] corpo = rs.getBytes("CORPO_RESPOSTA");
                        return new CorpoResposta(
                                StatusRequisicaoHttp.valueOf(rs.getString("STATUS")),
                                rs.getString("CONTENT_TYPE_RESPOSTA"),
                                corpo);
                    }
                },
                Long.valueOf(id));
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(linhas.get(0));
    }

    @Transactional
    public boolean concluir(long id, ResultadoChamada resultado, String headersJson, Instant finalizadoEm) {
        Long tamanho = resultado.getCorpo() == null ? null : Long.valueOf(resultado.getCorpo().length);
        String texto = TextoResposta.comoTexto(
                resultado.getCorpo(), resultado.getContentType(), resultado.getCharset());
        int atualizados = jdbc.update(CONCLUIR,
                new ArgumentosConclusao(resultado, headersJson, texto, tamanho, finalizadoEm, id));
        return atualizados == 1;
    }

    @Transactional
    public void pulsar(Collection<Long> ids, String instancia, Instant agora) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Timestamp momento = Timestamp.from(agora);
        for (Long id : ids) {
            jdbc.update(HEARTBEAT, momento, id, instancia);
        }
    }

    @Transactional
    public int marcarInterrompidas(Instant agora) {
        Timestamp momento = Timestamp.from(agora);
        return jdbc.update(SqlRequisicaoHttp.MARCAR_INTERROMPIDAS, momento, momento);
    }

    private Optional<RequisicaoHttp> um(String sql, Object id, RowMapper<RequisicaoHttp> mapper) {
        List<RequisicaoHttp> linhas = jdbc.query(sql, mapper, id);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(linhas.get(0));
    }

    private RowMapper<RequisicaoHttp> resumo() {
        return new RowMapper<RequisicaoHttp>() {
            @Override
            public RequisicaoHttp mapRow(ResultSet rs, int rowNum) throws SQLException {
                RequisicaoHttp requisicao = base(rs);
                requisicao.setCertificadoArquivo(rs.getString("CERTIFICADO_ARQUIVO"));
                requisicao.setHttpStatus(numero(rs, "HTTP_STATUS"));
                requisicao.setContentTypeResposta(rs.getString("CONTENT_TYPE_RESPOSTA"));
                requisicao.setTamanhoResposta(longo(rs, "TAMANHO_RESPOSTA"));
                requisicao.setMensagemErro(rs.getString("MENSAGEM_ERRO"));
                requisicao.setCorpoErro(textoErro(rs));
                requisicao.setFinalizadoEm(instante(rs, "FINALIZADO_EM"));
                return requisicao;
            }
        };
    }

    private RowMapper<RequisicaoHttp> execucao() {
        return new RowMapper<RequisicaoHttp>() {
            @Override
            public RequisicaoHttp mapRow(ResultSet rs, int rowNum) throws SQLException {
                RequisicaoHttp requisicao = base(rs);
                requisicao.setParametrosJson(rs.getString("PARAMETROS_JSON"));
                requisicao.setHeadersJson(rs.getString("HEADERS_JSON"));
                requisicao.setContentTypeRequisicao(rs.getString("CONTENT_TYPE_REQUISICAO"));
                requisicao.setCorpoRequisicao(rs.getBytes("CORPO_REQUISICAO"));
                requisicao.setCertificadosDestino(rs.getString("CERTIFICADOS_DESTINO"));
                return requisicao;
            }
        };
    }

    private RequisicaoHttp base(ResultSet rs) throws SQLException {
        RequisicaoHttp requisicao = new RequisicaoHttp();
        requisicao.setId(rs.getLong("ID"));
        requisicao.setChaveIdempotencia(rs.getString("CHAVE_IDEMPOTENCIA"));
        requisicao.setAssincrono(rs.getInt("ASSINCRONO") == 1);
        requisicao.setProcesso(rs.getString("PROCESSO"));
        requisicao.setLote(rs.getString("LOTE"));
        requisicao.setStatus(StatusRequisicaoHttp.valueOf(rs.getString("STATUS")));
        requisicao.setInstancia(rs.getString("INSTANCIA"));
        requisicao.setMetodo(rs.getString("METODO"));
        requisicao.setUrl(rs.getString("URL"));
        requisicao.setTipoCorpo(TipoCorpoHttp.valueOf(rs.getString("TIPO_CORPO")));
        requisicao.setTipoRetorno(TipoRetornoHttp.valueOf(rs.getString("TIPO_RETORNO")));
        requisicao.setTimeoutSegundos(rs.getInt("TIMEOUT_SEGUNDOS"));
        requisicao.setUsarCertificado(rs.getInt("USAR_CERTIFICADO") == 1);
        requisicao.setCriadoEm(instante(rs, "CRIADO_EM"));
        requisicao.setIniciadoEm(instante(rs, "INICIADO_EM"));
        return requisicao;
    }

    private String textoErro(ResultSet rs) throws SQLException {
        String texto = TextoResposta.comoTexto(
                rs.getBytes("CORPO_ERRO"), rs.getString("CONTENT_TYPE_RESPOSTA"), rs.getString("CHARSET_RESPOSTA"));
        if (texto == null) {
            return null;
        }
        texto = texto.trim();
        return texto.isEmpty() ? null : texto;
    }

    private Integer numero(ResultSet rs, String coluna) throws SQLException {
        Number valor = (Number) rs.getObject(coluna);
        return valor == null ? null : Integer.valueOf(valor.intValue());
    }

    private Long longo(ResultSet rs, String coluna) throws SQLException {
        Number valor = (Number) rs.getObject(coluna);
        return valor == null ? null : Long.valueOf(valor.longValue());
    }

    private Instant instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(coluna);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void texto(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.VARCHAR);
        } else {
            ps.setString(indice, valor);
        }
    }

    private void clob(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.CLOB);
        } else {
            ps.setString(indice, valor);
        }
    }

    private void blob(PreparedStatement ps, int indice, byte[] valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.BLOB);
        } else {
            ps.setBytes(indice, valor);
        }
    }

    private void instante(PreparedStatement ps, int indice, Instant valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(indice, Timestamp.from(valor));
        }
    }

    private static final class ArgumentosConclusao implements org.springframework.jdbc.core.PreparedStatementSetter {
        private final ResultadoChamada resultado;
        private final String headersJson;
        private final String corpoTexto;
        private final Long tamanho;
        private final Instant finalizadoEm;
        private final long id;

        private ArgumentosConclusao(ResultadoChamada resultado, String headersJson, String corpoTexto,
                Long tamanho, Instant finalizadoEm, long id) {
            this.resultado = resultado;
            this.headersJson = headersJson;
            this.corpoTexto = corpoTexto;
            this.tamanho = tamanho;
            this.finalizadoEm = finalizadoEm;
            this.id = id;
        }

        @Override
        public void setValues(PreparedStatement ps) throws SQLException {
            ps.setString(1, resultado.getStatus().name());
            if (resultado.getHttpStatus() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, resultado.getHttpStatus().intValue());
            }
            if (headersJson == null) {
                ps.setNull(3, Types.CLOB);
            } else {
                ps.setString(3, headersJson);
            }
            if (resultado.getContentType() == null) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, resultado.getContentType());
            }
            if (resultado.getCharset() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, resultado.getCharset());
            }
            if (resultado.getCorpo() == null) {
                ps.setNull(6, Types.BLOB);
            } else {
                ps.setBytes(6, resultado.getCorpo());
            }
            if (corpoTexto == null) {
                ps.setNull(7, Types.CLOB);
            } else {
                ps.setString(7, corpoTexto);
            }
            if (tamanho == null) {
                ps.setNull(8, Types.NUMERIC);
            } else {
                ps.setLong(8, tamanho.longValue());
            }
            if (resultado.getMensagem() == null) {
                ps.setNull(9, Types.CLOB);
            } else {
                ps.setString(9, resultado.getMensagem());
            }
            if (resultado.getCertificadoArquivo() == null) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, resultado.getCertificadoArquivo());
            }
            ps.setTimestamp(11, Timestamp.from(finalizadoEm));
            ps.setLong(12, id);
        }
    }
}
