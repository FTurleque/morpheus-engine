# Audit complet — MORPHEUS 1.2.1 — `develop`

Statut : **Relevé daté — audit de branche du 10 septembre 2026, révision 5 du 11 septembre (d95549e2)**

**Rév. 5 — 11 septembre 2026.** Rév. 1 à l'aveugle sur `8cff0093` · rév. 2 (A-01, A-02) sur `4387ec37` ·
rév. 3 (A-03, A-04, A-05) sur `8f8193bc` · rév. 4 (A-06, A-07) sur `30adc86c` ·
**rév. 5 (reliquat) sur `d95549e2`** (PR #312, #313, #314, #315).

> **Régime de ce document.** Relevé daté, versé au dépôt le 15 septembre 2026 sans réécriture de son analyse :
> les analyses corrigées et les errata en font partie. Sauf mention d'un autre SHA, tout nombre qu'il cite vaut
> **à `d95549e2`** et n'est pas réputé courant. La provenance de chaque nombre — mesure de l'auditeur ou relevé
> du propriétaire — est établie par la section [Limites de cette vérification](#limites-de-cette-vérification),
> qui est normative.

**Les huit findings sont clos ou requalifiés, et le reliquat l'est aussi.** Les trois décisions que la rév. 4
laissait ouvertes sont tranchées, les deux constats de l'audit du 9 septembre restés sans verdict depuis quatre
révisions sont mesurés et clos, et une dette a été fermée par suppression. Aucun défaut de sécurité exploitable
n'a été trouvé au cours des quatre journées.

Une seule dette nouvelle a été ouverte — **DT-15** — et elle l'a été délibérément, pour empêcher un mandat
implicite de se reformer. C'est le bon usage du [registre](../architecture/risks/register.md).

---

## État final des huit findings

| | Finding | Statut | Clos en |
|---|---|---|---|
| A-01 | Gates d'architecture asservissant du texte | **Clos** — pilote livré (rév. 2) ; **généralisation tranchée** en rév. 5 | rév. 2 · #305 → rév. 5 · #314 |
| A-02 | Fraîcheur du scan de sécurité | **Clos** — clé indexée sur le schéma H2, sentinelle horodatée | rév. 2 · #301 #304 |
| A-03 | Garde du gate de couverture par module | **Clos** — peuplement dérivé du réacteur | rév. 3 · #306 |
| A-04 | Dérive de la couche documentaire normative | **Clos** — les trois items | rév. 3 · #307 |
| A-05 | Trois exécutions de CI par merge | **Requalifié** — dette mesurée et acceptée (DT-13) | rév. 3 · #308 |
| A-06 | Dénominateur agrégé contenant du code non livré | **Clos** — population dérivée ; **consignée en ADR-0104** en rév. 5 | rév. 4 · #309 → rév. 5 · #312 |
| A-07 | Zones hors de toute vérification | **Clos** — 0 lien brisé ; volet workflows → DT-14, **clos en rév. 5** | rév. 4 · #310 → rév. 5 · #313 |
| A-08 | ADR-0103 absent de l'index | **Clos** — et le test qui l'attrape existe | rév. 3 · #307 |

## État du reliquat

| Point ouvert en rév. 4 | Verdict de la rév. 5 | PR |
|---|---|---|
| L'ADR sur la séparation des échelles, due depuis le 9 septembre | **Écrite — [ADR-0104](../adr/0104-two-coverage-scales-share-one-population.md)**, couvrant les deux sujets : la séparation (#297) *et* la population commune (#309) | #312 |
| **DT-14** — workflows historiques, décision du mainteneur | **Tranchée : supprimés**, et le test D2 **remplacé, pas retiré** — plus strict qu'avant | #313 |
| **Le mandat ouvert d'[ADR-0103](../adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md)** — généralisation aux classes restantes | **Tranché par amendement, en trois groupes** : groupe 1 livré, groupe 2 planifié (DT-15), groupe 3 refusé | #314 |
| **F-11 · F-12** — les deux constats du 9 septembre jamais ré-examinés | **Mesurés et clos** ; F-12 coupé en un défaut réel corrigé et une observation sans action | #315 |

Ratchets à l'arrivée, lus dans [`config/m21-quality-ratchets.properties`](../../config/m21-quality-ratchets.properties)
à `d95549e2` : `testsMinimum=1550`, `architectureTestsMinimum=385`, `perModule*=0.620 / 0.535`,
`aggregate*=0.850 / 0.680`. **Aucun ratchet n'a été relevé ni abaissé sur les douze lots** — le fichier de seuils
est identique octet pour octet de `30adc86c` à `d95549e2`. **104 ADR et 104 lignes d'[index](../adr/README.md) à
`d95549e2`.**

---

## Le mandat ADR-0103 — TRANCHÉ — et c'est un refus partiel, ce qui est le bon signe

La rév. 4 disait qu'un mandat laissé ouvert « reste recevable ». L'amendement du 11/09 ne l'a pas simplement
converti en oui : il l'a **découpé par groupe de capacité**, avec un verdict distinct par groupe.

### Le recomptage a produit le fait décisif

Le brief annonçait 31 classes et 192 assertions ; le recomptage sur `a1417cc8` en donne **30 classes / 193
assertions**, écart de définition sans incidence. Mais la répartition, elle, décide :

```text
tests de routeurs Local*HttpRoutesArchitectureTest    13 classes    83 assertions     (a1417cc8)
autres cibles (services, plomberie, bootstraps)       17 classes   110 assertions     (a1417cc8)
```

Et sous ce découpage apparaît ce que le décompte par assertion cachait : **`morpheus-api` déclarait 17 routeurs
pour 13 tests de routeur** — vérifié indépendamment à `d95549e2`, 17 fichiers `*HttpRoutes.java` sous
`src/main/java`, 13 fichiers `*HttpRoutesArchitectureTest.java`. `MorpheusPolicyHttpRoutes`,
`MorpheusPolicyManagementHttpRoutes`, `MorpheusQueryHttpRoutes` et `MorpheusReasoningHttpRoutes` ne portaient
**aucun** des trois interdits. Le sujet n'était donc pas la répétition, c'était la **portée**.

### Groupe 1 — livré, et il confirme ma correction d'A-01 au lieu de l'annuler

[`HttpRoutesFamilyArchitectureTest`](../../morpheus-architecture-tests/src/test/java/com/morpheus/architecture/HttpRoutesFamilyArchitectureTest.java)
énonce trois interdits une fois sur toute la famille : aucun routeur
n'atteint `MorpheusRemote*`, n'écrit la réponse (`MorpheusHttpResponseWriter`), ne parse le chemin
(`MorpheusHttpPathParser`). **Aucune assertion textuelle n'est supprimée** : les 49 assertions par routeur
restent, et quatre méthodes `@Test` s'ajoutent.

Le point le plus intéressant du lot est l'expérience **E2**. La rév. 2 avait établi qu'ArchUnit est aveugle aux
constantes inlinées par `javac`. Ici la famille `MorpheusRemote*` en expose qui sont **publiques**
(`MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS`, quatre de plus dans `MorpheusRemoteIdentityFile`) :
introduire l'une d'elles dans un routeur fait **passer la règle ArchUnit** et n'échouer que le scan textuel.
Pour cette famille, l'angle mort n'est donc pas fermé par accident comme dans le pilote — **il est ouvert**, et
seul le texte le couvre. La coexistence règle + texte n'est plus une précaution, c'est une obligation.

Une quatrième méthode ferme la faille propre aux règles de famille :
`theRulesSeeEveryRouterTheSourcesDeclare` exige que les routeurs vus par ArchUnit soient **exactement** ceux que
déclarent les sources de tous les modules. `failOnEmptyShould` attrape une famille tombée à zéro ; il n'attrape
pas une famille passée de dix-sept à seize. C'est précisément le défaut de garde qu'A-03 avait révélé, reconnu
et évité d'avance.

### Groupe 3 — refusé, et le refus est mieux argumenté que ne l'aurait été un accord

Les 110 assertions des 17 autres classes visent des cibles uniques : sans famille, le coût du pilote ne se
mutualise pas. `RemoteServerArchitectureTest` (19 assertions) garde en plus des frontières de sécurité, où la
mention elle-même est un signal. Le refus se rouvre **sur un fait** — une dépendance réelle qu'une assertion
textuelle a manquée — pas sur l'impression que la famille est répétitive.

### Groupe 2 — DT-15, planifié plutôt que promis

La frontière transport/JSON dépend de la **capacité** du routeur : huit routeurs ne lisent aucun corps de
requête, neuf en lisent un et portent donc légitimement `HttpExchange` et un décodeur. C'est le premier groupe
**substitutif** du chantier, et il corrige en passant le faux positif de `contains("HttpServer")` sur
`MorpheusHttpServer` — le second défaut que j'avais relevé en rév. 2. Il est inscrit en **DT-15** avec ses
conditions d'acceptation, plutôt que laissé en « à faire » dans un ADR.

---

## DT-14 — CLOSE PAR SUPPRESSION — et le test a été remplacé, pas retiré

C'était la question de la rév. 4 : supprimer les quatre workflows historiques oblige à remplacer
`D2RepositoryHardeningArchitectureTest#historicalPreflightsAvoidDeprecatedSetupJavaV4`, qui lisait `m10`, `m11`
et `m12` par leur nom. `rules/governance.md` interdit de faire descendre le compte de méthodes `@Test`.

Les quatre fichiers sont supprimés. Le test est remplacé par
`everyWorkflowAvoidsDeprecatedActionGenerationsAndTheHistoricalPreflightsStayRemoved`
([`D2RepositoryHardeningArchitectureTest`](../../morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java)),
et **la substitution est un gain net** :

| | Avant | Après (`d95549e2`) |
|---|---|---|
| Fichiers soumis à l'interdit | 3, nommés en dur, **jamais exécutés** | **tous** ceux du répertoire, énumérés |
| Assertions d'épinglage | 6 (une par action et par fichier) | **17** — vérifié : 9 usages `checkout` + 8 `setup-java`, tous épinglés Node 24 |
| Portée d'un `checkout` obsolète | invisible hors des trois noms | attrapé dans [`ci.yml`](../../.github/workflows/ci.yml), [`codeql.yml`](../../.github/workflows/codeql.yml), [`nightly.yml`](../../.github/workflows/nightly.yml), [`release.yml`](../../.github/workflows/release.yml), [`security.yml`](../../.github/workflows/security.yml) |
| Retour des fichiers supprimés | — | **refusé par nom** (`m9-validation`, `m10/11/12-preflight`) |
| Épinglage partiel dans un même fichier | passait — un seul match suffisait | **échoue** : `assertEveryUsePinnedNode24` compare usages et épinglages |

Ce dernier point est une correction que personne n'avait demandée : l'ancien helper se contentait d'**un**
match, donc un workflow épinglant une action à jour dans un job et une action obsolète dans un autre le
satisfaisait. L'interdit s'applique désormais à chaque usage.

[`docs/roadmap/DEPLOYMENT.md`](../roadmap/DEPLOYMENT.md) §9 a suivi — il renvoie à `ci.yml` et explique pourquoi
`clean test` ne suffit plus — et il ne restait à `d95549e2` **aucune référence résiduelle** aux quatre fichiers
dans le dépôt, hormis le Javadoc qui documente leur retrait.

---

## F-11 et F-12 — le seul angle mort déclaré de la rév. 4, refermé

### F-11 — aucun changement de forme, et le raisonnement compte plus que le verdict

Le constat disait qu'un ratchet de comptage absolu punit une consolidation légitime. Trois faits le referment,
dans l'ordre de leur force :

**La moitié du scénario n'existe pas sous ce comptage.** `testsMinimum` est relevé dans les **XML Surefire**,
pas dans le source — et Surefire compte **une exécution par invocation paramétrée**. Mesuré sur un projet
témoin avec les versions du dépôt : une classe portant un `@Test` et un `@ParameterizedTest` à trois valeurs
produit `tests="4"` pour deux annotations. *Fusionner des cas en test paramétré ne fait donc pas descendre le
compte* — et le dépôt ne porte d'ailleurs **aucun** `@ParameterizedTest` à cette date (`d95549e2`), ce que j'ai
confirmé.

**La marge a été multipliée par dix.** Relevés du propriétaire à `24bd5a9b` (Windows) : `tests=1674` pour un
plancher de 1550, marge **124 (8,0 %)** ; `architectureTests=422` pour 385, marge **37 (9,6 %)**. Le constat du
9 septembre parlait de +11 et +6.

**Et le garde-fou de vocabulaire vaut d'être noté** : le registre écrit noir sur blanc que
`grep -c '@Test'` compte des *annotations* quand le ratchet compte des *exécutions*, et qu'une marge citée
exige le relevé Surefire. C'est exactement le piège que j'avais failli poser dans le dernier brief ; il est
maintenant inscrit dans le dépôt, pas seulement évité une fois.

### F-12 — coupé en deux, ce qui était la bonne opération

Le finding mélangeait deux choses. Séparées :

**(a) Un défaut réel, corrigé.** `MorpheusAugmentedContextCli$ContextOptions` — le parseur d'options de la
commande `augmented-context` ([`MorpheusAugmentedContextCli.java`](../../morpheus-cli/src/main/java/com/morpheus/cli/MorpheusAugmentedContextCli.java))
— était à **0/49 lignes et 0/32 branches**. C'est le pendant CLI de F-02 : une
validation d'arguments que rien n'atteignait.
[`MorpheusAugmentedContextCliTest`](../../morpheus-cli/src/test/java/com/morpheus/cli/MorpheusAugmentedContextCliTest.java)
(9 tests, vérifié) la porte à
**49/49 et 32/32**, chaque refus asserté par le message que lit l'utilisateur et aucune base créée par un refus.
La classe englobante passe de 24/78 à 78/78 lignes.

**(b) Une observation de taille, sans action.** 17 classes de production dépassaient 400 lignes à `d95549e2` —
vérifié indépendamment, `MorpheusCli` 853, `SqlitePolicyPackStore` 743, `BoundedStdioClientTransport` 570 en tête,
inchangé depuis le 9 septembre. **Aucune n'est touchée**, et la raison est la bonne : aucune ne traverse une
frontière de module, et aucun instrument ne qualifie un seuil de taille — en fabriquer un inventerait une règle
que rien ne mesure. C'est le même refus que le groupe 3 d'ADR-0103, appliqué au même critère.

---

## Ce que douze lots apprennent sur la méthode

### Cinq analyses corrigées par la mesure

| Finding | Ce que j'affirmais | Ce que la mesure a établi | Par quoi |
|---|---|---|---|
| A-02 | Le repli de clé `v12` est le défaut | 12.2.2 et 13.0.0 écrivent le **même schéma 5.6** : le repli était sain, **la clé versionnée était le défaut** | comparaison de DDL |
| A-01 | ArchUnit est « strictement meilleur » | Aucune des deux formes ne domine ; l'angle mort des constantes inlinées est **déjà exercé sur du code livré** | violation exécutée |
| A-03 | 17 rapports attendus ; le correctif cassera `-pl` | **16**, et le contrat de `-pl` ne change pas | `clean verify` réel |
| A-05 | Deux exécutions redondantes ; le saut conditionnel bloque la PR | **C'est le filtre de branche qui bloque** ; le second run porte le **seul** gate de couverture contre `main` | doc GitHub · lecture du `case` |
| A-06 · A-07 | Dénominateur pollué · 294 documents non couverts | **0,15 point** · le nombre ne mesure rien, mais **18 liens sur 561 sont brisés** | rapport JaCoCo · résolution des liens |

Les quatre premières corrections sont venues de l'implémenteur. **La cinquième est venue de moi** — parce que
c'est le seul lot où j'ai mesuré *avant* d'écrire le brief. C'est aussi le seul dont le brief n'a pas eu besoin
d'être corrigé après coup.

### Ce que le reliquat ajoute à la leçon

Le dernier lot n'a corrigé aucune de mes analyses. Il a fait autre chose : **il a recompté mes chiffres et les
a trouvés faux à la marge** — 31 classes annoncées pour 30, 192 assertions pour 193 — puis a montré que le
découpage importait plus que le total. Et c'est ce recomptage qui a fait apparaître le vrai sujet : 17 routeurs
pour 13 tests.

La formule de la rév. 4 était *« produire la mesure avant le finding »*. Elle se complète : **produire la
mesure, puis se demander ce qu'elle regroupe.** Les 193 assertions ne disaient rien ; leur partition en 83/110,
puis en 49/32, a tranché trois questions d'un coup.

### Ce que le dépôt a gagné, au-delà des huit findings

Huit instruments qui n'existaient pas le 9 septembre, tous prouvés en les cassant : la parité d'échec
inter-surfaces (F-02), la sentinelle de fraîcheur (A-02), le peuplement dérivé des deux échelles de couverture
(A-03, A-06), la cohérence de l'index des ADR (A-04, A-08), la résolution des liens Markdown (A-07), les trois
interdits de la famille des routeurs et la bijection règles/sources (rév. 5), et l'épinglage Node 24 étendu à
tous les workflows (rév. 5).

Deux ADR de méthode : **ADR-0103** (quand une règle se vérifie sur le texte, quand sur le bytecode) et
**ADR-0104** (deux échelles de couverture, une population, et ce qu'exige une hausse de plafond).

Et trois chiffres du dépôt corrigés en passant, parce qu'ils décrivaient mal ce qu'ils mesuraient : la lane
Windows annoncée à « ~8 minutes » en vaut 17 (dispersion 12–29), la cadence OWASP annoncée hebdomadaire est
quotidienne, et la marge des ratchets de comptage se lit dans Surefire, pas dans un `grep`.

---

## Deux observations de la rév. 5, aucune bloquante

**1. Les deux gardes de ce lot n'ont pas de preuve résidente.** Quatre gardes installés pendant la campagne
portent leur propre démonstration d'échec dans la suite — [`AdrIndexCoherenceTest`](../../morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdrIndexCoherenceTest.java),
les deux `*CoverageGateTest` ([`CoverageQualityGateTest`](../../morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java),
[`AggregateCoverageGateTest`](../../morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java)),
[`RepositoryDocumentationCoherenceTest`](../../morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryDocumentationCoherenceTest.java)
sur un corpus `@TempDir`. Les deux gardes livrés en
rév. 5 — `HttpRoutesFamilyArchitectureTest` et le remplaçant du test D2 — ont bien été **cassés avant d'être
acceptés** (six exécutions E0–E5 documentées dans l'amendement, chacune avec son résultat), mais cette preuve
vit dans la prose d'un ADR : **elle n'est pas rejouée par la CI**. Pour le test D2 en particulier, un répertoire
de workflows synthétique est la forme exacte que `RepositoryDocumentationCoherenceTest` emploie déjà. Écart de
forme, pas de fond — mais ADR-0103 §4 dit qu'« une garde qui n'a jamais refusé n'est pas une garde », et une
garde dont le refus n'est plus rejoué s'en rapproche avec le temps.

**2. La colonne *Statut* de l'index ne résume plus ADR-0103.** Le fichier porte désormais
« Acceptée — pilote livré ; généralisation décidée le 11/09/2026 » ; la ligne d'index dit toujours
« Acceptée — pilote livré ». `AdrIndexCoherenceTest` n'y voit rien, **et c'est délibéré** : la concordance est
asservie sur le *verdict* (le terme d'ouverture), pour laisser un dossier qualifier son statut plus richement
que l'index ne le résume. Un lecteur qui consulte l'index n'apprend donc pas que le mandat est tranché. Non
détectable par construction, mentionné pour que ce soit un choix et pas un oubli.

---

## Limites de cette vérification

**Cette section est normative.** Elle fixe, pour chaque nombre du document, qui l'a produit. Un lecteur qui
réutilise un chiffre de cet audit commence par le retrouver ici ; la fluidité du texte ci-dessus ne l'autorise pas
à confondre les deux provenances.

- **Mesure de l'auditeur** — produite par l'auditeur lui-même, **statiquement**, sur un clone de
  `origin/develop` : lecture des sources, comptage de fichiers et de littéraux, résolution de chemins. Le texte
  les signale par « vérifié », « vérifié indépendamment » ou « confirmé ».
- **Relevé du propriétaire** — produit côté propriétaire (poste Windows, WSL, GitHub Actions, sessions
  d'implémentation) et rapporté dans une PR, un ADR ou le registre des risques : tout ce qui a demandé un build,
  un test exécuté, un rapport JaCoCo ou Surefire, une violation provoquée, mais aussi les recomptages livrés par
  un lot. L'auditeur l'a **lu**, pas reproduit.

**Tout nombre du document qui ne figure pas dans le premier tableau est un relevé du propriétaire.**

### Conditions de l'audit

- **Aucun build, aucun test exécuté par l'auditeur.** Maven Central reste refusé par la politique de sortie
  réseau (403 sur `repo.maven.apache.org`).
- **L'état GitHub n'est pas observé** : PR, checks requis, exécutions, secrets, ruleset.

### Mesures de l'auditeur

| Constat | Valeur | SHA |
|---|---|---|
| Liens Markdown relatifs, et liens brisés | **563**, **0** brisé | `d95549e2` |
| Liens Markdown relatifs à l'ouverture de l'audit | 561, dont **18** brisés (A-07) | ouverture de l'audit |
| Fichiers ADR et lignes d'index | **104** / **104** | `d95549e2` |
| `config/m21-quality-ratchets.properties` | identique octet pour octet ; valeurs `1550`, `385`, `0.620 / 0.535`, `0.850 / 0.680` | `30adc86c` → `d95549e2` |
| Routeurs `*HttpRoutes.java` et tests `*HttpRoutesArchitectureTest.java` | **17** / **13** | `d95549e2` |
| Usages d'actions épinglés Node 24 | **9** `actions/checkout` + **8** `actions/setup-java` = 17, sur **5** workflows, tous épinglés — donc le test élargi passe | `d95549e2` |
| Les quatre workflows historiques | absents, **sans référence résiduelle** hors Javadoc de retrait | `d95549e2` |
| Test D2 | **remplacé et élargi**, pas supprimé | `d95549e2` |
| `MorpheusAugmentedContextCliTest` | présent, **9** tests | `d95549e2` |
| Classes de production au-dessus de 400 lignes | **17** ; têtes de liste `MorpheusCli` 853, `SqlitePolicyPackStore` 743, `BoundedStdioClientTransport` 570 | `d95549e2` |
| `@ParameterizedTest` dans le dépôt | **0** | `d95549e2` |
| Modules du réacteur, dont portant des sources principales | **18**, dont **16** — ce qui confirme statiquement le `reports=16` du gate par module | `d95549e2` |
| Ascendance | `main` est ancêtre de `develop` | `d95549e2` |

Les chiffres que l'auditeur avait **annoncés puis vus corrigés** — 31 classes et 192 assertions, 17 rapports
attendus (A-03), 294 documents non couverts (A-06 · A-07) — sont des affirmations de l'auditeur, pas des mesures ;
la table *Cinq analyses corrigées par la mesure* les conserve pour cette raison.

### Relevés du propriétaire

| Constat | Valeur | Où il est rapporté |
|---|---|---|
| Recomptage des assertions textuelles | 30 classes / 193 assertions ; 13 classes / 83 et 17 classes / 110 ; partition 49/32 | amendement d'ADR-0103, `a1417cc8` |
| Groupe 1 | 49 assertions par routeur conservées, 4 méthodes `@Test` ajoutées | #314 |
| Exécutions E0–E5, dont E2 (constantes publiques `MorpheusRemote*` inlinées) | résultats par exécution | amendement d'ADR-0103 |
| Groupe 3 | `RemoteServerArchitectureTest` : 19 assertions | amendement d'ADR-0103 |
| Groupe 2 | 8 routeurs sans corps, 9 à corps | amendement d'ADR-0103, DT-15 |
| Test D2 avant remplacement | 3 fichiers, 6 assertions d'épinglage | #313 |
| Comptes Surefire | `tests=1674` (plancher 1550, marge 124 · 8,0 %) ; `architectureTests=422` (plancher 385, marge 37 · 9,6 %) ; marges du 9 septembre +11 / +6 | Windows, `24bd5a9b` |
| Surefire et tests paramétrés | `tests="4"` pour un `@Test` + un `@ParameterizedTest` à trois valeurs | projet témoin, #315 |
| Couverture de `ContextOptions` | 0/49 lignes et 0/32 branches → 49/49 et 32/32 ; classe englobante 24/78 → 78/78 | rapport JaCoCo, #315 |
| A-02 | Dependency-Check 12.2.2 et 13.0.0 : même schéma 5.6 | comparaison de DDL, #301 #304 |
| A-03 | 16 rapports par module | `clean verify` réel, #306 |
| A-06 | effet de l'outillage sur le ratio agrégé : 0,15 point | rapport JaCoCo, #309 ; ADR-0104 |
| Durée de la lane Windows | 17 minutes, dispersion 12–29 | registre des risques, DT-13 |
| Plafond qualifié par module | `PER_MODULE_QUALIFIED_*` 62,5013 % / 53,7997 %, qualifié le 08/09 ; mesure du 09/09 : 63,02–63,06 % de lignes | ADR-0104 |

---

## Ce qu'il reste

Rien qui soit un finding, et plus rien qui soit une décision en attente. Trois choses **suivies**, chacune avec
ses conditions écrites :

1. **DT-15** — groupe 2 d'ADR-0103, la frontière transport/JSON par capacité. Décidé, non livré, une PR dédiée.
2. **DT-13** — la duplication CI sur la PR de promotion. Coût mesuré, accepté, à rouvrir si la cadence de
   `develop` augmente nettement.
3. **Le plafond qualifié par module est daté.** ADR-0104 le dit lui-même : `PER_MODULE_QUALIFIED_*`
   (62,5013 % / 53,7997 %) a été qualifié le 08/09 et la mesure l'a dépassé depuis (63,02–63,06 % de lignes).
   Le requalifier exige **une mesure exact-head sur Windows et sur Linux**, qualifiée sur la plus basse des
   deux. C'est le seul lot restant qui demande les deux plateformes.

Et une observation sans action assumée : les 17 classes au-dessus de 400 lignes. Elle se rouvre pour une raison
indépendante de la taille — un défaut, une couverture manquante, une frontière franchie — jamais sur le nombre
de lignes seul.
