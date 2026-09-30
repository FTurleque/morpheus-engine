# MORPHEUS 1.2.1 — Notes de version

Statut : **EN PRÉPARATION — NON PUBLIÉE**

`1.2.1` est la baseline corrective de développement. Elle ne devient une release publiée qu'après création du tag et
qualification de ses artefacts (suivi #185, critère de clôture dans
[`../validation/RELEASE_QUALIFICATION.md`](../validation/RELEASE_QUALIFICATION.md) ; version courante dans
[`../developer/PRODUCT_VERSION.md`](../developer/PRODUCT_VERSION.md)).
Cette page recense, au fil des corrections, les changements qu'un utilisateur de 1.2.0 doit connaître avant de migrer.
Elle ne revendique aucune publication.

## Ruptures

### Structured Markdown : un bloc `morpheus task` déclare son achèvement

Jusqu'à 1.2.0, un bloc `task` sans champ `completed` était publié comme **non achevé** : l'absence valait `false`, et
ce `false` était stocké puis servi par la CLI, le HTTP, le MCP et le contexte augmenté (`[pending]`) comme une
observation.

À partir de 1.2.1, `completed` est **obligatoire**, comme `key`, `change` et `title`, et vaut `true` ou `false`. Un bloc
qui l'omet est refusé à la lecture :

```text
Structured Markdown normalization failed: missing 'completed' in task block at line <N>
```

Le diagnostic est bloquant (`INVALID_SOURCE`, sévérité `ERROR`) et le provider Structured Markdown ne contribue alors
aucun contenu : dans un workspace où il est seul, la synchronisation échoue et n'active aucun nouveau snapshot ; en
composition, c'est le comportement de tout échec de lecture de ce provider.

**Migration.** Ajouter `completed=false` (ou `completed=true`) à chaque bloc `task` de `morpheus/specification.md` qui ne
le déclare pas. Aucune migration de base n'est nécessaire : les tâches déjà publiées ne changent pas tant que le projet
n'est pas resynchronisé.

Décision : [ADR-0108, amendement du 24 septembre 2026 (PRV-4)](../adr/0108-a-response-says-what-it-could-not-observe.md).
Format du bloc : [`../user/QUICKSTART.md`](../user/QUICKSTART.md).

### Qualité d'un change : l'absence d'observation n'est plus un fait

Jusqu'à 1.2.0, un change dont **aucune** contrainte n'avait été ingérée produisait le fait affirmatif
`criticalConstraintsKnown = TRUE` (`allMatch` est vrai d'un flux vide) et passait donc `PROPOSED → SPECIFIED`, alors qu'un change portant
une contrainte d'applicabilité `UNKNOWN` était bloqué. Inversement, un change dont les critères d'acceptation ne portent que
`requirementId` (sans `changeId`) produisait `acceptanceCriteriaDefined = FALSE` et se voyait refuser la même transition avec
`MISSING_ACCEPTANCE_CRITERIA`, alors que ces critères existent.

À partir de 1.2.1 :

- un change sans contrainte a `criticalConstraintsKnown = UNAVAILABLE` : l'évaluation de `PROPOSED → SPECIFIED` ne se conclut pas et
  rend le diagnostic `LIFECYCLE_REQUIRED_FACT_UNAVAILABLE` au lieu d'autoriser la transition ;
- les critères d'acceptation rattachés aux **exigences** du change comptent comme ceux rattachés au change ;
- `acceptanceCriteriaDefined` est `FALSE` seulement quand les exigences du change sont connues et qu'aucun critère n'existe ; sans lien
  vers une exigence courante, il est `UNAVAILABLE`.

**Migration.** Un change qui doit pouvoir passer `SPECIFIED` sans contrainte substantielle doit l'exprimer : ingérer ses contraintes, ou
déclarer explicitement une contrainte `NOT_APPLICABLE` (applicabilité et sévérité connues).

Décision : [ADR-0108, amendement du 25 septembre 2026 (QLT-1, QLT-2)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### Contexte augmenté : plus de chemin serveur dans le statut NEXUS (HTTP et MCP)

Jusqu'à 1.2.0, les deux routes `augmented-context` (rôle READ en remote) et les deux outils MCP `get_augmented_*_context`
publiaient le statut NEXUS tel quel dans `technicalContext.status.details` : `javaCommand`, `jar` et `home`, trois chemins
absolus du serveur, que la route `GET /api/v1/integrations/nexus/status` retirait pourtant déjà.

À partir de 1.2.1, une seule projection s'applique à ces quatre surfaces et à la route de statut : chaque emplacement est
rapporté comme configuré (`jarPathConfigured`, `homeDirectoryConfigured`, `javaCommandConfigured` : `"true"`/`"false"`) et non
nommé ; un message d'échec qui nomme un chemin est remplacé. `projectId`, `projectName` et `estimatedTokens` sont conservés. Les clés
NEXUS `jar`/`home` sont alignées sur les clés MINOS `jarPath`/`homeDirectory` (avant, la route de statut NEXUS les supprimait sans les
remplacer). La CLI garde les réglages complets.

**Migration.** Un client qui lisait `details.jar`, `details.home` ou `details.javaCommand` dans une réponse HTTP ou MCP doit lire
`jarPathConfigured`, `homeDirectoryConfigured` ou `javaCommandConfigured` ; la valeur du chemin reste disponible via la CLI.

Décision : [ADR-0094, amendement du 25 septembre 2026 (NEX-1)](../adr/0094-optional-team-remote-server-mode.md).

### Références externes MINOS : une recherche tronquée n'est plus une absence

Jusqu'à 1.2.0, le résolveur MINOS demandait au plus 1000 symboles, filtrait sur la clé exacte et répondait « introuvable »
quand elle n'était pas dans la page. Sur un projet dépassant cette borne, une référence déjà résolue devenait
`STALE` / `TARGET_REMOVED` : l'affirmation que le code avait été supprimé, fondée sur une page qui s'était arrêtée avant.

À partir de 1.2.1, une page pleine (autant de symboles que la limite demandée) sans correspondance exacte est
`UNAVAILABLE` : la référence devient `UNRESOLVED` ou `STALE` avec la raison `TARGET_UNAVAILABLE`. MINOS ne fournit pas
de total ni de drapeau de troncature ; le contrat est donc volontairement conservateur (mieux vaut un « indisponible » de trop
qu'un « supprimé » faux). Une recherche non pleine sans correspondance reste `NOT_FOUND` / `TARGET_NOT_FOUND` ou `TARGET_REMOVED`.

**Migration.** Aucune. Un consommateur qui traitait `TARGET_REMOVED` comme certain n'y trouvera plus de faux positifs ;
relancer la résolution ou affiner la clé de symbole.

### CLI `portfolio` : une option inconnue est refusée

Jusqu'à 1.2.0, `morpheus portfolio <action>` acceptait en silence toute option `--clé valeur` qu'elle ne lisait pas.
Une faute de frappe sur un filtre facultatif changeait le sens de la commande sans l'annoncer : `portfolio references
--portfolio P --projet X` rendait **toutes** les références du portefeuille, code 0. Sur une action d'écriture, `add-project
… --workspac /src` persistait une appartenance sans workspace.

À partir de 1.2.1, chaque action refuse une option qu'elle ne lit pas, comme les autres adaptateurs du CLI :

```text
MORPHEUS error [2]: unknown option: --projet
```

**Migration.** Un script qui passait une option ignorée reçoit maintenant le code `2` : corriger ou retirer l'option.
Les options reconnues par action sont exactement celles que l'action lit (`ACTION_OPTIONS` dans `MorpheusPortfolioCli`).

Décision : garde `CliOptionParsingRefusesUnknownOptionsTest` (chaque méthode qui appelle `SimpleOptions.parse` appelle aussi `rejectUnknown`) ; un `case` sans entrée dans `ACTION_OPTIONS` est refusé comme action inconnue.

### CLI `policy evaluate` et `policy dry-run` : un refus atteint le code de sortie

Jusqu'à 1.2.0, ces commandes rendaient `0` quelle que soit la décision écrite dans le JSON, `BLOCK` et `UNKNOWN` compris.
Un pipeline qui appliquait l'idiome documenté `if ($LASTEXITCODE -ne 0) { throw }` laissait donc passer un refus, et un
`UNKNOWN` (règle non évaluable) était converti en succès au niveau du processus.

À partir de 1.2.1, la décision effective est reportée sur le code de sortie :

| Décision | Code |
|---|---:|
| `PASS`, `WARN` | `0` |
| `BLOCK`, `UNKNOWN` | `4` (`STATE_ERROR`) |

Le JSON est toujours imprimé, y compris avec le code `4`. Les autres actions de `policy` (configuration, listes, audit) ne
changent pas : elles rendent `0` quand elles réussissent.

**Migration.** Une CI qui appelait `policy evaluate` ou `policy dry-run` sans regarder le JSON échoue maintenant sur `BLOCK` et
`UNKNOWN` : c'est le but. Pour ne pas échouer, lire `decision` dans le JSON plutôt que le code de sortie. Les validateurs
`scripts/validate-m25.*` attendent désormais explicitement le code `4` sur leurs deux appels.

Décision : [ADR-0108](../adr/0108-a-response-says-what-it-could-not-observe.md).

### CLI : un refus sur l'état ne rend plus le code d'usage

Jusqu'à 1.2.0, les commandes `policy`, `views`, `export view`, `query execute`, `portfolio` et `server identity` rendaient
`2` (usage) pour un refus qui ne portait pas sur l'appel mais sur l'état : un identifiant qui ne désigne rien, un pack qui
n'est pas actif. La table des codes publiée par `morpheus help` réserve pourtant `3` à l'entité absente et `4` à l'état.
Un script ne pouvait pas distinguer une faute de frappe d'un identifiant inconnu.

À partir de 1.2.1 :

| Refus | Avant | Après |
|---|---:|---:|
| `unknown policy pack: …`, `unknown policy pack version: …`, `policy override does not exist: …`, `rule is not present in active policy pack version: …` | `2` | `3` |
| `unknown saved view: …` (`views get\|versions\|execute\|update\|archive`, `export view`) | `2` | `3` |
| `unknown portfolio: …` (`portfolio …`, `query execute --portfolio`) | `2` | `3` |
| `remote auth file does not exist`, `remote principal does not exist: …` | `2` | `3` |
| `policy pack is not active in scope: …`, `policy pack must be active before adding an override: …` | `2` | `4` |
| `project is not a portfolio member: …`, `start project is not a portfolio member: …` | `2` | `4` |
| `remote principal already exists: …`, `cannot revoke the last active ADMIN identity`, `cannot change the role of the last active ADMIN identity`, `migration would leave no ADMIN identity active after …` | `2` | `4` |

Deux messages changent aussi. Un fichier d'identités absent était refusé avec `remote auth file must be a regular
non-symbolic file` ; il est maintenant refusé avec `remote auth file does not exist` (un répertoire ou un lien symbolique
garde l'ancien message et le code `2`). `portfolio missing` sur un portefeuille inconnu répondait `project is not a
portfolio member` ; il répond `unknown portfolio`, comme les autres actions. Les refus sur l'état de `server identity`
s'impriment `MORPHEUS server error: …` au lieu de `MORPHEUS server usage error: …`.

HTTP et MCP ne changent pas : ces refus restent `400 BAD_REQUEST` et un résultat d'outil en erreur.

**Migration.** Un script qui traitait `2` comme « entité absente » ou « pack inactif » doit tester `3` ou `4`. Un script
qui ne distingue que `0` du reste n'est pas concerné. Les refus de budget (packs actifs, overrides, règles évaluées,
identités) et `freshness observation must not move backwards` rendent toujours `2`.

Décision : [ADR-0108, amendement du 26 septembre 2026 (codes de sortie des refus sur l'état)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### MCP `apply_change_lifecycle_transition` : un refus rend un résultat en erreur

Jusqu'à 1.2.0, l'outil rendait un résultat **sans `isError`** (donc sans erreur) pour les quatre refus que le service lui
rend au lieu de les lever : `CONFLICT` (clé d'idempotence réutilisée pour une autre commande, révision attendue périmée),
`NOT_AUTHORIZED`, `REQUIRES_CONFIRMATION` et `REJECTED`. Seul le corps disait le refus (`"state":"CONFLICT"`). Le lanceur
`mcp --stdio` refuse aujourd'hui toute mutation de cycle de vie (le provider OpenSpec qu'il embarque ne déclare pas `WRITE_CHANGE`) et
le câblage par défaut de `MorpheusMcpServer` la refuse toujours : un client qui ne regardait que `isError` tenait donc pour réussie une mutation
qui n'avait pas eu lieu.

À partir de 1.2.1, `isError` suit l'état :

| État | `isError` |
|---|:---:|
| `APPLIED`, `ALREADY_APPLIED` | `false` |
| `CONFLICT`, `NOT_AUTHORIZED`, `REQUIRES_CONFIRMATION`, `REJECTED` | `true` |

Le corps ne change pas d'un octet : c'est le même JSON (`state`, `reason`, `lifecycleState`, `audit`), avec ou sans erreur. Le CLI
(code de sortie `4` pour les quatre mêmes états) ne change pas, ni HTTP, qui répond toujours `200` avec l'état dans le corps.

**Migration.** Un client qui ne lisait le texte du résultat que lorsque `isError` valait `false` doit le lire aussi lorsqu'il vaut
`true` : c'est là que se trouvent désormais l'état et la raison du refus, en JSON et non en message d'exception. Un client qui
branchait sur `isError` seul obtient désormais le bon comportement sans changement.

Décision : [ADR-0102, amendement du 29 septembre 2026 (MCP-5)](../adr/0102-mcp-failure-contract-is-one-rule.md).

### MCP `get_policy_audit`, `list_policy_pack_versions`, `list_saved_view_versions` : une page, et non plus un tableau nu

Jusqu'à 1.2.0, ces trois outils rendaient leur collection entière sous un **tableau JSON nu**, avec un schéma d'entrée réduit à
`{id}`. Au-delà d'1 Mio le transport remplace la réponse par `MCP_RESPONSE_TOO_LARGE` et conseille de réessayer avec un `limit` plus
petit ; le serveur refusait ce `limit` comme argument inconnu. L'outil était inappelable, avec une instruction de réparation que son
propre schéma interdisait.

À partir de 1.2.1, chacun rend une **page** dans le vocabulaire commun : `offset`, `limit`, `totalMatches`, `hasMore`, `items`. Les
schémas déclarent `offset` (0 par défaut, de 0 à 2147483647) et `limit` (50 par défaut, de 1 à 100) ; un argument hors de ces bornes est
refusé, et tout autre argument inconnu l'est toujours. L'ordre est total : versions d'un pack par numéro croissant, révisions d'une vue par
révision croissante, audit par instant puis identifiant, comparés comme des valeurs.

**Migration.** Le tableau devient `items` : un client qui lisait la réponse comme un tableau doit lire `items`. Une collection de 50
éléments ou moins revient avec les mêmes éléments dans le même ordre ; au-delà, un client qui lisait « tout » n'obtient plus que les 50
premiers : **lire `hasMore` et paginer** (`offset` + `limit`, jusqu'à `hasMore` faux). `get_policy_pack` et `get_saved_view` ne changent pas.
`list_composition_conflicts` garde ses valeurs par défaut et ses bornes (50, 1 à 100) : son schéma publie désormais le maximum d'`offset` (2147483647) que son handler appliquait déjà. Un `offset` au-delà était déjà refusé ; il l'est maintenant avant le handler, avec le texte de validation du SDK.

**Ce qui n'est pas borné.** La borne limite la réponse, pas la lecture : la collection est toujours lue en entier avant d'être
tranchée. Et HTTP et le CLI ne changent pas : `GET /api/v1/policy-packs/{id}/versions`, `…/audit`, `GET /api/v1/saved-views/{id}/versions`,
`policy pack-versions`, `policy audit` et `views versions` rendent toujours la collection entière, sans `offset` ni `limit`.
Enfin, ce changement ne promet pas que toute réponse MCP tient dans 1 Mio : d'autres outils (les parcours de graphe `traverse_portfolio`,
`trace_requirement` et `get_change_context`, `export_saved_view`, entre autres) ne prennent pas de page, et le pire cas en octets de
plusieurs d'entre eux dépasse le cadre par arithmétique (non mesuré) ; la liste, avec sa raison par outil, est dans l'ADR-0107.

Décision : [ADR-0107, amendement du 29 septembre 2026 (MCP-2)](../adr/0107-one-vocabulary-for-a-paginated-response.md).

### MCP, pages des trois outils de collection croissante : aucune sortie ne change, trois phrases sont corrigées

Aucun comportement ne change. Le Javadoc de `PageArguments` (MCP-2) affirmait trois choses que le code ne tenait pas : qu'un `limit = 0` est refusé « sans que le store
ait été ouvert » (le refus de `slice` précède la **lecture de la collection**, mais un appel direct du handler a déjà ouvert le store ; sous le serveur, le SDK refuse `limit = 0`
contre le schéma publié avant tout handler, donc aucun store n'est ouvert : établi par lecture du code et du SDK, non mesuré de bout en bout) ;
que les bornes de `limit` sont celles du catalogue « que `PageRequest` applique aussi » (vrai du seul **maximum**, 100, et rien ne le tenait : `PageBoundsAgreementTest` le tient
désormais, ainsi que trois descriptions d'outils qui écrivaient « default 50, maximum 100 » à la main) ; et que tout outil sur une collection qui ne fait que croître prend une page
(trois outils le font ; `list_policy_packs` et d'autres restent rendus entiers, avec leur raison dans l'ADR-0107).

Décision : [ADR-0107, amendement du 30 septembre 2026 (MCP-6)](../adr/0107-one-vocabulary-for-a-paginated-response.md).

### Exports (CLI `export query`, MCP `export_query`, HTTP `POST /api/v1/exports`) : `limit` et `offset` sont refusés

Jusqu'à 1.2.0, ces trois surfaces acceptaient `limit` et `offset` pour un export et les ignoraient : `export query --limit 10`,
`export_query` avec `limit: 10` et `POST /api/v1/exports` avec `"query":{"limit":10}` rendaient l'export **complet** (jusqu'à
10 000 lignes), ou une erreur de budget sur le total, pour une demande que l'appelant croyait bornée. Un export a toujours été
complet (`QueryExportService` matérialise toute la vue et ne lit jamais la page) ; c'est l'entrée qui promettait le contraire.

À partir de 1.2.1, un export est complet **par contrat** et refuse tout paramètre de page :

| Surface | Avant | Après |
|---|---|---|
| CLI `export query` | `--limit N`, `--offset N` acceptés et ignorés | code d'usage `2`, `--limit is not accepted by export: an export is always complete, bounded by 10000 rows and never by a page` ; rien n'est imprimé |
| MCP `export_query` | schéma déclarant `offset` et `limit` | schéma sans les deux ; un appel qui en porte un est refusé avant le handler, comme tout argument inconnu (le texte du refus est celui du SDK) |
| HTTP `POST /api/v1/exports` | `query` acceptait `offset` et `limit` | `query` porte `entity`, `filter`, `sort`, `fields` ; `limit` ou `offset` répond `400 BAD_REQUEST` |

L'export d'une saved view (`export view`, `export_saved_view`, `POST /api/v1/saved-views/{id}/export`) n'a jamais pris de page et ignore la
page stockée avec la vue : il le dit désormais dans l'aide et les descriptions. Ce qui borne un export est le nombre de lignes
(10 000) et la taille (`QueryBudgets`), avec un échec explicite au-delà.

**Migration.** Retirer `--limit`, `--offset`, `limit` et `offset` des appels d'export : ils n'ont jamais eu d'effet. Pour lire une
tranche, utiliser `query execute` / `execute_query` / `POST /api/v1/queries/execute`, qui paginent. Le contrat OpenAPI M24
(`morpheus-v1-query-m24.yaml`) déclare le nouveau schéma d'entrée `ExportQueryRequest`.

**Ce que cela laisse ouvert : un export de plus d'1 Mio n'est pas rendu par `export_query`.** Un export ne se pagine pas, et le cadre du
transport MCP est d'1 Mio alors que le budget d'un export est de 10 Mio. Un export valide de plus d'1 Mio est donc **refusé par le transport**
(erreur `MCP_RESPONSE_TOO_LARGE`, dont le message conseille de « réessayer avec un `limit` plus petit » : **ce conseil ne peut pas être suivi
sur `export_query`**, qui ne prend pas `limit`). Ce n'est pas nouveau (l'outil acceptait `limit` sans en tenir compte) et ce n'est pas corrigé
dans 1.2.1. Recours : sur MCP, lire les mêmes lignes avec `execute_query`, paginé (`offset`, `limit` de 1 à 500), qui ne rend pas l'enveloppe
d'un export ni ses rendus CSV et Markdown ; ou exporter par le CLI (`export query`) ou par HTTP (`POST /api/v1/exports`), qui n'ont pas ce
cadre. `export_saved_view` a la même limite et pas de recours MCP paginé (CLI `export view` ou HTTP). Le recours par `execute_query` est
prouvé par un test sur le serveur réel ; ceux du CLI et de HTTP sont lus dans le code, non mesurés sur un export de plus d'1 Mio.

Décision : [ADR-0102, amendement du 29 septembre 2026 (MCP-3)](../adr/0102-mcp-failure-contract-is-one-rule.md).

### Synchronisation : l'état de sync s'écrit avec une révision attendue

Jusqu'à 1.2.0, `recordAttempt` et `commitSuccessfulSync` écrivaient l'état de synchronisation par un upsert aveugle. Deux syncs concurrentes
du même projet ouvrent chacune leur connexion SQLite : le `synchronized` de l'adaptateur ne les excluait pas l'une de l'autre. Si A enregistrait
`SCAN_INCOMPLETE` pendant que B, qui avait lu l'état avant, terminait avec succès, le drapeau repassait à `NULL` et le `prepare()` suivant choisissait
le mode `INCREMENTAL` sur un inventaire que le système savait incomplet.

À partir de 1.2.1 :

- l'état porte une révision (colonne `revision`, schéma **19**) ; toute écriture énonce la révision qu'elle a lue et l'avance de un ;
- une divergence est l'échec nommé `SyncStateConflictException` (HTTP `409 STATE_CONFLICT`, code de sortie CLI `4`), et l'état persisté est celui du premier
  écrivain ; un plan dépassé ne réessaie pas et ne marque pas la baseline incohérente : il échoue ;
- `SyncPlan` porte `stateRevision` (la révision après l'enregistrement de sa tentative).

**Migration.** La migration `V019` ajoute la colonne ; les lignes existantes valent 1 (0 signifie « aucune ligne ») et une base 1.2.0 s'ouvre sans action.
Un client qui enchaînait deux `sync` concurrents sur le même projet voit maintenant l'un d'eux échouer au lieu d'un succès silencieux et faux : relancer
la sync. Un appelant du port `SyncStateStore` (tests, doubles) doit passer la révision attendue.

Décision : [ADR-0054, amendement du 25 septembre 2026 (SYN-1)](../adr/0054-persisted-sync-state-archives-and-freshness.md).

### Vues sauvegardées : ce qui s'écrit se relit, et une vue illisible est nommée

Jusqu'à 1.2.0, le codec bornait au décodage à 64 le nombre de valeurs d'un prédicat `IN` (avec la constante qui borne le nombre de
*prédicats* d'une requête), alors qu'aucune frontière d'entrée ne posait cette limite. Un `create_saved_view` avec un `IN` de 65 clés était
accepté puis devenait illisible ; et, comme `list` décode chaque ligne, **une seule vue empoisonnée rendait la liste entière de son scope
inexploitable**, sans aucune suppression possible.

À partir de 1.2.1 :

- le nombre de valeurs d'un `IN` a sa propre borne, `MAX_PREDICATE_VALUES` (256), appliquée au parse (message : `IN list for <champ> exceeds
  256 values`), à la validation, à l'encodage et au décodage : la même constante. Un `IN` de 65 à 256 valeurs, refusé au décodage avant, est accepté ;
- `list` ne tombe plus sur une ligne indécodable : la vue est **rendue** avec son identifiant, son nom, sa révision, son statut, ses dates et un champ
  `unreadableReason` (sans `query`) ;
- l'archivage (`archive`) ne décode pas la définition : une vue illisible peut être archivée, avec une révision d'historique conservée.

La forme d'une vue lisible ne change pas : seule une vue illisible porte `unreadableReason`.

**Migration.** Une installation qui contient déjà une vue illisible la voit apparaître dans `list` avec sa raison ; l'archiver
(`morpheus views archive`, `archive_saved_view`, `POST /api/v1/saved-views/{id}/archive`) la retire de la liste des vues actives.

Décision : [ADR-0108, amendement du 25 septembre 2026 (QRY-1)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### Composition multi-provider : l'union est le mode, et elle cesse d'être muette

Jusqu'à 1.2.0, un conflit annonçait `SELECTED_BY_PRECEDENCE` (« provider sélectionné ») alors que le contenu publié était l'union : les entités des
deux providers, sous des identités différentes (ADR-0023). Quand les providers s'accordaient sur une valeur, **aucun conflit** n'était émis : la
duplication était totalement muette ; et seuls trois types d'entité sur huit étaient observés. Les services de qualité comptaient donc une
population dupliquée.

À partir de 1.2.1 :

- `SELECTED_BY_PRECEDENCE` devient **`PRECEDENCE_RECORDED`** (précédence *enregistrée*, toutes les observations *publiées*) ; le motif est réécrit. Le nom est
  exposé (HTTP, CLI, MCP, OpenAPI) : un consommateur qui branchait sur l'ancien nom doit suivre. Les lignes déjà persistées sont migrées (`V020`) ;
- un accord entre providers émet **`IDENTICAL`**, valeur qui existait dans l'énuméré sans jamais être produite ;
- tous les types publiés sont observés : ajout de `PROJECT` (`displayName` ; `rootLocator` comme empreinte SHA-256, jamais comme chemin),
  `SCENARIO`, `CONSTRAINT`, `DESIGN_DECISION`, `TASK`, `ACCEPTANCE_CRITERION` ;
- un ratio de couverture (exigences, tâches) évalué par une politique est **`UNKNOWN`** quand le snapshot actif publie des clés dupliquées de ce type ; un
  comptage reste un comptage.

Une composition à un seul provider est inchangée : mêmes entités, aucun conflit, mêmes ratios. En revanche des compositions multi-providers qui ne
rapportaient aucun conflit en rapportent maintenant (les `IDENTICAL`), et un seuil de couverture peut passer de mesuré à `UNKNOWN`.

Décision : [ADR-0084, amendement du 26 septembre 2026 (CMP-1, CMP-2)](../adr/0084-provider-neutral-multi-provider-composition.md).

### CLI `composition status` et `composition conflicts` : `--revision` est refusé

Jusqu'à 1.2.0, `composition status` et `composition conflicts` acceptaient `--revision REV` sans le lire : seule
`composition sync` s'en sert. L'option était ignorée en silence et la commande rendait `0`, comme si la révision avait
été prise en compte.

À partir de 1.2.1, ces deux actions refusent toute option autre que `--project`, avant d'accéder à l'état :

```text
MORPHEUS error [2]: unknown option: --revision
```

Dans le même mouvement, `external-references list` et `resolve` vérifient leurs options avant de chercher le projet :
une option inconnue y rend `2` au lieu de `4` (« projet introuvable ») quand le projet n'existe pas, et une
sous-commande inconnue aussi (`unknown external-references subcommand`). Le code d'une invocation correcte ne change pas.

**Migration.** Retirer `--revision` des appels à `composition status` et `composition conflicts` ; il n'a jamais eu
d'effet.

Décision : [ADR-0103, amendement du 26 septembre 2026 (CLI-8)](../adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md) ;
garde `CliOptionParsingRefusesUnknownOptionsTest`, qui découvre désormais toutes les familles de parseurs du CLI.

### Qualité (`quality`, diagnostics HTTP) : un ratio sur population vide n'est plus publié comme une mesure

Jusqu'à 1.2.0, un projet dont le snapshot actif ne publiait aucune exigence rendait `requirementCoverageRatio: 1.0`
— et de même `taskCoverageRatio: 1.0` sans tâche. Ce `1.0` est une convention de validation, pas une observation : la
frontière policy le lisait déjà `UNKNOWN`, mais `morpheus quality` l'imprimait comme une couverture complète, et une
garde de CI écrite sur ce champ passait au vert sur un projet vide.

À partir de 1.2.1 :

- **JSON** (`morpheus --json quality`, `GET /api/v1/projects/{projectId}/diagnostics`) : l'objet `metrics` gagne deux
  champs, `requirementCoverageStatus` et `taskCoverageStatus`, qui valent `MEASURED` ou `UNDEFINED_EMPTY_POPULATION`.
  Les ratios gardent leur type (`double`) et leur valeur ; aucun autre champ ne change. Pour un projet qui a des
  exigences et des tâches, la seule différence est l'ajout des deux statuts à `MEASURED`.
- **Texte** (`morpheus quality`) : un ratio indéfini s'imprime `UNDEFINED_EMPTY_POPULATION` au lieu de `1.0`, par
  exemple `requirementCoverage=UNDEFINED_EMPTY_POPULATION`. Un ratio mesuré s'imprime comme avant.

Le code de sortie ne change pas (`0`).

**Migration.** Une garde qui lit `metrics.requirementCoverageRatio` ou `metrics.taskCoverageRatio` doit d'abord lire le
statut correspondant et traiter `UNDEFINED_EMPTY_POPULATION` comme une absence de mesure. **Un client qui ignore le
nouveau champ continue de lire `1.0` sur un projet vide** : c'est le prix de la compatibilité, et c'est pourquoi il faut
lire le statut. Un script qui analysait la sortie texte comme un nombre reçoit maintenant un mot sur un projet vide.
`MEASURED` ne dit que la population n'est pas vide : sur une composition multi-provider qui publie deux fois la même
exigence, le ratio reste `MEASURED` ici alors qu'une policy le lit `UNKNOWN` (voir la composition plus haut).

Décision : [ADR-0108, amendement du 26 septembre 2026 (CLI-4)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### CLI `portfolio`, `query`, `views`, `export` et `policy` : une option passée vide est refusée

Jusqu'à 1.2.0, ces commandes lisaient une option passée avec une valeur vide ou blanche (`--project ""`, `--limit "  "`)
comme une option **absente**. La conversion était silencieuse :

- `portfolio references --portfolio P --project ""` rendait **toutes** les références du portefeuille au lieu de celles
  du projet ;
- `portfolio add-project … --workspace ""` enregistrait une appartenance sans workspace ;
- `query execute … --limit ""` retombait sur la taille de page par défaut ;
- `policy evaluate … --id ""` évaluait tous les packs actifs de la portée au lieu d'un seul, avec le code de leur
  décision commune.

À partir de 1.2.1, la valeur vide ou blanche est refusée avant tout traitement, avec le nom de l'option, code `2`
(`USAGE`) :

```text
MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
```

Rien n'est écrit quand le refus porte sur une action d'écriture. Une invocation qui ne passe pas l'option se comporte
exactement comme avant. Une option inconnue passée vide reste signalée comme inconnue (`unknown option: --projet`). Une option **obligatoire** passée vide reçoit ce message au lieu de `--… is required` ; le code
reste `2`.

**Migration.** Un script qui passait une variable éventuellement vide (`--project "$PROJECT"`) reçoit maintenant le code
`2` quand la variable est vide. C'est le but : le comportement par défaut qu'il obtenait était silencieusement faux.
**Omettre l'option** quand il n'y a pas de valeur, au lieu de la passer vide. Aucune de ces options n'a de sens pour une
valeur vide que l'omission n'ait pas déjà : `views update` reconstruit toute la définition, donc omettre `--filter`
suffit à retirer un filtre.

Décision : [ADR-0108, amendement du 26 septembre 2026 (CLI-7)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### CLI (toutes les autres commandes) et lanceurs `api`, `mcp`, `api --remote` : une option passée vide est refusée

Le refus décrit ci-dessus pour `portfolio`, `query`, `views`, `export` et `policy` s'étend à **toutes** les commandes du
CLI et aux trois lanceurs de serveur. Jusqu'à 1.2.0, une option passée avec une valeur vide ou blanche y était, selon la
commande, lue comme absente, résolue comme le répertoire courant, ou refusée avec un message qui ne nommait pas le vide :

- `acceptance-criteria list --project P --change ""` listait **tous** les critères du projet au lieu de ceux du change ;
- `acceptance-criteria list … --limit ""` et `constraints evaluate … --limit ""` retombaient sur la taille de page par
  défaut ;
- `sync --project P --revision ""` et `composition sync --project P --revision ""` publiaient un snapshot sans révision ;
- `change-orchestration state … --lifecycle ""` répondait « cycle de vie non observé » (`UNAVAILABLE`), et
  `--abandonment-reason ""` valait une raison absente ;
- `--data-dir ""`, `--config-dir ""` et `--db ""` désignaient **le répertoire courant** : `paths` l'affichait, et une
  commande qui ouvre le store créait `morpheus.db` à cet endroit ;
- `server backup create --output-dir ""` durcissait les permissions du répertoire courant puis **y écrivait la
  sauvegarde**, et `server identity … --auth-file ""` y cherchait le fichier d'identités ;
- `api --remote --workspace-root ""` ajoutait le répertoire courant aux **racines de workspace autorisées** du serveur
  distant ; `--auth-file ""`, `--tls-keystore ""` et `--provider-plugin-dir ""` le prenaient pour fichier d'identités,
  keystore ou répertoire de plugins.

Chaque cas rendait `0`, ou échouait plus loin sans nommer l'option. À partir de 1.2.1, la valeur vide ou blanche est
refusée à la lecture des arguments, avec le nom de l'option, code `2` (`USAGE`) :

```text
MORPHEUS error [2]: --change requires a non-blank value; omit the option to leave it unset
```

Les commandes qui préfixent leurs erreurs autrement (`server`, `reason`, `update-check`, `provider-plugins`) gardent leur
préfixe ; les lanceurs refusent au démarrage, avant d'ouvrir un port ou le transport STDIO. Rien n'est enregistré quand le
refus porte sur une commande d'écriture (`sync`, `composition sync`, `lifecycle apply`, `projects add`, `server …`). Une
invocation qui ne passe pas l'option se comporte exactement comme avant.

Là où une valeur vide était déjà refusée, **le message change**, pas le code : `--… is required`,
`missing required option --…`, `--host must not be blank`, `--… must be an integer` et `manifest must not be blank`
deviennent `--… requires a non-blank value` pour une valeur vide. Un outil qui analysait ces messages doit accepter le
nouveau ; un outil qui ne regarde que le code de sortie n'a rien à changer.

Les variables d'environnement ne changent pas : `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB` et les
variables `MORPHEUS_SERVER_*` vides restent lues comme non définies.

**Migration.** Un script qui passait une variable éventuellement vide (`--data-dir "$DATA"`, `--change "$CHANGE"`,
`--workspace-root "$ROOT"`) reçoit maintenant le code `2` quand elle est vide. **Omettre l'option** quand il n'y a pas
de valeur, au lieu de la passer vide. Pour viser réellement le répertoire courant, l'écrire : `--data-dir .`.

Décision : [ADR-0108, amendement du 26 septembre 2026 (CLI-7, suite)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### CLI et lanceurs `api`, `mcp`, `api --remote` : une valeur faite d'espaces Unicode est une valeur blanche

Le refus d'une option passée vide, décrit ci-dessus, tenait pour blanche une valeur que `trim()` vide : espaces ASCII,
tabulations et caractères de contrôle. Une valeur faite d'un **espace Unicode** — espace cadratin `U+2003`, espace
idéographique `U+3000` — n'était pas blanche pour ce test, et six lectures d'options en appliquaient un autre. Selon la
commande, une telle valeur était donc :

- signalée comme une option **absente** : `server identity create --principal <U+2003>` répondait
  `--principal is required`, `reason analyze --question <U+2003>` répondait `missing required option --question` ;
- refusée plus loin sans nom d'option : `external-references list --project <U+2003>` répondait
  `Invalid UUID string`, après avoir créé la base de données ;
- ou **acceptée** : `requirements find --query <U+2003>` rendait, code `0`, exactement la sortie de la même commande
  sans `--query`, et `--data-dir <U+2003>` désignait un répertoire de ce nom dans le répertoire courant, pour
  toutes les commandes comme pour les lanceurs.

À partir de 1.2.1, une valeur est blanche si **chacun de ses caractères** est soit un blanc au sens de Java
(`Character.isWhitespace`, ce que retient `String.isBlank`), soit inférieur ou égal à `U+0020` (ce que retire
`trim()`) : espaces Unicode, caractères de contrôle, ou un mélange des deux. Elle est refusée avec le même message et le
même code `2` que la valeur vide :

```text
MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
```

Le message `--host must not be blank` des lanceurs `api` et `api --remote` disparaît au profit de celui-ci. Une option
**omise** reçoit toujours `--… is required` ou `missing required option --…`. Un espace insécable (`U+00A0`), l'espace
sans chasse (`U+200B`) et la marque d'ordre des octets (`U+FEFF`) ne sont pas blancs et restent des valeurs. Les
variables d'environnement ne changent pas.

**Migration.** Aucune pour une valeur qui porte un contenu. Un fichier ou un répertoire dont le nom entier est un
espace Unicode se désigne par une orthographe qui n'est pas blanche : `--data-dir ./<nom>`.

Décision : [ADR-0108, amendement du 30 septembre 2026 (CLI-10, CLI-11)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### CLI et lanceurs `api`, `mcp`, `api --remote` : une option répétée est refusée

Jusqu'à 1.2.0, une option à valeur donnée deux fois gardait, dans une partie du CLI, **sa dernière valeur sans rien
dire** :

- `--data-dir`, `--config-dir` et `--db` devant n'importe quelle commande : `morpheus --data-dir a --data-dir b projects list`
  lisait le store de `b`, code `0` ;
- les lanceurs `api`, `mcp --stdio` et `api --remote` : `--host`, `--port`, la disposition, `--auth-file`,
  `--tls-keystore`, `--provider-plugin-dir` et `--max-concurrent`, y compris quand les deux occurrences mélangeaient
  les orthographes `--x v` et `--x=v` ;
- `server` pour la disposition, dans les deux orthographes ;
- `update-check --manifest`, `provider-plugins --directory|--plugin|--workspace|--sha256` et
  `reason analyze --max-claims`.

Les autres options du CLI refusaient déjà la répétition. À partir de 1.2.1, toutes le font, code `2` (`USAGE`), avec le
même message :

```text
MORPHEUS error [2]: duplicate option: --data-dir
```

Les deux orthographes d'une option sont une seule option : `--data-dir a --data-dir=b` est refusé comme
`--data-dir a --data-dir b`. Les lanceurs refusent au démarrage, avant d'ouvrir un port ou le transport STDIO ; aucune
commande n'écrit quoi que ce soit avant le refus.

Ne changent pas : `api --remote --workspace-root`, qui nomme une liste et accepte toujours plusieurs occurrences, dans
l'une ou l'autre orthographe ; les drapeaux sans valeur `--json`, `--stdio` et `--remote`, qui peuvent être répétés sans
effet. Le message de `server` et de `reason analyze --question` pour une option répétée passe de `duplicate --x` à
`duplicate option: --x` ; leur code reste `2`.

**Migration.** Un script ou un enveloppeur qui ajoutait une option déjà présente (`--data-dir "$DEFAULT" … --data-dir "$ICI"`)
pour la remplacer reçoit maintenant le code `2`. **Passer l'option une seule fois**, avec la valeur voulue. Pour
plusieurs racines de workspace, `--workspace-root` reste répétable.

Décision : [ADR-0108, amendement du 26 septembre 2026 (CLI-7, répétition)](../adr/0108-a-response-says-what-it-could-not-observe.md).

### OpenAPI : `CompositionConflict.entityType` publie dix valeurs, `direction` de M23 publie `OUTGOING`/`INCOMING`

Dans 1.2.0, `CompositionEntityType` comptait quatre valeurs (`SPECIFICATION`, `REQUIREMENT`, `CHANGE`, `IDENTITY`) et
`docs/openapi/morpheus-v1.yaml` les listait exactement. La composition de 1.2.1 observe dix types (voir « Composition
multi-provider » plus haut), et le contrat les publie enfin : `GET /api/v1/projects/{id}/composition` peut renvoyer, dans
`entityType`, les six valeurs `PROJECT`, `SCENARIO`, `CONSTRAINT`, `DESIGN_DECISION`, `TASK` et `ACCEPTANCE_CRITERION`, que
le schéma de `develop` n'annonçait pas. L'énuméré est dans l'ordre de `CompositionEntityType` : `PROJECT`,
`SPECIFICATION`, `REQUIREMENT`, `SCENARIO`, `CHANGE`, `CONSTRAINT`, `DESIGN_DECISION`, `TASK`, `ACCEPTANCE_CRITERION`,
`IDENTITY`.

**Migration.** Un client généré ou écrit d'après le schéma de 1.2.0, avec un `switch` exhaustif sur les quatre valeurs,
gagne six cas : régénérer le client, ou traiter une valeur inconnue sans lever.

Le même balayage a trouvé une dérive qui, elle, existait dans 1.2.0 : `morpheus-v1-portfolio-m23.yaml` annonçait pour
`TraversalRequest.direction` `OUTBOUND`, `INBOUND`, `BOTH`, alors que le serveur, le CLI (`--direction`) et le serveur MCP
n'acceptent que `OUTGOING`, `INCOMING` et `BOTH` (sans distinction de casse). Le contrat et `docs/user/PORTFOLIOS.md` sont
corrigés ; le code ne change pas. Une requête écrite d'après l'ancien contrat avec `OUTBOUND` ou `INBOUND` était refusée
(HTTP 400, code d'usage du CLI, erreur d'outil MCP) et l'est toujours : **écrire `OUTGOING` ou `INCOMING`**.

Décision : [ADR-0103, amendement du 29 septembre 2026 (API-5)](../adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md).

### HTTP `GET /api/v1/projects/{id}/versions` : la lignée est une page

Jusqu'à 1.2.0, la route rendait toute la lignée publiée dans `items`, sans `offset` ni `limit` (ces paramètres étaient refusés).
À partir de 1.2.1, elle rend une **page**, avec le vocabulaire commun : `projectId`, `retentionPolicy`, `offset`, `limit`,
`totalMatches`, `hasMore`, `items` — quatre clés de plus. `offset` (0 par défaut) et `limit` (1 à 100, 50 par défaut) sont
acceptés ; l'ordre reste du plus ancien au plus récent.

**Migration.** Un client qui lisait `items` en entier n'obtient plus que les 50 premières publications : **lire `hasMore` et
paginer** (`?offset=50`, puis suivants). Une lignée de moins de 50 publications rend exactement les mêmes éléments qu'avant, aux clés de
page près. Le contrat OpenAPI déclare `offset` et `limit` sur cette route.

Décision : [ADR-0107, amendement du 29 septembre 2026 (API-3)](../adr/0107-one-vocabulary-for-a-paginated-response.md).

### HTTP `saved-views`, `policy-overrides`, `policy-activations` : la query string passe par le budget partagé

Jusqu'à 1.2.0, ces trois routes (`GET /api/v1/saved-views`, `/api/v1/policy-overrides`, `/api/v1/policy-activations`) parsaient
leur query string elles-mêmes, sans le budget que toutes les autres routes appliquent. À partir de 1.2.1 elles passent par le même
parseur : une query de plus de 16 paramètres ou de plus de 16 Kio est refusée en `400`, comme ailleurs.

Différences visibles, toutes sur des requêtes déjà mal formées (le statut reste `400` là où il l'était déjà) :

- un segment vide (`?scopeKind=PROJECT&&scopeId=…`, ou une `&` finale) est **accepté** au lieu d'être refusé ;
- un paramètre répété répond `duplicate query parameter: <clé>` (auparavant `invalid or duplicate query parameter[: <clé>]`) ;
- un nom de paramètre vide (`?=x`) répond `query parameter name must not be blank` (auparavant `invalid or duplicate query parameter`) ;
- un pourcentage invalide (`%zz`) répond `query parameter uses an invalid percent-encoding` au lieu du message brut du JDK ;
- un segment fait uniquement d'espaces est ignoré comme un segment vide ;
- `policy-overrides` répond `query parameter is required: <clé>` pour une clé absente (auparavant `missing query parameter: <clé>`).

Un client qui branchait sur l'ancien texte d'erreur doit brancher sur le code (`BAD_REQUEST`, HTTP 400), pas sur le message. Une requête
légitime (`?scopeKind=…&scopeId=…`) répond à l'identique.

Décision : [ADR-0103, amendement du 29 septembre 2026 (API-4)](../adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md).

### CLI `policy`, `query`, `views` et `export` : un refus d'option ne crée plus de base de données

Jusqu'à 1.2.0, ces quatre commandes ouvraient le store **avant** de vérifier leur action et leurs options. L'ouverture
crée le répertoire de données, le fichier de base et son schéma : une invocation refusée pour une option ou une action
inconnue rendait bien le code `2`, mais laissait une base créée à un emplacement que rien n'avait validé :

```text
morpheus --data-dir /tmp/neuf policy pack list --projet P
MORPHEUS error [2]: unknown option: --projet
```

`/tmp/neuf/morpheus.db` existait après ce refus. Les refus que 1.2.1 ajoute plus haut (valeur vide, option répétée) et,
pour `export`, le format invalide et le refus de page, venaient eux aussi après l'ouverture. À partir de 1.2.1, tous ces
refus précèdent l'ouverture, comme dans `portfolio` : rien n'est créé. Chaque refus garde son code et son message, et
chaque action accepte exactement les options qu'elle acceptait. Une seule préséance change, en mieux : un refus d'usage
(code `2`) précède désormais une erreur d'ouverture du store (répertoire non inscriptible, base verrouillée), qui rendait
le code `4` et masquait l'option fautive.

Ne changent pas : une option obligatoire absente, un identifiant mal formé, un entier invalide ou une portée absente sont
refusés après l'ouverture, comme avant. `projects`, `changes` et `change-orchestration` ouvrent encore le store avant de
refuser une partie de leurs options.

**Migration.** Aucune : seul disparaît l'effet de bord d'une invocation refusée.

Décision : [ADR-0108, amendement du 30 septembre 2026 (CLI-9)](../adr/0108-a-response-says-what-it-could-not-observe.md).
### Synchronisation OpenSpec : un refus nomme le fichier tel qu'il existe

Jusqu'à 1.2.0, l'échec de lecture d'un fichier OpenSpec ne nommait que le groupe (`current`, `changes`,
`requirement-deltas`) et le type de l'exception. À partir de 1.2.1, le refus nomme le fichier en cause, relatif à la racine
du workspace et écrit avec `/` sur toutes les plateformes (`openspec/specs/auth-session/spec.md: OpenSpec specification has
no title`). Le nom n'est jamais réécrit caractère par caractère : il est celui que l'opérateur trouvera sur son disque. Le
locator publié pour le même fichier, lui, reste normalisé (`file:openspec/specs/auth-session/spec.md`) ; les deux
s'accordent pour tout nom sans `\`.

Un seul cas change de texte, et seulement sous Linux et macOS, où `\` est un caractère légal d'un nom : un fichier dans un
répertoire nommé `a\b`. Le refus ne le nomme plus `openspec/specs/a/b/spec.md` (un chemin qui n'existe pas), et un texte
qui contient `\` est pris pour un emplacement du serveur par les filtres de divulgation, que toutes les surfaces
n'appliquent pas. Selon la surface :

- la CLI `sync` écrit le nom exact et la cause (`openspec/specs/a\b/spec.md: …`) : **mieux** qu'avant ;
- la synchronisation HTTP, locale et remote, répond `InvalidOpenSpecSource` : ni le fichier ni la cause — **moins** qu'avant ;
- `composition sync` rapporte `OpenSpec content reader failed for group current: InvalidOpenSpecSource` : **moins** qu'avant,
  et le refus de publication ne le compte pas comme retenu.

Le refus de budget de preuve (`evidence bytes exceeds budget for …`) suit la même règle. Le locator publié reste
`file:openspec/specs/a/b/spec.md`. Sous Windows `\` est le séparateur : ce cas n'existe pas et rien ne change. Le
constat plus large — un texte retenu devient un type nu sans dire qu'il a été retenu — est consigné dans l'ADR et
instruit à part.

**Migration.** Aucune. Un client qui branchait sur le texte d'un refus doit brancher sur le code (`INVALID_SOURCE`).

Décision : [ADR-0028, amendement du 30 septembre 2026 (PRV-6)](../adr/0028-unified-provider-read-contract.md).
### Deltas OpenSpec : un bloc de code jamais fermé est signalé, et `sync` nomme ce qu'il a sauté

Jusqu'à 1.2.0, une ligne qui commence par trois accents graves ou tildes suivis de texte (```` ```inline``` markers ````)
ouvrait un bloc de code jusqu'à la fin du fichier de delta : toute section qui suivait était ignorée, une exigence placée
après pouvait sortir avec le genre d'une section précédente (`REMOVED` au lieu d'`ADDED`), sans diagnostic, et la
catégorie `REQUIREMENT_DELTAS` restait `READ`. Un `## REMOVED Requirements` écrit dans un exemple de code changeait aussi
le genre des exigences suivantes. Et `sync` ne rapportait qu'un nombre de diagnostics : une exigence sautée n'était
nommée nulle part.

À partir de 1.2.1 :

- un titre de section de delta écrit dans un bloc de code n'est plus interprété. Un exemple fermé placé sous une
  section étrangère (`## Notes`) et contenant `## ADDED Requirements` puis `### Requirement: Phantom` ne publie plus
  `ADDED Phantom` : `Phantom` est nommée comme exigence sautée, et la catégorie passe à `PARTIAL` à cause de cet
  exemple (faux positif assumé) ;
- un bloc jamais fermé produit l'avertissement **`UNCLOSED_CODE_FENCE`** (nouveau code de `DiagnosticCode`), avec la
  ligne d'ouverture ; la catégorie passe à `PARTIAL` ; **toute** exigence placée après l'ouverture n'est plus publiée,
  elle est nommée par un `PARTIAL_INGESTION` (détail `requirement`) — **y compris quand un en-tête de delta bien formé
  suit le bloc**, cas où 1.2.0 la publiait avec le bon genre. Exemple : un fichier dont la ligne 1 est ```` ``` ````,
  suivi de `## ADDED Requirements` puis `### Requirement: A` publiait `ADDED A` en 1.2.0 ; en 1.2.1 il ne publie rien et
  nomme `A`. OpenSpec amont perd ces exigences sans rien signaler ;
- `sync` porte `diagnostics` : au plus 32 diagnostics (`code`, `severity`, `message`, `details`, `source`), les plus
  graves d'abord, avec `truncated` et `truncationReason` (`DIAGNOSTIC_LIMIT_REACHED:32`). En CLI, une ligne
  `diagnostic=…` par élément ; en JSON (`--json`) et en HTTP (`POST /api/v1/projects/{projectId}/sync`), une clé
  `diagnostics` de plus. Côté HTTP, seuls des détails allowlistés et des valeurs qui ne nomment aucun emplacement du
  serveur sont relayés. La réponse de `syncProject` est désormais typée dans l'OpenAPI (`SyncResult`).

Un fichier de delta sans bloc de code se lit à l'identique : mesuré sur les trois fichiers de delta du dépôt, avant et
après, deltas, preuves et diagnostics sont inchangés.

**Migration.** Un fichier de delta qui contient un bloc jamais fermé publie **moins** de deltas qu'en 1.2.0 : aucune
exigence qui suit l'ouverture n'est publiée — celles dont le genre était deviné à tort, mais aussi celles qu'un en-tête
de delta bien formé plaçait correctement —, et chacune est nommée dans `diagnostics`. Fermer le bloc (ou retirer la
suite d'accents graves en début de ligne) les rétablit. Un client qui lisait la réponse
de `sync` clé par clé n'est pas affecté ; un client qui validait la réponse contre un schéma fermé doit accepter
`diagnostics`.

Décision : [ADR-0028, amendement du 30 septembre 2026 (PRV-2, suite)](../adr/0028-unified-provider-read-contract.md).
