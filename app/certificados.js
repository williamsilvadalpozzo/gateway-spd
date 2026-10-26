const fs = require("fs");
const path = require("path");
const forge = require("node-forge");
const config = require("./config");

function pasta() {
  return config.certificados.path || "";
}

function listar() {
  const diretorio = pasta();
  if (!diretorio || !fs.existsSync(diretorio) || !fs.statSync(diretorio).isDirectory()) {
    return [];
  }
  return fs.readdirSync(diretorio)
    .filter(function (nome) {
      const lower = nome.toLowerCase();
      return lower.endsWith(".pfx") || lower.endsWith(".p12");
    })
    .sort(function (a, b) {
      return a.toLowerCase().localeCompare(b.toLowerCase());
    })
    .map(function (nome) {
      return path.join(diretorio, nome);
    });
}

function pemDaChave(chave) {
  if (chave.n && chave.e && chave.d) {
    return forge.pki.privateKeyToPem(chave);
  }
  return forge.pki.privateKeyToPem(chave);
}

function abrirPfx(arquivo) {
  const bytes = fs.readFileSync(arquivo);
  const der = forge.util.createBuffer(bytes.toString("binary"));
  let p12;
  try {
    p12 = forge.pkcs12.pkcs12FromAsn1(forge.asn1.fromDer(der), config.certificados.senha);
  } catch (erro) {
    const falha = new Error("falha ao abrir o certificado " + path.basename(arquivo));
    falha.cause = erro;
    throw falha;
  }
  const chaves = p12.getBags({ bagType: forge.pki.oids.pkcs8ShroudedKeyBag });
  const bolsasChave = chaves[forge.pki.oids.pkcs8ShroudedKeyBag] || [];
  const chave = bolsasChave[0] && bolsasChave[0].key;
  if (!chave) {
    throw new Error("pfx sem chave privada " + path.basename(arquivo));
  }
  const certificados = p12.getBags({ bagType: forge.pki.oids.certBag });
  const bolsas = certificados[forge.pki.oids.certBag] || [];
  const lista = bolsas.map(function (bolsa) { return bolsa.cert; }).filter(Boolean);
  lista.sort(function (a, b) {
    const aCasa = a.publicKey && chave.n && a.publicKey.n && a.publicKey.n.compareTo(chave.n) === 0;
    const bCasa = b.publicKey && chave.n && b.publicKey.n && b.publicKey.n.compareTo(chave.n) === 0;
    return aCasa === bCasa ? 0 : aCasa ? -1 : 1;
  });
  return {
    key: pemDaChave(chave),
    cert: lista.map(function (cert) { return forge.pki.certificateToPem(cert); }).join("")
  };
}

function descrever() {
  const diretorio = pasta();
  if (!diretorio) {
    return "Pasta de certificados nao configurada";
  }
  if (!fs.existsSync(diretorio) || !fs.statSync(diretorio).isDirectory()) {
    return "Pasta de certificados nao encontrada path=" + diretorio;
  }
  const arquivos = listar();
  if (arquivos.length === 0) {
    return "Nenhum certificado .pfx ou .p12 na pasta path=" + diretorio;
  }
  return "Certificados encontrados path=" + diretorio + " arquivos="
    + arquivos.map(function (arquivo) { return path.basename(arquivo); }).join(", ");
}

module.exports = { pasta, listar, abrirPfx, descrever };
