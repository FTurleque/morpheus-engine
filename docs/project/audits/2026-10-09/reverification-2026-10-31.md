# Revérification du 31 octobre 2026

**Base** — `origin/develop` = `de748b38` (PR #599), soit **35 commits et 370 fichiers** (+14 747 / −1 740) depuis `11bb1ccf`, la base de la passe du 30 octobre à midi.

**Build** — `./mvnw clean verify` sur les 19 modules : **BUILD SUCCESS en 10 min 38, 5 209 tests, 0 échec, 0 erreur, 13 ignorés**. 412 tests de plus que la veille.

**Méthode** — trois lots vérifiés en parallèle, même consigne : *une affirmation n'est pas une preuve, seul le code tranche* — et cela vaut pour les `verif_preuve` déjà inscrits, qui servent de carte et jamais de preuve. Les deux agents chargés des mesures chiffrées ont d'abord **rejoué la méthode de la passe précédente sur l'ancienne base** jusqu'à retrouver ses chiffres, avant de l'appliquer à la nouvelle : sans quoi un delta ne veut rien dire. Les trois constats neufs et la clôture de `AUD-ARC-13` ont en outre été revérifiés à la main.

**Un piège évité de peu, à noter pour les passes suivantes** — le refspec du clone d'audit ne ramène que `main`. Un `git fetch origin` laissait donc `origin/develop` à `11bb1ccf` et faisait conclure « rien n'a changé », alors que 35 commits attendaient. Le vrai `develop` a été lu par `git ls-remote --heads`.

---

## Ce qui s'est passé

**Cette vague est une réponse directe à l'audit du 30 octobre.** Elle ne vise pas des constats au hasard : elle reprend un par un les quatre que j'avais classés *partiels* ce midi-là, avec mes réserves pour cahier des charges. Trois constats se ferment, trois autres progressent réellement, et la note descend quand même de 86 à **84** — parce que la vague a fait apparaître trois défauts neufs, dont deux dans l'appareil de mesure lui-même.

| | |
|---|---|
| Moyenne au 30 octobre (midi) | **84** |
| Après les trois clôtures du 31, enregistrées par le dépôt | **86** |
| Après cette passe | **84** |

---

## Les trois clôtures, rouvertes dans le code

J'ai rouvert chacune avant d'accepter. Les trois tiennent, et les trois tiennent pour la bonne raison.

**`AUD-ARC-03` — les cycles de paquets.** Les composantes fortement connexes de `com.morpheus.application` passent de **17 à 2** en granularité fine, et de 16 tranches à **zéro** au premier niveau. La règle est devenue `slices()...beFreeOfCycles()` **sans aucune liste d'exception** : les 21 arêtes tolérées et les deux composantes acceptées ont disparu du fichier. Et le point que je suis allé vérifier en premier — le cycle a-t-il été repoussé dans un paquet non surveillé ? — se tranche par la négative : le domaine est sous sa propre règle `beFreeOfCycles()`, son recompte donne exactement les deux mêmes composantes avant et après, et `morpheus-api`, `morpheus-cli` et `morpheus-mcp` sont à zéro. Deux garde-fous méta empêchent la règle de devenir muette : elle est prouvée sur un paquet-fixture cyclique, et les tranches qu'elle juge sont comparées par égalité stricte aux répertoires réels des sources.

Une réserve de périmètre, que j'inscris plutôt que de la laisser au javadoc : la règle tranche au premier niveau, et le cycle `query.dsl` ↔ `query.saved` **survit**, structurellement hors de sa vue — alors que le constat du 9 octobre le nommait parmi les 17 paquets. Une paire sur dix-sept. Elle est désormais suivie à part (`AUD-ARC-19`).

**`AUD-ARC-13` — la décision dans le domaine.** Et ici, **un erratum de ma part**. Le 30 octobre j'ai classé ce constat *partiel* au motif que « le travers de fond n'est pas entamé : le ratio bouge de 3 % ». Or son action écrit, mot pour mot : « **Aucune refonte globale.** Au cas par cas […] **Mesurer le résultat par la réduction des arêtes de la SCC, pas par le nombre de lignes déplacées.** » J'ai jugé ce constat au ratio que son action me disait expressément de ne pas utiliser. Les arêtes, mesure que l'action avait fixée, passent de 21 à 0.

La clôture tient donc, et sur la mesure que le constat s'était lui-même donnée : deux décisions supplémentaires descendent sur leur type, chacune tenue par une règle sans exception, et le domaine porte sa première famille scellée (0 → 1 interface, et c'est bien un `sealed interface ... permits Allowed, Blocked`). Nuance comptable à dire quand même : sur les +367 lignes du domaine, **123 sont des lignes relocalisées** par le refactoring d'`AUD-ARC-03` ; une partie de l'amélioration du rapport est mécanique. Et les familles à enum restantes restent des records parce que leur forme est persistée en SQLite et sérialisée en JSON canonique — raison réelle, consignée en conséquence négative d'ADR-0156. Le travers de fond est désormais **assumé par décision**, pas résorbé.

**`AUD-PRF-05` — la pagination.** La mieux étayée des trois, et elle répond frontalement à ma réserve. Le `CASE` n'a pas été caché : il est descendu dans trois colonnes `GENERATED ALWAYS AS (...) VIRTUAL` déclarées par une migration **V024**, servies par deux index partiels dont le prédicat `WHERE temporal_state = 'CURRENT'` correspond littéralement au filtre de la requête. Le remplissage des lignes préexistantes est acquis par construction et **prouvé par un test** qui fabrique une base V023, rejoue la migration et compare chaque clé. Surtout — le point décisif — **le garde-fou a cessé d'excuser** : la constante dont le texte admettait « *no index serves the signed order of an identity* » a disparu, les trois formes de page n'ont plus d'excuse, et leur critère de recherche est resserré du filtre vers **le nom de l'index d'ordre**. Le test lance `EXPLAIN QUERY PLAN` par le vrai pilote sur une base migrée et refuse tout parcours de table et tout tri temporaire. L'ancienne excuse a été retirée parce que son énoncé est devenu faux : c'est le bon ordre des opérations.

---

## Les trois constats de fond : du vrai progrès, mesuré

**`AUD-TRV-03` — pour la première fois, le mouvement est favorable et par le bon mécanisme.**

| mesure | 28 oct | 30 oct | **31 oct** |
|---|---|---|---|
| assertions `contains` | 1 859 | 1 917 | **1 705** (−212) |
| règles `noClasses()` | 44 | 42 | **89** (+47) |
| `Files.readString` | 431 | 449 | **449** |
| listes sans genre déclaré | — | 11 | **0** |

Le rapport passe de 45,6:1 à **19,2:1**, et la décomposition tranche le sens : à texte constant, les règles seules donneraient 21,5:1 ; à règles constantes, le texte seul donnerait 40,6:1. **24,1 des 26,4 points de gain viennent de la montée des règles compilées**, pas de la baisse du texte — l'inverse exact du 30 octobre. Les 32 interdits migrés sont de **vraies règles ArchUnit** sur le bytecode, vérifiées à la main, et les trois interdits supprimés comme doublons le sont réellement. Le constat reste *partiel* parce que son critère de sortie ne tient pas : 19 assertions textuelles par règle, et `Files.readString` n'a pas bougé d'une unité.

**Second erratum.** Mon relevé du 30 octobre donnait le rapport règle/texte comme « 30,5:1 → 28,6:1 ». Ses propres comptes donnent 42,3:1 et 45,6:1. Les comptes étaient bons, les deux rapports dérivés étaient faux. La conclusion de cette passe-là tenait, les chiffres cités sont corrigés ici.

**`AUD-TRV-02` — l'écart a enfin bougé, et massivement : 20,0195 → 1,2825 point** (branches : 14,86 → 0,26). Et le mécanisme est établi, ce qui était la vraie question : **c'est la couverture qui est montée, pas la mesure qui a été redéfinie.** Population quasi identique (29 696 → 29 922 lignes), `lineCovered` +5 768, `lineMissed` −5 542, **44 nouveaux fichiers de test** dans `morpheus-application` et 17 dans le domaine — et surtout **aucun `pom.xml`, aucun workflow, aucun script modifié sur les 35 commits**. Les cliquets ont été **relevés**, pas abaissés (0,712 → 0,899), et comme le plafond qualifié est la borne au-dessus de laquelle le cliquet ne peut être posé, le relever **resserre** le gate. Les classes entièrement empruntées tombent de **123 à une**. Les rapports test/source : application 0,55 → **0,86**, domaine 0,53 → **0,99**.

Ce constat est à environ 95 % clos, et **son titre et sa section `preuve` sont désormais faux** — le titre annonce 20,9 points et « `morpheus-application` n'est pas testable sans ses modules voisins ». Ce qui le maintient ouvert est la troisième clause de son action, réellement non faite : remplacer le seuil unique par un seuil de non-régression **par module**. Les cliquets restent deux seuils uniques sur la somme des 16 rapports ; un module qui cesse de se tester reste invisible tant que la somme tient.

**`AUD-DEP-05` — rien n'est signé, et c'est tout ce que le dépôt pouvait faire.** Les trois verrous sont inchangés : champs de déclaration vides, workflow qui construit en état `none`, branche `release` qui échoue par construction. Ce qu'ajoute la vague est **documentaire** : une comparaison de cinq voies gratuites sur sources primaires datées, concluant qu'aucune n'est ouverte compte tenu des contraintes du projet, et un **contrôle d'origine** pour l'utilisateur (somme SHA-256 publiée, puis `gh attestation verify`). C'est une vraie atténuation vérifiable — mais ce n'est pas de l'Authenticode : il faut une commande hors produit, et SmartScreen ne la voit pas. `ADR-0153` reste archivé « Rejetée ». Le `blocage_amont` tient.

Observation à ne pas confondre avec une preuve : les deux tests du contrat de signature qui touchent la déclaration sont **conditionnels** — ils passent à vide tant que rien n'est câblé. Le contrat garde la cohérence de l'état non câblé ; il n'atteste aucune signature.

---

## Trois constats neufs, tous dans l'appareil plutôt que dans le produit

**`AUD-ARC-19` — Faible.** Le cycle `query.dsl` ↔ `query.saved` survit à la clôture d'`AUD-ARC-03`, hors de la vue d'une règle qui tranche au premier niveau. Sept imports, dans les deux sens. Rien n'est dissimulé — le javadoc le dit et ADR-0156 le borne — mais après la clôture, plus aucun constat ne le suivait et aucune règle ne le verra grandir.

**`AUD-TST-22` — Moyenne.** Les deux échelles de couverture ne sont **plus qualifiées au même commit** : le par-module à `d3580dc9`, l'agrégé à `61198c68`, 21 révisions plus loin et sur une population différente. Or la vérification d'unicité du commit est dans une boucle **par échelle** : rien n'impose aux deux la même tête. Conséquence : l'écart entre les deux échelles — que l'action d'`AUD-TRV-02` demande de suivre comme indicateur — n'est plus un écart, mais la somme d'un écart et d'une dérive temporelle. Ni le 20,0195 d'hier ni le 1,2825 d'aujourd'hui n'est une mesure à tête constante. Et cela s'est produit **sans qu'aucune assertion ne proteste**.

**`AUD-TST-23` — Moyenne.** Le mécanisme neuf qui fait tomber de 11 à 0 les listes sans genre déclaré classe 46 listes, dont **7 « compteurs de dette »** — genre défini par sa propre javadoc comme « exact dans les deux sens et **ne faisant que rétrécir, sous la décision qui le dit** ». La seule assertion portant sur les genres vérifie que l'ensemble des listes déclarées égale celui des listes trouvées. **Rien n'asserte le rétrécissement, et rien n'asserte l'ADR** — qui, pour au moins deux des sept, n'existe pas. C'est la contre-épreuve de la doctrine du dépôt : le vocabulaire d'une garantie, sans la garantie.

---

## Trois aggravations, toutes collatérales

**`AUD-ARC-17` s'aggrave pour la deuxième passe consécutive**, et la vague en fournit la démonstration sur pièce. Le déplacement de `LocalWritePermissionHardener` de `application.security` vers `application.files` a été répercuté côté code — par le compilateur — et nulle part côté document, **parce que rien ne les compare**. La divergence entre la table « Mesure » d'`ARCH-0150` et le gate passe de deux écarts à **trois**. L'ADR affirme maintenant trois faits faux : que l'application fait du HTTP, qu'elle crée des fils d'exécution, et qu'une classe d'accès fichier vit dans un paquet qu'elle a quitté. La vague a édité ce fichier — six renvois renumérotés — sans corriger aucun des trois. Ce constat a cessé d'être une anomalie figée : il croît à chaque vague.

**`AUD-DEP-28` et `AUD-DEP-25` s'aggravent par le même effet de bord**, et c'est le correctif d'`AUD-PRF-05` qui en est la cause : la migration V024 porte le schéma de 23 à 24. Le dépôt déclare toujours `1.2.1=20` pendant que 1.2.1 livre désormais le schéma 24 — l'écart passe de 3 à **4 niveaux** —, et quatre migrations sont maintenant entrées sans changement de version, au lieu de trois. Le garde-fou reste vert pour la raison structurelle identifiée hier : sa seule confrontation est un plafond, et `20 > 24` est faux.

**`AUD-QUA-21` n'est pas seulement toujours valide : son scénario d'impact s'est réalisé.** Les trois commits d'archivage de la vague ont archivé treize changes OpenSpec et laissé vivant celui qui porte la phrase fausse — et l'un d'eux a **édité son `tasks.md`**, cochant quatre tâches avec des notes datées du 30 octobre, sans toucher au `design.md`. Le document a été rouvert, relu et annoté, et la phrase fausse est passée sous les yeux de l'édition sans être vue.

---

## À l'actif du dépôt

Trois choses méritent d'être dites, parce qu'elles sont le contraire de ce qu'on redoute à chaque vague.

La vague a **archivé et renuméroté plusieurs ADR** (`ARCH-0147` → `ARCH-0155`, `ARCH-0152` → `ARCH-0156`) et déplacé des classes entre paquets. Vérification faite, **aucun garde-fou n'est devenu muet** : aucun test ne citait les numéros déplacés. En revanche, plusieurs pages de l'audit les citaient encore comme décisions en vigueur — les citations ont été recalées dans les constats concernés.

Elle a traité trois constats de couverture et de tests **sans toucher à l'appareil qui les mesure** : pas un `pom.xml`, pas un workflow, pas un script sur 35 commits. C'est la condition pour que les chiffres d'avant et d'après soient comparables, et elle a été respectée.

Et elle ne prétend pas que la migration vers le bytecode soit une substitution à garantie égale : là où la cible déclare une constante susceptible d'être inlinée, le dépôt a **gardé la moitié textuelle** en l'expliquant. Une règle de dépendance interdit la dépendance compilée, une assertion textuelle interdit la mention ; le dépôt distingue les deux et le dit.

---

## Contrôles

Les **24 contrôles de cohérence** passent : vocabulaire des statuts et sévérités, axes réels uniquement, unicité des identifiants, `depends_on` existants et acycliques, ordre des sprints respecté par les dépendances, réciprocité `bloque`/`depends_on`, alignement `findings.json` ↔ `sprints-meta.json` dans les deux sens, tout constat actif porte un `reste`, toute vérification porte sa date et sa révision, aucune date future, recalcul du score, cohérence des sprints déclarés terminés.

La réconciliation de base a été faite avant tout travail : **aucune perte de fusion**. Les 123 constats sont présents des deux côtés et les six seules divergences étaient exactement ceux que les sessions sœurs avaient retravaillés, chacun avec une révision postérieure à la mienne.

Vérifié et accepté sans en faire un constat : les trois excuses survivantes du garde-fou de plan de requête (`traceability.*`) portent une raison bornée — l'index restreint le tri aux liens d'un seul point d'ancrage — et le test signale une excuse devenue inutile. Ce n'est pas le motif que je reprochais à `AUD-PRF-05`.

---

## Scores

| Axe | 30 oct | 31 oct |
|---|---|---|
| Performance | 91 | **95** |
| Sécurité | 88 | **88** |
| Architecture | 84 | **88** |
| Dépendances | 82 | **82** |
| Qualité | 84 | **80** |
| Tests | 78 | **70** |
| **Moyenne** | **84** | **84** |

**123 constats** — 72 corrigés, 7 partiels, 17 toujours valides, 4 caducs, 22 tiennent, 1 non vérifiable. **Aucun constat `Critique` ni `Élevée` n'est ouvert** ; il y en avait onze le 9 octobre. Les 24 constats actifs se répartissent en 14 `Moyenne` et 10 `Faible`.

**Les onze premiers sprints du plan sont soldés.** Les trois sprints encore ouverts — 12, 13 et 14 — ne gardent plus **qu'un constat chacun**. Charge restante estimée : **19 à 58 jours**, contre 25,75 à 75 hier.

L'axe Tests perd 8 points, et c'est l'enseignement de la passe : les deux constats neufs qui le pénalisent ne portent pas sur le produit mais sur **l'appareil qui le mesure** — un indicateur devenu incalculable, et une promesse de garantie sans garantie. Le produit, lui, va mieux sur tous les axes touchés par la vague.

---

## Réserves de cette passe

- **Aucun agent n'a exécuté de test.** Tout ce qui est dit d'un garde-fou vient de la lecture de son code, pas de son exécution. Le build vert sur les 19 modules est la seule exécution de la passe, et il ne dit pas quelle règle a échoué sur quelle violation plantée.
- Les graphes de paquets ont été reconstruits sur les `import` des sources, alors que la règle ArchUnit juge le bytecode : les comptes de composantes sont une **borne inférieure** sur le nombre d'arêtes. L'absence d'un cycle visible seulement au bytecode ne peut pas être exclue.
- Les écarts de couverture **à tête constante** (4,32 puis 2,67 points) viennent d'artefacts de CI hors du dépôt et n'ont pas pu être revérifiés ; seuls les quatre fichiers de preuve conservés l'ont été, et ils sont cohérents avec ces chiffres. C'est l'objet d'`AUD-TST-22`.
- La mesure d'`AUD-DEP-27` (base NVD neuve) reste non reproductible : ni réseau ni base.
- La méthode de comptage d'`AUD-TRV-03` n'était écrite nulle part et a dû être reconstruite par essais sur la révision précédente, avec un écart résiduel d'une unité sur 1 917. Les deltas sont calculés avec la même méthode des deux côtés. **À inscrire dans le `verif_preuve` pour éviter de la refaire** à chaque passe.
- Les 35 commits n'ont pas été lus un à un : pour chaque constat, le code courant a été interrogé puis le diff restreint aux chemins concernés.
