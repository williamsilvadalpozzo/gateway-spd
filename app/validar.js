const { URL } = require("url");

const METODOS = new Set(["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"]);
const CORPOS = new Set(["NENHUM", "JSON", "TEXTO", "XML", "FORM_URLENCODED", "BINARIO_BASE64"]);
const RETORNOS = new Set(["AUTO", "JSON", "TEXTO", "XML", "BINARIO"]);
const BLOQUEADOS = new Set([
  "host", "content-length", "connection", "keep-alive", "proxy-connection",
  "transfer-encoding", "upgrade", "te", "trailer"
]);

function falha(mensagem) {
  const erro = new Error(mensagem);
  erro.statusHttp = 400;
  return erro;
}

function texto(valor, maximo, campo) {
  if (valor == null || String(valor).trim() === "") {
    return null;
  }
  const limpo = String(valor).trim();
  if (limpo.length > maximo) {
    throw falha(campo + " excede o tamanho");
  }
  return limpo;
}

function urlHttp(valor) {
  let destino;
  try {
    destino = new URL(valor);
  } catch (erro) {
    return false;
  }
  return (destino.protocol === "http:" || destino.protocol === "https:") && Boolean(destino.hostname);
}

function validar(pedido) {
  if (!pedido || typeof pedido !== "object") {
    throw falha("Requisicao invalida");
  }
  if (!pedido.metodo || !String(pedido.metodo).trim()) {
    throw falha("metodo e obrigatorio");
  }
  const metodo = String(pedido.metodo).trim().toUpperCase();
  if (!METODOS.has(metodo)) {
    throw falha("metodo HTTP invalido");
  }
  const url = texto(pedido.url, 4000, "url");
  if (!url || /[\r\n\0]/.test(url) || !urlHttp(url)) {
    throw falha("A url deve usar http ou https");
  }
  if (pedido.assincrono == null) {
    throw falha("assincrono e obrigatorio");
  }
  if (!pedido.tipoCorpo || !String(pedido.tipoCorpo).trim()) {
    throw falha("tipoCorpo e obrigatorio");
  }
  const tipoCorpo = String(pedido.tipoCorpo).trim().toUpperCase();
  if (!CORPOS.has(tipoCorpo)) {
    throw falha("tipoCorpo invalido");
  }
  const tipoRetorno = pedido.tipoRetorno == null || String(pedido.tipoRetorno).trim() === ""
    ? "AUTO"
    : String(pedido.tipoRetorno).trim().toUpperCase();
  if (!RETORNOS.has(tipoRetorno)) {
    throw falha("tipoRetorno invalido");
  }
  if (pedido.timeoutSegundos == null) {
    throw falha("timeoutSegundos e obrigatorio");
  }
  const timeout = Number(pedido.timeoutSegundos);
  if (!Number.isInteger(timeout) || timeout <= 0) {
    throw falha("timeoutSegundos deve ser maior que zero");
  }
  if ((metodo === "GET" || metodo === "HEAD") && tipoCorpo !== "NENHUM") {
    throw falha("GET e HEAD nao enviam corpo");
  }
  const parametros = pares(pedido.parametros, "parametro");
  const headers = pares(pedido.headers, "header");
  headers.forEach(function (par) {
    if (BLOQUEADOS.has(par.nome.toLowerCase())) {
      throw falha("O header " + par.nome + " nao pode ser enviado");
    }
  });
  const corpo = corpoDe(tipoCorpo, pedido.corpoTexto, pedido.corpoBase64);
  return {
    metodo: metodo,
    url: url,
    parametros: parametros,
    headers: headers,
    tipoCorpo: tipoCorpo,
    corpo: corpo,
    contentType: corpo ? contentType(headers, tipoCorpo) : null,
    tipoRetorno: tipoRetorno,
    timeoutSegundos: timeout,
    chave: texto(pedido.chaveIdempotencia, 200, "chaveIdempotencia"),
    assincrono: Boolean(pedido.assincrono),
    processo: texto(pedido.processo, 200, "processo"),
    lote: texto(pedido.lote, 400, "lote"),
    usarCertificado: Boolean(pedido.usarCertificado),
    certificadosDestino: pems(pedido.certificadosDestino)
  };
}

function pares(origem, rotulo) {
  if (!origem) {
    return [];
  }
  if (!Array.isArray(origem)) {
    throw falha(rotulo + " invalido");
  }
  return origem.map(function (par) {
    if (!par || par.nome == null || String(par.nome).trim() === "") {
      throw falha("nome do " + rotulo + " e obrigatorio");
    }
    const nome = String(par.nome).trim();
    const valor = par.valor == null ? null : String(par.valor);
    if (/[\r\n]/.test(nome) || (valor && /[\r\n]/.test(valor))) {
      throw falha(rotulo + " invalido");
    }
    return { nome: nome, valor: valor };
  });
}

function corpoDe(tipo, textoCorpo, base64) {
  if (tipo === "NENHUM") {
    return null;
  }
  if (tipo === "BINARIO_BASE64") {
    if (base64 == null || String(base64).trim() === "") {
      throw falha("corpoBase64 e obrigatorio");
    }
    const bytes = Buffer.from(String(base64), "base64");
    if (bytes.length === 0 && String(base64).trim() !== "") {
      throw falha("corpoBase64 invalido");
    }
    return bytes;
  }
  return Buffer.from(textoCorpo == null ? "" : String(textoCorpo), "utf8");
}

function contentType(headers, tipo) {
  const informado = headers.find(function (par) {
    return par.nome.toLowerCase() === "content-type" && par.valor && par.valor.trim();
  });
  if (informado) {
    return informado.valor.trim();
  }
  if (tipo === "JSON") return "application/json; charset=UTF-8";
  if (tipo === "TEXTO") return "text/plain; charset=UTF-8";
  if (tipo === "XML") return "application/xml; charset=UTF-8";
  if (tipo === "FORM_URLENCODED") return "application/x-www-form-urlencoded; charset=UTF-8";
  return "application/octet-stream";
}

function pems(origem) {
  if (!origem || origem.length === 0) {
    return [];
  }
  if (!Array.isArray(origem)) {
    throw falha("Certificado de destino invalido");
  }
  return origem.map(function (pem) {
    if (pem == null || String(pem).trim() === "" || !String(pem).includes("BEGIN CERTIFICATE")) {
      throw falha("Certificado de destino invalido");
    }
    return String(pem).trim();
  });
}

module.exports = { validar, urlHttp };
