# gateway-spd

Serviço de redirecionamento. O Maker pede a execução de um fluxo ou de uma procedure e recebe um token na hora. O OutSystems também pode descrever uma chamada HTTP: o gateway executa, grava o resultado no Oracle e devolve o id.

WAR `gateway-spd.war`, no mesmo modelo do `dpz-nfe`: Java 8, Spring Boot 2.7, Tomcat externo.

## O que ele faz

1. Recebe a URL do fluxo, o texto e, se vier, o tempo de execução.
2. Responde `200` com um token. Não espera o fluxo.
3. Chama a URL com o texto e o token. Não lê o JSON.
4. O fluxo avisa quando termina ou quando dá erro.
5. A tela consulta o token.
6. Depois de finalizado ou erro, o token fica 5 minutos e sai.
7. Sem aviso até o tempo de execução, o token fica `ERRO`. O padrão é 4 horas.

A resposta da chamada ao Maker não encerra o processo. O encerramento é o aviso do fluxo. Se o aviso não chega até o prazo, mais 30 segundos, o token fica `ERRO` com a mensagem `expirou sem conclusao`.

`tempoExecucaoSegundos` aceita no máximo 24 horas. Acima disso a resposta é `400` e nenhum token é criado.

## Endpoints

`POST /processos`

```json
{
  "url": "http://servidor/fluxo",
  "texto": "{\"devolucao\":754}",
  "tempoExecucaoSegundos": 14400
}
```

`tempoExecucaoSegundos` é opcional. Sem ele, vale 4 horas.

```json
{
  "token": "uuid",
  "status": "EM_EXECUCAO",
  "mensagem": null
}
```

A chamada ao Maker é `POST` `application/x-www-form-urlencoded` com os campos `texto` e `token`.

`POST /processos/{token}/conclusao`

```json
{
  "status": "FINALIZADO",
  "mensagem": null
}
```

`status` também aceita `ERRO`. A `mensagem` é gravada no token.

`POST /processos/procedure`

Executa uma procedure no Oracle do `dpz-nfe`. A resposta também é imediata, com token. O gateway marca `FINALIZADO` quando a procedure retorna, ou `ERRO` com a mensagem do banco. Não há aviso do Maker.

```json
{
  "procedure": "DALPOZZO.MINHA_PROCEDURE",
  "parametros": ["1", "2026-09-30"],
  "tempoExecucaoSegundos": 14400
}
```

`parametros` é opcional. Cada item entra como texto, na ordem da procedure. O nome aceita `procedure`, `schema.procedure` ou `schema.pacote.procedure`. No máximo 100 parâmetros. Quando a procedure volta, o token fica `FINALIZADO`. Falha de banco, timeout ou fila cheia deixa `ERRO` com a mensagem.

`GET /processos/{token}`

Devolve `EM_EXECUCAO`, `FINALIZADO` ou `ERRO`. Token ausente ou já removido responde `404`.

## Chamada HTTP

O endpoint do gateway não tem autenticação. O acesso fica restrito pela rede.

Antes de subir, executar `sql/GATEWAY_REQUISICAO_HTTP.sql` no Oracle. O estado da fila não usa a memória dos processos do Maker.

`POST /requisicoes-http`

`assincrono: true` responde `202` com o id e a fila executa. `assincrono: false` executa na hora e responde `200` com o estado final. Se `chaveIdempotencia` já existir, a resposta é `200` com o estado atual, sem executar de novo.

```json
{
  "metodo": "POST",
  "url": "https://api.exemplo.com/recurso/123",
  "parametros": [{"nome": "pagina", "valor": "1"}],
  "headers": [
    {"nome": "Authorization", "valor": "Bearer segredo"},
    {"nome": "Content-Type", "valor": "application/json"}
  ],
  "tipoCorpo": "JSON",
  "corpoTexto": "{\"campo\":\"valor\"}",
  "tipoRetorno": "BINARIO",
  "timeoutSegundos": 120,
  "chaveIdempotencia": "pedido-123",
  "assincrono": true,
  "processo": "NFSE_ADN",
  "lote": "filial-80213705001289",
  "usarCertificado": false,
  "certificadosDestino": []
}
```

`tipoCorpo` aceita `NENHUM`, `JSON`, `TEXTO`, `XML`, `FORM_URLENCODED` e `BINARIO_BASE64`. Upload de arquivo não entra nesta versão. `GET` e `HEAD` usam `NENHUM`.

`GET /requisicoes-http/{id}` devolve o estado, o status HTTP, o tipo e o tamanho. O corpo não vem nesse JSON.

`GET /requisicoes-http/{id}/resposta` baixa o corpo gravado. Zip e demais binários saem por aqui.

HTTP 2xx, 3xx, 4xx e 5xx do destino ficam `FINALIZADO`, com status e corpo. Timeout fica `TIMEOUT`. Falha de DNS, conexão ou TLS fica `ERRO`. Não há reenvio nem cancelamento.

A fila é FIFO pelo id, com várias instâncias. A cada 5 minutos, execução `EM_EXECUCAO` que passou de `timeoutSegundos` vira `ERRO` com a mensagem `execucao interrompida na instancia <nome>`.

Certificado cliente: `GATEWAY_CERT_PATH` e `GATEWAY_CERT_SENHA`. A pasta é lida na hora, os `.pfx` e `.p12` são balanceados pelo id, e falha de handshake tenta o próximo arquivo. `usarCertificado: true` pede esse certificado. `certificadosDestino` é a lista PEM pública para confiar no destino naquela chamada. `GATEWAY_INSTANCIA` fixa o nome da instância; sem ela, o gateway usa o hostname.

Segredo de header, senha e conteúdo de certificado não entram no log.

## Actuator e Swagger

`GET /actuator/health` e `GET /actuator/info`.

Swagger em `/swagger/index.html`. O contrato JSON fica em `/v3/api-docs`.

## Subir

```bash
mvn clean package
```

O arquivo fica em `target/gateway-spd.war`. Porta local: `8020`.

## Log

Arquivo `logs/gateway-spd.log` na pasta do Tomcat (`catalina.base/logs`). Fora do Tomcat, `./logs/gateway-spd.log`. Rotação diária, 30 dias. Processo em erro entra como `ERROR` com o token, a origem e a mensagem. Nível: `GATEWAY_LOG_LEVEL`.
