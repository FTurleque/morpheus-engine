# Revérification du 30 octobre 2026

**Base** — `origin/develop` = `11bb1ccf` (PR #588), soit **65 commits et 346 fichiers** (+11 611 / −1 895) depuis `65a97b06`, la base de la passe du 28 octobre au soir.

**Build** — `./mvnw clean verify` sur les 19 modules : **BUILD SUCCESS en 10 min 55, 4 797 tests, 0 échec, 0 erreur, 13 ignorés**. 105 tests de plus qu'au 28 octobre.

**Méthode** — quatre lots vérifiés en parallèle, chacun avec la même consigne : *une affirmation n'est pas une preuve, seul le code tranche*. Cette consigne vaut aussi pour les `verif_preuve` déjà inscrits dans `findings.json`, qui servent de carte et jamais de preuve. Les deux constats neufs et l'aggravation d'`AUD-ARC-17` ont en outre été revérifiés à la main avant inscription.

---

## Le résultat tient en une phrase

**La vague a fait beaucoup, et la note n'a pas bougé.**

| | |
|---|---|
| Moyenne au 28 octobre (nuit), avant enregistrement du sprint 10 | **84** |
| Après enregistrement des quatre clôtures du sprint 10 | **86** |
| Après la passe du 30 octobre | **84** |

Ce n'est pas un échec, et ce n'est pas du surplace. C'est la rencontre de trois mouvements qui s'annulent :

1. **Quatre clôtures confirmées** (sprint 10) — et confirmées pour la bonne raison : chacune tient par une règle compilée ou une suppression de code, aucune par une réécriture de texte. **Les dix premiers sprints du plan du 9 octobre sont soldés.**
2. **Quatre constats de fond passent de *toujours valide* à *partiel*** — `AUD-ARC-03`, `AUD-ARC-13`, `AUD-TRV-02`, `AUD-PRF-05`. Progrès réel et mesuré, mais un `partiel` pèse son poids entier au score : le barème ne récompense pas la moitié d'un chemin, et c'est voulu.
3. **Deux constats neufs s'ouvrent**, qui coûtent exactement ce que le sprint 10 avait rapporté.

---

## Ce qui a vraiment progressé

**`AUD-ARC-03` — les cycles de paquets de l'application.** Les composantes fortement connexes tombent de **17 à 9**, et une vraie règle ArchUnit les tient. Réserve : c'est une règle **à exceptions nommées** — 21 arêtes tolérées, 2 composantes acceptées. Elle protège l'état actuel autant qu'elle le fige. Ce n'est pas `beFreeOfCycles()`.

**`AUD-ARC-13` — la décision dans le domaine.** `Constraint` gagne 87 lignes et la logique de décision, le paquet `application.constraint` disparaît, et une règle **sans exception nommée** interdit à l'application de lire les constantes de l'enum. Un cas traité jusqu'au bout. Mais le travers de fond ne bouge pas : **0 interface, 0 type scellé** pour 60 records et 25 enums ; le rapport passe de 9,99× à 9,69×, soit 3 %.

**`AUD-TRV-02` — l'écart de couverture.** Les huit chiffres du constat sont **inchangés** et l'écart reste de **20,0195 points**. Ce qui est neuf, c'est qu'il est pour la première fois mesuré et borné : `BorrowedCoverageGateTest` (+649 lignes) nomme **123 classes** dont la couverture est entièrement empruntée à d'autres modules — **89 dans `morpheus-application`** — sous deux cliquets descendants. Rendre un travers visible, nommé et irréversible n'est pas le corriger ; c'est la condition pour le corriger.

**`AUD-PRF-05` — la pagination.** Le va-et-vient applicatif a disparu : page et total en une seule instruction SQL, et une règle force les stores livrés à l'implémenter. Reste le coût serveur : l'ordre est une expression `CASE` **qu'aucun index ne sert**, le prédicat lexical `instr(...)` n'est pas sargable — et `SqliteQueryPlanGuardTest`, le garde-fou censé le signaler, **excuse les trois formes par leur nom**.

---

## Ce qui s'est aggravé

**`AUD-TRV-03` repasse de *partiel* à *toujours valide*.** C'est le constat sur la prépondérance des assertions textuelles, et la vague l'a nourri :

| | 28 oct | 30 oct |
|---|---|---|
| Assertions `contains` | 1 859 | **1 916** (+57) |
| `Files.readString` dans les tests d'architecture | 431 | **449** (+18) |
| Vraies règles `noClasses()` | 44 | **42** (−2) |
| Listes de noms sans genre déclaré | 9 | **11** |

Le rapport règle/texte semble s'améliorer (30,5:1 → 28,6:1) — mais uniquement parce que le dénominateur a grossi. Le dépôt a gagné 57 assertions textuelles et perdu 2 règles compilées. C'est le travers que le constat décrit, et il s'accentue pendant qu'on le corrige ailleurs.

**`AUD-ARC-17` s'aggrave, et plus largement qu'on ne le croyait.** On pensait à une coquille de recopie à deux noms entre la table « Mesure » d'`ARCH-0150` et le gate. En réalité, la même table attribue encore à `product.UpdateDiscoveryService` les familles **« Réseau sortant »** et **« Fils d'exécution »**, alors que la **Décision 1 du même ADR** déclare ces deux familles inutilisées dans toute l'application, et que la classe ne porte plus qu'un `import java.net.URI`. Les lignes citées (`:8-10`, `:44`, `:139`) n'existent plus. La section « Mesure » décrit l'état **d'avant** le correctif d'`AUD-ARC-01` : un document d'architecture contredit sa propre décision, et un lecteur qui s'y fie conclut que l'application fait du HTTP. La vague a édité cet ADR — cinq renvois renumérotés — sans voir la contradiction.

**`AUD-DEP-05` passe de *toujours valide* à *partiel*, et c'est une bonne nouvelle mal déguisée.** Le dépôt a construit tout l'appareil de signature Windows : script, contrat de test, documentation SmartScreen. Mais **aucun artefact n'est signé** — trois champs de déclaration vides, une branche `release` qui se termine inconditionnellement en échec, un workflow qui construit en état `none`. La raison est écrite noir sur blanc : `ADR-0153` est archivé comme **« Rejetée »**, faute de certificat de signature de code. Ce qui manque n'est pas du code. Le constat porte désormais un `blocage_amont`.

---

## Deux constats neufs

**`AUD-DEP-28` — Moyenne.** Le dépôt déclare **deux niveaux de schéma pour la même version 1.2.1** : `released-schema-levels.properties` dit `1.2.1=20`, le POM dit 1.2.1, `SqliteSchemaManager:47` dit 23. Le test censé le voir ne peut structurellement pas l'attraper : sa seule confrontation entre niveau déclaré et schéma supporté est un **plafond** (`level > supported`) ; 20 > 23 est faux, la ligne passe. Le jour où le POM passera à 1.2.2, cette déclaration deviendra une affirmation définitive et fausse sur une version publiée — et le test restera vert. C'est la forme la plus coûteuse d'un garde-fou : il existe, il est vert, et il laisse passer précisément le cas qu'on lui confierait.

**`AUD-QUA-21` — Faible.** Après le renommage de paquet, cinq fichiers nomment encore `com.morpheus.integration.mcp`. Quatre sont des archives OpenSpec qui racontent le renommage — légitimes. Le cinquième est un `design.md` vivant qui affirme au présent que le module « ships the package `com.morpheus.integration.mcp` ». Un document de travail qu'on reprendra, avec un repère faux dans la phrase qui sert de point de départ.

---

## Ce que la vague n'a pas touché

Les onze dérives du sprint 15 ont été recomptées une à une. Dix sont **inchangées au chiffre près** — mêmes lignes, mêmes comptes, parfois décalées de quelques lignes par des insertions voisines. Une seule bouge : `AUD-ARC-18` passe à *partiel*, parce que `ReflectionAndClassLoadingBoundaryArchitectureTest` est le premier gate de confinement technologique à portée produit-entier et couvre donc `morpheus-cli` — mais sur la réflexion, soit **aucune** des trois familles (réseau, fichier, fils d'exécution) que `UpdateManifestFetcher` y a apportées. La lettre du constat est devenue fausse, sa substance tient entièrement.

Un point mérite d'être noté **à l'actif du dépôt** : la vague a renuméroté plusieurs ADR (`0109`→`ARCH-0152`, `0104`→`GEN-0154`, archivage de `0153`) et renommé un paquet dans 34 fichiers. Vérification faite, **aucun garde-fou n'est devenu muet** : aucun test ne citait les numéros déplacés. C'est le genre d'opération qui casse silencieusement des assertions textuelles, et elle ne l'a pas fait.

---

## Contrôles

Les **24 contrôles de cohérence** passent : vocabulaire des statuts et sévérités, axes réels uniquement (aucun axe inventé), unicité des identifiants, `depends_on` existants et acycliques, ordre des sprints respecté par les dépendances, réciprocité `bloque`/`depends_on`, alignement `findings.json` ↔ `sprints-meta.json` dans les deux sens, tout constat actif porte un `reste`, toute vérification porte sa date et sa révision, aucune date future, recalcul du score, cohérence des sprints déclarés terminés.

Trois constats actifs (`AUD-DEP-27`, `AUD-TST-21`, `AUD-PRF-20`) ont été complétés d'un `reste` que le contrôle a signalé manquant.

---

## Scores

| Axe | 28 oct (nuit) | 30 oct |
|---|---|---|
| Performance | 91 | **91** |
| Sécurité | 88 | **88** |
| Architecture | 84 | **84** |
| Qualité | 85 | **84** |
| Dépendances | 86 | **82** |
| Tests | 82 | **78** |
| **Moyenne** | **84** | **84** |

**120 constats** — 69 corrigés, 9 partiels, 15 toujours valides, 4 caducs, 22 tiennent, 1 non vérifiable. **Aucun constat `Critique` ni `Élevée` n'est ouvert** ; il y en avait onze le 9 octobre. Les 24 constats actifs se répartissent en 14 `Moyenne` et 10 `Faible`.

Charge restante estimée : **25,75 à 75 jours**.

---

## Réserves de cette passe

- La mesure d'`AUD-DEP-27` (base NVD neuve) reste non reproductible ici : ni réseau, ni base. Le constat repose, comme à l'origine, sur le relevé du registre de risques.
- La similarité après normalisation d'`AUD-QUA-20` (57 % sur 197 lignes) n'a pas été recalculée ; seules les longueurs (372 et 401) et l'absence de socle commun l'ont été. Les six lignes modifiées étant des imports, les chiffres d'origine tiennent très probablement.
- Les 65 commits n'ont pas été lus un à un. Pour chaque constat, le code courant a été interrogé puis le diff restreint aux chemins concernés. Un changement passant par un chemin non interrogé aurait pu échapper.
- Le classement d'`AUD-ARC-18` en *partiel* relève d'une lecture : si l'on tient que le constat visait les trois familles nommées plutôt que sa formulation générale, il reste *toujours valide*.
