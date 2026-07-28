# M23 — Multi-project / Portfolio Specification Intelligence

Statut : **ACTIVE — S0 cadrage en cours**

Issue : #103
Branche : `m23/portfolio-specification-intelligence`
Baseline : `main@67c587057e287d57b0733f9e425a57b26cc38ae4` après merge M22.

## Question de sortie

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

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
- [ ] PortfolioId
- [ ] membership et statut
- [ ] PortfolioEntityRef
- [ ] CrossProjectReference
- [ ] freshness et conflits

### M23-S2 — storage ports + memory
- [ ] PortfolioStore
- [ ] MemoryPortfolioStore
- [ ] comportement déterministe/idempotent

### M23-S3 — registry / freshness
- [ ] création et adhésion
- [ ] observations workspace/repository/provider
- [ ] missing non destructif
- [ ] fraîcheur incrémentale

### M23-S4 — cross-project references
- [ ] ajout idempotent
- [ ] provenance/evidence préservées
- [ ] conflits explicites

### M23-S5 — queries + traversal
- [ ] project-scoped
- [ ] portfolio-scoped
- [ ] BFS déterministe
- [ ] budgets et explication de troncature

### M23-S6 — SQLite
- [ ] migration V013
- [ ] SqlitePortfolioStore
- [ ] tests de parité et migration

### M23-S7 — surfaces publiques
- [ ] CLI
- [ ] MCP
- [ ] HTTP
- [ ] manifeste public-surfaces

### M23-S8 — packaging / docs
- [ ] classes packagées
- [ ] documentation utilisateur/développeur
- [ ] OpenAPI

### M23-S9 — qualification
- [ ] tests >= baseline M22
- [ ] architecture >= baseline M22
- [ ] JaCoCo gates
- [ ] SBOM/provenance
- [ ] Windows exact-head
- [ ] Linux exact-head
- [ ] ADR-0091 Accepted
- [ ] consolidation docs-only
