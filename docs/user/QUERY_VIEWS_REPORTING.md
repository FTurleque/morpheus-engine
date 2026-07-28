# Query DSL, Saved Views & Reporting — guide utilisateur

M24 ajoute une couche de requête métier provider-neutral à MORPHEUS. Elle permet d’interroger un projet ou un portfolio, de sauvegarder une définition de requête versionnée et d’exporter le résultat sans exposer SQL, SQLite ou un format provider.

## Principes

```text
DSL != SQL passthrough
saved view != materialized truth
export != mutation
portfolio result preserves ProjectSpecificationId
stale saved-view revision != silent overwrite
```

Une saved view mémorise **la définition** de la requête. Son exécution relit l’état publié courant ; elle n’est donc pas une copie figée des résultats.

## Entités interrogeables

Le DSL travaille sur des types métier déclarés explicitement :

```text
REQUIREMENT
SPECIFICATION
SCENARIO
CHANGE
CONSTRAINT
DESIGN_DECISION
TASK
ACCEPTANCE_CRITERION
EVIDENCE
PORTFOLIO_MEMBERSHIP
PORTFOLIO_REFERENCE
```

Les champs disponibles dépendent du type d’entité. Un champ inconnu, SQL, provider-specific ou transport-specific est rejeté avant l’exécution.

## Scopes

Une requête porte toujours un scope explicite :

```text
project   -> ProjectSpecificationId
portfolio -> PortfolioId
```

Une ligne issue d’un portfolio conserve son `ProjectSpecificationId`, afin que l’origine projet reste observable.

## Filtres

Opérateurs disponibles :

```text
eq
neq
contains
starts-with
ends-with
in
exists
```

Composition booléenne :

```text
and(...)
or(...)
not(...)
```

Exemples conceptuels :

```text
title contains "session"
status eq CURRENT
and(title contains "session", status eq CURRENT)
or(priority eq HIGH, priority eq CRITICAL)
not(description exists)
status in [CURRENT,PROPOSED]
```

Les valeurs absentes/nulles restent distinctes d’une chaîne vide. `exists` teste la présence du champ.

## Exécuter une requête en CLI

Projet :

```bash
morpheus query execute \
  --project <projectId> \
  --entity requirement \
  --filter 'title contains "session"' \
  --sort title:asc \
  --fields id,title,status \
  --offset 0 \
  --limit 50
```

Portfolio :

```bash
morpheus query execute \
  --portfolio <portfolioId> \
  --entity requirement \
  --filter 'status eq CURRENT' \
  --sort title:asc \
  --limit 100
```

Pour une sortie machine-readable, utiliser la convention globale MORPHEUS :

```bash
morpheus --json query execute --project <projectId> --entity requirement --limit 50
```

## Tri, projection et pagination

Le tri demandé est déterministe. MORPHEUS ajoute un tie-break canonique par identité afin que le résultat ne dépende pas de l’ordre SQLite, d’un `HashMap` ou du provider.

La projection limite les champs retournés, mais les identités nécessaires à l’interprétation restent présentes.

La pagination expose :

```text
offset
limit
totalMatches
hasMore
```

La taille maximale d’une page est `500`. Un dépassement est rejeté explicitement.

## Saved views

Créer une vue :

```bash
morpheus views create \
  --name "Requirements courants" \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT' \
  --sort title:asc \
  --fields id,title,status
```

Lister les vues :

```bash
morpheus views list --project <projectId>
```

Lire une vue :

```bash
morpheus views get --id <savedViewId>
```

Voir l’historique :

```bash
morpheus views versions --id <savedViewId>
```

Mettre à jour avec CAS :

```bash
morpheus views update \
  --id <savedViewId> \
  --expected-revision <revision> \
  --name "Requirements courants — triés" \
  --sort title:asc
```

Une `expectedRevision` obsolète échoue ; MORPHEUS ne remplace jamais silencieusement une version plus récente.

Archiver :

```bash
morpheus views archive --id <savedViewId> --expected-revision <revision>
```

Exécuter :

```bash
morpheus views execute --id <savedViewId>
```

Deux saved views peuvent avoir le même nom : leur identité reste `SavedViewId`, indépendante du nom.

## Exports

Formats disponibles :

```text
json
csv
markdown
```

Exporter directement une requête :

```bash
morpheus export query \
  --format json \
  --project <projectId> \
  --entity requirement \
  --filter 'status eq CURRENT'
```

Exporter une saved view :

```bash
morpheus export view --id <savedViewId> --format csv
```

JSON est canonique et transport-safe. CSV est UTF-8 avec ordre de colonnes et escaping déterministes. Markdown produit une table stable et testable.

L’export est read-only : il ne modifie ni snapshot, ni lifecycle, ni saved view.

## Budgets

```text
expression encodée       16 KiB max
nœuds AST                128 max
profondeur booléenne     8 max
prédicats                 64 max
champs de tri             8 max
champs de projection     32 max
taille de page           500 max
lignes exportées          10 000 max
taille export             10 MiB max
saved views par scope     250 max
nom de saved view         160 caractères max
```

Les limites ne provoquent jamais de troncature sémantique silencieuse. Une requête ou un export hors budget échoue explicitement.

## MCP et HTTP

Les mêmes intentions sont disponibles via MCP STDIO et HTTP `/api/v1` :

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

Routes HTTP principales :

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

Le contrat machine-readable M24 est `docs/openapi/morpheus-v1-query-m24.yaml`.

## Garanties

- aucune syntaxe SQL n’est un contrat public du DSL ;
- aucune identité projet n’est dérivée d’un chemin ou d’un repository ;
- aucun résultat portfolio ne perd son scope projet ;
- aucun ordre observable ne dépend de l’itération d’un store/provider ;
- aucune saved view ne devient une vérité matérialisée ;
- aucune mise à jour concurrente n’est transformée en last-write-wins silencieux ;
- aucun export ne déclenche une mutation métier ;
- aucun dépassement de budget n’est présenté comme un résultat complet.

Preuve de qualification : [`../validation/VALIDATION_M24.md`](../validation/VALIDATION_M24.md).