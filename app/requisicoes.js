const cliente = require("./cliente-http");
const config = require("./config");
const oracle = require("./oracle");

const INSERIR = "INSERT INTO GATEWAY_REQUISICAO_HTTP ("
  + "ID, CHAVE_IDEMPOTENCIA, ASSINCRONO, PROCESSO, LOTE, STATUS, INSTANCIA, HEARTBEAT_EM, "
  + "METODO, URL, PARAMETROS_JSON, HEADERS_JSON, TIPO_CORPO, CONTENT_TYPE_REQUISICAO, "
  + "CORPO_REQUISICAO, TIPO_RETORNO, TIMEOUT_SEGUNDOS, USAR_CERTIFICADO, CERTIFICADOS_DESTINO, "
  + "CRIADO_EM, INICIADO_EM) VALUES ("
  + ":id, :chave, :assincrono, :processo, :lote, :status, :instancia, :heartbeat, "
  + ":metodo, :url, :parametros, :headers, :tipoCorpo, :contentType, :corpo, :tipoRetorno, "
  + ":timeout, :usarCertificado, :destino, :criado, :iniciado)";

const RESUMO = "SELECT ID, CHAVE_IDEMPOTENCIA, ASSINCRONO, PROCESSO, LOTE, STATUS, "
  + "METODO, URL, TIPO_RETORNO, HTTP_STATUS, CONTENT_TYPE_RESPOSTA, CHARSET_RESPOSTA, "
  + "TAMANHO_RESPOSTA, MENSAGEM_ERRO, CERTIFICADO_ARQUIVO, CRIADO_EM, INICIADO_EM, FINALIZADO_EM, "
  + "CASE WHEN STATUS IN ('ERRO', 'TIMEOUT') OR NVL(HTTP_STATUS, 0) >= 400 "
  + "THEN CORPO_RESPOSTA END AS CORPO_ERRO "
  + "FROM GATEWAY_REQUISICAO_HTTP ";

const EXECUCAO = "SELECT ID, ASSINCRONO, STATUS, METODO, URL, PARAMETROS_JSON, HEADERS_JSON, "
  + "CONTENT_TYPE_REQUISICAO, CORPO_REQUISICAO, TIPO_RETORNO, TIMEOUT_SEGUNDOS, "
  + "USAR_CERTIFICADO, CERTIFICADOS_DESTINO FROM GATEWAY_REQUISICAO_HTTP ";

const REIVINDICAR = "SELECT ID FROM GATEWAY_REQUISICAO_HTTP "
  + "WHERE STATUS = 'PENDENTE' AND ASSINCRONO = 1 "
  + "AND ROWID = (SELECT RID FROM ("
  + "SELECT ROWID AS RID FROM GATEWAY_REQUISICAO_HTTP "
  + "WHERE STATUS = 'PENDENTE' AND ASSINCRONO = 1 "
  + "ORDER BY ID) WHERE ROWNUM = 1) "
  + "FOR UPDATE SKIP LOCKED";

const ativas = new Set();

function data(valor) {
  if (!valor) {
    return null;
  }
  const dataValor = valor instanceof Date ? valor : new Date(valor);
  return Number.isNaN(dataValor.getTime()) ? null : dataValor.toISOString();
}

function respostaDe(linha) {
  let corpo = null;
  if (linha.CORPO_ERRO) {
    const texto = cliente.comoTexto(linha.CORPO_ERRO, linha.CONTENT_TYPE_RESPOSTA);
    corpo = texto && texto.trim() ? texto.trim() : null;
  }
  return semNulos({
    id: Number(linha.ID),
    status: linha.STATUS,
    assincrono: Number(linha.ASSINCRONO) === 1,
    processo: linha.PROCESSO,
    lote: linha.LOTE,
    chaveIdempotencia: linha.CHAVE_IDEMPOTENCIA,
    metodo: linha.METODO,
    url: linha.URL,
    tipoRetorno: linha.TIPO_RETORNO,
    httpStatus: linha.HTTP_STATUS == null ? null : Number(linha.HTTP_STATUS),
    mensagem: linha.MENSAGEM_ERRO,
    corpo: corpo,
    contentType: linha.CONTENT_TYPE_RESPOSTA,
    tamanho: linha.TAMANHO_RESPOSTA == null ? null : Number(linha.TAMANHO_RESPOSTA),
    certificadoArquivo: linha.CERTIFICADO_ARQUIVO,
    criadoEm: data(linha.CRIADO_EM),
    iniciadoEm: data(linha.INICIADO_EM),
    finalizadoEm: data(linha.FINALIZADO_EM)
  });
}

function semNulos(objeto) {
  const saida = {};
  Object.keys(objeto).forEach(function (chave) {
    if (objeto[chave] !== null && objeto[chave] !== undefined) {
      saida[chave] = objeto[chave];
    }
  });
  return saida;
}

async function uma(sql, binds) {
  const conexao = await oracle.conexao();
  try {
    const resultado = await conexao.execute(sql, binds, { autoCommit: true });
    return resultado.rows && resultado.rows[0] ? resultado.rows[0] : null;
  } finally {
    await conexao.close();
  }
}

async function consultar(id) {
  const linha = await uma(RESUMO + "WHERE ID = :id", { id: id });
  if (!linha) {
    const erro = new Error("Requisicao nao encontrada");
    erro.statusHttp = 404;
    throw erro;
  }
  return respostaDe(linha);
}

async function baixar(id) {
  const linha = await uma(
    "SELECT STATUS, CONTENT_TYPE_RESPOSTA, CORPO_RESPOSTA FROM GATEWAY_REQUISICAO_HTTP WHERE ID = :id",
    { id: id }
  );
  if (!linha) {
    const erro = new Error("Requisicao nao encontrada");
    erro.statusHttp = 404;
    throw erro;
  }
  if (linha.STATUS === "PENDENTE" || linha.STATUS === "EM_EXECUCAO" || !linha.CORPO_RESPOSTA) {
    const erro = new Error("Resposta nao disponivel");
    erro.statusHttp = 404;
    throw erro;
  }
  return {
    contentType: linha.CONTENT_TYPE_RESPOSTA || "application/octet-stream",
    bytes: linha.CORPO_RESPOSTA
  };
}

async function criar(pedido) {
  if (pedido.chave) {
    const existente = await uma(RESUMO + "WHERE CHAVE_IDEMPOTENCIA = :chave", { chave: pedido.chave });
    if (existente) {
      return { aceita: false, corpo: respostaDe(existente) };
    }
  }
  const conexao = await oracle.conexao();
  const agora = new Date();
  let id;
  try {
    const seq = await conexao.execute("SELECT SQ_GATEWAY_REQUISICAO_HTTP.NEXTVAL AS ID FROM DUAL");
    id = Number(seq.rows[0].ID);
    await conexao.execute(INSERIR, {
      id: id,
      chave: pedido.chave,
      assincrono: pedido.assincrono ? 1 : 0,
      processo: pedido.processo,
      lote: pedido.lote,
      status: pedido.assincrono ? "PENDENTE" : "EM_EXECUCAO",
      instancia: pedido.assincrono ? null : config.instancia,
      heartbeat: pedido.assincrono ? null : agora,
      metodo: pedido.metodo,
      url: pedido.url,
      parametros: pedido.parametros.length ? JSON.stringify(pedido.parametros) : null,
      headers: pedido.headers.length ? JSON.stringify(pedido.headers) : null,
      tipoCorpo: pedido.tipoCorpo,
      contentType: pedido.contentType,
      corpo: pedido.corpo,
      tipoRetorno: pedido.tipoRetorno,
      timeout: pedido.timeoutSegundos,
      usarCertificado: pedido.usarCertificado ? 1 : 0,
      destino: pedido.certificadosDestino.length ? JSON.stringify(pedido.certificadosDestino) : null,
      criado: agora,
      iniciado: pedido.assincrono ? null : agora
    }, { autoCommit: true });
  } catch (erro) {
    await conexao.close();
    if (erro && erro.errorNum === 1 && pedido.chave) {
      const existente = await uma(RESUMO + "WHERE CHAVE_IDEMPOTENCIA = :chave", { chave: pedido.chave });
      if (existente) {
        return { aceita: false, corpo: respostaDe(existente) };
      }
    }
    throw erro;
  }
  await conexao.close();
  console.log("Requisicao HTTP id=" + id + " registrada assincrono=" + pedido.assincrono);
  if (pedido.assincrono) {
    return { aceita: true, corpo: await consultar(id) };
  }
  await executar(id);
  return { aceita: false, corpo: await consultar(id) };
}

async function executar(id) {
  const linha = await uma(EXECUCAO + "WHERE ID = :id", { id: id });
  if (!linha || linha.STATUS !== "EM_EXECUCAO") {
    return;
  }
  ativas.add(id);
  const chamada = {
    id: Number(linha.ID),
    metodo: linha.METODO,
    url: linha.URL,
    parametros: jsonLista(linha.PARAMETROS_JSON),
    headers: jsonLista(linha.HEADERS_JSON),
    corpo: linha.CORPO_REQUISICAO,
    contentType: linha.CONTENT_TYPE_REQUISICAO,
    timeoutSegundos: Number(linha.TIMEOUT_SEGUNDOS),
    usarCertificado: Number(linha.USAR_CERTIFICADO) === 1,
    certificadosDestino: jsonLista(linha.CERTIFICADOS_DESTINO)
  };
  try {
    const resultado = await cliente.executar(chamada);
    await concluir(id, resultado);
    console.log("Requisicao HTTP id=" + id + " concluida status=" + resultado.status
      + " http=" + resultado.httpStatus);
  } catch (erro) {
    console.error("Requisicao HTTP id=" + id, erro);
    await concluir(id, {
      status: "ERRO",
      httpStatus: null,
      corpo: null,
      contentType: null,
      charset: null,
      headers: [],
      mensagem: "falha interna | " + (erro.message || erro),
      certificadoArquivo: null
    });
  } finally {
    ativas.delete(id);
  }
}

async function concluir(id, resultado) {
  const texto = cliente.comoTexto(resultado.corpo, resultado.contentType);
  const conexao = await oracle.conexao();
  try {
    await conexao.execute(
      "UPDATE GATEWAY_REQUISICAO_HTTP SET STATUS = :status, HTTP_STATUS = :http, "
      + "HEADERS_RESPOSTA_JSON = :headers, CONTENT_TYPE_RESPOSTA = :tipo, CHARSET_RESPOSTA = :charset, "
      + "CORPO_RESPOSTA = :corpo, CORPO_RESPOSTA_TEXTO = :texto, TAMANHO_RESPOSTA = :tamanho, "
      + "MENSAGEM_ERRO = :mensagem, CERTIFICADO_ARQUIVO = :certificado, FINALIZADO_EM = :fim "
      + "WHERE ID = :id AND STATUS = 'EM_EXECUCAO'",
      {
        status: resultado.status,
        http: resultado.httpStatus,
        headers: resultado.headers && resultado.headers.length ? JSON.stringify(resultado.headers) : null,
        tipo: resultado.contentType,
        charset: resultado.charset,
        corpo: resultado.corpo,
        texto: texto,
        tamanho: resultado.corpo ? resultado.corpo.length : null,
        mensagem: resultado.mensagem,
        certificado: resultado.certificadoArquivo,
        fim: new Date(),
        id: id
      },
      { autoCommit: true }
    );
  } finally {
    await conexao.close();
  }
}

async function consumirUm() {
  const conexao = await oracle.conexao();
  let id = null;
  try {
    const encontrado = await conexao.execute(REIVINDICAR, [], { autoCommit: false });
    if (!encontrado.rows || !encontrado.rows[0]) {
      await conexao.rollback();
      return false;
    }
    id = Number(encontrado.rows[0].ID);
    const agora = new Date();
    const marcado = await conexao.execute(
      "UPDATE GATEWAY_REQUISICAO_HTTP SET STATUS = 'EM_EXECUCAO', INSTANCIA = :instancia, "
      + "INICIADO_EM = :inicio, HEARTBEAT_EM = :heartbeat WHERE ID = :id AND STATUS = 'PENDENTE'",
      { instancia: config.instancia, inicio: agora, heartbeat: agora, id: id },
      { autoCommit: false }
    );
    if (!marcado.rowsAffected) {
      await conexao.rollback();
      return false;
    }
    await conexao.commit();
  } catch (erro) {
    try { await conexao.rollback(); } catch (rollback) { /* a falha original segue */ }
    throw erro;
  } finally {
    await conexao.close();
  }
  await executar(id);
  return true;
}

async function pulsar() {
  if (ativas.size === 0) {
    return;
  }
  const conexao = await oracle.conexao();
  try {
    for (const id of ativas) {
      await conexao.execute(
        "UPDATE GATEWAY_REQUISICAO_HTTP SET HEARTBEAT_EM = :agora "
        + "WHERE ID = :id AND STATUS = 'EM_EXECUCAO' AND INSTANCIA = :instancia",
        { agora: new Date(), id: id, instancia: config.instancia },
        { autoCommit: true }
      );
    }
  } finally {
    await conexao.close();
  }
}

async function marcarInterrompidas() {
  const conexao = await oracle.conexao();
  try {
    const resultado = await conexao.execute(
      "UPDATE GATEWAY_REQUISICAO_HTTP SET STATUS = 'ERRO', "
      + "MENSAGEM_ERRO = 'execucao interrompida na instancia ' || NVL(INSTANCIA, 'desconhecida') "
      + "|| ' apos ' || TIMEOUT_SEGUNDOS || ' segundos sem conclusao', "
      + "FINALIZADO_EM = :agora WHERE STATUS = 'EM_EXECUCAO' AND INICIADO_EM IS NOT NULL "
      + "AND INICIADO_EM + NUMTODSINTERVAL(TIMEOUT_SEGUNDOS, 'SECOND') <= :agora",
      { agora: new Date() },
      { autoCommit: true }
    );
    return resultado.rowsAffected || 0;
  } finally {
    await conexao.close();
  }
}

function jsonLista(texto) {
  if (!texto) {
    return [];
  }
  try {
    const valor = JSON.parse(texto);
    return Array.isArray(valor) ? valor : [];
  } catch (erro) {
    return [];
  }
}

module.exports = { criar, consultar, baixar, consumirUm, pulsar, marcarInterrompidas };
