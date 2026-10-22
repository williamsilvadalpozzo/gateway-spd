package com.api.gateway.controller;

import com.api.gateway.domain.Processo;
import com.api.gateway.domain.StatusProcesso;
import com.api.gateway.dto.ConclusaoRequest;
import com.api.gateway.dto.IniciarProcedureRequest;
import com.api.gateway.dto.IniciarProcessoRequest;
import com.api.gateway.dto.ProcessoResponse;
import com.api.gateway.service.ProcessoService;
import com.api.gateway.dto.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.Valid;
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
@RequestMapping("/processos")
@Tag(name = "Processos", description = "Token em memoria para fluxo Maker ou procedure Oracle")
public class ProcessoController {

    private final ProcessoService processoService;

    public ProcessoController(ProcessoService processoService) {
        this.processoService = processoService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inicia um fluxo web do Maker",
            description = "Responde na hora com o token. A chamada ao Maker vai em seguida, com os campos texto e token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processo aceito",
                    content = @Content(schema = @Schema(implementation = ProcessoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido invalido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<?> iniciar(@Valid @RequestBody IniciarProcessoRequest request) {
        String url = request.getUrl().trim();
        if (!urlHttp(url)) {
            return ResponseEntity.badRequest().body(erro("A url deve comecar com http:// ou https://"));
        }
        Processo processo = processoService.iniciar(url, request.getTexto(), request.getTempoExecucaoSegundos());
        return ResponseEntity.ok(ProcessoResponse.de(processo));
    }

    @PostMapping(value = "/procedure", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Executa uma procedure Oracle",
            description = "Responde na hora com o token. O gateway marca FINALIZADO quando a procedure retorna, ou ERRO com a mensagem do banco.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processo aceito",
                    content = @Content(schema = @Schema(implementation = ProcessoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido invalido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<?> iniciarProcedure(@Valid @RequestBody IniciarProcedureRequest request) {
        String procedure = request.getProcedure().trim();
        if (!nomeProcedure(procedure)) {
            return ResponseEntity.badRequest().body(erro("A procedure deve ser nome ou schema.nome"));
        }
        Processo processo = processoService.iniciarProcedure(
                procedure,
                request.getParametros(),
                request.getTempoExecucaoSegundos());
        return ResponseEntity.ok(ProcessoResponse.de(processo));
    }

    @GetMapping(value = "/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta o token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processo encontrado",
                    content = @Content(schema = @Schema(implementation = ProcessoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Token ausente ou ja removido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<?> consultar(
            @Parameter(description = "Token devolvido na abertura", required = true) @PathVariable String token) {
        Optional<Processo> processo = processoService.consultar(token);
        if (!processo.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro("Processo nao encontrado"));
        }
        return ResponseEntity.ok(ProcessoResponse.de(processo.get()));
    }

    @PostMapping(value = "/{token}/conclusao", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe o aviso de conclusao do Maker",
            description = "Aceita FINALIZADO ou ERRO. Nao encerra uma procedure; esse caminho se encerra sozinho.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual do token",
                    content = @Content(schema = @Schema(implementation = ProcessoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido invalido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Token ausente ou ja removido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<?> concluir(
            @Parameter(description = "Token devolvido na abertura", required = true) @PathVariable String token,
            @Valid @RequestBody ConclusaoRequest request) {
        if (request.getStatus() == StatusProcesso.EM_EXECUCAO) {
            return ResponseEntity.badRequest().body(erro("A conclusao aceita FINALIZADO ou ERRO"));
        }
        Optional<Processo> processo = processoService.concluir(token, request.getStatus(), request.getMensagem());
        if (!processo.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro("Processo nao encontrado"));
        }
        return ResponseEntity.ok(ProcessoResponse.de(processo.get()));
    }

    private boolean nomeProcedure(String procedure) {
        return procedure != null && procedure.matches("[A-Za-z][A-Za-z0-9_$#]*(\\.[A-Za-z][A-Za-z0-9_$#]*){0,2}");
    }

    private boolean urlHttp(String url) {
        try {
            URL destino = new URL(url);
            String protocolo = destino.getProtocol();
            return ("http".equalsIgnoreCase(protocolo) || "https".equalsIgnoreCase(protocolo))
                    && destino.getHost() != null
                    && !destino.getHost().trim().isEmpty();
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private Map<String, String> erro(String mensagem) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("mensagem", mensagem);
        return body;
    }
}
