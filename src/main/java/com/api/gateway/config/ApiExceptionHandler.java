package com.api.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> argumento(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(erro(exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class,
            NoHandlerFoundException.class
    })
    public ResponseEntity<Map<String, String>> requisicao(Exception exception) {
        return ResponseEntity.badRequest().body(erro("Requisicao invalida"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> interno(Exception exception, HttpServletRequest request) {
        logger.error("Erro interno [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(erro("Erro interno ao processar a requisicao"));
    }

    private Map<String, String> erro(String mensagem) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("mensagem", mensagem);
        return body;
    }
}
