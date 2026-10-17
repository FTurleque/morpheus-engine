# ADR-0114 — Le travail sur la base s'exécute sur un pool borné de threads plateforme

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 13 octobre 2026
- Dépend de : ADR-0016 (baseline Java 21), ADR-0065 (serveur HTTP du JDK), ADR-0086 (concurrence SQLite bornée),
  ADR-0087 (opérabilité local-first)
- Portée : `morpheus-api` (`DatabaseWorkOffloadingHttpServer`, `MorpheusLocalHttpServerBootstrap`,
  `MorpheusRootHttpRoutes.ANSWERED_WITHOUT_THE_DATABASE`, `RequestProtectedHttpServer.stopped`)

## Contexte

Les deux serveurs HTTP exécutent chaque requête sur un thread virtuel. Le pilote SQLite est du code natif, et il
attend une base occupée **à l'intérieur de l'appel natif** : jusqu'à cinq secondes, le délai d'occupation. Un thread
virtuel dans une trame native reste monté sur son porteur. Les porteurs sont aussi nombreux que les processeurs.

Le constat AUD-PRF-18 de l'audit du 9 octobre 2026 attribuait le risque aux 94 méthodes `public synchronized` du
store et proposait de remplacer les moniteurs par des verrous. La spécification
(`openspec/changes/keep-the-scheduler-free-while-sqlite-waits`) a corrigé ce diagnostic sur lecture — un verrou ne
libère pas un thread qui attend dans du code natif — et a exigé une mesure avant tout remède.

## Mesure

`SchedulerAvailabilityUnderSqliteWaitTest`, sur un vrai serveur local : un écrivain tient la base en `BEGIN EXCLUSIVE`
pendant trois secondes, quatre fois plus de lectures que de processeurs sont en vol, et huit sondes `health` sont
envoyées pendant qu'elles attendent. Relevé du 13 octobre 2026, Windows, 16 processeurs, **JDK 24.0.1** :

```text
avant   64 lectures en vol   health (ms) = [13033, 8, 2, 2, 2, 2, 1, 1]   lectures : 8 × 200, 56 × 409
après   64 lectures en vol   health (ms) = [7, 3, 2, 3, 3, 3, 3, 3]       lectures : 64 × 200
```

Deux choses s'y lisent. La première sonde `health` attend treize secondes sur une route qui ne touche aucune base : le
serveur entier cesse de répondre, sonde de vie comprise. Et la mesure tourne sur un JDK 24, où l'épinglage par moniteur
n'existe plus (JEP 491) : ce sont bien les trames natives qui tiennent les porteurs, pas les moniteurs.
`-Djdk.tracePinnedThreads` n'imprime rien pendant l'attente — il signale un thread virtuel qui se *gare* épinglé, pas
un thread qui occupe son porteur dans du code natif.

Après le changement, sur les runners de la CI du 13 octobre 2026, 4 processeurs et 16 lectures en vol, toutes
servies : Linux `health (ms) = [1, 41, 41, 41, 42, 41, 41, 41]`, Windows `[1, 0, 0, 0, 0, 0, 0, 0]`. L'état « avant »
n'a été mesuré que sur la machine à 16 processeurs.

## Décision

1. **Tout gestionnaire qui peut toucher la base s'exécute sur un travailleur.**
   `DatabaseWorkOffloadingHttpServer` décore le serveur local : un gestionnaire enregistré à travers lui est remis à
   un pool de threads plateforme, et le thread virtuel qui a reçu la requête se gare jusqu'à la fin — ce qui rend son
   porteur. Une requête qui attend la base occupe un travailleur, jamais un porteur.

2. **Le pool est borné, et sa taille est la concurrence de la base.** `DATABASE_WORKERS`, le nombre de processeurs
   avec un minimum de deux, déclaré à un seul endroit. Au-delà, les requêtes attendent leur tour sur un thread virtuel
   garé, ce qui ne coûte rien. C'est ce qui transforme le pic en file : les 56 refus `409` de la mesure venaient de 64
   connexions se disputant la base en même temps.

3. **Le gestionnaire entier, sur un seul travailleur.** La portée de connexion est confinée à son thread
   (`SqliteConnectionScope`) ; déporter le gestionnaire d'un bloc lui garde son sens.

4. **Les routes qui répondent sans la base ne passent pas par les travailleurs.** `health`, `metrics` et `version`
   sont enregistrées directement, hors du décorateur : elles doivent rester servies quand tous les travailleurs sont
   pris. `readiness` interroge la base et passe par eux.

5. **Les moniteurs ne sont pas touchés.** Le remède n'en dépend pas. Dire ce que chacun protège, les stores étant
   construits par requête, reste à faire ; ce n'est plus une question de disponibilité.

## Amendement du 13 octobre 2026 — l'attente d'un travailleur est bornée

Le constat AUD-PRF-03 portait sur l'absence de contrôle d'admission du serveur local. Le pool ci-dessus en fournit le
plafond ; il restait une file sans fin devant lui. Décidé, sans ADR séparé comme la spécification
(`openspec/changes/admit-local-requests-before-they-meet-the-database`) le prévoyait :

6. **Une place par travailleur, attendue dans l'ordre d'arrivée.** Un sémaphore équitable de `DATABASE_WORKERS`
   permis. Une requête tient une place exactement pendant qu'un travailleur la traite : la file interne du pool reste
   vide, et la seule file est celle-ci.

7. **L'attente est bornée, puis refusée par son nom.** `ADMISSION_WAIT`, dix secondes — deux fois le délai
   d'occupation d'une connexion. Passé ce délai : `429`, code `LOCAL_SERVER_BUSY`, `Retry-After: 1`, avec les en-têtes
   de toute réponse locale. Le refus est écrit par le thread de la requête : rien n'a été remis à un travailleur.

8. **Le plafond suit la base, y compris en mode distant.** La spécification demandait que le plafond local ne soit
   pas inférieur à celui de la façade distante, pour qu'une requête déjà admise n'attende pas deux fois. Ce n'est pas
   appliqué : le plafond de la façade est un réglage de l'opérateur, et l'égaler ramènerait dans la base la contention
   que la mesure a montrée. Une requête que la façade a admise peut donc attendre un travailleur, dans la même borne,
   et recevoir ce `429` relayé.

Non fait : aucun compteur des refus d'admission. Le décorateur n'a pas de métriques, et lui en donner par le runtime
statique ajouterait un utilisateur à une liste qui ne fait que rétrécir.

## Amendement du 17 octobre 2026 — la sauvegarde du serveur distant tourne sur un travailleur (AUD-PRF-19)

La réserve ci-dessus est levée par une mesure. `RemoteSchedulerAvailabilityUnderSqliteWaitTest` démarre un vrai serveur
distant, tient la base avec un écrivain exclusif trois secondes, met en vol autant de sauvegardes que la voie
privilégiée en admet (processeurs + 4) et demande `server/status`, route de la façade qui ne touche aucune base :

```text
avant   processors=16, backups in flight=20, backup statuses={201=4, 500=16}
        status latencies (ms)=[5002, 41, 3, 3, 2, 2, 8, 2], probes that failed=[HttpConnectTimeoutException]
après   processors=16, backups in flight=20, backup statuses={201=20}
        status latencies (ms)=[13, 3, 3, 4, 2, 2, 2, 2], probes that failed=[]
```

Avant : la première sonde n'a pas pu se connecter en cinq secondes. La voie privilégiée par défaut (64 requêtes,
donc 16 sauvegardes) atteint le nombre de porteurs d'une machine de seize processeurs et le dépasse sur toute machine
plus petite.

- **La façade ne relaie pas tout à un pool.** Décorer le serveur distant comme le local aurait fait occuper un
  travailleur par chaque requête relayée, y compris celles qui n'attendent que le réseau, et ramené la capacité de la
  façade au nombre de processeurs. La sauvegarde est la seule route que la façade sert avec SQLite en main : elle seule
  passe par `DatabaseCallWorkers`, un pool de threads plateforme de même taille que celui du serveur local
  (`DATABASE_WORKERS`), pendant que le thread virtuel de la requête attend un futur et libère son porteur.
- **Pas d'admission propre.** Les requêtes qui y arrivent sont déjà bornées par la voie privilégiée ; une sauvegarde
  au-delà des travailleurs attend dans la file du pool. Le pool est arrêté avec le serveur.
- **La règle** : `RemoteServerArchitectureTest#theRemoteFacadeCallsTheDatabaseOnlyThroughItsDatabaseWorkers` refuse tout
  appel à `maintenance.` de `MorpheusRemoteHttpServer` qui ne soit pas dans `databaseWorkers.call(`. Cassée le
  17/10/2026 en retirant l'appel au travailleur, puis rétablie.
- Les seize `500` d'avant ne sont pas expliqués par cette mesure et ne sont pas traités ici : après la correction, les
  vingt sauvegardes réussissent. La cause n'est pas établie : famine des porteurs ou course sur le verrou de la base.

## Alternatives écartées

- **Remplacer les moniteurs par des verrous.** Ne libère pas un thread qui attend dans du code natif ; la mesure sur
  JDK 24 le confirme.
- **Passer la baseline à un JDK plus récent.** Même raison, pour le coût le plus élevé : Enforcer, CI, runtime embarqué.
- **Agrandir l'ordonnanceur** (`jdk.virtualThreadScheduler.parallelism`). Déplace la limite sans l'ôter, et la
  configure par une propriété système que le lanceur empaqueté devrait porter.
- **Servir toutes les requêtes sur un pool de threads plateforme borné.** Simple, mais `health` attendrait alors
  derrière les requêtes qui attendent la base : le défaut mesuré, sous une autre forme.
- **Lire le chemin dans le décorateur pour décider.** Ce serait une lecture de chemin de plus, hors du parseur
  partagé ; enregistrer les routes sans base dans leur propre contexte dit la même chose sans lire quoi que ce soit.

## Conséquences

- Une requête du serveur local occupe deux threads pendant qu'elle travaille : un virtuel garé, un travailleur. Le
  nombre de travailleurs est fixe.
- La file d'attente des requêtes n'est pas bornée par ce changement. Borner l'admission est l'objet de
  `admit-local-requests-before-they-meet-the-database`, qui a maintenant un seul endroit où le faire.
- Le serveur distant relaie ses requêtes au serveur local par HTTP : ses threads virtuels attendent le réseau et se
  garent. Il en bénéficie sans changement. Sa route de sauvegarde, elle, appelle SQLite directement sur un thread
  virtuel : elle peut tenir un porteur pendant une sauvegarde, dans la limite de la concurrence privilégiée. Non
  mesuré, et laissé en l'état. *(Mesuré et corrigé le 17 octobre 2026 : voir l'amendement du même jour.)*
- Le serveur MCP STDIO n'est pas concerné : il ne sert pas ses appels sur des threads virtuels.
- Le décorateur possède son pool et l'arrête quand le serveur qu'il décore s'arrête
  (`RequestProtectedHttpServer.stopped`).

## Preuves exécutables

- `SchedulerAvailabilityUnderSqliteWaitTest` : rouge avant le changement (treize secondes), verte après ; son message
  d'échec donne les processeurs, les lectures en vol, leurs statuts et les latences mesurées.
- `LocalHttpServerBootstrapArchitectureTest` : le contexte de l'API et les routeurs à contexte propre sont enregistrés
  à travers les travailleurs ; un seul enregistrement direct subsiste, celui des routes sans base.
- `PublicHttpRouteConvergenceTest` passe sans modification : chaque route de la table est toujours servie.
