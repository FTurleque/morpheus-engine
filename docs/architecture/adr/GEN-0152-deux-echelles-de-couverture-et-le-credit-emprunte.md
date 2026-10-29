---
id: GEN-0152
title: "Deux échelles de couverture sur une population commune, et le crédit qu'un module emprunte à ses voisins"
domain: general
rules: false
---

# Deux échelles de couverture sur une population commune, et le crédit qu'un module emprunte à ses voisins

- Statut : **Acceptée**
- Date : 2026-10-29
- Remplace : ADR-0104

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
qualifié sur l'autre ; une preuve commune ne dit pas quelle échelle elle porte. Et si la population agrégée résulte des
seules dépendances de `morpheus-coverage-report/pom.xml`, un module retiré de ce POM sort de la mesure sans que le gate
le remarque, et le plafond qualifié borne une population sur laquelle il n'a jamais été mesuré.

Un plafond qualifié est saisi à la main d'après les artefacts de runs CI, qui expirent : sans preuve conservée, un
plafond saisi trop haut passe tous les gates et plus personne ne peut le dire.

**L'écart entre les deux échelles.** Les deux rapports comptent les mêmes lignes et les mêmes branches pour chaque
module, et le couvert agrégé contient le couvert propre. Leur différence, module par module, est ce que la couverture
d'un module doit aux tests qui vivent ailleurs. Mesurée le 29/10/2026 à `6b3f6304`, elle est concentrée sur
`morpheus-application`, dont les suites comportementales vivent dans `morpheus-architecture-tests` : 166 classes du
réacteur n'étaient couvertes que par les tests d'autres modules. L'exprimer comme la différence de deux ratios donnerait
un nombre qui ne nomme aucun module et se lit comme la comparaison d'un ratio par module à un seuil agrégé, que la
séparation interdit. Une valeur consignée sans gate ne retient rien : la couverture empruntée croît sans que rien ne
casse. `morpheus-application` ne peut pas tester avec `morpheus-store-memory` ni `morpheus-provider-synthetic`, qui
dépendent de lui : une dépendance de test dans ce sens est un cycle du réacteur.

## Decision

1. **Une échelle, une paire de clés, un plafond, une preuve qui se nomme.** `perModule*` et `aggregate*` dans
   `config/m21-quality-ratchets.properties` ; aucune clé sans échelle. Chaque gate porte le plafond qualifié
   (`*_QUALIFIED_*_RATIO`) de sa propre échelle, applique `max(plancher D2, ratchet)` et écrit sa preuve avec
   `coverageScope=` en première ligne. Les validateurs ne concluent que sur `coverageScope=aggregate`.
2. **Une population commune, dérivée** : tout module des `<modules>` du POM racine qui porte une classe sous
   `src/main/java`, outillage de vérification compris. Un module absent, étranger ou à rapport périmé (`stale`) est
   refusé en se nommant.
3. **Un plafond se qualifie sur la plus basse mesure exact-head Windows et Linux** de son échelle, gardée dans
   `config/coverage-qualification/` ; le ratchet reste sous lui d'au moins trois fois la dispersion de référence, en
   lignes et en branches séparément.
4. **Le crédit emprunté est une troisième grandeur, pas une échelle.** Pour chaque module : lignes et branches que le
   rapport agrégé couvre et que le rapport propre ne couvre pas. `BorrowedCoverageGateTest` la mesure, l'écrit dans
   `m21-borrowed-coverage-summary.txt` (`coverageScope=borrowed-credit`) et refuse deux rapports qui ne décrivent pas
   les mêmes classes ou un crédit négatif.
5. **Ce crédit ne fait que décroître** : la liste exacte des classes entièrement empruntées, qui ne fait que
   rétrécir, et un maximum du total (`borrowedLineCreditMaximum`, `borrowedBranchCreditMaximum`) qualifié sur la plus
   **haute** mesure des deux plateformes, au plus trois fois la dispersion de référence au-dessus, qui ne fait que
   baisser.
6. **Aucune grandeur n'en gouverne une autre** : aucun seuil n'est dérivé d'un autre, et les deux échelles ne sont
   jamais alignées sur une valeur commune.

## Do's and Don'ts

### Do

- Nommer l'échelle de tout seuil de couverture cité (`aggregate*` ou `perModule*`).
- Déclarer `coverageScope=` en première ligne de toute preuve de couverture écrite.
- Qualifier un plafond sur la plus basse mesure Windows/Linux de son échelle, et remplacer sa preuve dans
  `config/coverage-qualification/` dans le même changement.
- Tester une classe dans le module qui la porte, contre des doubles de ses ports, et retirer son entrée de la liste
  des classes entièrement empruntées dans le même commit.
- Lancer un `./mvnw clean verify` complet avant de conclure sur une échelle ou sur le crédit emprunté.

### Don't

- Ne pas comparer un ratio par module à un seuil agrégé, ni l'inverse, ni publier leur différence comme un seuil.
- Ne pas réintroduire une clé de couverture qui ne nomme pas son échelle.
- Ne pas consommer une preuve de couverture sans portée déclarée en première ligne.
- Ne pas lister la population à la main, ni la modifier sur une seule échelle.
- Ne pas relever un plafond au-dessus du plus bas ratio de sa preuve conservée.
- Ne pas relever le maximum du crédit emprunté pour faire passer un build, ni le dériver d'un ratchet.
- Ne pas ajouter `morpheus-store-memory` ou `morpheus-provider-synthetic` aux tests de `morpheus-application`.
- Ne pas baisser un ratchet pour faire passer un build.

## Consequences

### Positives

- Chaque ratchet réagit à une régression réelle de sa propre grandeur, et chaque plafond garde sa preuve.
- Un module retiré de la mesure, ou un rapport périmé, fait échouer le gate en se nommant.
- Une classe nouvelle couverte seulement par un test HTTP, MCP ou d'architecture fait échouer le build en se nommant,
  avec son module : le remède — un test dans ce module — est dans le message.

### Négatives

- Une mesure exige un `clean verify` complet : chaque gate refuse un réacteur à moitié construit.
- Une requalification de plafond, ou du crédit qualifié, exige des runs sur les deux plateformes.
- Les tests de `morpheus-application` écrivent leurs doubles de ports à la main : ils ne remplacent pas les suites de
  contrat de `morpheus-architecture-tests`, qui continuent de tourner contre les deux stores.

### Risques

- Au plus haut millième admis, la marge d'un ratchet est nulle : la hausse suivante attend une requalification.
- Le maximum du crédit emprunté est un total : un transfert entre modules peut s'y cacher. La preuve nomme chaque
  module ; un maximum par module s'ajoute le jour où le total en cache un.

## Compliance and Enforcement

- `CoverageScaleSeparationTest` (`morpheus-architecture-tests/.../m21/`) : aucun gate ne lit la clé, le rapport ou la
  preuve d'une autre grandeur ; le garde des validateurs refuse une preuve `per-module`, `borrowed-credit`, sans portée
  ou absente.
- `CoverageQualityGateTest` : échelle par module, population dérivée, plancher D2, plafond, refus `stale`.
- `AggregateCoverageGateTest` (`morpheus-coverage-report`) : échelle agrégée, population dérivée et nommée dans la
  preuve, module absent ou étranger refusé, refus `stale`.
- `BorrowedCoverageGateTest` (`morpheus-coverage-report`) : crédit par module, refus d'un réacteur partiel, d'un
  rapport périmé, de deux rapports qui ne comptent pas les mêmes lignes et d'un crédit négatif ; liste exacte des
  classes entièrement empruntées ; maximum entre la mesure qualifiée et trois fois la dispersion de référence au-dessus.
- `CoverageQualificationEvidenceTest` : chaque plafond égal au plus bas ratio de sa preuve conservée, et chaque ratchet
  à au moins trois fois la dispersion sous la plus basse mesure conservée.
- `scripts/lib/Require-AggregateCoverageEvidence.ps1` et `scripts/lib/require-aggregate-coverage-evidence.sh` : garde
  partagé des validateurs sur la portée de la preuve.

## References

- [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) — intégrité de production.
- [ADR-0085](GEN-0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) — déterminisme des gates.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — ce que le cœur applicatif peut toucher.
- `.claude/rules/testing.md`, la skill `coverage-ratchet` et `config/coverage-qualification/`.
- Changement OpenSpec `shrink-the-coverage-a-module-borrows-from-its-neighbours` (constat AUD-TRV-02).
- [VALIDATION_M21](../../project/validation/VALIDATION_M21.md).
