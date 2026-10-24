---
id: BE-0114
title: "Le travail sur la base s'exécute sur un pool borné de threads plateforme"
domain: backend
rules: false
---

# Le travail sur la base s'exécute sur un pool borné de threads plateforme

- Statut : **Acceptée**
- Date : 2026-10-13

## Context

Les deux serveurs HTTP exécutent chaque requête sur un thread virtuel. Le pilote SQLite est du code natif, et il
attend une base occupée **à l'intérieur de l'appel natif**, jusqu'au délai d'occupation de la connexion. Un thread
virtuel dans une trame native reste monté sur son porteur, et les porteurs sont aussi nombreux que les processeurs.
Quand assez de requêtes attendent la base, tous les porteurs sont tenus : le serveur entier cesse de répondre, sonde
de vie comprise, alors que `health` ne touche aucune base.

Ce sont les trames natives qui tiennent les porteurs, pas les moniteurs : le défaut se mesure sur un JDK où
l'épinglage par moniteur n'existe plus (JEP 491). `-Djdk.tracePinnedThreads` ne le montre pas : il signale un thread
virtuel qui se *gare* épinglé, pas un thread qui occupe son porteur dans du code natif.

Options écartées :

| Option | Raison |
|---|---|
| Remplacer les moniteurs par des verrous | Ne libère pas un thread qui attend dans du code natif |
| Passer la baseline à un JDK plus récent | Même raison, pour le coût le plus élevé (Enforcer, CI, runtime embarqué) |
| Agrandir l'ordonnanceur (`jdk.virtualThreadScheduler.parallelism`) | Déplace la limite sans l'ôter, et exige une propriété système que le lanceur empaqueté devrait porter |
| Servir toutes les requêtes sur un pool plateforme borné | `health` attendrait derrière les requêtes qui attendent la base : le même défaut sous une autre forme |
| Lire le chemin dans le décorateur pour décider | Une lecture de chemin de plus, hors du parseur partagé ; enregistrer les routes sans base dans leur propre contexte dit la même chose sans rien lire |
| Décorer le serveur distant comme le local | Chaque requête relayée, même celles qui n'attendent que le réseau, occuperait un travailleur, et la capacité de la façade tomberait au nombre de processeurs |

## Decision

### Serveur local

1. **Tout gestionnaire qui peut toucher la base s'exécute sur un travailleur.** `DatabaseWorkOffloadingHttpServer`
   décore le serveur local : un gestionnaire enregistré à travers lui est remis à un pool de threads plateforme, et le
   thread virtuel qui a reçu la requête se gare jusqu'à la fin, ce qui rend son porteur. Une requête qui attend la base
   occupe un travailleur, jamais un porteur.
2. **Le pool est borné, et sa taille est la concurrence de la base.** `DATABASE_WORKERS` (le nombre de processeurs,
   avec un minimum de deux) est déclaré à un seul endroit, dans `DatabaseWorkOffloadingHttpServer`. Un pic devient une
   file au lieu d'une contention de connexions sur la base.
3. **Le gestionnaire entier, sur un seul travailleur.** La portée de connexion (`SqliteConnectionScope`) est confinée à
   son thread ; déporter le gestionnaire d'un bloc lui garde son sens.
4. **Les routes qui répondent sans la base ne passent pas par les travailleurs.** Les routes de
   `MorpheusRootHttpRoutes.ANSWERED_WITHOUT_THE_DATABASE` (`health`, `metrics`, `version`) sont enregistrées
   directement, hors du décorateur, pour rester servies quand tous les travailleurs sont pris. `readiness` interroge la
   base et passe par eux.
5. **Une place par travailleur, attendue dans l'ordre d'arrivée.** Un sémaphore équitable de `DATABASE_WORKERS`
   permis admet les requêtes. Une requête tient une place exactement pendant qu'un travailleur la traite : la file
   interne du pool reste vide, et la seule file est celle de l'admission.
6. **L'attente est bornée, puis refusée par son nom.** Au-delà de `ADMISSION_WAIT` (deux fois le délai d'occupation
   d'une connexion), la requête reçoit `429`, code `LOCAL_SERVER_BUSY`, un `Retry-After` (`RETRY_AFTER_SECONDS`) et les
   en-têtes de toute réponse locale. Le refus est écrit par le thread de la requête : rien n'a été remis à un
   travailleur.
7. **Le plafond suit la base, en mode distant aussi.** Le plafond local n'est pas aligné sur celui de la façade
   distante, qui est un réglage de l'opérateur : l'égaler ramènerait dans la base la contention que le pool évite. Une
   requête admise par la façade peut donc attendre un travailleur, dans la même borne, et recevoir ce `429` relayé.
8. **Le décorateur possède son pool** et l'arrête quand le serveur qu'il décore s'arrête
   (`RequestProtectedHttpServer.stopped`).

### Serveur distant

9. **La façade ne relaie pas tout à un pool.** Elle relaie ses requêtes au serveur local par HTTP : ses threads
   virtuels attendent le réseau et se garent. La sauvegarde est la seule route que la façade sert avec SQLite en main :
   elle seule passe par `DatabaseCallWorkers`, un pool de threads plateforme de taille `DATABASE_WORKERS`, pendant que
   le thread virtuel de la requête attend un futur et libère son porteur. Aucun appel à `maintenance.` de
   `MorpheusRemoteHttpServer` ne se fait hors de `databaseWorkers.call(`.
10. **Pas d'admission propre côté façade.** Les sauvegardes sont déjà bornées par la voie privilégiée de la façade ;
    une sauvegarde au-delà des travailleurs attend dans la file du pool. Le pool est arrêté avec le serveur.

### Hors de portée

- Les moniteurs des stores ne sont pas touchés : le remède n'en dépend pas.
- Le serveur MCP STDIO n'est pas concerné : il ne sert pas ses appels sur des threads virtuels.

## Do's and Don'ts

### Do

- Enregistrer tout routeur qui peut atteindre la base à travers `DatabaseWorkOffloadingHttpServer`.
- Garder dans `ANSWERED_WITHOUT_THE_DATABASE` les seules routes qui ne touchent aucune base.
- Lire la taille du pool dans `DATABASE_WORKERS`, et la borne d'attente dans `ADMISSION_WAIT`.
- Faire passer tout appel SQLite de la façade distante par `databaseWorkers.call(`.

### Don't

- Ne pas enregistrer directement sur le serveur HTTP un routeur qui atteint la base.
- Ne pas laisser une requête attendre un travailleur sans borne.
- Ne pas décider du passage par les travailleurs en relisant le chemin dans le décorateur.
- Ne pas décorer la façade distante entière par un pool de travailleurs.
- Ne pas remplacer ce mécanisme par un agrandissement de l'ordonnanceur des threads virtuels.

## Consequences

### Positives

- `health`, `metrics` et `version` restent servies pendant qu'une base occupée retient les travailleurs.
- Un pic de requêtes devient une file bornée, et un dépassement est refusé par un code nommé.
- La sauvegarde distante ne tient aucun porteur pendant qu'elle attend la base.

### Négatives

- Une requête du serveur local occupe deux threads pendant qu'elle travaille : un virtuel garé, un travailleur.
- Le nombre de travailleurs est fixe.
- Aucun compteur des refus d'admission : le décorateur n'a pas de métriques, et lui en donner par le runtime statique
  ajouterait un utilisateur à une liste qui ne fait que rétrécir.

### Risques

- Ce que protège chaque moniteur des stores, construits par requête, n'est pas documenté ; ce n'est pas une question
  de disponibilité.
- Les échecs de sauvegarde distante observés sous saturation, quand la sauvegarde ne passe pas par les travailleurs,
  n'ont pas de cause établie (famine des porteurs ou course sur le verrou de la base) ; une sauvegarde qui passe par
  les travailleurs ne les produit pas.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LocalHttpServerBootstrapArchitectureTest.java` :
  le contexte de l'API et les routeurs à contexte propre sont enregistrés à travers les travailleurs ; un seul
  enregistrement direct subsiste, celui des routes de `ANSWERED_WITHOUT_THE_DATABASE`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m26/RemoteServerArchitectureTest.java`
  (`theRemoteFacadeCallsTheDatabaseOnlyThroughItsDatabaseWorkers`) : tout appel à `maintenance.` de
  `MorpheusRemoteHttpServer` passe par `databaseWorkers.call(`.
- `morpheus-api/src/test/java/com/morpheus/api/SchedulerAvailabilityUnderSqliteWaitTest.java` : sur un vrai serveur
  local, `health` reste servie pendant qu'un écrivain exclusif tient la base et que les lectures en vol dépassent le
  nombre de processeurs.
- `morpheus-api/src/test/java/com/morpheus/api/remote/RemoteSchedulerAvailabilityUnderSqliteWaitTest.java` : même preuve sur
  le serveur distant, sauvegardes en vol.
- `morpheus-api/src/test/java/com/morpheus/api/local/LocalAdmissionTest.java` : jamais plus de requêtes dans les
  travailleurs que `DATABASE_WORKERS`, refus nommé avec `Retry-After` après l'attente bornée, place rendue par un
  gestionnaire qui échoue.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java` : chaque route de
  la table reste servie.

## References

- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — serveur HTTP du JDK.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — concurrence SQLite bornée.
- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — portée de connexion par opération.
- `openspec/changes/keep-the-scheduler-free-while-sqlite-waits`,
  `openspec/changes/admit-local-requests-before-they-meet-the-database`.
