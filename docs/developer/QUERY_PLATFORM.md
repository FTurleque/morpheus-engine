# M24 — Query Platform architecture

M24 introduit une plateforme de requête provider-neutral dans la couche application de MORPHEUS. Elle couvre le DSL, l’exécution déterministe, les saved views versionnées, l’export/reporting et l’exposition cohérente via CLI, MCP et HTTP.

Qualification de référence :

```text
Executable SHA  be69e47da0ae209d2246df9c67bc08caeafb2bb0
Windows         543 tests / 221 architecture / PASS
Linux           543 tests / 221 architecture / PASS
SQLite          V014
ADR             ADR-0092
```

## 1. Frontière d’architecture

```text
CLI / MCP / HTTP
       |
       v
morpheus-application/query
       |
       +--> store ports
       |
       v
morpheus-domain + published snapshots
       ^
       |
Memory / SQLite adapters
```

Invariants :

```text
adapters -> application -> domain
application -X-> adapters
DSL != SQL passthrough
provider-specific types -X-> query model
transport types -X-> query model
```

Le modèle de requête ne dépend ni de SQLite, ni d’OpenSpec, ni de Markdown, ni d’un type MCP/HTTP/CLI.

## 2. AST provider-neutral

Package principal :

```text
com.morpheus.application.query.dsl
```

Types structurants :

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
QuerySortDirection
QueryProjection
QueryPage
QueryDiagnostic
QueryResult
```

`QueryDslParser` centralise la syntaxe textuelle consommée par les surfaces. `QueryValidator` vérifie champs, types, opérateurs, scope et budgets avant accès aux stores.

## 3. Registre de champs

`QuerySchemaRegistry` est un registre fermé et typé par `QueryEntityType`.

Chaque champ décrit :

```text
nom public
logical type
opérateurs autorisés
```

Le registre empêche d’exposer accidentellement :

```text
noms de tables/colonnes
fragments SQL
chemins provider
structures transport
champs internes non contractuels
```

Un ajout de champ public doit être traité comme une évolution de contrat : registre, tests, surfaces et documentation doivent converger.

## 4. Parsing et validation

Syntaxe supportée :

```text
field eq value
field neq value
field contains value
field starts-with value
field ends-with value
field in [a,b]
field exists
and(...)
or(...)
not(...)
```

Le parser applique déjà les limites d’expression, de nœuds AST et de profondeur ; le validateur applique également les limites de prédicats, tri, projection et pagination.

L’absence/null reste distincte de chaîne vide. `EXISTS` teste la présence, sans coercition implicite.

## 5. Exécution déterministe

`QueryExecutionService` :

- valide la requête avant tout accès store ;
- lit l’état publié CURRENT ;
- supporte scope projet et scope portfolio ;
- préserve `ProjectSpecificationId` dans les résultats portfolio ;
- applique les filtres booléens ;
- applique un tri stable ;
- complète le tri par un tie-break canonique `projectId` puis `entityId` ;
- applique projection et pagination ;
- retourne `totalMatches` et `hasMore`.

La déterminisme observable ne dépend jamais de l’ordre SQLite, provider ou `HashMap`.

## 6. Vues transport-safe

`QueryPublicViews` convertit les résultats en records adaptés aux surfaces. Les adapters ne sérialisent pas directement les objets domaine ni les identités internes.

Ce principe prolonge ADR-0047 :

```text
domain/application result
        |
        v
transport-safe projection
        |
        v
canonical JSON / CLI / MCP / HTTP
```

## 7. Saved views

Package :

```text
com.morpheus.application.query.saved
```

Types :

```text
SavedViewId
SavedViewDefinition
SavedViewVersion
SavedViewStatus
SavedViewService
SavedViewConflictException
```

Port :

```text
com.morpheus.application.store.SavedViewStore
```

Adapters :

```text
MemorySavedViewStore
SqliteSavedViewStore
```

Invariant :

```text
saved view != materialized truth
```

Une saved view stocke une `QueryDefinition` et ses métadonnées. `execute` relit toujours la vérité publiée courante.

Les updates utilisent `expectedRevision`. Une révision obsolète lève un conflit explicite. L’identité est `SavedViewId`, jamais le nom.

## 8. SQLite V014

Migration additive :

```text
V014__saved_views.sql
```

Elle ajoute :

```text
saved_views
saved_view_versions
```

avec indexes de lookup scope/name et versions.

Le store SQLite encode la `QueryDefinition` avec `QueryDefinitionCodec`, format déterministe binaire + Base64. Il ne s’agit ni de Java serialization ni d’un JSON libre arbitraire.

Les migrations historiques V001→V013 ne sont pas réécrites.

## 9. Export/reporting

Package :

```text
com.morpheus.application.query.export
```

Types principaux :

```text
QueryExportFormat
QueryExport
QueryExportBudgetPolicy
QueryExportBudgetException
QueryExportView
QueryReportFormatter
QueryExportService
```

Formats :

```text
JSON      CanonicalJsonSerializer
CSV       UTF-8 + quoting/escaping déterministes
Markdown  table déterministe
```

Invariant :

```text
export != mutation
```

`QueryExportService` lit les pages nécessaires et rejette explicitement un résultat dépassant le nombre maximal de lignes ou la taille maximale en octets. Il ne tronque pas silencieusement un export.

## 10. Budgets centralisés

`QueryBudgets` définit :

```text
MAX_ENCODED_EXPRESSION_BYTES = 16 KiB
MAX_AST_NODES                = 128
MAX_BOOLEAN_DEPTH            = 8
MAX_FILTERS                  = 64
MAX_SORT_FIELDS              = 8
MAX_PROJECTION_FIELDS        = 32
MAX_PAGE_SIZE                = 500
MAX_EXPORT_ROWS              = 10_000
MAX_EXPORT_BYTES             = 10 MiB
MAX_SAVED_VIEWS_PER_SCOPE    = 250
MAX_SAVED_VIEW_NAME          = 160
```

Les surfaces doivent déléguer à ces règles, pas recopier des politiques métier concurrentes.

## 11. CLI

Adapter :

```text
MorpheusQueryCli
```

Familles :

```text
query execute
views create/list/get/versions/update/archive/execute
export query/view
```

La CLI parse les arguments de transport puis construit le même `QueryDefinition` que les autres surfaces.

## 12. MCP

Adapter :

```text
MorpheusQueryMcpTools
```

Tools :

```text
execute_query
create_saved_view
list_saved_views
get_saved_view
list_saved_view_versions
update_saved_view
archive_saved_view
execute_saved_view
export_query
export_saved_view
```

Les JSON Schemas sont fermés (`additionalProperties=false`) et expriment les budgets publics pertinents.

## 13. HTTP

Services/routes :

```text
MorpheusQueryApiService
MorpheusQueryHttpRoutes
```

Routes principales :

```text
POST /api/v1/queries/execute
GET  /api/v1/saved-views
POST /api/v1/saved-views
GET  /api/v1/saved-views/{id}
PUT  /api/v1/saved-views/{id}
GET  /api/v1/saved-views/{id}/versions
POST /api/v1/saved-views/{id}/execute
POST /api/v1/saved-views/{id}/archive
POST /api/v1/saved-views/{id}/export
POST /api/v1/exports
```

Le mapper HTTP rejette propriétés inconnues et trailing tokens. Les erreurs distinguent validation, budget, conflit de révision et conflit d’état.

Les exports sont retournés avec leur média type natif, sans wrapper JSON arbitraire.

OpenAPI M24 : [`../openapi/morpheus-v1-query-m24.yaml`](../openapi/morpheus-v1-query-m24.yaml).

## 14. Convergence des surfaces

`contracts/public-surfaces.tsv` porte les intentions publiques M24. La convergence signifie :

```text
même intention métier
même validation
mêmes budgets
même scope
même CAS saved-view
même déterminisme
mêmes règles d’export
```

Elle ne signifie pas que CLI, MCP et HTTP doivent avoir la même représentation transport.

## 15. Tests M24

Contrats principaux :

```text
QueryDslContractTest
QueryDslParserContractTest
QueryExecutionContractTest
QueryExportContractTest
QueryNullSemanticsContractTest
QueryPlatformArchitectureTest
SavedViewContractTest
SavedViewPersistenceParityTest
MorpheusQueryCliTest
MorpheusQueryMcpToolsTest
MorpheusQueryApiContractTest
SqliteSchemaMigrationTest
```

Le gate exact-head qualifié a produit :

```text
Windows  543 tests / 221 architecture
Linux    543 tests / 221 architecture
```

## 16. Modifier la plateforme

Toute évolution doit préserver l’ordre suivant :

```text
1. définir le contrat métier
2. étendre AST/registre/budgets si nécessaire
3. étendre validation
4. étendre service application
5. implémenter les ports/adapters
6. ajouter tests de déterminisme/parité
7. mettre à jour manifeste/OpenAPI si le contrat public change
8. qualifier exact-head Windows + Linux
```

Une modification post-gate de code, POM, contrat runtime, packaging ou validateur invalide la preuve M24.

## Références

- [`../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md`](../adr/0092-provider-neutral-query-dsl-saved-views-reporting.md)
- [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md)
- [`../roadmap/M24_EXECUTION.md`](../roadmap/M24_EXECUTION.md)
- [`../openapi/morpheus-v1-query-m24.yaml`](../openapi/morpheus-v1-query-m24.yaml)
- [`../../contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv)