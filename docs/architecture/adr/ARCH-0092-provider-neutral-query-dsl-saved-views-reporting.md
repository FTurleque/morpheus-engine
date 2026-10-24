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
(`MAX_BOOLEAN_DEPTH`), les prédicats feuilles (`MAX_PREDICATES`), les champs de tri (`MAX_SORT_FIELDS`) et de
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
- refuse une profondeur booléenne supérieure à `MAX_BOOLEAN_DEPTH` avant l'appel récursif suivant ;
- soumet la définition reconstruite à `QueryValidator` avant de la retourner ;
- refuse aussi, à l'encodage, une définition invalide ou une représentation qui dépasserait la taille encodée admise.

Il encode la définition de façon déterministe, sans sérialisation Java ni JSON arbitraire. `QueryValidator` arrête son
parcours dès qu'un budget structurel est dépassé, pour qu'un AST construit directement en mémoire ne provoque pas de
récursion non bornée. Les adaptateurs policy HTTP, MCP et CLI décodent les assertions de requête persistées par ce même
`QueryDefinitionCodec` ; aucun décodeur parallèle n'existe.

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

### Don't

- Ne pas accepter de SQL, de nom de table, de chemin provider ni de type transport dans une requête.
- Ne pas laisser l'ordre de stockage, de provider ou d'un `HashMap` déterminer l'ordre d'un résultat.
- Ne pas tronquer un résultat ou un export en le présentant comme complet.
- Ne pas stocker le résultat d'une saved view comme vérité.
- Ne pas écrire de décodeur Base64 de requête dans un adaptateur.
- Ne pas réécrire une migration historique.

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

### Risques

- Un champ ajouté au registre sans liste d'opérateurs fermée rouvrirait des combinaisons non validées. Réponse : la
  validation refuse toute combinaison non déclarée avant exécution.

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

## References

- [ADR-0091](DATA-0091-multi-project-portfolio-intelligence.md) — scope portfolio.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs et assertions de requête.
- [VALIDATION_M24](../../project/validation/VALIDATION_M24.md).
