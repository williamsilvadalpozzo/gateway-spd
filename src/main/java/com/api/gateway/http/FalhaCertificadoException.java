package com.api.gateway.http;

public class FalhaCertificadoException extends RuntimeException {

    public FalhaCertificadoException(String mensagem) {
        super(mensagem);
    }

    public FalhaCertificadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
