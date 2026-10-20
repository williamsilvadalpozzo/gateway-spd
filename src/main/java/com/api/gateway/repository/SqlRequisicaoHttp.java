package com.api.gateway.repository;

public final class SqlRequisicaoHttp {

    public static final String PREFIXO_INTERROMPIDA = "execucao interrompida na instancia ";

    public static final String REIVINDICAR =
            "SELECT ID FROM GATEWAY_REQUISICAO_HTTP "
                    + "WHERE STATUS = 'PENDENTE' AND ASSINCRONO = 1 "
                    + "ORDER BY ID FETCH FIRST 1 ROW ONLY FOR UPDATE SKIP LOCKED";

    public static final String MARCAR_EXECUCAO =
            "UPDATE GATEWAY_REQUISICAO_HTTP "
                    + "SET STATUS = 'EM_EXECUCAO', INSTANCIA = ?, INICIADO_EM = ?, HEARTBEAT_EM = ? "
                    + "WHERE ID = ? AND STATUS = 'PENDENTE'";

    public static final String MARCAR_INTERROMPIDAS =
            "UPDATE GATEWAY_REQUISICAO_HTTP "
                    + "SET STATUS = 'ERRO', "
                    + "MENSAGEM_ERRO = '" + PREFIXO_INTERROMPIDA + "' || NVL(INSTANCIA, 'desconhecida'), "
                    + "FINALIZADO_EM = ? "
                    + "WHERE STATUS = 'EM_EXECUCAO' "
                    + "AND INICIADO_EM IS NOT NULL "
                    + "AND INICIADO_EM + NUMTODSINTERVAL(TIMEOUT_SEGUNDOS, 'SECOND') <= ?";

    private SqlRequisicaoHttp() {
    }
}
