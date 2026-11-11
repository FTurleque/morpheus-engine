# Vérification du 21 octobre 2026 — le sprint 8, et deux errata

Audit du 9 octobre, revérifié les 13, 15, 17 et 18 octobre, puis le **21 octobre sur `6bdf4bbb`**
(`origin/develop`), après 72 commits et les PR #517 à #535 — le **sprint 8** et la clôture du sprint 7.

## Réponse

**Quatre des six constats du sprint 8 sont corrigés, deux restent partiels.** Et cette passe corrige **deux
erreurs de l'audit lui-même** : les deux constats de branche que j'avais levés le 18 octobre décrivaient une
fenêtre de promotion, pas un changement de modèle, et un constat corrigé depuis une semaine était reconduit
en « toujours valide » sans être remesuré.

| Constat | Sév. | Verdict | |
|---|---|---|---|
| `AUD-TST-03` | Moyenne | **corrigé** | 457 `assertThrows` nus → 0 |
| `AUD-TST-06` | Moyenne | **corrigé** | |
| `AUD-TST-08` | Faible | **corrigé** | |
| `AUD-QUA-04` | Moyenne | **corrigé** | |
| `AUD-TST-09` | Faible | partiel | la preuve est conservée, la marge est intacte |
| `AUD-TST-10` | Moyenne | partiel | trois paliers, mais des bases reconstruites |
| `AUD-TRV-07` | Moyenne | partiel | la moitié Windows reste sans lanceur |
| `AUD-PRF-14` | Faible | **toujours valide** | le sprint 7 a été clos par-dessus |

**Notes** : Tests 66 → **75**, Dépendances 75 → **84**, Qualité 63 → **71**. Sécurité, Performance et
Architecture inchangées. **Moyenne 70 → 75**, le meilleur niveau depuis le 9 octobre. Il reste **trois
constats `Élevée`** : `AUD-ARC-01`, `AUD-ARC-02`, `AUD-PRF-04`.

---

## Les deux errata

### `AUD-DEP-23` et `AUD-DEP-24` sont caducs : j'ai pris une fenêtre de release pour un modèle

Le 18 octobre, j'ai levé un constat `Élevée` disant que les huit voies nocturnes analysaient une branche
gelée. Ce que j'observais était exact — `develop` était alors 32 commits derrière `main`, et les PR #508 à
#514 avaient bien atterri sur `main`. Mais c'était **la fenêtre de promotion de la v1.2.1**, pas une
bascule : la PR #515 a resynchronisé `develop` à 14h53 le même jour, la #516 a refermé la fenêtre à 16h33, et
depuis **les dix-neuf PR du lot sont toutes allées sur `develop`**. `main` n'a plus reçu un seul merge.

Le modèle décrit par `docs/project/governance/ROADMAP.md` — feature → `develop` → promotion vers `main` —
n'a jamais changé. C'est ma lecture qui a confondu une phase du cycle avec une règle. Les quatre volets de
`AUD-DEP-24` tombent avec la prémisse : Dependabot vise `develop`, qui est la bonne branche, et douze de ses
PR y ont été fusionnées sans rejeu dans ce lot ; les quatorze validateurs comparent à `origin/develop`, ce
qui est correct ; et la documentation n'a jamais été fausse.

Il reste un résidu trop mince pour un constat : `origin/HEAD` pointe sur `main`, donc un run planifié exécute
la version de `nightly.yml` portée par `main`. J'ai mesuré l'écart réel entre les deux définitions — il se
réduit à des montées de version d'actions par Dependabot, **aucune différence de logique**.

**La leçon de méthode** : auditer un dépôt pendant sa fenêtre de release, c'est risquer de prendre l'état
transitoire pour l'état permanent. Un écart de branches mérite d'être regardé deux fois, à deux jours
d'intervalle, avant d'être nommé défaut.

### `AUD-DEP-13` était corrigé depuis le 14 octobre et quatre passes ne l'ont pas vu

Le `<version>1.0.4</version>` a disparu de `morpheus-mcp-transport/pom.xml` dans le commit `6a09a0a4`
(PR #486, 14 octobre) — **celui-là même qui soldait `AUD-DEP-14`**. `reactive-streams` est depuis résolu par
le BOM Reactor, ce que le message de ce commit annonçait explicitement.

J'ai vérifié `AUD-DEP-14` ce jour-là sans regarder que le même commit fermait aussi `AUD-DEP-13`, et les
quatre passes suivantes l'ont reconduit sans le remesurer. **Un constat soldé en dommage collatéral d'un
autre n'est signalé par rien** : c'est le défaut de méthode à retenir, et il vaut pour toutes les passes à
venir.

---

## Ce qui est soldé dans le sprint 8

**`AUD-TST-03`** est le plus net. Recomptage indépendant, par appariement de parenthèses sur chaque site :
**457 `assertThrows` au résultat jeté avant le lot, 0 après**, hors les corpus fabriqués du gate lui-même.
Les 125 sites nus qui subsistent portent tous sur un type d'exception déclaré par le dépôt, ce que la règle
exempte volontairement. Aucune liste transitoire n'a été livrée avec la règle : les sites ont tous été
corrigés par les six commits qui la précèdent.

**`AUD-TST-06`** vaut pour sa construction. Le gate ne compare pas la colonne `cli` à une liste recopiée — il
la compare à `CliCommandPaths.PATHS`, **qui est la table de routage de la CLI**. `MorpheusMain.run` la
consulte avant tout dispatch : une action ajoutée au `switch` d'une sous-commande est inatteignable tant
qu'elle n'y figure pas. La déclaration gouverne le comportement, et c'est ce qui rend la comparaison valable.

**`AUD-TST-08`** aussi : la frontière entre tests écrits et tests générés est **mesurée, pas déclarée** —
c'est le nom que Surefire donne à l'exécution qui sépare les deux comptes. Ni liste, ni annotation à
maintenir.

**`AUD-QUA-04`** a pris les deux options plutôt que l'une : le total est retiré du texte **et** un gate
compare chaque énumération au POM racine, dans les deux sens, avec un garde-fou qui échoue si aucune page
n'énumère le réacteur.

## Ce qui reste partiel, et pourquoi

**`AUD-TST-09`** — le dépôt a traité l'autre moitié du problème, et il le dit. La preuve des quatre plafonds
est désormais conservée dans le dépôt, ce qui était urgent : les artefacts CI du 9 octobre expiraient le 23.
Mais **aucune valeur de ratchet n'a bougé** et l'écart que le constat nomme est inchangé — 0,334 à 0,409
point. La proposition OpenSpec l'écrit elle-même : « the margin is spent by convention, not by a gate ».
À noter : les fichiers de preuve conservés portent les ratchets **en vigueur au moment du run**, pas ceux
d'aujourd'hui, et rien ne signale la divergence.

**`AUD-TST-10`** — le test couvre trois paliers publiés au lieu d'un et remonte jusqu'au schéma courant, mais
la base de départ est **reconstruite en rejouant les scripts de migration de l'arbre courant**. Aucune base
écrite par un binaire publié n'existe dans le dépôt, et c'est exactement la distinction que le constat
posait. Le test l'admet : « the released program did not write them ». Atténuation réelle : le registre
reçoit les empreintes figées, donc l'édition d'une migration déjà publiée fait échouer l'upgrade.

**`AUD-TRV-07`** — la tâche 1.4, ouverte depuis le premier jour, a produit un relevé de 547 lignes. Je ne
peux pas le recouper, mais il est **falsifiable** — il nomme un échec — et il a produit **un effet
vérifiable** : `write-build-provenance.ps1` mourait sur un HEAD détaché, son repli était inatteignable, et le
correctif plus son test sont là. C'est le genre de défaut qu'un run fictif n'invente pas. La décision 3.7 est
écrite, datée, attribuée.
Ce qui bloque : **huit `.ps1` n'ont toujours aucun lanceur automatique**, et la décision entérine une lane
Linux-seule sans poser la question de la parité. Le cas le plus net : `validate-m21.sh` est un check requis
de chaque PR, `validate-m21.ps1` n'est lancé par rien — deux variantes du même gate, deux statuts
d'enforcement.

**`AUD-PRF-14`** — intact. `ProviderIngestionBudget.java` n'a **reçu aucun commit depuis le 9 octobre**. La
PR #533, nommée `test/sprint-7-close`, archive trois changes et ne touche aucun fichier du constat. Le sprint
7 a été clos sur un constat ouvert, sans que rien dans le merge ne le dise.

---

## Un constat neuf : `AUD-TST-19`

Le commit qui a re-basé les ratchets de tests indique avoir mesuré « 2610 declared, 1584 generated, **693
architecture** ». Il a posé `testsMinimum=2600` et `generatedTestsMinimum=1574` — dix sous chaque mesure —
et **laissé `architectureTestsMinimum=585`**. L'écart est donc de **108 exécutions** sur la troisième clé,
contre 10 sur les deux autres.

Plus d'une centaine de tests d'architecture peuvent disparaître sans qu'aucun validateur ne bronche. C'est la
même famille de défaut que celle qu'`AUD-TST-08` vient de corriger, sur la troisième clé du même fichier,
traitée dans le même commit — et elle porte sur la couche qui tient tous les autres invariants du dépôt.

## Une aggravation : `AUD-QUA-02`, troisième fois

Le localisateur de racine de dépôt est recopié dans **99 fichiers**, contre 73 au 9 octobre, 91 le 13 et 97
le 18. **97 des 99 copies sont dans `morpheus-architecture-tests`**, aucune dans `src/main`. Le sprint 8, qui
ajoute sept changes de tests, en a apporté sa part. Tant que le socle n'existe pas, tout travail de test
aggrave ce constat — y compris le travail qui solde les autres.

---

## Ce que cette passe n'a pas pu établir

La limite habituelle, et elle pèse lourd sur ce lot : **aucun build, aucun test Java, aucun gate n'a pu être
exécuté** — Maven Central est refusé par le proxy. Les trois ratchets `2600 / 1574 / 585` et les quatre
plafonds de couverture ne sont donc pas revérifiables : j'ai établi la mécanique de comptage et la cohérence
interne, pas les nombres. `ReleasedSchemaUpgradeTest` et `CliCommandPathsTest` n'ont pas tourné.
**PowerShell est absent** : les vingt et une classes sautées de la suite Python sont toutes des cas
PowerShell 5.1, donc la moitié `.ps1` du dispositif n'est ici ni lancée ni testée. **L'API GitHub est
refusée** : les identifiants de run, les durées d'étape et l'état des checks requis restent des revendications.

Seule exécution réelle : la suite Python, **322 tests, 0 échec, 21 sautés** — contre 292 et 17 à la passe
précédente. Elle corrobore exactement le chiffre que le dépôt revendique pour la jambe Linux de son run.
