# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## État produit

**MORPHEUS 1.0.0 est validé, intégré et officiellement publié.** Les évolutions 1.x M21 à M26 sont également validées et intégrées sur cette baseline produit.

```text
Release stable    v1.0.0
M20 merge         75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA  51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge         2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge         67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge         88355b69c493677c8689eecad214fb00d283359b
M24 executable    be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 merge         2b483ded10c783fff22c25035db89475c5c9fdaf
M25 exact head    a392604fc9e8d00f4021351ab5ba53f8488ab920
M25 merge         62bf0ea37f732116e821df7d98ae89d36c6dd75d
M26 exact head    bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 PR head       36378842e3ef41e379ade17f869b0939d052bbbc
M26 merge         49016a18c844a78ec864235c544d82d487da7c8a
M26 tests         579 PASS Windows + Linux
M26 architecture  234 PASS Windows + Linux
```

Preuve de publication : [docs/validation/VALIDATION_R1.md](docs/validation/VALIDATION_R1.md).  
Dernière preuve technique : [docs/validation/VALIDATION_M26.md](docs/validation/VALIDATION_M26.md).

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
- Query DSL provider-neutral borné ;
- saved views versionnées avec CAS ;
- exports JSON canonique, CSV et Markdown déterministes/read-only ;
- Policy Packs provider-neutral versionnés ;
- activations et overrides explicites avec CAS et provenance ;
- dry-run de gouvernance strictement read-only ;
- audit append-only des configurations de policy ;
- CLI locale scriptable ;
- serveur MCP STDIO ;
- API HTTP locale `/api/v1` ;
- **mode serveur d’équipe remote optionnel en HTTPS** ;
- **Bearer authentication avec persistence hash-only** ;
- **RBAC READ / WRITE / ADMIN** ;
- **concurrence remote bornée avec HTTP 429** ;
- **backup SQLite cohérent et restore offline explicite** ;
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
           + provider-neutral governance policy contracts
           + optional remote/team access boundary
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

## Policy Packs / Governance Automation

```bash
morpheus policy pack create \
  --name "Release governance" \
  --rules 'new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0' \
  --actor operator \
  --reason baseline
```

Activer explicitement une version :

```bash
morpheus policy activate \
  --id <policyPackId> \
  --version <versionId> \
  --project <projectId> \
  --expected-revision 0 \
  --actor operator \
  --reason enable
```

Tester sans mutation :

```bash
morpheus policy dry-run --id <policyPackId> --version <versionId> --project <projectId>
```

Guide : [docs/user/POLICY_PACKS.md](docs/user/POLICY_PACKS.md).

## Team / Remote Server Mode — M26

Le mode local reste le comportement par défaut. Un bind non-loopback n’est jamais obtenu implicitement.

Le mode remote est explicitement activé par `api --remote` et exige :

```text
HTTPS
PKCS12 keystore
TLS 1.3 / TLS 1.2
Bearer authentication
READ / WRITE / ADMIN RBAC
```

Les tokens sont générés avec 256 bits d’entropie et seul leur SHA-256 est persisté.

Surfaces serveur M26 :

```text
GET  /api/v1/server/status        READ
POST /api/v1/server/backups       ADMIN
GET  /api/v1/metrics              ADMIN
```

Maintenance locale :

```text
server identity create
server backup create
server backup verify
server restore --confirm
```

Le provisioning d’identité et le restore sont volontairement absents du control plane HTTP/MCP. Le restore est **offline uniquement**.

La concurrence applicative est bornée (`1..512`, défaut `64`) et la saturation retourne HTTP `429`. Le listen backlog HTTPS est distinct de cette limite applicative afin de ne pas transformer la saturation en refus TCP prématuré.

Guide utilisateur : [docs/user/TEAM_REMOTE_SERVER.md](docs/user/TEAM_REMOTE_SERVER.md).  
Architecture : [docs/developer/REMOTE_SERVER_PLATFORM.md](docs/developer/REMOTE_SERVER_PLATFORM.md).  
OpenAPI : [docs/openapi/morpheus-v1-remote-m26.yaml](docs/openapi/morpheus-v1-remote-m26.yaml).

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
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
pack activation != domain truth mutation
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
multi-client concurrency != unbounded concurrency
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
HTTP local           JDK jdk.httpserver
Remote HTTPS         JDK HttpsServer, opt-in
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

Gate M26 Windows :

```powershell
.\validate-m26.cmd 1.0.0
```

Gate M26 Linux :

```bash
bash ./scripts/validate-m26.sh 1.0.0
```

Preuve technique : [docs/validation/VALIDATION_M26.md](docs/validation/VALIDATION_M26.md).

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
  M25  Policy Packs & Governance Automation ✅
  M26  Optional Team/Remote Server Mode ✅

NOW
  M27  Evidence-backed Assisted Reasoning
```

M27 reste optionnel : `facts != inference` et aucun LLM n’est requis dans le core.

## Documentation

**Point d’entrée : [docs/README.md](docs/README.md)**.

Roadmap : [docs/governance/ROADMAP.md](docs/governance/ROADMAP.md).  
Roadmap 1.x : [docs/roadmap/POST_M20_EVOLUTION.md](docs/roadmap/POST_M20_EVOLUTION.md).  
Team/Remote Server : [docs/user/TEAM_REMOTE_SERVER.md](docs/user/TEAM_REMOTE_SERVER.md).  
Architecture Remote : [docs/developer/REMOTE_SERVER_PLATFORM.md](docs/developer/REMOTE_SERVER_PLATFORM.md).
