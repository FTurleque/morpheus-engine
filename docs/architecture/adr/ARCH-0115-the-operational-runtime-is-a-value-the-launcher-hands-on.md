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
et reçoit une `Connection` nue, depuis de nombreux stores construits en de nombreux endroits. Avec ADR-0162, une
opération ouvre une portée de connexion (`SqliteConnectionScope`) avant ses stores : la connexion a un propriétaire,
qui peut porter le recorder.

Options écartées :

- **Un paramètre sur chaque constructeur de store** : il touche tous les stores et tous leurs sites de construction,
  racines nommées comprises ; la portée porte la même information en un seul endroit.
- **Un runtime construit par le lanceur à côté du défaut** : deux jeux de compteurs dans un même processus tant
  qu'une classe reste sur le défaut.
- **Une remise à zéro du runtime statique entre tests** : elle garde l'état partagé, ajoute un ordre d'exécution à
  respecter, et ne survit pas au parallélisme.
- **Activer le parallélisme JUnit partout** : les états de JVM encore partagés entre tests (décrits dans
  [BUILD_AND_TEST](../../developer/BUILD_AND_TEST.md#parallélisme-junit)) le refusent.

Le câblage que la décision suppose :

- les troncs reçoivent le recorder de leurs services, outils et commandes, qui le prennent de leur point d'entrée ;
  il descend ainsi jusqu'aux routeurs (`UnforeseenFailureTrace.record(recorder, échec)` n'a pas d'état), au journal
  d'accès remote, à la readiness, au raisonnement, à la synchronisation, à la récupération des instantanés, au
  contexte augmenté, à la résolution des références externes et aux intégrations MINOS et NEXUS ;
- les deux enveloppes de connexion, celle du bail et celle de la portée, portent le recorder, et l'exécuteur de
  transactions le demande à la connexion : un store construit dans une portée est observé par qui a ouvert la portée,
  et une connexion remplacée après un échec de nettoyage garde le recorder de sa portée. La portée compte par le même
  porteur que les compteurs de contention (`SqliteContentionMetrics`) ;
- le défaut statique subsiste pour ce qui n'a pas d'ouvreur ou que personne ne construit : la connexion ouverte hors
  portée (`SqliteContentionMetrics`), le bail par base (`SqliteDatabaseLease`, dont la libération n'appartient à aucun
  ouvreur), `ExternalJarIntegrity`, qui sert aussi l'activateur de plugins exécuté dans la JVM du worker de sonde et
  dans le service de plugins, sans runtime à lui remettre, et `OperationalSlf4jServiceProvider`, instancié par le
  `ServiceLoader` (ADR-0146). C'est pourquoi le défaut ne disparaît pas quand la liste de ses utilisateurs se vide de
  tout le reste ; chacun y est motivé dans le test qui la tient.

Configuration et garde-fous du parallélisme : [BUILD_AND_TEST](../../developer/BUILD_AND_TEST.md#parallélisme-junit).

## Decision

1. **Valeur** : `OperationalRuntime` porte compteurs, sink, recorder et journal en fichier ; deux runtimes ne
   partagent rien.

2. **Remise** : seuls `MorpheusMain` et `MorpheusCli.main` prennent `LocalOperationalRuntime.process()` ; ils y
   nomment le journal (ADR-0146) et le remettent par constructeur aux serveurs et commandes, sans conteneur ni second runtime.

3. **Troncs** : `ApiRuntime`, `CliRuntime`, `MorpheusMcpRuntime` prennent le recorder par constructeur et le
   descendent ; nul constructeur de commodité ne lit le défaut (un test passe le sien ou `OperationalRecorder.noop()`).

4. **Store** : le recorder voyage avec la connexion ; `SqliteConnectionScope.open(chemin, recorder)` et
   `SqliteOperationRuntime.open(chemin, recorder)` sont les seules ouvertures ; aucun store n'en prend.

5. **Portée** : elle compte `sqlite.scope.connection.opened`, `sqlite.scope.connection.closed` et
   `sqlite.scope.schema.checked` sur ce recorder, sans compteur statique.

6. **Défaut** : le runtime statique est celui du processus ; y écrivent connexion hors portée, bail, intégrité des
   JAR externes et binding SLF4J. `MorpheusHttpServer.start` sans runtime en construit un, servi par `metrics` ; un
   serveur MCP sans recorder ne compte rien.

7. **Utilisateurs nommés** : les classes de production qui mentionnent `LocalOperationalRuntime` sont exactement
   `StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`, liste qui ne fait que rétrécir ; aucun test ne le
   mentionne.

8. **Parallélisme JUnit** seulement dans `morpheus-store-sqlite`, `morpheus-api`, `morpheus-mcp` et `morpheus-cli`,
   par un `junit-platform.properties` versionné. Un test qui change un état lu par toute la JVM (locale, propriété
   système, flux standard, pilote JDBC) est `@Isolated`, partout. Activer un autre module est un changement à part,
   précédé d'exécutions parallèles propres répétées.

## Do's and Don'ts

### Do

- Remettre le runtime, ou son recorder, par constructeur depuis le point d'entrée.
- Ouvrir une portée SQLite avec le recorder de l'appelant, et la fermer dans un `finally`.
- Dans un test qui asserte sur des compteurs, construire son propre runtime ou recorder et y lire des valeurs
  absolues.
- Retirer de `MAIN_USERS` une classe qui ne mentionne plus `LocalOperationalRuntime`.
- Marquer `@Isolated` un test qui change un état lu par toute la JVM, sature une ressource exprès ou mesure une
  ressource de toute la JVM.

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
- Des exécutions parallèles propres ne prouvent pas l'absence d'un autre défaut ; un test ajouté à un module
  parallèle qui lit un statique du processus, ou partage entre ses méthodes un état qu'il modifie, casse ce module.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/StaticOperationalRuntimeUsersArchitectureTest.java` :
  `noMainClassStartsToReadTheStaticRuntime` (liste `MAIN_USERS`), `noTestReadsTheStaticRuntime`,
  `onlyAnEntryPointTakesTheRuntimeOfTheProcessAsAValue`, `noTestReadsProcessWideCountersOfTheConnectionScope`,
  `theConnectionScopeKeepsNoCounterOfTheProcess`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteConnectionScopeUseArchitectureTest.java` :
  construction d'un store seulement dans `SqliteOperationRuntime` ou une classe nommée, portée ouverte d'abord.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteContentionObservabilityTest.java`
  (`twoStoresOpenedWithTwoRecordersMoveTwoSetsOfCounters`) : valeurs absolues sur ses propres compteurs.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusRootHttpRoutesContractTest.java`
  (`theServedMetricsCarryTheTransactionsOfTheStoresTheServerOpens`) et `UnforeseenFailureTraceTest.java` : la route
  des métriques d'un serveur porte les compteurs de son propre runtime, lus en valeurs absolues.
- `HttpRequestConnectionCountTest` (`morpheus-api`), `CliCommandConnectionCountTest` (`morpheus-cli`),
  `McpToolCallConnectionCountTest` (`morpheus-mcp`) : une connexion de portée et une vérification de schéma par
  opération, sur le recorder du test.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/NoSqliteScopeLeftBehindExtension.java` et
  `SqliteConnectionScopeThreadConfinementTest.java` : portée laissée sur un thread.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JvmMutatingTestsAreIsolatedArchitectureTest.java` :
  un test qui change la locale, le fuseau, une propriété système, un flux standard ou un pilote de `DriverManager` est
  `@Isolated`, dans tout module.
- `src/test/resources/junit-platform.properties` de `morpheus-store-sqlite`, `morpheus-api`, `morpheus-mcp` et
  `morpheus-cli` : configuration du parallélisme.
- Revue : l'absence de `junit-platform.properties` activant le parallélisme dans un autre module.

## References

- [ADR-0162](ARCH-0162-racines-de-composition-nommees-paquets-sans-cycle-hors-une-paire-bornee-et-decisions-dans-le-domaine.md) — racines de composition nommées, une
  portée de connexion par opération.
- [ADR-0146](BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) — un seul binding de journalisation, journal
  opérationnel borné.
- [BUILD_AND_TEST](../../developer/BUILD_AND_TEST.md#parallélisme-junit) — parallélisme JUnit et état de la JVM partagé
  entre tests.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — plugins providers hors processus.
