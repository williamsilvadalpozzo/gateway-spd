# gateway-spd

Serviço de redirecionamento. O Maker pede a execução, recebe um token na hora, e o gateway chama o fluxo do Maker em seguida. O estado fica só na memória.

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
