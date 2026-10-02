package com.api.gateway.service;

public class ChamadaMakerException extends RuntimeException {

    public ChamadaMakerException(String message) {
        super(message);
    }

    public ChamadaMakerException(String message, Throwable cause) {
        super(message, cause);
    }
}
