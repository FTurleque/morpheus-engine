# ADR-0115 — Le runtime opérationnel est une valeur que le lanceur remet ; le parallélisme JUnit en dépend

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 13 octobre 2026
- Dépend de : ADR-0109 (racines de composition nommées, une portée de connexion par opération), ADR-0111 (un seul
  binding de journalisation, celui du produit), ADR-0112 (journal opérationnel borné en fichier)
- Portée : `morpheus-application` (`OperationalRuntime`, `LocalOperationalRuntime`), `morpheus-store-sqlite`
  (`SqliteContentionMetrics`, `SqliteConnectionScope`, `SqliteDatabaseLease`, `SqliteTransactionRunner`),
  `morpheus-cli` (`MorpheusMain`), `morpheus-architecture-tests` (`StaticOperationalRuntimeUsersArchitectureTest`)

## Contexte

Les compteurs, le sink et le recorder du processus étaient trois champs statiques de `LocalOperationalRuntime`. Tout
ce qui comptait ou consignait les atteignait par le nom de la classe, jusqu'au fond du store SQLite, dont les
compteurs de contention étaient des méthodes statiques appelées par l'exécuteur de transactions.

Un test ne pouvait donc observer que ce que toute la JVM avait écrit. `SqliteContentionObservabilityTest` lisait des
différences avant et après, ce qui ne tient que si rien d'autre ne s'exécute pendant ce temps. Le réacteur n'active
aucun parallélisme JUnit ; l'activer aurait rendu ces assertions non déterministes (constat AUD-QUA-13 de l'audit du
9 octobre 2026).

Le chemin du store n'avait aucune instance qui puisse porter des compteurs : l'exécuteur de transactions est statique
et reçoit une `Connection` nue, appelée depuis dix classes de stores, construites à cinquante-six endroits. Depuis
l'amendement d'ADR-0109 du 13 octobre 2026, une opération ouvre une portée de connexion avant ses stores : la
connexion a un propriétaire.

## Décision

1. **Le runtime est une valeur.** `OperationalRuntime` porte les compteurs, le sink, le recorder et la destination du
   journal en fichier. Deux runtimes ne partagent rien.

2. **Le lanceur le prend une fois et le remet.** `MorpheusMain` prend le runtime du processus, y nomme le répertoire
   du journal (ADR-0112), et le passe par constructeur aux commandes qu'il construit. Aucune injection par
   annotation, aucun conteneur : le câblage reste explicite.

3. **Dans le store, le recorder voyage avec la connexion.** Qui ouvre une connexion ou une portée
   (`SqliteConnectionScope.open(chemin, recorder)`) nomme le recorder ; les deux enveloppes de connexion - celle du
   bail, celle de la portée - le portent, et l'exécuteur de transactions le demande à la connexion. Aucun paramètre
   n'est ajouté aux constructeurs des stores : un store construit dans une portée est observé par qui a ouvert la
   portée. Une connexion remplacée après un échec de nettoyage garde le recorder de sa portée.

4. **Un défaut statique subsiste, et il est le même objet.** `LocalOperationalRuntime` garde un runtime pour le
   processus : c'est celui que le lanceur prend. Une connexion ouverte sans recorder, et toute classe qui n'en a pas
   encore reçu, écrivent dans celui-là. Tant que la liste ci-dessous n'est pas vide, construire un second runtime
   dans le lanceur partagerait les compteurs en deux, et la route des métriques mentirait par omission.

5. **La liste des utilisateurs du défaut est nommée et ne fait que rétrécir.** Aucun test ne le mentionne. Seul un
   point d'entrée prend le runtime du processus comme valeur.

6. **Le binding SLF4J reste sur le défaut, pour toujours.** `OperationalSlf4jServiceProvider` est instancié par le
   `ServiceLoader` (ADR-0111) : personne ne le construit, donc personne ne peut rien lui remettre. C'est la raison
   pour laquelle le défaut statique ne disparaît pas quand la liste se vide de tout le reste.

7. **Le parallélisme JUnit n'est pas activé, et ne peut pas l'être avant deux conditions.** La première est que plus
   aucun test n'observe un état partagé par la JVM ; la seconde est que la liste ci-dessous soit traitée. Cette
   décision lève un obstacle - les compteurs opérationnels - et nomme les autres.

## Ce qui partage encore de l'état entre tests

Relevé par recherche dans `src/test/java` et dans les classes que ces tests observent, le 13 octobre 2026.

| État partagé | Qui l'écrit ou le lit | Effet attendu en parallèle |
|---|---|---|
| Compteurs statiques de `SqliteConnectionScope` (`diagnostics()` : connexions physiques ouvertes, fermées, actives, pic) | lus en différence par `SqliteConnectionScopeTest`, `SqliteConnectionScopeCloseFailureTest`, `SqliteRuntimeConnectionTest`, `ApiRuntimeSqliteSessionTest`, `CliRuntimeSqliteSessionTest`, `MorpheusMcpRuntimeConnectionTest`, `MorpheusCompositionMcpToolsTest`, et les trois tests de compte de connexions | **échec observé** (voir plus bas) |
| Pilote SQLite dans `DriverManager` — **supprimé le 17/10/2026** (quatrième amendement) | `PhysicalSqliteConnections` (trois copies : `morpheus-api`, `morpheus-cli`, `morpheus-mcp`) désenregistre le pilote, enregistre un pilote compteur, puis rétablit ; utilisé par `HttpRequestConnectionCountTest`, `CliCommandConnectionCountTest`, `McpToolCallConnectionCountTest` | une connexion ouverte par un autre test pendant la fenêtre est comptée ; entre le désenregistrement et l'enregistrement, elle échoue faute de pilote |
| Propriété système `java.home` | posée puis rétablie par `MinosPeerLauncherResolutionTest` et `NexusPeerLauncherResolutionTest` | tout test qui résout un lanceur Java pendant la fenêtre lit un répertoire temporaire |
| `Locale.setDefault` | `PolicyApiEnumVocabularyLocaleTest`, `McpEnumVocabularyLocaleTest` | tout formatage sensible à la locale dans un autre test |
| Runtime opérationnel du processus, lu par la route `/metrics` | écrit par tout test qui traverse une classe de la liste. `UnforeseenFailureTraceTest` y lit une **différence exacte** (`before + 2`, puis `before`) du compteur des erreurs internes : la règle textuelle ne le voit pas, il ne nomme pas le runtime. `MorpheusApiContractTest` et `MorpheusRootHttpRoutesContractTest` n'en attendent que la présence ou la croissance | `UnforeseenFailureTraceTest` échouerait dès qu'un autre test du même module provoque une erreur interne pendant sa fenêtre ; les deux autres non |
| Fichier hors répertoire temporaire | `MorpheusRemoteLoadProfileTest` écrit `target/remote-load-profile.txt`, que la lane nocturne publie | un seul écrivain ; aucun tant qu'il le reste |
| Champs statiques d'une classe de test | `MorpheusRemoteAdversarialClientTest` (serveur, client et répertoire partagés entre ses méthodes, posés en `@BeforeAll`) | ses méthodes ne peuvent pas s'exécuter en concurrence |
| Portée de connexion liée au thread (`SqliteConnectionScope`) | toute portée qu'un test n'aurait pas fermée | le thread d'un pool réutilisé refuserait la portée du test suivant |

Aucun test n'écoute sur un port fixe : la recherche ne trouve que des ports `0`, et les littéraux `8765`, `8443` et
`12345` sont des adresses d'échanges factices ou des options analysées sans être ouvertes. Les baux
(`SqliteDatabaseLease`) et la quarantaine de l'exécuteur de transactions sont statiques mais indexés par base ou par
connexion : deux tests sur deux bases temporaires n'y partagent rien.

## Observation - les tests du store en parallèle

Les tests de `morpheus-store-sqlite` ont été exécutés deux fois le 13 octobre 2026 avec un
`junit-platform.properties` temporaire, non versionné (`parallel.enabled=true`, modes `concurrent` pour les méthodes
et les classes), après cette décision. Sur 205 exécutions :

| Exécution | Échecs | Tests |
|---|---|---|
| 1 | 2 | `SqliteConnectionScopeCloseFailureTest`, `SqliteRuntimeConnectionTest` |
| 2 | 4 | les deux mêmes, et deux méthodes de `SqliteConnectionScopeTest` |

Tous lisent `SqliteConnectionScope.diagnostics()` et y trouvent une connexion de plus ou de moins qu'attendu.
`SqliteContentionObservabilityTest` passe dans les deux exécutions. C'est une observation, pas un gate : deux
exécutions ne prouvent pas l'absence d'un autre défaut.

## Conséquences

- Un test qui asserte sur des compteurs construit les siens et y lit des valeurs absolues.
- Le chemin du store est injectable sans élargir un constructeur de store ni une racine de composition : la portée
  suffit. Les racines d'ADR-0109 ouvrent encore leur portée sans recorder, donc sur le défaut du processus.
- Le défaut statique reste lu par vingt classes au 13 octobre 2026 ; le nombre se relit dans
  `StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`, pas ici. Hors le lanceur, le binding SLF4J et les deux
  classes du store qui tiennent le défaut et le bail, ce sont des constructeurs de commodité et des aides statiques
  dont l'appelant n'a pas encore de recorder à remettre : les services de `morpheus-api` et les outils de
  `morpheus-mcp` sont construits à partir d'un chemin de base, et les passerelles MINOS et NEXUS par leur runtime
  d'intégration.
- Un bail est unique par base pour tout le processus : sa libération n'appartient à aucun ouvreur, et
  `SqliteDatabaseLease` reste sur le défaut.
- `MorpheusCli.main` est un second point d'entrée ; il prend le runtime du processus comme le lanceur.
- Les compteurs statiques de `SqliteConnectionScope` sont le prochain obstacle : la même forme que celle-ci, sur une
  autre classe.
- La règle textuelle ne voit qu'une mention. Un test qui lit l'état du processus à travers le produit - la route des
  métriques - lui échappe : `UnforeseenFailureTraceTest` le fait, et ne peut asserter une valeur absolue que lorsque
  le serveur local recevra son runtime au lieu de lire le défaut.

## Options écartées

- **Un paramètre sur chaque constructeur de store.** Dix constructeurs, cinquante-six sites, dont les racines
  nommées ; la portée porte la même information en un seul endroit.
- **Un runtime construit par le lanceur à côté du défaut.** Deux jeux de compteurs dans un processus tant qu'un seul
  utilisateur reste sur le défaut.
- **Une remise à zéro du runtime statique entre tests.** Elle garde l'état partagé et ajoute un ordre d'exécution à
  respecter ; elle ne survit pas au parallélisme, qui est la raison d'être de cette décision.
- **Activer le parallélisme maintenant.** L'observation ci-dessus le refuse.

## Méthode de validation

- `SqliteContentionObservabilityTest` : valeurs absolues sur ses propres compteurs, dont
  `twoStoresOpenedWithTwoRecordersMoveTwoSetsOfCounters` ; rouge quand l'exécuteur de transactions écrit dans le
  défaut du processus.
- `MorpheusRootHttpRoutesContractTest#theServedMetricsCarryTheTransactionsOfTheStoresTheServerOpens` : la route des
  métriques d'un serveur démarré porte les transactions de ses stores ; rouge quand le défaut du store n'est plus le
  runtime du processus.
- `StaticOperationalRuntimeUsersArchitectureTest` : la liste nommée, aucun test, seul un point d'entrée prend le
  runtime du processus. Chaque règle a été cassée une fois, l'échec constaté, puis rétablie.

## Amendement du 15 octobre 2026 — la portée compte sur le recorder de son ouvreur (AUD-QUA-17)

La conséquence « les compteurs statiques de `SqliteConnectionScope` sont le prochain obstacle » est traitée.

- **Les quatre compteurs statiques et `diagnostics()` sont supprimés.** Une portée compte la connexion physique
  qu'elle ouvre (`sqlite.scope.connection.opened`), celle qu'elle libère (`sqlite.scope.connection.closed`) et la
  vérification de schéma qu'elle exécute (`sqlite.scope.schema.checked`) sur le recorder que son ouvreur nomme, par
  le même porteur que les compteurs de contention (`SqliteContentionMetrics`). Le pic (`peak`) n'a pas d'équivalent :
  un compteur n'exprime pas un maximum, et aucun code de production ne le lisait.
- **Il n'y a plus d'ouverture de portée sans recorder.** `SqliteConnectionScope.open(chemin, recorder)` est la seule ;
  `SqliteOperationRuntime.open(chemin, recorder)` aussi. Le défaut du processus reste celui d'une connexion ouverte
  hors portée (décision 4).
- **Les racines de composition remettent le recorder qu'elles ont reçu** (ce que la conséquence « les racines
  d'ADR-0109 ouvrent encore leur portée sans recorder » laissait ouvert) : `ApiRuntime`, `CliRuntime` et
  `MorpheusMcpRuntime` le prennent par constructeur, de leurs services, outils et commandes, qui le prennent de leur
  point d'entrée. Le lanceur remet le runtime du processus au serveur local, au serveur remote, au serveur MCP et à
  chaque commande. Un serveur HTTP démarré sans runtime (les surcharges courtes de `MorpheusHttpServer.start`) en
  construit un à lui, que sa route `metrics` sert ; un serveur MCP construit sans recorder ne compte nulle part — il
  ne sert aucun compteur.
- **Les tests de comptage lisent des valeurs absolues sur leur propre recorder**, et les trois tests de compte de
  connexions (HTTP, MCP, CLI) assertent désormais aussi **une** vérification de schéma par opération, qui n'était
  qu'inférée.
- **Deux règles**, dans `StaticOperationalRuntimeUsersArchitectureTest` : aucun test ne mentionne
  `SqliteConnectionScope.diagnostics(` ni `SqliteConnectionScope.Diagnostics` ; `SqliteConnectionScope` et ses classes
  imbriquées ne déclarent aucun champ statique hors constantes `String`, le `ThreadLocal` de la portée et
  `serialVersionUID`. Cassées le 15/10/2026 (une mention dans un test, un `AtomicLong` statique planté), puis rétablies.

## Second amendement du 15 octobre 2026 — les troncs reçoivent le runtime ; le store passe en parallèle (AUD-QUA-13)

- **Les troncs API, MCP et d'intégration prennent le recorder de leur point d'entrée.** Le lanceur remet le runtime du
  processus aux serveurs et aux commandes ; ceux-ci le remettent à leurs services, outils, routeurs
  (`UnforeseenFailureTrace.record(recorder, échec)` n'a plus d'état), au journal d'accès remote, à la readiness, au
  raisonnement, à la synchronisation (`IncrementalSyncService`, `LocalSourceInventoryScanner`, publication observée), à
  la récupération des instantanés (`RuntimeSnapshotRecovery`), au contexte augmenté et à la résolution des références
  externes, et aux intégrations MINOS et NEXUS (`MinosIntegrationRuntime.resolve`, `NexusIntegrationRuntime`, leurs
  passerelles, dont le lancement porte le recorder). Les constructeurs de commodité qui lisaient le défaut sont
  supprimés, sans surcharge de compatibilité : les tests passent le leur, ou `OperationalRecorder.noop()` quand ils
  n'observent rien.
- **`MAIN_USERS` passe de 20 à 6**, chacun motivé dans `StaticOperationalRuntimeUsersArchitectureTest` : les deux
  points d'entrée (`MorpheusMain`, `MorpheusCli`), le binding SLF4J (décision 6), le défaut d'une connexion ouverte
  hors portée (`SqliteContentionMetrics`), le bail par base (`SqliteDatabaseLease`), et `ExternalJarIntegrity`, dont
  la mise en quarantaine d'un JAR épinglé sert aussi l'activateur de plugins, exécuté dans la JVM du worker de sonde
  et dans le service de plugins, qui n'ont pas de runtime à lui remettre.
- **La décision 4 n'est plus vraie qu'à moitié.** Le lanceur ne construit toujours pas de second runtime : il prend
  celui du processus. Mais un serveur HTTP démarré sans runtime en construit un à lui, servi par sa route `metrics` ;
  c'est ce qui rend `UnforeseenFailureTraceTest` indépendant de l'état du processus — il lit désormais des valeurs
  absolues (0, puis 2) au lieu d'une différence (`before + 2`).
- **Le parallélisme JUnit est activé pour `morpheus-store-sqlite` seul**, par un `junit-platform.properties` versionné
  dans ses ressources de test (méthodes et classes concurrentes). Mesuré avant : quatre exécutions de ses 210 tests
  (209 exécutés, 1 ignoré) en parallèle, **zéro échec** sur les quatre, là où le 13/10/2026 deux exécutions en
  donnaient deux puis quatre, tous sur `diagnostics()`. Les autres modules restent séquentiels : le pilote compteur
  (`PhysicalSqliteConnections`) de `morpheus-api`, `morpheus-cli` et `morpheus-mcp`, `java.home` et
  `Locale.setDefault` (tableau ci-dessus) y partagent encore un état de la JVM ; quatre exécutions propres ne prouvent
  pas l'absence d'un autre défaut, et un test qui lit un statique du processus casse ce module.

## Troisième amendement du 17 octobre 2026 — un filet pour la portée laissée sur un thread (AUD-QUA-18)

Le parallélisme de `morpheus-store-sqlite` rend atteignable ce que la table de l'état partagé nommait depuis le
13/10/2026 : `SqliteConnectionScope` garde un `ThreadLocal`, une seconde portée sur le même thread est refusée
(`SQLITE_CONNECTION_SCOPE_NESTED`), et les tests tournent désormais sur les threads réutilisés d'un pool. Un test qui
laisse une portée ouverte ferait échouer le test suivant du même thread, loin de sa cause.

- **La portée qui ne se fermait qu'après des assertions se ferme dans un `finally`**
  (`SqliteConnectionScopeThreadConfinementTest`).
- **`NoSqliteScopeLeftBehindExtension`**, enregistrée pour tout le module par `META-INF/services` et
  `junit.jupiter.extensions.autodetection.enabled`, lit après chaque test la portée encore attachée au thread
  (`SqliteConnectionScope.attachedToThisThread()`, visible du paquet seulement), la ferme, et fait échouer **le test
  fautif** par son nom. Éprouvée le 17/10/2026 par un test qui ouvre une portée sans la fermer : « leaksAScope() of
  ScratchLeakTest left a SQLite connection scope open on its thread (detached) », puis supprimé.
- **Le parallélisme est fixe** (`config.strategy=fixed`, quatre fils) : sans cela il suit `availableProcessors()`, et
  une course invisible sur un portable à deux cœurs peut se révéler sur un runner à seize.

Ce que cet amendement ne fermait pas — le pilote compteur de `morpheus-api`, `morpheus-cli` et `morpheus-mcp` — est
fermé par l'amendement suivant.

## Quatrième amendement du 17 octobre 2026 — le pilote compteur disparaît (AUD-QUA-17)

Les trois tests de compte de connexions (`HttpRequestConnectionCountTest`, `CliCommandConnectionCountTest`,
`McpToolCallConnectionCountTest`) ne passent plus par un pilote JDBC compteur. `PhysicalSqliteConnections`, copié dans les
trois modules, désenregistrait le pilote SQLite du `DriverManager` de la JVM, mettait un pilote compteur à sa place et le
rétablissait : un état de processus lu en différence, la forme exacte du défaut que cet ADR traite pour les compteurs
statiques de la portée. Les trois copies sont supprimées.

- **Ce qui reste mesuré, exactement.** Une opération ouvre **une** connexion de portée (`sqlite.scope.connection.opened`
  vaut 1) et exécute **une** vérification de schéma (`sqlite.scope.schema.checked` vaut 1), lues en différence sur le
  recorder que le test a remis au serveur, à l'outil ou au lanceur : un recorder par test, rien de partagé.
- **Ce qui n'est plus observé à l'exécution.** Une connexion ouverte *hors* portée — un store construit à côté du runtime —
  n'est plus comptée. Elle est refusée là où elle s'écrit : `SqliteConnectionScopeUseArchitectureTest` n'admet de
  construction de store que dans `SqliteOperationRuntime` ou dans une classe nommée, et exige l'ouverture de la portée
  avant la première construction. C'est une garantie sur le texte et le bytecode, pas sur une exécution : un chemin qui
  ouvrirait une connexion sans construire de store (`SqliteDatabaseSecurity.open`) ne serait vu par aucune des deux.
- **Ce que cela ne change pas pour le parallélisme.** `morpheus-api`, `morpheus-cli` et `morpheus-mcp` restent
  séquentiels : `java.home` et `Locale.setDefault` (tableau ci-dessus) y partagent encore un état de la JVM. Les activer
  est un lot à part, avec quatre exécutions propres par module.

## Cinquième amendement du 17 octobre 2026 — un test de saturation ne court pas contre les autres

`SqliteLocalSecurityContractTest#concurrentFirstOpensMaterializeTheJournalWithoutRacingOnTheProbeTable` ouvre une base
vide depuis seize threads, vingt-cinq fois, avec le délai d'occupation de cinq secondes du produit. Sur le runner Windows de
la CI il a rendu `SQLITE_BUSY` deux fois de suite (PR 508, puis PR 510 qui ne touche aucun code de store), pendant que le
module exécutait ses autres tests en parallèle : un test voisin de `SqliteMigrationAtomicityTest` y a pris 63 s au lieu de
moins de 2. La classe est désormais `@Isolated`, comme `SqliteDatabaseLeaseTest` : un test qui sature SQLite exprès ne doit
pas rivaliser avec d'autres tests, sinon il échoue pour une raison qui n'est pas celle qu'il vérifie. La cause de la
lenteur du runner n'est pas établie.
