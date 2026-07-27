# MORPHEUS — Roadmap d’évolution post-M20

Statut : **ACTIVE — MORPHEUS 1.0 baseline intégrée ; D1 en cours ; M21 prochain jalon**

Dernière mise à jour : 27 juillet 2026

Cette roadmap commence après l’intégration de M20 et devient la trajectoire active de MORPHEUS 1.x. La trajectoire [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md) est conservée comme historique D0→M20.

## Baseline acquise

```text
C0 → M20      ✅ validés et intégrés
M20 code      9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge     75d0b82ab0c960692db2fee1ced146fa6547fd4a
M20 tests     454/454 PASS Windows + Linux
Architecture  182/182 PASS Windows + Linux
MORPHEUS      1.0.0
```

La publication opérationnelle du tag stable `v1.0.0` et de la GitHub Release est un acte de release distinct de la preuve M20 : M20 est intégré, mais la release publique/stable doit encore être créée depuis le commit intégré exact.

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
cross-project identity != source path
remote mode != mandatory cloud dependency
MORPHEUS != MINOS
MORPHEUS != NEXUS
MORPHEUS != JARVIS
```

# NOW

## R1 — Publication officielle MORPHEUS 1.0.0

Type : **release operation**, pas nouveau jalon fonctionnel.

Objectif : publier les artefacts déjà qualifiés depuis le commit intégré exact, avec tag `v1.0.0`, checksums et notes de release.

Gate :

```text
main exact SHA selected
v1.0.0 points exactly to release SHA
Windows setup + checksum published
Windows portable + checksum published
Linux portable + checksum published
release manifest(s) published
installation documentation linked
```

## D1 — Consolidation post-M20

Issue : **#94**.

Objectif : figer MORPHEUS 1.0 comme baseline documentaire active et ouvrir la trajectoire 1.x sans réécrire les preuves historiques.

Livrables :

```text
README / docs portal réconciliés
architecture/build docs alignées M20
ROADMAP globale alignée
POST_M20_EVOLUTION active
POST_M14_EXECUTION conservée historique
VALIDATION_D1 avec preuve locale
```

Exit criteria : delta documentaire uniquement, `git diff --check` PASS, reactor complet PASS, PR Ready après preuve réelle.

## M21 — Production Integrity & Surface Convergence

Question de sortie :

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

Axes :

```text
M21-S0  budgets/gates et ADR avant changement
M21-S1  CI reproductible et durable, sans logique de milestone temporaire
M21-S2  JaCoCo / quality gates / tendances de couverture
M21-S3  nettoyage Maven, dépendances, warnings et reproducible-build hygiene
M21-S4  convergence contractuelle CLI / MCP / HTTP
M21-S5  documentation single-source-of-truth + tests de cohérence
M21-S6  SBOM, provenance de build, signatures/checksums et trust policy
M21-S7  update channel / version discovery sans auto-mutation implicite
M21-S8  qualification Windows + Linux exact-head
```

Contraintes :

```text
surface parity != same transport shape
read surface != write capability
release metadata != runtime business state
update discovery != automatic update
security metadata != hidden network dependency
```

Exit criteria : gate production intégral Windows/Linux, contrats publics cohérents, dette build/documentation mesurée et bornée, supply-chain vérifiable.

# NEXT

## M22 — Provider SDK & Plugin Discovery Platform

Question de sortie :

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

Livrables attendus :

```text
Provider SDK public/stable
plugin metadata + compatibility contract
discovery explicite
capability negotiation
isolation classloader/process à décider par ADR
provider diagnostics
reference provider template
contract test kit
```

Invariants : `provider plugin != domain dependency`, absence d’un plugin optionnel non fatale, version incompatible explicite.

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

> MORPHEUS peut-il proposer des hypothèses ou synthèses assistées tout en distinguant strictement faits déterministes, inférences et preuves ?

Ce jalon est **optionnel** et ne change pas le principe `no mandatory LLM in core`.

Livrables candidats :

```text
InferenceObservation / Hypothesis model
source/evidence links
confidence + model/provider metadata
explicit deterministic vs inferred views
human confirmation boundary
optional adapter interface
no implicit lifecycle/write mutation
```

Invariants : `facts != inference`, `inference != evidence`, `confidence != truth`, aucune inférence ne remplace un fait publié.

# Ordre proposé

```text
M20 integrated
   ↓
R1  publish 1.0.0
   ↓
D1  consolidate 1.0 baseline
   ↓
M21 production integrity / surface convergence
   ↓
M22 provider SDK / plugins
   ↓
M23 portfolio intelligence
   ↓
M24 query DSL / saved views / reporting
   ↓
M25 policy packs
   ↓
M26 optional team/remote
   ↓
M27 optional assisted reasoning
```

L’ordre M22→M24 peut être réévalué après M21 avec des preuves d’usage réelles. M25→M27 restent volontairement plus lointains et ne doivent pas être détaillés en implémentation avant le cadrage du jalon concerné.

# Règle de pilotage 1.x

Pour chaque jalon M21+ :

```text
1. issue canonique
2. plan Mxx_EXECUTION avec NOW/NEXT/LATER et slices
3. question de sortie + invariants
4. ADR avant changement structurant
5. vertical slices
6. tests backend/adapters réels pertinents
7. gate exact-head Windows/Linux selon scope
8. VALIDATION_Mxx avec SHA et résultats réels
9. ADR acceptée seulement après preuve
10. PR Ready seulement après gate vert
11. merge uniquement après autorisation explicite
12. réconciliation roadmap/index après merge
```
