# ADR-0109 — Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 8 octobre 2026
- Dépend de : ADR-0003 (port de stockage de connaissance), ADR-0017 (Maven multi-module), ADR-0065 (HTTP
  `jdk.httpserver`), ADR-0062 (SDK MCP natif)
- Amende : ADR-0003 (§ 7, « aucune dépendance de CLI/MCP/API à la base choisie »)
- Portée : `morpheus-api`, `morpheus-mcp`, `morpheus-mcp-transport`, `morpheus-domain` ;
  `morpheus-architecture-tests` (`AdapterCompositionRootArchitectureTest`)

## Contexte

ADR-0003 range parmi ses conséquences « aucune dépendance de CLI/MCP/API à la base choisie », et les règles du dépôt
disent que les adaptateurs sont frères et que le câblage est explicite dans `MorpheusMain`. Le code dit autre chose :
`morpheus-api` déclare des dépendances de compilation sur `morpheus-store-sqlite` et `morpheus-provider-openspec`,
`morpheus-mcp` sur `morpheus-store-sqlite`, et des classes de ces deux modules construisent elles-mêmes leurs stores.
Aucun ADR ne l'acceptait, aucune règle ne le bornait : le modèle documenté et le code divergeaient sans que rien le
signale (audit outillé du 8 octobre 2026, constat ARC-AUD-3).

**Mesure, par ArchUnit et non par recherche textuelle** (classes de production importées par
`morpheus-architecture-tests`, 8 octobre 2026, `develop` à `3ec3ea46`) :

| Mesure | Résultat |
|---|---|
| classes de `com.morpheus.api` / `com.morpheus.mcp` qui dépendent de `com.morpheus.store..` ou `com.morpheus.provider..` | **32** (20 `api`, 12 `mcp`) — l'audit en comptait 17 par recherche des `import` ; une seule, `MorpheusProjectSyncApiService`, atteint un provider (OpenSpec) |
| classes de `com.morpheus.integration.mcp..` qui dépendent d'un autre paquet `com.morpheus..` | **0** — règle jusqu'ici écrite dans des Javadocs de test seulement |
| cycles entre tranches de `com.morpheus.domain.(*)..` | **2** : `provider ↔ source`, `requirement ↔ scenario` — ArchUnit 1.5.1 ne signale le second qu'une fois le premier écarté |
| cycles entre tranches de `com.morpheus.application.(*)..` | **167** rapportés (`archunit.cycles.maxNumberToDetect` relevé à 2000), 16 tranches impliquées |
| cycles entre tranches de `com.morpheus.api.(*)..` | sans objet : `api` n'a pas de sous-paquet |

## Décision

Décisions du mainteneur du 8 octobre 2026.

### 1. `api` et `mcp` sont des racines de composition de leur propre runtime, par une liste nommée qui ne fait que rétrécir

Les serveurs HTTP et MCP construisent leur runtime à chaque requête ou appel d'outil, à partir du chemin de la base
qu'on leur passe. Les 32 classes mesurées sont reconnues comme les **racines de composition** de ce runtime, et
seulement elles. Elles sont nommées dans `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` :

- `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider` refuse toute **autre** classe de `api` ou `mcp` qui
  dépend d'un store ou d'un provider concret ;
- `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider` exige que la liste soit **exactement** l'ensemble
  mesuré : une racine qui ne dépend plus d'aucun store doit être retirée. La liste peut rétrécir, elle ne peut pas
  grandir en silence.

**Ajouter une racine exige d'amender cet ADR**, avec la raison pour laquelle la classe ne peut pas passer par un port
de l'application. Le reste des interdits est inchangé : `application` ne connaît aucun adaptateur
(`LayerDependencyTest`), `api` ne dépend ni de `cli` ni de `mcp`, les providers et les stores restent frères.

### 2. `morpheus-mcp-transport` ne dépend d'aucun autre paquet MORPHEUS

Le transport STDIO borné est partagé par le serveur MCP et par les clients MINOS et NEXUS : il ne connaît aucun type
MORPHEUS. Son POM l'empêche déjà ; `theMcpTransportDependsOnNoOtherMorpheusPackage` le tient le jour où une
dépendance y serait ajoutée.

### 3. Les paquets du domaine sont sans cycle, à deux exceptions nommées ; l'application est reportée

`domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` interdit tout cycle entre tranches de `com.morpheus.domain`,
sauf les deux mesurés, **nommés par la paire de types exacte** qui ferme chacun : `ProviderProbeResult → SourceLocator`
(un résultat de sonde porte le localisateur de la source sondée) et `RequirementDelta → Scenario` (un delta porte ses
scénarios, un scénario nomme son exigence). Toute autre dépendance entre ces paquets, ou tout nouveau cycle, échoue.

La même règle sur `com.morpheus.application` est **reportée** : 167 cycles sur 16 tranches demanderaient une refonte
des paquets, hors de portée d'une décision d'architecture. La mesure est consignée ici pour la prochaine décision.

## Amendement du 13 octobre 2026 — une racine qui construit des stores partage une connexion

Être nommée racine de composition donne le droit d'atteindre un store concret ; cela ne disait rien de la manière. Le
constat AUD-TRV-05 de l'audit du 9 octobre 2026 l'a montré : le runtime MCP construisait sept stores sans portée de
connexion, soit sept connexions physiques et sept vérifications de schéma par appel d'outil, là où les runtimes HTTP et
CLI en partagent une entre neuf stores.

- **Une classe de `api`, `mcp` ou `cli` qui construit des stores SQLite ouvre d'abord une `SqliteConnectionScope`.**
  `MorpheusMcpRuntime` et `MorpheusCompositionMcpTools` le font désormais, comme `ApiRuntime`, `CliRuntime` et le
  runtime interne de `MorpheusQueryApiService`.
- **Une classe dont chaque opération ne construit qu'un store en est dispensée, et elle est nommée.** Un store, c'est
  une connexion ; une portée n'y ajouterait rien. Neuf classes sont dans ce cas, listées avec l'opération concernée
  dans `SqliteConnectionScopeUseArchitectureTest`. Une classe nommée qui ouvrirait deux stores ensemble est refusée.
- **La liste des racines ne rétrécit pas par ce changement.** La spécification
  (`openspec/changes/own-the-sqlite-connection-policy-in-one-place`) prévoyait un type de runtime par adaptateur,
  paramétré par les stores d'une opération. Mesuré : après le runtime MCP, un seul site ouvrait encore deux connexions
  pour une opération. Le refaire en un type commun aurait déplacé toutes les racines nommées pour corriger un site ; la règle
  tient l'invariant sans ce déplacement.
- **Une portée ne s'imbrique pas**, et le refus a un type : `SqliteConnectionScope.NestedScopeException`.

## Second amendement du 13 octobre 2026 — la portée appartient à l'ensemble de stores, pas au transport

L'amendement précédent affirmait qu'après le runtime MCP « un seul site ouvrait encore deux connexions pour une
opération ». C'était faux, et la mesure que la tâche 1.2 du changement demandait l'a montré dès qu'elle a été faite au
pilote JDBC, sur une opération entière et non sur un runtime. Pour la même opération, sur une base déjà initialisée :

| Opération | HTTP | MCP | CLI |
|---|---|---|---|
| lister les vues sauvegardées d'un périmètre (avant) | 1 | 5 | 5 |
| lister les packs de policy (avant) | 8 | 8 | 8 |
| les deux (après) | 1 | 1 | 1 |

La règle textuelle ne lisait que `morpheus-api`, `morpheus-mcp` et `morpheus-cli`. Les deux ensembles de stores que
ces trois transports partagent — `SqliteQueryStores` (cinq stores) et `SqlitePolicyStores` (huit) — vivent dans
`morpheus-store-sqlite` et n'ouvraient aucune portée : elle ne pouvait pas les voir. Le runtime interne de
`MorpheusQueryApiService`, que le constat AUD-TRV-05 nommait, n'était pas une copie d'`ApiRuntime` : c'était une
troisième copie de `SqliteQueryRuntime`, la seule des trois à ouvrir une portée.

- **La portée est ouverte par l'ensemble de stores.** `SqliteQueryStores.open` et `SqlitePolicyStores.open` ouvrent
  la `SqliteConnectionScope` avant leur premier store et la ferment après le dernier. Une base qui ne s'ouvre pas y est
  rapportée comme elle l'était par le premier store : `KnowledgeStoreException`, message du store de connaissance.
- **Le runtime interne de `MorpheusQueryApiService` est supprimé.** L'adaptateur HTTP ouvre `SqliteQueryRuntime`,
  comme la CLI et le serveur MCP : un seul câblage des services de requête pour les trois transports. L'absorber dans
  `ApiRuntime` aurait laissé deux câblages et n'aurait rien changé aux cinq connexions de la CLI et du MCP.
- **La liste des racines ne rétrécit toujours pas.** `MorpheusQueryApiService` dépend encore de `store-sqlite`, par
  `SqliteQueryRuntime`, comme `MorpheusPolicyApiService` par `SqlitePolicyRuntime`.
- **La règle a deux mécanismes** (ADR-0103), dans `SqliteConnectionScopeUseArchitectureTest`. Le texte lit désormais
  aussi `morpheus-store-sqlite` ; dix classes sont nommées pour leur store unique, `SqliteServerMaintenance` s'y ajoute
  (la sauvegarde amène la base au schéma courant par le seul store de connaissance). Une règle sur le bytecode se tient
  à côté : toute classe de production qui construit deux stores SQLite différents dépend de la portée. Elle ne connaît
  aucune liste de modules — c'est ce qui manquait — et voit un store construit par référence de constructeur, que le
  texte n'épelle pas. Elle ne dit pas que la portée entoure les constructions, ni ne voit un même store construit deux
  fois dans un bloc : le texte tient ces deux points. Chacune a été cassée une fois.

Reste hors de cet amendement : les dix classes nommées ouvrent un store, donc une connexion, hors de toute portée. Une
opération qui passerait par deux d'entre elles en ouvrirait deux ; aucune des deux opérations mesurées ne le fait, et
la mesure n'a porté que sur ces deux-là.

## Amendement du 15 octobre 2026 — un runtime par transport, une seule classe construit les stores

Les deux amendements précédents laissaient trois restes, que le constat AUD-TRV-05 nommait encore : chaque transport
avait trois types de runtime (HTTP : `ApiRuntime`, neuf stores, plus `SqliteQueryRuntime`, cinq, et
`SqlitePolicyRuntime`, huit ; la CLI les mêmes avec `CliRuntime` ; le MCP les mêmes avec `MorpheusMcpRuntime`, sept, et
un assemblage sans type dans `MorpheusCompositionMcpTools`) ; neuf classes construisaient un store hors de toute
portée ; la liste des racines n'avait pas bougé.

- **Une seule classe construit les stores d'une opération : `SqliteOperationRuntime`** (`morpheus-store-sqlite`). Elle
  ouvre toujours la portée, avant tout store, puis construit chaque store la première fois qu'une opération le demande
  et l'inscrit aussitôt pour libération. Une opération paie les stores qu'elle lit, une connexion physique et une
  vérification de schéma. Elle câble aussi les services de requête et de policy, qui n'ont donc plus qu'un câblage.
  `SqliteQueryRuntime`, `SqlitePolicyRuntime`, `SqliteQueryStores`, `SqlitePolicyStores` et `SqliteStoreConnection.openScope`
  sont supprimés.
- **Un type de runtime par transport** : `ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime`. Chacun enveloppe
  `SqliteOperationRuntime` et ne rend que des **ports de l'application** : une classe qui lit par lui ne dépend
  d'aucun type SQLite. C'est ce qui fait rétrécir la liste — la plupart des racines n'atteignaient `store-sqlite` que
  parce que leur runtime exposait des champs typés `Sqlite…Store`. La CLI a deux fabriques nommées : `recovering`, pour
  les commandes qui récupéraient déjà les publications interrompues, et `open`, pour la requête, la policy et le
  portefeuille, qui ne le faisaient pas.
- **Une base qui ne s'ouvre pas est la même erreur partout** : `KnowledgeStoreException`, message du store de
  connaissance. HTTP et CLI levaient jusqu'ici l'`IllegalStateException` de la portée, le MCP un message propre.
- **Un store se construit après l'ouverture**, plus pendant : une base au schéma trop récent échoue au premier store
  demandé, plus à l'ouverture du runtime. Le `try` qui ouvre le runtime l'entoure dans tous les sites.

| Mesure | Avant (`984bb270`) | Après |
|---|---|---|
| `COMPOSITION_ROOTS` | 32 | **5** — `ApiRuntime`, `MorpheusMcpRuntime`, `MorpheusProjectSyncApiService` (provider OpenSpec), `MorpheusRemoteHttpServer` et son bootstrap (`SqliteServerMaintenance`) |
| types de runtime, par transport | 3 (+ un assemblage sans type en MCP) | **1** |
| classes de `api`, `mcp`, `cli`, `store-sqlite` qui construisent un store | 16 | **3** |
| … dont sous une portée qu'elles ouvrent | 6 (7 ouvertures) | **1** (1 ouverture) |
| … dont hors portée, nommées | 10 | **2** |

Les deux survivants hors portée sont nommés avec leur motif dans `SqliteConnectionScopeUseArchitectureTest` :
`SqliteServerMaintenance` (la sauvegarde agit sur le fichier et amène la base au schéma courant par le seul store de
connaissance) et `CliProjectWriteCapabilityResolver`, appelé **depuis** l'opération d'un autre runtime : un store
construit pendant qu'une portée est ouverte sur le thread emprunte sa connexion, là où un runtime ouvert à cet endroit
serait refusé comme portée imbriquée.

**Les deux règles sont renforcées**, chacune cassée puis restaurée le 15/10/2026 :

- *texte* : il lit le code sans les commentaires — une ouverture que seul un commentaire épelait suffisait —, exige
  que l'ouverture précède la première construction, et tient deux listes fermées (`SCOPE_OWNERS`,
  `ONE_STORE_PER_OPERATION`) au lieu d'un seuil « au moins six ». Cassé par trois classes de brouillon : ouverture en
  commentaire, ouverture après le store, portée reçue en paramètre sans ouverture.
- *bytecode* : une classe qui construit **un** store (et non plus deux) **appelle** `SqliteConnectionScope.open`
  elle-même, ou est nommée ; la règle demandait seulement une dépendance vers la classe de portée, qu'un paramètre ou
  la capture de son exception satisfaisaient. Cassé par les mêmes classes : elle refuse l'ouverture en commentaire et
  la portée reçue en paramètre ; elle ne voit pas l'ordre — c'est l'asymétrie pour laquelle le texte est gardé
  (ADR-0103).

`MultiResourceOwnershipContractTest` ne trouve plus aucun corps qui ouvre deux stores : sa règle reste, et
`theOperationRuntimeRegistersEveryStoreItOpensForRelease` tient la nouvelle forme — chaque store que construit le
runtime d'opération passe par la méthode qui l'inscrit pour libération.

## Alternatives écartées

- **Ramener tout le câblage dans `morpheus-cli`** (les adaptateurs ne dépendant plus que des ports de l'application,
  sous une règle `noClasses()` stricte) : c'est le modèle que décrivait la documentation, mais il exige de déplacer
  32 classes, les POMs et les bootstraps des serveurs, sans défaut observé qui le motive aujourd'hui. Il reste possible :
  la liste nommée en est le compteur.
- **Ne rien écrire** : laisser la règle non dite, c'était la situation constatée par l'audit.

## Conséquences

- ADR-0003 est amendé : sa conséquence « aucune dépendance de CLI/MCP/API à la base choisie » vaut pour la CLI et pour
  toute classe de `api` et `mcp` hors des racines nommées.
- `.claude/CLAUDE.md` et `.claude/rules/architecture.md` décrivent ce modèle.
- Une nouvelle classe de `api` ou `mcp` qui veut un store passe par une racine existante ou amende cet ADR.

## Preuves exécutables

`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java`,
quatre tests verts. Chacun a été **cassé pour prouver qu'il tient**, puis restauré (8 octobre 2026) :

- une classe `com.morpheus.api.PlantedStoreUser` portant un `SqliteSpecificationKnowledgeStore` fait échouer les deux
  tests de la décision 1 ;
- un nom ajouté à la liste sans dépendance concrète (`MorpheusHttpServer`) fait échouer le test d'égalité ;
- une dépendance `morpheus-domain` ajoutée au POM du transport, avec une classe qui l'utilise, fait échouer la
  décision 2 ;
- un record `com.morpheus.domain.provider.PlantedLocatorHolder(SourceLocator)` rouvre le cycle `provider ↔ source`
  par une autre paire de types et fait échouer la décision 3.
