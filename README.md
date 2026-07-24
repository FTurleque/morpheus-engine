# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet doit devenir : exigences, changements, contraintes, scénarios, décisions de conception et tâches associées.

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

## Écosystème

```text
                           JARVIS
                        Orchestration
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
      MORPHEUS              MINOS              NEXUS
  Spec & Intent             Code              Context
   Intelligence          Intelligence        Intelligence
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                     ALFRED / BRAINIAC
```

- MORPHEUS possède l'intention/specification ;
- MINOS possède l'intelligence de code ;
- NEXUS possède la sélection/ranking/compression du contexte ;
- JARVIS orchestre.

Chaque brique reste autonome.

## Architecture

```text
Sources / workspaces
        ↓
Specification providers
        ↓
Normalisation MORPHEUS
        ↓
KnowledgeSnapshot / SpecificationVersion
        ↓
Persistence snapshot-scoped (Memory / SQLite)
        ↓
Query / Search / Traceability / Quality / Change Analysis
        ↓
  ┌─────┼─────┐
  ↓     ↓     ↓
 CLI   MCP   HTTP API
              \
               \ optional ports
                ↓
        morpheus-integration-minos
                ↓ MCP STDIO
              MINOS
```

**OpenSpec est le provider de référence initial, pas le domaine de MORPHEUS.**

## Invariants structurants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
lifecycle absent != inferred lifecycle
unresolved reference != invented fact
MINOS remains optional
live external resolution != snapshot mutation
```

## Fondation technique

```text
Language             Java
Compatibility        Java 21 source / bytecode
Compiler             --release 21
Build                Maven 3.9.16 + Wrapper
Persistent store     SQLite JDBC 3.53.1.0
Memory store         contract reference backend
DomainIdentity       UUIDv7
MCP SDK              Java MCP SDK 2.0.0
MCP transport        STDIO
HTTP server          JDK jdk.httpserver
Distribution         jpackage portable app-image
LLM                  none required
```

## État du projet

```text
C0  Cadrage fonctionnel et architectural       ✅ VALIDÉ
M0  Faisabilité technique                      ✅ VALIDÉ
M1  Discovery / providers / store              ✅ VALIDÉ
M2  Ingestion et modèle normalisé              ✅ VALIDÉ — 94/94
M3  Temporalité / lifecycle / snapshots        ✅ VALIDÉ / INTÉGRÉ — 147/147
M4  Traçabilité typée                          ✅ VALIDÉ / INTÉGRÉ — 189/189
M5  Requêtes et contexte compact               ✅ VALIDÉ / INTÉGRÉ — 227/227
M6  Qualité / couverture / diagnostics         ✅ VALIDÉ / INTÉGRÉ — 261/261
M7  Synchronisation incrémentale / fraîcheur   ✅ VALIDÉ / INTÉGRÉ — 282/282
M8  Analyse des changements                    ✅ VALIDÉ / INTÉGRÉ — 289/289
M9  CLI / distribution locale                  ✅ VALIDÉ / INTÉGRÉ — 298/298 Windows + Linux
M10 MCP STDIO natif                            ✅ VALIDÉ / INTÉGRÉ — 307/307
M11 API HTTP headless                          ✅ VALIDÉ / INTÉGRÉ — 314/314
M12 MINOS optionnel                            🚧 FONCTIONNELLEMENT COMPLET — gate pending
```

Dernier jalon officiellement validé et intégré : **M11**.

M11 merge :

```text
e30ed4095700b445fedc4517c22ff447c22238f4
```

M11 gate :

```text
Domain                  21/21 PASS
Application             82/82 PASS
OpenSpec                26/26 PASS
Synthetic                7/7 PASS
SQLite                   7/7 PASS
MCP                      5/5 PASS
API                      4/4 PASS
CLI                     12/12 PASS
Architecture           150/150 PASS
TOTAL                  314/314 PASS
```

## CLI

Options globales :

```text
--json
--data-dir PATH
--config-dir PATH
--db PATH
```

Commandes principales :

```text
help
version
paths
projects list
projects add --workspace PATH
sync --project ID [--revision REV]
sync-status --project ID
requirements find --project ID [--query TEXT]
changes list --project ID
changes get --project ID --change ID
constraints list --project ID --change ID
decisions list --project ID --change ID
tasks list --project ID --change ID
trace-requirement --project ID --requirement ID
change-context --project ID --change ID
analyze-change --project ID --change ID
quality --project ID
```

M12 ajoute :

```text
minos-status
external-references list --project ID --owner ID
external-references resolve --project ID --reference ID
```

## MCP

Lancement :

```text
morpheus mcp --stdio
```

M10 : 14 tools read-only. M12 ajoute sans modifier les contrats historiques :

```text
list_external_references
resolve_external_reference
```

Soit **16 tools** sur le serveur M12.

Documentation : [`docs/MCP.md`](docs/MCP.md).

## API HTTP

Lancement :

```text
morpheus api --host 127.0.0.1 --port 8765
```

Base : `/api/v1`.

M12 ajoute :

```text
GET /api/v1/integrations/minos/status
GET /api/v1/projects/{projectId}/external-references?ownerId=...
GET /api/v1/projects/{projectId}/external-references/{referenceId}/resolution
```

Documentation : [`docs/API.md`](docs/API.md).

## MINOS optionnel — M12

Architecture :

```text
MORPHEUS Java 21
  -> MCP client STDIO
  -> process MINOS Java 24
```

Aucune dépendance `com.minos.*` dans MORPHEUS.

Coordonnée :

```text
system       = MINOS
resourceType = SYMBOL
project      = projet MINOS
externalId   = symbolKey exact
revision     = activeSnapshotId attendu, optionnel
```

Configuration :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Sans configuration, `minos-status=DISABLED` et MORPHEUS reste totalement fonctionnel.

La résolution live retourne `stored`, `observed`, `persisted=false` et ne réécrit jamais le snapshot publié.

Documentation : [`docs/MINOS.md`](docs/MINOS.md).

## Distribution

Artefacts :

```text
morpheus-cli-<version>-all.jar
morpheus-<version>-windows-x64.zip
morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent leur runtime Java, MCP et API. M12 embarque le **client/adaptateur** MINOS, jamais MINOS lui-même.

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
distribution/build-windows-installer.ps1
```

## Quick start

```text
morpheus projects add --workspace <workspace-openspec>
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query session
morpheus changes list --project <projectId>
morpheus quality --project <projectId>
```

MCP client :

```json
{"command":"morpheus","args":["--db","/path/to/morpheus.db","mcp","--stdio"]}
```

## M12 — gate attendu

Implémentation actuelle :

```text
resolution taxonomy enrichie
live non-mutating resolution Memory/SQLite
morpheus-integration-minos
real MINOS MCP STDIO fixture
exact symbolKey + revision
CLI M12
MCP M12
HTTP M12
packaging excludes com/minos/*
standalone MINOS-disabled smoke
```

Projection avant preuve : **331 tests**.

Gate officiel :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

M12 ne sera marqué VALIDÉ qu'après ce gate.

## Références

- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`docs/VALIDATION_M11.md`](docs/VALIDATION_M11.md)
- [`docs/roadmap/M11_EXECUTION.md`](docs/roadmap/M11_EXECUTION.md)
- [`docs/roadmap/M12_EXECUTION.md`](docs/roadmap/M12_EXECUTION.md)
- [`docs/MCP.md`](docs/MCP.md)
- [`docs/API.md`](docs/API.md)
- [`docs/MINOS.md`](docs/MINOS.md)
- [`distribution/README.md`](distribution/README.md)
