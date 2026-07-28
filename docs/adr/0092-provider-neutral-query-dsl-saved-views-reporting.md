# ADR-0092 — Provider-neutral query DSL, saved views and reporting

Statut : **Proposée — M24**

Date : 28 juillet 2026

## Contexte

MORPHEUS possède déjà des requêtes déterministes ciblées (requirements, contenu métier, qualité, analyse, portfolio) et un JSON canonique transport-safe. Ces contrats restent cependant spécialisés par use case. M24 doit permettre à un utilisateur de décrire une vue métier complexe, la sauvegarder et l'exporter sans transformer SQL, SQLite, un provider ou un transport en langage métier.

M23 ajoute le scope portfolio et impose de conserver l'identité projet de chaque élément. M24 doit étendre cette capacité sans créer un moteur concurrent aux services applicatifs existants.

## Décision proposée

M24 introduit un AST provider-neutral borné dans la couche application, composé de types explicites :

```text
QueryDefinition
QueryScope
QueryEntityType
QueryFilter
QueryPredicate
QueryOperator
QuerySort
QuerySortDirection
QueryProjection
QueryPage
QueryResult
QueryDiagnostic
```

Invariants :

```text
DSL != SQL passthrough
provider-specific types -X-> query model
transport types -X-> query model
projection != domain mutation
bounded query != silently truncated semantics
stable sort != storage/provider iteration order
portfolio query result preserves ProjectSpecificationId
```

### Scope

Une requête est explicitement project-scoped ou portfolio-scoped. Le scope est exprimé par `ProjectSpecificationId` ou `PortfolioId`, jamais par workspace, repository, provider ou table.

### Entités et champs

Le DSL n'autorise que des `QueryEntityType` et champs déclarés par un registre applicatif fermé. Les champs transport/SQL ne sont pas adressables. Chaque champ déclare son type logique et les opérateurs autorisés.

### Filtres

Le premier contrat supporte uniquement les opérateurs justifiés et déterministes :

```text
EQ
NEQ
CONTAINS
STARTS_WITH
ENDS_WITH
IN
EXISTS
AND
OR
NOT
```

Les combinaisons champ/opérateur/type incompatibles produisent un diagnostic explicite et empêchent l'exécution.

Absence/null est distincte de chaîne vide. `EXISTS` teste la présence ; les autres opérateurs ne transforment pas silencieusement une valeur absente en valeur vide.

### Tri

Chaque tri demandé est stable. Un tie-break canonique par identité métier est toujours ajouté conceptuellement lorsque les clés demandées ne suffisent pas. Aucun résultat observable ne dépend de l'ordre SQLite, d'un `HashMap`, de l'ordre provider ou d'un UUID généré au moment de l'exécution.

### Projection

La projection limite les champs transport-safe retournés. Les champs d'identité nécessaires à l'interprétation du résultat restent présents selon le scope : identité d'entité et, pour un résultat portfolio, `ProjectSpecificationId`.

### Pagination

M24 conserve une pagination offset/limit bornée et expose `totalMatches` et `hasMore`. Une page dépassant le budget est rejetée explicitement.

## Budgets

Budgets initiaux M24 :

```text
encoded query expression   <= 16 KiB
AST nodes                  <= 128
boolean nesting depth      <= 8
leaf predicates            <= 64
sort fields                <= 8
projection fields          <= 32
page size                  <= 500
export rows                <= 10,000
export bytes               <= 10 MiB
saved views per scope      <= 250
saved-view name            <= 160 chars
```

Les budgets sont centralisés, testés et exposés dans les diagnostics/schémas publics. Un dépassement ne produit jamais un résultat partiel présenté comme complet.

## Saved views

M24 introduit une saved view first-class :

```text
SavedViewId
SavedViewDefinition
SavedViewVersion
SavedViewStore
```

Invariant :

```text
saved view != materialized truth
```

Une saved view stocke la définition de requête et ses métadonnées, jamais une copie autoritative du résultat.

`SavedViewId` est stable et indépendant du nom. Les mises à jour utilisent une révision attendue/CAS ; une révision obsolète échoue explicitement et ne peut pas être transformée en last-write-wins silencieux. L'historique de versions reste lisible selon le port de persistence.

## Persistence

`SavedViewStore` est un port application implémenté par Memory et SQLite. SQLite reçoit une migration additive V014 si V013 reste la dernière migration au moment de l'implémentation. Aucune migration historique n'est réécrite.

## Export et reporting

Les exports consomment un `QueryResult` validé et sont read-only :

```text
export != mutation
```

JSON : projections transport-safe puis `CanonicalJsonSerializer`, conformément à ADR-0047. Les objets domaine ne sont pas sérialisés directement.

CSV : UTF-8, ordre de colonnes explicite, quoting/escaping/newlines déterministes, lignes dans l'ordre du résultat.

Markdown : table déterministe, lisible et suffisamment stable pour snapshots/tests. Aucun LLM ou template libre n'intervient.

Les exports respectent les budgets de lignes et d'octets et ne déclenchent aucune mutation métier ou de saved view.

## Surfaces

CLI, MCP et HTTP exposent les mêmes intentions métier via les mêmes services applicatifs : exécuter une requête, gérer/exécuter une saved view et exporter un résultat.

Invariant :

```text
surface parity != same transport shape
```

Les formes CLI/JSON/HTTP peuvent différer, mais validation, budgets, scope, saved-view CAS, tri et exports sont définis une seule fois dans application.

## Conséquences attendues

Positives : langage métier stable et provider-neutral, vues partageables sans matérialisation autoritative, exports déterministes, budgets explicites, convergence des surfaces.

Coûts : registre de champs explicite à maintenir, store/versioning supplémentaires, migration SQLite, contrats de transport et tests de déterminisme plus nombreux.

## Validation requise avant acceptation

Cette ADR reste **Proposée** jusqu'à preuve du même SHA exécutable sur Windows et Linux avec :

```text
query DSL contract PASS
saved-view versioning/CAS PASS
Memory/SQLite parity PASS
SQLite V014 PASS
canonical JSON export PASS
CSV export PASS
Markdown export PASS
query/export budgets PASS
CLI/MCP/HTTP convergence PASS
architecture contract PASS
SBOM/provenance PASS
portable Windows/Linux PASS
postGateExecutableDelta=NONE
```

Preuve finale attendue : `docs/validation/VALIDATION_M24.md`.
