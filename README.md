# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## État produit

**MORPHEUS 1.0.0 est validé, intégré et officiellement publié.** Les évolutions 1.x M21, M22 et M23 sont également validées et intégrées sur cette baseline produit.

```text
Release stable    v1.0.0
M20 merge         75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA  51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge         2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge         67c587057e287d57b0733f9e425a57b26cc38ae4
M23 executable    04a906e9d5858292ed0f0f1bec65246fef91ed63
M23 merge         88355b69c493677c8689eecad214fb00d283359b
M23 tests         507 PASS Windows + Linux
M23 architecture  195 PASS Windows + Linux
```

Preuve de publication : [docs/validation/VALIDATION_R1.md](docs/validation/VALIDATION_R1.md).  
Dernière preuve technique : [docs/validation/VALIDATION_M23.md](docs/validation/VALIDATION_M23.md).

## Ce que MORPHEUS fournit

- ingestion et normalisation de spécifications ;
- providers réels OpenSpec et Structured Markdown ;
- Provider SDK et plugins externes explicitement découvrables/activables ;
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
- **portfolio multi-projets provider-neutral**, références inter-projets et traversal bornée ;
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
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing / action choice
```

Chaque moteur reste autonome.

## Installation et démarrage

Documentation : **[Installation MORPHEUS 1.0](docs/user/INSTALLATION.md)**.

Release stable : **`v1.0.0`**.

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

## Premier portfolio M23

```bash
morpheus portfolio create --name "Platform"
morpheus portfolio add-project \
  --portfolio <portfolioId> \
  --project <projectId> \
  --name "Billing"
morpheus portfolio overview --portfolio <portfolioId>
```

Guide : [docs/user/PORTFOLIOS.md](docs/user/PORTFOLIOS.md).

## Surfaces

### CLI

```text
projects
sync / sync-status
composition sync / status / conflicts
provider-plugins discover / probe
portfolio create / add-project / missing / freshness
portfolio add-reference / list / overview / members / references / conflicts / traverse
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

M23 expose notamment :

```text
create_portfolio
register_portfolio_project
mark_portfolio_project_missing
observe_portfolio_freshness
add_cross_project_reference
get_portfolio_overview
list_portfolio_references
traverse_portfolio
```

La lecture et l’écriture restent séparées : le write lifecycle exige `WRITE_CHANGE`, confirmation, `expectedRevision`, `idempotencyKey` et audit.

Référence : [docs/developer/MCP.md](docs/developer/MCP.md).

### API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

Base : `/api/v1`. Les routes M23 sont sous `/api/v1/portfolios`.

Références :

- [docs/developer/API.md](docs/developer/API.md) ;
- [docs/openapi/morpheus-v1-portfolio-m23.yaml](docs/openapi/morpheus-v1-portfolio-m23.yaml).

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
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
freshness != full destructive rescan
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
morpheus-provider-sdk
morpheus-provider-testkit
morpheus-provider-reference
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

Dernier gate de jalon Windows :

```powershell
.\validate-m23.cmd
```

Dernier gate de jalon Linux :

```bash
bash ./scripts/validate-m23.sh 1.0.0
```

Preuve technique : [docs/validation/VALIDATION_M23.md](docs/validation/VALIDATION_M23.md).

## Roadmap 1.x

Trajectoire active : **[POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md)**.

```text
DONE
  R1   publication officielle v1.0.0 ✅
  D1   consolidation post-M20 ✅
  M21  Production Integrity & Surface Convergence ✅
  M22  Provider SDK & Plugin Discovery Platform ✅
  M23  Multi-project / Portfolio Specification Intelligence ✅

NOW
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
Portfolio M23 : [docs/user/PORTFOLIOS.md](docs/user/PORTFOLIOS.md).  
Architecture M23 : [docs/developer/PORTFOLIO_INTELLIGENCE.md](docs/developer/PORTFOLIO_INTELLIGENCE.md).

Preuves récentes :

- [VALIDATION_R1](docs/validation/VALIDATION_R1.md) ;
- [VALIDATION_M21](docs/validation/VALIDATION_M21.md) ;
- [VALIDATION_M22](docs/validation/VALIDATION_M22.md) ;
- [VALIDATION_M23](docs/validation/VALIDATION_M23.md).
