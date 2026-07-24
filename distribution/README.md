# MORPHEUS — Distribution locale M9 à M12

Stratégie : **native-first**, archive portable autonome comme artefact principal.

État :

```text
M9   Windows + Linux validés
M10  MCP STDIO embarqué validé
M11  API HTTP + jdk.httpserver embarqués validés
M12  client/adaptateur MINOS optionnel implémenté — gate pending
```

## Artefacts

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java ; aucun JDK séparé n'est requis côté utilisateur final.

## Contenu M12

L'uber-JAR contient :

```text
CLI MORPHEUS
MCP server MORPHEUS
MCP client SDK
HTTP API
morpheus-integration-minos
Jackson
SQLite JDBC
```

Il **ne contient pas MINOS**.

Le build échoue si une entrée :

```text
com/minos/*
```

est détectée dans le shaded JAR.

Le JAR MINOS reste une dépendance runtime externe et optionnelle, configurée via `MORPHEUS_MINOS_JAR`.

## Preuve de contenu

M12 contrôle notamment :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/client/McpClient.class
io/modelcontextprotocol/client/transport/StdioClientTransport.class
com/morpheus/api/MorpheusHttpServer.class
com/morpheus/integration/minos/MinosMcpExternalReferenceResolver.class
com/morpheus/integration/minos/MinosMcpCodeGateway.class
com/morpheus/integration/minos/MinosIntegrationRuntime.class
tools/jackson/databind/json/JsonMapper.class
```

Attendu :

```text
MCP/API/MINOS adapter packaging proof: PASS
```

## Windows

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Workdir M12 :

```text
dist/.m12-windows
```

Le script :

1. construit l'uber-JAR ;
2. vérifie MCP/API/client MINOS ;
3. rejette toute classe d'implémentation MINOS ;
4. crée l'app-image via `jpackage` ;
5. embarque `jdk.httpserver` ;
6. teste `--version` ;
7. teste `--json version` ;
8. teste `--json minos-status` sans configuration MINOS ;
9. démarre l'API packagée et vérifie `/api/v1/health` ;
10. produit le ZIP avec retry/backoff.

Smoke standalone M12 attendu :

```json
{"configured":false,"details":{"javaCommand":"java","timeoutSeconds":"20"},"message":"MINOS integration is not configured","state":"DISABLED","system":"MINOS"}
```

Le message exact peut évoluer de manière compatible ; le gate exige au minimum `"state":"DISABLED"`.

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

Workdir : `dist/.m12-windows/image/morpheus`.

WiX reste nécessaire uniquement pour produire EXE/MSI ; le ZIP portable reste officiel.

## Linux

Le packaging Linux doit être exécuté depuis Linux/WSL avec filesystem Linux pour constituer une preuve Linux réelle.

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./mvnw clean test
./distribution/build-portable.sh
```

Workdir M12 :

```text
dist/.m12-linux
```

Le script vérifie également :

```text
MINOS status DISABLED sans configuration
jdk.httpserver présent dans le runtime packagé
aucune classe com/minos/*
```

## Configuration MINOS runtime

L'archive MORPHEUS ne nécessite pas MINOS.

Pour activer l'intégration :

```text
MORPHEUS_MINOS_JAR=<path-to-minos-uber-jar>
MORPHEUS_MINOS_JAVA=<optional-java-command>
MORPHEUS_MINOS_HOME=<optional-minos-home>
MORPHEUS_MINOS_TIMEOUT_SECONDS=<1..120>
```

L'adapter lance MINOS à la demande via MCP STDIO ; aucun process MINOS n'est démarré lors d'un simple `--version`, d'un bootstrap CLI ou d'un health API.

Lorsque `MORPHEUS_MINOS_HOME` est défini, il est transmis au process MINOS comme `-Dminos.home=<path>` avant `-cp`.

## Smoke de compatibilité avec le vrai MINOS

Le gate autonome prouve que MORPHEUS reste valide **sans** MINOS. Pour prouver en plus le contrat inter-dépôts réel :

```powershell
.\distribution\test-minos-compatibility.ps1 `
  -MinosJar <path-to-minos-code-intelligence\target\*-all.jar> `
  -MinosJava <java-24-or-newer>
```

Le script utilise le launcher MORPHEUS M12 packagé par défaut :

```text
dist/.m12-windows/image/morpheus/morpheus.exe
```

Il configure temporairement `MORPHEUS_MINOS_*`, démarre réellement `com.minos.mcp.MinosMcpServer` via l'adapter MCP STDIO et exige :

```text
system = MINOS
state  = AVAILABLE
```

Résultat attendu :

```text
Real MINOS MCP compatibility smoke: PASS
```

Ce smoke ne copie pas MINOS dans MORPHEUS et ne crée aucune dépendance Maven entre les dépôts.

## Layout runtime MORPHEUS

Options :

```text
--data-dir PATH
--config-dir PATH
--db PATH
```

Variables :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Windows :

```text
data   = %LOCALAPPDATA%\Morpheus
config = %APPDATA%\Morpheus
db     = <data>\morpheus.db
logs   = <data>\logs
```

Linux :

```text
data   = $XDG_DATA_HOME/morpheus ou ~/.local/share/morpheus
config = $XDG_CONFIG_HOME/morpheus ou ~/.config/morpheus
db     = <data>/morpheus.db
logs   = <data>/logs
```

L'installation binaire reste séparée des données SQLite/config utilisateur.

## Fins de ligne

`.gitattributes` impose :

```text
mvnw     LF
*.sh     LF
mvnw.cmd CRLF
*.ps1    CRLF
```

## Gates historiques

```text
M9  Windows + Linux 298/298 PASS
M10 Windows         307/307 PASS + MCP packaging
M11 Windows         314/314 PASS + packaged API health
M12 attendu         331 tests + MINOS optional packaging proof
```

## Gate M12

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

M12 ne sera validé qu'après preuve de ces deux commandes, complétée idéalement par le smoke de compatibilité contre le vrai JAR MINOS.

Références :

- [`../docs/VALIDATION_M11.md`](../docs/VALIDATION_M11.md)
- [`../docs/MINOS.md`](../docs/MINOS.md)
- [`../docs/roadmap/M12_EXECUTION.md`](../docs/roadmap/M12_EXECUTION.md)
