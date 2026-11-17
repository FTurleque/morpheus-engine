# Revérification du 14 novembre 2026 (soir) — `e33c877a`

Base : `origin/main` = **`e33c877a`**. Un seul changement depuis `cb63896a` : la PR **#644**, deux lignes de
`docs/developer/BUILD_AND_TEST.md` et le change OpenSpec qui les porte. Troisième passe de la journée.

Build : **vert**, `BUILD SUCCESS` en **9 min 10 s** sur `e33c877a` — **5 434 tests** (14 ignorés) dont **1 093 gates**, et les deux verdicts d'enforcement à **0**. Les dix-huit totaux par module sont **identiques, un à un**, à ceux de la passe de l'après-midi sur `cb63896a` : c'est la preuve la plus simple que le changement n'a touché aucun comportement. `morpheus-mcp-transport` repasse pour la troisième fois d'affilée depuis son échec unique du matin.

## `AUD-TST-28` — corrigé, et le correctif a vu plus loin que le constat

La phrase de `BUILD_AND_TEST.md:127` énonce maintenant la fenêtre entière. Je l'ai relue **clause par clause** contre
`CoverageQualificationVerifier.windowProblem` :

| Ce que la page dit | Ce que le code fait |
|---|---|
| « refuse un seuil sous le plancher » | `floor < d2Floor - RATIO_PRECISION` |
| « au moins trois fois la dispersion … sous sa plus basse mesure conservée » | `!atTheD2Floor && headroom < HEADROOM_SPREADS * spread`, `HEADROOM_SPREADS = 3L` |
| « plus de neuf fois cette dispersion …, **plancher D2 compris** » | `headroom > MAXIMUM_HEADROOM_SPREADS * spread`, `9L`, **sans** garde `atTheD2Floor` |
| « le plancher n'y est admis que quand aucun seuil plus haut ne tient dans cette bande » | Javadoc `:346` ; la bande vide, le mou du plancher est sous trois dispersions, donc aucune branche ne mord |
| « un module que la preuve ne porte pas reste au plancher D2 » | branche `measured.isEmpty() || measured.contains(null)` |
| « le refus nomme le plus petit seuil admis » | `-- raise it to at least %.3f` |

Chaque clause a son équivalent. La plus utile est « plancher D2 compris » : c'est précisément la subtilité qu'un
lecteur ne devinerait pas, puisque la borne basse, elle, épargne le plancher.

**Et le correctif a réparé un second défaut de la même phrase, que mon constat n'avait pas relevé.** L'ancien texte
définissait la dispersion d'un module par « la dispersion de référence de l'échelle », entière, là où le vérificateur
la multiplie par la part du module dans la population et l'arrondit à l'unité supérieure. La proposition de la PR le
dit sans détour : « Écart trouvé en relisant la phrase du constat ».

C'est une faute de ma part, et elle mérite d'être nommée plutôt que rangée. J'avais cité
`.claude/rules/testing.md:70`, qui énonce la bonne formule. J'avais, le matin même, recalculé les trente-deux bandes
avec `ceil(référence × part du module)`. Je tenais les deux bouts et je n'ai pas comparé la définition du guide à
celle que j'appliquais : mon constat a lu la phrase en cherchant un mot manquant, pas en vérifiant ce qu'elle
affirmait. Vérifié depuis, les cinq autres énoncés de la règle donnent tous la référence proportionnelle
(`GEN-0167:85`, `per-module-coverage-floor.md:88`, `.claude/rules/testing.md:70`,
`coverage-qualification-evidence.md:74`, `coverage-ratchet/SKILL.md:60`) : le défaut était bien confiné à cette page,
et il y est resté le temps que quelqu'un relise la phrase au lieu d'y chercher un mot.

## Recalcul des trente-deux bandes, sans lire le code du dépôt

Puisque cette passe ferme le dernier constat, elle ne pouvait pas se réduire à la relecture d'un paragraphe. J'ai
recalculé, depuis `config/coverage-qualification/` et le fichier de seuils, que chaque seuil en vigueur tient dans sa
bande : 4 résumés conservés, 16 modules portés par les quatre, **32 clés, zéro refus, aucune clé orpheline**.

Deux faits mesurés qui méritent d'être écrits, parce qu'ils ne se voient pas dans un build vert :

- **Plusieurs seuils sont posés exactement sur un bord de bande.** `morpheus-integration-minos.lineCoverageMinimum`
  laisse un mou de **9** pour une borne haute de **9** : il reste **zéro** unité avant refus. Quatre clés sont de même
  exactement à `3×` leur dispersion (`morpheus-integration-minos.branch`, `morpheus-integration-nexus.branch`,
  `morpheus-provider-reference.line`, `morpheus-provider-testkit.line`). Les refus sont des inégalités strictes, donc
  ces seuils passent — c'est le résultat voulu, chaque clé étant posée au millième extrême admissible.
- **Conséquence prévisible, et non documentée** : la prochaine amélioration de couverture des lignes de
  `morpheus-integration-minos` fera mécaniquement sortir sa clé par le haut, et le build refusera jusqu'à ce que le
  seuil monte. Ce n'est pas un défaut — c'est exactement ce que la bande sert à provoquer, et `GEN-0167` l'assume dans
  son raisonnement — mais celui qui améliorera ce module doit savoir que son gain de couverture se paiera d'une hausse
  de seuil dans le même changement. Je le consigne ici plutôt que d'en faire un constat : rien n'est faux.

Une seule clé reste au plancher D2, `morpheus-provider-reference.branchCoverageMinimum` : son haut de bande est sous
le plancher, le cas que la règle admet explicitement.

## Observations sans constat

- `Math.max(1L, ...)` protège la référence d'un module contre une valeur nulle. Avec `total ≥ 1` et
  `population ≥ total`, `ceilDiv(24 × total, population)` vaut toujours au moins 1 : cette garde ne peut pas mordre.
  Le guide ne la mentionne pas, et c'est bien ainsi — documenter un cas inatteignable apprendrait au lecteur une
  règle fausse. Je ne l'ouvre pas non plus comme code mort : elle rend un invariant local évident à la lecture, pour
  une ligne.
- `coverage-qualification-evidence.md:147` garde une question ouverte sur le droit du guide à citer les paramètres de
  la règle (24 lignes, 11 branches, « trois fois »). Elle reste cohérente après #644, qui ajoute « neuf » à la même
  liste de paramètres. C'est une question que la spécification assume, pas une dérive.
- Le change OpenSpec `fix-build-guide-module-floor-band` est encore en vol : ses tâches portent « Archiver le
  changement après la revue, sur demande explicite ». Normal, et à ne pas confondre avec un oubli.

## État — aucun constat ouvert

| | |
|---|---|
| Constats | **133** — 106 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable |
| Ouverts | **aucun** |
| Axes | Architecture, Qualité, Sécurité, Tests, Performance, Dépendances — **100** chacun |
| Sprints | **15 sur 15 soldés** |

C'est la première fois depuis le 9 octobre que l'audit n'a plus rien d'ouvert, et j'écris donc **100** après avoir
passé la journée à écrire 99,8. Le 99,8 n'était pas de la modestie : c'était le refus d'afficher un 100 pendant qu'un
constat restait ouvert. Il n'y en a plus.

**Ce que ce 100 dit, et ce qu'il ne dit pas.** Il dit qu'aucun écart que je sais démontrer ne reste ouvert sur les six
axes. Il ne dit pas que le code est sans défaut : il dit que ma capacité à en trouver, avec les méthodes de cet audit,
est pour l'instant épuisée sur cette base. Les 22 constats Info qui « tiennent » restent des observations vraies — ils
ne comptent pas dans le score parce qu'ils ne demandent pas d'action, pas parce qu'ils ont disparu. Et les trois
dernières passes ont chacune ouvert un constat né de la précédente : la seule prédiction honnête est que la prochaine
vague en produira un autre.

## La question de cadence, reposée pour la dernière fois

Posée le 12 novembre, sans réponse depuis. Trois passes aujourd'hui, dont deux pour un mot et sa correction, et un
constat ouvert ce matin fermé ce soir. Le rythme actuel trouve de vraies choses — mais il les trouve de plus en plus
petites, et le dernier défaut utile que j'aie manqué, c'est un correctif du mainteneur qui l'a vu, pas moi.

À rythme inchangé, je continue de revérifier à chaque vague. Deux autres options : **avant chaque release**, ou **sur
demande**. Sans réponse, je garde le rythme actuel par défaut.
