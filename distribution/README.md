# MORPHEUS — Distribution locale M9 à M13

Stratégie : **native-first**, archive portable autonome comme artefact principal.

```text
M9   Windows + Linux validés
M10  MCP STDIO embarqué validé
M11  API HTTP + jdk.httpserver validés
M12  adapter MINOS optionnel validé
M13  adapter NEXUS optionnel validé
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

Il n'embarque **ni MINOS ni NEXUS**. Le build échoue si le shaded JAR contient `com/minos/*` ou `com/nexus/*`.

## Windows — validation M13

Commande :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Workdir : `dist/.m13-windows`.

Preuves obtenues :

```text
Maven package: BUILD SUCCESS
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
jpackage app-image: PASS
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
MINOS status -> DISABLED sans configuration
NEXUS status -> DISABLED sans configuration
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive produite :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
33,654,379 bytes
```

## Linux

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./mvnw clean test
./distribution/build-portable.sh
```

Workdir : `dist/.m13-linux`.

Le script vérifie également :

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

Smoke réel complémentaire :

```powershell
.\distribution\test-minos-compatibility.ps1 `
  -MinosJar <minos-code-intelligence\target\*-all.jar> `
  -MinosJava <java-24-or-newer>
```

## Configuration NEXUS

```text
MORPHEUS_NEXUS_JAR=<nexus-mcp-java-*-runner.jar>
MORPHEUS_NEXUS_JAVA=<java-21-or-newer>
MORPHEUS_NEXUS_HOME=<optional>
MORPHEUS_NEXUS_TIMEOUT_SECONDS=<1..120>
```

Smoke réel complémentaire :

```powershell
.\distribution\test-nexus-compatibility.ps1 `
  -NexusRunnerJar N:\workspace-dev\nexus-context-engine\adapters\mcp-java\target\nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar `
  -NexusJava <java-21-or-newer> `
  -NexusHome <optional>
```

Ces smokes cross-repo ne remplacent pas le gate MORPHEUS et ne faisaient pas partie du gate M13 officiel.

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
M13 346/346 Windows + MINOS/NEXUS optional packaging
```

M13 architecture : **154/154 PASS**.

Références :

- [`../docs/VALIDATION_M13.md`](../docs/VALIDATION_M13.md)
- [`../docs/MINOS.md`](../docs/MINOS.md)
- [`../docs/NEXUS.md`](../docs/NEXUS.md)
- [`../docs/roadmap/M13_EXECUTION.md`](../docs/roadmap/M13_EXECUTION.md)
