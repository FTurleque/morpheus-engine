# MORPHEUS — Roadmap d’évolution post-M20

Statut : **ACTIVE — MORPHEUS 1.0.0 publié ; M21 à M26 intégrés ; M27 prochain jalon actif**

Dernière mise à jour : 29 juillet 2026

Cette roadmap commence après l’intégration de M20 et porte la trajectoire active de MORPHEUS 1.x. La trajectoire [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md) est conservée comme historique D0→M20.

## Branche d’intégration active

La trajectoire 1.x travaille sur **`develop`** :

```text
milestone branches     depuis develop
milestone PR targets   develop
main                   branche de stabilisation / livraison, hors travail courant
```

Le merge M24 dans `main` est conservé comme fait historique. Depuis M25, les nouveaux jalons repartent de `develop` et ciblent `develop`, sauf décision explicite contraire du propriétaire.

## Baseline acquise

```text
C0 → M20      ✅ validés et intégrés
D1            ✅ validé et intégré
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
M24           ✅ validé et intégré
M25           ✅ validé et intégré
M26           ✅ validé et intégré
M20 merge     75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 merge      51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge     2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge     67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge     88355b69c493677c8689eecad214fb00d283359b
M24 merge     2b483ded10c783fff22c25035db89475c5c9fdaf
M24 code      be69e47da0ae209d2246df9c67bc08caeafb2bb0
M25 code      a392604fc9e8d00f4021351ab5ba53f8488ab920
M25 PR head   9239be641992f40a46f228e09cf6b34ad1cbb1a4
M25 merge     62bf0ea37f732116e821df7d98ae89d36c6dd75d
M25 tests     565 PASS Windows + Linux
M26 code      bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 PR head   36378842e3ef41e379ade17f869b0939d052bbbc
M26 merge     49016a18c844a78ec864235c544d82d487da7c8a
M26 tests     579 PASS Windows + Linux
Architecture  234 PASS M26 Windows + Linux
MORPHEUS      1.0.0
v1.0.0        ✅ tag stable publié
```

Preuves :

- [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md) ;
- [`../validation/VALIDATION_M21.md`](../validation/VALIDATION_M21.md) ;
- [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md) ;
- [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md) ;
- [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md) ;
- [`../validation/VALIDATION_M25.md`](../validation/VALIDATION_M25.md) ;
- [`../validation/VALIDATION_M26.md`](../validation/VALIDATION_M26.md).

## Invariants post-1.0

```text
local-first remains default
no mandatory LLM in core
facts != inference
inference never overwrites published facts
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
provider identifier != DomainIdentity
source path != identity
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
UNKNOWN != BLOCKED
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
cross-project identity != source path
project identity != workspace path
project identity != repository URL
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
freshness != full destructive rescan
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
stale saved-view revision != silent overwrite
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
pack activation != domain truth mutation
surface parity != same transport shape
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
plaintext bearer over remote HTTP is forbidden
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
multi-client concurrency != unbounded concurrency
metrics != secret disclosure
MORPHEUS != MINOS
MORPHEUS != NEXUS
MORPHEUS != JARVIS
update discovery != automatic update
checksum != signature
```

# DONE

## R1 — Publication officielle MORPHEUS 1.0.0

Statut : **TERMINÉ / PUBLIÉ** — issue #96.

```text
release SHA exact      51f6a120f3461c8d8c24323f3db8211d28d6cb42
tag stable             v1.0.0
Windows setup          PASS / published
Windows portable       PASS / published
Linux portable         PASS / published
SHA-256                PASS / published
release manifests      PASS / published
GitHub Release         stable / 8 assets
```

## D1 — Consolidation post-M20

Issue : **#94 CLOSED / completed**.  
PR : **#95 MERGED**.  
Merge : `51f6a120f3461c8d8c24323f3db8211d28d6cb42`.

## M21 — Production Integrity & Surface Convergence

Issue : **#98 CLOSED / completed**.  
PR : **#99 MERGED**.  
Merge : `2fdce6601a07628c315fe03932750cd8ece3d777`.  
Head exécutable qualifié : `239d99657fbf193761767f382489dd637e642fe9`.

```text
Tests                473 PASS Windows + Linux
Architecture         187 PASS Windows + Linux
CycloneDX/provenance PASS Windows + Linux
Portable             PASS Windows + Linux
CLI/MCP/HTTP         convergence PASS
Executable delta     NONE Windows + Linux
ADR-0089             Acceptée — M21
```

## M22 — Provider SDK & Plugin Discovery Platform

Issue : **#100 CLOSED / completed**.  
PR : **#101 MERGED**.  
Merge : `67c587057e287d57b0733f9e425a57b26cc38ae4`.  
Head exécutable qualifié : `e42bc31384831e56592b11a3509b49a3fdf61773`.

```text
SDK API               1
External provider     PASS
Tests                  494 PASS Windows + Linux
Architecture           190 PASS Windows + Linux
CycloneDX/provenance   PASS Windows + Linux
Portable               PASS Windows + Linux
Executable delta       NONE Windows + Linux
ADR-0090               Acceptée — M22
```

## M23 — Multi-project / Portfolio Specification Intelligence

Issue : **#103 CLOSED / completed**.  
PR : **#104 MERGED**.  
Merge : `88355b69c493677c8689eecad214fb00d283359b`.  
Head exécutable qualifié : `04a906e9d5858292ed0f0f1bec65246fef91ed63`.

```text
Portfolio registry       provider-neutral
Project identity         indépendante workspace/repository/provider
Cross-project references provenance/evidence préservées
Queries                  project-scoped + portfolio-scoped
Traversal                BFS déterministe, bornée, explicable
Persistence              Memory + SQLite V013
Tests                    507 PASS Windows + Linux
Architecture             195 PASS Windows + Linux
CLI/MCP/HTTP             convergence PASS
CycloneDX/provenance     PASS Windows + Linux
Portable                 PASS Windows + Linux
Executable delta         NONE Windows + Linux
ADR-0091                 Acceptée — M23
```

## M24 — Query DSL, Saved Views & Export/Reporting

Statut : **TERMINÉ / VALIDÉ / INTÉGRÉ**.

Issue : **#105 CLOSED / completed**.  
PR : **#106 MERGED**.  
Merge : `2b483ded10c783fff22c25035db89475c5c9fdaf`.  
Head exécutable qualifié : `be69e47da0ae209d2246df9c67bc08caeafb2bb0`.

```text
Query DSL                provider-neutral / typé / borné
Scopes                   project + portfolio explicites
Filter/sort/projection   PASS
Pagination               offset/limit/totalMatches/hasMore
Stable ordering          identity tie-break PASS
Null semantics           absent/null != empty
Saved views              versionnées + CAS
Persistence              Memory + SQLite V014
Canonical JSON           PASS
CSV                      PASS
Markdown                 PASS
Query/export budgets     PASS
CLI/MCP/HTTP             convergence PASS
Tests                    543 PASS Windows + Linux
Architecture             221 PASS Windows + Linux
Windows coverage         44.2936% line / 38.1166% branch
Linux coverage           44.3037% line / 38.1166% branch
CycloneDX/provenance     PASS Windows + Linux
Portable                 PASS Windows + Linux
Executable delta         NONE Windows + Linux
ADR-0092                 Acceptée — M24
```

Preuve : [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md).  
Plan final : [`M24_EXECUTION.md`](M24_EXECUTION.md).

## M25 — Policy Packs & Governance Automation

Statut : **TERMINÉ / VALIDÉ / INTÉGRÉ**.

Issue : **#107 CLOSED / completed**.  
PR : **#108 MERGED dans `develop`**.  
Merge : `62bf0ea37f732116e821df7d98ae89d36c6dd75d`.  
Head exact qualifié Windows + Linux/WSL : `a392604fc9e8d00f4021351ab5ba53f8488ab920`.  
Head PR post-gate docs-only : `9239be641992f40a46f228e09cf6b34ad1cbb1a4`.

Question de sortie :

> Les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme politiques versionnées, explicables et auditables sans transformer recommandations, texte libre ou dry-run en mutation silencieuse ?

Réponse : **oui, démontré sur Windows et Linux/WSL puis intégré dans `develop`**.

```text
Policy packs              provider-neutral / versions immuables
Scopes                    project + portfolio
Applicability             APPLICABLE / NOT_APPLICABLE / UNKNOWN
Decisions                 PASS / WARN / BLOCK / UNKNOWN
Overrides                 CAS + provenance conservée
Dry-run                   read-only PASS
Audit                     append-only
Persistence               Memory + SQLite V015
CLI/MCP/HTTP              convergence PASS
Tests                     565 PASS Windows + Linux
Architecture              231 PASS Windows + Linux
Windows coverage          42.9925% line / 36.3983% branch
Linux coverage            42.9945% line / 36.3983% branch
CycloneDX/provenance      PASS Windows + Linux
Portable                  PASS Windows + Linux
Executable delta          NONE Windows + Linux
ADR-0093                  Acceptée — M25
CI / GitHub Actions       non utilisé — juillet 2026
```

Preuve : [`../validation/VALIDATION_M25.md`](../validation/VALIDATION_M25.md).  
Plan final : [`M25_EXECUTION.md`](M25_EXECUTION.md).

## M26 — Optional Team/Remote Server Mode

Statut : **TERMINÉ / VALIDÉ / INTÉGRÉ**.

Issue : **#109 CLOSED / completed**.  
PR : **#110 MERGED dans `develop`**.  
Merge : `49016a18c844a78ec864235c544d82d487da7c8a`.  
Head exact qualifié Windows + Linux/WSL : `bf481b24054c4577144b4cb2ede2bdbc4d9974a2`.  
Head PR post-gate docs-only : `36378842e3ef41e379ade17f869b0939d052bbbc`.

Question de sortie :

> MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ?

Réponse : **oui, démontré sur Windows et Linux/WSL puis intégré dans `develop`**.

```text
Local mode                first-class / loopback-only
Remote mode               explicit opt-in
Transport                 HTTPS TLS 1.3/1.2 + PKCS12
Authentication            Bearer / token hash-only persistence
Authorization             READ < WRITE < ADMIN
Concurrency               bounded 1..512 + HTTP 429
HTTPS backlog             distinct et borné
Security headers          PASS / no implicit CORS
Secret non-disclosure     PASS
Observability             process-local counters
Backup                    VACUUM INTO + integrity + schema + SHA-256
Restore                   offline only + confirm + server lease
Schema                    SQLite V015 / future schema rejected
Control plane MCP         intentionally absent
Tests                     579 PASS Windows + Linux
Architecture              234 PASS Windows + Linux
Windows coverage          44.3507% line / 37.8842% branch
Linux coverage            44.3527% line / 37.8842% branch
CycloneDX/provenance      PASS Windows + Linux
Portable                  PASS Windows + Linux
Executable delta          NONE Windows + Linux
ADR-0094                  Acceptée — M26
CI / GitHub Actions       non utilisé — juillet 2026
```

Preuve : [`../validation/VALIDATION_M26.md`](../validation/VALIDATION_M26.md).  
Plan final : [`M26_EXECUTION.md`](M26_EXECUTION.md).

# NOW

## M27 — Evidence-backed Assisted Reasoning

Question : MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

Axes : evidence envelopes, explicit confidence, provenance, optional reasoning adapters, no silent fact mutation.

Invariants : `facts != inference`, inference never overwrites published facts, assisted reasoning remains optional.
