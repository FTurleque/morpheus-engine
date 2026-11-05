---
id: BE-0159
title: "Le travail sur la base s'exécute sur un pool borné de threads plateforme"
domain: backend
rules: false
---

# Le travail sur la base s'exécute sur un pool borné de threads plateforme

- Statut : **Acceptée**
- Date : 2026-11-03
- Remplace : ADR-0114

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
| Décorer le serveur distant comme le local | Chaque requête relayée, même celles qui n'attendent que le réseau, occuperait un travailleur, et la capacité de la façade tomberait au nombre de travailleurs ; l'admission précéderait l'authentification, et un appelant anonyme pourrait tenir la file des sauvegardes |
| Dériver la taille du pool du nombre de processeurs | Un travailleur attend un fichier SQLite, il ne calcule pas : plus de travailleurs sur une grosse machine ajoutent de la contention sur ce fichier, moins sur un runner de deux processeurs refusent un pic qu'un poste met en file, et le comportement de l'admission changerait d'une machine à l'autre |
| Laisser l'appelant d'une sauvegarde distante attendre sans délai | Une sauvegarde qui ne finit pas tiendrait sa requête, sa place privilégiée et son créneau sans fin, sans que rien ne le dise |

### Détail du mécanisme

#### Serveur local

1. **Tout gestionnaire qui peut toucher la base s'exécute sur un travailleur.** `DatabaseWorkOffloadingHttpServer`
   décore le serveur local : un gestionnaire enregistré à travers lui est remis à un pool de threads plateforme, et le
   thread virtuel qui a reçu la requête se gare jusqu'à la fin, ce qui rend son porteur. Une requête qui attend la base
   occupe un travailleur, jamais un porteur.
2. **Le pool est borné, et sa taille est la concurrence de la base.** `DATABASE_WORKERS` vaut huit, quelle que soit la
   machine, et est déclaré à un seul endroit, dans `DatabaseCallWorkers`, le mécanisme des deux serveurs. Un pic devient
   une file au lieu d'une contention de connexions sur la base.
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

#### Serveur distant

9. **La façade ne relaie pas tout à un pool.** Elle relaie ses requêtes au serveur local par HTTP : ses threads
   virtuels attendent le réseau et se garent. La sauvegarde est la seule route que la façade sert avec SQLite en main :
   elle seule passe par un pool de `DatabaseCallWorkers` propre à la façade, après l'authentification, le rôle et la
   voie privilégiée, pendant que le thread virtuel de la requête se gare. Le travail SQLite de l'adaptateur est un
   `DatabaseCall` nommé, que seuls les travailleurs exécutent.
10. **La façade borne l'attente et le travail, et refuse par son nom.** Une sauvegarde attend une place au plus
    `ADMISSION_WAIT`, puis reçoit `429 DATABASE_WORKERS_BUSY` ; une fois prise, elle attend au plus `BACKUP_DEADLINE`, le
    délai d'une écriture relayée, puis reçoit `504 BACKUP_TIMEOUT`, issue inconnue, son travailleur gardant sa place
    jusqu'au retour de l'appel. Côté local, le gestionnaire écrit lui-même sa réponse : son appelant attend la fin sans
    délai propre. Le pool est arrêté avec le serveur.

#### Hors de portée

- Les moniteurs des stores ne sont pas touchés : le remède n'en dépend pas.
- Le serveur MCP STDIO n'est pas concerné : il ne sert pas ses appels sur des threads virtuels.

## Decision

1. Tout travail SQLite des serveurs HTTP s'exécute sur un pool borné de threads plateforme, `DatabaseCallWorkers`,
   jamais sur le thread virtuel de la requête, qui se gare et rend son porteur.
2. `DATABASE_WORKERS` vaut huit sur toute machine et n'est déclaré que dans `DatabaseCallWorkers`, seul constructeur
   d'un pool de threads plateforme de l'adaptateur.
3. Serveur local : `DatabaseWorkOffloadingHttpServer` remet chaque gestionnaire entier à un travailleur ; les routes
   qui répondent sans la base (`health`, `metrics`, `version`) sont enregistrées hors de lui.
4. Une place par travailleur, admise par un sémaphore équitable ; au-delà de `ADMISSION_WAIT`, refus nommé : `429
   LOCAL_SERVER_BUSY` en local, `429 DATABASE_WORKERS_BUSY` sur la façade. Le plafond local suit la base, pas le
   réglage de la façade.
5. Serveur distant : la façade n'est pas décorée entière, ses requêtes relayées attendent le réseau. Sa sauvegarde,
   seule route SQLite, passe par les travailleurs après l'authentification, le rôle et la voie privilégiée, et attend
   au plus `BACKUP_DEADLINE`, puis `504 BACKUP_TIMEOUT`, issue inconnue.
6. Le travail SQLite est un `DatabaseCall` nommé que seuls les travailleurs exécutent ; chaque pool s'arrête avec son
   serveur.

Le détail de chaque point est dans la section Context, sous « Détail du mécanisme ».

## Do's and Don'ts

### Do

- Enregistrer tout routeur qui peut atteindre la base à travers `DatabaseWorkOffloadingHttpServer`.
- Garder dans `ANSWERED_WITHOUT_THE_DATABASE` les seules routes qui ne touchent aucune base.
- Lire la taille du pool dans `DatabaseCallWorkers.DATABASE_WORKERS`, et la borne d'attente dans `ADMISSION_WAIT`.
- Écrire tout travail SQLite de l'adaptateur HTTP comme une classe nommée qui implémente `DatabaseCall`, remise aux
  travailleurs.

### Don't

- Ne pas enregistrer directement sur le serveur HTTP un routeur qui atteint la base.
- Ne pas laisser une requête attendre un travailleur sans borne.
- Ne pas décider du passage par les travailleurs en relisant le chemin dans le décorateur.
- Ne pas décorer la façade distante entière par un pool de travailleurs.
- Ne pas construire un second pool de threads plateforme dans l'adaptateur HTTP, ni exécuter un `DatabaseCall` hors
  des travailleurs.
- Ne pas dériver la taille du pool du nombre de processeurs.
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
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/DatabaseWorkArchitectureTest.java`, sur le
  bytecode de tout l'adaptateur : un appel à `SqliteServerMaintenance` (hors le bail pris au démarrage) ne se fait que
  depuis un `DatabaseCall` ; seul `DatabaseCallWorkers` exécute un `DatabaseCall` ; le décorateur local remet ses
  gestionnaires aux travailleurs ; seul `DatabaseCallWorkers` construit un pool de threads plateforme.
- `morpheus-api/src/test/java/com/morpheus/api/local/DatabaseCallWorkersTest.java` : refus nommé après l'attente
  d'admission, refus nommé à l'échéance avec la place gardée par le travailleur.
- `morpheus-api/src/test/java/com/morpheus/api/SchedulerAvailabilityUnderSqliteWaitTest.java` : sur un vrai serveur
  local, `health` reste servie pendant qu'un écrivain exclusif tient la base et que les lectures en vol dépassent le
  nombre de processeurs.
- `morpheus-api/src/test/java/com/morpheus/api/remote/RemoteSchedulerAvailabilityUnderSqliteWaitTest.java` : même preuve sur
  le serveur distant, sauvegardes en vol.
- `morpheus-api/src/test/java/com/morpheus/api/local/LocalAdmissionTest.java` : jamais plus de requêtes dans les
  travailleurs que le pool n'en a, refus nommé avec `Retry-After` après l'attente bornée, place rendue par un
  gestionnaire qui échoue.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java` : chaque route de
  la table reste servie.

## References

- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — serveur HTTP du JDK.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — concurrence SQLite bornée.
- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0156](ARCH-0156-racines-de-composition-nommees-paquets-sans-cycle-et-decisions-dans-le-domaine.md) — portée de connexion par opération.
- `openspec/changes/archive/2026-11-05-keep-the-scheduler-free-while-sqlite-waits`,
  `openspec/changes/archive/2026-11-05-admit-local-requests-before-they-meet-the-database`.
