const oracledb = require("oracledb");
const config = require("./config");

oracledb.outFormat = oracledb.OUT_FORMAT_OBJECT;
oracledb.fetchAsString = [oracledb.CLOB];
oracledb.fetchAsBuffer = [oracledb.BLOB];

let pool = null;
let avisoPool = null;

async function obterPool() {
  if (pool) {
    return pool;
  }
  if (!config.oracle.senha) {
    throw new Error("senha Oracle nao configurada");
  }
  pool = await oracledb.createPool({
    user: config.oracle.usuario,
    password: config.oracle.senha,
    connectString: config.oracle.connectString,
    poolMin: 1,
    poolMax: 20,
    poolIncrement: 1
  });
  avisoPool = null;
  return pool;
}

async function conexao() {
  const atual = await obterPool();
  return atual.getConnection();
}

function registrarFalha(erro) {
  const texto = erro && erro.message ? erro.message : String(erro);
  if (texto !== avisoPool) {
    console.error("Falha na conexao Oracle", texto);
    avisoPool = texto;
  }
}

async function fechar() {
  if (pool) {
    const atual = pool;
    pool = null;
    await atual.close(0);
  }
}

module.exports = { conexao, registrarFalha, fechar };
