# Revérification du 11 novembre 2026 — `555aa2e0`

Base : **`555aa2e0`**, qui n'est pas `origin/develop`. Le `develop` local du mainteneur est **six pull requests en
avance** (#625 à #630) et rien n'est poussé : `origin/develop` reste à `421a9429`. L'audit porte donc sur un arbre qui
n'existe que sur sa machine — propre, commité, et vérifié là où il est.

> **Deux pièges de session, l'un neuf.** Le clone d'audit ne ramène que `main` ; `origin/develop` se récupère
> explicitement (sixième passe consécutive où je le note). Le piège neuf : la base à auditer **n'était pas sur le
> remote**. J'ai lancé le build sur sa machine et lu `git rev-parse HEAD` dans la même commande — c'est ce qui a
> montré `555aa2e0` au lieu de `421a9429`. Sans ce réflexe, cette passe aurait conclu « une PR de retard, rien de
> neuf » devant quatre constats traités.
>
> **Un piège de mesure, aussi.** Les fichiers de sa machine sont en CRLF : mon premier comptage des clés au plancher
> D2, avec une expression ancrée en fin de ligne, en a trouvé **zéro** au lieu d'une. Recompté en dépouillant le
> retour chariot. Toute mesure de cette passe est faite sur les fichiers dépouillés.

## Ce qui s'est passé

Les deux prompts du 10 novembre ont été exécutés : **#626** ouvre un changement OpenSpec par constat, puis **#627**,
**#628**, **#629** et **#630** implémentent, avec deux ADR — `GEN-0166` accepté, qui remplace `GEN-0163` archivé, et le
registre, l'index arc42 et quatre spécifications `docs/developer/specifications/` mis à jour avec. **#625** commite ma
revérification du 10 novembre. **#624**, poussée elle, avait aligné la documentation la veille.

Je note au passage ce que je n'ai pas eu à relever : `GEN-0163` est archivé et **aucune citation périmée ne subsiste**
— `.claude/rules/testing.md`, la skill `coverage-ratchet` et `docs/developer/BUILD_AND_TEST.md` pointent tous
`GEN-0166`, et l'index donne les deux statuts justes. C'est le mécanisme exact d'`AUD-QUA-21` et d'`AUD-QUA-22`, et il
a été tenu sans qu'un constat le demande.

## Les quatre clôtures

**`AUD-TRV-02` — le dernier constat `Moyenne` de la mission.** `GEN-0166` décision 5 écrit la marge réclamée : la
référence d'un seuil de module est celle de l'échelle **multipliée par la part du module** dans la population de la
grandeur, arrondie à l'unité supérieure, lignes et branches séparément. **J'ai recalculé les trente-deux seuils** à
partir des compteurs par module des quatre résumés conservés de `dbd65062` — part, référence proportionnelle,
dispersion des runs, plus basse mesure, borne admissible : les trente-deux valeurs du fichier sont **exactement** celles
que la règle permet. Les clés au plancher absolu D2 passent de **six à une** (`testkit` 0,761 / 0,593,
`provider-reference` lignes 0,842, `minos` branches 0,661, `nexus` branches 0,606). La dernière,
`morpheus-provider-reference.branchCoverageMinimum`, a une fenêtre **vide par arithmétique** : 10 branches dans le
module, part 0,00099, référence 1 branche, plus basse mesure 0,600, borne admissible **0,300** — sous le plancher de
0,35. L'ADR l'écrit nommément. Le constat énonçait son propre critère de clôture — « une marge proportionnelle pour les
petits modules fermerait ce constat » — et il est rempli.

**`AUD-TST-25`.** `config/enforcement-ceiling-ratchets.properties` : une clé par plafond, nommée `TestClass.CHAMP`,
l'en-tête qui dit que ces clés ne font que baisser et qu'un relèvement se justifie en pull request. L'égalité est
assertée **dans les deux sens** avec le remède nommé dans le refus, et une clé qu'aucun plafond ne lit est refusée
aussi — ce que je n'avais pas demandé. Les deux plafonds valent toujours 0.

**`AUD-ARC-21`.** La fabrique `tableOfFactsUnder` existe, symétrique de `debtCounterUnder` ; les deux
`CONFINED_FAMILIES` sont déclarées sous `ARCH-0165`, et trois autres tables ont gagné leur ADR au passage. La règle
inverse — `everyTableOfFactsAnAcceptedRecordNamesDeclaresThatRecord` — ferme la porte par laquelle le défaut était
entré : un ADR accepté qui écrit en span de code le `TestClass.CHAMP` d'une table est l'ADR que cette table déclare,
et une table nommée par deux ADR acceptés est refusée. `ARCH-0165` est enfin cité par du code.

**`AUD-QUA-23`.** La documentation d'abord (#624), le gate ensuite (#629) — le seul ordre qui laisse le build vert, et
c'est celui qui a été suivi. Le gate neuf lit **toute page Markdown suivie par Git sous `docs/`**, hors des quatre
répertoires de relevés datés qu'il déclare d'après `rules/documentation.md`, avec une garde qui refuse un balayage qui
ne lirait rien et des fixtures qui plantent les deux sens, transcription en bloc de code comprise. Et le dépôt a fait
mieux que ma prescription : je demandais de corriger trois valeurs périmées, il a **retiré les valeurs**. Une cellule
qui ne porte plus de nombre ne peut plus dériver — c'est la différence entre remettre un compteur à zéro et retirer le
compteur.

## Un constat neuf : `AUD-TST-26`

Ce n'est pas une dérive de ces PR, c'est un **reste de portée**, et la portée était écrite d'avance. La proposition du
changement sur la marge dit : « les clés **au plancher** dont la fenêtre devient non vide sont posées au plus haut
millième de leur fenêtre ». Les clés déjà au-dessus du plancher n'ont donc pas bougé, alors que la marge
proportionnelle s'applique aux trente-deux. Deux d'entre elles restent très loin sous leur fenêtre neuve :
`morpheus-integration-nexus.lineCoverageMinimum` vaut **0,473** pour une borne admissible de **0,873**, et
`morpheus-integration-minos.lineCoverageMinimum` vaut **0,560** pour **0,905** — **40,0 et 34,5 points de mou**.

Ce n'est pas une violation : `GEN-0166` borne un seuil par le haut et n'oblige nulle part à le poser au maximum.
C'est ce qui reste de la **substance** d'`AUD-TRV-02` — « un module peut cesser de se tester sans qu'aucun gate ne le
dise » — concentré sur deux clés au lieu de six. Les deux modules sont petits, 173 et 200 lignes, donc `Faible` ; mais
ce sont les deux adaptateurs qui parlent à MINOS et NEXUS, c'est-à-dire à du code tiers lancé en processus.

## Contrôles

- **Build** : `./mvnw clean verify` sur `555aa2e0`, machine du mainteneur — **vert** — 19 modules, **5 425 tests, 0 échec, 14 ignorés**, `BUILD SUCCESS` en **8 min 37**, verdict séparé `documentation drift: 0` / `architecture violation: 0`. Le module des gates déclare **1 090 exécutions** contre 1 078 le 10 novembre : les douze de plus sont les fixtures des trois gates neufs. C'est la **seule exécution** de ces six pull requests, que la CI n'a pas encore vues. Les quatre clôtures reposaient sur la lecture du code et sur mon recalcul des trente-deux seuils ; l'exécution les confirme — et les douze exécutions de plus dans le module des gates sont les fixtures que ces trois changements ont livrées avec leurs règles, ce qui est la preuve que les violations plantées sont jouées et non seulement racontées
- **Barème rejoué** avant toute modification : reconstruit depuis `findings.json`, il redonne exactement les six axes
  et la moyenne 98 publiés le 10 novembre.
- **La formule de la marge rejouée sur les cinq clés que la PR vient de poser** : elle les reproduit au millième. C'est
  ce qui me permet d'affirmer que les deux clés laissées en arrière le sont, et de combien.
- **Charge restante** recalculée depuis les efforts : 0,25 à 1 jour.
- **Ce que je n'ai pas vérifié** : la fraîcheur des dépendances (`AUD-DEP-17`, Info) — pas d'accès à Maven Central
  depuis le conteneur. Et je n'ai pas relu les quatre spécifications `docs/developer/specifications/` ajoutées par ces
  PR : elles décrivent des gates que j'ai lus dans leur code, ce qui est la source qui tranche.

## Scores

| Axe | 10 nov. | 11 nov. |
|---|---|---|
| Sécurité | 100 | **100** |
| Performance | 100 | **100** |
| Architecture | 98 | **100** |
| Qualité | 94 | **100** |
| Dépendances | 99 | **100** |
| Tests | 95 | **99** |
| **Moyenne** | **98** | **99,8** |

**131 constats : 103 corrigés · 22 Info qui tiennent · 4 caducs · 1 non vérifiable · 1 toujours valide.** Un seul
constat actif, `Faible`, effort S. Aucun `Critique` ni `Élevée` depuis le 28 octobre ; **aucun `Moyenne`** depuis
aujourd'hui. Quatorze des quinze sprints sont soldés.

La moyenne arrondit à 100 au barème. Je l'écris **99,8**, parce qu'un 100 affiché pendant qu'un constat reste ouvert
est exactement le genre de chiffre que cet audit passe son temps à démentir.

## Réserves

- **Rien de tout cela n'est poussé.** Six PR vivent sur une seule machine, et la CI ne les a pas vues sur `develop` ;
  mon build local est la seule exécution de cet arbre. Pousser est la première chose à faire.
- Le constat neuf se ferme en posant deux clés, soit un quart de journée. Mais son action demande aussi de décider si
  un seuil abandonné loin sous sa fenêtre doit devenir un échec nommé — c'est une décision pour `GEN-0166`, pas un
  réglage.
- Un audit à un constat `Faible` n'est pas un audit terminé : c'est un audit dont les constats restants sont tous des
  **mécanismes**, pas des défauts d'état. Les quatre dernières passes l'ont montré — chaque clôture a ouvert la
  question du cran suivant, et c'est sain.
