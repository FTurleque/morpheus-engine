# ADR-0107 — Un seul vocabulaire pour une réponse paginée, et une page embarquée est une valeur

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 23 septembre 2026
- Dépend de : ADR-0043 (requête lexicale déterministe), ADR-0045 (requêtes de contenu métier déterministes),
  ADR-0047 (vues compactes et JSON canonique), ADR-0103 (le mécanisme d'enforcement se choisit sur l'intention),
  ADR-0106 (§2 de l'amendement : pagination de `get_current_specification`)
- Portée : `morpheus-mcp`, `morpheus-api` — `PagedEnvelope` (une copie par module),
  `PagedResponseVocabularyArchitectureTest`

## Contexte

L'audit de code du 23/09/2026 (constat **MCP-8**) a relevé que l'enveloppe d'une réponse paginée était construite
à la main dans neuf endroits répartis sur sept classes de `morpheus-mcp` et `morpheus-api`. Neuf fois la même
convention — `offset`, `limit`, `totalMatches`, `hasMore`, `items` — et aucun test pour la tenir.

Relue site par site, la convention n'était même pas tenue par les neuf : `find_requirements` côté MCP rendait
`totalMatches` et `hasMore` **sans** `offset` ni `limit`, là où sa jumelle HTTP (`GET …/requirements`) les rend.

Le dixième producteur, ajouté par l'amendement d'ADR-0106 à `get_current_specification`, s'en écartait
franchement : `specificationCount` pour le total, `specifications` pour les éléments, `offset`, `limit` et
`hasMore` à plat parmi les identifiants et les compteurs du snapshot. La même collection, dans le même ordre et
avec la même projection, est servie en HTTP par `GET /projects/{projectId}/specifications` sous l'enveloppe
canonique : deux transports nommaient différemment la même grandeur de la même page.

L'écart n'était pas une distraction. L'objet de `get_current_specification` est un résumé qui embarque une page ;
`items` y serait ambigu à côté de quatre compteurs, et `specificationCount` se lit bien à côté de
`requirementCount`. Renommer à plat aurait réglé la cohérence en cassant la lisibilité.

## Décision

### 1. Le vocabulaire

Une page porte exactement cinq clés, dans cet ordre de construction : `offset`, `limit`, `totalMatches`,
`hasMore`, `items`. Le total d'une page s'appelle `totalMatches` dans tous les transports. Les identifiants de la
réponse (`snapshotId`, `query`, `primaryProviderId`) précèdent la page et ne réutilisent jamais une de ses clés.

`find_requirements` côté MCP gagne `offset` et `limit`. Le changement est additif et aligne l'outil sur sa jumelle
HTTP.

### 2. Une page embarquée est une valeur, pas des clés à plat

Quand une réponse n'est pas une page mais **contient** une page, la page est l'objet porté par la clé qui nomme sa
collection. `get_current_specification` rend donc :

```text
projectId, snapshotId, snapshotState, specificationVersionId,
specifications: { offset, limit, totalMatches, hasMore, items },
requirementCount, scenarioCount, changeCount, acceptanceCriterionCount
```

`items` n'est plus ambigu (la clé qui le contient le nomme), `specificationCount` disparaît parce que
`totalMatches` le dit déjà, et la page embarquée ne répète pas `snapshotId`, que l'objet englobant porte.
Les quatre compteurs voisins restent où ils sont : ce sont des compteurs de collections **non paginées**, pas des
totaux de page.

La forme existante de `get_specification_context` (MCP et HTTP) — une page `requirements` qui porte son propre
`snapshotId` — est déjà une valeur ; elle n'est pas modifiée ici, pour ne pas déplacer un schéma HTTP publié sans
nécessité.

### 3. Où vit la fabrique : une copie par adaptateur, pas dans `application`

La fabrique `PagedEnvelope` existe **deux fois**, une par module, en classe package-private de même code.

`morpheus-mcp` et `morpheus-api` sont des adaptateurs frères : aucun ne peut appeler l'autre
(`LayerDependencyTest`). Restait `morpheus-application`, que les deux voient. La fabrique n'y va pas, parce
qu'elle ne peut pas y entrer sans y faire entrer de la présentation : son travail est de fusionner des clés de
réponse dans une map, après des identifiants choisis par chaque adaptateur. C'est de la mise en forme de réponse,
pas un cas d'usage ni un port.

Le précédent qui semblait l'y autoriser a été examiné et écarté : `application.query.compact.CompactQueryTypes.
PageMetadata` porte déjà `offset`, `limit`, `totalMatches` et `hasMore` comme composants de record. Mais un record
se sérialise en objet imbriqué : il ne peut pas se fusionner à plat après un `snapshotId`, ce que font huit des dix
producteurs. S'en servir aurait demandé de changer la forme de huit réponses publiques pour loger une fabrique —
l'inverse de l'objectif.

Deux copies au lieu de neuf : l'essentiel du gain est là, et c'est la forme que ce dépôt préfère à une arête de
dépendance nouvelle entre modules sous garde.

### 4. La garde

`PagedResponseVocabularyArchitectureTest` rend la convention exécutable. Intention en une phrase (ADR-0103) :
**aucune réponse paginée n'invente un nom**. Elle vise du texte dans des littéraux de map, pas une dépendance
compilée : la garde est donc textuelle, et vérifie que :

- chaque `PagedEnvelope` épelle exactement les cinq clés, dans l'ordre canonique ;
- les deux copies sont le même code, au nom de package et de module près ;
- hors des deux fabriques, aucune source de production des deux modules n'épelle `"totalMatches"` ni `"hasMore"`
  — toute page porte `hasMore`, donc une page construite à la main est refusée ;
- aucune source des deux modules n'épelle une orthographe concurrente du total (`"specificationCount"`,
  `"totalCount"`, `"totalItems"`, `"totalResults"`, `"total"`, `"count"`). Cette liste est un refus fini, pas une
  preuve : la morsure réelle est la règle précédente ;
- `get_current_specification` porte sa page sous `"specifications"`.

La garde a été cassée pour prouver qu'elle tient : `"totalMatches"` renommé dans la fabrique de `morpheus-api`
fait échouer la règle d'ordre canonique et la règle de copie identique ; un `"hasMore"` réécrit à la main dans
`MorpheusHistoryApiService` fait échouer la règle de construction.

## Conséquences

- La forme de `get_current_specification` change : `specificationCount`, `offset`, `limit` et `hasMore` quittent
  le premier niveau pour l'objet `specifications`. Le changement est interne à `morpheus-mcp` : l'outil n'est porté
  par aucune colonne de `contracts/public-surfaces.tsv` ni par aucun fichier de `docs/openapi/` (vérifié par
  l'amendement d'ADR-0106).
- `find_requirements` côté MCP ajoute `offset` et `limit` à sa réponse.
- Aucune réponse HTTP ne change de forme ; les sept producteurs HTTP rendent les mêmes clés qu'avant.
- `morpheus-cli` ne produit aujourd'hui aucune page à clés `hasMore` et n'est pas couvert par la garde. Le jour où
  il en produit une, l'étendre est une ligne dans `MODULES`, et une troisième copie de la fabrique.
- Les pages sérialisées depuis des records d'`application` (`PageMetadata`, `QueryResult`) portent déjà les mêmes
  noms par leurs composants ; elles sont hors de la portée textuelle de la garde.

## Alternatives écartées

- **Renommer à plat `specificationCount` en `totalMatches`.** Cohérent mais illisible : `totalMatches` et `items`
  au milieu de quatre compteurs ne disent pas de quelle collection ils parlent.
- **Porter la fabrique dans `morpheus-application`.** Voir §3 : de la présentation entrerait dans la couche des
  cas d'usage, ou huit réponses publiques changeraient de forme.
- **Une règle ArchUnit.** Aucune règle de bytecode ne voit le nom d'une clé de map ; ADR-0103 classe ce cas parmi
  les interdits textuels par nature.
