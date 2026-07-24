# MORPHEUS — Distribution locale M9 à M13

Stratégie : **native-first**, archive portable autonome comme artefact principal.

```text
M9   Windows + Linux validés
M10  MCP STDIO embarqué validé
M11  API HTTP + jdk.httpserver validés
M12  adapter MINOS optionnel validé
M13  adapter NEXUS optionnel implémenté — gate pending
```

## Artefacts

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java. Aucun JDK séparé n'est requis pour MORPHEUS lui-même.

## Contenu M13

L'uber-JAR embarque :

```text
CLI MORPHEUS
MCP server + MCP client SDK
HTTP API
morpheus-integration-minos
morpheus-integration-nexus
Jackson
SQLite JDBC
```

Il n'embarque **ni MINOS ni NEXUS**.

Le build échoue si le shaded JAR contient :

```text
com/minos/*
com/nexus/*
```

## Preuve de contenu M13

Classes exigées notamment :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/client/McpClient.class
io/modelcontextprotocol/client/transport/StdioClientTransport.class
com/morpheus/api/MorpheusHttpServer.class
com/morpheus/integration/minos/MinosIntegrationRuntime.class
com/morpheus/integration/nexus/NexusMcpContextGateway.class
com/morpheus/integration/nexus/NexusMcpTechnicalContextProvider.class
com/morpheus/integration/nexus/NexusIntegrationRuntime.class
tools/jackson/databind/json/JsonMapper.class
```

Attendu :

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
```

## Windows

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Workdir :

```text
dist/.m13-windows
```

Le script :

1. construit l'uber-JAR ;
2. prouve la présence des adapters MCP/API/MINOS/NEXUS ;
3. rejette `com/minos/*` et `com/nexus/*` ;
4. crée l'app-image via `jpackage` ;
5. embarque `jdk.httpserver` ;
6. teste `--version` et `--json version` ;
7. exige `minos-status=DISABLED` sans configuration ;
8. exige `nexus-status=DISABLED` sans configuration ;
9. vérifie `/api/v1/health` sur le launcher packagé ;
10. crée le ZIP avec retry/backoff.

Attendu :

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

Workdir : `dist/.m13-windows/image/morpheus`.

## Linux

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./mvnw clean test
./distribution/build-portable.sh
```

Workdir : `dist/.m13-linux`.

Le script Linux vérifie également :

```text
MINOS status DISABLED
NEXUS status DISABLED
jdk.httpserver présent
aucune classe com/minos/*
aucune classe com/nexus/*
```

## Configuration MINOS

```text
MORPHEUS_MINOS_JAR=<minos-*-all.jar>
MORPHEUS_MINOS_JAVA=<java-24-or-newer>
MORPHEUS_MINOS_HOME=<optional>
MORPHEUS_MINOS_TIMEOUT_SECONDS=<1..120>
```

MORPHEUS lance MINOS à la demande par MCP STDIO.

Smoke réel :

```powershell
.\distribution\test-minos-compatibility.ps1 `
  -MinosJar <minos-code-intelligence\target\*-all.jar> `
  -MinosJava <java-24-or-newer>
```

Attendu : `Real MINOS MCP compatibility smoke: PASS`.

## Configuration NEXUS

```text
MORPHEUS_NEXUS_JAR=<nexus-mcp-java-*-runner.jar>
MORPHEUS_NEXUS_JAVA=<java-21-or-newer>
MORPHEUS_NEXUS_HOME=<optional>
MORPHEUS_NEXUS_TIMEOUT_SECONDS=<1..120>
```

MORPHEUS lance le runner NEXUS uniquement pour un status live ou une construction de contexte.

Smoke réel :

```powershell
.\distribution\test-nexus-compatibility.ps1 `
  -NexusRunnerJar N:\workspace-dev\nexus-context-engine\adapters\mcp-java\target\nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar `
  -NexusJava <java-21-or-newer> `
  -NexusHome <optional>
```

Attendu : `Real NEXUS MCP compatibility smoke: PASS`.

## Layout runtime MORPHEUS

```text
--data-dir PATH
--config-dir PATH
--db PATH

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

## Gates historiques

```text
M9  298/298 Windows + Linux
M10 307/307 Windows + MCP packaging
M11 314/314 Windows + packaged API health
M12 331/331 Windows + MINOS optional packaging
M13 projection 346 tests + MINOS/NEXUS optional packaging
```

## Gate M13

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

M13 reste non validé jusqu'à preuve de ces deux commandes.

Références :

- [`../docs/VALIDATION_M12.md`](../docs/VALIDATION_M12.md)
- [`../docs/MINOS.md`](../docs/MINOS.md)
- [`../docs/NEXUS.md`](../docs/NEXUS.md)
- [`../docs/roadmap/M13_EXECUTION.md`](../docs/roadmap/M13_EXECUTION.md)
