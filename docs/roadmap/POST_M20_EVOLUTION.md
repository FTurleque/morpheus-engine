# MORPHEUS — Roadmap d’évolution post-M20

Statut : **ACTIVE — MORPHEUS 1.0.0 publié ; M21 intégré ; M22 techniquement qualifié Windows + Linux ; M23 prochain jalon après intégration M22**

Dernière mise à jour : 28 juillet 2026

Cette roadmap commence après l’intégration de M20 et porte la trajectoire active de MORPHEUS 1.x. La trajectoire [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md) est conservée comme historique D0→M20.

## Baseline acquise

```text
C0 → M20      ✅ validés et intégrés
D1            ✅ validé et intégré
M21           ✅ validé et intégré
M22           ✅ techniquement qualifié Windows + Linux — intégration différée pendant gel CI
M20 code      9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge     75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 merge      51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 code      239d99657fbf193761767f382489dd637e642fe9
M21 merge     2fdce6601a07628c315fe03932750cd8ece3d777
M22 code      e42bc31384831e56592b11a3509b49a3fdf61773
M21 tests     473 PASS Windows + Linux
M22 tests     494 PASS Windows + Linux
Architecture  190 PASS M22 Windows + Linux
MORPHEUS      1.0.0
v1.0.0        ✅ tag stable publié
GitHub Release ✅ MORPHEUS 1.0.0 — 8/8 assets
```

Preuves :

- [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md) ;
- [`../validation/VALIDATION_M21.md`](../validation/VALIDATION_M21.md) ;
- [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md).

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

Exit criteria : **PASS**.

## M21 — Production Integrity & Surface Convergence

Issue : **#98 CLOSED / completed**.
PR : **#99 MERGED**.
Merge : `2fdce6601a07628c315fe03932750cd8ece3d777`.

Head exécutable qualifié Windows + Linux :

```text
239d99657fbf193761767f382489dd637e642fe9
```

Question de sortie :

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

Réponse : **oui, démontré sur Windows et Linux**.

```text
Windows reactor      14/14 SUCCESS
Linux reactor        14/14 SUCCESS
Tests                473 PASS
Architecture         187 PASS
Windows coverage     46.2800% line / 41.2734% branch
Linux coverage       46.2430% line / 41.2734% branch
CycloneDX/provenance PASS Windows + Linux
Portable             PASS Windows + Linux
CLI/MCP/HTTP          convergence PASS
Executable delta     NONE Windows + Linux
ADR-0089              Acceptée — M21
```

Preuve : [`../validation/VALIDATION_M21.md`](../validation/VALIDATION_M21.md).
Plan : [`M21_EXECUTION.md`](M21_EXECUTION.md).

## M22 — Provider SDK & Plugin Discovery Platform

Statut : **TECHNIQUEMENT TERMINÉ / QUALIFIÉ** — issue #100 ; PR #101 temporairement fermée pendant le gel CI avant août.

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
Capabilities           DISCOVER_PROJECT + READ_CURRENT_SPECIFICATIONS
Tests                  494 PASS Windows + Linux
Architecture           190 PASS Windows + Linux
Windows coverage       47.0508% line / 41.8839% branch
Linux coverage         47.0389% line / 41.8839% branch
CycloneDX/provenance   PASS Windows + Linux
Portable               PASS Windows + Linux
CLI/MCP/HTTP           provider platform convergence PASS
Executable delta       NONE Windows + Linux
ADR-0090               Acceptée — M22
```

Preuve : [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md).
Plan : [`M22_EXECUTION.md`](M22_EXECUTION.md).

L’intégration GitHub reste différée jusqu’à la fin du gel CI demandé ; aucune requalification n’est nécessaire tant que les changements supplémentaires restent documentaires uniquement.

# NOW

## M22 — intégration différée par gel CI

La partie technique M22 est terminée. La seule action restante avant de démarrer M23 est la réouverture puis l’intégration de PR #101 après la fin du gel CI, sans changement exécutable supplémentaire.

# NEXT

## M23 — Multi-project / Portfolio Specification Intelligence

Question de sortie :

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

Livrables attendus :

```text
portfolio registry
cross-project references
project-scoped + portfolio-scoped queries
cross-project traceability
conflict/provenance preservation
bounded traversal
incremental portfolio freshness
```

Invariants : `cross-project identity != source path`, absence d’un projet != suppression d’identité, traversal bornée et explicable.

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

Invariants : DSL != SQL passthrough, saved view != materialized truth, export != mutation.

# LATER

## M25 — Policy Packs & Governance Automation

Question de sortie :

> Les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme des politiques versionnées, explicables et auditables sans transformer du texte libre en interdiction implicite ?

Axes : policy packs, versioning, applicability, severity, blocking policy, provenance, dry-run, audit.

Invariants : `constraint text != executable policy`, `UNKNOWN != BLOCKED`, policy recommendation != applied mutation.

## M26 — Optional Team/Remote Server Mode

Question de sortie :

> MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ni imposer un cloud ?

Axes : authentication, authorization, concurrency, remote API hardening, multi-client state, backups, migration, observability.

Invariants : local mode reste first-class ; remote mode est opt-in ; authz read != authz write ; server state != source-of-truth provider.

## M27 — Evidence-backed Assisted Reasoning

Question de sortie :

> MORPHEUS peut-il enrichir ses réponses par des inférences assistées sans mélanger faits publiés, heuristiques et suggestions ?

Axes : evidence envelopes, explicit confidence, provenance, optional reasoning adapters, no silent fact mutation.

Invariants : `facts != inference`, inference never overwrites published facts, assisted reasoning remains optional.