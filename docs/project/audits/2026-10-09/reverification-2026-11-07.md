# Revérification du 7 novembre 2026

**Base** — `origin/develop` = `f0fe88b7` (PR #617), soit **69 commits et 283 fichiers** (+16 797 / −1 247) depuis `30520bd3`, la base de la veille.

**Build** — `./mvnw clean verify` sur les 19 modules : **BUILD SUCCESS en 9 min 17, 5 395 tests, 0 échec, 0 erreur, 14 ignorés**, avec le nouveau verdict séparé : `documentation drift: 0`, `architecture violation: 0`.

**Méthode** — quatre lots en parallèle, même consigne : *une affirmation n'est pas une preuve, seul le code tranche*. Les agents chargés des mesures chiffrées ont **rejoué chaque méthode sur la base précédente** jusqu'à en retrouver les chiffres avant de l'appliquer. Les trois constats neufs ont été revérifiés à la main.

---

## Ce qui s'est passé

Le prompt du 6 novembre a produit ce qu'il demandait : **PR #614 a ouvert un changement OpenSpec par constat**, puis #615, #616 et #617 ont implémenté. **Treize des quatorze constats actifs ont été traités**, et un relevé de revendications, `closures-2026-11-06.md`, les détaille avec **trois sections « Ce qui n'est pas fait » successives**, chacune honorée par la section suivante. Le dépôt a aussi tranché sur `AUD-ARC-15`, que j'avais laissé en question ouverte : non seulement il a rouvert un écart accepté deux jours plus tôt, mais il a retiré **95 méthodes `synchronized` sur 96**.

**Dix clôtures confirmées**, trois constats restent actifs du plan, et trois défauts neufs apparaissent.

| | |
|---|---|
| Moyenne au 6 novembre | **93** |
| Moyenne au 7 novembre | **96** |

**127 constats, 7 actifs** — 3 `Moyenne`, 4 `Faible`. **Sécurité et Performance atteignent 100.** Charge restante : **8,75 à 25 jours**, contre 12 à 36 la veille.

---

## Un piège de réconciliation, inversé

Mes fichiers d'audit du 6 novembre sont **non commités** dans l'arbre de travail du mainteneur. La copie *commitée* du dépôt est donc restée à mon état du **31 octobre** — 123 constats, 24 actifs — alors que ma copie de travail en portait 124 avec 14 actifs. C'est le piège de la perte de fusion à l'envers : adopter la copie du dépôt aurait effacé onze clôtures.

J'ai vérifié que l'écart était **exactement** mes onze clôtures du 6, qu'aucune session sœur n'avait touché `findings.json` depuis, et j'ai adopté ma copie. Un agent en a fait les frais : il a lu l'audit commité, n'y a pas trouvé `AUD-DEP-29` — ouvert la veille — et a dû le vérifier d'après son brief. **Cette passe-là est à commiter**, sinon le dépôt porte un audit périmé de deux semaines.

---

## Les dix clôtures

**`AUD-ARC-15`** — 96 → **1** méthode `public synchronized`, et zéro sous `adapter/`. Le travail ne documente pas 96 verrous, il en retire 95 et remplace la garantie par un **confinement au fil vérifié à l'exécution** : `requireOwningThread` refuse réellement tout fil autre que celui qui a ouvert la portée. La table des moniteurs et la règle textuelle que le constat demandait existent, exactes dans les deux sens et sur les deux mesures.

**`AUD-ARC-17`** — le constat qui s'était aggravé **trois passes consécutives** cesse d'exister. Et il faut dire comment, parce que je m'attendais à une esquive : la table « Mesure » d'`ARCH-0150` n'a pas été soustraite, elle a été **requalifiée de prétention présente en relevé daté**, et elle est exacte en tant que telle — les onze longueurs et le total 2 424 ont été revérifiés contre le commit qu'elle date. Ce qui mentait n'était pas la mesure, c'était son emplacement : une section « Mesure » dans un ADR vivant se lit comme un état courant. Les trois écarts sont corrigés (26 entrées de part et d'autre), la comparaison nom par nom existe dans les deux sens, et une règle neuve attrape désormais **la cause mécanique** de l'aggravation : un nom qualifié que l'ADR écrit doit résoudre vers un fichier source.

**`AUD-ARC-19`** — l'action offrait deux voies ; la seconde a été prise **avec les deux moyens au lieu d'un** : règle au deuxième niveau *et* cliquet. Un chiffre à expliquer : la liste porte **15** arêtes là où j'en avais mesuré 13. Les deux de plus préexistaient et échappaient à une mesure qui lisait les imports — ce sont des dépendances nées d'accesseurs de record. Le cliquet est posé **à 15, la valeur mesurée**, pas au-dessus.

**`AUD-QUA-19`** — 7 copies → **0 hors des deux propriétaires**, et la règle est une **vraie règle de bytecode par signature** sur `com.morpheus` entier.

**`AUD-QUA-20`** — la mesure de similarité, refaite avec la méthode du constat et validée par rejeu (197 lignes / 57,4 % reproduits exactement sur la base), passe à **39 lignes, 19,7 %** : −158 lignes, −37,7 points. Les passerelles tombent de 372 à 218 et de 401 à 239. Le cycle de vie vit une fois, dans le bon module, et trois règles de bytecode **sélectives par structure et non par noms** interdisent d'en refaire une.

**`AUD-QUA-21`** — la ligne est corrigée et un contrôle confronte les paquets cités par un change vivant **aux sources réelles du réacteur**, rejoué indépendamment : 117 paquets, 0 refus.

**`AUD-TST-21`** — le contrat existe : 516 lignes, 18 tests, deux formes seules admises, ancrage **dynamique** au POM, table des exemptions exacte dans les deux sens.

**`AUD-TST-22`** et **`AUD-TST-23`** étaient déjà clos la veille.

**`AUD-PRF-20`** — les **deux moitiés** tombent. L'attente a son délai (`tryAcquire` avec le reste de l'échéance, plus aucun `acquire()` nu dans `morpheus-api`), avec un refus nommé et codé déclaré sur les 20 opérations relayées. Et la borne composée est **écrite dans deux guides et dérivée des constantes par un test** : changer une constante casse le test jusqu'à ce que la documentation suive.

**`AUD-DEP-26`** — la règle existe et porte plus loin que demandé : **tous** les workflows, l'identifiant inconnu traité à part, et le registre auto-contradictoire refusé en amont. 12 renvois tenus, dont 5 dans `nightly.yml` que personne n'avait jamais comptés.

**`AUD-DEP-29`** — `jacoco.version` corrigé, et les **neuf versions puis les sept clés de cliquet vérifiées une par une** : aucune divergence. Le mécanisme n'a **pas de liste blanche** de propriétés, donc une page qui copie une version de plus est tenue sans toucher au test.

---

## Les trois constats qui restent, et un arbitrage

**`AUD-TRV-02` est à un cheveu de la clôture.** La troisième clause est exécutée, et bien : population dérivée du POM, complétude refusée avant toute conclusion, clé manquante refusée **sans build**, rapport propre de chaque module jugé contre son seuil, lignes et branches séparément, tous les défauts nommés en une fois avec le remède écrit. Le défaut que ce constat décrivait depuis un mois est désormais **vu, pour 12 des 16 modules**.

Ce qui reste est étroit et mesuré. **Six clés sur trente-deux sont au plancher D2**, non quatre comme le relevé l'annonce — les deux clés de branches de `integration-minos` et `integration-nexus` manquent à son compte. Le plancher n'y protège rien (0,40 contre 0,810 mesuré), mais **la fenêtre y est vide par construction** : la marge exigée est absolue, 72 lignes, et dépasse la taille de ces modules. Ce n'est pas un relâchement, c'est un trou que seule une marge *proportionnelle* fermerait — 1,7 % des lignes. Et surtout, l'action ne se réduisait pas à sa troisième clause : sa **première** nommait sept classes à couvrir en priorité, **deux n'ont toujours aucun test**, et le compte des classes sans test propre n'a pas bougé d'une unité.

**`AUD-TRV-03` — et ici je retire une prescription qui était la mienne et qui était mauvaise.** La tête de l'action demandait de « plafonner explicitement la part textuelle ». Le dépôt la refuse par écrit, avec cet argument : *un ratio se fait baisser en supprimant un contrôle de documentation ou en ajoutant une règle qui ne voit rien de neuf, et la plupart des assertions textuelles visent des fichiers où le texte est la seule prise*.

**Cet argument est juste, et c'est le mien.** Cet audit répète depuis un mois qu'une assertion textuelle et une règle ArchUnit n'enforcent pas la même proposition et qu'aucune ne domine l'autre. Un plafond sur le ratio récompenserait exactement les deux gestes que je reproche par ailleurs. Je retire donc cette clause en tant que prescription — sans quoi le constat resterait ouvert sur un critère **inatteignable par construction**, ce qui ne sert personne.

Ce qui le maintient ouvert n'est plus la proportion mais ce qui reste mesurable : **le texte remonte** (+62 assertions après un recul de 212) et `Files.readString` monte pour la **deuxième passe consécutive** (449 → 455 → 479). Les 62 assertions neuves ont rendu 0,67 des 1,12 point que les six règles neuves avaient gagnés. La clause 1 n'est pas tenue sur le fond — le gabarit demande l'intention, le gate ne vérifie qu'un titre de section, et vider la section en gardant le titre passe. La clause 2 est tenue **sur sa famille**, qui pèse moins de 2 % des assertions. La clause 3 a produit un gain net — la séparation du verdict, publiée par la CI — mais la sortie du module n'a pas eu lieu, et **le motif écrit pour ne pas la faire n'est pas établi** : la règle invoque un cliquet qui descendrait, alors que la marge mesurée est de 232 à 253 pour 207 exécutions portées par les classes concernées.

**`AUD-DEP-05`** est inchangé au mot près pour la quatrième revérification. La décision de renoncer vit toujours dans une cellule de registre, deux en-têtes de `tasks.md` archivés et un champ JSON — aucun ADR, et une recherche sur les 106 fichiers du registre confirme que le seul texte du sujet est l'ADR **rejeté** et archivé.

---

## Trois constats neufs, et un motif qui se répète

**`AUD-TST-24` — Faible.** Le contrat des suppressions Dependency-Check borne la **forme** d'une règle, jamais le **nombre** ni la liste des détections éteintes. Deux scénarios déroulés contre ses 18 assertions passent avec zéro problème : un couple module × CPE inédit bien formé, et n'importe quel CVE supplémentaire sur les natives SQLite. Le titre du constat parent disait « une règle élargie **ou ajoutée** » : la première moitié est tenue, la seconde ne l'est pas.

**`AUD-ARC-20` — Faible.** La comparaison d'une énumération d'ADR à son gate est **sur déclaration** : 3 listes sur 54. Et `ARCH-0158:47` énumère déjà `RUNTIME_ENTRY_POINTS` hors de toute balise, en la déclarant « exacte, elle ne fait que rétrécir » — rien ne la compare. C'est le travers d'`AUD-ARC-17` qui survit dans un autre ADR, et l'endroit où il réapparaîtra.

**`AUD-QUA-22` — Faible.** Quatre citations au présent d'`ADR-0156`, archivé, dans des documents **vivants** : trois dans un change OpenSpec ouvert, une dans une spécification. Trouvées indépendamment par deux vérifications. C'est `AUD-QUA-21` transposé d'un nom de paquet à un identifiant de décision, et le contrôle qui vient de fermer le parent ne peut structurellement pas le voir.

**Le motif qui se répète mérite d'être nommé.** Ces trois constats, plus une faiblesse inscrite dans le `reste` d'`AUD-TRV-03`, pointent tous la même chose : **le mécanisme de cliquet du dépôt est sur déclaration.** Une liste n'est un compteur de dette que si elle a pensé à se déclarer ; une énumération n'est comparée que si elle porte sa racine ; deux plafonds (`AWAITING_A_RULE_CEILING`, `UNEXPLAINED_TYPE_BAN_CEILING`) se présentent comme des cliquets qui ne font que baisser mais ne sont que des constantes Java, sans clé ni comparaison à un état antérieur. Le dispositif est excellent là où il s'applique ; ce qui lui manque est de détecter **ce qui a oublié de s'y inscrire**.

---

## Contrôles

Les **24 contrôles de cohérence** passent. Deux corrections à apporter au relevé du dépôt et à l'audit : la « Requalification du 7 novembre » annonce **quatre** clés au plancher D2, il y en a **six** ; et le titre d'`AUD-TRV-02` reste périmé pour la quatrième passe — il annonce 20,9 points pour un écart mesuré à **2,68**, soit un facteur huit.

Une incohérence mineure dans le relevé, non imputable à un constat : `archgate check` y passe de **27 règles à 24** entre deux sections alors que la seconde **ajoute** un gate. Soit archgate compte autre chose que les tests d'architecture, soit l'un des deux chiffres est faux.

---

## Scores

| Axe | 6 nov | 7 nov |
|---|---|---|
| Sécurité | 100 | **100** |
| Performance | 99 | **100** |
| Qualité | 88 | **95** |
| Dépendances | 90 | **95** |
| Architecture | 92 | **94** |
| Tests | 90 | **91** |
| **Moyenne** | **93** | **96** |

**127 constats** — 93 corrigés, 4 partiels, 3 toujours valides, 4 caducs, 22 tiennent, 1 non vérifiable. **Aucun `Critique` ni `Élevée`** ; il y en avait onze le 9 octobre. Les 7 constats actifs sont 3 `Moyenne` et 4 `Faible`.

---

## Réserves de cette passe

- **Aucun agent n'a exécuté de test.** Le build vert sur les 19 modules est la seule exécution ; il ne dit pas quelle règle a échoué sur quelle violation plantée, et les preuves « vues en échec » du relevé sont des actes d'une session passée.
- La méthode de comptage d'`AUD-TRV-03` reproduit 1 717 à l'exact sur la base précédente, mais **seulement** avec la variante « commentaires retirés, blocs de texte conservés » — qui n'est pas celle que le dépôt emploie pour ses propres comptes (1 702). Les mouvements rapportés sont solides, les niveaux absolus restent indexés sur une méthode dont le code d'origine n'existe pas. **À inscrire comme commande littérale**, pas comme description.
- La population des interdits textuels d'`AUD-TRV-03` a été réimplémentée indépendamment et retrouve exactement les deux membres que la vague a retirés : confirmation forte, pas démonstration.
- Les ratios par module cités pour les six clés au plancher viennent des compteurs **conservés**, mesurés trois commits avant la tête.
- Les 69 commits n'ont pas été lus un à un.
