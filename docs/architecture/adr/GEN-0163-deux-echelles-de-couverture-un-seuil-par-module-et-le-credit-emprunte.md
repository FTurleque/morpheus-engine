---
id: GEN-0163
title: "Deux échelles de couverture sur une population commune, un seuil propre à chaque module, et le crédit qu'un module emprunte à ses voisins"
domain: general
rules: false
---

# Deux échelles de couverture sur une population commune, un seuil propre à chaque module, et le crédit qu'un module emprunte à ses voisins

- Statut : **Acceptée**
- Date : 2026-11-06
- Remplace : ADR-0154

## Context

Deux gates mesurent la couverture de MORPHEUS, et ils ne mesurent pas la même grandeur.

| Gate | Rapport lu | Ce qui peut créditer une ligne |
|---|---|---|
| `CoverageQualityGateTest` (`morpheus-architecture-tests/.../m21/`) | le `*/target/site/jacoco/jacoco.xml` de chaque module, module des tests d'architecture exclu | les tests **du module qui porte la ligne**, et eux seuls |
| `AggregateCoverageGateTest` (`morpheus-coverage-report`) | `target/site/jacoco-aggregate/jacoco.xml`, la mesure canonique | **toute** exécution du réacteur, y compris l'exécution inter-modules des tests d'architecture |

Un service de `morpheus-application` exercé seulement par les tests de `morpheus-api` compte comme non couvert sur la
première échelle et comme couvert sur la seconde. Les deux lectures sont justes : elles répondent à *ce que chaque
module couvre par lui-même* et à *ce que le produit couvre*.

Plusieurs défauts menacent cette paire. Une clé de seuil commune aux deux gates fait gouverner une grandeur par un
seuil qualifié sur l'autre ; une preuve commune ne dit pas quelle échelle elle porte. Si la population agrégée résulte
des seules dépendances de `morpheus-coverage-report/pom.xml`, un module retiré de ce POM sort de la mesure sans que le
gate le remarque, et le plafond qualifié borne une population sur laquelle il n'a jamais été mesuré. Un plafond
qualifié est saisi à la main d'après les artefacts de runs CI, qui expirent : sans preuve conservée, un plafond saisi
trop haut passe tous les gates et plus personne ne peut le dire.

**Une somme dilue le module qui cesse de se tester.** L'échelle par module dit ce que chaque module couvre seul, mais
un gate qui somme les rapports et compare un ratio total à une paire de clés ne le vérifie qu'en total : un module dont
la couverture s'effondre reste invisible tant que les lignes couvertes des autres compensent (constat AUD-TRV-02 de
l'audit du 2026-10-09). Trois options existent : le total seul, qui laisse ce trou ; des seuils dérivés de la preuve
sans clé, qu'une requalification faite après la régression d'un module baisserait sans que personne ne le décide ; un
seuil déclaré par module, retenu. Un seuil en nombre de lignes échouerait quand du code couvert est retiré et ne se
compare pas aux ratchets : le seuil est un ratio. Des clés par module dans `config/m21-quality-ratchets.properties`
seraient confondues, par simple recherche de texte, avec la clé totale, et ce fichier est lu par les validateurs : les
seuils vivent dans un fichier propre à l'échelle par module.

**L'écart entre les deux échelles.** Les deux rapports comptent les mêmes lignes et les mêmes branches pour chaque
module, et le couvert agrégé contient le couvert propre. Leur différence, module par module, est ce que la couverture
d'un module doit aux tests qui vivent ailleurs. Mesurée le 29/10/2026 à `6b3f6304`, elle est concentrée sur
`morpheus-application`, dont les suites comportementales vivent dans `morpheus-architecture-tests` : 166 classes du
réacteur n'étaient couvertes que par les tests d'autres modules. L'exprimer comme la différence de deux ratios donnerait
un nombre qui ne nomme aucun module et se lit comme la comparaison d'un ratio par module à un seuil agrégé, que la
séparation interdit. `morpheus-application` ne peut pas tester avec `morpheus-store-memory` ni
`morpheus-provider-synthetic`, qui dépendent de lui : une dépendance de test dans ce sens est un cycle du réacteur.

## Decision

1. **Une échelle, ses clés, un plafond, une preuve qui se nomme.** `perModule*` et `aggregate*` dans
   `config/m21-quality-ratchets.properties` ; aucune clé sans échelle. Chaque gate porte le plafond qualifié
   (`*_QUALIFIED_*_RATIO`) de sa propre échelle, applique `max(plancher D2, ratchet)` et écrit sa preuve avec
   `coverageScope=` en première ligne. Les validateurs ne concluent que sur `coverageScope=aggregate`.
2. **Un seuil par module, sur l'échelle par module seulement.** `config/m21-per-module-coverage-floors.properties`
   déclare `<module>.lineCoverageMinimum` et `<module>.branchCoverageMinimum` pour chaque module de la population, ni
   plus ni moins. Le gate compare le rapport propre de chaque module à `max(plancher D2, seuil)`, à côté du total, et
   écrit les compteurs de chaque module dans sa preuve.
3. **Une population commune, dérivée** : tout module des `<modules>` du POM racine qui porte une classe sous
   `src/main/java`, outillage de vérification compris. Un module absent, étranger ou à rapport périmé est refusé en se
   nommant.
4. **Un plafond ou un seuil se qualifie sur la plus basse mesure exact-head Windows et Linux**, gardée dans
   `config/coverage-qualification/`, les deux échelles à une seule tête. Le ratchet reste sous son plafond, et le seuil
   d'un module sous la plus basse mesure de ce module, d'au moins trois fois la dispersion (celle des runs gardés ou la
   référence, la plus large), en lignes et en branches séparément. Un module que la preuve ne porte pas ne déclare que
   le plancher D2.
5. **Le crédit emprunté est une troisième grandeur, pas une échelle**, mesurée par module par
   `BorrowedCoverageGateTest`, qui ne fait que décroître : liste exacte des classes entièrement empruntées et maximum
   du total, qualifié sur la plus **haute** mesure.
6. **Aucune grandeur n'en gouverne une autre** : aucun seuil n'est dérivé d'un autre, et rien n'aligne deux échelles.

## Do's and Don'ts

### Do

- Nommer l'échelle de tout seuil de couverture cité (`aggregate*`, `perModule*` ou le seuil d'un module).
- Déclarer `coverageScope=` en première ligne de toute preuve de couverture écrite.
- Déclarer les deux seuils d'un module au plancher D2 dans le changement qui l'ajoute au réacteur, et les retirer dans
  celui qui le retire.
- Qualifier un plafond ou un seuil sur la plus basse mesure Windows/Linux, et remplacer la preuve des deux échelles
  dans `config/coverage-qualification/` dans le même changement.
- Tester une classe dans le module qui la porte, contre des doubles de ses ports.
- Lancer un `./mvnw clean verify` complet avant de conclure sur une échelle ou sur le crédit emprunté.

### Don't

- Ne pas comparer un ratio par module à un seuil agrégé, ni l'inverse, ni publier leur différence comme un seuil.
- Ne pas faire lire les seuils par module au gate agrégé ni au gate du crédit emprunté.
- Ne pas réintroduire une clé de couverture qui ne nomme pas son échelle.
- Ne pas lister la population à la main, ni appliquer un seuil par défaut à un module sans clé.
- Ne pas relever un plafond ou un seuil au-delà de sa preuve conservée.
- Ne pas baisser un ratchet ou un seuil pour faire passer un build, ni relever le maximum du crédit emprunté.
- Ne pas ajouter `morpheus-store-memory` ou `morpheus-provider-synthetic` aux tests de `morpheus-application`.

## Consequences

### Positives

- Chaque ratchet réagit à une régression réelle de sa propre grandeur, et chaque plafond garde sa preuve.
- Un module qui cesse de se tester fait échouer le build sous son nom, avec le remède : un test dans ce module.
- Un module retiré de la mesure, ou un rapport périmé, fait échouer le gate en se nommant.

### Négatives

- Une mesure exige un `clean verify` complet : chaque gate refuse un réacteur à moitié construit.
- Une requalification exige des runs sur les deux plateformes ; ajouter un module exige deux lignes de configuration.
- Les tests de `morpheus-application` écrivent leurs doubles de ports à la main.

### Risques

- Au plus haut millième admis, la marge d'un ratchet ou d'un seuil est nulle : la hausse suivante attend une
  requalification.
- La dispersion de référence est celle de l'échelle : pour un petit module, la marge est large en points, et son seuil
  attrape un module qui cesse de se tester plutôt qu'une baisse fine.
- Rien dans le build n'interdit de baisser un seuil dans sa fenêtre : la baisse se voit dans le diff d'un fichier de
  gouvernance et se justifie dans la PR.

## Compliance and Enforcement

- `CoverageScaleSeparationTest` (`morpheus-architecture-tests/.../m21/`) : aucun gate ne lit la clé, le rapport, la
  preuve ou le fichier de seuils d'une autre grandeur ; le garde des validateurs refuse une preuve `per-module`,
  `borrowed-credit`, sans portée ou absente.
- `CoverageQualityGateTest` : échelle par module, population dérivée, plancher D2, plafond, refus `stale`, seuil de
  chaque module, fichier de seuils égal à la population.
- `AggregateCoverageGateTest` (`morpheus-coverage-report`) : échelle agrégée, population dérivée et nommée dans la
  preuve, module absent ou étranger refusé, refus `stale`.
- `BorrowedCoverageGateTest` (`morpheus-coverage-report`) : crédit par module, liste exacte des classes entièrement
  empruntées, maximum entre la mesure qualifiée et trois fois la dispersion de référence au-dessus.
- `CoverageQualificationEvidenceTest` : chaque plafond égal au plus bas ratio de sa preuve conservée, chaque ratchet et
  chaque seuil de module dans sa fenêtre, compteurs de chaque module complets et égaux aux totaux.
- `scripts/lib/Require-AggregateCoverageEvidence.ps1` et `scripts/lib/require-aggregate-coverage-evidence.sh` : garde
  partagé des validateurs sur la portée de la preuve.

## References

- [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) — intégrité de production.
- [ADR-0085](GEN-0085-predeclared-performance-budgets-and-deterministic-large-fixtures.md) — déterminisme des gates.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — ce que le cœur applicatif peut toucher.
- `.claude/rules/testing.md`, la skill `coverage-ratchet` et `config/coverage-qualification/`.
- Changements OpenSpec `shrink-the-coverage-a-module-borrows-from-its-neighbours` et
  `thresh-every-module-coverage-on-its-own-floor` (constat AUD-TRV-02).
- [VALIDATION_M21](../../project/validation/VALIDATION_M21.md).
