const express = require("express");
const config = require("./config");
const certificados = require("./certificados");
const oracle = require("./oracle");
const requisicoes = require("./requisicoes");
const processos = require("./processos");
const validar = require("./validar");

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "20mb" }));

function semCache(res) {
  res.set("Cache-Control", "no-store");
}

function erro(res, status, mensagem) {
  semCache(res);
  res.status(status).json({ mensagem: mensagem });
}

function registrar(prefixo) {
  app.get(prefixo + "/versao", function (req, res) {
    semCache(res);
    res.json({ nome: config.nome, versao: config.versao });
  });

  app.get(prefixo + "/actuator/health", function (req, res) {
    res.json({ status: "UP" });
  });

  app.post(prefixo + "/processos", function (req, res) {
    const url = req.body && req.body.url ? String(req.body.url).trim() : "";
    if (!validar.urlHttp(url)) {
      erro(res, 400, "A url deve comecar com http:// ou https://");
      return;
    }
    if (!req.body.texto || !String(req.body.texto).trim()) {
      erro(res, 400, "Requisicao invalida");
      return;
    }
    try {
      semCache(res);
      res.json(processos.iniciar(url, req.body.texto, req.body.tempoExecucaoSegundos));
    } catch (falha) {
      erro(res, falha.statusHttp || 500, falha.message || "Erro interno ao processar a requisicao");
    }
  });

  app.post(prefixo + "/processos/procedure", function (req, res) {
    const procedure = req.body && req.body.procedure ? String(req.body.procedure).trim() : "";
    if (!processos.nomeProcedure.test(procedure)) {
      erro(res, 400, "A procedure deve ser nome ou schema.nome");
      return;
    }
    try {
      semCache(res);
      res.json(processos.iniciarProcedure(procedure, req.body.parametros, req.body.tempoExecucaoSegundos));
    } catch (falha) {
      erro(res, falha.statusHttp || 500, falha.message || "Erro interno ao processar a requisicao");
    }
  });

  app.get(prefixo + "/processos/:token", function (req, res) {
    const processo = processos.consultar(req.params.token);
    if (!processo) {
      erro(res, 404, "Processo nao encontrado");
      return;
    }
    semCache(res);
    res.json({
      token: processo.token,
      status: processo.status,
      mensagem: processo.mensagem
    });
  });

  app.post(prefixo + "/processos/:token/conclusao", function (req, res) {
    const status = req.body && req.body.status ? String(req.body.status).trim() : "";
    if (status !== "FINALIZADO" && status !== "ERRO") {
      erro(res, 400, "A conclusao aceita FINALIZADO ou ERRO");
      return;
    }
    const processo = processos.concluir(req.params.token, status, req.body.mensagem);
    if (!processo) {
      erro(res, 404, "Processo nao encontrado");
      return;
    }
    semCache(res);
    res.json({
      token: processo.token,
      status: processo.status,
      mensagem: processo.mensagem
    });
  });

  app.post(prefixo + "/requisicoes-http", async function (req, res) {
    try {
      const pedido = validar.validar(req.body);
      const resultado = await requisicoes.criar(pedido);
      semCache(res);
      res.status(resultado.aceita ? 202 : 200).json(resultado.corpo);
    } catch (falha) {
      erro(res, falha.statusHttp || 500, falha.statusHttp ? falha.message : "Erro interno ao processar a requisicao");
      if (!falha.statusHttp) {
        console.error(falha);
      }
    }
  });

  app.get(prefixo + "/requisicoes-http/:id/resposta", async function (req, res) {
    if (!/^\d+$/.test(req.params.id)) {
      erro(res, 400, "Requisicao invalida");
      return;
    }
    try {
      const corpo = await requisicoes.baixar(Number(req.params.id));
      semCache(res);
      res.set("Content-Type", corpo.contentType);
      res.send(corpo.bytes);
    } catch (falha) {
      erro(res, falha.statusHttp || 500, falha.statusHttp ? falha.message : "Erro interno ao processar a requisicao");
    }
  });

  app.get(prefixo + "/requisicoes-http/:id", async function (req, res) {
    if (!/^\d+$/.test(req.params.id)) {
      erro(res, 400, "Requisicao invalida");
      return;
    }
    try {
      semCache(res);
      res.json(await requisicoes.consultar(Number(req.params.id)));
    } catch (falha) {
      erro(res, falha.statusHttp || 500, falha.statusHttp ? falha.message : "Erro interno ao processar a requisicao");
    }
  });
}

registrar("");
registrar(config.contexto);

app.use(function (erroJson, req, res, next) {
  if (erroJson && erroJson.type === "entity.parse.failed") {
    erro(res, 400, "Requisicao invalida");
    return;
  }
  next(erroJson);
});

async function fila() {
  try {
    while (await requisicoes.consumirUm()) {
      // segue enquanto houver pendente
    }
  } catch (falha) {
    oracle.registrarFalha(falha);
  }
}

const servidor = app.listen(config.porta, function () {
  console.log("gateway-spd " + config.versao + " na porta " + config.porta + " instancia " + config.instancia);
  console.log(config.configExiste
    ? "Configuracao lida de " + config.configPath
    : "Arquivo de configuracao ausente em " + config.configPath);
  console.log(certificados.descrever());
});

setInterval(function () { processos.limpar(Date.now()); }, config.limpezaMs);
setInterval(fila, config.filaMs);
setInterval(function () {
  requisicoes.pulsar().catch(oracle.registrarFalha);
}, config.heartbeatMs);
setInterval(function () {
  requisicoes.marcarInterrompidas().catch(oracle.registrarFalha);
}, config.varreduraMs);
setTimeout(fila, config.filaMs);

function encerrar() {
  servidor.close();
  oracle.fechar().catch(function () { /* o processo esta saindo */ });
}

process.on("SIGINT", encerrar);
process.on("SIGTERM", encerrar);
