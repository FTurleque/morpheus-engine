# ADR-0092 — Provider-neutral query DSL, saved views and reporting

Statut : **Acceptée — M24**

Date : 28 juillet 2026

## Contexte

MORPHEUS possédait déjà des requêtes déterministes spécialisées, un JSON canonique transport-safe et, depuis M23, un scope portfolio. M24 devait permettre d'exprimer une vue métier complexe, la sauvegarder et l'exporter sans transformer SQL, SQLite, un provider ou un transport en langage métier.

## Décision

M24 introduit dans la couche application un AST provider-neutral borné :

```text
QueryDefinition
QueryScope
QueryEntityType
QueryFilter
  QueryPredicate
  QueryAnd
  QueryOr
  QueryNot
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

## Scope

Une requête est explicitement project-scoped ou portfolio-scoped. Le scope est `ProjectSpecificationId` ou `PortfolioId`, jamais un workspace, repository, provider ou nom de table.

## Entités et champs

Le DSL n'autorise que des `QueryEntityType` et champs déclarés par `QuerySchemaRegistry`. Chaque champ possède un type logique et une liste fermée d'opérateurs autorisés.

Les noms SQL, détails de stockage, chemins provider et structures transport ne sont pas adressables.

## Filtres

Opérateurs :

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

Absence/null est distincte de chaîne vide. `EXISTS` teste la présence sans coercition implicite.

## Tri

Le tri observable est stable. Un tie-break canonique par `projectId` puis `entityId` complète les clés demandées lorsque nécessaire. Aucun résultat ne dépend de l'ordre SQLite, provider ou `HashMap`.

## Projection et pagination

La projection limite les champs transport-safe mais préserve les identités nécessaires à l'interprétation. Un résultat portfolio conserve son `ProjectSpecificationId`.

La pagination offset/limit expose `totalMatches` et `hasMore`. Une page hors budget est rejetée explicitement.

## Budgets

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

Les budgets sont centralisés dans `QueryBudgets`, testés et exposés par les contrats publics pertinents. Un dépassement ne produit jamais un résultat partiel présenté comme complet.

## Saved views

M24 introduit :

```text
SavedViewId
SavedViewDefinition
SavedViewVersion
SavedViewStatus
SavedViewStore
SavedViewService
```

Invariant :

```text
saved view != materialized truth
```

Une saved view stocke la définition de requête et ses métadonnées, jamais une copie autoritative du résultat. `SavedViewId` est stable et indépendant du nom.

Les updates utilisent `expectedRevision`/CAS. Une révision obsolète échoue explicitement ; aucun last-write-wins silencieux n'est autorisé. L'historique des versions reste lisible.

## Persistance

`SavedViewStore` est un port application implémenté par Memory et SQLite.

SQLite ajoute uniquement la migration additive :

```text
V014__saved_views.sql
```

Aucune migration historique n'est réécrite.

`QueryDefinitionCodec` encode la définition de façon déterministe pour SQLite sans Java serialization ni JSON arbitraire.

## Export et reporting

Les exports sont read-only :

```text
export != mutation
```

Formats :

```text
JSON      projections transport-safe + CanonicalJsonSerializer
CSV       UTF-8, colonnes/quoting/escaping/newlines déterministes
Markdown  table stable et testable
```

Les budgets de lignes et d'octets sont vérifiés explicitement.

## Surfaces

CLI, MCP et HTTP exposent les mêmes intentions métier via les mêmes services applicatifs : exécuter une requête, gérer/exécuter une saved view et exporter un résultat.

Invariant :

```text
surface parity != same transport shape
```

Les représentations transport peuvent différer ; validation, budgets, scope, CAS, tri et export restent définis dans application.

## Conséquences

Positives : langage métier stable et provider-neutral, vues partageables sans matérialisation autoritative, exports déterministes, budgets explicites et convergence des surfaces.

Coûts : registre de champs explicite à maintenir, store/versioning supplémentaire, migration SQLite et tests de déterminisme plus nombreux.

## Validation acquise

L'acceptation repose sur la double qualification exact-head Windows + Linux du même SHA exécutable :

```text
be69e47da0ae209d2246df9c67bc08caeafb2bb0
```

Résultats :

```text
Windows tests              543 PASS
Linux tests                543 PASS
Architecture               221 PASS Windows + Linux
query DSL contract         PASS
saved-view versioning/CAS  PASS
Memory/SQLite parity       PASS
SQLite V014                PASS
canonical JSON export      PASS
CSV export                 PASS
Markdown export            PASS
query/export budgets       PASS
CLI/MCP/HTTP convergence   PASS
SBOM/provenance            PASS Windows + Linux
portable                   PASS Windows + Linux
postGateExecutableDelta    NONE
```

Preuve normative de qualification : [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md).