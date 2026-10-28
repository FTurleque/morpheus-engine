# Audit MORPHEUS — synthèse exécutive

**Dépôt** `FTurleque/morpheus-engine` · **révision** `20cf2e0` (`main` == `develop`, 0 commit d'écart) · **version produit** 1.2.1 · **date** 9 octobre 2026.

**Périmètre** — 1 753 fichiers suivis, 1 185 `.java` (~152 000 lignes, dont 65 181 en `src/main` et 86 803 en `src/test`), 378 `.md` (~72 700 lignes), 18 modules Maven, 109 ADR, schéma SQLite en version 20. Les six axes ont été couverts avec lecture approfondie du cœur : `api`, `cli`, `mcp`, `store-sqlite`, `application`, `mcp-transport`, `provider-openspec`, `domain`, `provider-sdk`, `architecture-tests`. Le dépôt tient sous le seuil des 2 000 fichiers : aucun module n'a été écarté *a priori*.

**96 constats** : 0 Critique, 11 Élevée, 40 Moyenne, 21 Faible, 24 Info. Les constats sont dans `findings.md` (lisible) et `findings.json` (exploitable). Six ont été produits par axe puis consolidés ; sept sont des fusions inter-axes (`AUD-TRV-*`). La consolidation a été relue par un agent qui n'avait produit aucun constat : **un faux positif a été supprimé, quatre sévérités ont bougé, quatre constats ont été fusionnés, et un constat majeur a été ajouté** — le détail des arbitrages est dans chaque constat concerné.

---

## État au 28 octobre 2026 — plus aucun constat `Élevée` ouvert

Branche `fix/audit-arc-01-prf-04`, sur `aace25fc`, après un `./mvnw clean verify` vert sur les 19 modules (4 692 tests, 0 échec, 13 ignorés). Les deux derniers
constats `Élevée` sont corrigés, et avec eux le test de garde qui ne gardait rien.

**`AUD-ARC-01` est corrigé.** La découverte de mise à jour lit son manifeste par un port de l'application,
`UpdateManifestSource` ; son transport `java.net.http` vit dans `morpheus-cli`, seul appelant. Le gate technologique
refuse le réseau sortant et les fils d'exécution dans tout `com.morpheus.application`, sans exception nommée. La voie
« cœur pur » du constat d'origine reste écartée par ADR-0150 : c'est un choix écrit et gaté, que l'action demandait de
trancher, pas une correction qui manque. DT-18 est soldée.

**`AUD-PRF-04` est corrigé.** L'audit des mutations de cycle de vie et les archives de synchronisation sont lus par
pages bornées au store, comme l'audit des policies, et aucun port de journal n'offre plus de lecture entière. Les
archives ont une clé d'ordre stockée (migration V023) ; le tri en mémoire a disparu. ADR-0131 est consolidé dans
ADR-0151, puisque la clôture par une règle d'appel qu'il retenait pour ces deux journaux est remplacée.

**`AUD-TST-20` est corrigé** (sprint 15) : le test de garde compare la liste exacte des méthodes que la règle juge,
et une fixture la casse dans les deux sens à chaque exécution.

**Plan du 9 octobre : 63 soldés sur 73, 10 restants.** Les sprints 1 à 9 sont terminés. **113 constats :
65 corrigés · 5 partiels · 16 toujours valides · 4 caducs · 22 Info qui tiennent · 1 non vérifiable.** Aucun constat
`Élevée` n'est ouvert.

---

## État au 28 octobre 2026 — contre-vérification : le sprint 9 terminé

Une passe indépendante, sur `5c3ce4dd` (PR #577), avec un build de contrôle : `./mvnw clean verify` vert sur les
19 modules, 4 669 tests, 0 échec. Elle couvre le sprint 9, que les notes précédentes laissaient hors de leur
périmètre, et relit leurs sept verdicts. Détail dans **`contre-verification-2026-10-28.md`**. Le registre en est
reconstruit sur `fbf58cac`, chaque constat repris de la contre-vérification relu dans le code.

**Le sprint 9 est terminé** : `AUD-QUA-02`, `AUD-QUA-06`, `AUD-QUA-07`, `AUD-QUA-08`, `AUD-QUA-09` et `AUD-ARC-10`
sont corrigés. Chaque helper du socle a un propriétaire, et les règles qui refusent une copie cherchent une forme —
une signature, un appel compilé, un fichier de test qui remonte depuis le répertoire courant — et non un nom.

**`AUD-ARC-16` passe à partiel, pas à caduc.** La contre-vérification le déclare caduc : la garde d'exactitude que
l'action demandait en premier existait déjà le 26 octobre (`SqliteStorePackagesArchitectureTest:81`, `:109`). C'est
exact, mais l'action demandait aussi, dans ses deux voies, d'écrire que l'élargissement de visibilité est le prix du
découpage et que la liste des types publics de `runtime/` ne peut que rétrécir. Aucun ADR ne le dit.

**Trois constats naissent**, au sprint 15 : `AUD-SEC-18` (deux frontières d'espace de travail aux garanties
opposées, sans décision écrite), `AUD-QUA-19` (sept copies de `safeMessage` hors du CLI, que la règle du sprint 9
ne voit pas) et `AUD-QUA-20` (les passerelles MINOS et NEXUS portent l'essentiel de la duplication qui reste entre
les deux modules d'intégration).

**Quatre citations corrigées** : `AUD-TST-10` (le contrôle par palier de `validate-r2` s'ajoute à `tests >= 1`, et
six paliers ne sont le départ d'aucun test d'upgrade), `AUD-PRF-14`, `AUD-TST-19` et `AUD-TST-09` (32 champs de
plafond périmés et non lus dans les preuves conservées).

**Ce que la reconstruction n'a pas repris de la contre-vérification** :
- la correction de citation d'`AUD-PRF-09` est sans objet : le registre cite le test par son nom ;
- l'intitulé d'`AUD-TST-09` n'est plus littéralement vrai : l'écart de branche agrégé est de 0,4195 point, au-delà
  des 0,41 de l'intitulé ;
- « 78 des 89 blocs » d'`AUD-QUA-20` n'est pas reproduit ; la mesure inscrite est la sienne, 151 des 195 fenêtres
  de douze lignes communes aux deux modules ;
- quelques comptes d'avant ne sont pas reproduits à l'identique (342 appels de `RepositoryRoot`, 352 relus ;
  11 fichiers de `PublicPayloads`, 10 relus) : aucun ne change un statut.

**Plan du 9 octobre : 61 soldés sur 73, 12 restants.** Les sprints 1 à 5 et 7 à 9 sont terminés. **113 constats :
62 corrigés · 7 partiels · 17 toujours valides · 4 caducs · 22 Info qui tiennent · 1 non vérifiable.** Il reste deux
constats `Élevée` : `AUD-ARC-01` et `AUD-PRF-04`, tous deux partiels.

| Axe | 26 oct. | **28 oct.** | |
|---|---|---|---|
| Sécurité | 96 | **92** | −4 |
| Qualité | 70 | **88** | **+18** |
| Dépendances / exploitation | 83 | **87** | +4 |
| Tests | 70 | **83** | +13 |
| Performance | 77 | **78** | +1 |
| Architecture | 55 | **59** | +4 |
| **Moyenne** | **75** | **81** | **+6** |

Notes de la contre-vérification, qui comptait `AUD-ARC-16` comme caduc ; le registre le garde ouvert, sans que la
note d'architecture soit recalculée.

---

## État au 28 octobre 2026 — les sprints 1, 5, 7 et 8 terminés

Sept PR fusionnées (#570 à #576) sur les sept constats encore ouverts des sprints 1, 5, 7 et 8, statuts relus
dans le code de `develop` à `6c6a97f6` ; puis la branche `fix/finish-sprints-1-5` sur les deux qui restaient,
`AUD-DEP-07` et `AUD-PRF-09`. Détail dans **`reverification-2026-10-28.md`** et
**`reverification-2026-10-28-sprints-1-5.md`**.

**Cinq constats se ferment** : `AUD-PRF-14` (une seule passe mesure et décode, chaque répertoire listé une fois),
`AUD-TRV-07` (les huit validateurs `.ps1` ont une lane nocturne Windows, et une règle refuse un validateur qu'aucune
voie ne lance), `AUD-TST-09` (plafonds de couverture requalifiés sur les deux plateformes, marge exécutée par un
test), `AUD-TST-10` (upgrade testé depuis des bases écrites par v1.0.0, v1.1.0 et v1.2.1) et `AUD-TST-19` (les trois
ratchets de présence à dix sous une mesure déterministe, raison écrite à côté des clés).

**`AUD-PRF-09` est corrigé** : la décision du 17 octobre est rouverte par le mainteneur. Chaque appel amont de la
façade remote a un délai (60 s en lecture, 300 s en écriture et ADMIN), et une écriture expirée répond un résultat
inconnu, `504 UPSTREAM_WRITE_TIMEOUT`.

**`AUD-DEP-07` est corrigé** : le gate CVE a une seconde source de rafraîchissement, les flux de données JSON 2.0 du
NVD, sans clé, qui prend le relais dès que le chemin par `NVD_API_KEY` n'a pas rafraîchi la base.

**Plan du 9 octobre : 55 soldés sur 73, 18 restants.** Les sprints 1, 2, 3, 4, 5, 7 et 8 sont terminés. La PR #569
(sprint 9), fusionnée le 28 octobre, n'est pas revérifiée par cette passe.

---

## État au 26 octobre 2026 — la grande restructuration, et le premier build vert de la mission

67 commits et **25 pull requests** depuis la passe du 24 octobre (#540 à #564) : 907 fichiers,
+15 100 / −5 206. La plus grosse vague depuis l'audit. Statuts établis sur `4694b8b2`.
Détail dans **`reverification-2026-10-26.md`**.

**Pour la première fois, l'audit dispose d'une exécution réelle.** Les six passes précédentes ont
travaillé sans compiler — Maven Central est injoignable depuis le conteneur d'audit. Le build a été
lancé sur la machine du mainteneur : **`./mvnw clean verify` vert sur les 19 modules en 10 min 46,
4 437 tests, 0 échec, 13 skips**, dont 779 exécutions de tests d'architecture. Les règles ArchUnit
des trois nouveaux ADR passent, les gates de couverture passent, et la couverture est mesurée.

**Trois constats se ferment**, tous en architecture, tous avec une règle exécutable à l'appui :
`AUD-ARC-02` (**Élevée** — l'ingestion passe par un port, l'agrégat concret a disparu du dépôt),
`AUD-ARC-06` (`morpheus-api` en sept paquets de rôle, racines de composition de 32 à 5) et
`AUD-ARC-09` (`MorpheusCli` de 908 à 109 lignes, `MorpheusMcpToolService` supprimé). Trois ADR du
25 octobre — ARCH-0147, ARCH-0148, ARCH-0149 — tranchent le sprint 11.

**Quatre constats naissent de cette même restructuration** : l'élargissement de visibilité qu'elle a
imposé dans `morpheus-store-sqlite` (`AUD-ARC-16`), un test de garde qui resterait vert si sa règle
devenait aveugle (`AUD-TST-20`), la liste d'ADR-0148 recopiée à la main dans son gate (`AUD-ARC-17`)
et deux dérives de la trace de suivi (`AUD-DEP-26`).

**110 constats : 49 corrigés · 12 partiels · 22 toujours valides · 4 caducs · 22 Info qui tiennent ·
1 non vérifiable.** Il reste **deux constats `Élevée`** : `AUD-ARC-01` et `AUD-PRF-04`, tous deux
partiels.

| Axe | 9 oct. | 17 oct. | 18 oct. | 21 oct. | **26 oct.** | |
|---|---|---|---|---|---|---|
| Sécurité | 75 | 100 | 96 | 96 | **96** | — |
| Dépendances / exploitation | 36 | 87 | 75 | 84 | **83** | −1 |
| Performance | 28 | 52 | 77 | 77 | **77** | — |
| Tests | 54 | 69 | 66 | 75 | **70** | −5 |
| Qualité | 46 | 66 | 63 | 71 | **70** | −1 |
| Architecture | 26 | 46 | 46 | 46 | **55** | **+9** |
| **Moyenne** | 44 | 70 | 70 | 75 | **75** | — |

La moyenne ne bouge pas, et c'est le résultat honnête. L'architecture gagne neuf points, mais quatre
constats neufs et une lecture plus exacte de la couverture reprennent ailleurs ce qui a été gagné.
Un score stable après la plus grosse vague de la mission ne dit pas que rien n'a avancé : il dit que
le travail a déplacé autant de dette qu'il en a soldé, et que la mesure le voit.

**Ce que le build révèle sur la couverture.** Mesurée par module et par ses *propres* tests :
`morpheus-application` **48,00 %** de lignes sur 11 076 instrumentées, `morpheus-store-memory`
52,87 %, `morpheus-domain` 59,78 %, contre `morpheus-api` 94,24 % et `morpheus-cli` 88,19 %.
L'agrégat affiche 91,33 %. L'écart de `AUD-TRV-02` n'est plus déduit de deux plafonds : il est
mesuré, et sa cause est visible — le cœur métier n'atteint les chiffres de l'agrégat que parce que
les tests des adaptateurs le traversent. Par ailleurs la couverture agrégée réelle (91,33 % / 77,49 %)
**dépasse les plafonds qualifiés inscrits** le 9 octobre (90,30 % / 76,11 %), qui décrivent donc un
état périmé.

**Aggravations.** Les copies de `sha256` passent de 6 à 7 — la nouvelle est née du découpage de
`morpheus-store-sqlite`. Les blocs de 12 lignes dupliqués dans `src/main` passent de 322 à 405.
`AUD-QUA-02` baisse de 99 à 71 copies, sous son chiffre d'origine pour la première fois, mais la
divergence double : 16 écritures distinctes du même localisateur contre 7, et 5 critères de détection
incompatibles contre 2.

**Errata.** Le « reste à faire » d'`AUD-DEP-05` demandait une section de vérification SHA-256 qui
existe depuis le tag `v1.2.1`, antérieure au constat. Et `AUD-ARC-11` sous-comptait son propre
sujet : huit classes de production atteignent la réflexion, pas trois.

---

## État au 21 octobre 2026 — le sprint 8, et deux errata de l'audit

72 commits depuis la passe du 18 octobre (PR #517 à #535) : le **sprint 8** et la clôture du sprint 7.
Statuts établis sur `6bdf4bbb`. Détail dans **`reverification-2026-10-21.md`**.

**Quatre des six constats du sprint 8 sont corrigés**, deux restent partiels. Et cette passe corrige **deux
erreurs de l'audit lui-même** — voir les errata ci-dessous.

**106 constats : 46 corrigés · 7 partiels · 26 toujours valides · 4 caducs · 22 Info qui tiennent · 1 non
vérifiable.** Un constat neuf, `AUD-TST-19`. Il reste **trois constats `Élevée`** : `AUD-ARC-01`,
`AUD-ARC-02`, `AUD-PRF-04`.

| Axe | 9 oct. | 13 oct. | 17 oct. | 18 oct. | **21 oct.** | |
|---|---|---|---|---|---|---|
| Sécurité | 75 | 100 | 100 | 96 | **96** | — |
| Dépendances / exploitation | 36 | 68 | 87 | 75 | **84** | +9 |
| Performance | 28 | 52 | 52 | 77 | **77** | — |
| Tests | 54 | 69 | 69 | 66 | **75** | +9 |
| Qualité | 46 | 56 | 66 | 63 | **71** | +8 |
| Architecture | 26 | 35 | 46 | 46 | **46** | — |
| **Moyenne** | **44** | **63** | **70** | **70** | **75** | **+5** |

**Le meilleur niveau depuis le 9 octobre.** Charge restante : 38 à 110 jours sur 33 constats ouverts. Les
sprints 2, 3 et 4 sont terminés, les sprints 1, 5, 6 et 7 gardent un reliquat chacun, le sprint 8 est
largement entamé.

**Premier erratum — `AUD-DEP-23` et `AUD-DEP-24` sont caducs.** Le 18 octobre j'ai levé un constat `Élevée`
disant que les voies nocturnes analysaient une branche gelée. L'observation était exacte, la conclusion non :
c'était la **fenêtre de promotion de la v1.2.1**, pas un changement de modèle. La PR #516 a refermé la
fenêtre le 18 à 16h33, et depuis les dix-neuf PR du lot sont toutes allées sur `develop`. Le modèle décrit
par `ROADMAP.md` n'a jamais changé. *La leçon : auditer pendant une fenêtre de release, c'est risquer de
prendre l'état transitoire pour l'état permanent.*

**Second erratum — `AUD-DEP-13` était corrigé depuis le 14 octobre.** Le `<version>1.0.4</version>` a disparu
dans le commit qui soldait `AUD-DEP-14` ; j'ai vérifié l'un sans voir que le même commit fermait l'autre, et
quatre passes l'ont reconduit sans le remesurer. *Un constat soldé en dommage collatéral d'un autre n'est
signalé par rien.*

**Ce qui est le plus solide dans ce lot.** `AUD-TST-03` : 457 `assertThrows` au résultat jeté avant, **0**
après, recomptés indépendamment. `AUD-TST-06` : le gate ne compare pas à une liste recopiée mais à la table
de routage de la CLI elle-même, que `MorpheusMain.run` consulte avant tout dispatch. `AUD-TST-08` : la
frontière entre tests écrits et générés est **mesurée**, pas déclarée.

**Un constat neuf, `AUD-TST-19`.** Le commit qui a re-basé les ratchets a mesuré 693 tests d'architecture et
laissé le ratchet à 585 : **108 exécutions de jeu** sur la couche qui tient tous les autres invariants, quand
les deux autres clés du même fichier n'en ont que 10.

**Une aggravation, troisième fois : `AUD-QUA-02`.** 99 copies du localisateur de racine, contre 73 au
9 octobre — dont 97 dans `morpheus-architecture-tests`. Tant que le socle n'existe pas, tout travail de test
aggrave ce constat, y compris celui qui solde les autres.

**Trois réserves.** *Aucun build n'a pu être exécuté* dans aucune des six passes : les ratchets et les
plafonds de couverture ne sont pas revérifiables, seule leur mécanique l'est. *PowerShell est absent*, donc
la moitié `.ps1` du dispositif n'est ici ni lancée ni testée — ce qui est aussi le fond de `AUD-TRV-07`.
*L'API GitHub est refusée*, donc les identifiants de run et les durées restent des revendications. Seule
exécution réelle : la suite Python, **322 tests, 0 échec, 21 sautés**.

## État au 18 octobre 2026 — le sprint 7, et le changement de branche d'intégration

33 commits depuis la passe du 17 octobre (PR #508 à #515), portant le **sprint 7 de performance** et un
changement que personne n'avait annoncé à l'audit : **`main` a remplacé `develop` comme branche
d'intégration**. Les statuts sont ceux de `29c942ae`, dont l'arbre est identique à `origin/main`. Détail
dans **`reverification-2026-10-18.md`**, complément du soir compris.

**Huit constats passent à corrigé**, dont `AUD-PRF-02`, l'un des deux derniers `Élevée` de l'axe
Performance. **Quatre constats sont neufs**, tous nés du changement de branche ou de ses effets de bord :
`AUD-DEP-23`, `AUD-DEP-24`, `AUD-DEP-25`, `AUD-SEC-17`.

**105 constats : 41 corrigés · 5 partiels · 34 toujours valides · 2 caducs · 22 Info qui tiennent · 1 non
vérifiable.** Il ne reste que **trois constats `Élevée`** : `AUD-ARC-01`, `AUD-ARC-02` et `AUD-PRF-04`.

| Axe | 9 oct. | 13 oct. | 15 oct. | 17 oct. | **18 oct.** | |
|---|---|---|---|---|---|---|
| Sécurité | 75 | 100 | 100 | 100 | **96** | −4 |
| Dépendances / exploitation | 36 | 68 | 73 | 87 | **75** | −12 |
| Performance | 28 | 52 | 52 | 52 | **77** | **+25** |
| Tests | 54 | 69 | 65 | 69 | **66** | −3 |
| Qualité | 46 | 56 | 52 | 66 | **63** | −3 |
| Architecture | 26 | 35 | 36 | 46 | **46** | — |
| **Moyenne** | **44** | **63** | **63** | **70** | **70** | — |

La moyenne ne bouge pas, et c'est l'information : **le sprint 7 paie exactement ce que les quatre constats
neufs coûtent.** Le gain de 25 points sur Performance est le plus gros d'une passe sur un seul axe depuis le
9 octobre.

**Où en est le plan.** Les sprints **2, 3 et 4 sont terminés**. Les sprints 1, 5, 6 et 7 gardent **un
reliquat chacun**, et ce sont les seuls restes du plan du 9 octobre dans la première moitié :

| Sprint | Reliquat | Où il en est |
|---|---|---|
| 1 | `AUD-DEP-07` | **Bloqué en amont** — Dependency-Check 13.0.1 n'existe pas ; rien que le dépôt puisse faire |
| 5 | `AUD-PRF-09` | Change OpenSpec ouvert, non archivé : en cours |
| 6 | `AUD-PRF-04` | Partiel — la table HTTP est corrigée, les deux autres sont mises hors d'atteinte par une règle |
| 7 | `AUD-PRF-14` | **Jamais commencé** — cité dans aucun change OpenSpec |

Les sprints 8 à 14 n'ont pas commencé, conformément à l'ordre. Charge restante : 41 à 120 jours sur 39
constats ouverts.

**Correction de modélisation.** Les neuf constats nés *après* le 9 octobre — d'une scission, d'un effet de
bord d'un correctif, ou du changement de branche — étaient rangés dans les sprints du plan d'origine, ce qui
faisait croire que ceux-ci traînaient depuis le 9 octobre. C'était faux. Ils sont regroupés dans un
**sprint 15, « Dérives apparues pendant les correctifs »** : 9 constats, 7 ouverts. Aucun n'est prérequis
d'un constat du plan d'origine, la vérification a été faite avant de les déplacer.

**Ce qu'il faut savoir du changement de branche.** Les sept PR du lot ont toutes atterri sur `main`, et
`origin/HEAD` y pointe : `main` est la branche d'intégration, `develop` un miroir rattrapé à la main. Les
huit voies nocturnes — Sonar, SpotBugs, PIT, budgets M19, répétition de release, validateurs sans voie —
épinglent toutes `develop`. Pendant environ vingt-quatre heures elles ont **rendu vert sur du code périmé**,
jusqu'à ce que la PR #515 synchronise les deux branches, une minute après que le relevé l'ait trouvé.
Aujourd'hui les arbres sont identiques et le volet aigu est éteint, d'où la rétrogradation d'`AUD-DEP-23`
d'Élevée à Moyenne. Mais rien ne garantit que `develop` suive : **un vert nocturne atteste l'état de
`develop` au moment du run, pas celui de `main`.** Le reste du basculement n'est pas répercuté non plus —
Dependabot vise toujours `develop`, donc une mise à jour de dépendance fusionnée là n'atteindra jamais
`main`, silencieusement (`AUD-DEP-24`).

**Deux verdicts contre-vérifiés, deux revendications qui ne tiennent pas.** Les deux `Élevée` de Performance
ont été repris par une lecture indépendante : verdicts confirmés, et le comptage d'`AUD-PRF-02` est
recalculable à l'identique. En revanche, `closures-2026-10-17.md` affirme que le filet d'`AUD-QUA-18` est
« éprouvée par un test qui fuit la portée » — **ce test n'existe pas** ; et il justifie le maintien en
séquentiel de trois modules par `java.home` et `Locale.setDefault`, alors que `java.home` n'est posé dans
aucun des trois et que `morpheus-cli` ne porte ni l'un ni l'autre. Aucun verdict ne change, mais c'est la
fiabilité du registre de clôture qui est en cause, et c'est le motif que cet audit suit depuis le début.

**Trois réserves.** *Sécurité 96* au lieu de 100 vient d'`AUD-SEC-17`, un rétrécissement du contrôle avant
lecture introduit par une optimisation : l'invariant « rien hors frontière n'entre dans l'inventaire » tient
et il est testé ; l'invariant « aucun contenu n'est lu à travers un lien » ne tient plus. *Aucun build n'a
pu être exécuté* dans aucune des cinq passes — Maven Central est refusé par le proxy, PowerShell est absent,
l'API GitHub aussi. Tout est établi par lecture de code, sauf la suite Python de `scripts/tests`,
réexécutée : **292 tests, 0 échec, 17 sautés**. Enfin, pour tout le sprint 7, *la structure algorithmique est
vérifiable, la baisse de coût ne l'est pas* : elle n'est tenue que par des gates M19 qui ne tournent pas
dans `clean verify`.

## État au 17 octobre 2026 — vérification des sept constats portés à « corrigé »

54 commits depuis la passe du 15 octobre (PR #493 à #506), dont la promotion de **v1.2.1** sur `main` le
16 octobre à `2a8b1ede`. Le mainteneur a porté **sept constats à `corrigé`** dans ce fichier ; chacun a été
revérifié sur le code à `ae368aa3`. Détail dans **`reverification-2026-10-17.md`**. Ce qui a été fait ensuite pour les
sprints 4, 5 et 6, sans toucher aux statuts : **`closures-2026-10-17.md`**.

**Cinq sur sept sont confirmés** — `AUD-TRV-04`, `AUD-TRV-05`, `AUD-TST-12`, `AUD-TST-18`, `AUD-QUA-13`.
**Un ne l'est pas** : `AUD-QUA-17` repasse à *partiel*. Et **un contenait une affirmation fausse, de moi** :
voir l'erratum ci-dessous.

**101 constats : 33 corrigés · 3 partiels · 40 toujours valides · 2 caducs · 22 Info qui tiennent · 1 non
vérifiable.** Un constat neuf, `AUD-QUA-18`, est né de cette passe.

| Axe | 9 oct. | 13 oct. | 15 oct. | **17 oct.** | |
|---|---|---|---|---|---|
| Sécurité | 75 | 100 | 100 | **100** | — |
| Dépendances / exploitation | 36 | 68 | 73 | **87** | +14 |
| Tests | 54 | 69 | 65 | **69** | +4 |
| Qualité | 46 | 56 | 52 | **66** | +14 |
| Performance | 28 | 52 | 52 | **52** | — |
| Architecture | 26 | 35 | 36 | **46** | +10 |
| **Moyenne** | **44** | **63** | **63** | **70** | **+7** |

Sprints 2 et 3 terminés ; 1, 4, 5, 6, 8 et 10 entamés. Charge restante : 44 à 128 jours sur 43 constats
ouverts. **Il ne reste que quatre constats Élevée** — `AUD-ARC-01`, `AUD-ARC-02`, `AUD-PRF-02`,
`AUD-PRF-04` — tous dans les deux axes où rien n'a jamais pu être exécuté.

**Erratum sur `AUD-DEP-12`.** Le constat disait deux choses. La première était vraie et elle est corrigée :
`main` n'était jamais promue, la question « sur quelle branche taguer » était bloquante ; v1.2.1 est
désormais coupée et le tag est à la pointe de `main`. La seconde était **fausse** : j'affirmais que le retard
de `main` privait l'alerte CVE de son déclencheur quotidien. Un `schedule` GitHub s'arme depuis la branche
par défaut, et `security.yml` était déjà sur `main` **avant** la promotion, avec son `cron '17 4 * * *'`
— vérifié sur `origin/main^1`. L'alerte n'a jamais été interrompue, et le dépôt n'avait rien à réparer de ce
côté. Le constat passe à corrigé sur ce qui était vrai.

**`AUD-QUA-17` repasse à partiel.** `SqliteConnectionScope` est bien nettoyé — plus aucun compteur statique,
`diagnostics()` supprimé, les dix classes de test qui le lisaient tombées à zéro. Mais le constat portait sur
une *classe de défaut*, pas sur une classe : un état de processus lu en différence par des tests. Elle s'est
déplacée dans `PhysicalSqliteConnections`, recopié à l'identique dans trois modules, qui déregistre le driver
SQLite du `DriverManager` global — et c'est précisément pour lui que trois modules restent séquentiels. Le
dépôt est plus honnête que la revendication : ce résidu est déclaré dans l'ADR-0115.

**Le constat neuf, `AUD-QUA-18`.** Le parallélisme JUnit vient d'être activé sur `morpheus-store-sqlite`,
ce qui est le résultat voulu d'`AUD-QUA-13`, mais il rend atteignable un risque qui dormait : une portée de
connexion mal fermée reste attachée à un thread du pool et fait échouer le test suivant qui y atterrit, sans
rapport avec sa cause. Un cas concret existe dans le module, et aucun filet ne le couvre.

**Trois réserves avant de citer ces chiffres.** *Sécurité 100* signifie qu'aucun constat de sécurité ne reste
ouvert, pas que le produit est sûr. *Aucun build n'a pu être exécuté* dans aucune des quatre passes : Maven
Central est refusé par le proxy, PowerShell est absent, et cette fois l'API GitHub l'est aussi — la release
v1.2.1 et ses assets sont consignés dans le dépôt mais n'ont pas pu être lus. Tout est établi par lecture de
code, sauf la suite Python de `scripts/tests`, réexécutée : **292 tests, 0 échec, 17 sautés**. Enfin, *« le
parallélisme est configuré » est établi ; « la suite passe en parallèle » ne l'est pas.*

<details><summary>État au 16 octobre 2026, tel que le mainteneur l'avait consigné (passe précédente)</summary>

Les cinq PR du lot ont été fusionnées dans `develop` le 16 octobre (HEAD `557fed02`), et les statuts
revérifiés sur le code fusionné. Six constats passés à corrigé : `AUD-TST-12`, `AUD-TST-18`, `AUD-TRV-04`,
`AUD-TRV-05`, `AUD-QUA-13`, `AUD-QUA-17`. Deux restés partiels : `AUD-TRV-07` et `AUD-DEP-07`. La
vérification du 17 octobre confirme quatre de ces six et renvoie `AUD-QUA-17` à partiel.

</details>

## État au 15 octobre 2026 — après fusion

Les 9 constats laissés `partiel` le 13 octobre ont été revérifiés sur le code. Les **six branches dédiées
ont été fusionnées** dans `develop` le 15 octobre (PR #487 à #492, 47 commits) et supprimées. Tous les
statuts ci-dessous sont ceux de `origin/develop` à `984bb270`, revérifiés **après** la fusion. Le détail est
dans **`reverification-2026-10-15.md`**.

**Trois des neuf constats sont soldés** : `AUD-DEP-14` (PR #486), `AUD-TRV-01` et `AUD-ARC-14`.
Un quatrième, `AUD-DEP-07`, reste ouvert mais **bloqué sur une publication amont** — Dependency-Check
13.0.1 n'existe pas, la dernière version publiée est 13.0.0 du 3 août, donc aucune action du dépôt ne le
ferme. Les cinq autres ont beaucoup avancé mais restent partiels, et la section 4 de leurs changes OpenSpec
(vérification : `clean verify`, gate de couverture, validateur M19) est ouverte dans les cinq cas.

**100 constats : 27 corrigés · 6 partiels · 42 toujours valides · 2 caducs · 22 Info qui tiennent · 1 non
vérifiable.** Deux constats neufs sont nés de cette passe : `AUD-QUA-17` et `AUD-TST-18`.

| Axe | 9 oct. | 13 oct. | 15 oct. avant fusion | **15 oct. après fusion** |
|---|---|---|---|---|
| Sécurité | 75 | 100 | 100 | **100** |
| Dépendances / exploitation | 36 | 68 | 69 | **73** |
| Tests | 54 | 69 | 61 | **65** |
| Qualité | 46 | 56 | 48 | **52** |
| Performance | 28 | 52 | 52 | **52** |
| Architecture | 26 | 35 | 35 | **36** |
| **Moyenne** | **44** | **63** | **61** | **63** |

Sprint 3 terminé ; sprints 1, 2, 4, 5, 6 et 10 partiellement soldés ; 7, 8, 9, 11 à 14 non commencés.
Charge restante : 48 à 141 jours sur 48 constats ouverts.

**Les deux constats neufs, à lire ensemble.** `AUD-QUA-17` : le verrou du parallélisme JUnit ne s'est pas
levé, il s'est **déplacé** — `SqliteConnectionScope` porte quatre compteurs statiques de processus lus en
différence par les tests, et leur nombre est **passé de 6 à 10 classes par la fusion même du 15 octobre**,
parce que le correctif d'`AUD-TRV-05` a payé sa mesure en ajoutant trois tests de comptage. Le travail est
bon ; son effet de bord mérite son propre constat. `AUD-TST-18` : `validate-r3` exige 17 fichiers `pom.xml`,
le dépôt en a 19 — le validateur de release **ne peut plus passer**, dans aucune des deux variantes, et
personne ne s'en est aperçu parce qu'aucun workflow ne le lance. Attention au faux ami : le commit
*« the fabricated checkout reads the reactor from the root POM »* corrige le harnais de test d'`AUD-TRV-07`,
pas le validateur.

**Trois réserves avant de citer ces chiffres.** *Sécurité 100* signifie qu'aucun constat de sécurité ne
reste ouvert, pas que le produit est sûr. *`AUD-DEP-12` reste aggravé* : `main` est loin derrière `develop`,
ce qui rend la question « sur quelle branche taguer » bloquante et prive l'alerte CVE de son déclencheur
quotidien. Et *aucun build n'a pu être exécuté* dans aucune des passes : Maven Central est refusé par le
proxy. Tout est établi par lecture de code — à une exception, la suite Python de `scripts/tests`, réexécutée
après fusion : **244 tests, 0 échec, 17 classes sautées** (le miroir PowerShell).

<details><summary>État au 13 octobre 2026 (passe précédente)</summary>

Les 96 constats ont été revérifiés un par un sur le code à `d5be889b`, après 263 commits et 113 PR
fusionnées. Détail dans `reverification-2026-10-13.md`. **24 corrigés · 9 partiels · 40 toujours valides ·
1 caduc**, et 22 des 24 constats *Info* tenaient toujours. Deux constats neufs (`AUD-PRF-19`, `AUD-ARC-15`)
sont nés de la scission de `AUD-PRF-18`, devenu caduc après que le dépôt a mesuré que ma cause était fausse.
Moyenne 44 → 63. Trois comptages ont monté sans traitement parce que les correctifs eux-mêmes les
alimentent : le localisateur de racine recopié (73 → 91), les `assertThrows` nus (300 → 332), la part
textuelle de l'enforcement (1/35 → 1/39).

</details>

Les notes ci-dessous et tout ce qui suit décrivent l'**état du 9 octobre**, conservé comme référence.

---

## Notes par axe

| Axe | Note | Calcul | Ce que la note dit |
|---|---|---|---|
| Sécurité | **75**/100 | 100 − (1×10 + 3×4 + 3×1) | Le mieux tenu. 8 constats *Info* documentent des protections réelles et correctes. |
| Tests | **54**/100 | 100 − (1×10 + 8×4 + 4×1) | Dispositif impressionnant, mais une partie ne s'exécute plus. |
| Qualité | **46**/100 | 100 − (1×10 + 10×4 + 4×1) | Hygiène de base irréprochable ; la dette est dans la duplication du socle. |
| Dépendances / exploitation | **36**/100 | 100 − (2×10 + 10×4 + 4×1) | Build et chaîne CI exemplaires ; l'exploitation est le point faible. |
| Performance | **28**/100 | 100 − (4×10 + 7×4 + 4×1) | **À lire avec la mise en garde ci-dessous.** |
| Architecture | **26**/100 | 100 − (4×10 + 8×4 + 2×1) | **À lire avec la mise en garde ci-dessous.** |
| **Moyenne** | **44**/100 | | |

Barème appliqué : 100 − 25 par Critique − 10 par Élevée − 4 par Moyenne − 1 par Faible, plancher 0, un constat fusionné comptant sur chacun de ses axes.

**Deux notes communiquent faux, et il faut le dire avant de les citer.**

*Performance 28/100* mesure la densité de constats structurels dans le seul axe où rien n'a pu être mesuré à l'exécution. Les preuves ne disent pas « le produit ne tient pas la charge » : elles disent que le produit **a** des budgets de performance explicites et gelés (10 000 requirements, 25 000 liens, 60 s pour une publication complète), des bornes de traversée, de scan et de trame MCP — et qu'il **a cessé de les exécuter**. Si un seul chiffre doit porter ce risque, c'est `AUD-TST-01`, pas 28/100.

*Architecture 26/100* parce que le barème ne distingue pas une dette arbitrée d'une dette ignorée. Les 167 cycles de paquets de `morpheus-application` sont mesurés, chiffrés et explicitement reportés par une décision datée (ADR-0109). Un dépôt qui tient 605 méthodes de tests d'architecture, 40 règles de bytecode, une liste de racines de composition qui ne fait que rétrécir et un ADR par frontière n'est pas « à 26/100 d'architecture ». Le chiffre réellement actionnable de cet axe est **1 règle de bytecode pour 35 assertions textuelles** (`AUD-TRV-03`).

---

## Les cinq constats majeurs

1. **`AUD-DEP-01` — la 1.2.1 ajoute 5 migrations de schéma (V016→V020) et aucun document d'upgrade ne les couvre.** Avec `AUD-DEP-16` (aucune sauvegarde automatique avant migration) et `AUD-TST-10` (le seul test d'upgrade depuis une base réelle part du palier 12), c'est le seul cluster de l'audit dont la matérialisation est **irréversible pour un utilisateur** : une base passée en V020 fait refuser le démarrage du binaire précédent, et rien ne garantit qu'une sauvegarde pré-upgrade existe. *Bloqueur de release.*

2. **`AUD-TST-01` — les cinq budgets de performance M19 ne sont exécutés par aucun pipeline.** `M19PerformanceGate` porte un javadoc expliquant que son nom ne correspond pas volontairement aux patterns Surefire ; aucun `<includes>` ne le rattrape, et `grep m19 .github/workflows/` ne renvoie rien. Ils n'ont pas tourné depuis le SHA M19, neuf milestones plus tôt. Une régression de performance ne casse aucun build. **Correction la plus rentable de tout l'audit : brancher `validate-m19` sur `nightly.yml`.**

3. **`AUD-PRF-18` — épinglage des threads virtuels.** 94 méthodes `public synchronized` faisant du JDBC bloquant dans `morpheus-store-sqlite`, atteintes depuis les deux serveurs HTTP qui tournent sur `newVirtualThreadPerTaskExecutor()`, sur une baseline Java 21 où un blocage dans un `synchronized` épingle le thread porteur (JEP 491 ne lève cela qu'en Java 24). Aucun `jdk.virtualThreadScheduler.*` configuré. Quelques écritures concurrentes au-dessus d'un `busy_timeout` de 5 s en mode `journal_mode = PERSIST` suffisent à affamer l'ordonnanceur — y compris les requêtes qui n'auraient jamais touché SQLite. **Ce constat n'a été produit par aucun des six axes : il vient de la relecture indépendante.**

4. **`AUD-SEC-02` — divulgation de chemins serveur à un appelant distant.** Quatre routeurs qui enregistrent leur propre contexte (`query`, `policy`, `policy-management`, `reasoning`, une vingtaine de routes) renvoient le message d'exception brut au lieu de passer par `BoundaryFailureMessage.safe`, dont le javadoc affirme pourtant que « both servers apply this ». Le proxy remote relaie le corps amont octet pour octet : le chemin jusqu'à un appelant hors machine est établi. Premier constat de l'axe Sécurité, et le seul des 96 qui aurait mérité *Critique* si la divulgation avait été démontrée sur une route ADMIN.

5. **`AUD-TRV-04` — le produit est muet.** Zéro `org.slf4j`, zéro `LoggerFactory` sur 659 fichiers `src/main`. Le seul binding packagé est `slf4j-nop`, qui jette les événements du SDK MCP et de sa pile transitive. Le seul canal structuré est opt-in et `noop` par défaut, et une valeur mal orthographiée de `MORPHEUS_OPERATIONAL_LOGS` le désactive en silence (`AUD-DEP-10`). Un répertoire de logs est publié dans le manifeste de release et calculé par la CLI — **sans aucun écrivain**. En incident, aucune trace ne survit à la fin du processus.

---

## Thèmes transverses

Cinq causes racines expliquent la majorité des 96 constats.

**1. L'invariant est écrit, pas exécuté.** Une règle vit en prose — `.claude/rules/*`, `SECURITY.md`, un javadoc, un ADR — et rien ne la vérifie, ou le vérificateur lit du texte au lieu du bytecode. C'est le thème qui porte le plus de constats (`AUD-ARC-08`, `AUD-ARC-11`, `AUD-ARC-14`, `AUD-QUA-04`, `AUD-QUA-12`, `AUD-SEC-02`, `AUD-SEC-07`, `AUD-DEP-06`, `AUD-TST-06`, `AUD-TRV-01`, `AUD-TRV-03`) et c'est aussi celui que le dépôt revendique maîtriser : `.claude/rules/meta.md` existe précisément pour ça. Cas particulier qui mérite son nom : **la convergence est vérifiée sur la présence, pas sur la forme** — les gates comparent des listes de routes et d'outils, jamais les payloads (`AUD-ARC-10`), jamais la colonne CLI (`AUD-TST-06`), jamais la politique de connexion. Cas le plus net : `validate-d2` code en dur un plancher de 820/258 au lieu de lire la source vivante à 3820/585, et le test qui l'épingle asserte `contains("820")` — satisfait par la sous-chaîne de « 3820 ».

**2. Il n'existe pas de point de passage obligé : chaque site d'appel choisit sa politique.** 12 sites de composition SQLite pour 3 ouvertures de scope (`AUD-TRV-05`), 4 routeurs qui refont leur propre filtre de message (`AUD-SEC-02`), pas de contrôle d'admission local face aux 4 sémaphores du serveur remote (`AUD-PRF-03`), socle d'analyse d'arguments recopié par sous-commande (`AUD-QUA-06`). Mécanique commune : sans point de passage obligé, chaque ajout choisit sa politique et **aucun durcissement ne se propage**.

**3. La duplication par copie est le mode de croissance du socle.** 73 copies du localisateur de racine de dépôt sous 3 noms et 7 implémentations divergentes, 10 constructeurs de `Map` sous 2 noms, 6 `sha256` avec 2 messages d'erreur différents, 4 projections `requirement` identiques, deux fichiers de settings MINOS/NEXUS jumeaux à 90 % avec dérive déjà installée. Le thème le mieux soutenu quantitativement (`AUD-QUA-02`, `-06`, `-07`, `-08`, `-09`, `AUD-ARC-10`).

**4. Les budgets de performance existent mais ont cessé d'être exécutés, et les chemins ajoutés depuis n'en ont pas reçu.** Formulation importante : le coût **a** été un critère de conception — budgets gelés, `MAX_NODES`, `ScanBudget`, bornes de trame, ADR-0085. Ce qui a cessé, c'est de le vérifier (`AUD-TST-01`), et les chemins ajoutés depuis n'ont pas de budget : `findPath` appelé par nœud découvert (`AUD-PRF-02`), tables d'audit sans purge ni `LIMIT` (`AUD-PRF-04`). C'est un défaut de chaîne d'exécution, pas de culture d'ingénierie.

**5. Le produit est muet.** Le seul des cinq qui soit un défaut de produit et non de vérification. Voir constat majeur n° 5.

---

## Plan d'action

Deux lectures des mêmes constats, et elles ne se contredisent pas.

**`sprints.md` donne l'ordre d'exécution.** Les 72 constats actionnables — les 24 *Info* n'en demandent aucune — sont répartis en **14 sprints**, dans un ordre contraint par **33 dépendances** relevées pendant l'audit et vérifiées par script : aucun prérequis ne se trouve dans un sprint postérieur à celui qui en dépend. Charge totale : 60 à 181 jours, enveloppe dérivée des efforts par constat.

| | Sprint | Pourquoi là |
|---|---|---|
| 1 | Remettre le dispositif en marche | Rien ne se corrige de façon mesurable tant que les gates ne tournent pas. |
| 2 | Débloquer la release 1.2.1 | Le seul cluster irréversible pour un utilisateur. |
| 3 | Fermer les écarts de sécurité | Huit corrections petites et indépendantes. |
| 4 | Rendre le produit observable | Les sprints suivants corrigeraient à l'aveugle. |
| 5 | Écarter le risque d'indisponibilité | Épinglage des threads virtuels, politique de connexion, admission. |
| 6 | Borner les coûts qui croissent avec les données | Mesurable seulement après le sprint 1. |
| 7 | Le SQL de détail | Mêmes fichiers que le 6, donc après lui, jamais en parallèle. |
| 8 | Fiabiliser les tests | Ce qui empêche la suite de détecter une régression. |
| 9 | Supprimer la duplication du socle | Un helper en six exemplaires dérive déjà. |
| 10 | Dire la vérité dans les règles | Sept règles sont fausses au sens où elles sont écrites. |
| 11 | Trancher l'architecture | Un ADR d'abord : il conditionne les sprints 12 et 13. |
| 12 | Restructurer les adaptateurs | Rendre les frontières exprimables par la structure. |
| 13 | Couverture et pagination | Les deux chantiers longs qui dépendent des sprints 1 et 11. |
| 14 | Signer la distribution | Chemin critique externe : l'achat d'un certificat, pas du code. |

**Chemin critique** — quatre constats conditionnent le plus de travail en aval : `AUD-TST-01` débloque 7 constats pour un effort **S** (c'est pourquoi il ouvre le sprint 1), `AUD-ARC-01` en débloque 4, `AUD-DEP-01` 3, `AUD-TRV-04` 2.

**`plan-action.md` donne la même matière classée par urgence** — *Immédiat*, *Court terme*, *Fond*. Les sprints 1 à 5 recouvrent l'*Immédiat* et le début du *Court terme*, les sprints 6 à 10 le reste, les sprints 11 à 14 le *Fond*. Quand les deux divergent, c'est l'ordre des sprints qui s'applique : une action urgente dont le prérequis n'est pas livré ne peut pas être vérifiée.

---

## Angles morts

Ce qui n'a **pas** été audité, et pourquoi. À lire avant de conclure quoi que ce soit de cet audit.

- **Aucun build, aucun test, aucun gate n'a été exécuté.** Maven Central est refusé par la politique réseau du conteneur d'audit (403 sur `CONNECT`, confirmé sur `repo.maven.apache.org` et `repo1.maven.org`) et `~/.m2` était vide. **Aucune couverture réelle n'a donc été mesurée** : les preuves `target/m21-*-coverage-summary.txt` n'existent pas, et les écarts de couverture sont calculés depuis les constantes des gates et les ratios lignes de test / lignes `main`. Seule exécution réelle de l'audit : les 7 tests Python de `scripts/tests/` (`Ran 7 tests in 0.297s / OK`). Pour couvrir : importer les preuves depuis les artefacts CI `m21-integrity-<OS>` plutôt que de compter sur un build local.
- **Aucun scan NVD/OWASP.** Les constats de dépendances portent sur la **fraîcheur des versions**, jamais sur une absence de CVE prouvée. À passer au scanner en priorité : `reactor-core 3.7.0` (18 correctifs de retard dans son propre train), `sqlite-jdbc 3.53.4.0`, `jackson-databind 3.2.3`, `mcp-core 2.0.1`.
- **Aucun graphe d'appels complet.** Le graphe de dépendances a été reconstruit à la main depuis les `import` en tête de fichier. Cette méthode est aveugle aux constantes inlinées par `javac`, aux dépendances par génériques et annotations, et aux dépendances transitives. Elle a été contrôlée : aucune référence pleinement qualifiée inter-module n'est inlinée hors ligne d'`import`.
- **Aucun `EXPLAIN QUERY PLAN`.** Les conclusions sur l'utilisation des index reposent sur les règles documentées de SQLite appliquées au schéma lu. Chaque action correspondante demande une vérification par `EXPLAIN QUERY PLAN` avant modification.
- **Aucun comportement d'exécution observé** : pas de profileur, pas de benchmark, pas de test de charge. `AUD-PRF-18` en particulier repose sur la sémantique documentée de Java 21 et sur la structure du code.
- **Windows non observé.** Les chemins ACL de `LocalWritePermissionHardener`, l'installeur Inno Setup, l'upgrade transactionnel et le désinstalleur n'ont été lus que comme texte.
- **Le mode remote n'a pas été exercé de bout en bout.** Aucun constat ne dit si un test démarre réellement la façade HTTPS avec un keystore et exerce un refus de rôle, ni ce que valent les 4 sémaphores sans test de saturation. C'est la seule surface exposée hors machine : l'écart de couverture le plus coûteux à ignorer, et il est mesurable statiquement.
- **Sauvegarde et restauration sous concurrence** — aucun constat n'examine ce que fait un `VACUUM INTO` pendant qu'un écrivain tient le verrou EXCLUSIVE en mode PERSIST, ni le sort du `ThreadLocal` de `SqliteConnectionScope` si une restauration remplace le fichier sous un scope ouvert. C'est le seul chemin du produit dont l'échec est destructif.
- **Attribution de licence du runtime embarqué.** `AUD-DEP-03` couvre les six dépendances tierces, mais la distribution est une app-image `jpackage` qui embarque un runtime Java entier, et rien dans le dépôt ne nomme la distribution de JDK utilisée pour construire les artefacts publiés.
- **Pas de détecteur de duplication professionnel.** Les chiffres de duplication viennent d'un scan de blocs normalisés de 12 lignes écrit pour cet audit. Un CPD/PMD donnerait un chiffre différent, probablement plus élevé. La complexité cyclomatique n'a pas été mesurée du tout, seulement la longueur et la profondeur d'imbrication.
- **SpotBugs et PIT non rejoués.** Les 195 alertes et 1 586 mutants survivants cités par `docs/audits/AUDIT_OUTILLE_2026-10-08.md` n'ont pas été revérifiés à `20cf2e0`.
- **Modules survolés** : `morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit`, `morpheus-provider-reference` et les 31 fichiers `.py`. Sur les 147 fichiers de `morpheus-architecture-tests`, cinq ont été lus intégralement ; les 142 autres n'ont été parcourus que par `grep`.
- **Outils d'indexation de code tiers non utilisés**, conformément à la consigne de l'audit : non validés sur cette application.

## Chiffres vivants relus pour cet audit

Relus à `20cf2e0` via `.claude/skills/live-numbers/numbers.sh`, et jamais recopiés d'une page de documentation. Ils se périment : les relire avant de les citer.

`testsMinimum` 3820 · `architectureTestsMinimum` 585 · `perModuleLineCoverageMinimum` 0,691 · `perModuleBranchCoverageMinimum` 0,612 · `aggregateLineCoverageMinimum` 0,900 · `aggregateBranchCoverageMinimum` 0,757 · plafonds qualifiés 0,694341 / 0,615727 (par module) et 0,903026 / 0,761089 (agrégée) · 18 modules · 109 ADR, plus haut numéro 0109, aucun doublon · 90 lignes au manifeste de convergence (en-tête comprise) · `SUPPORTED_SCHEMA_VERSION` 20 · 605 méthodes `@Test` dans `morpheus-architecture-tests`.

**Une divergence documentation / code relevée en chemin**, signalée ici comme l'exige `.claude/rules/meta.md` : `docs/developer/BUILD_AND_TEST.md:17`, `docs/developer/README.md:32`, `docs/architecture/arc42/05-vue-blocs.md:81` et `docs/architecture/arc42/04-strategie-solution.md:27` annoncent « 17 modules enfants » alors que `pom.xml` en déclare 18 — et le détecteur capable de l'empêcher existe déjà (`RepositoryDocumentationCoherenceTest`), sans être branché sur ces pages. C'est `AUD-QUA-04`.
