# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## Ce que MORPHEUS fournit

- ingestion et normalisation de spécifications ;
- snapshots versionnés et séparation `CURRENT / PROPOSED / HISTORICAL` ;
- recherche de requirements et requêtes métier ;
- traçabilité déterministe ;
- diagnostics qualité ;
- analyse de changements proposés ;
- CLI locale scriptable ;
- serveur MCP STDIO read-only ;
- API HTTP locale `/api/v1` ;
- intégrations optionnelles MINOS, NEXUS et JARVIS.

MORPHEUS ne nécessite aucun LLM pour son cœur fonctionnel.

## Écosystème

```text
MORPHEUS = specification / intent / lifecycle rules
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing
```

Chaque moteur reste autonome.

```text
Sources / workspaces
 -> providers
 -> normalisation MORPHEUS
 -> KnowledgeSnapshot / SpecificationVersion
 -> Memory | SQLite
 -> Query / Traceability / Quality / Change Analysis
 -> CLI | MCP | HTTP API
              |
              +-> MINOS optionnel via MCP STDIO
              +-> NEXUS optionnel via MCP STDIO
              +-> contrat read-only <- HTTP <- JARVIS
```

## Démarrage

La distribution portable embarque son runtime Java.

### Windows

```powershell
.\morpheus\morpheus.exe help
```

### Linux

```bash
./morpheus/bin/morpheus help
```

Parcours complet : **[Démarrage rapide](docs/user/QUICKSTART.md)**.

## Premier projet

```bash
morpheus projects add --workspace /path/to/project
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query "session"
morpheus changes list --project <projectId>
```

Mode JSON :

```bash
morpheus --json requirements find --project <projectId> --query "session"
```

## Surfaces

### CLI

```text
projects
sync / sync-status
requirements
changes / constraints / decisions / tasks
trace-requirement
change-context
analyze-change
quality
minos-status / external-references
nexus-status / augmented-context
change-orchestration
```

Référence : [docs/user/CLI.md](docs/user/CLI.md).

### MCP STDIO

```bash
morpheus mcp --stdio
```

Catalogue actuel : **20 tools read-only**.

Référence : [docs/developer/MCP.md](docs/developer/MCP.md).

### API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

```text
base = /api/v1
OpenAPI = 3.1.0
contract version = 1.3.0
```

Référence : [docs/developer/API.md](docs/developer/API.md).

## Intégrations optionnelles

### MINOS

Résout des références vers des symboles de code via MCP STDIO, sans dépendance `com.minos.*` et sans embarquer MINOS.

### NEXUS

Construit du contexte technique à partir d’une intention MORPHEUS. NEXUS reste propriétaire du ranking, de la fusion, de la compression et du budget.

### JARVIS

Consomme un contrat HTTP read-only :

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

MORPHEUS évalue les transitions mais ne les applique pas via ce contrat.

Guide : [docs/user/INTEGRATIONS.md](docs/user/INTEGRATIONS.md).

## Invariants importants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
optional engine absence != MORPHEUS failure
live external observation != snapshot mutation
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
```

## Fondation technique

```text
Java                 21
Build                Maven Wrapper
Persistent store     SQLite
DomainIdentity       UUIDv7
MCP SDK              Java MCP SDK 2.0.0
MCP transport        STDIO
HTTP server          JDK jdk.httpserver
Distribution         jpackage portable app-image
```

Modules Maven :

```text
morpheus-domain
morpheus-application
morpheus-provider-openspec
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
```

## Build développeur

```powershell
.\mvnw.cmd clean test
```

Packaging Windows :

```powershell
.\distribution\build-portable.ps1
```

Documentation développeur : [docs/developer/README.md](docs/developer/README.md).

## État du projet

```text
C0-M2  fondation                               ✅ VALIDÉ
M3     temporalité / lifecycle                 ✅ VALIDÉ / INTÉGRÉ — 147/147
M4     traçabilité                             ✅ VALIDÉ / INTÉGRÉ — 189/189
M5     requêtes / contexte compact             ✅ VALIDÉ / INTÉGRÉ — 227/227
M6     qualité / diagnostics                   ✅ VALIDÉ / INTÉGRÉ — 261/261
M7     sync incrémentale                       ✅ VALIDÉ / INTÉGRÉ — 282/282
M8     analyse changements                     ✅ VALIDÉ / INTÉGRÉ — 289/289
M9     CLI / distribution                      ✅ VALIDÉ / INTÉGRÉ — 298/298
M10    MCP STDIO                               ✅ VALIDÉ / INTÉGRÉ — 307/307
M11    API HTTP                                ✅ VALIDÉ / INTÉGRÉ — 314/314
M12    MINOS optionnel                         ✅ VALIDÉ / INTÉGRÉ — 331/331
M13    NEXUS optionnel                         ✅ VALIDÉ / INTÉGRÉ — 346/346
M14    JARVIS orchestration contract           ✅ VALIDÉ / INTÉGRÉ — 357/357
```

Gate M14 de référence :

```text
MORPHEUS        357/357 PASS
Architecture    160/160 PASS
Packaging       PASS
JARVIS          536 tests BUILD SUCCESS
Client MORPHEUS 6/6 PASS
```

## Documentation

**Point d’entrée : [docs/README.md](docs/README.md)**

```text
docs/user/       utilisation, quickstart, CLI, intégrations
docs/developer/  architecture, build/tests, API, MCP, intégrations
docs/reference/  index des contrats machine
docs/governance/ index roadmap / ADR / validations
docs/adr/        décisions d’architecture
docs/roadmap/    exécution historique des jalons
docs/openapi/    OpenAPI machine-readable
```

Roadmap : [docs/ROADMAP.md](docs/ROADMAP.md).  
Validation M14 : [docs/VALIDATION_M14.md](docs/VALIDATION_M14.md).
