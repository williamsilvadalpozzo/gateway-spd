package com.api.gateway.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class DiagnosticoErroTest {

    @Test
    void descreveCadeiaSemConsultaNaMensagem() {
        SQLException ora = new SQLException("ORA-02014: FOR UPDATE na view?token=segredo", "42000", 2014);
        RuntimeException topo = new RuntimeException("falha ao ler?sigilo=1", ora);

        String cadeia = DiagnosticoErro.cadeia(topo);

        assertTrue(cadeia.startsWith("RuntimeException: falha ao ler"));
        assertTrue(cadeia.contains("SQLException codigo=2014"));
        assertTrue(cadeia.contains("ORA-02014"));
        assertTrue(!cadeia.contains("token="));
        assertTrue(!cadeia.contains("sigilo="));
    }

    @Test
    void removeConsultaDaUrl() {
        assertEquals("https://adn.nfse.gov.br/contribuintes/DFe/1",
                DiagnosticoErro.urlSemConsulta("https://adn.nfse.gov.br/contribuintes/DFe/1?cnpjConsulta=1"));
    }
}
