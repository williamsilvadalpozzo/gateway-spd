package com.api.gateway.service;

import com.api.gateway.domain.StatusRequisicaoHttp;

public class CorpoResposta {

    private final StatusRequisicaoHttp status;
    private final String contentType;
    private final byte[] bytes;

    public CorpoResposta(StatusRequisicaoHttp status, String contentType, byte[] bytes) {
        this.status = status;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    public StatusRequisicaoHttp getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
