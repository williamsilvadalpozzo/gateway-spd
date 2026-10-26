const fs = require("fs");
const path = require("path");

function preenchido(valor) {
  return valor != null && String(valor).trim() !== "";
}

function primeiro(...nomes) {
  for (let i = 0; i < nomes.length; i++) {
    const valor = process.env[nomes[i]];
    if (preenchido(valor)) {
      return String(valor).trim();
    }
  }
  return "";
}

function lerArquivo() {
  const caminho = preenchido(process.env.GATEWAY_CONFIG)
    ? path.resolve(process.env.GATEWAY_CONFIG)
    : path.join(__dirname, "..", "gateway.config.json");
  if (!fs.existsSync(caminho)) {
    return { caminho: caminho, existe: false, dados: {} };
  }
  return {
    caminho: caminho,
    existe: true,
    dados: JSON.parse(fs.readFileSync(caminho, "utf8"))
  };
}

function escolher(ambiente, doArquivo, padrao) {
  if (preenchido(ambiente)) {
    return String(ambiente).trim();
  }
  if (preenchido(doArquivo)) {
    return String(doArquivo).trim();
  }
  return padrao || "";
}

function jdbc(url) {
  const marca = "@//";
  const indice = url.indexOf(marca);
  if (indice < 0) {
    return null;
  }
  const resto = url.substring(indice + marca.length);
  const barra = resto.lastIndexOf("/");
  if (barra <= 0) {
    return null;
  }
  const hostPorta = resto.substring(0, barra);
  const servico = resto.substring(barra + 1);
  const doisPontos = hostPorta.lastIndexOf(":");
  if (doisPontos <= 0) {
    return { host: hostPorta, porta: "1521", servico: servico };
  }
  return {
    host: hostPorta.substring(0, doisPontos),
    porta: hostPorta.substring(doisPontos + 1),
    servico: servico
  };
}

function oracle(arquivo) {
  const bloco = arquivo.oracle || {};
  const url = escolher(
    primeiro("GATEWAY_FLOW_ORACLE_JDBC_URL", "DPZ_XML_ORACLE_JDBC_URL"),
    bloco.jdbcUrl
  );
  const lido = url ? jdbc(url) : null;
  const host = (lido && lido.host) || escolher(
    primeiro("GATEWAY_FLOW_ORACLE_HOST", "DPZ_XML_ORACLE_HOST"),
    bloco.host,
    "orcldevtst.c11muiyjxfz9.sa-east-1.rds.amazonaws.com"
  );
  const porta = (lido && lido.porta) || escolher(
    primeiro("GATEWAY_FLOW_ORACLE_PORT", "DPZ_XML_ORACLE_PORT"),
    bloco.porta,
    "1521"
  );
  const servico = (lido && lido.servico) || escolher(
    primeiro("GATEWAY_FLOW_ORACLE_SERVICE_NAME", "DPZ_XML_ORACLE_SERVICE_NAME"),
    bloco.servico,
    "OUTSYDEV"
  );
  return {
    usuario: escolher(
      primeiro("GATEWAY_FLOW_ORACLE_USERNAME", "DPZ_XML_ORACLE_USERNAME"),
      bloco.usuario,
      "OUTSYSTEM"
    ),
    senha: escolher(primeiro("GATEWAY_FLOW_ORACLE_PASSWORD", "DPZ_XML_ORACLE_PASSWORD"), bloco.senha),
    connectString: host + ":" + porta + "/" + servico
  };
}

function versao() {
  try {
    const pacote = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "package.json"), "utf8"));
    return { nome: pacote.name || "gateway-spd", versao: pacote.version || null };
  } catch (erro) {
    return { nome: "gateway-spd", versao: null };
  }
}

const pacote = versao();
const arquivo = lerArquivo();
const certificadosArquivo = arquivo.dados.certificados || {};

module.exports = {
  porta: Number(escolher(process.env.PORT || process.env.SERVER_PORT, arquivo.dados.porta, "8020")),
  contexto: "/gateway-spd",
  nome: pacote.nome,
  versao: pacote.versao,
  configPath: arquivo.caminho,
  configExiste: arquivo.existe,
  instancia: escolher(primeiro("GATEWAY_INSTANCIA"), arquivo.dados.instancia, "1"),
  certificados: {
    path: escolher(primeiro("GATEWAY_CERT_PATH"), certificadosArquivo.path),
    senha: escolher(primeiro("GATEWAY_CERT_SENHA"), certificadosArquivo.senha)
  },
  retencaoMs: 5 * 60 * 1000,
  prazoPadraoMs: 4 * 60 * 60 * 1000,
  prazoMaximoMs: 24 * 60 * 60 * 1000,
  folgaPrazoMs: 30 * 1000,
  filaMs: 1000,
  heartbeatMs: 15000,
  varreduraMs: 300000,
  limpezaMs: 15000,
  oracle: oracle(arquivo.dados)
};
