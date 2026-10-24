---
id: BE-0131
title: "Une page parle un seul vocabulaire, et un journal est conservé et lu par pages"
domain: backend
rules: false
---

# Une page parle un seul vocabulaire, et un journal est conservé et lu par pages

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0107, ADR-0116

## Context

Plusieurs réponses de `morpheus-mcp` et de `morpheus-api` sont des pages. Construites à la main, elles dérivent : une
même collection servie par deux transports nomme alors différemment le même total, ou omet `offset` et `limit` d'un
côté. Une réponse résumée qui embarque une page à plat parmi ses propres compteurs rend `items` ambigu.

Certaines collections ne décroissent jamais : la lignée des versions publiées d'un projet, l'audit de configuration des
policies, les versions d'un pack de policies, les révisions d'une vue enregistrée, l'audit des mutations de cycle de vie
et les archives de synchronisation. Aucune instruction du produit ne supprime de ces trois derniers journaux. Rendue
entière, une telle collection finit par dépasser le cadre MCP (`BoundedStdioServerTransportProvider` remplace alors la
réponse par `MCP_RESPONSE_TOO_LARGE` et conseille de paginer) ou la taille de réponse amont que le serveur remote
accepte (`MorpheusRemoteHttpServer.MAX_PROXY_RESPONSE_BYTES`, `502 UPSTREAM_RESPONSE_TOO_LARGE`), sans recours.

Options écartées :

- **Renommer à plat `specificationCount` en `totalMatches`** : cohérent, mais illisible au milieu des compteurs voisins.
- **Porter la fabrique de page dans `morpheus-application`** : de la mise en forme de réponse entrerait dans la couche
  des cas d'usage ; un record d'application se sérialise en objet imbriqué et changerait la forme des réponses à plat.
- **Une règle ArchUnit** : aucune règle de bytecode ne voit le nom d'une clé de map.
- **Un curseur** (`?after=…`) : plus fidèle à une collection en ajout seul, mais une troisième forme de page.
- **Un second vocabulaire, ou une clé `count`** : la copie qui dérive.
- **Borner la lignée côté domaine** : la rétention `KEEP_ALL_PUBLISHED` est une décision ; seule la restitution est
  en cause.
- **Trier l'audit par `ORDER BY at, id`** : `at` est un instant en texte dont l'ordre lexicographique n'est pas l'ordre
  chronologique quand le nombre de décimales varie.
- **Paginer les journaux sans lecteur** : il faudrait concevoir un ordre pour aucun appelant.
- **Purger un journal** : une purge est une décision sur ce que le produit garde comme preuve, pas un correctif.

## Decision

### 1. Le vocabulaire d'une page

Une page porte exactement cinq clés, dans cet ordre : `offset`, `limit`, `totalMatches`, `hasMore`, `items`. Le total
d'une page s'appelle `totalMatches` sur tous les transports. Les identifiants de la réponse (`snapshotId`, `query`,
`primaryProviderId`…) précèdent la page et ne réutilisent jamais une de ses clés. Aucune orthographe concurrente du
total (`specificationCount`, `totalCount`, `totalItems`, `totalResults`, `total`, `count`) n'est employée.

### 2. Une page embarquée est une valeur

Quand une réponse **contient** une page, la page est l'objet porté par la clé qui nomme sa collection. Ainsi
`get_current_specification` rend ses spécifications sous `specifications: { offset, limit, totalMatches, hasMore,
items }`, et ses compteurs de collections non paginées (`requirementCount`, `scenarioCount`, `changeCount`,
`acceptanceCriterionCount`) restent à côté. La page embarquée ne répète pas les identifiants de l'objet englobant.

### 3. Une fabrique par adaptateur

La fabrique `PagedEnvelope` existe une fois dans `morpheus-mcp` et une fois dans `morpheus-api`, en classe
package-private de même code au nom de package près. Les deux adaptateurs sont frères et ne s'appellent pas ; la
fabrique n'entre pas dans `morpheus-application`. Aucune source de production de ces deux modules n'écrit
`"totalMatches"` ou `"hasMore"` hors de sa fabrique.

### 4. Une collection en ajout seul est restituée par page, dans un ordre total

L'ordre d'une page est total et stable ; aucun curseur n'est offert. Une page lue pendant qu'un élément s'ajoute peut
glisser, et `totalMatches`, rendu à chaque page, permet de le constater. Les arguments de page sont validés avant la
lecture de la collection.

- **La lignée des versions d'un projet** (`GET /api/v1/projects/{projectId}/versions`) est rendue par page, du plus
  ancien au plus récent : une publication s'ajoute en fin et ne décale aucune page lue. La projection (deux lectures de
  store par élément) est bornée par `limit` ; la lignée est parcourue en entier pour connaître `totalMatches`.
- **L'audit de configuration des policies est paginé au store**, sur les trois transports qui le servent
  (`get_policy_audit`, `GET /api/v1/policy-packs/{id}/audit`, `policy audit`), avec le même vocabulaire et les mêmes
  bornes. `PolicyPackStore.listAudit(packId, PageRequest)` rend une `PolicyAuditPage` ; le port n'a pas de lecture du
  journal entier. Le store SQLite ne construit que les enregistrements de la page, dans l'ordre de la colonne
  `policy_audit.at_key` (l'instant en texte de largeur fixe) puis de l'identifiant, sur un index qui la suit.
- **Un outil MCP sur une collection croissante prend une page.** `PageArguments` lit et déclare ses bornes depuis les
  constantes de `MorpheusMcpToolCatalog` : le schéma publie exactement les bornes que le handler applique, et
  `PageArguments.MAX_LIMIT` est égal à `PageRequest.MAX_LIMIT`. Tout outil servi par le câblage par défaut déclare
  `offset` et `limit`, ou figure dans une liste d'exemptions avec sa raison (`ONE_RESULT`, `BOUNDED_IN_COUNT`,
  `UNBOUNDED_ACKNOWLEDGED`). Une borne en nombre d'éléments ne promet rien de la taille d'une réponse.

### 5. Les collections encore découpées en mémoire

Les versions d'un pack de policies et les révisions d'une vue enregistrée sont paginées sur MCP seulement
(`list_policy_pack_versions`, `list_saved_view_versions`), par `PagedEnvelope.slice` sur une liste déjà lue en entier :
la borne limite la réponse, pas le coût de lecture. Sur HTTP (`GET /api/v1/policy-packs/{id}/versions`,
`GET /api/v1/saved-views/{id}/versions`) et sur la CLI (`policy pack versions`, `views versions`), elles sont rendues
entières. Les paginer partout est une décision qui reste à prendre ; elle change un contrat public.

### 6. Un journal est conservé pour la vie de la base

Aucune instruction du produit ne supprime de `policy_audit`, de `change_lifecycle_mutation_audit` ni de
`sync_source_archives`. L'opérateur dispose de la sauvegarde et de la restauration hors ligne. Une purge exigerait une
taille mesurée qui pose un problème réel, ou une obligation écrite, et une décision qui remplace celle-ci.

Les deux journaux sans lecteur, `ChangeLifecycleMutationStore.listAudit` et `SyncStateStore.listArchives`, rendent
toutes les lignes d'un change ou d'un projet. Aucune classe de production ne les appelle ; la surface qui en aura besoin
les borne d'abord. Les archives de synchronisation continuent d'être écrites : elles sont la trace qu'une source a
disparu.

## Do's and Don'ts

### Do

- Construire toute page par `PagedEnvelope`, avec les cinq clés dans l'ordre canonique.
- Placer une page embarquée sous la clé qui nomme sa collection.
- Garder les deux copies de `PagedEnvelope` identiques au nom de package près.
- Donner `offset` et `limit` à tout outil MCP sur une collection croissante, ou l'exempter avec sa raison.
- Déclarer dans le schéma d'un outil exactement les bornes que son handler applique.
- Paginer au store un journal qu'une surface lit, dans un ordre que la base peut suivre.
- Conserver les journaux pour la vie de la base.

### Don't

- Ne pas écrire `"totalMatches"` ou `"hasMore"` à la main hors des fabriques, ni une orthographe concurrente du total.
- Ne pas déplacer la fabrique de page dans `morpheus-application`.
- Ne pas trier l'audit des policies sur le texte de `at`.
- Ne pas appeler `ChangeLifecycleMutationStore.listAudit` ni `SyncStateStore.listArchives` depuis le code de
  production sans les borner d'abord.
- Ne pas supprimer d'un journal, ni introduire une purge sans décision qui remplace celle-ci.

## Consequences

### Positives

- Un client lit le même vocabulaire de page sur MCP et HTTP, et l'audit des policies sur les trois transports.
- Une collection croissante reste lisible sur MCP au-delà du cadre, en suivant le conseil du transport.
- La lecture de l'audit ne matérialise que la page demandée.

### Négatives

- La page d'un journal profond coûte plus que celle d'un journal court : `OFFSET` parcourt l'index jusqu'au décalage.
- La lignée d'un projet est parcourue en entier à chaque page pour son total.
- Les valeurs par défaut et les maxima de `limit` diffèrent selon les familles d'outils (catalogue, portefeuille,
  requêtes) ; seul le maximum partagé avec `PageRequest` est tenu.

### Risques

- Les versions d'un pack et les révisions d'une vue sont lues en entier à chaque appel, et rendues entières sur HTTP et
  la CLI ; la taille à laquelle elles atteindraient la borne du proxy remote n'est pas mesurée.
- Plusieurs outils exemptés sous `UNBOUNDED_ACKNOWLEDGED` peuvent dépasser le cadre MCP.
- La garde de vocabulaire ne voit ni une collection non paginée, ni une page sérialisée depuis un record d'application.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PagedResponseVocabularyArchitectureTest.java` :
  cinq clés dans l'ordre, deux copies identiques, aucune page construite à la main, aucune orthographe concurrente du
  total, page de `get_current_specification` sous `specifications`.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/GrowingCollectionToolsArePageableTest.java` : tout outil servi est
  paginé ou exempté avec une raison.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/GrowingCollectionToolsPagingTest.java` : pages, ordre, bornes identiques
  dans le schéma et le handler.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/PageBoundsAgreementTest.java` : égalité des maxima de `limit`.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusApiHistoryContractTest.java` et
  `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LocalVersionsHttpRoutesArchitectureTest.java` :
  pagination de la lignée d'un projet.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusPolicyAuditPagingHttpTest.java` et
  `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqlitePolicyAuditPageTest.java` : page de l'audit sur
  HTTP, requête de page sans tri.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AppendOnlyJournalReadsArchitectureTest.java` :
  aucune lecture de production des deux journaux sans lecteur, aucune instruction qui supprime d'un journal.

## References

- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — requête lexicale déterministe
- [ADR-0106](BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) — borne entrante défensive, borne
  sortante comme défaut
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — une réponse dit ce qu'elle n'a pas pu observer
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — une copie est gardée avant que le schéma avance
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — lectures métier sur le snapshot publié
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique
- `contracts/public-surfaces.tsv`
