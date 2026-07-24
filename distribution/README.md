# MORPHEUS — Distribution locale M9 à M14

Stratégie : **native-first**, archive portable autonome comme artefact principal.

```text
M9   Windows + Linux validés
M10  MCP STDIO embarqué validé
M11  API HTTP + jdk.httpserver validés
M12  adapter MINOS optionnel validé
M13  adapter NEXUS optionnel validé
M14  contrat d'orchestration JARVIS implémenté — gate pending
```

## Artefacts

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java. Aucun JDK séparé n'est requis pour MORPHEUS lui-même.

## Contenu M14

L'uber-JAR embarque :

```text
CLI MORPHEUS
MCP server + MCP client SDK
HTTP API
M14 orchestration application services
M14 CLI/MCP/API adapters
morpheus-integration-minos
morpheus-integration-nexus
Jackson
SQLite JDBC
```

Il n'embarque **ni MINOS, ni NEXUS, ni JARVIS**.

Le build échoue si le shaded JAR contient :

```text
com/minos/*
com/nexus/*
com/jarvis/*
```

Classes M14 exigées notamment :

```text
com/morpheus/mcp/MorpheusJarvisOrchestrationMcpTools.class
com/morpheus/api/MorpheusJarvisOrchestrationApiService.class
com/morpheus/cli/MorpheusJarvisOrchestrationCli.class
com/morpheus/application/orchestration/ChangeOrchestrationStateService.class
com/morpheus/application/orchestration/ChangeTransitionEvaluationService.class
```

## Windows M14

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Workdir : `dist/.m14-windows`.

Preuves attendues :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
jpackage app-image: PASS
MINOS status -> DISABLED sans configuration
NEXUS status -> DISABLED sans configuration
change-orchestration présent dans le help packagé
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

Il réutilise `dist/.m14-windows/image/morpheus`.

## Linux M14

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./mvnw clean test
./distribution/build-portable.sh
```

Workdir : `dist/.m14-linux`.

Le script vérifie :

```text
classes M14 présentes
MINOS status DISABLED
NEXUS status DISABLED
change-orchestration visible
jdk.httpserver présent
aucune classe com/minos/*
aucune classe com/nexus/*
aucune classe com/jarvis/*
```

## MINOS / NEXUS

Les configurations M12/M13 restent inchangées :

```text
MORPHEUS_MINOS_JAR / MORPHEUS_MINOS_JAVA / MORPHEUS_MINOS_HOME / MORPHEUS_MINOS_TIMEOUT_SECONDS
MORPHEUS_NEXUS_JAR / MORPHEUS_NEXUS_JAVA / MORPHEUS_NEXUS_HOME / MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Les smokes cross-repo existants restent disponibles :

```text
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

## JARVIS M14

JARVIS n'est pas embarqué. La preuve cross-repo utilise HTTP local :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Dépôt JARVIS : issue #92, PR #93 draft.

## Layout runtime MORPHEUS

```text
--data-dir PATH
--config-dir PATH
--db PATH
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Windows : `%LOCALAPPDATA%\Morpheus` / `%APPDATA%\Morpheus`.  
Linux : XDG data/config standards.

## Gates historiques et projection

```text
M9  298/298 Windows + Linux
M10 307/307 Windows + MCP packaging
M11 314/314 Windows + packaged API health
M12 331/331 Windows + MINOS optional packaging
M13 346/346 Windows + MINOS/NEXUS optional packaging
M14 projection 357 tests | Architecture 160 | M14 orchestration packaging pending
```

M14 reste non validé tant que :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

n'ont pas produit une preuve verte.

Références :

- [`../docs/JARVIS.md`](../docs/JARVIS.md)
- [`../docs/roadmap/M14_EXECUTION.md`](../docs/roadmap/M14_EXECUTION.md)
- [`../docs/API.md`](../docs/API.md)
- [`../docs/MCP.md`](../docs/MCP.md)
