# Audit MORPHEUS — synthèse exécutive

**Dépôt** `FTurleque/morpheus-engine` · **révision** `20cf2e0` (`main` == `develop`, 0 commit d'écart) · **version produit** 1.2.1 · **date** 9 octobre 2026.

**Périmètre** — 1 753 fichiers suivis, 1 185 `.java` (~152 000 lignes, dont 65 181 en `src/main` et 86 803 en `src/test`), 378 `.md` (~72 700 lignes), 18 modules Maven, 109 ADR, schéma SQLite en version 20. Les six axes ont été couverts avec lecture approfondie du cœur : `api`, `cli`, `mcp`, `store-sqlite`, `application`, `mcp-transport`, `provider-openspec`, `domain`, `provider-sdk`, `architecture-tests`. Le dépôt tient sous le seuil des 2 000 fichiers : aucun module n'a été écarté *a priori*.

**96 constats** : 0 Critique, 11 Élevée, 40 Moyenne, 21 Faible, 24 Info. Les constats sont dans `findings.md` (lisible) et `findings.json` (exploitable). Six ont été produits par axe puis consolidés ; sept sont des fusions inter-axes (`AUD-TRV-*`). La consolidation a été relue par un agent qui n'avait produit aucun constat : **un faux positif a été supprimé, quatre sévérités ont bougé, quatre constats ont été fusionnés, et un constat majeur a été ajouté** — le détail des arbitrages est dans chaque constat concerné.

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
