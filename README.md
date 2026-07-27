# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## État produit

**MORPHEUS 1.0.0 / M20 est validé et intégré.**

```text
M20 issue       #92 CLOSED / completed
M20 PR          #93 MERGED
Code qualifié   9199ed43c4bd8596a97db055eeff17ae31399eb8
Merge           75d0b82ab0c960692db2fee1ced146fa6547fd4a
Tests           454/454 PASS Windows + Linux
Architecture    182/182 PASS Windows + Linux
Reactor         14/14 SUCCESS
```

La publication GitHub stable `v1.0.0` reste une opération de release distincte de l’intégration M20.

## Ce que MORPHEUS fournit

- ingestion et normalisation de spécifications ;
- providers réels OpenSpec et Structured Markdown ;
- composition multi-provider provider-neutral et explicable ;
- provenance, précédence et conflits conservés ;
- snapshots versionnés et séparation `CURRENT / PROPOSED / HISTORICAL` ;
- recherche de requirements et requêtes métier ;
- traçabilité déterministe ;
- diagnostics qualité ;
- analyse de changements proposés ;
- critères d’acceptation, vérification et evidence explicites ;
- sémantique explicite des contraintes ;
- lifecycle contrôlé avec capability, confirmation, CAS, idempotency et audit ;
- CLI locale scriptable ;
- serveur MCP STDIO ;
- API HTTP locale `/api/v1` ;
- intégrations optionnelles MINOS, NEXUS et JARVIS ;
- setup Windows per-user ;
- distributions portables Windows/Linux avec runtime Java embarqué.

MORPHEUS ne nécessite aucun LLM pour son cœur fonctionnel.

## Écosystème

```text
MORPHEUS = specification facts / intent / lifecycle rules
           + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing / action choice
```

Chaque moteur reste autonome.

## Installation et démarrage

Documentation : **[Installation MORPHEUS 1.0](docs/user/INSTALLATION.md)**.

### Windows installé

Programme par défaut :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

État persistant :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

### Portable

Windows :

```powershell
.\morpheus\morpheus.exe help
```

Linux :

```bash
./morpheus/bin/morpheus help
```

Les distributions embarquent leur runtime Java ; aucun JDK utilisateur n’est requis.

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
change-orchestration
lifecycle apply
```

Référence : [docs/user/CLI.md](docs/user/CLI.md).

### MCP STDIO

```bash
morpheus mcp --stdio
```

La lecture et l’écriture restent séparées : le write lifecycle exige `WRITE_CHANGE`, confirmation, `expectedRevision`, `idempotencyKey` et audit.

Référence : [docs/developer/MCP.md](docs/developer/MCP.md).

### API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

Base : `/api/v1`.

Référence : [docs/developer/API.md](docs/developer/API.md).

## Invariants importants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
provider identifier != DomainIdentity
source path != identity
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
optional engine absence != MORPHEUS failure
optional provider absence != project failure when optional
MORPHEUS rules != JARVIS action sequencing
```

## Fondation technique

```text
Java                 21
Build                Maven Wrapper 3.9.16
Persistent store     SQLite
DomainIdentity       UUIDv7
MCP SDK              Java MCP SDK 2.0.0
MCP transport        STDIO
HTTP server          JDK jdk.httpserver
Distribution         jpackage + Inno Setup Windows
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

Gate M20 Windows :

```powershell
.\validate-m20.cmd
```

Gate M20 Linux :

```bash
bash scripts/validate-m20.sh
```

Preuve : [docs/validation/VALIDATION_M20.md](docs/validation/VALIDATION_M20.md).

## Roadmap 1.x

Trajectoire active : **[POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md)**.

```text
NOW
  R1   publication officielle v1.0.0
  D1   consolidation post-M20 — issue #94
  M21  Production Integrity & Surface Convergence

NEXT
  M22  Provider SDK & Plugin Discovery Platform
  M23  Multi-project / Portfolio Specification Intelligence
  M24  Query DSL, Saved Views & Export/Reporting

LATER
  M25  Policy Packs & Governance Automation
  M26  Optional Team/Remote Server Mode
  M27  Evidence-backed Assisted Reasoning
```

M27 reste optionnel : `facts != inference` et aucun LLM n’est requis dans le core.

## Documentation

**Point d’entrée : [docs/README.md](docs/README.md)**.

Roadmap : [docs/governance/ROADMAP.md](docs/governance/ROADMAP.md).
Roadmap 1.x : [docs/roadmap/POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md).
Dernière validation intégrée : [docs/validation/VALIDATION_M20.md](docs/validation/VALIDATION_M20.md).