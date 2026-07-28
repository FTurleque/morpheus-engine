# MORPHEUS — Roadmap d’évolution post-M20

Statut : **ACTIVE — MORPHEUS 1.0.0 publié ; M21 et M22 intégrés ; M23 qualifié Windows + Linux, intégration en cours ; M24 prochain jalon**

Dernière mise à jour : 28 juillet 2026

Cette roadmap commence après l’intégration de M20 et porte la trajectoire active de MORPHEUS 1.x. La trajectoire [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md) est conservée comme historique D0→M20.

## Baseline acquise

```text
C0 → M20      ✅ validés et intégrés
D1            ✅ validé et intégré
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ qualifié Windows + Linux — PR #104 en intégration
M20 merge     75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 merge      51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge     2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge     67c587057e287d57b0733f9e425a57b26cc38ae4
M23 code      04a906e9d5858292ed0f0f1bec65246fef91ed63
M23 tests     507 PASS Windows + Linux
Architecture  195 PASS M23 Windows + Linux
MORPHEUS      1.0.0
v1.0.0        ✅ tag stable publié
```

Preuves :

- [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md) ;
- [`../validation/VALIDATION_M21.md`](../validation/VALIDATION_M21.md) ;
- [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md) ;
- [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md).

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
capability declaration != capability implementation proof
probe != read
optional provider absence != project failure
incompatible provider != silently loaded provider
classloader isolation != security sandbox
cross-project identity != source path
project identity != workspace path
project identity != repository URL
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
freshness != full destructive rescan
remote mode != mandatory cloud dependency
MORPHEUS != MINOS
MORPHEUS != NEXUS
MORPHEUS != JARVIS
surface parity != same transport shape
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

Preuve : [`../validation/VALIDATION_M21.md`](../validation/VALIDATION_M21.md).

## M22 — Provider SDK & Plugin Discovery Platform

Issue : **#100 CLOSED / completed**.  
PR : **#101 MERGED**.  
Merge : `67c587057e287d57b0733f9e425a57b26cc38ae4`.

Head exécutable qualifié Windows + Linux :

```text
e42bc31384831e56592b11a3509b49a3fdf61773
```

Question de sortie :

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

Réponse : **oui, démontré sur Windows et Linux**.

```text
SDK API               1
Discovery             metadata-only / zero classloading
Activation            explicite / URLClassLoader dédié
Probe                  SpecificationProvider
Read                   SpecificationContentReader
Reference provider     vrai JAR externe
Tests                  494 PASS Windows + Linux
Architecture           190 PASS Windows + Linux
CycloneDX/provenance   PASS Windows + Linux
Portable               PASS Windows + Linux
Executable delta       NONE Windows + Linux
ADR-0090               Acceptée — M22
```

Preuve : [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md).

# NOW

## M23 — Multi-project / Portfolio Specification Intelligence

Statut : **TECHNIQUEMENT TERMINÉ / QUALIFIÉ Windows + Linux — PR #104 en intégration**.

Issue : #103.  
PR : #104.  
Baseline : `main@67c587057e287d57b0733f9e425a57b26cc38ae4`.

Head exécutable qualifié Windows + Linux :

```text
04a906e9d5858292ed0f0f1bec65246fef91ed63
```

Question de sortie :

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

Réponse : **oui, démontré sur Windows et Linux**.

```text
Portfolio registry       provider-neutral
Project identity         indépendante workspace/repository/provider
Missing project          non destructif
Cross-project references provenance/evidence préservées
Conflicts                explicites, sans silent last-write-wins
Queries                  project-scoped + portfolio-scoped
Traversal                BFS déterministe, bornée, explicable
Traversal order          ordre de découverte BFS préservé
Freshness                incrémentale par projet
Persistence              Memory + SQLite V013
CLI/MCP/HTTP             convergence PASS
Tests                    507 PASS Windows + Linux
Architecture             195 PASS Windows + Linux
Windows coverage         46.7034% line / 40.9099% branch
Linux coverage           46.6979% line / 40.9099% branch
CycloneDX/provenance     PASS Windows + Linux
Portable                 PASS Windows + Linux
Executable delta         NONE Windows + Linux
ADR-0091                 Acceptée — M23
```

Preuve : [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md).  
Plan : [`M23_EXECUTION.md`](M23_EXECUTION.md).

Les commits post-gate restent strictement documentaires ; le SHA exécutable qualifié demeure `04a906e9d5858292ed0f0f1bec65246fef91ed63`.

# NEXT

## M24 — Query DSL, Saved Views & Export/Reporting

Question de sortie :

> Les utilisateurs peuvent-ils exprimer, sauvegarder et exporter des vues métier complexes sans dépendre d’un transport ou d’un format provider particulier ?

Livrables attendus :

```text
provider-neutral query DSL
filter/sort/projection/pagination
saved views versionnées
canonical JSON export
CSV/Markdown reporting
CLI/MCP/HTTP parity
query budgets
```

Invariants : `DSL != SQL passthrough`, `saved view != materialized truth`, `export != mutation`.

# LATER

## M25 — Policy Packs & Governance Automation

Question : les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme politiques versionnées, explicables et auditables ?

Axes : policy packs, versioning, applicability, severity, blocking policy, provenance, dry-run, audit.

Invariants : `constraint text != executable policy`, `UNKNOWN != BLOCKED`, policy recommendation != applied mutation.

## M26 — Optional Team/Remote Server Mode

Question : MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ?

Axes : authentication, authorization, concurrency, remote API hardening, multi-client state, backups, migration, observability.

Invariants : local mode reste first-class ; remote mode est opt-in ; authz read != authz write ; server state != source-of-truth provider.

## M27 — Evidence-backed Assisted Reasoning

Question : MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

Axes : evidence envelopes, explicit confidence, provenance, optional reasoning adapters, no silent fact mutation.

Invariants : `facts != inference`, inference never overwrites published facts, assisted reasoning remains optional.
