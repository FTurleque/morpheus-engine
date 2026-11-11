# Vérification du 18 octobre 2026 — le sprint 7, et ce que le changement de branche a cassé

Audit du 9 octobre, revérifié les 13, 15 et 17 octobre, puis le **18 octobre sur `341b08ad`** — et cette fois
sur **`origin/main`**, car la branche d'intégration a changé.

41 commits depuis la dernière passe (PR #508 à #514). Douze constats étaient visés, dont les deux derniers
`Élevée` de l'axe Performance. Le dépôt porte un relevé, `closures-2026-10-17.md`, qui dit ce qui a été fait
et précise lui-même qu'il ne touche pas aux statuts — « une ligne de ce fichier est une revendication, et la
revérification suivante dira si le code la confirme ». C'est exactement ce qui suit.

## Réponse

**Huit des douze sont confirmés corrigés. Quatre restent partiels.** Mais le bilan global baisse, parce que
le changement de branche d'intégration a cassé trois choses que personne n'a vues.

| Constat | Sév. | Verdict | |
|---|---|---|---|
| `AUD-PRF-02` | Élevée | **corrigé** | ✓ |
| `AUD-PRF-07` | Moyenne | **corrigé** | ✓ |
| `AUD-PRF-08` | Moyenne | **corrigé** (sur le coût) | ✓ |
| `AUD-PRF-10` | Moyenne | **corrigé** | ✓ |
| `AUD-PRF-11` | Faible | **corrigé** | ✓ |
| `AUD-PRF-12` | Faible | **corrigé** | ✓ |
| `AUD-PRF-13` | Faible | **corrigé** | ✓ |
| `AUD-QUA-18` | Faible | **corrigé** | ✓ |
| `AUD-PRF-04` | Élevée | partiel | |
| `AUD-PRF-19` | Moyenne | partiel | |
| `AUD-QUA-17` | Moyenne | partiel | |
| `AUD-PRF-09` | Moyenne | ouvert — **clôture par décision légitime**, et **erratum** | ⚠ |

Quatre constats neufs : `AUD-DEP-23`, `AUD-DEP-24`, `AUD-DEP-25`, `AUD-SEC-17`.

**Notes** : Performance **52 → 77**, le plus gros gain d'une passe sur un seul axe depuis le début.
Mais Dépendances 87 → 69, Tests 69 → 60, Sécurité 100 → 96, Qualité 66 → 63. **Moyenne 70 → 68.**

Il reste **quatre constats Élevée** : `AUD-ARC-01`, `AUD-ARC-02`, `AUD-PRF-04` et le nouveau `AUD-DEP-23`.

---

## Le point le plus important : les huit voies nocturnes analysent une branche gelée

C'est `AUD-DEP-23`, et il est `Élevée`.

La branche d'intégration est passée à `main` : `develop..main` = 32 commits, l'inverse = 0, et
`origin/HEAD` pointe sur `main`. `develop` est gelée exactement sur `f7e304d8` et n'a plus reçu un commit.

Or les **huit** voies de `nightly.yml` se replient toutes sur `develop` — deux avec un `ref: develop`
épinglé en dur, six avec `schedule && 'develop' || github.ref`. Le cron s'arme bien depuis `main`, puis
chaque job fait un checkout de `develop`.

Depuis la bascule, Sonar, SpotBugs, PIT, les budgets de performance M19, la répétition de release et les
validateurs sans voie **rejouent tous `f7e304d8`**. Ils ne voient aucun des 32 commits, dont les deux
migrations SQLite et la réécriture des stores. Et ils ne tombent pas en échec : ils réussissent.

Un dispositif qui rend vert sur du code périmé est pire qu'un dispositif en panne, parce qu'il produit une
assurance fausse. C'est le motif que cet audit suit depuis le premier jour, et il vient de se reproduire au
cœur de la machinerie. Conséquence directe : les budgets M19 que plusieurs constats citent comme preuve ne
sont plus mesurés sur le code qu'ils prétendent mesurer.

Le commentaire du workflow justifie l'épinglage par « ne pas analyser `main` une seconde fois ». Le
raisonnement est désormais inversé — c'est `main` qu'il faut analyser, et `develop` qui est le doublon vide.

**Le correctif est couplé**, et c'est ce qui le rend moins trivial qu'il n'en a l'air : trois tests
d'architecture épinglent textuellement `develop` dans les workflows et tomberaient si on retirait la branche
des déclencheurs.

### Et le reste du basculement n'est pas répercuté non plus

C'est `AUD-DEP-24`, `Moyenne`. Dependabot vise toujours `develop` pour ses deux écosystèmes : les mises à
jour de dépendances arriveront sur une branche morte. Sept validateurs comparent par défaut à
`origin/develop`, et **l'un d'eux bascule réellement au rouge** : `validate-r3` refuse toute migration SQLite
dans le delta, or le delta `develop...main` en contient deux. Il échoue donc aujourd'hui pour une raison qui
n'a rien à voir avec ce qu'il vérifie — le meilleur moyen de prendre l'habitude de l'ignorer. Enfin, deux
pages décrivent encore l'ancien modèle de branches, dont la politique de gouvernance.

---

## Les huit confirmés

**`AUD-PRF-02`** — `findPath` n'existe plus nulle part. Une analyse ne déclenche plus que deux traversées par
delta, et chaque chemin est reconstruit en mémoire depuis la carte des prédécesseurs que le BFS construisait
déjà. L'invariant est posé dans le type, pas dans un commentaire : le constructeur compact
d'`ExplainedTraversal` refuse une traversée qui n'expliquerait pas exactement les nœuds atteints. La
propriété du plus court chemin est structurellement préservée et testée sur les deux backends.

**`AUD-PRF-07`** — soldé, et le constat **sous-comptait** : il y avait onze index redondants, pas six. La
migration V022 les supprime tous, et la propriété est désormais tenue par un test **dérivé du schéma**,
collations comprises — ajouter demain un nouvel index redondant le ferait échouer. Le titre du constat est
corrigé.

**`AUD-PRF-10`, `AUD-PRF-11`, `AUD-PRF-12`** — ce sont les trois cas où seul un plan de requête réel tranche,
et je les ai exécutés sur une base construite depuis les migrations du dépôt. Les `USE TEMP B-TREE FOR ORDER
BY` ont disparu, les index sont suivis, et pour `saved_views` l'index devient même couvrant. Point vérifié
séparément parce que c'était le risque du changement de collation : le `COUNT(*)` qui applique le budget par
scope reste servi par un index couvrant.

**`AUD-PRF-13`** — soldé, et la branche prise est la bonne : pour les archives de source, c'est l'`ORDER BY`
SQL qui a été retiré, parce qu'il était **faux** — `archived_at` stocke un `Instant.toString()` dont le texte
ne trie pas par temps quand la fraction varie de longueur. Le test le plus inhabituel du lot est ici : il
garde la *justification* écrite au-dessus de l'instruction, pas seulement le code.

**`AUD-PRF-08`** — soldé sur le coût, et prouvé par comptage plutôt que par durée : `3 + 4 × profondeur`
requêtes système deviennent 4 par répertoire. Mais l'optimisation a rétréci une propriété de sécurité, et
cela sort du périmètre d'un constat de performance — voir `AUD-SEC-17`.

**`AUD-QUA-18`** — les trois gestes que je demandais sont faits : la portée est fermée dans un `finally`,
l'extension `AfterEach` **ferme la portée puis échoue en nommant le test fautif**, et le parallélisme est
fixé à quatre fils, versionné.

---

## Les quatre qui restent partiels

**`AUD-PRF-04`** — la seule des trois tables exposée publiquement est réellement paginée au store, avec un
plan de requête vérifié et, surtout, un test qui prouve que la clé de tri écrite par la migration est celle
que le code écrit ensuite : le piège classique de ce genre de migration est fermé. Mais les deux autres
tables ne sont pas paginées du tout — elles sont seulement interdites d'appel par une règle — et **aucune
politique de rétention n'existe**, alors que l'action la demandait. L'ADR-0116 décide l'inverse : conserver
les journaux pour la vie de la base. Décision défendable et tracée, mais elle borne la taille d'une réponse,
pas la croissance du stockage.

**`AUD-QUA-17`** — le pilote compteur JDBC a bien disparu et les trois tests lisent leur propre recorder.
Mais le remplacement n'est pas équivalent : le pilote voyait *toute* connexion physique à l'exécution ; la
règle qui le remplace est statique, limitée aux constructeurs nommés `Sqlite*Store`, et exempte deux classes.
Surtout, **le comptage est devenu aveugle au défaut** : les compteurs ne sont écrits que par la portée, donc
une connexion ouverte hors portée part sur le runtime du processus et reste invisible du recorder du test.
Les trois tests asserteraient toujours `1`. Trois `DriverManager.getConnection` subsistent d'ailleurs en
production hors de toute portée et hors de la règle.

**`AUD-PRF-19`** — la route de sauvegarde est bien déportée, et c'est bien la seule route que la façade sert
avec SQLite en main : je l'ai vérifié en énumérant les routes. Mais **le serveur remote sert toujours sur un
exécuteur de threads virtuels nu**, sans le décorateur d'admission bornée dont le serveur local dispose. Le
titre du constat portait sur les deux ; seule la route est traitée, et rien ne garde la propriété qui rend
cela suffisant.

**`AUD-PRF-09`** — voir l'erratum.

---

## Erratum : `AUD-PRF-09` affirmait une chose fausse

Le constat disait « ni timeout de lecture **ni budget mémoire** ». La seconde moitié est inexacte : le budget
mémoire couvre bien les écritures — bornes sur `Content-Length` et sur la copie, appliquées sans condition ;
ce qui diffère est le *moment* où le slot est pris, et un test dédié le couvre. Le dépôt l'avait relevé dès
le 13 octobre dans son `tasks.md` ; je n'avais pas corrigé le constat. Le titre est rectifié.

Sur ce qui reste vrai — l'absence de délai amont sur WRITE et ADMIN — **la clôture par décision est
légitime**. La décision est écrite, datée, vérifiable à deux endroits, et le document qui la porte est
**inchangé depuis avant la clôture** : il date de 1.2.1, il n'a pas été écrit après coup pour justifier.
Le raisonnement tient aussi : un `504` mentirait sur un commit peut-être déjà durable. Les dégâts sont
confinés au quart de la capacité, avec refus explicite au-delà et trois jauges pour le voir. Le constat reste
ouvert au barème parce que le défaut existe, mais il n'attend plus un correctif : il attend une réouverture
de la décision.

---

## Les deux autres constats neufs

**`AUD-SEC-17`** (Moyenne) — l'optimisation de `AUD-PRF-08` a rétréci le contrôle *avant lecture*. Une
ancêtre déjà validée puis remplacée par un lien pointant **à l'intérieur** du workspace passe désormais : le
fichier est ouvert, lu et empreinté, puis refusé par la re-validation. L'invariant « rien hors frontière
n'entre dans l'inventaire » tient, et il est testé. L'invariant « aucun contenu n'est jamais lu à travers un
lien » ne tient plus. Le commit est honnête sur la re-validation, qu'il dit inchangée — ce qui est exact —
mais ne dit pas que la vérification avant lecture, elle, a changé de sens.

**`AUD-DEP-25`** (Moyenne) — le produit s'annonce **1.2.1** alors qu'il migre la base en **V022**, quand le
1.2.1 publié s'arrête à V020. Deux programmes portant la même version produisent deux schémas différents :
exactement le scénario que le guide de mise à niveau 1.2.1 décrit comme dangereux. Le dépôt en est conscient
à moitié — il a écrit le guide 1.2.2 et l'annonce « en préparation » — mais n'a pas ouvert la version, et
`UpgradeGuideCoherenceTest`, qui passerait, ne regarde que le guide et le schéma, jamais la version du
réacteur. C'est le chaînon manquant.

---

## Ce que cette passe n'a pas pu établir

Les limites habituelles, plus une nouvelle utile à connaître.

**Aucun build, aucun test Java, aucun gate exécuté** — Maven Central est refusé par le proxy. En revanche,
pour la première fois, les plans de requête ont été **réellement exécutés** : une base construite depuis les
22 migrations du dépôt, et `EXPLAIN QUERY PLAN` lancé sur le texte exact des instructions du code. C'est ce
qui permet de trancher `AUD-PRF-10`, `-11` et `-12` autrement que par lecture. Réserve : SQLite 3.45.1 du
module Python, pas le pilote `sqlite-jdbc` du produit — un planificateur d'une autre version pourrait
choisir autrement, et c'est précisément ce que le garde `SqliteQueryPlanGuardTest` surveille en CI.

La suite Python de `scripts/tests` a tourné : **292 tests, 0 échec, 17 sautés**, inchangé.

**Aucun chiffre de performance n'est vérifié.** Tous ceux du relevé de clôture sont rapportés comme
revendications : `change_analysis_p95_ms=414` — que trois documents du dépôt chiffrent d'ailleurs
différemment, 414, 399 et 404 —, `policy_audit_page_p95_ms=29`, les 5 002 ms puis 13 ms de la sonde remote,
les quatre exécutions propres du module store sur Windows. Et l'API GitHub est refusée, donc l'état réel des
runs nocturnes depuis la bascule n'est pas observable : le raisonnement d'`AUD-DEP-23` est établi sur le
texte des workflows et les refs du dépôt, qui suffisent.

## Méthode

Inchangée. Une case cochée, un message de commit, un ADR ou une ligne du relevé de clôture est une
revendication ; seule la lecture du code établit un fait. Cette fois, le relevé de clôture est globalement
honnête — son auteur écrit lui-même qu'il ne change pas les statuts — mais il **surdit deux fois la preuve** :
il annonce `AUD-QUA-18` « éprouvée par un test qui fuit la portée » alors que ce test a été supprimé après
usage, ce que l'ADR, lui, reconnaît ; et il réduit `AUD-PRF-19` du « serveur » à « la route », ce qui change
la portée du constat. Dans les deux cas, les ADR sont plus exacts que le relevé.

---

# Complément du 18 octobre au soir — sur `29c942ae`

Le relevé ci-dessus a été écrit à `341b08ad`. `develop` a bougé une minute après, et deux verdicts
`Élevée` ont été contre-vérifiés par une lecture indépendante. Ce complément dit ce que ça change.

## `AUD-DEP-23` : le volet aigu s'est éteint tout seul, une minute après

Le constat a été écrit quand `develop` était gelée sur `f7e304d8` et que `develop..main` valait 32 : les
huit voies nocturnes rejouaient bien du code périmé. **La PR #515 a fusionné `main` dans `develop` à
14h53**, soit une minute après le relevé. Mesuré maintenant : `develop..main` = **0**,
`main..develop` = **1** (le commit de fusion), et **les deux arbres sont identiques**. Les voies nocturnes
analysent donc aujourd'hui le bon code.

Le constat n'est pas clos pour autant, et la sévérité passe d'**Élevée à Moyenne**. Ce qui reste est
structurel : les huit voies épinglent toujours une branche qui n'est pas la branche d'intégration, et rien
ne garantit qu'elle la suive. La remise à niveau a été faite à la main ; la prochaine PR fusionnée dans
`main` recreusera l'écart jusqu'à ce que quelqu'un y repense.

Le modèle de branches est établi par lecture, pas supposé : les sept PR du lot (#508 à #514) ont toutes
atterri sur `main`, `origin/HEAD` pointe sur `main`, et la #515 est une fusion de `main` **dans** `develop`.
`main` est la branche d'intégration, `develop` un miroir rattrapé à la main.

**Règle d'usage tant que ce n'est pas corrigé** : un vert nocturne atteste l'état de `develop` au moment du
run, pas celui de `main`. Les deux ne coïncident que si quelqu'un vient de les synchroniser.

## `AUD-DEP-24` : trois volets sur quatre tiennent

Restent vrais : Dependabot vise `develop` pour ses deux écosystèmes, quatorze fichiers de validateurs
comparent par défaut à `origin/develop`, et la documentation enseigne encore `feature → develop → main`.
N'est plus vrai : l'échec de `validate-r3` sur `main` — depuis que `develop` a rattrapé, le diff des
migrations est vide et le gate ne tombe plus.

Le volet Dependabot est le plus coûteux : puisque les fusions vont maintenant de `main` vers `develop`, une
mise à jour de dépendance fusionnée dans `develop` **n'atteindra jamais `main`**. Et c'est silencieux —
rien ne signale une PR fusionnée au mauvais endroit.

## Les deux `Élevée` de Performance, contre-vérifiés

Une seconde lecture indépendante a repris `AUD-PRF-02` et `AUD-PRF-04`. **Les deux verdicts sont
confirmés**, sans divergence. Deux choses méritent d'être ajoutées.

Sur `AUD-PRF-02`, le comptage revendiqué n'est pas seulement exact, il est **recalculable** : 92 lectures de
store avant pour une chaîne de 12, 326 pour 24, contre 14 et 26 après — les chiffres du relevé de clôture,
retrouvés à l'identique. Mais **un changement de contrat observable a été livré sous une étiquette
« perf »** : au budget de nœuds, l'analyse ne lève plus d'exception et rend un résultat tronqué avec
avertissements. Le même `analyze-change` qui plantait rend maintenant une réponse. C'est une amélioration,
elle est dans l'amendement d'ADR-0108, mais elle n'est pas nommée dans le verdict.

Sur `AUD-PRF-04`, le partage n'est pas celui qu'on croit : **la table exposée en HTTP est pleinement
corrigée**, au store, avec index dédié, contrat OpenAPI borné, et un test qui lit le *plan d'exécution* pour
exiger l'index et interdire un tri temporaire. Ce n'est pas un demi-travail. Les deux autres tables ne sont
pas paginées du tout — elles sont mises hors d'atteinte par une règle ArchUnit qui échoue si une classe de
production les appelle. Le risque n'est pas annulé, il est déplacé sur la règle. Et le coût par page reste
linéaire malgré la pagination : le `COUNT(*)` parcourt tout le pack à chaque page, et `OFFSET` saute N
entrées d'index.

## Deux revendications des relevés de clôture ne tiennent pas

Elles ne changent aucun verdict, mais elles touchent la fiabilité du registre lui-même, et c'est le genre
de chose que cet audit suit depuis le premier jour.

`closures-2026-10-17.md:17` écrit que le filet d'`AUD-QUA-18` est « éprouvée par un test qui fuit la
portée ». **Ce test n'existe pas** : aucune classe du dépôt ne référence `NoSqliteScopeLeftBehindExtension`
ni `attachedToThisThread`. Le filet est présent et correctement écrit — le verdict reste « corrigé » — mais
si la ligne `autodetection.enabled=true` disparaissait, il deviendrait du code mort sans que rien ne le
signale. C'est une revendication de preuve sans preuve, dans le document qui sert de registre de clôture.

`closures-2026-10-17.md:24-25` et l'ADR-0115 justifient le maintien en séquentiel de `morpheus-api`,
`morpheus-cli` et `morpheus-mcp` par « `java.home` et `Locale.setDefault` ». **`java.home` n'est posé dans
aucun des trois** — il l'est dans `integration-minos` et `integration-nexus` — et **`morpheus-cli` ne porte
ni l'un ni l'autre** : son maintien en séquentiel n'a aucune justification écrite exacte, et rien n'y
interdit d'activer le parallélisme.

Au passage, le tableau de l'état partagé entre tests de l'ADR-0115, daté du 13 octobre, désigne encore
`UnforeseenFailureTraceTest` comme lisant une différence sur le runtime du processus, alors que ce test a
été réécrit depuis. C'est la seule cartographie de l'état partagé du dépôt, et rien ne la compare au code.

## Notes après correction

Performance **52 → 77**, le plus gros gain d'une passe sur un seul axe. Dépendances 87 → **75** (et non 69 :
la rétrogradation d'`AUD-DEP-23` en rend 6). Tests 69 → 66, Qualité 66 → 63, Sécurité 100 → 96,
Architecture 46 inchangée. **Moyenne 70, inchangée** — le sprint 7 paie exactement ce que les quatre
constats neufs coûtent.

**Il ne reste que trois constats `Élevée`** : `AUD-ARC-01`, `AUD-ARC-02` et `AUD-PRF-04`. Les deux premiers
sont dans le seul axe où rien n'a jamais pu être exécuté.

Aucun sprint n'est plus marqué terminé : les quatre constats neufs rouvrent les sprints 1, 2 et 3, qui
étaient soldés. Ce n'est pas une régression du travail — c'est que le changement de branche a créé des
défauts neufs dans des sprints anciens.

## Correction de modélisation des sprints

Le tableau d'avancement publié plus haut dans la journée disait « aucun sprint n'est terminé ». C'était
faux, et c'est une erreur de ma part, pas un état du dépôt.

Les neuf constats nés **après** le 9 octobre — d'une scission, d'un effet de bord d'un correctif, ou du
changement de branche — avaient été rangés dans les sprints du plan d'origine : `AUD-DEP-23` et
`AUD-DEP-24` en sprint 1, `AUD-DEP-25` en sprint 2, `AUD-SEC-17` en sprint 3, `AUD-QUA-17` et `AUD-QUA-18`
en sprint 4, `AUD-PRF-19` en sprint 5, `AUD-TST-18` en sprint 8, `AUD-ARC-15` en sprint 10. Un constat né
le 18 octobre d'un changement de branche n'est pas « du travail de sprint 1 qui traîne depuis le
9 octobre » — c'est du travail neuf.

Ils sont désormais dans un **sprint 15, « Dérives apparues pendant les correctifs »**. Contrôle fait avant
de les déplacer : aucun d'eux n'est prérequis d'un constat du plan d'origine, l'ordre de correction est donc
intact.

Ce que le plan du 9 octobre dit une fois nettoyé :

| Sprint | État | Reliquat |
|---|---|---|
| 1 | 1 restant | `AUD-DEP-07` — bloqué en amont, rien que le dépôt puisse faire |
| 2 | **terminé** | — |
| 3 | **terminé** | — |
| 4 | **terminé** | — |
| 5 | 1 restant | `AUD-PRF-09` — change OpenSpec ouvert, non archivé |
| 6 | 1 restant | `AUD-PRF-04` — partiel |
| 7 | 1 restant | `AUD-PRF-14` — **jamais commencé** |
| 8 à 14 | non commencés | conformément à l'ordre |

Sur le sprint 7 en particulier, puisque c'est celui qu'on vient de finir : les deux changes archivés du
17 octobre couvrent `AUD-PRF-02`, `PRF-04`, `PRF-05`, `PRF-12` et `PRF-13`, et les autres PR du lot
`PRF-07`, `PRF-08`, `PRF-10` et `PRF-11`. **`AUD-PRF-14` n'est cité dans aucun change OpenSpec** et son
fichier n'a reçu aucun commit depuis l'audit : `ProviderIngestionBudget.read()` ne mémoïse toujours rien,
`utf8Bytes(text)` et `text.lines().count()` reparcourent encore le texte deux fois après le décodage, et les
deux lecteurs OpenSpec énumèrent toujours chacun de leur côté `openspec/changes` en facturant chacun le
budget. C'est un *Faible*, mais c'est le dernier constat du sprint 7, et il n'a pas été commencé.
