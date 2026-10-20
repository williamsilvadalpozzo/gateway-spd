# Proposta — Redirecionador HTTP universal

## Estado

Definicao fechada e implementada. A tabela e criada por sql/GATEWAY_REQUISICAO_HTTP.sql.

## Objetivo

Receber do OutSystems uma descricao completa de uma chamada HTTP, persistir a
requisicao, devolver imediatamente seu identificador e executa-la de forma
assincrona. A resposta remota deve ser preservada no Oracle para consulta ou
download posterior.

O comportamento desejado e semelhante a um cliente como o Postman, mas operado
por uma fila persistente.

## Decisoes confirmadas

- cada chamada informa se o processamento e assincrono ou sincrono;
- resposta da chamada assincrona contendo o ID da requisicao;
- fila persistida no Oracle;
- processamento FIFO, da requisicao mais antiga para a mais nova;
- varias instancias podem consumir a fila ao mesmo tempo;
- cada item e reivindicado e marcado `EM_EXECUCAO` antes da chamada;
- URL de destino livre, limitada aos protocolos HTTP e HTTPS;
- headers, inclusive autenticacao, enviados pelo OutSystems;
- endpoint do gateway sem autenticacao;
- suporte a JSON, texto, XML, formulario e binario;
- upload de arquivo fica de fora desta versao;
- corpo da resposta e erro da requisicao armazenados na tabela;
- formato esperado da resposta pode ser informado na chamada;
- consulta posterior do status e download da resposta;
- certificado cliente lido da pasta configurada, com balanceamento entre os arquivos encontrados;
- processo informado pelo chamador para agrupar as requisicoes de um servico;
- lote informado pelo chamador como texto livre;
- corpo de entrada e resposta sem limite de tamanho;
- registros e BLOBs sem prazo de retencao e sem limpeza automatica;
- falha sem reenvio e sem cancelamento.

## Fluxo

1. Validar a chamada recebida do OutSystems.
2. Persistir requisicao, corpo, processo e lote.
3. Se `assincrono` for verdadeiro, gravar `PENDENTE`, confirmar a transacao e
   responder `202 Accepted` com o ID.
4. Se `assincrono` for falso, a propria requisicao executa a chamada, grava o
   resultado e responde `200` com o estado final.
5. Na fila, cada instancia reivindica a linha `PENDENTE` assincrona mais antiga
   que ainda nao foi reivindicada.
6. A reivindicacao grava `EM_EXECUCAO`, a instancia e o inicio antes de chamar
   o destino.
7. Montar e executar a chamada HTTP.
8. Persistir status HTTP, headers, bytes da resposta e, quando houver, o erro.
9. Finalizar como `FINALIZADO`, `ERRO` ou `TIMEOUT`.

Uma resposta HTTP 4xx ou 5xx fica `FINALIZADO`, com o status HTTP e o corpo
recebido gravados. Falha de transporte, TLS, DNS, timeout ou processamento
interno fica `ERRO` ou `TIMEOUT`, com a mensagem e o detalhe disponivel gravados
na mesma tabela. O erro nao fica somente no log da aplicacao.

## Modo, processo e lote

`assincrono` e obrigatorio.

- `true`: a linha nasce `PENDENTE` e somente a fila a executa. A resposta HTTP e
  `202`, com o ID.
- `false`: a instancia que recebeu a chamada grava `EM_EXECUCAO` e executa na
  hora. A fila nao reivindica essa linha. A resposta HTTP e `200`, com o estado
  final, o status HTTP do destino e a mensagem de erro, quando houver. O corpo,
  inclusive um zip, continua no endpoint de download.

`processo` e o nome do servico usado para agrupar as requisicoes, por exemplo
`NFSE_ADN` ou `DOWNLOAD_ZIP`. `lote` e um texto livre do requisitante, como um
identificador de rodada, CNPJ ou NSU. Os dois sao gravados como vieram, sem
lista fixa de valores.

## Fila com varias instancias

A ordem continua sendo o `ID` crescente. A reivindicacao precisa ser atomica para
duas instancias nao executarem a mesma linha:

```sql
SELECT ID
  FROM GATEWAY_REQUISICAO_HTTP
 WHERE STATUS = 'PENDENTE'
   AND ASSINCRONO = 1
 ORDER BY ID
 FETCH FIRST 1 ROW ONLY
   FOR UPDATE SKIP LOCKED;
```

Em seguida, na mesma transacao:

```sql
UPDATE GATEWAY_REQUISICAO_HTTP
   SET STATUS = 'EM_EXECUCAO',
       INSTANCIA = :instancia,
       INICIADO_EM = SYSTIMESTAMP,
       HEARTBEAT_EM = SYSTIMESTAMP
 WHERE ID = :id
   AND STATUS = 'PENDENTE';
```

`SKIP LOCKED` permite que outra instancia avance para o proximo pendente enquanto
a primeira ainda confirma a reivindicacao. A atualizacao condicional por
`STATUS = 'PENDENTE'` impede consumo duplicado.

Enquanto a chamada estiver em andamento, a instancia atualiza `HEARTBEAT_EM`.
Se ela sair de operacao, a linha permanece `EM_EXECUCAO` sem ninguem processando.

A cada 5 minutos, as instancias ativas procuram requisicoes `EM_EXECUCAO` que
atingiram o tempo limite informado em `timeoutSegundos`, contado de
`INICIADO_EM`. Cada uma encontrada passa a `ERRO`, com a mensagem
`execucao interrompida na instancia <nome>`. Nao ha reenvio. Uma nova chamada
precisa ser registrada pelo requisitante.

## Contrato inicial

### Criar requisicao

`POST /requisicoes-http`

Exemplo:

```json
{
  "metodo": "POST",
  "url": "https://api.exemplo.com/recurso",
  "parametros": [
    {"nome": "pagina", "valor": "1"}
  ],
  "headers": [
    {"nome": "Authorization", "valor": "Bearer segredo"},
    {"nome": "Content-Type", "valor": "application/json"}
  ],
  "tipoCorpo": "JSON",
  "corpoTexto": "{\"campo\":\"valor\"}",
  "corpoBase64": null,
  "tipoRetorno": "AUTO",
  "timeoutSegundos": 120,
  "chaveIdempotencia": "pedido-123",
  "assincrono": true,
  "processo": "NFSE_ADN",
  "lote": "filial-80213705001289-nsu-123456",
  "usarCertificado": false,
  "certificadosDestino": []
}
```

Listas sao usadas para parametros e headers para preservar nomes repetidos.

Tipos de corpo previstos:

- `NENHUM`;
- `JSON`;
- `TEXTO`;
- `XML`;
- `FORM_URLENCODED`;
- `BINARIO_BASE64`.

Tipos de retorno previstos:

- `AUTO`;
- `JSON`;
- `TEXTO`;
- `XML`;
- `BINARIO`.

Resposta:

```json
{
  "id": 123,
  "status": "PENDENTE",
  "criadoEm": "2026-10-15T13:00:00-03:00"
}
```

### Consultar

`GET /requisicoes-http/{id}`

Retorna metadados, estado, status HTTP, tipo e tamanho da resposta, mas nao inclui
o BLOB no JSON.

### Baixar resposta

`GET /requisicoes-http/{id}/resposta`

Transmite o BLOB usando o `Content-Type` recebido do destino. Para respostas sem
`Content-Type`, utiliza `application/octet-stream`.

## Tabela proposta

`GATEWAY_REQUISICAO_HTTP`:

- `ID` — PK;
- `CHAVE_IDEMPOTENCIA` — identificador opcional do chamador;
- `ASSINCRONO` — `1` entra na fila; `0` e executada na propria chamada;
- `PROCESSO` — texto recebido para agrupar as requisicoes de um servico;
- `LOTE` — texto livre informado pelo requisitante;
- `STATUS` — `PENDENTE`, `EM_EXECUCAO`, `FINALIZADO`, `ERRO` ou `TIMEOUT`;
- `INSTANCIA` — instancia que reivindicou a linha;
- `HEARTBEAT_EM` — ultimo sinal da instancia durante o consumo;
- `METODO`;
- `URL`;
- `PARAMETROS_JSON` — `CLOB`;
- `HEADERS_JSON` — `CLOB` com os headers necessarios para executar a chamada;
- `TIPO_CORPO`;
- `CONTENT_TYPE_REQUISICAO`;
- `CORPO_REQUISICAO` — `BLOB`;
- `TIPO_RETORNO`;
- `TIMEOUT_SEGUNDOS`;
- `CERTIFICADO_ARQUIVO` — nome do arquivo usado no handshake, quando houver;
- `USAR_CERTIFICADO` — indica se a chamada apresenta um certificado da pasta;
- `CERTIFICADOS_DESTINO` — `CLOB` com os certificados publicos enviados para confiar na URL;
- `HTTP_STATUS`;
- `HEADERS_RESPOSTA_JSON` — `CLOB`;
- `CONTENT_TYPE_RESPOSTA`;
- `CHARSET_RESPOSTA`;
- `CORPO_RESPOSTA` — `BLOB`;
- `TAMANHO_RESPOSTA`;
- `MENSAGEM_ERRO` — `CLOB`;
- `CRIADO_EM`;
- `INICIADO_EM`;
- `FINALIZADO_EM`;
- campos de auditoria.

Indices minimos:

- PK em `ID`;
- indice de fila em `(ASSINCRONO, STATUS, ID)`;
- indice de agrupamento em `(PROCESSO, LOTE)`;
- `UNIQUE` na chave de idempotencia quando informada.

O `ID`, crescente, define a ordem FIFO. A selecao usa bloqueio de linha e
`SKIP LOCKED`, porque mais de uma instancia pode consumir a fila.

## Erros persistidos

Todo resultado consultavel fica na tabela:

- destino respondeu 2xx ou 3xx: `FINALIZADO`, `HTTP_STATUS` e `CORPO_RESPOSTA`;
- destino respondeu 4xx ou 5xx: `FINALIZADO`, `HTTP_STATUS`, `CORPO_RESPOSTA` e
  `MENSAGEM_ERRO` com o motivo recebido;
- timeout: `TIMEOUT`, `MENSAGEM_ERRO` e o corpo parcial quando existir;
- DNS, conexao, TLS ou falha interna: `ERRO` e `MENSAGEM_ERRO`;
- instancia interrompida: `ERRO`, instancia e mensagem de interrupcao.

`GET /requisicoes-http/{id}` devolve status, HTTP status e mensagem de erro. O
corpo, inclusive o corpo de erro do destino, continua no endpoint de download.

## Certificado cliente

O certificado A1 nao vai no JSON, no header nem na tabela. Ele entra no
handshake TLS, quando o destino exige autenticacao mutua. A consulta de NFS-e
no ADN funciona desse modo: o `GET` leva o NSU na URL e o gateway apresenta o
certificado da matriz durante a conexao.

O application configura a pasta e a senha. Os nomes nao ficam fixos na
configuracao. Cada instancia precisa enxergar a mesma pasta.

```yaml
gateway:
  certificados:
    path: ${GATEWAY_CERT_PATH:}
    senha: ${GATEWAY_CERT_SENHA:}
```

Na subida, e de novo na hora de usar, o gateway lista os arquivos `.pfx` e
`.p12` dessa pasta, confere a quantidade e os nomes. A lista acompanhada e a
da pasta naquele momento: incluir ou retirar um arquivo passa a valer na
proxima chamada, sem alterar o application.

A requisicao nao escolhe o arquivo. `usarCertificado: true` indica que a
chamada precisa do certificado, como a consulta ao ADN. O gateway ordena os
nomes e usa o resto da divisao do ID da requisicao pela quantidade encontrada.
Assim o uso se distribui entre os arquivos da pasta, tambem com varias
instancias. O nome usado fica em `CERTIFICADO_ARQUIVO`. Senha e conteudo do
arquivo nao sao persistidos. Se o handshake falhar, a mesma execucao tenta o
proximo arquivo da pasta antes de gravar `ERRO`.

Os certificados da pasta sao da matriz. O ADN aceita certificado de outro
estabelecimento da mesma empresa quando o CNPJ raiz, os oito primeiros
digitos, coincide com o CNPJ consultado.

Sem `cnpjConsulta`, o ADN devolve os documentos do CNPJ completo que esta no
certificado, no caso a matriz. Para a filial, o OutSystems informa o CNPJ dela
nesse parametro. O NSU e de cada CNPJ, entao matriz e filial nao compartilham
o mesmo cursor.

Exemplo da consulta da filial com certificado da matriz:

```json
{
  "metodo": "GET",
  "url": "https://adn.nfse.gov.br/contribuintes/DFe/123456",
  "parametros": [
    {"nome": "cnpjConsulta", "valor": "80213705001289"}
  ],
  "headers": [
    {"nome": "Accept", "valor": "application/json"}
  ],
  "tipoCorpo": "NENHUM",
  "tipoRetorno": "BINARIO",
  "usarCertificado": true,
  "certificadosDestino": []
}
```

O corpo recebido, inclusive quando compactado, fica integral no `BLOB`.

## Confianca no destino

`usarCertificado` e `certificadosDestino` tratam lados diferentes da conexao HTTPS.

`usarCertificado` apresenta um certificado nosso, lido da pasta, quando o
destino exige autenticacao mutua. A URL continua sendo informada em `url`.

`certificadosDestino` resolve o caso em que o Java recusa o certificado do
servidor. O truststore padrao da JVM nao contem a autoridade daquele destino,
como ocorreu na distribuicao da SEFAZ com a raiz GlobalSign. O campo e uma
lista de strings PEM, uma por certificado publico: raiz e intermediarios.
Cada item contem o texto completo, incluindo `BEGIN CERTIFICATE` e
`END CERTIFICATE`. Eles nao substituem o certificado A1 e nao carregam chave
privada.

Na execucao, o gateway soma esses certificados ao truststore da JVM somente
para aquela URL. A validacao do nome do host e da cadeia continua ativa. Nao ha
opcao de aceitar qualquer certificado. Se a lista vier vazia, vale apenas o
truststore padrao.

Como a fila e assincrona, os PEM ficam em `CERTIFICADOS_DESTINO` para a
instancia que for executar a chamada. Um destino publico com cadeia ja aceita
pelo Java nao precisa enviar nada.

## Seguranca

O endpoint do gateway nao exige autenticacao. O acesso fica restrito pela rede
em que o servico for publicado.

- somente HTTP e HTTPS;
- sem limite de tamanho de requisicao ou resposta;
- o tempo limite de cada chamada e o `timeoutSegundos` informado nela;
- quantidade maxima de redirects, preferencialmente zero por padrao;
- bloqueio dos headers `Host`, `Content-Length`, `Connection` e equivalentes;
- validacao TLS normal, sem opcao de ignorar certificado;
- `Authorization`, `Cookie`, chaves de API e outros segredos nunca entram em log.

## Recuperacao e garantias

- requisicoes `PENDENTE` sobrevivem ao reinicio e continuam na ordem;
- varias instancias reivindicam linhas diferentes sem duplicar a execucao;
- uma requisicao `EM_EXECUCAO` que atingiu o proprio tempo limite e marcada
  `ERRO` pela analise das instancias ativas, a cada 5 minutos;
- falha, timeout e execucao interrompida nao geram reenvio nem cancelamento;
- nao e possivel garantir exatamente uma execucao em sistemas externos;
- `chaveIdempotencia` evita duplicacao no gateway, mas o destino tambem deve
  suportar idempotencia para eliminar efeitos duplicados;
- chave ja existente devolve o registro atual em HTTP 200, sem criar outra
  linha e sem executar de novo.

## Impacto no gateway atual

O executor atual usa varias threads e o estado dos processos fica somente em
memoria. O novo redirecionador nao deve reutilizar esse armazenamento para a fila
FIFO.

O script `GATEWAY_PROCESSO_LOG.sql` registra apenas chamadas e nao comporta estado,
corpos e respostas. Deve ser criada uma tabela especifica.

