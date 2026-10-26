const http = require("http");

function post(lote, usarCertificado, comPem) {
  const pedido = {
    metodo: "GET",
    url: "https://adn.nfse.gov.br/contribuintes/DFe/1",
    parametros: [{ nome: "cnpjConsulta", valor: "80213705000126" }],
    headers: [{ nome: "Accept", valor: "application/json" }],
    tipoCorpo: "NENHUM",
    tipoRetorno: "JSON",
    timeoutSegundos: 30,
    assincrono: false,
    processo: "NFSE_ADN",
    lote: lote,
    usarCertificado: usarCertificado,
    certificadosDestino: comPem ? ["-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"] : []
  };
  const corpo = Buffer.from(JSON.stringify(pedido));
  return new Promise(function (resolve) {
    const inicio = Date.now();
    const req = http.request({
      host: "10.20.1.61",
      port: 8080,
      path: "/gateway-spd/requisicoes-http",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": corpo.length
      },
      timeout: 45000
    }, function (res) {
      const partes = [];
      res.on("data", function (p) { partes.push(p); });
      res.on("end", function () {
        resolve(lote + " http=" + res.statusCode + " ms=" + (Date.now() - inicio)
            + " bytes=" + corpo.length + " " + Buffer.concat(partes).toString("utf8").slice(0, 400));
      });
    });
    req.on("error", function (e) { resolve(lote + " erro " + e.message); });
    req.on("timeout", function () { req.destroy(); resolve(lote + " timeout cliente"); });
    req.write(corpo);
    req.end();
  });
}

(async function () {
  console.log(await post("teste-node-sem-pem", true, false));
  console.log(await post("teste-node-pem-invalido", true, true));
})();
