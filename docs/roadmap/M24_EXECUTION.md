# M24 — Query DSL, Saved Views & Export/Reporting

Statut : **QUALIFIÉ WINDOWS + LINUX — consolidation docs-only et intégration en cours**

Issue : #105 — **OPEN jusqu’au merge**
PR : #106 — **DRAFT jusqu’à fin de consolidation**
Branche : `m24/query-dsl-saved-views-reporting`
Baseline : `main@f70eaa1ad58633ee59874ab44f70963ab51152c6`
SHA exécutable qualifié : `be69e47da0ae209d2246df9c67bc08caeafb2bb0`

## Question de sortie

> Les utilisateurs peuvent-ils exprimer, sauvegarder et exporter des vues métier complexes sans dépendre d’un transport ou d’un format provider particulier ?

**Réponse : oui, démontré sur Windows et Linux sur le même SHA exécutable.**

## Invariants

```text
DSL != SQL passthrough
saved view != materialized truth
export != mutation
projection != domain mutation
bounded query != silently truncated semantics
portfolio result preserves ProjectSpecificationId
stable sort != SQLite/provider/HashMap/random order
stale saved-view revision != silent overwrite
surface parity != same transport shape
local-first remains default
```

## Budgets M24

```text
max encoded query expression   16 KiB
max AST nodes                  128
max boolean nesting depth      8
max leaf predicates            64
max sort fields                8
max projection fields          32
max page size                  500
max export rows                10,000
max export bytes               10 MiB
max saved views per scope      250
max saved-view name            160 chars
```

Tout dépassement est une erreur explicite, jamais une troncature sémantique silencieuse.

## Contrat livré

```text
QueryDefinition
QueryScope
  ProjectQueryScope
  PortfolioQueryScope
QueryEntityType
QueryFilter
  QueryPredicate
  QueryAnd
  QueryOr
  QueryNot
QueryOperator
QuerySort
QueryProjection
QueryPage
QueryResult
QueryDiagnostic
```

Le DSL n’expose aucun fragment SQL, nom de table/colonne, chemin provider ou structure transport.

## Saved views

```text
SavedViewId
SavedViewDefinition
SavedViewVersion
SavedViewStatus
SavedViewStore
SavedViewService
```

Les vues sont versionnées, utilisent CAS via `expectedRevision`, conservent une identité indépendante du nom et stockent une définition de requête, jamais une vérité matérialisée.

Persistence : Memory + SQLite additive V014.

## Export/reporting

```text
JSON      canonical, transport-safe
CSV       UTF-8, quoting/escaping déterministes
Markdown  table stable et testable
```

Les exports sont read-only et bornés en lignes/octets.

## Surfaces

CLI :

```text
query execute
views create/list/get/versions/update/archive/execute
export query/view
```

MCP : 10 tools M24, schémas stricts.

HTTP :

```text
POST /api/v1/queries/execute
GET/POST /api/v1/saved-views
GET/PUT /api/v1/saved-views/{id}
GET  /api/v1/saved-views/{id}/versions
POST /api/v1/saved-views/{id}/execute
POST /api/v1/saved-views/{id}/archive
POST /api/v1/saved-views/{id}/export
POST /api/v1/exports
```

Contrats machine : `contracts/public-surfaces.tsv` et `docs/openapi/morpheus-v1-query-m24.yaml`.

## Slices

### M24-S0 — cadrage / ADR / traçage GitHub

- [x] ré-audit baseline M23/M24
- [x] issue #105
- [x] branche M24 exacte depuis `main@f70eaa1...`
- [x] roadmap opérationnelle M24
- [x] ADR-0092 créée puis acceptée après preuve
- [x] PR #106 Draft ouverte

### M24-S1 — AST provider-neutral / validation / budgets

- [x] modèle explicite du DSL
- [x] registre de champs métier typés
- [x] diagnostics structurés
- [x] validation opérateur/type
- [x] budgets expression/AST/page/sort/projection/export/views
- [x] tests invalid field/operator/type/nesting/budgets

### M24-S2 — moteur de requête déterministe

- [x] project-scoped query sur snapshots publiés
- [x] portfolio-scoped query sur projets membres
- [x] filtering booléen
- [x] stable sorting + tie-break identité
- [x] projection
- [x] pagination `offset/limit/totalMatches/hasMore`
- [x] identité projet préservée en portfolio
- [x] fixtures dont l’ordre physique ne masque pas les défauts
- [x] absent/null distinct de chaîne vide

### M24-S3 — saved views / versioning

- [x] `SavedViewId`
- [x] définition + metadata + versions
- [x] create/get/list/update/archive/execute
- [x] CAS `expectedRevision`
- [x] même nom != même identité
- [x] query invalide rejetée avant persistance
- [x] archive versionnée

### M24-S4 — Memory persistence

- [x] port `SavedViewStore`
- [x] implémentation Memory
- [x] ordre déterministe
- [x] historique des versions
- [x] parité comportementale

### M24-S5 — SQLite V014

- [x] migration additive V014
- [x] `SqliteSavedViewStore`
- [x] round-trip/reopen/version preservation
- [x] migration depuis V013
- [x] aucune modification rétroactive de migration

### M24-S6 — export / reporting

- [x] canonical JSON stable
- [x] CSV stable et échappé
- [x] Markdown stable
- [x] empty result
- [x] project identity portfolio conservée
- [x] budgets rows/bytes
- [x] export strictement read-only

### M24-S7 — surfaces publiques

- [x] CLI query/views/export
- [x] MCP tools + JSON schemas stricts
- [x] HTTP `/api/v1` routes cohérentes
- [x] OpenAPI M24
- [x] `contracts/public-surfaces.tsv`
- [x] mêmes services applicatifs / mêmes règles métier

### M24-S8 — architecture / packaging / documentation / validator

- [x] contrats Query DSL / execution / saved views / export
- [x] frontières domain/application/adapters
- [x] DSL != SQL
- [x] saved view != materialized truth
- [x] export != mutation
- [x] packaging Windows/Linux
- [x] `validate-m24.cmd`
- [x] `scripts/validate-m24.ps1`
- [x] `scripts/validate-m24.sh`
- [x] guide utilisateur M24
- [x] guide développeur M24
- [x] OpenAPI + manifeste public qualifiés avant gate

### M24-S9 — qualification / intégration

- [x] Windows exact-head PASS
- [x] Linux exact-head PASS sur le même SHA exécutable
- [x] tests/architecture/coverage réels enregistrés
- [x] SBOM/provenance/portable PASS
- [x] `postGateExecutableDelta=NONE`
- [x] preuve `docs/validation/VALIDATION_M24.md`
- [x] ADR-0092 Acceptée après preuve
- [x] consolidation post-gate strictement docs-only
- [ ] PR #106 Ready + merge avec expected head
- [ ] issue #105 CLOSED/completed après merge
- [ ] réconciliation post-merge docs-only
- [ ] M25 devient NOW

## Qualification exacte

Windows :

```text
M24 VALIDATION PASS
sha=be69e47da0ae209d2246df9c67bc08caeafb2bb0
tests=543
architectureTests=221
lineCoverage=0.442936
branchCoverage=0.381166
portable=True
postGateExecutableDelta=NONE
```

Linux / WSL2 :

```text
M24 VALIDATION PASS
sha=be69e47da0ae209d2246df9c67bc08caeafb2bb0
tests=543
architectureTests=221
lineCoverage=0.443037
branchCoverage=0.381166
portable=true
postGateExecutableDelta=NONE
```

Les deux plateformes ont également confirmé : Query DSL, saved views, JSON/CSV/Markdown, budgets, convergence CLI/MCP/HTTP, SQLite V014, SBOM et provenance PASS.

Preuve complète : [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md).

## Règle post-gate

Le SHA `be69e47da0ae209d2246df9c67bc08caeafb2bb0` est le SHA **exécutable** qualifié. Les commits de consolidation qui suivent doivent rester exclusivement documentaires. Avant le merge, le compare `be69e47d... -> PR head` doit confirmer qu’aucun fichier exécutable ou contractuel n’a changé.