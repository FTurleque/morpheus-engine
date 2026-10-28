---
id: GEN-0104
title: "Deux échelles de couverture, une population commune : elles ne diffèrent que par l'attribution"
domain: general
rules: false
---

# Deux échelles de couverture, une population commune : elles ne diffèrent que par l'attribution

- Statut : **Acceptée**
- Date : 2026-09-11

## Context

Deux gates mesurent la couverture de MORPHEUS, et ils ne mesurent pas la même grandeur.

| Gate | Rapport lu | Ce qui peut créditer une ligne |
|---|---|---|
| `CoverageQualityGateTest` (`morpheus-architecture-tests/.../m21/`) | somme des `*/target/site/jacoco/jacoco.xml`, module des tests d'architecture exclu | les tests **du module qui porte la ligne**, et eux seuls |
| `AggregateCoverageGateTest` (`morpheus-coverage-report`) | `target/site/jacoco-aggregate/jacoco.xml`, la mesure canonique | **toute** exécution du réacteur, y compris l'exécution inter-modules des tests d'architecture |

Un service de `morpheus-application` exercé seulement par les tests de `morpheus-api` compte comme non couvert sur la
première échelle et comme couvert sur la seconde. Les deux lectures sont justes : elles répondent à *ce que chaque
module couvre par lui-même* et à *ce que le produit couvre*.

Deux défauts menacent cette paire. Une clé de seuil commune aux deux gates fait gouverner une grandeur par un seuil
qualifié sur l'autre, et laisse sur l'échelle agrégée une marge morte où une large part de la couverture peut
disparaître sans qu'aucun gate ne réagisse ; une preuve commune ne dit pas quelle échelle elle porte. Et deux gates à
deux seuils invitent à croire qu'ils mesurent deux populations de modules : si la population agrégée résulte des
seules dépendances de `morpheus-coverage-report/pom.xml`, un module retiré de ce POM sort de la mesure sans que le
gate le remarque, et le plafond qualifié continue de borner une population sur laquelle il n'a jamais été mesuré.

Un plafond qualifié est saisi à la main d'après les artefacts de runs CI, qui expirent : sans preuve conservée, un
plafond saisi trop haut passe tous les gates et plus personne ne peut le dire.

## Decision

### 1. Une échelle, une paire de clés, un plafond qualifié, une preuve qui se nomme

- `config/m21-quality-ratchets.properties` déclare `perModuleLineCoverageMinimum` / `perModuleBranchCoverageMinimum`
  et `aggregateLineCoverageMinimum` / `aggregateBranchCoverageMinimum`. Aucune clé indépendante de l'échelle :
  `lineCoverageMinimum` et `branchCoverageMinimum` sont refusées.
- Chaque gate porte le plafond qualifié **de sa propre échelle** : `PER_MODULE_QUALIFIED_*_RATIO` dans
  `CoverageQualityGateTest`, `AGGREGATE_QUALIFIED_*_RATIO` dans `AggregateCoverageGateTest`. Le gate applique
  `max(plancher D2, ratchet)` et refuse un ratchet au-dessus de son plafond comme un ratchet ramené au plancher.
- Chaque gate écrit sa propre preuve et y déclare son échelle **en première ligne** :
  `m21-per-module-coverage-summary.txt` (`coverageScope=per-module`) et `m21-aggregate-coverage-summary.txt`
  (`coverageScope=aggregate`). Les validateurs `validate-m21.*`, `validate-d2.*` et `validate-m22.*` à
  `validate-m27.*` lisent la preuve agrégée et refusent de conclure sur une preuve d'une autre échelle, sans portée
  déclarée, ou absente.

### 2. Les deux échelles portent sur la même population, dérivée du réacteur

La population est : **tout module déclaré dans les `<modules>` du POM racine qui porte au moins une classe sous
`src/main/java`**. Elle est dérivée, jamais listée à la main.

- `CoverageQualityGateTest` exige le `jacoco.xml` de chacun de ces modules et nomme ceux qui manquent.
- `AggregateCoverageGateTest` dérive la même population, refuse un rapport agrégé dont les groupes de modules
  diffèrent en nommant le module absent ou étranger, et écrit `populationRule=`, `populationModules=` et `population=`
  dans sa preuve.
- Les modules sans sources principales (`morpheus-architecture-tests`, `morpheus-coverage-report`) sont expliqués par
  la règle, pas exclus par leur nom. Déclarer un module le rend obligatoire du même geste.

Les deux échelles ne diffèrent donc que par **les exécutions autorisées à créditer une ligne**. Séparer les échelles
et leur donner une population commune sont les deux moitiés d'une même décision : la première empêche un seuil de
gouverner la mauvaise grandeur, la seconde empêche de croire que deux grandeurs parlent de deux produits.

### 3. L'outillage de vérification reste dans la population

`morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit` et `morpheus-provider-reference`
portent des classes principales ; ils sont dans la population, délibérément :

1. Aucune décision de gate n'en dépend : l'outillage est couvert au-dessus de la moyenne, et son retrait déplace le
   ratio agrégé de moins d'un point sans qu'aucun seuil ne bascule.
2. Le plafond agrégé est qualifié avec l'outillage dedans : le retirer imposerait une requalification Windows + Linux,
   ou laisserait un plafond sans preuve.
3. Les deux échelles différeraient aussi par la population, et la décision 2 perdrait sa raison d'être.

Retirer l'outillage est un changement de population : il exige une requalification des deux plateformes et une
révision de cet ADR, jamais un réglage de `morpheus-coverage-report/pom.xml`.

### 4. Un rapport plus ancien que le code est refusé

Les deux gates refusent, sous la cause nommée `stale`, un rapport plus ancien que le fichier le plus récent de
`src/main/java` ou de `src/test/java` des modules qu'il mesure (pour l'échelle agrégée : les modules de la population,
et `morpheus-architecture-tests`, dont l'exécution inter-modules crédite ce rapport). La comparaison est stricte. La
référence n'est pas le répertoire `target/classes` : l'heure d'un répertoire ne change que lorsqu'une entrée y est
créée ou supprimée, jamais quand un fichier plus bas est réécrit. Les tests posent les dates explicitement et
n'attendent jamais l'horloge du système de fichiers.

### 5. Ce qu'une hausse de plafond exige

Relever un `*_QUALIFIED_*_RATIO` exige une **mesure exact-head de cette échelle sur Windows et sur Linux**, citée dans
le commentaire de la constante, et qualifiée sur **la plus basse** des mesures, jamais la meilleure : les deux
plateformes exécutent le même nombre de tests, mais certains no-opent hors de leur OS, donc Linux couvre légèrement
moins de lignes à nombre de tests identique.

La preuve de la qualification en vigueur est gardée avec le code, dans `config/coverage-qualification/`, sous la
disposition et selon la procédure de dépôt que décrit la skill `coverage-ratchet`
(`.claude/skills/coverage-ratchet/SKILL.md`). Un plafond est **égal au plus bas ratio de son échelle** dans ces
fichiers, à la précision des résumés. Une qualification est complète
ou refusée : les deux plateformes, au moins deux runs chacune, un seul commit, une seule population (et, sur l'échelle
agrégée, la liste des modules que le résumé nomme). Un résumé d'une autre échelle, un ratio qui contredit ses propres
compteurs, un fichier absent du manifeste ou un fichier du manifeste absent sont refusés. Seule la qualification en
vigueur est gardée : une requalification remplace l'ensemble de l'échelle et sa constante dans le même changement, et
Git garde l'ensemble précédent.

Le ratchet se place **sous** le plafond, avec une marge d'au moins trois fois la dispersion maximale observée entre
plateformes, comptée séparément en lignes et en branches : un ratchet collé à la mesure transformerait une variation
ordinaire en échec de build. La dispersion retenue est la plus grande de celle des runs conservés et de la référence
du 15/09/2026 (24 lignes, 11 branches) ; une hausse au-delà passe par une requalification.

Une session qui ne dispose que d'une plateforme peut relever un ratchet dans la marge déjà qualifiée ; elle ne touche
pas au plafond. Une hausse ne concerne que l'échelle réellement mesurée : aligner les deux sur une valeur moyenne
referait sous un autre nom le défaut que la séparation corrige.

### 6. Ce que cet ADR ne décide pas

Les valeurs. Les ratchets vivent dans `config/m21-quality-ratchets.properties`, les plafonds dans les constantes des
deux gates et leur preuve dans `config/coverage-qualification/`. Cet ADR fixe **comment** une valeur est qualifiée et
à quelle échelle elle s'applique, pas laquelle.

## Do's and Don'ts

### Do

- Nommer l'échelle de tout seuil de couverture cité (`aggregate*` ou `perModule*`).
- Déclarer `coverageScope=` en première ligne de toute preuve de couverture écrite.
- Qualifier un plafond sur la plus basse mesure Windows/Linux de son échelle, et remplacer sa preuve dans
  `config/coverage-qualification/` dans le même changement.
- Lancer un `./mvnw clean verify` complet avant de conclure sur une échelle.

### Don't

- Ne pas comparer un ratio par module à un seuil agrégé, ni l'inverse.
- Ne pas réintroduire une clé de couverture qui ne nomme pas son échelle.
- Ne pas consommer une preuve de couverture sans portée déclarée en première ligne.
- Ne pas lister la population à la main, ni la modifier sur une seule échelle.
- Ne pas relever un plafond au-dessus du plus bas ratio de sa preuve conservée.
- Ne pas aligner les deux échelles sur une valeur moyenne.
- Ne pas baisser un ratchet pour faire passer un build.

## Consequences

### Positives

- Chaque ratchet réagit à une régression réelle de sa propre grandeur.
- Chaque plafond est prouvé aux deux bornes, sur chaque échelle, et sa preuve survit à l'expiration des artefacts CI.
- Un module retiré de la mesure, ou un rapport périmé, fait échouer le gate en se nommant.

### Négatives

- Une mesure plus exigeante coûte un `clean verify` complet : le gate par module refuse un réacteur à moitié construit,
  le gate agrégé refuse un rapport qui ne mesure pas toute la population, et les deux refusent un rapport périmé.
- Une requalification de plafond exige des runs sur les deux plateformes.

### Risques

- Au plus haut millième admis, la marge restante est nulle : la hausse suivante attend une requalification, que seuls
  les runners CI des deux plateformes fournissent.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageScaleSeparationTest.java` : aucun
  gate ne lit la clé, le rapport ou la preuve de l'autre échelle ; le garde des validateurs refuse une preuve
  `per-module`, une preuve sans portée et une preuve absente.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java` : échelle par
  module, population dérivée, plancher D2, plafond, refus `stale`.
- `morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java` : échelle agrégée,
  population dérivée et nommée dans la preuve, module absent ou étranger refusé, refus `stale`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualificationEvidenceTest.java` :
  chaque plafond égal au plus bas ratio de sa preuve conservée, qualification complète ou refusée, et chaque ratchet à
  au moins trois fois la dispersion sous la plus basse mesure conservée, en lignes et en branches.
- `scripts/lib/Require-AggregateCoverageEvidence.ps1` et `scripts/lib/require-aggregate-coverage-evidence.sh` : garde
  partagé des validateurs sur la portée de la preuve.

## References

- [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) — intégrité de production.
- [ADR-0085](GEN-0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) — déterminisme des gates.
- `.claude/rules/architecture.md` (« Règle ArchUnit ou assertion textuelle ») et la skill `enforcement-choice` — assertion
  textuelle et règle ArchUnit, et l'exigence de casser une règle avant de l'accepter.
- `config/m21-quality-ratchets.properties`, `config/coverage-qualification/`, `.claude/rules/testing.md`.
- [VALIDATION_M21](../../project/validation/VALIDATION_M21.md).
