# Revérification du 13 octobre 2026

Les 96 constats de l'audit du 9 octobre, réétablis un par un sur le code quatre jours plus tard.

**Référence** `origin/develop` = `d5be889b` (13 oct.), contre `20cf2e0` (9 oct.). Entre les deux : **263 commits, 113 PR fusionnées**. `origin/main` est **95 commits derrière** `develop` — un fait qui a son importance, voir plus bas.

**Méthode** — un agent par axe, sept au total, chacun avec la consigne cardinale : *un change OpenSpec qui cite un constat, un message de commit, une tâche cochée sont des revendications, pas des preuves ; on ouvre le code et on vérifie le fait.* Statuts : `corrigé`, `partiel`, `toujours valide`, `caduc`, et pour les constats *Info* — qui affirment une propriété plutôt qu'un défaut — `tient` ou `ne tient plus`.

---

## Résultat

| | 9 oct. | 13 oct. |
|---|---|---|
| Constats | 96 | **98** (deux nés d'une scission) |
| Actionnables | 72 | 74 |
| — corrigés | — | **24** |
| — partiels | — | 9 |
| — toujours valides | — | 40 |
| — caducs | — | 1 |
| *Info* qui tiennent | 24 | **22** (1 non vérifiable ici, 1 caduc) |

| Axe | 9 oct. | 13 oct. | | Ce qui reste |
|---|---|---|---|---|
| Sécurité | 75 | **100** | +25 | rien d'ouvert |
| Dépendances / exploitation | 36 | **68** | +32 | 1 Élevée, 5 Moyennes, 2 Faibles |
| Tests | 54 | **69** | +15 | 7 Moyennes, 3 Faibles |
| Qualité | 46 | **56** | +10 | 1 Élevée, 8 Moyennes, 2 Faibles |
| Performance | 28 | **52** | +24 | 2 Élevées, 6 Moyennes, 4 Faibles |
| Architecture | 26 | **35** | +9 | 3 Élevées, 8 Moyennes, 3 Faibles |
| **Moyenne** | **44** | **63** | **+19** | |

Même barème qu'au 9 octobre. Un constat `partiel` compte pour son poids entier : un défaut à moitié traité reste un défaut ouvert.

**Sécurité à 100 ne veut pas dire « le produit est sûr ».** Cela veut dire qu'aucun constat de sécurité de cet audit ne reste ouvert. Les angles morts d'origine sont intacts : le mode remote n'a jamais été exercé de bout en bout, aucun scan NVD n'a tourné, et cette revérification n'a pas pu lancer un seul test.

**Sprints** — 51 constats affectés, 23 soldés, 28 restants, charge restante 49 à 144 jours. Le **sprint 3 est terminé** (8/8). Les sprints 1, 2, 4, 5 et 6 sont partiellement soldés. Les sprints 7 à 14 n'ont pas commencé — conformément à l'ordre.

---

## Ce qui est réellement fait

**Le chemin critique de l'audit est levé.** `AUD-TST-01` — les cinq budgets de performance M19 que plus aucun pipeline n'atteignait — est corrigé et vérifié : job `m19-budgets` dans `nightly.yml:218`, matrice Linux et Windows, sans `continue-on-error`, lançant deux scripts qui existent et nomment les cinq gates par `-Dtest=`. Le job est **aussi sur `main`**, donc le `schedule` l'atteint réellement. Un test d'architecture l'ancre (`GateReachabilityArchitectureTest.java:119`). Il débloque sept constats en aval.

**L'axe Sécurité est soldé.** Les six constats actionnables sont corrigés, chacun avec sa règle d'enforcement : les quatre routeurs passent par `BoundaryFailureMessage.safe`, le double décodage de chemin a disparu au profit d'un parseur unique partant de `getRawPath()`, le journal d'accès remote existe et est fail-closed, la commande Java des pairs MCP se résout sur `java.home`, les en-têtes sont uniformes avec HSTS côté remote, et le `.gitignore` protège identités et keystore avec un gate qui vérifie les deux sens.

**Le cluster irréversible est désamorcé.** `UPGRADE_1_2_1.md` existe, nomme les migrations V016→V020, rend la sauvegarde obligatoire, et un test dérive la version de schéma de `SqliteSchemaManager` au lieu de la recopier. Mieux : la sauvegarde pré-migration est devenue **automatique et bloquante** (`SqlitePreMigrationCopy`, appelée depuis `SqliteStoreConnection.open:76` avant `migrate`), ce que l'audit n'avait demandé qu'en option.

**Le piège que je redoutais a été traité de front.** `AUD-DEP-06` reposait sur une assertion `contains("820")` satisfaite par la sous-chaîne de « 3820 » — un verrou qui ne verrouillait rien. Elle a disparu, remplacée par une règle qui porte **sur l'opération et non sur une valeur** : elle extrait les deux comparaisons par regex, exige qu'il y en ait exactement deux, et refuse tout opérande numérique en dur. Le javadoc nomme explicitement le défaut de sous-chaîne.

**Et deux N+1 ont été réellement supprimés** : la lecture de contenu métier passe à une requête par table enfant, et l'écriture des liens de traçabilité passe de ~100 000 `prepareStatement` pour 25 000 liens à **cinq au total**, indépendamment de la taille du lot.

---

## Le point le plus intéressant : un constat démoli par la mesure

`AUD-PRF-18` — l'épinglage des threads virtuels, que j'avais classé Élevée et présenté comme le chemin vers l'indisponibilité du serveur entier — devient **caduc**, et c'est le meilleur résultat possible.

Le dépôt n'a pris aucune des trois options que je proposais. Il a **mesuré d'abord** (`SchedulerAvailabilityUnderSqliteWaitTest`, ADR-0114) : sur JDK 24, où JEP 491 a supprimé l'épinglage par moniteur, une sonde `health` attend quand même **13 secondes** pendant que 64 lectures attendent une base tenue en `BEGIN EXCLUSIVE`. Ce sont donc les **trames natives du pilote SQLite** qui tiennent les porteurs, pas les moniteurs. Mon constat avait la bonne conséquence et la mauvaise cause. Le remède retenu — déporter tout gestionnaire pouvant toucher la base sur un pool borné de threads plateforme — neutralise les deux ; `health` repasse à 7 ms.

Une nuance que l'ADR laisse implicite et qui mérite d'être au dossier : cette mesure exonère les moniteurs **sur JDK 24, pas sur la baseline Java 21 livrée**, où un blocage dans un `synchronized` épingle bel et bien le porteur en plus des trames natives. Le remède rend la question sans objet là où il s'applique, puisqu'un thread plateforme ne peut pas être épinglé. Le rejet de l'option « verrous » reste défendable ; sa démonstration est incomplète.

Le constat est remplacé par deux :

- **`AUD-PRF-19`** (Moyenne, sprint 5) — le serveur **remote** sert toujours sur threads virtuels nus, sans le décorateur qui protège désormais le local, et sa route de sauvegarde appelle SQLite directement. L'ADR le reconnaît : « non mesuré, et laissé en l'état ». C'est la seule surface exposée hors machine.
- **`AUD-ARC-15`** (Faible, sprint 10) — les 94 moniteurs n'ont toujours aucune raison d'être documentée. Ce n'est plus un risque de disponibilité, c'est une justification manquante : les stores sont construits par requête, chaque moniteur protège un objet qu'un seul thread atteint.

---

## Revendication contre réalité

**Les sept agents ont cherché des tâches cochées que le code contredit. Ils n'en ont trouvé aucune.** C'est le résultat de gouvernance le plus notable de cette passe, et il mérite d'être dit avant les réserves : les 61 changes OpenSpec non implémentés le déclarent en tête (« Implementation is a separate lot. Nothing below is done by the pull request that introduces this change »), et dans les changes fusionnés, les tâches non faites sont décochées avec une note datée expliquant pourquoi. Plusieurs `tasks.md` **contredisent l'audit sur ses propres chiffres** et écrivent « trust neither number » — c'est la bonne attitude.

Trois asymétries méritent néanmoins d'être enregistrées.

**1. Corriger un constat en a aggravé un autre.** `AUD-QUA-02` (le localisateur de racine de dépôt recopié) passe de **73 à 91 copies**. Les deux gates ajoutés pour fermer `AUD-QUA-05` et `AUD-QUA-12` embarquent chacun leur propre copie privée. Le sprint 3 a recopié le localisateur plus vite que le dépôt ne l'avait fait en deux mois. Même mécanique sur `AUD-TST-03` : les `assertThrows` nus passent de **300 à 332**, les nouveaux tests reproduisant le défaut que le constat décrit. Et sur `AUD-TRV-03`, le ratio enforcement bytecode / textuel se **dégrade** de 1 pour 35 à 1 pour 39 : les correctifs des autres constats alimentent celui-là, parce qu'ils sont presque tous des assertions textuelles.

**2. Un change archivé solde un constat qui ne l'est pas entièrement.** `make-declared-gates-reachable` revendique `AUD-TRV-01`. Trois volets sur quatre sont faits, et bien faits : les quatre `.ps1` morts sont retirés avec leurs SHA de récupération, la parité `.ps1`/`.sh` est assertée **génériquement**, et `git switch` est interdit dans un validateur. Le quatrième — corriger les commandes citées dans `VALIDATION_M15..M18.md` — a été écarté par une décision défendable (immuabilité des preuves datées), mais celle-ci est enregistrée dans un README de répertoire, pas dans le suivi du constat. Un lecteur du plan d'action croira le volet traité.

**3. Le décompte de cases cochées ne mesure rien.** `own-the-sqlite-connection-policy-in-one-place` coche 7 tâches sur 13, dont la 3.2 — le site que le constat nommait — est explicitement non faite. À l'inverse, `record-who-acted-on-the-remote-server` laisse cinq cases décochées alors que le défaut est bel et bien corrigé. La proportion ne corrèle pas avec l'avancement, dans les deux sens.

---

## Ce qui s'est dégradé

**`AUD-DEP-12` a changé de nature.** Ce n'est plus seulement « le pom est à 1.2.1 sans tag ». L'écart au dernier tag passe de 1 588 à **1 865 commits**, et surtout **`main` est maintenant 95 commits derrière `develop`**. `release.yml` ne se déclenche que sur un tag : un tag sur `develop` publierait un état que `main` n'a jamais vu, un tag sur `main` publierait une 1.2.1 amputée de 95 commits. La question « sur quelle branche taguer ? » est devenue bloquante.

Conséquence en cascade que personne n'avait vue : le job `refresh-alert` qui crédite `AUD-DEP-07` ne tourne quotidiennement qu'une fois le workflow présent sur `main`. **L'alerte CVE anticipée n'est donc pas encore quotidienne.** Promouvoir `develop` vers `main` avant de taguer règle les deux.

Trois comptages ont monté sans traitement : `AUD-QUA-02` (73 → 91), `AUD-TST-03` (300 → 332), `AUD-TRV-03` (1/35 → 1/39). Et `AUD-QUA-09`, les jumeaux MINOS/NEXUS, a vu son diff normalisé passer de 48 à **61 lignes** : les deux chemins de confiance continuent de diverger.

---

## Corrections apportées à l'audit lui-même

La revérification a trouvé trois erreurs dans mes propres constats. Elles sont corrigées dans `findings.md` :

- **`AUD-ARC-11`** annonçait « quatre classes de production » utilisant la réflexion ; il y en a **trois**. Le fond tient.
- **`AUD-ARC-13`** affirmait que la politique de blocage de contrainte n'a qu'un seul consommateur applicatif ; il y en a **deux** (`ChangeOrchestrationStateService` et `ConstraintPolicyEvaluationService`).
- **`AUD-PRF-18`** avait la bonne conséquence et la mauvaise cause — voir plus haut.

Un agent a par ailleurs annoncé 116 ADR ; le compte réel est **114**, plus haut numéro 0114, sans doublon. J'ai retenu le chiffre de la source vivante.

Et trois constats auraient dû **nommer leur critère de comptage**. Les `tasks.md` du dépôt recomptent trois des chiffres de l'audit et trouvent trois valeurs différentes — 44 contre 45 contre 50 scripts, 21 contre 13 contre 23 `catch` silencieux. Aucun n'est faux : ils mesurent trois choses. Un constat qui donne un nombre sans dire comment il l'a obtenu est invérifiable, et c'est une leçon pour le prochain audit.

---

## Ce qui reste

Six constats de sévérité **Élevée** restent ouverts :

| Constat | Statut | Sprint | Objet |
|---|---|---|---|
| `AUD-ARC-01` | toujours valide | 11 | Le cœur applicatif fait du `java.nio.file` et du HTTP sortant en direct |
| `AUD-ARC-02` | toujours valide | 11 | Le chemin d'ingestion principal n'a aucun port |
| `AUD-PRF-02` | toujours valide | 6 | BFS complet relancé par nœud déjà découvert — fichier strictement intact |
| `AUD-PRF-04` | toujours valide | 6 | Trois tables append-only sans `LIMIT` ni purge |
| `AUD-TRV-04` | partiel | 4 | Le produit parle enfin, mais 5 sites de suppression restent sans trace |
| `AUD-TRV-05` | partiel | 5 | 5 ouvertures de scope SQLite sur 12 sites, le runtime dupliqué non absorbé |

**L'axe Architecture est le seul à n'avoir presque pas bougé** — 10 constats sur 11 toujours valides, un seul partiellement entamé. Ce n'est pas de la négligence : ces constats sont planifiés sprints 9 à 12, et l'ordre a été respecté. `MorpheusCli.java` et `MorpheusMcpToolService.java` n'ont reçu **aucun commit** en quatre jours, au caractère près.

Deux aggravations à traiter avant qu'elles ne grossissent, parce qu'elles sont alimentées par le travail en cours : le localisateur de racine recopié à chaque nouveau gate, et la part textuelle de l'enforcement qui croît avec chaque correctif.

---

## Angles morts de cette revérification

Les mêmes qu'au 9 octobre, et pour la même raison.

- **Aucun build, aucun test, aucun gate exécuté.** Maven Central est toujours refusé par le proxy du conteneur d'audit et `~/.m2` est vide. Tous les statuts ci-dessus sont établis par **lecture de code**. Les règles d'architecture et les tests que je cite existent et leur logique est correcte à la lecture ; je ne peux pas affirmer qu'ils **passent**. Seule exécution réelle : `python3 -m unittest discover -s scripts/tests` → *Ran 56 tests, OK* (contre 7 tests le 9 octobre).
- **Aucune couverture mesurée**, donc `AUD-TRV-02` reste vérifié sur ses seuils déclarés et jamais sur un rapport JaCoCo du jour. Les 20,87 points d'écart entre les deux échelles sont relus dans les gates, pas mesurés.
- **Aucun scan NVD**, aucun `EXPLAIN QUERY PLAN`, aucun profileur, aucun test de charge. Les gains des corrections de performance sont établis en nombre d'instructions préparées, pas en temps.
- **Le statut de Dependency-Check 13.0.1 reste indéterminé** : Maven Central refusé, recherche web non concluante.
- **Les preuves d'exécution citées par les `tasks.md`** (runs GitHub Actions, mesures Windows) sont des revendications que je n'ai pas pu rejouer. Point de vigilance signalé par l'agent Tests : sur runner Windows hébergé, les marges des budgets M19 tombent à 2,6×–3,7× contre ~10× sur la machine de référence — cette voie est sensible au bruit d'infrastructure.
- **Une fragilité structurelle, signalée par plusieurs agents** : les règles ajoutées pour verrouiller `AUD-TRV-04`, `-05` et `-06` sont **textuelles** et reposent sur des littéraux de code (`"Path launchJar = staged.orElseThrow();"`, des listes de noms de fichiers). Un renommage les rend silencieuses sans rien casser. C'est exactement ce que `AUD-TRV-03` décrit, et c'est pourquoi ce constat se dégrade pendant qu'on corrige les autres.
