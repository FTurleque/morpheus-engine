# Revérification du 6 novembre 2026

**Base** — `origin/develop` = `30520bd3` (PR #613), soit **62 commits et 386 fichiers** (+15 806 / −1 067) depuis `de748b38`, la base du 31 octobre.

**Build** — `./mvnw clean verify` sur les 19 modules : **BUILD SUCCESS en 8 min 33, 5 255 tests, 0 échec, 0 erreur, 14 ignorés.** Deux minutes de moins qu'au 31 octobre, les suites `api`, `mcp` et `cli` tournant désormais en parallèle.

**Méthode** — quatre lots en parallèle, même consigne : *une affirmation n'est pas une preuve, seul le code tranche*, et cela vaut pour les `verif_preuve` déjà inscrits. Les agents chargés des mesures chiffrées ont d'abord **rejoué leur méthode sur la base précédente** jusqu'à en retrouver les chiffres. Le constat neuf et l'arbitrage sur `AUD-DEP-05` ont été revérifiés à la main.

---

## Le dépôt a changé de méthode, et c'est l'événement de la passe

Cette vague ne se contente pas de corriger : elle a écrit un relevé, **`closures-2026-11-03.md`**, qui revendique onze constats traités, **avec la preuve qu'on peut rejouer**, et qui ouvre par cette phrase :

> « Il ne change **pas** les statuts de `findings.json` : une ligne de ce fichier est une revendication, et la revérification suivante dira si le code la confirme. »

Une section « Ce qui n'est pas fait » y liste cinq réserves assumées. C'est la bonne répartition du travail : le dépôt dit ce qu'il a fait et où il s'est arrêté, l'audit vérifie. Toutes les réserves qu'il annonçait se sont vérifiées exactes.

**Les onze revendications sont confirmées.** Aucune ne reposait sur une réécriture de texte ; chacune tient par une règle compilée, une suppression de code ou une décision écrite et acceptée.

| | |
|---|---|
| Moyenne au 31 octobre | **84** |
| Moyenne au 6 novembre | **93** |

C'est le plus grand mouvement d'une passe depuis l'ouverture de l'audit. **124 constats, 14 actifs** — 4 `Moyenne`, 10 `Faible`. L'axe **Sécurité atteint 100**.

---

## Les onze clôtures, rouvertes dans le code

**`AUD-PRF-19`** — les trois points les plus laids du reste ont **disparu, pas été déplacés** : le motif `maintenance\.\w+\(` et la fenêtre de 60 caractères n'existent plus nulle part dans le dépôt. Le remplacement est `DatabaseWorkArchitectureTest`, **quatre vraies règles ArchUnit sur le bytecode**. `DATABASE_WORKERS` passe d'`availableProcessors()` à 8 fixe, le pool est borné par une admission à 10 s. L'exécuteur distant reste non décoré — mais l'action offrait deux branches, et c'est la seconde qui est honorée : `ADR-0159`, accepté, **écarte explicitement** la décoration avec son motif, et la seule interaction base directe de la façade est déportée sur le mécanisme.

**`AUD-SEC-17` et `AUD-SEC-18`** — le chemin rapide interroge désormais la racine et **chaque segment** avant toute lecture ; le cache n'épargne plus que la résolution, jamais le test de lien. Le refus a son code propre et arrête le scan, racine suivante comprise. Et `ADR-0157` tranche enfin entre les deux frontières qui se contredisaient : il les nomme toutes les deux et choisit la propriété **forte** — la plus coûteuse, pas la plus commode.

**`AUD-ARC-16`** — les deux voies sont faites. `RUNTIME_PUBLIC_TYPES` (13 entrées) est exact dans les deux sens depuis le bytecode, avec son cliquet. Et `ARCH-0158` est cette fois l'ADR attendu : bon module, bonne propriété — la **visibilité**, pas la dépendance —, accepté, et il écarte le `module-info.java` par écrit avec son motif. Mon refus du 31 octobre de clore sur `ARCH-0155`, qui écrivait la décision pour l'autre module, était fondé.

**`AUD-TRV-08`** — `RepositoryTree` existe, **onze sites** l'utilisent (vérifiés un à un), aucune marche de la racine ne subsiste ailleurs, la règle couvre tous les modules du réacteur, et le comportement est prouvé en bac à sable avec une copie plantée — le dispositif ne dépend plus d'un worktree réel pour se démontrer, ce qui était précisément le reproche.

**`AUD-TST-22` et `AUD-TST-23`**, ouverts par l'audit six jours plus tôt, sont clos — et sur le **mécanisme**, pas seulement l'instance. Pour le premier, l'assertion inter-échelles existe (`headsAcrossScales`, appelée **hors** de la boucle par échelle) avec son test négatif dédié ; une requalification sans elle n'aurait fermé que la moitié. Pour le second, l'assertion **lit le registre d'ADR réel**, exige le statut « Acceptée » et que l'ADR nomme la liste, et le cliquet refuse les deux sens par réflexion sur le champ compilé.

**`AUD-DEP-25` et `AUD-DEP-28`** — le réacteur passe à 1.2.2 sur **19 POM sur 19**, et un gate relie désormais les quatre termes : version du POM, schéma livré, dernier niveau publié, guide le plus récent — avec ses deux refus exercés sur données fabriquées. L'égalité manquante est ajoutée sans retirer le plafond, ce qui est le bon ajout. Et `1.2.1=20` est **vérifié exact contre le tag `v1.2.1`** : 20 migrations, schéma 20.

**`AUD-DEP-27`, et c'est l'issue la plus intéressante de la campagne.** Rendre `sqlite-jdbc` identifiable comme le moteur SQLite qu'il embarque **a rendu visibles des CVE réelles** : sur la base CI rafraîchie, l'indice fait apparaître les vulnérabilités de SQLite antérieures à 3.54.0, dont **quatre au-dessus du seuil de 7.0**. D'où la montée du pilote en 3.54.1.0. Corriger un constat de visibilité a immédiatement produit un correctif de sécurité. Et le dépôt ne s'en remet pas à l'indice : une étape de CI lit le rapport produit et échoue sous un nom propre, `SQLITE_ENGINE_NOT_IDENTIFIED`, si l'identification disparaît.

**`AUD-QUA-17`** — clos, et avec **un erratum de ma part** (ci-dessous).

---

## Trois errata, dont un sérieux

**Le plus grave.** Le 30 octobre j'ai inscrit sur `AUD-QUA-17` : « Recompte sur `11bb1ccf` : les chiffres du constat sont inchangés », et je l'ai fait **repasser de `PARTIEL` à `TOUJOURS_VALIDE`** — un durcissement. Ce recompte **n'a pas eu lieu.** Les quatre compteurs statiques sur lesquels reposait tout le constat avaient été retirés le **15 octobre**, quinze jours plus tôt, par un commit ancêtre de la base que je prétendais mesurer. La revendication du dépôt était exacte ; c'est l'audit qui avait tort. Un statut durci sur un recompte annoncé mais non fait est la faute la plus coûteuse que puisse commettre cet audit, puisque c'est exactement ce qu'il reproche au reste du dépôt.

**Le second** concerne `AUD-ARC-19`, que j'ai ouvert le 31 octobre en écrivant « 7 imports, dans les deux sens ». C'était l'addition de deux grandeurs différentes. La mesure exacte est **13 déclarations d'import, 5 fichiers, 9 types distincts**. La conclusion tient, le chiffre était mal formé.

**Le troisième** est une réserve de méthode plutôt qu'une erreur. La méthode de comptage d'`AUD-TRV-03`, qui n'était écrite nulle part, a été reconstruite et **inscrite définitivement dans le constat** — elle ne sera plus à refaire. Rejouée sur la base précédente, elle donne 1 701 assertions là où j'annonçais 1 705 ; l'écart de 0,23 % n'est pas résolu et les comparaisons futures partent de 1 701.

---

## Ce qui reste, et ce qui s'aggrave

**Les trois sprints encore ouverts du plan ne gardent qu'un constat chacun.**

`AUD-TRV-03` reste `PARTIEL`, et **s'éloigne de son critère de sortie sur deux mesures**. Le rapport s'améliore à peine (19,11 → 18,46), et uniquement grâce à quatre règles ajoutées pour d'autres constats : à règles constantes, le texte seul dégrade le rapport. Surtout, **`Files.readString` bouge pour la première fois depuis l'ouverture du constat — à la hausse**, 449 → 455. Durcir la gouvernance des listes de noms a alourdi la machinerie de lecture d'artefacts que ce constat mesure.

`AUD-TRV-02` reste `PARTIEL` pour sa **seule** troisième clause : le seuil de non-régression par module n'existe toujours pas, le gate somme les 16 rapports dans un ratio unique. Attention à une lecture piégeuse : **l'écart passe de 1,28 à 2,67 points sans qu'un seul test de couverture ait changé.** C'est le 1,28 qui était faux — il soustrayait deux mesures prises à deux têtes et sur deux populations. L'écart double parce que la mesure a été réparée, pas parce que la couverture a baissé.

`AUD-DEP-05` reste `PARTIEL` avec blocage amont, et **c'est un `PARTIEL` qui ne progressera plus.** Le mainteneur a accepté le risque le 31 octobre (RT-14) : les releases restent non signées. Ni `CORRIGE` — le constat n'est pas « décider quoi faire », c'est « les binaires ne sont pas signés », et une décision de ne pas agir n'est pas une action —, ni `CADUC` — MORPHEUS publie toujours un binaire Windows. Une faiblesse de forme mérite d'être signalée : une décision de ce poids est portée par une cellule de registre, deux en-têtes de `tasks.md` archivés et un champ JSON, alors que la convention du dépôt pour une décision structurante est l'ADR accepté.

**Trois aggravations.** `AUD-ARC-17` croît pour la **troisième passe consécutive** : aux deux noms de classe et au paquet divergents s'ajoutent désormais **cinq longueurs de fichier périmées et un total faux** dans la même table. Rien ne compare toujours l'ADR au gate. `AUD-TST-21` a vu **son scénario d'impact se réaliser** : une troisième règle de suppression, d'une forme inédite, est entrée dans l'arbre sans qu'aucune assertion ne la voie — pendant que le fichier d'indices voisin, né dans la même vague, recevait son contrat de huit assertions. Et `AUD-QUA-21` récidive pour la deuxième fois : la vague a de nouveau édité le `tasks.md` du document qui porte la phrase fausse sans voir la phrase — et l'archivage massif a fait de ce document le **seul change OpenSpec vivant** du dépôt, donc celui que la prochaine reprise ouvrira à coup sûr.

---

## Un constat neuf

**`AUD-DEP-29` — Moyenne.** `.claude/rules/build.md` déclare neuf versions de dépendances et trois versions épinglées, et **n'est pas dans la liste des neuf surfaces** que le gate de cohérence compare au POM. La dérive a **déjà eu lieu** : la page annonce `jacoco.version=0.8.15` pendant que le POM porte 0.8.16, le bump de dependabot n'ayant touché qu'un seul fichier. Ce n'est pas une documentation parmi d'autres : `CLAUDE.md` la charge à **chaque session d'agent**, donc c'est le premier chiffre que lit quiconque raisonne sur les versions. Et le dépôt s'est donné `rules/meta.md` précisément pour ce mode de défaillance — la règle existe, la page qu'elle vise est hors du gate qui la ferait tenir, et l'exemple qu'elle raconte vient de se reproduire.

---

## Contrôles

Les **24 contrôles de cohérence** passent. Réconciliation faite avant tout travail : **aucune perte de fusion**, et la copie du dépôt était identique à la mienne — les sessions sœurs n'ont pas touché aux statuts, conformément à ce qu'annonçait leur relevé. Le bloc que le dépôt avait ajouté au README de l'audit a été préservé.

**Piège de méthode à retenir** : le refspec du clone d'audit ne ramène que `main`. Un `git fetch origin` laissait `origin/develop` à l'ancienne base et faisait conclure « rien n'a changé », alors que 62 commits attendaient. Le vrai `develop` se lit par `git ls-remote --heads`.

---

## Scores

| Axe | 31 oct | 6 nov |
|---|---|---|
| Sécurité | 88 | **100** |
| Performance | 95 | **99** |
| Architecture | 88 | **92** |
| Tests | 70 | **90** |
| Dépendances | 82 | **90** |
| Qualité | 80 | **88** |
| **Moyenne** | **84** | **93** |

**124 constats** — 83 corrigés, 5 partiels, 9 toujours valides, 4 caducs, 22 tiennent, 1 non vérifiable. **Aucun constat `Critique` ni `Élevée`** ; il y en avait onze le 9 octobre. Les 14 constats actifs sont 4 `Moyenne` et 10 `Faible`.

Charge restante estimée : **12 à 36 jours**, contre 19 à 58 le 31 octobre.

---

## Réserves de cette passe

- **Aucun agent n'a exécuté de test.** Tout ce qui est dit d'un garde-fou vient de la lecture de son code. Le build vert sur les 19 modules est la seule exécution, et il ne dit pas quelle règle a échoué sur quelle violation plantée — les preuves « vues en échec » du relevé sont des actes d'une session passée, non reproductibles ici.
- Trois tests du correctif `AUD-SEC-17` sont `@DisabledOnOs(WINDOWS)` et n'ont donc pas tourné dans ce build ; les deux qui prouvent le chemin rapide, eux, tournent sur les deux plateformes.
- La chaîne CVE d'`AUD-DEP-27` est **documentaire sur son maillon central** : que quatre CVE dépassent 7.0 repose sur le registre des risques et un message de commit, non revérifiable sans accès NVD. Et la suppression est bornée aux natifs extraits alors que sur une base de longue date l'identifiant se pose sur le JAR : le gate pourrait échouer sur un CVE que le produit ne porte pas. À confronter à un rapport CI réel.
- Les deux plafonds de couverture sont cohérents entre eux mais **à 75 commits derrière la tête** : l'écart de 2,67 points est exact entre les échelles, il ne dit rien de la couverture à `30520bd3`.
- Les 62 commits n'ont pas été lus un à un : pour chaque constat, le code courant a été interrogé puis le diff restreint aux chemins concernés.
