# M23 — Multi-project / Portfolio Specification Intelligence

Statut : **TECHNIQUEMENT TERMINÉ / QUALIFIÉ — Windows + Linux exact-head PASS — intégration PR #104 en cours**

Issue : #103  
PR : #104  
Branche : `m23/portfolio-specification-intelligence`  
Baseline : `main@67c587057e287d57b0733f9e425a57b26cc38ae4` après merge M22.

Head exécutable qualifié Windows + Linux :

```text
04a906e9d5858292ed0f0f1bec65246fef91ed63
```

Les commits postérieurs sont strictement documentaires. Toute modification exécutable invaliderait les gates acquis.

## Question de sortie

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

**Réponse : oui, démontré sur Windows et Linux.**

## Invariants

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
precedence != provenance erasure
traversal is bounded and explainable
freshness != full destructive rescan
local-first remains default
```

## Budgets

```text
max traversal depth        8
max traversal nodes        1,000
max traversal links        5,000
max portfolio page         500
reference relation length  128
entity type length         128
revision length            512
```

## Slices

### M23-S0 — cadrage / ADR
- [x] issue #103
- [x] branche M23
- [x] ADR-0091 proposée avant code
- [x] invariants et budgets

### M23-S1 — modèle domaine portfolio
- [x] `PortfolioId`
- [x] membership et statut
- [x] `PortfolioEntityRef`
- [x] `CrossProjectReference`
- [x] freshness et conflits

### M23-S2 — storage ports + memory
- [x] `PortfolioStore`
- [x] `MemoryPortfolioStore`
- [x] comportement déterministe/idempotent

### M23-S3 — registry / freshness
- [x] création et adhésion
- [x] observations workspace/repository/provider
- [x] missing non destructif
- [x] fraîcheur incrémentale

### M23-S4 — cross-project references
- [x] ajout idempotent
- [x] provenance/evidence préservées
- [x] conflits explicites

### M23-S5 — queries + traversal
- [x] project-scoped
- [x] portfolio-scoped
- [x] BFS déterministe
- [x] ordre de découverte BFS préservé dans les vues publiques
- [x] budgets et explication de troncature

### M23-S6 — SQLite
- [x] migration V013
- [x] `SqlitePortfolioStore`
- [x] tests de parité et migration

### M23-S7 — surfaces publiques
- [x] CLI `morpheus portfolio ...`
- [x] MCP portfolio tools
- [x] HTTP `/api/v1/portfolios`
- [x] manifeste `public-surfaces.tsv`
- [x] projections `PortfolioPublicViews` transport-safe

### M23-S8 — packaging / docs
- [x] classes M23 + V013 packagées
- [x] smoke portfolio CLI
- [x] smoke portfolio HTTP
- [x] convergence CLI/MCP/HTTP
- [x] documentation utilisateur `docs/user/PORTFOLIOS.md`
- [x] documentation développeur `docs/developer/PORTFOLIO_INTELLIGENCE.md`
- [x] contrat OpenAPI M23 `docs/openapi/morpheus-v1-portfolio-m23.yaml`
- [x] index ADR / validation / documentation réconciliés
- [x] roadmaps active et gouvernance réconciliées

### M23-S9 — qualification
- [x] tests >= baseline M22
- [x] architecture >= baseline M22
- [x] JaCoCo gates
- [x] SBOM/provenance
- [x] Windows exact-head
- [x] Linux exact-head
- [x] même SHA exécutable Windows + Linux
- [x] `postGateExecutableDelta=NONE` Windows + Linux
- [x] ADR-0091 Acceptée
- [x] consolidation docs-only

## Preuve Windows exact-head

```text
M23 VALIDATION PASS
sha=04a906e9d5858292ed0f0f1bec65246fef91ed63
baseRef=origin/main
version=1.0.0
tests=507
architectureTests=195
lineCoverage=0.467034
branchCoverage=0.409099
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

## Preuve Linux exact-head

```text
M23 VALIDATION PASS
sha=04a906e9d5858292ed0f0f1bec65246fef91ed63
baseRef=origin/main
version=1.0.0
tests=507
architectureTests=195
lineCoverage=0.466979
branchCoverage=0.409099
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
M23 LINUX EXIT CODE: 0
```

## Sortie

```text
Executable qualified SHA  04a906e9d5858292ed0f0f1bec65246fef91ed63
Windows                    PASS
Linux                      PASS
Tests                      507 PASS
Architecture               195 PASS
ADR-0091                   Acceptée — M23
Post-gate changes          docs-only
Next milestone             M24 — Query DSL, Saved Views & Export/Reporting
```

Preuve détaillée : [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md).
