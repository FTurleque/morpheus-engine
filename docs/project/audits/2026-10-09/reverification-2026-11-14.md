# Revérification du 14 novembre 2026 — `d35e7466`

Base : **`d35e7466`** = `origin/main`, **10 commits et 34 fichiers** (+1 731 / −721) depuis `12cd8874`. L'arbre du
mainteneur est propre et synchronisé ; mes fichiers d'audit du 12 novembre sont commités (PR #638). Méthode inchangée :
*une revendication n'est pas une preuve, seul le code tranche.*

## `AUD-TST-26` se ferme, et le correctif est plus général que le constat

Je demandais deux choses dans cet ordre : poser les deux clés oubliées dans leur fenêtre, puis réfléchir à borner le
mou. Le dépôt a fait l'inverse, et c'est le bon ordre.

**La borne est décidée d'abord.** `GEN-0167`, accepté, remplace `GEN-0166` : le seuil d'un module laisse sous sa plus
basse mesure conservée **au moins trois et au plus neuf** fois sa dispersion retenue. La raison est écrite — « neuf
dispersions, trois fois la bande minimale » — et l'ADR explique pourquoi une borne en points de pourcentage ne dirait
rien d'un module de quelques dizaines de lignes. `CoverageQualificationVerifier.MAXIMUM_HEADROOM_SPREADS = 9L` la
porte, et le refus **nomme le plus petit seuil admis** (`raise it to at least 0.839`).

**Puis les clés suivent.** Vingt-deux clés sur trente-deux sont relevées. **J'ai recalculé les trente-deux** depuis les
compteurs par module des quatre résumés conservés, inchangés : part du module, référence proportionnelle, dispersion
des runs, plus basse mesure, bornes basse et haute. **Les trente-deux sont dans la bande.** La seule restée au plancher
D2 — `provider-reference` en branches — y est parce que sa borne haute vaut 0,300, sous le plancher de 0,35 : la
même fenêtre vide que le 11 novembre, et l'ADR la nomme. Les deux clés de mon constat :
`integration-nexus` en lignes **0,473 → 0,839** pour une bande [0,8382 ; 0,8728], `integration-minos` **0,560 →
0,875** pour [0,8750 ; 0,9050].

Ce qui change à l'usage n'est pas le chiffre, c'est le signal : un seuil abandonné loin sous sa mesure était un
silence, c'est maintenant un échec avec son remède chiffré.

## Un constat neuf, et c'est un mot

`AUD-TST-27`. Dans `CoverageQualificationVerifier.java`, trois lignes au-dessus de la constante qui borne le mou :

> « A module floor leaves at most **three** times its margin under its lowest kept count (ADR-0167): further down, the
> module could lose most of its coverage before its own floor bites. »
>
> `private static final long MAXIMUM_HEADROOM_SPREADS = 9L;`

La justification donnée — un module qui perdrait l'essentiel de sa couverture avant que son seuil ne morde — est
exactement celle du **maximum**, donc le commentaire documente bien la constante du dessous, et il en annonce le
tiers. Les quatre autres énoncés de la même règle disent neuf : l'ADR, la spécification OpenSpec et ses trois
scénarios, `.claude/rules/testing.md`, et le message de refus qui calcule avec la constante. La constante voisine
`HEADROOM_SPREADS = 3L` n'a, elle, aucun commentaire.

Rien n'est faux à l'exécution : les trente-deux seuils sont dans la bande calculée avec 9, ce que mon recalcul
établit. Je l'ouvre quand même, en `Faible` et en effort `S`, parce que cet audit a ouvert `AUD-QUA-23` pour trois
cellules de tableau périmées, et parce que le lecteur de cette phrase est précisément celui qui viendra modifier la
borne. Il pourrait « rétablir l'accord » en baissant la constante.

## Le build : rouge, puis vert, et je raconte les deux

Le premier `./mvnw clean verify` sur cette base a **échoué** dans `morpheus-mcp-transport`, sur deux tests de cycle de
vie qui lancent de vrais processus pairs :

```text
concurrentClosesTearDownOnceAndLeaveNoPeerBehind     recorded launches=[]            20 s, 1282 échantillons
aFailedConnectEndsInATerminalStateThatCannotBeReconnected
                                                      surviving transport threads=4   10 s,  642 échantillons
```

Les deux messages disent la même chose : *l'état observé n'a pas changé d'un seul échantillon*. Rien n'a démarré.

Rejouée seule, la classe passe ses **six tests en 1,979 s**. Et **aucun fichier de `morpheus-mcp-transport` n'a changé**
depuis le dernier build vert : les trois seules sources Java touchées par cette vague sont dans
`morpheus-architecture-tests/.../m21`. Un relancement complet du réacteur est en cours au moment où j'écris.

Je n'impute pas cet échec au code, faute de reproduction — et je ne l'efface pas. Ce que je peux affirmer : ces deux
tests dépendent du démarrage effectif d'un processus Java tiers dans un budget de 10 et 20 secondes, et cette machine
a déjà montré qu'elle se met en veille ou se bride pendant un build (une heure d'horloge pour neuf minutes de temps
Maven, le 10 novembre). **Si l'échec se reproduit sur une machine au repos ou en CI, il devient un constat** ; une
occurrence isolée non reproductible n'en est pas un.

## Contrôles

- **Build** : **rouge au premier passage, vert au second**. Le premier `clean verify` a échoué dans `morpheus-mcp-transport` ; le second, relancé machine au repos, est **vert** : `BUILD SUCCESS` en **9 min 27 s**, **5 428 tests** (14 ignorés), dont **1 093 gates**, `[enforcement verdict] documentation drift: 0` et `architecture violation: 0`. La classe fautive y passe en **1,974 s**.
- **Les trente-deux seuils recalculés** indépendamment, bande basse et bande haute, à partir des résumés conservés.
  C'est cette vérification qui autorise à écrire que la clé restée au plancher y est par arithmétique.
- **Barème rejoué** : il redonne les six axes et la moyenne publiés le 12 novembre.
- **Migration d'ADR vérifiée** : `GEN-0166` est archivé, `GEN-0167` accepté, et **aucune citation périmée** ne subsiste
  hors archive — la page de règles, la skill `coverage-ratchet` et `BUILD_AND_TEST.md` pointent toutes `0167`. C'est le
  mécanisme d'`AUD-QUA-21` et d'`AUD-QUA-22`, tenu sans qu'un constat le demande, pour la deuxième passe de suite.
- **Faux positif du superviseur accepté dans les formes** : la nouvelle entrée `accepted_findings` de
  `.claude/supervisor.config.json` porte sa raison démontrée (`headroom <= lowest <= total`, donc la division ne peut
  pas être par zéro), comme la règle de revue ajoutée le même jour l'exige.
- **Ce que je n'ai pas vérifié** : la fraîcheur des dépendances (`AUD-DEP-17`, Info) ; et le refactor de la PR #639,
  dont j'ai lu la classe extraite sans rejouer ses vingt-sept cas.

## Scores

Inchangés : Sécurité, Performance, Architecture, Qualité, Dépendances à **100**, Tests à **99**, moyenne **99,8**.
Un constat `Faible` en remplace un autre sur le même axe.

**132 constats : 104 corrigés · 22 Info qui tiennent · 4 caducs · 1 non vérifiable · 1 toujours valide.**

## Réserves

- L'échec du premier build est la seule chose de cette passe qui mérite d'être resurveillée. Si la prochaine passe le
  revoit, il faudra le traiter comme un défaut de test — un budget de dix secondes sur le démarrage d'un processus
  tiers n'est pas tenable sur une machine chargée — et non comme un accident.
- Le constat neuf se corrige en un mot. Le gate qui le préviendrait — comparer un nombre écrit en toutes lettres dans
  un commentaire à la constante qu'il documente — n'est pas évidemment généralisable, et je ne le prescris pas.
- Depuis le 11 novembre, chaque passe ferme un constat et en ouvre un plus petit. C'est le signe que l'audit touche le
  fond de ce qu'il sait voir : les défauts restants sont des mots et des commentaires, pas des comportements.
