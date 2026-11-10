# Revérification du 7 novembre 2026, au soir — `00ca1378`

Base : `origin/develop` = **`00ca1378`** (PR #618), **5 commits, 38 fichiers** (+1 855 / −331) depuis `f0fe88b7`,
la base de la passe du matin. Méthode inchangée : *une revendication n'est pas une preuve, seul le code tranche*.

> **Rappel de piège, quatrième passe consécutive.** Le clone d'audit ne ramène que `main`. `origin/develop` se lit
> par `git ls-remote --heads origin` et se récupère par
> `git fetch origin '+refs/heads/develop:refs/remotes/origin/develop'`. Sans cela, cette passe aurait conclu
> « rien n'a changé » devant cinq commits.

## Ce qui s'est passé

Une seule pull request, `chore/audit-2026-11-07-findings`, qui fait trois choses dans cet ordre : elle **commite mes
fichiers d'audit** du 6 et du 7 novembre — le dépôt ne porte plus un audit périmé de deux semaines, ce qui était
l'avertissement principal de la passe du matin ; elle **traite les sept constats actifs** ; elle fait **accepter deux
ADR**, `SEC-0164` et `ARCH-0165`, qui sont des décisions et non des correctifs.

Aucun relevé de revendications cette fois : le message de la PR annonce « treat the seven active findings » et laisse
les statuts intacts. J'ai donc vérifié les sept un par un.

## Six clôtures

**`AUD-TST-24` — l'étendue des suppressions CVE est bornée.** `config/dependency-check-suppression-ratchets.properties`
est neuf : une clé, `suppressedDetections=3`, qui ne fait que baisser. `SUPPRESSED_DETECTIONS` tient les trois cibles
éteintes et le gate les compare **dans les deux sens** au fichier XML, puis la taille de la table à la clé, avec un
refus nommé dans chaque direction. Mes deux scénarios d'ouverture sont devenus une fixture : le test fabrique un
fichier dont il assert d'abord que **chaque règle est bien formée**, puis exige trois refus — la règle
`[packageUrl, cpe]` inédite, la règle `[filePath, vulnerabilityName]` sur un CVE de plus, et une entrée de table que
plus aucune règle ne vise. Ce qui passait avec zéro problème échoue maintenant par sa cible.

**`AUD-ARC-20` — le mécanisme est fermé, pas seulement le cas.** `ARCH-0158` balise son énumération et la liste passe
en `debtCounterEnumeratedBy` : les listes comparées à leur ADR passent de 3 à 4 sur 54. Surtout,
`everyEnumerationADecisionRecordWritesIsDelimitedAndDeclared` lit tous les ADR acceptés et refuse une balise ouverte
pour une liste que `KINDS` ne déclare pas énumérée par cet ADR, **et** un paragraphe qui nomme une liste en écrivant
hors balises deux de ses noms. C'est exactement la détection que l'action demandait : ne plus attendre qu'on pense à
se déclarer.

**`AUD-QUA-22` — les quatre citations et leur gate.** Les quatre pointent `ARCH-0162`. Plus aucune mention d'`ADR-0156`
hors des changements archivés et de la ligne « Remplace ». `OpenSpecDecisionRecordCitationTest` dérive du registre les
préfixes, les numéros en vigueur et les numéros archivés, puis refuse dans tout document vivant un ADR archivé cité
sans la marque `(archivé)`, un numéro inexistant, et un ADR cité sous le préfixe d'un autre domaine — en nommant le
bon. **Prédicat réimplémenté indépendamment et rejoué** : 92 fichiers vivants, zéro refus.

**`AUD-DEP-05` — la décision de ne pas signer est écrite.** `SEC-0164` est `Acceptée` : les releases Windows restent
non signées tant qu'aucun certificat sans coût n'est accessible, et la provenance est la somme SHA-256 plus
l'attestation GitHub. Recoupé pièce par pièce : `INSTALLATION.md:217` et `:223-233` portent l'avertissement « éditeur
inconnu » et la commande `gh attestation verify` ; RT-14 est ouvert et accepté avec sa condition de réouverture ;
`ADR-0153` est archivée `Rejetée` ; les trois champs de déclaration sont vides ; `release.yml:150` appelle
`build-release.ps1` sans `-SigningState`, dont le défaut est `none` ; la lane nocturne exerce le chemin par
`validate-m20.ps1 -TestSigning`. **Aucun binaire n'est signé, et je clos quand même** : c'est la seconde branche de
l'action, écrite le 9 octobre. Un constat est corrigé quand son action est faite, pas quand son titre est épuisé.

**`AUD-ARC-18` — la phrase manquante existe.** `ARCH-0165` est `Acceptée` : une technologie sortie du cœur entre dans
`morpheus-cli` ou `morpheus-api`, derrière un port ; chaque module de transport a son gate de familles ; ses listes
confinées sont des **tables de faits**, l'option du compteur de dette étant écartée avec son motif — « faux par
construction, puisque porter ces technologies est le rôle d'un module de transport ». J'accepte l'argument : c'est le
même que celui qui m'a fait retirer le plafond textuel la veille.

**`AUD-TRV-03` — et c'est la clôture la plus discutable de la passe.** La tête de l'action, retirée hier comme
prescription fautive. **Clause 1, désormais tenue sur le fond** : `PullRequestTemplateCoherenceTest` exige la forme
exacte du tableau — `| Fichier | Intention | Mécanisme | Violation plantée |` et son séparateur à quatre colonnes,
hors commentaire HTML — avec cinq refus plantés, dont le tableau caché dans un commentaire. Ce que le build ne peut
pas atteindre, le texte réellement écrit dans une PR, est hors de portée de tout mécanisme du dépôt.
**Clause 2** : les deux plafonds à zéro, assertés par égalité. **Clause 3** : le verdict séparé est en place, la sortie
du module n'a pas eu lieu — mais le motif écrit pour ne pas la faire, que je contestais par les chiffres, **est
maintenant vrai** : `architectureTestsMinimum` passe de 836 à 1 058 pour une mesure de 1 068, soit la marge de dix que
le fichier s'impose, et `.claude/rules/testing.md` inscrit en propre « un plancher oublié ». Sortir les 222 exécutions
des 29 classes `@CoherenceCheck` ferait tomber le compte 212 sous le cliquet.

Le ratio, lui, ne baisse pas : **1 789 assertions `contains` contre 99 règles** (18,0:1), +5 assertions, aucune règle
neuve, `Files.readString` à 480. Mais tenir ce constat ouvert là-dessus réinstallerait le critère que j'ai retiré comme
mauvais. Méthode rejouée sur les deux bases antérieures avant de conclure : `noClasses()` 93 → 99 → 99 et
`Files.readString` 455 → 479 → 480 reproduisent les chiffres des deux passes précédentes à l'unité.

## Le constat qui reste

**`AUD-TRV-02` tient par un seul fil.** Sa première clause est close : les **sept** classes de comportement qu'elle
nommait ont toutes un test propre, les deux dernières arrivant avec cette PR (`QuerySchemaRegistryTest`,
`WorkspacePathBoundaryTest`). Le compte des classes sans test propre passe de 12 (412 lignes) à **9 (248)**, méthode
rejouée à l'identique. Ce qui l'empêche de se fermer : **six clés sur trente-deux au plancher absolu D2**, inchangées,
sur ~1,7 % des lignes, où la fenêtre de qualification est vide parce que la marge exigée est **absolue** et dépasse la
taille de ces modules. Une marge proportionnelle pour les petits modules fermerait ce constat.

**Titre et section `preuve` réécrits**, après l'avoir recommandé quatre passes de suite : le titre annonçait 20,9 points
pour un écart mesuré à **2,6823**, soit une lecture fausse d'un facteur huit. La mesure du 9 octobre n'est pas effacée,
elle devient le point de départ daté dans la section `impact`.

## Deux constats neufs

Ils ne sont pas des dérives de cette PR : ce sont les réserves inscrites dans les deux constats que je ferme, sorties
pour ne pas disparaître avec eux.

**`AUD-TST-25`** — `AWAITING_A_RULE_CEILING` et `UNEXPLAINED_TYPE_BAN_CEILING` valent 0, sont assertés par égalité, et
leur javadoc promet « never raised ». Rien ne tient cette promesse : aucune clé de cliquet, aucune comparaison à un
état antérieur, recherche faite sur tout le dépôt. Les relever dans le même changement passe le build. Depuis que j'ai
retiré le plafond du ratio, ces deux constantes sont la **seule** borne de la part textuelle de l'enforcement.

**`AUD-ARC-21`** — `ARCH-0165`, acceptée du jour, énonce deux tables de faits ; `KINDS` les déclare sans ADR, et le
record `Kind` n'offre aucun `tableOfFactsUnder` : une table de faits ne peut porter son ADR que si cet ADR **énumère**
ses noms. Conséquence : `ARCH-0165` n'est cité par **aucune source** du dépôt — ni les deux gates qu'il fonde, ni une
page de `.claude/`. C'est le défaut qu'`AUD-QUA-22` vient de faire gater pour les documents OpenSpec, à l'endroit où
il n'est pas gaté.

## Contrôles et corrections

- **Build vert sur `00ca1378`** : 19 modules, **5 413 tests, 0 échec, 14 ignorés**, `BUILD SUCCESS` en **10 min 01** de
  temps Maven, avec le verdict séparé `documentation drift: 0` et `architecture violation: 0`. Le module des gates
  déclare **1 078 exécutions**, au-dessus du cliquet relevé à 1 058 : la marge annoncée tient. Les tests neufs de la PR
  sont donc exécutés, pas seulement lus, et les fixtures des deux gates neufs passent.
  Deux précisions d'honnêteté sur ce run. Le premier essai est mort avec la session qui l'avait lancé, et le second a
  demandé **plus d'une heure d'horloge** pour dix minutes de temps Maven : la machine était manifestement en veille ou
  bridée entre mes relevés, `MorpheusRemoteAdversarialClientTest` restant plus de vingt minutes sans écrire une ligne
  avant de passer. Et ce verdict est arrivé **après** la première publication de cette passe, qui disait les six
  clôtures fondées sur la seule lecture du code ; elles le sont désormais aussi sur l'exécution.
- **Erratum sur `AUD-QUA-22`**, à mon débit. J'écrivais qu'`ARCH-0162` « n'a pas de décisions numérotées », donc que la
  citation « décision 3 » n'avait pas d'équivalent littéral. Faux : `ARCH-0162` numérote ses six décisions, et sa
  décision 3 est « `com.morpheus.transport.mcp` ne dépend d'aucun autre paquet MORPHEUS » — exactement ce que la
  citation corrigée affirme. Vérifié aussi sur `f0fe88b7` : la numérotation existait quand je l'ai niée. Ma
  prescription de « réécrire décision 3 » était sans objet.
- **Erratum sur un chiffre du tableau de bord** : la carte annonçait « 84 archivés ». La méthode du dépôt
  (`*-NNNN-*.md`, le `README.md` n'étant pas un ADR) donne **83 archivés** et **80 en vigueur** sur `00ca1378` ; mon
  compte précédent avait inclus un `README.md`.
- **Contrôle neuf dans l'appareil d'audit.** `gen16.py` annonçait le sprint 15 terminé quand `coherence.py` le voyait
  ouvert : les deux constats neufs n'étaient pas inscrits dans la liste de membres de leur sprint, que les générateurs
  lisent. Corrigé, et un **25ᵉ contrôle** refuse désormais un constat absent de la liste de son sprint, ainsi qu'un
  identifiant de liste qui ne correspond à aucun constat. Les 25 contrôles passent.
- **Écart relevé entre le dépôt et ma mesure**, sans conséquence : `.claude/rules/testing.md` écrit que le cliquet
  d'architecture était « resté 222 exécutions sous sa marge de dix ». Ma mesure du matin disait 232 ; les deux disent
  la même chose, le dépôt retirant la marge de dix du total. Simple différence de cadrage.

## Scores

| Axe | 7 nov. (matin) | 7 nov. (soir) |
|---|---|---|
| Sécurité | 100 | **100** |
| Performance | 100 | **100** |
| Dépendances | 95 | **100** |
| Architecture | 94 | **98** |
| Qualité | 95 | **95** |
| Tests | 91 | **95** |
| **Moyenne** | **96** | **98** |

**129 constats : 99 corrigés · 1 partiel · 2 toujours valides · 4 caducs · 22 Info qui tiennent · 1 non vérifiable.**
Les **3 constats actifs** sont 1 `Moyenne` et 2 `Faible`. Charge restante : **3,5 à 10 jours**, contre 8,75 à 25.
**13 des 15 sprints** sont soldés.

## Réserves

- Les deux constats neufs sont des faiblesses de **mécanisme**, pas d'état : les deux plafonds valent zéro et les deux
  listes de `ARCH-0165` sont exactes. Leur coût est dans la facilité du retour en arrière, pas dans le présent.
- `AUD-ARC-20` est clos mais sa détection s'accroche au **nom** de la liste : un ADR qui énumérerait les mêmes classes
  sans écrire `TestClass.CHAMP` reste de la prose aux yeux du gate. Réserve inscrite dans le constat clos.
- La preuve de qualification des deux plafonds de couverture porte toujours le commit `dbd65062`, désormais à **huit
  commits** de la tête. Ce n'est pas une dérive ; c'est à surveiller si l'écart recommence à bouger.
- Aucun scan NVD/OWASP n'a tourné dans cette passe : l'axe Dépendances à 100 ne dit rien d'une CVE, il dit que les
  constats de fraîcheur et de cliquets sont soldés.
