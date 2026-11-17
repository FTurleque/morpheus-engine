# Revérification du 16 novembre 2026 — `968431af`

Base : `origin/main` = **`968431af`**, trois commits depuis `1b5fe93d` : la PR **#646** propose les changements des
trois constats du 15 novembre, la PR **#647** implémente et archive le plus petit des trois.

Build : **vert**, `BUILD SUCCESS` en **10 min 42 s** — **5 437 tests** (14 ignorés) dont **1 093 gates**, les deux verdicts d'enforcement à **0**. Les **trois tests de plus** qu'au 15 novembre tombent exactement là où #647 en a ajouté : **+2 dans `MORPHEUS Application`** (789 → 791) et **+1 dans `MORPHEUS OpenSpec Provider`** (84 → 85). Les seize autres modules rendent des totaux identiques à l'octet près.

## `AUD-QUA-24` — corrigé, et vérifié en relançant ce qui l'avait produit

La PR #647 ajoute au type lui-même la distinction qui manquait : une énumération `Scope { DOCUMENT, WHOLE_READ }` et
deux formes de phrase.

| Portée | Phrase rendue |
|---|---|
| `DOCUMENT` | `provider ingestion document bytes exceeds budget for <fichier>: <valeur>` |
| `WHOLE_READ` | `provider ingestion <grandeur> **for the whole read** exceeds the budget: <valeur>, **reached while reading** <fichier>` |

Les six grandeurs cumulatives passent `Scope.WHOLE_READ`, la seule taille de document garde `Scope.DOCUMENT`.

Je ne me suis pas contenté de lire le diff. J'ai reconstruit la CLI depuis `968431af` et relancé l'ingestion sur la
copie de `minos-code-intelligence` — le corpus qui avait produit le constat :

```
provider ingestion evidence bytes for the whole read exceeds the budget: 557385 > 524288,
reached while reading openspec/specs/frontieres-de-modules/spec.md
```

Mêmes chiffres, même fichier, attribution inversée. Le fichier de 74 122 octets n'est plus accusé de dépasser
524 288 : il est nommé comme l'endroit où la lecture s'est arrêtée.

**Le correctif va plus loin que le constat**, comme celui du 14 novembre. Je signalais la forme d'une phrase ; la PR a
réparé au passage une seconde confusion que je n'avais pas vue : `requireUtf8Document` passait le compte de lignes
d'**un** document à `requireLines`, la grandeur que la session cumule, donc deux quantités différentes sortaient sous
le même nom « line count ». Le document a maintenant sa propre grandeur, `document line count`, en `Scope.DOCUMENT`.
Et la règle, qui ne vivait nulle part, est désormais écrite dans une spécification OpenSpec
(`openspec/specs/provider-ingestion-budget/spec.md`) et une page de documentation.

## `AUD-ARC-23` — la question que je laissais ouverte est tranchée, et par la mesure

Le 15 novembre j'écrivais refuser de choisir entre « le plafond est trop bas » et « l'accumulation d'évidence est
excessive », faute d'une mesure du volume d'évidence par exigence. Le mainteneur a trouvé le mécanisme et l'a écrit
dans sa proposition. Je l'ai vérifié dans le code, puis mesuré.

**Le mécanisme.** `OpenSpecCurrentSpecificationReader` appelle `evidence(...)` à trois niveaux : la spécification
entière (`:172`), chaque exigence avec ses scénarios (`:272`), puis chaque scénario (`:375`). Et `evidence()` facture
au budget l'extrait **entier** de la plage de lignes — `String.join`, sans troncature (`:421`). Le texte d'un
scénario est donc facturé jusqu'à trois fois. Or `Evidence` n'en conserve que l'empreinte (`excerptHash`) et la plage
de lignes : **le budget facture des octets que rien ne garde.**

**La mesure**, recalculée indépendamment sur `openspec/specs/` de ce dépôt en reproduisant la segmentation du lecteur :

| | octets |
|---|---:|
| fichiers de spécification | 400 666 |
| évidence au niveau spécification | 400 666 |
| évidence au niveau exigence (361) | 366 619 |
| évidence au niveau scénario (933) | 234 033 |
| **total facturé** | **1 001 318** |

Soit un multiplicateur de **2,50×**, et le budget de 512 Kio épuisé après **52 %** de la lecture — ce qui recoupe le
refus observé à 524 777 en cours de lecture.

**Et la conséquence qui déplace la cible du correctif : à facturation unique, les trois corpus passent.** 164, 401 et
503 Kio sous 512 Kio. Le plafond n'était pas le fautif — ce que ma formulation d'origine laissait croire en le nommant
dans son titre, que j'ai corrigé. Je note toutefois que compter une fois ne suffira pas longtemps à
`minos-code-intelligence`, qui serait alors à **96 %** du budget : la marge reste à qualifier sur un corpus réel, et
l'échec reste total, sans troncature déclarée.

Le constat reste **`Élevée` et ouvert** : rien n'est implémenté, les dix tâches du changement
`qualify-the-evidence-budget-on-real-corpora` sont à zéro, et deux des trois projets restent inatteignables.

**Mesure étendue aux trois corpus, et deux de mes chiffres corrigés.** Le multiplicateur est **stable** — il tient à
la structure du lecteur, pas aux données :

| corpus | fichiers `specs/` | facturé | multiplicateur | budget épuisé à |
|---|---:|---:|---:|---:|
| `nexus-context-engine` | 161 287 | 409 407 | **2,54×** | 128 % (passe) |
| `morpheus-engine` | 400 581 | 1 001 063 | **2,50×** | 52 % |
| `minos-code-intelligence` | 524 079 | 1 368 367 | **2,61×** | 38 % |

En rapportant au budget ce qui est réellement **facturé** plutôt que la taille des fichiers, deux affirmations que
j'avais publiées tombent : `nexus` n'est pas « à 40 % du plafond » mais consomme **78 %** du budget, et à facturation
unique `minos` ne serait pas « à 96 % » mais à **99,96 % — 209 octets de marge**. J'avais deux fois rapporté une
grandeur au budget sans la facturer : exactement l'erreur que ce constat reproche au message de refus.
**Conséquence pour l'action : compter une fois est nécessaire mais ne suffit pas.** La borne doit être requalifiée
dans le même changement, sinon le premier scénario ajouté à minos rouvre le constat.

## `AUD-ARC-22` — inchangé dans le code, mais il a un plan

`SUPPORTED_SCHEMA` vaut toujours `"spec-driven"`, la comparaison est toujours une égalité stricte, les trois projets
déclarent toujours `detailed-uml`. Le constat tient à l'identique.

Ce qui a changé : le changement `accept-a-declared-derived-openspec-schema` retient l'option que le constat
recommandait, et il garde le fail-closed au lieu de l'assouplir. C'est la **déclaration** du schéma qui ouvre la
porte — `derives: spec-driven` dans `openspec/schemas/<nom>/schema.yaml` — pas une liste de noms admis : un schéma
muet, ou qui déclare un parent inconnu, reste refusé sous `UNSUPPORTED_PROVIDER_SCHEMA`. Un ADR fixera ce que
« dériver » engage. Dix tâches, aucune faite.

`Élevée` et ouvert : une proposition n'est pas un correctif.

## État

| | |
|---|---|
| Constats | **136** — 107 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable, **2 toujours valides** |
| Ouverts | `AUD-ARC-22` (Élevée) · `AUD-ARC-23` (Élevée) — tous deux portés par un changement OpenSpec en vol |
| Axes | Architecture **80**, Qualité / Sécurité / Tests / Performance / Dépendances **100** — moyenne **96,7** |
| Sprints | 15 soldés sur 16 ; reste **2 à 6 jours** |

## Ce que cette passe confirme sur la méthode

La passe du 15 novembre avait trouvé trois constats en **exécutant** le produit, après vingt passes de lecture qui
n'en avaient vu aucun. Celle-ci confirme la même chose dans l'autre sens : **la clôture d'`AUD-QUA-24` a été prouvée
en relançant l'ingestion**, pas en relisant le diff — et c'est ce qui permet d'affirmer que le message a changé *pour
le corpus qui l'avait produit*, et pas seulement dans un test.

Deuxième confirmation, moins confortable : pour la deuxième fois en trois jours, le correctif du mainteneur a réparé
quelque chose que mon constat n'avait pas vu — ici la confusion du compte de lignes d'un document avec le cumul de la
session. Et pour `AUD-ARC-23`, c'est lui qui a trouvé le triple comptage que j'avais renoncé à chercher. Mon apport
sur ce point s'est limité à vérifier sa trouvaille et à la chiffrer : 2,50×. C'est utile, mais ce n'est pas trouver.
