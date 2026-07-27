# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## Ce que MORPHEUS fournit

- ingestion et normalisation de spécifications ;
- providers réels OpenSpec et Structured Markdown ;
- composition multi-provider déterministe, provider-neutral et explicable ;
- provenance, précédence et conflits conservés explicitement ;
- snapshots versionnés et séparation `CURRENT / PROPOSED / HISTORICAL` ;
- recherche de requirements et requêtes métier ;
- traçabilité déterministe ;
- diagnostics qualité ;
- analyse de changements proposés ;
- critères d’acceptation, vérification et evidence explicites ;
- sémantique explicite des contraintes et décisions lifecycle explicables ;
- CLI locale scriptable ;
- serveur MCP STDIO avec **22 tools read-only + 1 tool write explicite** ;
- API HTTP locale `/api/v1` ;
- première mutation lifecycle contrôlée par capability, confirmation, CAS, idempotency et audit ;
- intégrations optionnelles MINOS, NEXUS et JARVIS.

MORPHEUS ne nécessite aucun LLM pour son cœur fonctionnel.

## Écosystème

```text
MORPHEUS = specification / intent / lifecycle rules / controlled state invariants
           + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing / action choice
```

Chaque moteur reste autonome.

```text
Sources / workspaces
 -> providers réels OpenSpec | Structured Markdown
 -> normalisation provider-neutral
 -> ProviderContribution
 -> composition déterministe / provenance / conflits
 -> KnowledgeSnapshot / SpecificationVersion
 -> Memory | SQLite
 -> Query / Traceability / Quality / Change Analysis
 -> CLI | MCP | HTTP API
               |
               +-> MINOS optionnel via MCP STDIO
               +-> NEXUS optionnel via MCP STDIO
               +-> contrat d’orchestration read-only <- HTTP <- JARVIS
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
morpheus composition sync --project <projectId>
morpheus composition status --project <projectId>
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
composition sync / status / conflicts
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
22 tools read-only
+ 1 tool write explicite : apply_change_lifecycle_transition
```

M18 ajoute notamment `get_composition_status` et `list_composition_conflicts`. Le tool write n’est jamais activé par une simple capacité de lecture : il exige `WRITE_CHANGE`, confirmation, `expectedRevision`, `idempotencyKey` et audit.

Référence : [docs/developer/MCP.md](docs/developer/MCP.md).

### API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

```text
base             = /api/v1
OpenAPI          = 3.1.0
contract version = 1.7.0
```

M18 expose :

```text
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts
```

Évaluation et mutation lifecycle restent séparées :

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
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
applicable != blocking
warning != blocker
severity != blocking policy
optional engine absence != MORPHEUS failure
optional provider absence != project failure when optional
live external observation != snapshot mutation
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
MORPHEUS rules != JARVIS action sequencing
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
morpheus-provider-markdown
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

Dernier gate de jalon mono-commande Windows :

```powershell
.\validate-m19.cmd
```

Documentation développeur : [docs/developer/README.md](docs/developer/README.md).

## État du projet

```text
C0-M14  plateforme et intégrations fondamentales    ✅ VALIDÉ / INTÉGRÉ
D0      réconciliation documentaire                 ✅ VALIDÉ / INTÉGRÉ
M15     acceptance / verification / evidence        ✅ VALIDÉ / INTÉGRÉ — 371/371
M16     constraint semantics / blocking policy      ✅ VALIDÉ / INTÉGRÉ — 393/393
M17     controlled lifecycle / write operations     ✅ VALIDÉ / INTÉGRÉ — 410/410
M18     real providers / multi-provider             ✅ VALIDÉ / INTÉGRÉ — PR #86 — 418/418
M19     production hardening / scale / operability  ✅ VALIDÉ TECHNIQUEMENT — PR #89 NON MERGÉE — 449/449
M20     release engineering / PROD / 1.0            ⏳ APRÈS MERGE M19
```

Dernier gate intégré de référence : **M18**.

```text
Code validé      7e8caacff567f51354fcb88bd7505a6d135071c0
Merge M18        30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
MORPHEUS         418/418 PASS
Architecture     170/170 PASS
Packaging        Windows + smokes PASS
Portable ZIP     33 919 431 octets
```

Dernier gate techniquement qualifié : **M19**, SHA de code exact `dca27db969b426ad43941ccb8cee7e926efb931b`.

```text
MORPHEUS         449/449 PASS Windows + Linux
Architecture     178/178 PASS Windows + Linux
Reactor          14/14 SUCCESS
Budgets          PASS, seuils gelés inchangés
Packaging        Windows + Linux + smokes PASS
PR               #89, non mergée
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
Dernière validation intégrée : [docs/validation/VALIDATION_M18.md](docs/validation/VALIDATION_M18.md).
