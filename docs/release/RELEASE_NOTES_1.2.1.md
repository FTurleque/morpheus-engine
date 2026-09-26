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

Décision : [ADR-0108, amendement du 26 septembre 2026 (CLI-4)](../adr/0108-a-response-says-what-it-could-not-observe.md).
