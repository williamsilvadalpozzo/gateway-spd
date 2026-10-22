package com.api.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/versao")
@Tag(name = "Versao", description = "Versao do WAR publicado")
public class VersaoController {

    private final ObjectProvider<BuildProperties> build;

    public VersaoController(ObjectProvider<BuildProperties> build) {
        this.build = build;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mostra a versao publicada",
            description = "Devolve o nome e a versao gravados na compilacao do WAR.")
    public Map<String, String> versao() {
        BuildProperties propriedades = build.getIfAvailable();
        Map<String, String> corpo = new LinkedHashMap<String, String>();
        corpo.put("nome", propriedades == null || propriedades.getName() == null
                ? "gateway-spd" : propriedades.getName());
        corpo.put("versao", propriedades == null ? null : propriedades.getVersion());
        return corpo;
    }
}
