const http = require("http");
const https = require("https");
const { URL } = require("url");
const { randomUUID } = require("crypto");
const config = require("./config");
const oracle = require("./oracle");

const processos = new Map();
const NOME = /^[A-Za-z][A-Za-z0-9_$#]*(\.[A-Za-z][A-Za-z0-9_$#]*){0,2}$/;

function resposta(processo) {
  const corpo = { token: processo.token, status: processo.status };
  if (processo.mensagem) {
    corpo.mensagem = processo.mensagem;
  }
  return corpo;
}

function prazo(segundos) {
  if (segundos == null) {
    return config.prazoPadraoMs;
  }
  const valor = Number(segundos);
  if (!Number.isFinite(valor) || valor <= 0) {
    const erro = new Error("tempoExecucaoSegundos deve ser maior que zero");
    erro.statusHttp = 400;
    throw erro;
  }
  const ms = valor * 1000;
  if (ms > config.prazoMaximoMs) {
    const erro = new Error("tempoExecucaoSegundos excede 24 horas");
    erro.statusHttp = 400;
    throw erro;
  }
  return ms;
}

function iniciar(url, texto, tempo) {
  const limite = Date.now() + prazo(tempo);
  const processo = {
    token: randomUUID(),
    status: "EM_EXECUCAO",
    mensagem: null,
    origem: "fluxo",
    destino: url,
    limite: limite,
    finalizadoEm: null
  };
  processos.set(processo.token, processo);
  console.log("Processo aceito token=" + processo.token + " origem=fluxo destino=" + url);
  setImmediate(function () {
    chamarMaker(processo, texto);
  });
  return resposta(processo);
}

function iniciarProcedure(procedure, parametros, tempo) {
  const valores = Array.isArray(parametros) ? parametros.map(function (item) {
    return item == null ? null : String(item);
  }) : [];
  if (valores.length > 100) {
    const erro = new Error("A procedure aceita no maximo 100 parametros");
    erro.statusHttp = 400;
    throw erro;
  }
  const limite = Date.now() + prazo(tempo);
  const processo = {
    token: randomUUID(),
    status: "EM_EXECUCAO",
    mensagem: null,
    origem: "procedure",
    destino: procedure,
    limite: limite,
    finalizadoEm: null
  };
  processos.set(processo.token, processo);
  console.log("Processo aceito token=" + processo.token + " origem=procedure destino=" + procedure);
  setImmediate(function () {
    executarProcedure(processo, valores).then(function () {
      concluirInterno(processo.token, "FINALIZADO", null);
    }).catch(function (erro) {
      concluirInterno(processo.token, "ERRO", erro.message || "Falha ao executar a procedure");
    });
  });
  return resposta(processo);
}

function consultar(token) {
  return processos.get(token) || null;
}

function concluir(token, status, mensagem) {
  const processo = processos.get(token);
  if (!processo) {
    return null;
  }
  if (processo.origem === "procedure") {
    return processo;
  }
  if (processo.status !== "EM_EXECUCAO") {
    return processo;
  }
  processo.status = status;
  processo.mensagem = mensagem || null;
  processo.finalizadoEm = Date.now();
  return processo;
}

function concluirInterno(token, status, mensagem) {
  const processo = processos.get(token);
  if (!processo || processo.status !== "EM_EXECUCAO") {
    return;
  }
  processo.status = status;
  processo.mensagem = mensagem;
  processo.finalizadoEm = Date.now();
}

function limpar(agora) {
  for (const processo of processos.values()) {
    if (processo.status === "EM_EXECUCAO" && agora > processo.limite + config.folgaPrazoMs) {
      processo.status = "ERRO";
      processo.mensagem = "expirou sem conclusao";
      processo.finalizadoEm = agora;
      console.error("Processo em erro token=" + processo.token + " mensagem=expirou sem conclusao");
    }
    if (processo.status !== "EM_EXECUCAO" && processo.finalizadoEm
        && agora > processo.finalizadoEm + config.retencaoMs) {
      processos.delete(processo.token);
    }
  }
}

function chamarMaker(processo, texto) {
  let destino;
  try {
    destino = new URL(processo.destino);
  } catch (erro) {
    concluirInterno(processo.token, "ERRO", "Falha ao chamar o Maker");
    return;
  }
  const corpo = "texto=" + encodeURIComponent(texto == null ? "" : texto)
    + "&token=" + encodeURIComponent(processo.token);
  const biblioteca = destino.protocol === "https:" ? https : http;
  const req = biblioteca.request({
    protocol: destino.protocol,
    hostname: destino.hostname,
    port: destino.port || (destino.protocol === "https:" ? 443 : 80),
    path: destino.pathname + destino.search,
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
      "Content-Length": Buffer.byteLength(corpo)
    },
    timeout: Math.max(processo.limite - Date.now(), 1)
  }, function (res) {
    res.resume();
    if (res.statusCode < 200 || res.statusCode >= 300) {
      concluirInterno(processo.token, "ERRO", "Maker respondeu HTTP " + res.statusCode);
    }
  });
  let enviado = false;
  req.on("error", function (erro) {
    if (enviado) {
      console.warn("Chamada ao Maker enviada sem resposta util token=" + processo.token);
      return;
    }
    concluirInterno(processo.token, "ERRO", erro.message || "Falha ao chamar o Maker");
  });
  req.write(corpo);
  enviado = true;
  req.end();
}

async function executarProcedure(processo, valores) {
  if (!NOME.test(processo.destino)) {
    throw new Error("A procedure deve ser nome ou schema.nome");
  }
  const marcas = valores.map(function (_, indice) { return ":" + (indice + 1); }).join(", ");
  const sql = "BEGIN " + processo.destino + "(" + marcas + "); END;";
  const binds = {};
  valores.forEach(function (valor, indice) {
    binds[String(indice + 1)] = valor;
  });
  const conexao = await oracle.conexao();
  try {
    await conexao.execute(sql, binds, {
      autoCommit: true,
      callTimeout: Math.max(Math.floor((processo.limite - Date.now()) / 1000), 1) * 1000
    });
  } catch (erro) {
    const codigo = erro && erro.errorNum;
    if (codigo === 1013) {
      throw new Error("A procedure excedeu o tempo de execucao");
    }
    throw new Error(erro.message || "Falha ao executar a procedure");
  } finally {
    await conexao.close();
  }
}

module.exports = {
  iniciar: iniciar,
  iniciarProcedure: iniciarProcedure,
  consultar: consultar,
  concluir: concluir,
  limpar: limpar,
  nomeProcedure: NOME
};
