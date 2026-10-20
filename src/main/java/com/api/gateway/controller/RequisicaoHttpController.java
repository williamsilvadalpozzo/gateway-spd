package com.api.gateway.controller;

import com.api.gateway.dto.CriarRequisicaoHttpRequest;
import com.api.gateway.dto.ErroResponse;
import com.api.gateway.dto.RequisicaoHttpResponse;
import com.api.gateway.service.CorpoResposta;
import com.api.gateway.service.RequisicaoHttpService;
import com.api.gateway.service.ResultadoCriacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/requisicoes-http")
@Tag(name = "Requisicoes HTTP", description = "Registra, consulta e baixa o resultado de uma chamada HTTP")
public class RequisicaoHttpController {

    private final RequisicaoHttpService service;

    public RequisicaoHttpController(RequisicaoHttpService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registra uma chamada HTTP",
            description = "assincrono true responde 202 e a fila executa. assincrono false executa na hora e responde 200. "
                    + "Chave de idempotencia ja existente responde 200 com o estado atual, sem executar de novo.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Requisicao assincrona aceita",
                    content = @Content(schema = @Schema(implementation = RequisicaoHttpResponse.class))),
            @ApiResponse(responseCode = "200", description = "Estado atual ou resultado da chamada sincrona",
                    content = @Content(schema = @Schema(implementation = RequisicaoHttpResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido invalido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<RequisicaoHttpResponse> criar(@RequestBody CriarRequisicaoHttpRequest request) {
        ResultadoCriacao resultado = service.criar(request);
        HttpStatus status = resultado.isAceita() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(RequisicaoHttpResponse.de(resultado.getRequisicao()));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta o estado da requisicao",
            description = "Devolve status, HTTP status, tipo e tamanho. O corpo fica no endpoint de download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado da requisicao",
                    content = @Content(schema = @Schema(implementation = RequisicaoHttpResponse.class))),
            @ApiResponse(responseCode = "404", description = "Requisicao nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<RequisicaoHttpResponse> consultar(@PathVariable("id") long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RequisicaoHttpResponse.de(service.consultar(id)));
    }

    @GetMapping("/{id}/resposta")
    @Operation(summary = "Baixa o corpo da resposta",
            description = "Envia os bytes gravados com o Content-Type do destino. Sem tipo, usa application/octet-stream.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corpo da resposta"),
            @ApiResponse(responseCode = "404", description = "Requisicao ou resposta indisponivel",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<byte[]> resposta(@PathVariable("id") long id) {
        CorpoResposta corpo = service.baixar(id);
        MediaType tipo = tipo(corpo.getContentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(tipo)
                .body(corpo.getBytes());
    }

    private MediaType tipo(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
