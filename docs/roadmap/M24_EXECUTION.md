# M24 — Query DSL, Saved Views & Export/Reporting

Statut : **EN COURS — M24-S0 cadré ; implémentation active**

Issue : #105 — **OPEN**  
PR : à créer en Draft  
Branche : `m24/query-dsl-saved-views-reporting`  
Baseline : `main@f70eaa1ad58633ee59874ab44f70963ab51152c6`.

## Question de sortie

> Les utilisateurs peuvent-ils exprimer, sauvegarder et exporter des vues métier complexes sans dépendre d’un transport ou d’un format provider particulier ?

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

Budgets pré-déclarés ; tout dépassement est une erreur explicite, jamais une troncature sémantique silencieuse.

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

Ces valeurs sont des garde-fous de plateforme. Le gate M24 vérifie qu'elles sont centralisées, documentées et appliquées de manière cohérente sur les surfaces.

## Contrat de DSL ciblé

Le modèle reste volontairement borné :

```text
QueryDefinition
QueryScope
QueryEntityType
QueryFilter
  - Predicate
  - And
  - Or
  - Not
QueryOperator
  - EQ
  - NEQ
  - CONTAINS
  - STARTS_WITH
  - ENDS_WITH
  - IN
  - EXISTS
QuerySort
QuerySortDirection
QueryProjection
QueryPage
QueryResult
QueryDiagnostic
```

Le DSL travaille uniquement sur des champs métier explicitement enregistrés par `QueryEntityType`. Aucun fragment SQL, nom de table/colonne, chemin provider ou syntaxe transport ne devient un concept métier.

## Scopes

```text
project-scoped   = ProjectSpecificationId explicite
portfolio-scoped = PortfolioId explicite ; chaque ligne conserve ProjectSpecificationId
```

Le scope fait partie de la requête et des saved views. Il n'est jamais inféré d'un chemin de workspace ou d'une URL de repository.

## Saved views

Une saved view stocke une définition de requête versionnée, pas ses résultats.

```text
SavedViewId                identité stable indépendante du nom
SavedViewDefinition        id + name + scope + query + revision + timestamps
SavedViewVersion           historique immuable des révisions
SavedViewStore             port application
```

Mise à jour : CAS sur `expectedRevision`; une révision obsolète échoue explicitement.

## Exports

```text
JSON      canonical via projections transport-safe / ADR-0047
CSV       UTF-8, header/order/escaping/newlines déterministes
Markdown  table stable, lisible, testable
```

L'export consomme un `QueryResult` déjà validé et ne possède aucun port de mutation.

## Slices

### M24-S0 — cadrage / ADR / traçage GitHub
- [x] ré-audit baseline M23/M24
- [x] issue #105
- [x] branche M24 exacte depuis `main@f70eaa1...`
- [x] roadmap opérationnelle M24
- [x] ADR-0092 proposée
- [ ] PR Draft ouverte

### M24-S1 — AST provider-neutral / validation / budgets
- [ ] modèle explicite du DSL
- [ ] registre de champs métier typés
- [ ] diagnostics structurés
- [ ] validation opérateur/type
- [ ] budgets AST/expression/page/sort/projection
- [ ] tests invalid field/operator/type/nesting/budgets

### M24-S2 — moteur de requête déterministe
- [ ] project-scoped query sur snapshots publiés
- [ ] portfolio-scoped query sur projets membres
- [ ] filtering booléen
- [ ] stable sorting + tie-break identité
- [ ] projection
- [ ] pagination `offset/limit/totalMatches/hasMore`
- [ ] identité projet préservée en portfolio
- [ ] fixtures dont l'ordre physique ne masque pas les défauts

### M24-S3 — saved views / versioning
- [ ] `SavedViewId`
- [ ] définition + metadata + versions
- [ ] create/get/list/update/execute
- [ ] CAS `expectedRevision`
- [ ] même nom != même identité
- [ ] query invalide rejetée avant persistance

### M24-S4 — Memory persistence
- [ ] port `SavedViewStore`
- [ ] implémentation Memory
- [ ] ordre déterministe
- [ ] historique des versions
- [ ] parité comportementale

### M24-S5 — SQLite V014
- [ ] migration additive V014
- [ ] `SqliteSavedViewStore`
- [ ] round-trip/reopen/version preservation
- [ ] migration depuis V013
- [ ] aucune modification rétroactive de migration

### M24-S6 — export / reporting
- [ ] canonical JSON stable
- [ ] CSV stable et échappé
- [ ] Markdown stable
- [ ] empty result
- [ ] project identity portfolio conservée
- [ ] budgets rows/bytes
- [ ] export strictement read-only

### M24-S7 — surfaces publiques
- [ ] CLI query/views/export
- [ ] MCP tools + JSON schemas stricts
- [ ] HTTP `/api/v1` routes cohérentes
- [ ] OpenAPI M24
- [ ] `contracts/public-surfaces.tsv`
- [ ] même services applicatifs / mêmes règles métier

### M24-S8 — architecture / packaging / documentation / validator
- [ ] `QueryDslContractTest` ou contrat équivalent
- [ ] frontières domain/application/adapters
- [ ] DSL != SQL
- [ ] saved view != materialized truth
- [ ] export != mutation
- [ ] packaging Windows/Linux
- [ ] `validate-m24.cmd`
- [ ] `scripts/validate-m24.ps1`
- [ ] `scripts/validate-m24.sh`
- [ ] docs utilisateur/développeur/OpenAPI/index

### M24-S9 — qualification / intégration
- [ ] Windows exact-head PASS
- [ ] Linux exact-head PASS sur le même SHA exécutable
- [ ] tests/architecture/coverage réels enregistrés
- [ ] SBOM/provenance/portable PASS
- [ ] `postGateExecutableDelta=NONE`
- [ ] preuve `docs/validation/VALIDATION_M24.md`
- [ ] ADR-0092 Acceptée seulement après preuve
- [ ] consolidation post-gate docs-only
- [ ] PR Ready + merge avec expected head
- [ ] issue #105 CLOSED/completed après merge
- [ ] réconciliation post-merge docs-only
- [ ] M25 devient NOW

## Qualification canonique

Jusqu'en août 2026, GitHub Actions n'est pas un gate M24.

Windows et Linux doivent produire un bloc machine-readable issu des logs réels :

```text
M24 VALIDATION PASS
sha=<exact-head>
baseRef=origin/main
version=1.0.0
tests=<actual>
architectureTests=<actual>
lineCoverage=<actual>
branchCoverage=<actual>
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Aucun nombre n'est prérempli ou inventé.
