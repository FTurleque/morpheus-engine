---
id: ARCH-0092
title: "Provider-neutral query DSL, saved views and reporting"
domain: architecture
rules: false
---

# Provider-neutral query DSL, saved views and reporting

- Statut : **Acceptée**
- Date : 2026-07-28

## Context

MORPHEUS dispose de requêtes déterministes spécialisées, d'un JSON canonique transport-safe et d'un scope portfolio.
Il faut pouvoir exprimer une vue métier complexe, la sauvegarder et l'exporter sans transformer SQL, SQLite, un
provider ou un transport en langage métier.

Une définition de requête encodée arrive de l'extérieur (saved view persistée, assertion de requête d'un policy pack) :
son décodage récursif est une surface d'attaque si les budgets ne s'appliquent qu'après coup.

Options écartées :

- **Un export qui honore `offset` et `limit`** : il changerait `QueryExport` sur les trois surfaces, dont les rendus
  CSV et Markdown, et contredirait le contrat publié d'un export, la vue bornée complète.
- **Sauter une saved view illisible dans une liste, ou la réparer au démarrage** : dégradation silencieuse, ou
  réécriture d'une donnée que le système ne sait pas lire.

## Decision

### Modèle

La couche application porte un AST provider-neutral borné, dans `com.morpheus.application.query.dsl` :

```text
QueryDefinition  QueryScope  QueryEntityType
QueryFilter (QueryPredicate | QueryAnd | QueryOr | QueryNot)
QueryOperator  QuerySort  QuerySortDirection  QueryProjection  QueryPage  QueryResult  QueryDiagnostic
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

### Scope, entités et champs

Une requête est explicitement project-scoped ou portfolio-scoped : le scope est un `ProjectSpecificationId` ou un
`PortfolioId`, jamais un workspace, un dépôt, un provider ou un nom de table.

Le DSL n'adresse que des `QueryEntityType` et des champs déclarés par `QuerySchemaRegistry`. Chaque champ a un type
logique et une liste fermée d'opérateurs autorisés. Noms SQL, détails de stockage, chemins provider et structures
transport ne sont pas adressables.

### Filtres

Les opérateurs de prédicat sont l'ensemble fermé de `QueryOperator` (`EQ`, `NEQ`, `CONTAINS`, `STARTS_WITH`,
`ENDS_WITH`, `IN`, `EXISTS`) ; `AND`, `OR` et `NOT` sont les nœuds `QueryAnd`, `QueryOr`, `QueryNot`. Une combinaison
champ/opérateur/type incompatible produit un diagnostic explicite et empêche l'exécution. Absence/null est distincte
de la chaîne vide ; `EXISTS` teste la présence sans coercition implicite.

### Tri, projection, pagination

Le tri observable est stable : un tie-break canonique par `projectId` puis `entityId` complète les clés demandées.
Aucun résultat ne dépend de l'ordre SQLite, provider ou `HashMap`.

La projection limite les champs transport-safe mais préserve les identités nécessaires à l'interprétation ; un
résultat portfolio conserve son `ProjectSpecificationId`. La pagination offset/limit expose `totalMatches` et
`hasMore` ; une page hors budget est rejetée explicitement.

### Budgets

Tous les budgets sont des constantes de `QueryBudgets` (valeurs à lire dans la classe), testées et exposées par les
contrats publics pertinents. Ils couvrent au moins : la taille de l'expression encodée
(`MAX_ENCODED_EXPRESSION_BYTES`), les nœuds de l'AST (`MAX_AST_NODES`), la profondeur booléenne
(`MAX_BOOLEAN_DEPTH`), les prédicats feuilles (`MAX_PREDICATES`), les valeurs d'un prédicat
(`MAX_PREDICATE_VALUES`, distinct de `MAX_PREDICATES`), les champs de tri (`MAX_SORT_FIELDS`) et de
projection (`MAX_PROJECTION_FIELDS`), la taille de page (`MAX_PAGE_SIZE`), les lignes et octets d'export
(`MAX_EXPORT_ROWS`, `MAX_EXPORT_BYTES`), les saved views par scope (`MAX_SAVED_VIEWS_PER_SCOPE`) et la longueur d'un
nom de saved view (`MAX_SAVED_VIEW_NAME`). Un dépassement ne produit jamais un résultat partiel présenté comme
complet.

### Décodage d'une définition encodée

Une définition encodée est non fiable et suit un ordre imposé :

```text
untrusted encoded query -> size check -> bounded decode -> semantic validation -> QueryDefinition
```

`QueryDefinitionCodec` :

- refuse une représentation encodée au-delà de `MAX_ENCODED_EXPRESSION_BYTES` avant le décodage Base64 ;
- compte globalement chaque nœud de l'AST pendant la désérialisation et refuse celui qui dépasse `MAX_AST_NODES` ;
- compte globalement les prédicats et refuse celui qui dépasse `MAX_PREDICATES` ;
- refuse un prédicat dont le nombre de valeurs dépasse `MAX_PREDICATE_VALUES` ;
- refuse une profondeur booléenne supérieure à `MAX_BOOLEAN_DEPTH` avant l'appel récursif suivant ;
- soumet la définition reconstruite à `QueryValidator` avant de la retourner ;
- refuse aussi, à l'encodage, une définition invalide ou une représentation qui dépasserait la taille encodée admise.

Il encode la définition de façon déterministe, sans sérialisation Java ni JSON arbitraire. `QueryValidator` arrête son
parcours dès qu'un budget structurel est dépassé, pour qu'un AST construit directement en mémoire ne provoque pas de
récursion non bornée. Les adaptateurs policy HTTP, MCP et CLI décodent les assertions de requête persistées par ce même
`QueryDefinitionCodec` ; aucun décodeur parallèle n'existe.

Ce que le système accepte d'écrire, il sait le relire : `MAX_PREDICATE_VALUES` est appliqué par le parseur (message
qui nomme la borne et le champ), par le validateur, donc par l'encodeur, et par le décodeur.

### Saved views

`SavedViewId`, `SavedViewDefinition`, `SavedViewVersion`, `SavedViewStatus`, le port `SavedViewStore` et
`SavedViewService` portent les vues sauvegardées.

```text
saved view != materialized truth
```

Une saved view stocke la définition de requête et ses métadonnées, jamais une copie autoritative du résultat ; elle
s'exécute contre la vérité publiée courante. `SavedViewId` est stable et indépendant du nom. Une mise à jour porte
`expectedRevision` (CAS) : une révision obsolète échoue explicitement, sans last-write-wins silencieux, et l'historique
des versions reste lisible.

`SavedViewStore` est implémenté par le store mémoire et par SQLite. SQLite n'ajoute que la migration additive
`V014__saved_views.sql` ; aucune migration historique n'est réécrite.

Une liste nomme ce qu'elle n'a pas pu lire. `SavedViewStore.list` rend des `SavedViewEntry`, lisibles ou illisibles
(identifiant, nom, révision, statut, dates, raison de l'échec de décodage) : une ligne indécodable ne fait pas échouer
la liste et n'en est pas omise. Les vues publiques la rendent comme `UnreadableSavedViewView` avec `unreadableReason`,
sans `query`, et la forme d'une vue lisible ne change pas. `SavedViewStore.archive` change le statut sans décoder la
définition, pour qu'une vue illisible puisse être retirée.

### Export et reporting

```text
export != mutation
```

Les exports sont read-only :

| Format | Forme |
|---|---|
| JSON | projections transport-safe sérialisées par `CanonicalJsonSerializer` |
| CSV | UTF-8, colonnes, quoting, échappement et fins de ligne déterministes |
| Markdown | table stable et testable |

Les budgets de lignes et d'octets sont vérifiés explicitement.

**Un export est complet par contrat, sur les trois surfaces, et un paramètre de page y est refusé**, là où chaque
surface refuse déjà (un paramètre publié est honoré ou refusé, ADR-0102) :

- **MCP** : le schéma d'`export_query` ne déclare que le périmètre, `entity`, `filter`, `sort`, `fields` et `format` ;
  `offset` et `limit` sont refusés par le validateur du SDK. La description de l'outil dit que l'export est toujours
  complet, borné par `QueryBudgets.MAX_EXPORT_ROWS`, et ne prend ni `offset` ni `limit` ; celle d'`export_saved_view`
  dit qu'il ignore la page stockée avec la vue.
- **CLI** : `export query` n'accepte ni `--offset` ni `--limit`, et le refus dit pourquoi
  (`--limit is not accepted by export: an export is always complete…`) ; l'aide le dit aussi.
- **HTTP** : `ExportRequest.query` est un `ExportQueryRequest` (`entity`, `filter`, `sort`, `fields`), fermé par
  `additionalProperties: false` dans `docs/developer/reference/openapi/morpheus-v1-query-m24.yaml` ; `limit` et `offset`
  y sont refusés en `400` par le décodeur strict.
- **Saved view** : `export view` et `POST /api/v1/saved-views/{id}/export` ignorent la page stockée avec la vue, et le
  disent.

Un export valide plus grand que le cadre STDIO MCP (`BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES`,
inférieur à `QueryBudgets.MAX_EXPORT_BYTES`) n'est pas rendu par `export_query` ni par `export_saved_view` : le
transport le refuse par `MCP_RESPONSE_TOO_LARGE`, avec le conseil `MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE`, qui
ne peut pas être suivi sur un export. Les recours sont `execute_query` paginé (mêmes lignes, sans l'enveloppe d'export
ni ses rendus CSV et Markdown), le CLI (`export query`, `export view`) et HTTP (`POST /api/v1/exports`,
`POST /api/v1/saved-views/{id}/export`). `export_saved_view` n'a pas de recours MCP paginé. La description
d'`export_query` le dit à l'agent.

### Surfaces

CLI, MCP et HTTP exposent les mêmes intentions métier par les mêmes services applicatifs : exécuter une requête,
gérer et exécuter une saved view, exporter un résultat.

```text
surface parity != same transport shape
```

Les représentations transport peuvent différer ; validation, budgets, scope, CAS, tri et export restent définis dans
l'application.

## Do's and Don'ts

### Do

- Déclarer tout champ interrogeable dans `QuerySchemaRegistry`, avec son type logique et ses opérateurs autorisés.
- Lire un budget dans `QueryBudgets`, jamais dans un littéral local.
- Faire passer tout décodage d'une définition encodée par `QueryDefinitionCodec`.
- Exiger `expectedRevision` sur toute mise à jour d'une saved view.
- Ajouter une évolution de schéma SQLite par une nouvelle migration additive.
- Appliquer la même borne à l'écriture et à la relecture d'une définition persistée.
- Rendre dans une liste de saved views les entrées illisibles, marquées comme telles.
- Refuser, sur les trois surfaces, un paramètre de page passé à un export.

### Don't

- Ne pas accepter de SQL, de nom de table, de chemin provider ni de type transport dans une requête.
- Ne pas laisser l'ordre de stockage, de provider ou d'un `HashMap` déterminer l'ordre d'un résultat.
- Ne pas tronquer un résultat ou un export en le présentant comme complet.
- Ne pas stocker le résultat d'une saved view comme vérité.
- Ne pas écrire de décodeur Base64 de requête dans un adaptateur.
- Ne pas réécrire une migration historique.
- Ne pas sauter ni réparer silencieusement une saved view illisible.

## Consequences

### Positives

- Langage métier stable et provider-neutral.
- Vues partageables sans matérialisation autoritative.
- Exports déterministes, budgets explicites, convergence des surfaces.
- Une définition encodée hostile est refusée avant toute récursion non bornée.

### Négatives

- Registre de champs explicite à maintenir.
- Store et versioning supplémentaires, migration SQLite.
- Tests de déterminisme plus nombreux.
- Un export plus grand que le cadre MCP n'est atteignable qu'en lignes paginées (`execute_query`) ou hors MCP.

### Risques

- Un champ ajouté au registre sans liste d'opérateurs fermée rouvrirait des combinaisons non validées. Réponse : la
  validation refuse toute combinaison non déclarée avant exécution.
- `versions(id)` d'une saved view illisible échoue.
- Les recours CLI et HTTP pour un export plus grand que le cadre MCP sont établis par lecture du code, pas mesurés.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m24/QueryPlatformArchitectureTest.java` :
  la plateforme de requête de l'application ne dépend ni d'un transport ni d'un store concret ; le manifeste et
  l'OpenAPI exposent les mêmes familles d'intentions.
- Dans le même répertoire `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m24/` :
  - `QueryDslContractTest`, `QueryDslParserContractTest`, `QueryNullSemanticsContractTest` : champs et opérateurs
    fermés, budgets d'AST, de prédicats, de taille et de profondeur, refus du SQL, absence distincte du vide ;
  - `QueryExecutionContractTest` : tri, projection et pagination déterministes, étanchéité du scope projet ;
  - `QueryExportContractTest` : exports JSON, CSV et Markdown déterministes, budgets d'export en échec explicite ;
  - `SavedViewContractTest`, `SavedViewPersistenceParityTest`, `SavedViewBudgetConcurrencyTest` : identité
    indépendante du nom, CAS, historique, exécution de la définition, parité mémoire/SQLite, budget atomique.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/AuditHardeningWorkflowContractTest.java`
  (`policyAdaptersCannotBypassBoundedQueryDefinitionCodec`) : ordre de décodage du codec, arrêt du validateur, et
  usage du codec partagé par les adaptateurs policy HTTP, MCP et CLI.
- `morpheus-application/src/test/java/com/morpheus/application/query/dsl/QueryDefinitionCodecBudgetTest.java` :
  charges adversariales (imbrication profonde de `NOT`, dépassement global de nœuds et de prédicats, taille encodée).
- `morpheus-application/src/test/java/com/morpheus/application/query/dsl/QueryPredicateValuesBudgetTest.java`,
  `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSavedViewUnreadableRowTest.java`,
  `morpheus-application/src/test/java/com/morpheus/application/query/dsl/QueryPublicViewsUnreadableTest.java` :
  symétrie écriture-lecture du budget de valeurs, liste et archivage d'une saved view illisible.
- Export complet : `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusQueryMcpToolsTest.java`
  (`anExportPastTheFrameIsRefusedByTheTransportAndItsRowsAreReadThroughPagedQueries`),
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusQueryCliTest.java`,
  `morpheus-api/src/test/java/com/morpheus/api/MorpheusQueryApiContractTest.java`,
  `morpheus-application/src/test/java/com/morpheus/application/query/export/QueryExportMaterializationTest.java`
  (`anExportIgnoresThePageOfItsDefinitionAndIsComplete`).

## References

- [ADR-0091](DATA-0091-multi-project-portfolio-intelligence.md) — scope portfolio.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs et assertions de requête.
- [ADR-0102](BE-0102-mcp-failure-contract-is-one-rule.md) — un schéma d'entrée ne déclare que ce que le handler honore.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — ce qui n'est pas observé est dit.
- [VALIDATION_M24](../../project/validation/VALIDATION_M24.md).
