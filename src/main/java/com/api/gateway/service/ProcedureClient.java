package com.api.gateway.service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class ProcedureClient {

    private static final String NOME = "[A-Za-z][A-Za-z0-9_$#]*(\\.[A-Za-z][A-Za-z0-9_$#]*){0,2}";

    private final DataSource dataSource;

    public ProcedureClient(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void executar(String procedure, List<String> parametros, int timeoutSegundos) {
        if (procedure == null || !procedure.matches(NOME)) {
            throw new ChamadaMakerException("A procedure deve ser nome ou schema.nome");
        }
        List<String> valores = parametros == null ? Collections.<String>emptyList() : parametros;
        String sql = "{ call " + procedure + montarMarcadores(valores.size()) + " }";
        Connection connection = null;
        CallableStatement statement = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            statement = connection.prepareCall(sql);
            statement.setQueryTimeout(Math.max(timeoutSegundos, 1));
            for (int i = 0; i < valores.size(); i++) {
                String valor = valores.get(i);
                if (valor == null) {
                    statement.setNull(i + 1, Types.VARCHAR);
                } else {
                    statement.setString(i + 1, valor);
                }
            }
            statement.execute();
        } catch (SQLTimeoutException e) {
            throw new ChamadaMakerException("A procedure excedeu o tempo de execucao", e);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1013) {
                throw new ChamadaMakerException("A procedure excedeu o tempo de execucao", e);
            }
            throw new ChamadaMakerException(mensagemSql(e), e);
        } finally {
            fechar(statement);
            fechar(connection);
        }
    }

    private String mensagemSql(SQLException erro) {
        StringBuilder texto = new StringBuilder();
        SQLException atual = erro;
        while (atual != null) {
            if (texto.length() > 0) {
                texto.append(" | ");
            }
            String detalhe = atual.getMessage() == null
                    ? "Falha ao executar a procedure"
                    : atual.getMessage().trim();
            if (atual.getErrorCode() != 0 && !detalhe.startsWith("ORA-")) {
                texto.append("ORA-").append(String.format("%05d", atual.getErrorCode())).append(": ");
            }
            texto.append(detalhe);
            atual = atual.getNextException();
        }
        if (texto.length() == 0) {
            return "Falha ao executar a procedure";
        }
        return texto.toString();
    }

    private String montarMarcadores(int quantidade) {
        if (quantidade == 0) {
            return "";
        }
        StringBuilder marcas = new StringBuilder("(");
        for (int i = 0; i < quantidade; i++) {
            if (i > 0) {
                marcas.append(", ");
            }
            marcas.append("?");
        }
        marcas.append(")");
        return marcas.toString();
    }

    private void fechar(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // A conexao volta para o pool. O status do token ja foi definido.
        }
    }
}
