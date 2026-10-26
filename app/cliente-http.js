const http = require("http");
const https = require("https");
const path = require("path");
const { URL } = require("url");
const certificados = require("./certificados");

const BLOQUEADOS = new Set([
  "host", "content-length", "connection", "keep-alive", "proxy-connection",
  "transfer-encoding", "upgrade", "te", "trailer"
]);

function montarUrl(url, parametros) {
  if (!parametros || parametros.length === 0) {
    return url;
  }
  let base = url;
  let fragmento = "";
  const hash = base.indexOf("#");
  if (hash >= 0) {
    fragmento = base.substring(hash);
    base = base.substring(0, hash);
  }
  const query = parametros.map(function (par) {
    return encodeURIComponent(par.nome) + "=" + encodeURIComponent(par.valor == null ? "" : par.valor);
  }).join("&");
  return base + (base.includes("?") ? "&" : "?") + query + fragmento;
}

function semConsulta(url) {
  try {
    const destino = new URL(url);
    return destino.origin + destino.pathname;
  } catch (erro) {
    const indice = url.indexOf("?");
    return indice < 0 ? url : url.substring(0, indice);
  }
}

function nomes(parametros) {
  if (!parametros || parametros.length === 0) {
    return "nenhum";
  }
  return parametros.map(function (par) {
    return par && par.nome ? String(par.nome).trim() : "?";
  }).join(",");
}

function limitar(texto, maximo) {
  if (texto == null) {
    return null;
  }
  const limpo = String(texto).replace(/\u0000/g, "");
  return limpo.length <= maximo ? limpo : limpo.substring(0, maximo);
}

function diagnostico(resumo, chamada, certificado, erro) {
  const partes = [
    resumo || "falha na chamada",
    (chamada.metodo || "?") + " " + semConsulta(chamada.url),
    "timeout " + chamada.timeoutSegundos + "s",
    "usarCertificado " + Boolean(chamada.usarCertificado),
    "pems " + ((chamada.certificadosDestino && chamada.certificadosDestino.length) || 0),
    "parametros " + nomes(chamada.parametros),
    "certificado " + (certificado || "nenhum")
  ];
  let texto = partes.join(" | ");
  if (erro && erro.message) {
    texto += " | " + erro.name + ": " + String(erro.message).replace(/[\r\n]+/g, " ");
  }
  return limitar(texto, 4000);
}

function textual(contentType) {
  if (!contentType) {
    return true;
  }
  const tipo = contentType.toLowerCase();
  return tipo.includes("json") || tipo.includes("xml") || tipo.includes("text")
    || tipo.includes("html") || tipo.includes("javascript") || tipo.includes("yaml")
    || tipo.includes("csv") || tipo.includes("urlencoded");
}

function charsetDe(contentType) {
  if (!contentType) {
    return null;
  }
  const partes = contentType.split(";");
  for (let i = 1; i < partes.length; i++) {
    const parte = partes[i].trim();
    if (parte.toLowerCase().startsWith("charset=")) {
      const charset = parte.substring("charset=".length).trim().replace(/"/g, "");
      return charset.length <= 40 ? charset : charset.substring(0, 40);
    }
  }
  return null;
}

function comoTexto(corpo, contentType) {
  if (!corpo || !textual(contentType)) {
    return null;
  }
  return corpo.toString("utf8");
}

function handshake(erro) {
  const texto = ((erro && erro.code) || "") + " " + ((erro && erro.message) || "");
  return /HANDSHAKE|CERT_|UNABLE_TO_VERIFY|alert number 40|certificate/i.test(texto);
}

function classificar(chamada, erro, certificado) {
  const texto = ((erro && erro.code) || "") + " " + ((erro && erro.message) || "");
  let resumo = erro && /^(pasta |nenhum certificado|falha ao abrir|pfx sem)/.test(erro.message)
    ? erro.message
    : "falha na chamada";
  let status = "ERRO";
  if (erro && (erro.code === "ETIMEDOUT" || erro.code === "ECONNABORTED" || /timeout/i.test(texto))) {
    resumo = "tempo limite de " + chamada.timeoutSegundos + " segundos";
    status = "TIMEOUT";
  } else if (erro && (erro.code === "ENOTFOUND" || erro.code === "EAI_AGAIN")) {
    resumo = "falha de DNS";
  } else if (erro && (erro.code === "ECONNREFUSED" || erro.code === "ENETUNREACH" || erro.code === "EHOSTUNREACH")) {
    resumo = "falha de conexao";
  } else if (handshake(erro)) {
    resumo = "falha de handshake";
  } else if (/SSL|TLS|bad record|decrypt/i.test(texto)) {
    resumo = "falha de tls";
  }
  return {
    status: status,
    httpStatus: null,
    corpo: null,
    contentType: null,
    charset: null,
    headers: [],
    mensagem: diagnostico(resumo, chamada, certificado, erro),
    certificadoArquivo: certificado,
    tentarOutro: status !== "TIMEOUT" && handshake(erro)
  };
}

function pedir(url, chamada, material, certificado) {
  return new Promise(function (resolve) {
    let destino;
    try {
      destino = new URL(url);
    } catch (erro) {
      resolve(classificar(chamada, erro, certificado));
      return;
    }
    const seguro = destino.protocol === "https:";
    const biblioteca = seguro ? https : http;
    const headers = {};
    (chamada.headers || []).forEach(function (par) {
      if (!par || !par.nome || BLOQUEADOS.has(String(par.nome).trim().toLowerCase())) {
        return;
      }
      if (String(par.nome).toLowerCase() === "content-type") {
        return;
      }
      headers[par.nome] = par.valor == null ? "" : par.valor;
    });
    const enviaCorpo = chamada.corpo && chamada.metodo !== "GET" && chamada.metodo !== "HEAD";
    if (enviaCorpo && chamada.contentType) {
      headers["Content-Type"] = chamada.contentType;
    }
    const opcoes = {
      protocol: destino.protocol,
      hostname: destino.hostname,
      port: destino.port || (seguro ? 443 : 80),
      path: destino.pathname + destino.search,
      method: chamada.metodo,
      headers: headers,
      timeout: chamada.timeoutSegundos * 1000,
      rejectUnauthorized: true
    };
    if (material) {
      opcoes.cert = material.cert;
      opcoes.key = material.key;
    }
    const req = biblioteca.request(opcoes, function (res) {
      const pedacos = [];
      res.on("data", function (pedaco) { pedacos.push(pedaco); });
      res.on("end", function () {
        const corpo = Buffer.concat(pedacos);
        const contentType = res.headers["content-type"] || null;
        const headersResposta = Object.keys(res.headers).map(function (nome) {
          const valor = res.headers[nome];
          return { nome: nome, valor: Array.isArray(valor) ? valor.join(", ") : valor };
        });
        let mensagem = null;
        if (res.statusCode >= 400) {
          const texto = comoTexto(corpo, contentType);
          mensagem = texto && texto.trim() ? limitar(texto.trim(), 4000) : "HTTP " + res.statusCode;
        }
        resolve({
          status: "FINALIZADO",
          httpStatus: res.statusCode,
          corpo: corpo,
          contentType: contentType ? limitar(contentType, 255) : null,
          charset: charsetDe(contentType),
          headers: headersResposta,
          mensagem: mensagem,
          certificadoArquivo: certificado,
          tentarOutro: false
        });
      });
    });
    req.on("timeout", function () {
      req.destroy(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" }));
    });
    req.on("error", function (erro) {
      resolve(classificar(chamada, erro, certificado));
    });
    if (enviaCorpo) {
      req.write(chamada.corpo);
    }
    req.end();
  });
}

async function executar(chamada) {
  const url = montarUrl(chamada.url, chamada.parametros);
  if (!chamada.usarCertificado) {
    return pedir(url, chamada, null, null);
  }
  if (!certificados.pasta()) {
    return classificar(chamada, new Error("pasta de certificados nao configurada"), null);
  }
  const arquivos = certificados.listar();
  if (arquivos.length === 0) {
    return classificar(chamada, new Error("nenhum certificado .pfx ou .p12 na pasta path=" + certificados.pasta()), null);
  }
  const inicio = Number(chamada.id) % arquivos.length;
  const tentativas = [];
  let ultimo = null;
  for (let i = 0; i < arquivos.length; i++) {
    const arquivo = arquivos[(inicio + i) % arquivos.length];
    const nome = path.basename(arquivo);
    let material;
    try {
      material = certificados.abrirPfx(arquivo);
    } catch (erro) {
      ultimo = classificar(chamada, erro, nome);
      tentativas.push(ultimo.mensagem);
      continue;
    }
    const resultado = await pedir(url, chamada, material, nome);
    if (!resultado.tentarOutro) {
      return resultado;
    }
    ultimo = resultado;
    tentativas.push(resultado.mensagem);
  }
  const mensagem = limitar(tentativas.join(" || "), 4000);
  return {
    status: "ERRO",
    httpStatus: null,
    corpo: null,
    contentType: null,
    charset: null,
    headers: [],
    mensagem: mensagem,
    certificadoArquivo: ultimo && ultimo.certificadoArquivo,
    tentarOutro: false
  };
}

module.exports = { executar, comoTexto, charsetDe };
