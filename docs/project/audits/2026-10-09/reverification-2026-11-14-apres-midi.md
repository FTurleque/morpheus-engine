# Revérification du 14 novembre 2026 (après-midi) — `cb63896a`

Base : `origin/main` = **`cb63896a`**, 12 commits et 46 fichiers (+1 175 / −56) depuis `d35e7466`, la base de la passe
du matin. Quatre PR : **#640** ordre des arguments de `claude mcp add`, **#641** le commentaire de la borne du mou,
**#642** génération ordonnée des identités de domaine (`DATA-0168`), **#643** archivage.

Note de méthode : mon clone portait encore une référence `origin/develop`. Elle n'existe plus sur le dépôt —
`git ls-remote --heads origin` ne rend que `main`. C'était un reste local de la suppression du 12 novembre, élagué
avant toute mesure. Une référence de suivi périmée n'est pas un fait du dépôt.

Build : **vert**, `BUILD SUCCESS` en **8 min 25 s** sur `cb63896a` — **5 434 tests** (14 ignorés) dont **1 093 gates**, et les deux verdicts d'enforcement à **0** (documentation drift, architecture violation). Les **six tests de plus** qu'au matin sont tous dans `morpheus-domain` (167 → 173) : ce sont ceux de la génération ordonnée des identités. **`morpheus-mcp-transport` repasse** — l'échec non reproduit du premier passage de ce matin ne s'est pas manifesté une troisième fois.

## `AUD-TST-27` — corrigé

Le constat visait un mot : `CoverageQualificationVerifier.java:46` annonçait « at most **three** times » au-dessus de
`MAXIMUM_HEADROOM_SPREADS = 9L`.

La PR #641 (`fe5dbe14`) écrit « at most **nine** times » à la ligne 48, et reprend aussi le Javadoc de la méthode qui
applique la règle (`:297`), que le constat ne citait pas. La constante n'a pas bougé — c'est l'issue correcte : le
commentaire mentait, pas le code. Recompté sur le dépôt vivant, changements OpenSpec archivés exclus : plus aucune
occurrence de « at most three » ni de « trois fois » appliquée au **maximum** de la bande. Les six énoncés de la règle
disent neuf (`GEN-0167:86` et `:117`, la spec OpenSpec, `per-module-coverage-floor.md:23` et `:84`,
`.claude/rules/testing.md:70`, `.claude/skills/coverage-ratchet/SKILL.md:59`, et le message de refus du vérificateur).

Le changement est passé par un change OpenSpec en règle, archivé par la PR #643. Pour un mot. La discipline tient
jusqu'au plus petit constat de l'audit.

## `DATA-0168` — l'identité de domaine générée dans l'ordre, vérifiée en l'exécutant

`DATA-0168` remplace `DATA-0140` : `rand_a` d'un UUIDv7 porte désormais un compteur monotone par processus
(RFC 9562, section 6.2, méthode 1), tenu par `MonotonicTimestampSequence`. Le défaut d'origine est nommé dans l'ADR :
deux identités créées dans la même milliseconde se comparaient dans un ordre tiré au sort, et une transcription de la
CLI (`analyze-change-json`) a fini par échouer sur la CI.

Une revendication n'est pas une preuve. J'ai copié `DomainIdentity` et `MonotonicTimestampSequence` hors du dépôt, les
ai compilés seuls et exécutés :

```
1. horloge figée, 20 000 tirages : monotone=true ; horodatage emprunté = +9 ms
2. recul de 10 s puis de 24 h       : croissant=true
3. 200 000 identités réelles        : triées=true v7=true distinctes=true tri_restauré=true
4. ordre du texte == ordre de compareTo : true
5. 8 fils x 25 000                  : total=200 000 distinctes=200 000 croissant_par_fil=true
```

Ce que chaque ligne établit :

- **1.** La monotonie stricte tient même quand l'horloge ne bouge pas : au-delà de 4 096 tirages dans la même
  milliseconde, la séquence emprunte l'horodatage suivant plutôt que de répéter une valeur. Le coût est borné et
  mesuré : 20 000 identités d'affilée poussent l'horodatage **9 ms** dans le futur. L'ADR interdit déjà de lire cet
  horodatage comme un `createdAt`, ce qui est exactement la lecture que cet emprunt rendrait fausse.
- **2.** Un recul d'horloge — NTP, veille, machine virtuelle — ne produit pas d'identité décroissante : la séquence
  continue sur le dernier horodatage rendu.
- **3.** Sur 200 000 identités réelles, l'ordre de génération est l'ordre de `compareTo`, sans collision, et les
  invariants du constructeur compact (version 7, variante 2) tiennent sur toutes. Le test du mélange puis du tri est
  celui qui compte : il prouve que l'ordre est **porté par la valeur**, pas par l'ordre d'insertion.
- **4.** L'ordre lexicographique de la forme textuelle coïncide avec `compareTo`. C'est la propriété qui transporte la
  garantie jusqu'aux stores : un `ORDER BY` sur l'identité persistée rend le même ordre que le domaine. Elle tient
  parce que le bit de signe de la partie haute reste à zéro jusqu'à l'an 6429 environ ; `UUID.compareTo` compare des
  `long` **signés**, et c'est le genre de détail qui se retourne silencieusement si quelqu'un déplace l'horodatage.
- **5.** `next` est `synchronized` et la séquence est un singleton de processus : sous huit fils, aucune collision et
  chaque fil voit sa propre sous-suite croissante.

La limite est écrite par l'ADR elle-même — aucun ordre garanti entre deux processus — et elle ne touche pas le défaut
d'origine, qui vivait dans un seul processus. Les Don'ts de l'ADR couvrent les mauvaises lectures (`createdAt`, ordre
inter-processus, remplir `rand_a` au hasard). **Pas de constat.**

## `AUD-TST-28` — ouvert (Faible, S, sprint 15)

`docs/developer/BUILD_AND_TEST.md:127` décrit la règle que tient
`CoverageQualificationEvidenceTest#everyModuleFloorStaysInsideItsQualifiedWindow` ainsi : « refuse un seuil sous le
plancher, et, au-dessus, un seuil qui ne garde pas trois fois la dispersion du module », puis conclut « Poser ou
relever un seuil de module est donc une décision dans cette fenêtre ». La borne haute n'y est pas : le mot « neuf »
est absent de toute la section « Le seuil propre à chaque module » (lignes 120-132).

C'est la moitié de la règle — celle qui existait **avant** `GEN-0167`. La borne haute est précisément ce qui a fait
monter 22 clés sur 32 le matin même. Les cinq autres énoncés la portent ; `coverage-qualification-evidence.md:78` ne
cite que la borne basse mais renvoie explicitement à `per-module-coverage-floor.md` pour « la règle et ses refus »,
donc cette page-là est complète par délégation. Le guide de build ne l'est pas, et `rules/meta.md` le nomme parmi les
destinations à mettre à jour quand un seuil bouge : c'est une page qu'on lit au moment précis où la borne manquante
compte.

Rien n'est faux à l'exécution, et le refus du gate nomme le plus petit seuil admis, donc le lecteur est rattrapé. Coût
réel : un cycle perdu. Aucun gate ne compare cette prose à la règle — `RepositoryDocumentationCoherenceTest` compare
des valeurs de ratchet, pas des bornes écrites en toutes lettres. La relecture est le seul filet, et elle a cédé ici.

## Vérifié sans constat

**Le correctif `claude mcp add` (#640) est un vrai correctif, avec sa reproduction.** `--env` est variadique dans les
vrais CLI : un nom de serveur écrit après lui était lu comme une variable de plus. Le faux CLI du vérificateur a
d'abord été **durci pour refuser** cet ordre, puis l'appel a été corrigé — test de reproduction avant correctif, comme
la règle du dépôt l'exige. Point notable : l'assertion précédente, `'mcp add --scope user .* morpheus -- '`,
**épinglait l'ordre cassé**. Un gate peut tenir un défaut aussi fermement qu'une propriété ; celui-ci l'a fait, et
c'est la lecture de la documentation amont, pas le build, qui l'a découvert. Les trois clients CLI placent maintenant
`morpheus` juste après `mcp add`, et seul `claude` porte `--scope user` : la correction a bien été généralisée aux
frères.

**Archivage de `DATA-0140`.** Bandeau qui nomme `DATA-0168`, statut « Remplacée par ADR-0168 », retrait de l'index et
d'arc42, ligne de `DATA-0168` présente dans les deux. Recompté : **80 ADR en vigueur, 86 archivés**, aucun doublon de
numéro entre registre et archive, plus haut numéro attribué `0168`. La chaîne `0009/0015/0023 → 0140 → 0168` fait deux
sauts : `AdrIndexCoherenceTest` l'admet (le successeur nommé existe), la règle du dépôt ne demande pas d'aplatir, et
le bandeau de `0140` mène en un clic à la décision en vigueur. Observation, pas constat.

## État

| | |
|---|---|
| Constats | **133** — 105 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable, **1 toujours valide** |
| Axes | Tests **99**, Architecture / Qualité / Sécurité / Performance / Dépendances **100** — moyenne **99,8** |
| Sprints | 14 sur 15 soldés ; reste **0,25 à 1 jour** |
| Ouvert | `AUD-TST-28` seul |

Le 99,8 est écrit plutôt que le 100 arrondi : un 100 affiché pendant qu'un constat reste ouvert est exactement le genre
de chiffre que cet audit passe son temps à démentir.

## Question ouverte, posée depuis le 12 novembre

Faut-il continuer à revérifier **à chaque vague**, ou passer à un rythme plus lâche — par exemple avant chaque release ?
Deux passes dans la même journée pour fermer un mot et en ouvrir un autre est le signal que la question mérite une
réponse.
