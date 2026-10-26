const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const raiz = path.resolve(__dirname, "..");
const pacote = require(path.join(raiz, "package.json"));
const dist = path.join(raiz, "dist");
const nomeArtefato = `${pacote.name}-${pacote.version}-linux-x64`;
const nomeDiretorio = pacote.name;
const diretorio = path.join(dist, nomeDiretorio);
const artefato = path.join(dist, `${nomeArtefato}.tar.gz`);

function copiar(origem, destino) {
  fs.cpSync(path.join(raiz, origem), path.join(diretorio, destino || origem), {
    recursive: true
  });
}

function executar(comando, argumentos, opcoes) {
  const resultado = childProcess.spawnSync(comando, argumentos, {
    cwd: raiz,
    stdio: "inherit",
    shell: false,
    ...opcoes
  });
  if (resultado.error) {
    throw resultado.error;
  }
  if (resultado.status !== 0) {
    throw new Error(`${comando} terminou com codigo ${resultado.status}`);
  }
}

fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(diretorio, { recursive: true });

copiar("app");
copiar("deploy");
copiar("sql");
copiar("package.json");
copiar("package-lock.json");
copiar("README.md");
copiar("release-notes.json");
copiar("gateway.config.example.json");

const npm = process.platform === "win32" ? "npm.cmd" : "npm";
executar(npm, [
  "ci",
  "--omit=dev",
  "--os=linux",
  "--cpu=x64",
  "--ignore-scripts"
], { cwd: diretorio });

executar("tar", [
  "-czf",
  artefato,
  "-C",
  dist,
  nomeDiretorio
]);

fs.rmSync(diretorio, { recursive: true, force: true });
console.log(`Artefato criado: ${artefato}`);
