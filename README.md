# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## État produit

**MORPHEUS 1.0.0 est validé, intégré et officiellement publié.** Les évolutions 1.x M21 à M24 sont également validées et intégrées sur cette baseline produit.

```text
Release stable    v1.0.0
M20 merge         75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA  51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge         2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge         67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge         88355b69c493677c8689eecad214fb00d283359b
M24 executable    be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 PR head       863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
M24 merge         2b483ded10c783fff22c25035db89475c5c9fdaf
M24 tests         543 PASS Windows + Linux
M24 architecture  221 PASS Windows + Linux
```

Preuve de publication : [docs/validation/VALIDATION_R1.md](docs/validation/VALIDATION_R1.md).  
Dernière preuve technique : [docs/validation/VALIDATION_M24.md](docs/validation/VALIDATION_M24.md).

## Ce que MORPHEUS fournit

- ingestion et normalisation de spécifications ;
- providers réels OpenSpec et Structured Markdown ;
- Provider SDK et plugins externes explicitement découvrables/activables ;
- composition multi-provider provider-neutral et explicable ;
- provenance, précédence et conflits conservés ;
- snapshots versionnés et séparation `CURRENT / PROPOSED / HISTORICAL` ;
- recherche de requirements, requêtes métier, traçabilité et qualité ;
- critères d’acceptation, vérification et evidence explicites ;
- sémantique explicite des contraintes ;
- lifecycle contrôlé avec capability, confirmation, CAS, idempotency et audit ;
- portfolio multi-projets provider-neutral, références inter-projets et traversal bornée ;
- **Query DSL provider-neutral borné** ;
- **saved views versionnées avec CAS** ;
- **exports JSON canonique, CSV et Markdown déterministes/read-only** ;
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
           + provider-neutral query/view/reporting contracts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing / action choice
```

Chaque moteur reste autonome.

## Installation et démarrage

Documentation : **[Installation MORPHEUS 1.0](docs/user/INSTALLATION.md)**.

Release stable : **`v1.0.0`**.

Windows portable :

```powershell
.\morpheus\morpheus.exe help
```

Linux portable :

```bash
./morpheus/bin/morpheus help
```

Les distributions embarquent leur runtime Java ; aucun JDK utilisateur n’est requis.

## Premier projet

```bash
morpheus projects add --workspace /path/to/project
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query "session"
```

## Portfolio

```bash
morpheus portfolio create --name "Platform"
morpheus portfolio add-project --portfolio <portfolioId> --project <projectId> --name "Billing"
morpheus portfolio overview --portfolio <portfolioId>
```

Guide : [docs/user/PORTFOLIOS.md](docs/user/PORTFOLIOS.md).

## Query DSL / Saved Views / Reporting

Exécuter une requête projet :

```bash
morpheus query execute \
  --project <projectId> \
  --entity requirement \
  --filter 'title contains "session"' \
  --sort title:asc \
  --limit 50
```

Créer une saved view :

```bash
morpheus views create \
  --name "Current requirements" \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT'
```

Exporter :

```bash
morpheus export view --id <savedViewId> --format csv
```

Guide complet : [docs/user/QUERY_VIEWS_REPORTING.md](docs/user/QUERY_VIEWS_REPORTING.md).

## Surfaces M24

CLI :

```text
query execute
views create/list/get/versions/update/archive/execute
export query/view
```

MCP :

```text
execute_query
create_saved_view
list_saved_views
get_saved_view
list_saved_view_versions
update_saved_view
archive_saved_view
execute_saved_view
export_query
export_saved_view
```

HTTP :

```text
POST /api/v1/queries/execute
GET/POST /api/v1/saved-views
GET/PUT /api/v1/saved-views/{id}
GET /api/v1/saved-views/{id}/versions
POST /api/v1/saved-views/{id}/execute
POST /api/v1/saved-views/{id}/archive
POST /api/v1/saved-views/{id}/export
POST /api/v1/exports
```

OpenAPI : [docs/openapi/morpheus-v1-query-m24.yaml](docs/openapi/morpheus-v1-query-m24.yaml).

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
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
portfolio result preserves ProjectSpecificationId
surface parity != same transport shape
optional engine absence != MORPHEUS failure
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

Gate M24 Windows :

```powershell
.\validate-m24.cmd 1.0.0
```

Gate M24 Linux :

```bash
bash ./scripts/validate-m24.sh 1.0.0
```

Preuve technique : [docs/validation/VALIDATION_M24.md](docs/validation/VALIDATION_M24.md).

## Roadmap 1.x

Trajectoire active : **[POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md)**.

```text
DONE
  R1   publication officielle v1.0.0 ✅
  D1   consolidation post-M20 ✅
  M21  Production Integrity & Surface Convergence ✅
  M22  Provider SDK & Plugin Discovery Platform ✅
  M23  Multi-project / Portfolio Specification Intelligence ✅
  M24  Query DSL, Saved Views & Export/Reporting ✅

NOW
  M25  Policy Packs & Governance Automation

LATER
  M26  Optional Team/Remote Server Mode
  M27  Evidence-backed Assisted Reasoning
```

M27 reste optionnel : `facts != inference` et aucun LLM n’est requis dans le core.

## Documentation

**Point d’entrée : [docs/README.md](docs/README.md)**.

Roadmap : [docs/governance/ROADMAP.md](docs/governance/ROADMAP.md).  
Roadmap 1.x : [docs/roadmap/POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md).  
Queries / Saved Views / Reporting : [docs/user/QUERY_VIEWS_REPORTING.md](docs/user/QUERY_VIEWS_REPORTING.md).  
Architecture Query Platform : [docs/developer/QUERY_PLATFORM.md](docs/developer/QUERY_PLATFORM.md).