---
id: ARCH-0115
title: "Le runtime opérationnel est une valeur que le lanceur remet ; le parallélisme JUnit en dépend"
domain: architecture
rules: false
---

# Le runtime opérationnel est une valeur que le lanceur remet ; le parallélisme JUnit en dépend

- Statut : **Acceptée**
- Date : 2026-10-13

## Context

Les compteurs, le sink et le recorder opérationnels d'un processus sont de l'état observable. Tant que le code les
atteint par un accès statique, un test ne peut observer que ce que toute la JVM a écrit : il lit des différences avant
et après, ce qui ne tient que si rien d'autre ne s'exécute pendant ce temps, et toute exécution parallèle des tests
rend ces assertions non déterministes.

Le chemin du store SQLite pose la même question à un endroit sans instance : l'exécuteur de transactions est statique
et reçoit une `Connection` nue, depuis de nombreux stores construits en de nombreux endroits. Avec ADR-0109, une
opération ouvre une portée de connexion (`SqliteConnectionScope`) avant ses stores : la connexion a un propriétaire,
qui peut porter le recorder.

Options écartées :

- **Un paramètre sur chaque constructeur de store** : il touche tous les stores et tous leurs sites de construction,
  racines nommées comprises ; la portée porte la même information en un seul endroit.
- **Un runtime construit par le lanceur à côté du défaut** : deux jeux de compteurs dans un même processus tant
  qu'une classe reste sur le défaut.
- **Une remise à zéro du runtime statique entre tests** : elle garde l'état partagé, ajoute un ordre d'exécution à
  respecter, et ne survit pas au parallélisme.
- **Activer le parallélisme JUnit partout** : les états de JVM encore partagés (voir plus bas) le refusent.

## Decision

1. **Le runtime est une valeur.** `OperationalRuntime` porte les compteurs, le sink, le recorder et la destination du
   journal en fichier. Deux runtimes ne partagent rien.

2. **Le lanceur le prend une fois et le remet.** `MorpheusMain` prend le runtime du processus
   (`LocalOperationalRuntime.process()`), y nomme le répertoire du journal (ADR-0146), et le remet par constructeur au
   serveur local, au serveur remote, au serveur MCP et à chaque commande. `MorpheusCli.main`, second point d'entrée,
   fait de même. Aucune injection par annotation, aucun conteneur : le câblage reste explicite.

3. **Les troncs remettent le recorder qu'ils ont reçu.** `ApiRuntime`, `CliRuntime` et `MorpheusMcpRuntime` le
   prennent par constructeur, de leurs services, outils et commandes, qui le prennent de leur point d'entrée. Il
   descend ainsi jusqu'aux routeurs (`UnforeseenFailureTrace.record(recorder, échec)` n'a pas d'état), au journal
   d'accès remote, à la readiness, au raisonnement, à la synchronisation, à la récupération des instantanés, au
   contexte augmenté, à la résolution des références externes et aux intégrations MINOS et NEXUS. Il n'existe aucun
   constructeur de commodité qui lirait le défaut : un test passe son propre recorder, ou `OperationalRecorder.noop()`
   quand il n'observe rien.

4. **Dans le store, le recorder voyage avec la connexion.** Qui ouvre une portée la nomme avec son recorder :
   `SqliteConnectionScope.open(chemin, recorder)` et `SqliteOperationRuntime.open(chemin, recorder)` sont les seules
   ouvertures. Les deux enveloppes de connexion — celle du bail, celle de la portée — portent le recorder, et
   l'exécuteur de transactions le demande à la connexion. Aucun paramètre n'est ajouté aux constructeurs des stores :
   un store construit dans une portée est observé par qui a ouvert la portée. Une connexion remplacée après un échec
   de nettoyage garde le recorder de sa portée.

5. **La portée compte sur le recorder de son ouvreur.** Elle compte la connexion physique qu'elle ouvre
   (`sqlite.scope.connection.opened`), celle qu'elle libère (`sqlite.scope.connection.closed`) et la vérification de
   schéma qu'elle exécute (`sqlite.scope.schema.checked`), par le même porteur que les compteurs de contention
   (`SqliteContentionMetrics`). `SqliteConnectionScope` ne garde aucun compteur statique.

6. **Le défaut statique subsiste, et il est le runtime du processus.** `LocalOperationalRuntime` garde un runtime pour
   le processus : c'est celui que le lanceur prend, et le lanceur ne construit pas de second runtime. Écrivent dans ce
   défaut une connexion ouverte hors portée (`SqliteContentionMetrics`), le bail par base (`SqliteDatabaseLease`, dont
   la libération n'appartient à aucun ouvreur), et `ExternalJarIntegrity`, qui sert aussi l'activateur de plugins
   exécuté dans la JVM du worker de sonde et dans le service de plugins, qui n'ont pas de runtime à lui remettre.
   Un serveur HTTP local démarré sans runtime (les surcharges courtes de `MorpheusHttpServer.start`) construit le sien,
   que sa route `metrics` sert ; un serveur MCP construit sans recorder ne compte nulle part et ne sert aucun compteur.

7. **La liste des utilisateurs du défaut est nommée et ne fait que rétrécir.** Les classes de production qui mentionnent
   `LocalOperationalRuntime` sont exactement celles de `StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`,
   chacune motivée dans ce test. Aucun test ne mentionne le défaut. Seuls les deux points d'entrée prennent le runtime
   du processus comme valeur.

8. **Le binding SLF4J reste sur le défaut.** `OperationalSlf4jServiceProvider` est instancié par le `ServiceLoader`
   (ADR-0146) : personne ne le construit, donc personne ne peut rien lui remettre. C'est pourquoi le défaut statique
   ne disparaît pas quand la liste se vide de tout le reste.

9. **Le parallélisme JUnit est activé pour `morpheus-store-sqlite` seul**, par un `junit-platform.properties`
   versionné dans ses ressources de test : méthodes et classes concurrentes, pool fixe (`config.strategy=fixed`), pour
   qu'une course ne dépende pas du nombre de cœurs de la machine. Dans ce module :
   - `NoSqliteScopeLeftBehindExtension`, enregistrée par `META-INF/services` et
     `junit.jupiter.extensions.autodetection.enabled`, lit après chaque test la portée encore attachée au thread
     (`SqliteConnectionScope.attachedToThisThread()`, visible du paquet seulement), la ferme, et fait échouer le test
     fautif par son nom : une portée laissée ouverte ferait sinon échouer, par le refus
     `SQLITE_CONNECTION_SCOPE_NESTED`, le test suivant du même thread du pool ;
   - une portée se ferme dans un `finally`, jamais seulement après des assertions ;
   - un test qui sature SQLite exprès (`SqliteLocalSecurityContractTest`) ou qui compte une ressource de toute la JVM
     (`SqliteDatabaseLeaseTest`) est `@Isolated` : il ne rivalise pas avec d'autres tests.

10. **Les autres modules restent séquentiels** tant qu'ils partagent un état de la JVM entre tests (tableau
    ci-dessous). Les activer est un changement à part, précédé d'exécutions parallèles propres répétées par module.

État de la JVM encore partagé entre tests, hors `morpheus-store-sqlite` :

| État partagé | Qui l'écrit ou le lit | Effet attendu en parallèle |
|---|---|---|
| Propriété système `java.home` | posée puis rétablie par `MinosPeerLauncherResolutionTest` et `NexusPeerLauncherResolutionTest` | tout test qui résout un lanceur Java pendant la fenêtre lit un répertoire temporaire |
| `Locale.setDefault` | `PolicyApiEnumVocabularyLocaleTest`, `McpEnumVocabularyLocaleTest` | tout formatage sensible à la locale dans un autre test |
| Fichier hors répertoire temporaire | `MorpheusRemoteLoadProfileTest` écrit `target/remote-load-profile.txt`, que la lane nocturne publie | aucun tant qu'il reste le seul écrivain |
| Champs statiques d'une classe de test | `MorpheusRemoteAdversarialClientTest` (serveur, client et répertoire partagés entre ses méthodes, posés en `@BeforeAll`) | ses méthodes ne peuvent pas s'exécuter en concurrence |

Aucun test n'écoute sur un port fixe. Les baux (`SqliteDatabaseLease`) et la quarantaine de l'exécuteur de
transactions sont statiques mais indexés par base ou par connexion : deux tests sur deux bases temporaires n'y
partagent rien.

## Do's and Don'ts

### Do

- Remettre le runtime, ou son recorder, par constructeur depuis le point d'entrée.
- Ouvrir une portée SQLite avec le recorder de l'appelant, et la fermer dans un `finally`.
- Dans un test qui asserte sur des compteurs, construire son propre runtime ou recorder et y lire des valeurs
  absolues.
- Retirer de `MAIN_USERS` une classe qui ne mentionne plus `LocalOperationalRuntime`.
- Marquer `@Isolated` un test de `morpheus-store-sqlite` qui sature SQLite ou mesure une ressource de toute la JVM.

### Don't

- N'ajouter aucune classe de production à `MAIN_USERS`.
- Ne lire `LocalOperationalRuntime` dans aucun test.
- Ne prendre `LocalOperationalRuntime.process()` ailleurs que dans `MorpheusMain` et `MorpheusCli`.
- Ne déclarer dans `SqliteConnectionScope` aucun champ statique hors constantes `String`, le `ThreadLocal` de la
  portée et `serialVersionUID`.
- Ne pas construire dans le lanceur un second runtime à côté de celui du processus.
- N'activer le parallélisme JUnit dans un autre module que lorsque plus aucun de ses tests ne partage un état de la
  JVM.

## Consequences

### Positives

- Un test observe ses propres compteurs, indépendamment des autres tests de la JVM.
- Le chemin du store est observable sans élargir un constructeur de store ni une racine de composition : la portée
  suffit.
- `morpheus-store-sqlite` exécute ses tests en parallèle, et un test qui laisse une portée ouverte est désigné par son
  nom.

### Négatives

- Une connexion ouverte hors portée n'est pas comptée à l'exécution : elle est refusée là où elle s'écrit, par
  `SqliteConnectionScopeUseArchitectureTest`, garantie sur le texte et le bytecode et non sur une exécution. Un chemin
  qui ouvrirait une connexion sans construire de store (`SqliteDatabaseSecurity.open`) n'est vu par aucune des deux.
- Le défaut statique demeure pour le binding SLF4J, le bail et l'intégrité des JAR externes.

### Risques

- La règle textuelle ne voit qu'une mention : un test qui lirait l'état du processus à travers le produit lui
  échapperait.
- Des exécutions parallèles propres ne prouvent pas l'absence d'un autre défaut ; un test ajouté à
  `morpheus-store-sqlite` qui lit un statique du processus, pose une propriété système ou la locale par défaut casse
  ce module.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/StaticOperationalRuntimeUsersArchitectureTest.java` :
  `noMainClassStartsToReadTheStaticRuntime` (liste `MAIN_USERS`), `noTestReadsTheStaticRuntime`,
  `onlyAnEntryPointTakesTheRuntimeOfTheProcessAsAValue`, `noTestReadsProcessWideCountersOfTheConnectionScope`,
  `theConnectionScopeKeepsNoCounterOfTheProcess`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java` :
  construction d'un store seulement dans `SqliteOperationRuntime` ou une classe nommée, portée ouverte d'abord.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteContentionObservabilityTest.java`
  (`twoStoresOpenedWithTwoRecordersMoveTwoSetsOfCounters`) : valeurs absolues sur ses propres compteurs.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusRootHttpRoutesContractTest.java`
  (`theServedMetricsCarryTheTransactionsOfTheStoresTheServerOpens`) et `UnforeseenFailureTraceTest.java` : la route
  des métriques d'un serveur porte les compteurs de son propre runtime, lus en valeurs absolues.
- `HttpRequestConnectionCountTest` (`morpheus-api`), `CliCommandConnectionCountTest` (`morpheus-cli`),
  `McpToolCallConnectionCountTest` (`morpheus-mcp`) : une connexion de portée et une vérification de schéma par
  opération, sur le recorder du test.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/NoSqliteScopeLeftBehindExtension.java` et
  `SqliteConnectionScopeThreadConfinementTest.java` : portée laissée sur un thread.
- `morpheus-store-sqlite/src/test/resources/junit-platform.properties` : configuration du parallélisme.
- Revue : l'absence de `junit-platform.properties` activant le parallélisme dans un autre module.

## References

- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées, une
  portée de connexion par opération.
- [ADR-0146](BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) — un seul binding de journalisation.
- [ADR-0146](BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) — journal opérationnel borné.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — plugins providers hors processus.
