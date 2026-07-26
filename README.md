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
- critères d’acceptation, vérification et evidence explicites ;
- sémantique explicite des contraintes et décisions lifecycle explicables ;
- CLI locale scriptable ;
- serveur MCP STDIO avec **20 tools read-only + 1 tool write M17 explicite** ;
- API HTTP locale `/api/v1` ;
- première mutation lifecycle contrôlée par capability, confirmation, CAS, idempotency et audit ;
- intégrations optionnelles MINOS, NEXUS et JARVIS.

MORPHEUS ne nécessite aucun LLM pour son cœur fonctionnel.

## Écosystème

```text
MORPHEUS = specification / intent / lifecycle rules / controlled state invariants
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing / action choice
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
               +-> lifecycle write explicite (M17, capability-gated)
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
changes / constraints / acceptance-criteria / decisions / tasks
trace-requirement
change-context
analyze-change
quality
minos-status / external-references
nexus-status / augmented-context
change-orchestration                 read-only evaluation
lifecycle apply                      explicit controlled write
```

Référence : [docs/user/CLI.md](docs/user/CLI.md).

### MCP STDIO

```bash
morpheus mcp --stdio
```

Catalogue actuel :

```text
20 tools read-only
+ 1 tool write explicite : apply_change_lifecycle_transition
```

Le tool write n’est jamais activé par une simple capacité de lecture : il exige `WRITE_CHANGE`, confirmation, `expectedRevision`, `idempotencyKey` et audit.

Référence : [docs/developer/MCP.md](docs/developer/MCP.md).

### API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

```text
base             = /api/v1
OpenAPI          = 3.1.0
contract version = 1.6.0
```

Évaluation et mutation sont séparées :

```text
POST .../transition-check       read-only
POST .../lifecycle-transitions  controlled write
```

Référence : [docs/developer/API.md](docs/developer/API.md).

## Intégrations optionnelles

### MINOS

Résout des références vers des symboles de code via MCP STDIO, sans dépendance `com.minos.*` et sans embarquer MINOS.

### NEXUS

Construit du contexte technique à partir d’une intention MORPHEUS. NEXUS reste propriétaire du ranking, de la fusion, de la compression et du budget.

### JARVIS

Consomme les faits et décisions MORPHEUS :

```text
MORPHEUS = facts + lifecycle rules + transition decisions + controlled state invariants
JARVIS   = sequencing + orchestration + action choice
```

JARVIS choisit l’action. Une décision `ALLOWED` ne constitue jamais une mutation ; la mutation M17 requiert une commande distincte et tous ses garde-fous.

Guide : [docs/user/INTEGRATIONS.md](docs/user/INTEGRATIONS.md).

## Invariants importants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
UNKNOWN != FAILED
UNKNOWN != BLOCKED
optional engine absence != MORPHEUS failure
live external observation != snapshot mutation
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
read capability != write capability
ALLOWED != applied
published snapshot != operational lifecycle state
no implicit overwrite
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

Gate M17 mono-commande Windows :

```powershell
.\validate-m17.cmd
```

Documentation développeur : [docs/developer/README.md](docs/developer/README.md).

## État du projet

```text
C0-M14  plateforme et intégrations fondamentales    ✅ VALIDÉ / INTÉGRÉ
D0      réconciliation documentaire                 ✅ VALIDÉ / INTÉGRÉ
M15     acceptance / verification / evidence        ✅ VALIDÉ / INTÉGRÉ — 371/371
M16     constraint semantics / blocking policy      ✅ VALIDÉ / INTÉGRÉ — 393/393
M17     controlled lifecycle / write operations     🚧 EN COURS — PR #81 Draft
M18     real providers / multi-provider             ⏳ PLANIFIÉ
M19     production hardening / scale                ⏳ PLANIFIÉ
M20     release engineering / PROD / 1.0            ⏳ PLANIFIÉ
```

Dernier gate intégré de référence : **M16**.

```text
MORPHEUS        393/393 PASS
Architecture    161/161 PASS
Packaging       Windows + smokes PASS
```

Roadmap post-M14 : **D0 → M15 → M16 → M17 → M18 → M19 → M20**. Voir [la roadmap détaillée](docs/roadmap/POST_M14_EXECUTION.md).

## Documentation

**Point d’entrée : [docs/README.md](docs/README.md)**

```text
docs/user/        utilisation, quickstart, CLI, intégrations
docs/developer/   architecture, build/tests, API, MCP, intégrations
docs/product/     baseline C0, cas d’usage, MVP historique
docs/reference/   index des contrats machine
docs/governance/  roadmap courante, politique documentaire, audit
docs/validation/  preuves historiques des gates
docs/adr/         décisions d’architecture
docs/roadmap/     plans historiques + exécution post-M14
docs/openapi/     OpenAPI machine-readable
```

Roadmap : [docs/governance/ROADMAP.md](docs/governance/ROADMAP.md).  
Politique documentaire : [docs/governance/DOCUMENTATION_STATUS.md](docs/governance/DOCUMENTATION_STATUS.md).  
Dernière validation intégrée : [docs/validation/VALIDATION_M16.md](docs/validation/VALIDATION_M16.md).
