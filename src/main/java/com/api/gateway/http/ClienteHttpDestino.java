package com.api.gateway.http;

public interface ClienteHttpDestino {

    ResultadoChamada executar(ChamadaHttp chamada);
}
