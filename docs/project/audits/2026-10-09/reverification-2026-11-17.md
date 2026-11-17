# Revérification MORPHEUS — 17 novembre 2026

**Base** `origin/main` = `ad47046c` · 4 commits et 73 fichiers (+2 648 / −3 209) depuis `7d3b012d` · version produit 1.2.2
**PR** #655 `docs(adr): tenir les sections remises aux agents sous la borne du briefing` · #656 archivage de `hold-the-adr-briefing-budget`

## Verdict

**Score global 100. Aucun constat ouvert. Sprint 17 soldé.** 138 constats — 111 corrigé, 4 caduc, 22 tient, 1 non vérifiable ici.
Les six axes sont à 100 : `Qualite` remonte de 96 en fermant le dernier constat ouvert.

Ce chiffre dit qu'**aucun écart que je sache démontrer ne reste ouvert** — pas que le code est sans défaut. Réserve à garder
en tête : chacune des trois dernières passes a ouvert un constat né de la précédente, et les deux derniers sont nés d'un geste
que je n'avais pas à faire — exécuter le produit, puis vérifier la règle qu'un constat invoquait au lieu de sa seule clôture.

## Build

`./mvnw clean verify`, machine du mainteneur, Windows : **BUILD SUCCESS en 16 min 02** — **5 475 tests**, 0 échec, 14 ignorés,
dont **1 100 gates**. Les deux verdicts d'enforcement à **0** (dérive documentaire, violation d'architecture).
Journal : `N:\workspace-dev\build-20261117.log`.

Les **trois tests de plus** qu'à la passe précédente (5 472 / 1 097) sont dans le seul module des gates, et ce sont exactement
les trois méthodes du gate neuf — `AdrBriefingBudgetTest`, relevé nommément dans le journal : `Tests run: 3, Failures: 0`.
Aucun autre total par module ne bouge. Empreinte attendue d'un changement de documentation qui ajoute une classe de test.

## AUD-QUA-26 → CORRIGÉ

Les trois branches de l'action sont faites, et le correctif dépasse le constat.

**Mesure.** 80 ADR en vigueur réénumérés (`*-[0-9][0-9][0-9][0-9]-*.md`), section prise de son titre `##` au `##` suivant,
mesurée après `strip()` — la lecture que le constat avait tranchée. **Zéro dépassement.** `Decision` : maximum **2 000**
(`SEC-0004-local-first-no-llm-core.md`), médiane 1 884, contre 56 dépassements et 10 279 au plus long la veille.
L'ADR pile à 2 000 est admis : la règle dit « 2 000 caractères **au plus** ».

**La règle dit ce qu'elle compte** (`.claude/rules/adr.md:57-60`) : « Une section court de son titre `##` au titre `##`
suivant, sous-sections `###` comprises, mesurée sans les blancs de bord. » C'était le premier point de l'action, sans lequel
aucune mesure n'était vérifiable.

**Un gate du dépôt la lit** : `AdrBriefingBudgetTest` (`@CoherenceCheck`), source relue — `BRIEFING_BUDGET = 2_000`,
registre énuméré comme je l'énumère, et trois tests : la borne sur tout le registre, le découpage (une sous-section `###`
appartient à sa section ; la mesure est identique en LF et en CRLF), le message de refus (il nomme l'ADR, la section et sa
longueur ; une section pile à la borne passe, une à borne+1 échoue).

**Trois ajouts que le constat n'avait pas demandés.** La borne couvre aussi **`Do's and Don'ts`**, que
`archgate review-context` remet également et que mon constat n'avait pas regardée (maximum 1 906, aucun ADR ne manque
l'une des deux). Une section briefée **vide** échoue, ce qui ferme le piège d'un gate dont l'extraction ne trouverait rien
et passerait tout le registre. Et la limite d'`archgate check` est écrite : il ne reconnaît un titre que sur un simple saut
de ligne, donc **sur un arbre CRLF il ne voit aucune section et ne signale aucun dépassement — sous Windows, il n'est pas
une preuve**. Je rapporte ce comportement comme ce que le dépôt déclare de son propre outil ; Archgate vit hors du dépôt et
je ne l'ai pas vérifié.

## Le défaut que j'ai cherché, et qui n'est pas là

Resserrer 56 sections peut tenir la borne en **supprimant** des règles au lieu de déplacer les raisons vers `Context` — ce
que la règle elle-même interdit (« `Decision` garde toutes les règles »). C'est le mode d'échec propre à ce correctif, donc
c'est lui que j'ai mesuré, plutôt que de relire le diff.

| | avant | après | delta |
|---|---|---|---|
| `Decision` (56 ADR modifiés) | 304 630 | 140 801 | **−163 829** |
| `Context` | 137 882 | 287 088 | **+149 206** |
| fichiers entiers | 816 969 | 800 231 | −16 738 (**2 %**) |

Aucune section de titre `##` n'a disparu. Un seul ADR perd plus de 2 000 caractères nets.

**Deux recherches plus fines ont sur-signalé, et je le dis plutôt que de publier leurs chiffres.** Un test sur le vocabulaire
des lignes supprimées a désigné 38 règles « perdues » : la plupart survivent reformulées — ARCH-0066 garde
`getRequestURI().getPath()`, BE-0040 garde `maxDepth`, ARCH-0093 garde ses six mentions de dry-run, DATA-0168 garde
`HEURISTIC` et `PARTIALLY_RESOLVED`. Un test sur les identifiants cités a désigné 10 orphelins : BE-0040 garde `MAX_LINKS`
sous sa forme courte, SEC-0129 garde en entier la règle du pin et de la copie vérifiée exposée au seul `URLClassLoader` —
seul le nom `ProviderPluginActivator` a quitté le texte. Les deux seuls réellement orphelins,
`TraceabilityStore.incoming` et `ExternalReference.ownerId`, n'existent plus dans aucune source : les retirer était une
correction, pas une perte.

**Aucune règle perdue que je sache démontrer** — avec la limite que cela vaut pour ce que des mesures et des recherches
ciblées établissent, pas pour une relecture humaine des 56 sections réécrites.

## Deux sources de production dans une vague étiquetée `docs`

`BoundedStdioServerTransportProvider.java` et `HandlerDeadlineExceededException.java` changent d'une ligne chacun :
`ADR-0106, §4` devient `ADR-0106, point 7`, parce que l'aplatissement a supprimé la numérotation des sous-sections.
C'est la bonne conséquence, et elle a été suivie partout : les quatre renvois vivants à un point numéroté d'ADR résolvent
tous — ADR-0089 point 3, ADR-0018 point 5, ADR-0151 §2, et `ADR-0104 §4` dans un relevé daté dont l'ADR est archivé (la
prose d'un relevé daté ne se réécrit pas, le renvoi est juste pour sa date).

**Observation que je n'ouvre pas en constat** : rien dans le build ne surveille ces renvois. Mais aucun n'est cassé, donc
il n'y a pas d'écart à démontrer — un constat se démontre, il ne s'anticipe pas. Noté dans le champ « reste » d'AUD-QUA-26,
qui est sa place.

## Méthode

Deux gestes ont porté cette passe, et ce sont les mêmes que les trois précédentes recommandent :

- **vérifier la clôture par la mesure qui a ouvert le constat**, relancée sur la nouvelle tête, pas par la relecture du diff ;
- **chercher le mode d'échec propre au correctif** plutôt que les écarts qu'on sait déjà chercher. Ici : la suppression
  déguisée en déplacement. Il n'y était pas, et c'est un résultat de vérification, pas un non-événement.

Et une leçon de prudence : mes deux heuristiques ont produit 48 faux positifs entre elles. Un test sur le vocabulaire ou sur
les identifiants mesure le texte, pas les règles. Les publier aurait fabriqué un constat faux — le troisième de cette
campagne si je l'avais fait.

## Reste côté mainteneur

Inchangé : `nexus-context-engine` et `minos-code-intelligence` ont besoin d'une ligne `derives: spec-driven` dans leur propre
`openspec/schemas/detailed-uml/schema.yaml` pour qu'OpenSpec les ingère sous schéma dérivé.
