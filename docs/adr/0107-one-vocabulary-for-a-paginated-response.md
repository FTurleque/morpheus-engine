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

## Amendement du 29 septembre 2026 (API-3) — une collection qui ne décroît jamais est restituée par page

`GET /api/v1/projects/{id}/versions` rendait sa lignée entière sous un `items` nu, refusait `?offset` et `?limit` (400
`unknown query parameter`) que sa route sœur `.../versions/{snapshotId}/requirements` accepte, et n'employait pas le
vocabulaire de page que cet ADR déclare unique. La collection est **structurellement croissante**
(`retentionPolicy = KEEP_ALL_PUBLISHED`, un élément par publication réussie), et chaque élément coûte deux lectures de store
(`findSnapshotVersion`, `findSpecificationVersion`) qu'aucun paramètre ne permettait d'éviter.

**Décision.** La route reprend le patron de sa sœur : `MorpheusVersionsHttpRoutes` alimente `versions(...)` par le même
`page(query)` privé (`offset` ≥ 0, `limit` de 1 à `MAX_LIMIT`, 50 par défaut), et la réponse est
`PagedEnvelope.following(projectId, retentionPolicy ; slice(...))`. `slice` n'applique la projection qu'aux éléments retenus :
le coût par élément est borné par `limit`. Une collection qui ne décroît jamais est restituée par page.

**Ce qui n'est pas borné, et c'est dit.** La lignée reste parcourue **en entier** pour connaître `totalMatches` : c'est une
lecture de métadonnées (un `findSnapshot` par prédécesseur, chaînée), inévitable sans changer le modèle de stockage. Seule la
projection — les deux lectures de store par élément — est bornée. L'ordre est celui de la lignée, du plus ancien au plus
récent : total, stable et **append-only**, donc une publication survenue entre deux requêtes s'ajoute en fin et ne décale
aucune page déjà lue.

### Alternatives écartées

- **Borner la lignée côté domaine.** La rétention `KEEP_ALL_PUBLISHED` est une décision ; le constat porte sur la
  restitution, pas sur la conservation.
- **Un second extracteur de page** propre à cette route : c'est la copie qui dérive.
- **Un curseur** (`?after=<snapshotId>`) : plus fidèle à une collection append-only, mais une troisième forme de page.

### Ce que la garde ne couvre pas

`PagedResponseVocabularyArchitectureTest` n'interdit que les littéraux `"totalMatches"` et `"hasMore"` écrits à la main ; elle
n'aurait **pas** vu l'ancien `items` nu, et ne voit toujours pas une collection non paginée. Le comportement est tenu par
`MorpheusApiHistoryContractTest` (deux pages, `hasMore`, paramètre inconnu refusé) et le texte du routeur par
`LocalVersionsHttpRoutesArchitectureTest`.

## Amendement du 29 septembre 2026 (MCP-2) — un outil MCP sur une collection croissante prend une page, parce que le conseil du transport en dépend

### Constat

`get_policy_audit`, `list_policy_pack_versions` et `list_saved_view_versions` rendaient leur collection **entière**, sous un tableau
nu, avec un schéma d'entrée réduit à `{id}` et `additionalProperties: false`. Les trois collections ne décroissent jamais : un
enregistrement d'audit par changement de configuration, une version par mise à jour d'un pack, une révision par mise à jour d'une vue
(`SqlitePolicyPackStore.listAudit`, `ORDER BY at, id` ; `listVersions`, `ORDER BY version_number` ; `SqliteSavedViewStore.listVersions`,
`ORDER BY revision` — aucune n'a de `LIMIT`).

Or le cadre MCP est de 1 Mio (`BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES`) et, au-delà, le transport remplace la
réponse par `MCP_RESPONSE_TOO_LARGE` accompagné de `MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE` : *paginated read tools accept offset
and limit: retry with a smaller limit, then page with offset* (amendement d'ADR-0106). L'agent qui suit ce conseil envoie `{id, limit}` ;
le serveur arme `validateToolInputs(true)` et le SDK refuse l'appel avant tout handler pour argument inconnu. L'outil était
**inappelable au-delà d'1 Mio, avec une instruction de réparation que son propre schéma interdisait**.

### Décision

1. **Les trois outils rendent une page**, dans le vocabulaire de cet ADR et par la même fabrique : `PagedEnvelope.slice`, dont la
   projection ne s'applique qu'aux éléments retenus. Aucune méthode n'est ajoutée à la copie de `morpheus-mcp` ni de clé `count`/`total` :
   `PagedResponseVocabularyArchitectureTest` exige que les deux copies restent identiques.
2. **Un seul endroit lit et déclare les bornes : `PageArguments`.** `limit` vaut 50 par défaut, de 1 à 100 (les constantes du catalogue
   MCP, que `PageRequest` applique aussi) ; `offset` vaut 0 par défaut, de 0 à `Integer.MAX_VALUE`. Le schéma déclare **les mêmes bornes
   que le code**, `maximum` d'`offset` compris : un schéma qui tait une borne que le handler applique est le défaut de MCP-3.
   Le dépôt avait trois conventions pour `offset` (catalogue : 0 à 1 000 000 des deux côtés ; composition, portefeuille et requête :
   `Integer.MAX_VALUE` dans le code, aucun `maximum` dans le schéma). J'ai retenu la borne du code des trois derniers, **déclarée**, sans
   plafond arbitraire sur une collection qui ne fait que croître ; les deux autres conventions ne sont pas touchées.
3. **L'ordre est celui que le service rend, et il est total.** Versions d'un pack : `PolicyPackService.versions` trie par
   `Comparable`. Audit : `PolicyPackService.audit` trie par `(at, id)` **comparés comme valeurs**, pas comme texte — la colonne `at`
   est un `TEXT` (`Instant.toString()`) dont l'ordre lexicographique n'est pas l'ordre chronologique quand le nombre de décimales varie
   (`…:00.5Z` se trie avant `…:00Z`), et `ORDER BY at, id` en SQL le rendrait faux. Révisions d'une vue : `ORDER BY revision` sur un
   entier, non retrié par le service. Aucun curseur : une page lue pendant qu'un enregistrement s'ajoute peut se décaler, ce que l'audit
   n'exclut pas (l'horloge peut reculer, `at` n'est pas monotone) ; `totalMatches`, rendu à chaque page, permet de le constater.
4. **La borne s'applique en mémoire, pas en SQL, et c'est dit.** La tranche est prise dans une liste déjà lue. Descendre à
   `LIMIT/OFFSET` passerait par les ports `application.store.PolicyPackStore` et `SavedViewStore`, leurs implémentations mémoire et
   SQLite et les tests de parité, et — pour l'audit — obligerait à réordonner en SQL une colonne `TEXT` dont l'ordre est faux. La borne
   **limite la réponse, pas le coût de lecture** : la collection reste lue en entier à chaque appel. C'est un résidu assumé, écrit dans
   le Javadoc de `PageArguments`, à traiter si la lecture devient le goulot.

### Périmètre : MCP seul, et pourquoi

Ces trois collections sont aussi servies par HTTP (`GET /api/v1/policy-packs/{id}/versions`, `MorpheusPolicyHttpRoutes` :85-88 ;
`GET /api/v1/policy-packs/{id}/audit`, :100-103 ; `GET /api/v1/saved-views/{id}/versions`, `MorpheusQueryApiService.savedViewVersions`
:78) et par le CLI (`policy pack-versions`, `MorpheusPolicyCli` :95-97 ; `policy audit`, :155-157 ; `views versions`,
`MorpheusQueryCli` :115-117). **Elles n'y sont pas paginées et ne le deviennent pas ici.** La raison n'est pas que leur croissance
soit acceptable : c'est que seul le cadre MCP d'1 Mio rend l'outil *inappelable* — un client HTTP ou CLI lit une réponse plus grande
sans que rien ne casse. Cet amendement **qualifie** donc la phrase de l'amendement API-3 (« une collection qui ne décroît jamais est
restituée par page ») : elle décrit la route `versions` d'un projet, et son extension à d'autres collections n'est faite ici que pour
MCP. Résidu à nommer : le proxy remote refuse toute réponse locale de plus de 16 Mio (`MorpheusRemoteHttpServer.MAX_PROXY_RESPONSE_BYTES`,
`MorpheusRemoteProxyTransport.requireBoundedLength` : `502 UPSTREAM_RESPONSE_TOO_LARGE`), sans recours par `limit` sur ces routes ;
**la taille à laquelle ces trois collections atteindraient ce seuil n'a pas été mesurée**. Étendre la page à HTTP et au CLI est un
changement de contrat public (OpenAPI, manifeste, deux surfaces de plus) à décider séparément.

### Ce qui change pour un client MCP

Une rupture de forme, annoncée dans `docs/release/RELEASE_NOTES_1.2.1.md` : le tableau nu devient l'enveloppe à cinq clés, avec `limit`
à 50 par défaut. Pour une collection de 50 éléments ou moins, les éléments et leur ordre sont ceux d'avant, sous `items`.

### Alternatives écartées

- **`LIMIT`/`OFFSET` dans le port.** Voir §4 : plus juste sur le coût, plus large que le constat, et l'ordre de l'audit y est faux.
- **Un second vocabulaire de page**, ou une clé `count` : la copie qui dérive (§1 de cet ADR).
- **Étendre `idSchema()` à `offset` et `limit` pour tous les outils à identifiant.** `get_policy_pack` et `get_saved_view` lisent une
  chose ; un schéma qui accepte un paramètre qu'il ignore est le défaut de MCP-3.
- **Nommer les trois outils dans la garde.** C'est le filtre trop étroit d'API-7 : le quatrième outil du même défaut passerait.

### Preuves exécutables

- `GrowingCollectionToolsPagingTest` : par outil, sans `offset` ni `limit` la collection revient entière, dans l'ordre, sous
  l'enveloppe ; `limit=1` rend un élément et `hasMore` ; parcourir les pages redonne la collection une fois ; un `offset` au-delà
  du total rend une page vide avec le bon `totalMatches` ; l'audit est rendu chronologiquement sur des `at` dont l'ordre texte diffère
  (le test vérifie qu'il diffère, faute de quoi il ne prouverait rien) ; **`{id, limit}` passe la validation de schéma du serveur
  sur les trois outils**, `{id, argumentInconnu}` reste refusé ; les bornes sont refusées à l'identique par le schéma et par le
  handler (`limit` 0 et 101, `offset` −1 et 2³¹), et la plus large est acceptée des deux côtés ; `get_policy_pack` refuse toujours
  une page.
- `GrowingCollectionToolsArePageableTest` : tout outil que le câblage par défaut sert déclare `offset` **et** `limit`, ou figure
  dans `NOT_PAGED` avec une raison. Le critère de « paginé » est structurel, sans nom d'outil ; l'échec nomme l'outil ; une
  exemption qui n'est plus servie, ou qui est devenue paginée, échoue aussi ; un ensemble vide échoue.

### Ce que la garde ne couvre pas

Elle ne sait pas si une collection **croît** : une exemption est un jugement écrit, vérifié contre le code le 29/09/2026, que le test ne
rejoue pas — un outil qui rend une collection croissante et figure à tort dans `NOT_PAGED` la satisfait. Elle ne sait pas si un outil
paginé **honore** `offset` et `limit` : `export_query` les déclarait et les ignorait (MCP-3 les a retirés), `create_saved_view` et `update_saved_view` les
déclarent parce qu'ils appartiennent à la requête stockée, et `get_specification_context` pagine ses exigences mais rend ses scénarios
et ses changements entiers. Elle ne juge que ce que `MorpheusMcpServer.toolSpecifications` sert avec le câblage par défaut.

**Ce que la liste `NOT_PAGED` dit, et ne dit pas.** Ses raisons sont de trois sortes : `ONE_RESULT` (une seule réponse sur une seule
chose), `BOUNDED_IN_COUNT` et `UNBOUNDED_ACKNOWLEDGED`. La deuxième s'appelait d'abord « bornée par un budget » : le mot trompait. Un budget
**en nombre d'éléments** ne dit **rien** de la taille de la réponse sous le cadre d'1 Mio, puisqu'un élément peut être lui-même gros. Les outils
dont le budget, multiplié par la taille d'un élément que l'arithmétique établit au minimum (ou par le plus gros élément, quand une constante le borne), dépasse le cadre — par arithmétique sur les constantes lues, jamais par mesure — sont donc
classés `UNBOUNDED_ACKNOWLEDGED` avec cette raison : `export_saved_view` (budget d'octets `MAX_EXPORT_BYTES`, 10 Mio : un export valide de
quelques Mio ne peut pas être rendu et le conseil du transport ne peut pas être suivi, ce qui est la classe du constat), `list_saved_views`
(250 vues × une expression encodée pouvant atteindre 16 Kio), `evaluate_policies` et `dry_run_policy_pack` (budget en nombre de règles, mais
chaque résultat de règle porte une `evidence` de jusqu'à 1024 entrées, ADR-0108), `reason_with_evidence` (la réponse répète chaque évidence
`PUBLISHED_FACT` dans `evidence` et dans `facts`), `get_augmented_*` (un budget de jetons, pas d'octets), et les trois parcours de graphe
`traverse_portfolio`, `trace_requirement` et `get_change_context` (plafonds de 1000 nœuds et 5000 liens, `PortfolioTraversalService`,
`TraceabilityTraversalService` ; un lien n'est pas petit : le plus petit lien que la vue compacte rend — quatre identifiants et un identifiant
d'évidence, `TraceLinkView` — fait environ 320 octets sur une chaîne d'exemple construite, un lien de portefeuille environ 420, et 5000 liens de
320 octets sont au-dessus de 1 Mio. **Pire cas par arithmétique, non mesuré**, et l'ADR n'établit pas qu'un instantané ou un portefeuille réel
atteigne ce nombre de liens ; `traverse_portfolio` prend 1000 liens par défaut, soit environ 420 Kio au minimum, et n'excède le cadre qu'à son
maximum déclaré ; `get_change_context` rend en plus les exigences, contraintes, décisions et tâches d'un changement, dont aucune borne en nombre
n'a été trouvée).

**Le critère n'a pas été appliqué en octets à tous les outils.** Ce paragraphe l'a d'abord été à une partie seulement, et une première rédaction
laissait `BOUNDED_IN_COUNT` pour les trois parcours de graphe sans l'avoir fait. Restent `BOUNDED_IN_COUNT`, **sans aucune promesse de taille** :
`list_policy_overrides` (256 × 1280 caractères d'acteur et de motif font environ 330 Kio pour ces deux champs ; les autres champs n'ont pas été
sommés), `list_policy_activations` et `execute_saved_view` (pire cas en octets **non évalué** : la taille d'une ligne n'est bornée par aucune
constante lue). Cette catégorie dit « borné en nombre » et rien d'autre.

**Résidus nommés** (raison `UNBOUNDED_ACKNOWLEDGED`) : les précédents, plus `list_policy_packs` (aucun plafond à `PolicyPackService.create`,
aucune suppression), `list_external_references`, `get_portfolio_overview` (`PortfolioQueryService.overview` rend les inscriptions et la
fraîcheur d'un portefeuille **et** les conflits et le compte dérivés de **toutes** ses références inter-projets, auxquelles
`add_cross_project_reference` ne fait qu'ajouter ; `MAX_PORTFOLIO_PROJECTS` borne les requêtes de portefeuille, pas l'inscription),
`get_composition_status` (rend aussi ses conflits, que `list_composition_conflicts` pagine), `get_blocking_conditions`,
`list_reasoning_adapters`, et `export_query` une fois l'export déclaré complet (MCP-3). Aucun n'est garanti petit.

### Décisions ajoutées après relecture

- **`list_composition_conflicts` consomme `PageArguments`.** Il appliquait déjà les mêmes bornes (50 et 100 ; `offset` jusqu'à `Integer.MAX_VALUE`)
  par une copie privée. Il les prend désormais de `PageArguments`, lecture et schéma. Le seul effet visible : son schéma publie enfin le
  `maximum` d'`offset` que son handler appliquait déjà.
- **Ce qui garde ses propres bornes, non gardé, par choix.** `MorpheusPortfolioMcpTools` (défaut 100, maximum `PortfolioQueryService.MAX_PAGE_SIZE`,
  500), les outils de requête (`queryProperties()` : défaut 100, maximum `QueryBudgets.MAX_PAGE_SIZE`, et un `offset` dont le schéma ne déclare aucun
  maximum alors que le code applique `Integer.MAX_VALUE`) et le catalogue (`MorpheusMcpToolCatalog`, `MorpheusMcpToolService` : 50 et 100, `offset`
  jusqu'à 1 000 000). Leurs valeurs diffèrent, donc une garde d'égalité serait fausse ; le désaccord schéma/code des outils de requête est la
  classe du défaut de MCP-3, dont la garde est la liste des propriétés de `export_query`, pas une garde de bornes. Aucune garde n'est revendiquée ici.
- **Les arguments de page sont lus, et refusés hors bornes, avant la lecture de la collection** (`PageArguments.slice` prend un `Supplier`) : un
  `limit` de 0 sur une collection inconnue répond sur le `limit`, un `limit` valide répond que la collection est inconnue
  (`thePageArgumentsAreRefusedBeforeTheCollectionIsRead`). `list_composition_conflicts` reçoit son état d'abord ; sa lecture précède donc sa validation.

## Amendement du 30 septembre 2026 (MCP-6) — le Javadoc de `PageArguments` décrit le chemin servi, et l'égalité des bornes est tenue

Cet amendement ne change aucun comportement. Il corrige trois énoncés du Javadoc de `PageArguments` que l'amendement MCP-2 avait écrits et que le code ne tenait pas,
et il ajoute la garde d'une égalité que ce même amendement affirmait.

### Constat

1. **« sans que le store ait été ouvert ».** Le Javadoc de `PageArguments.slice` écrivait qu'un appelant qui demande `limit = 0` l'apprend « without
   the store having been opened for a page nobody can return ». Le `Supplier` diffère la **lecture de la collection**, pas l'ouverture du store :
   `MorpheusPolicyMcpTools.call` ouvre `SqlitePolicyRuntime` avant son `switch` (ligne 81 à `933a63fe`), `MorpheusQueryMcpTools.call` ouvre
   `SqliteQueryRuntime` de même (ligne 85), et les `PageArguments.slice` sont dans le `try`. L'énoncé de l'amendement MCP-2 de cet ADR (« avant la
   lecture de la collection ») était juste ; c'est le Javadoc qui promettait plus. **Mais ce refus n'est pas celui qu'un appel servi rencontre** (voir la décision).
2. **« que `PageRequest` applique aussi ».** L'amendement MCP-2, Décision, point 2 (« `limit` vaut 50 par défaut, de 1 à 100 (les constantes du catalogue
   MCP, que `PageRequest` applique aussi) »), et le Javadoc écrivaient que les bornes de `limit` sont celles du catalogue que `PageRequest` applique aussi.
   `PageRequest.MAX_LIMIT` (`morpheus-application`) et `MorpheusMcpToolCatalog.MAX_LIMIT` (`morpheus-mcp`) sont deux littéraux `100` dans deux modules, et
   rien ne tenait leur égalité. Et `PageRequest` ne porte **pas de valeur par défaut** : l'énoncé ne pouvait être vrai que du maximum.
3. **« tout outil qui rend une collection qui ne fait que croître prend ces arguments ».** Faux à l'époque : `list_policy_packs` rend une collection
   sans plafond ni suppression et figure dans les résidus nommés (`UNBOUNDED_ACKNOWLEDGED`) de cet ADR. Le Javadoc nomme désormais les trois outils du constat
   MCP-2 et renvoie à `GrowingCollectionToolsArePageableTest` pour les autres.

### Décision

- **Le texte est corrigé seul ; la validation de page n'est pas remontée avant l'ouverture du store.** Sous le serveur la propriété voulue existe déjà, pour les
  refus que le schéma exprime : `MorpheusMcpServer.build` arme `validateToolInputs(true)` (tenu par
  `McpFailureContractTest#theServerArmsSchemaValidationForEveryToolItServes`), et le SDK (`mcp-core` 2.0.1, `McpAsyncServer.toolsCallRequestHandler`) exécute
  `ToolInputValidator.validate` **avant** `callHandler().apply` ; `McpSyncServer` enveloppe le serveur asynchrone. Le schéma de ces outils publie les mêmes bornes que
  le handler (`PageArguments.properties()` ; `GrowingCollectionToolsPagingTest#theBoundsAreEnforcedByTheHandlerAndAnnouncedByTheSchemaAlike`). Un appel servi avec
  `limit = 0` est donc refusé par le SDK avant tout handler, et **aucun store n'est ouvert pour lui**. C'est ce que CLI-9 installe dans le CLI (le store n'est
  ouvert qu'une fois les options acceptées) : la surface MCP servie l'avait déjà pour ce qu'un schéma exprime. Le refus propre de `PageArguments.slice` n'est atteint
  qu'en appel direct du handler, comme le font les tests ; il y a le store déjà ouvert, c'est **assumé et non gardé**. Cela est établi **par lecture** du code
  et des sources du SDK, non mesuré par un appel servi de bout en bout.
- **Conséquence.** Le constat portait sur un énoncé faux au niveau du handler ; il n'y a pas de défaut de comportement sur le chemin servi.
  Remonter la validation dans `MorpheusPolicyMcpTools` et `MorpheusQueryMcpTools` n'apporterait rien à un appelant réel, seulement aux appels directs des tests.
- **Inventaire des ouvertures de store par appel dans `morpheus-mcp`** (vérifié à `933a63fe`). Il décrit l'ordre des refus **sémantiques** dans les handlers (un identifiant
  qui doit être analysé, une valeur que le schéma n'exprime pas), **pas** les refus de schéma, que le SDK rend avant tout handler. Ouvrent **avant** de lire un argument :
  `MorpheusPolicyMcpTools`, `MorpheusPolicyMcpManagementTools`, `MorpheusQueryMcpTools`, `MorpheusPortfolioMcpTools` et `MorpheusMcpToolService.execute`
  (outils du catalogue, dont la page est lue dans la branche). Lisent leurs identifiants **puis** ouvrent : `MorpheusCompositionMcpTools` (`projectId`),
  `MorpheusAugmentedContextMcpTools` (`projectId` et les options), `MorpheusControlledLifecycleMcpTools`, `MorpheusExternalReferenceMcpTools` (`list`, `resolve`) et
  `MorpheusJarvisOrchestrationMcpTools`. Aucun ordre uniforme n'est revendiqué pour les refus sémantiques. Les noms de classes et les lignes sont une lecture à `933a63fe`.
- **L'égalité des maxima est gardée** : `PageBoundsAgreementTest` exige `PageRequest.MAX_LIMIT == MorpheusMcpToolCatalog.MAX_LIMIT`, que `PageArguments.MAX_LIMIT`
  et le `maximum` publié par son schéma lui soient égaux, que `PageArguments.slice` accepte `MAX_LIMIT` et refuse `MAX_LIMIT + 1` sans lire la collection, et que
  `PageRequest` refuse `MAX_LIMIT + 1` avec le texte qui nomme `MAX_LIMIT`. Le message d'échec dit quels énoncés deviennent faux.
- **Le consommateur qui rend cette égalité nécessaire dans un sens.** `MorpheusMcpToolService.page` (l. 288-291) construit un `PageRequest` avec un `limit` borné par
  `MorpheusMcpToolCatalog.MAX_LIMIT`, et les schémas du catalogue publient `integer(1, MAX_LIMIT)`. Si le maximum du catalogue dépasse celui de `PageRequest`, les
  outils du catalogue refusent un `limit` que leur schéma accepte : la classe de défaut de MCP-3. **Dans ce sens de divergence, réécrire la phrase n'est pas un remède
  suffisant** ; il faut ramener les constantes l'une à l'autre. Aucun test n'appelle un outil du catalogue à son `limit` maximal (`MorpheusMcpToolServiceTest` prend
  `limit` = 1 ; `MorpheusMcpToolCatalogTest` fige le `maximum` du schéma sur le littéral 100 sans rien appeler) : cette garde d'égalité est la seule de ce sens.
- **Trois copies écrites à la main, gardées.** Les descriptions de `list_policy_pack_versions`, `get_policy_audit` (`MorpheusPolicyMcpTools`) et `list_saved_view_versions`
  (`MorpheusQueryMcpTools`) écrivent « default 50, maximum 100 » en dur. `PageBoundsAgreementTest` exige que chacune contienne le texte construit à partir des constantes.
  Une quatrième description qui recopierait ces nombres ne serait pas vue.
- Précision sur « Leurs valeurs diffèrent, donc une garde d'égalité serait fausse » (amendement MCP-2, « Ce qui garde ses propres bornes ») : cela vaut pour
  le portefeuille et les outils de requête. Pour le catalogue (50 et 100), `PageArguments` **lit** ces constantes : l'égalité n'est pas à garder, elle est
  structurelle, et c'est celle avec `PageRequest` qui ne l'était pas. L'énoncé MCP-2 (Décision, point 2) reste écrit tel quel ; il se lit, depuis cet amendement,
  comme ne portant que sur le **maximum** de `limit`.
- **Le CLI, pour mémoire.** Dans le CLI l'ouverture du store créait le fichier à un emplacement non validé avant le refus des options (CLI-9, corrigé : voir l'ADR-0108,
  amendement du 30 septembre 2026) ; la surface MCP servie n'était pas dans ce cas pour un refus de schéma.

### Ce que la garde ne couvre pas

`PageBoundsAgreementTest` ne compare que les **maxima** de `limit` et le texte de trois descriptions : pas les valeurs par défaut (`PageRequest` n'en a pas ; le CLI prend 20,
le catalogue 50, le portefeuille et les outils de requête 100), pas les plafonds d'`offset` (1 000 000 dans le catalogue, `Integer.MAX_VALUE` dans `PageArguments`, par
choix), pas les maxima des outils qui gardent leurs propres bornes (500, volontairement différents de `PageRequest`). Il ne découvre aucun outil : un quatrième outil qui
recopierait `100` au lieu de lire `PageArguments` ou le catalogue, ou une quatrième description, ne serait pas vu. Il n'appelle aucun outil du catalogue à son maximum. Le fait
que l'ouverture du store précède le refus d'une page en appel direct n'est pas gardé : c'est un résidu écrit, pas une propriété testée. Le fait qu'un appel servi avec
`limit = 0` n'ouvre aucun store est établi par lecture (`validateToolInputs(true)`, l'ordre du SDK), non par une mesure de bout en bout.
