---
mode: agent
description: "Exécute les tests et le rapport de coverage Morpheus Engine pour un module ou tout le reactor."
---

# Test gate Morpheus Engine

Miroir du prompt Claude `.claude/commands/test-gate.md`. Utilisation : préciser le module
visé (ex. "morpheus-api"), "arch" pour les tests d'architecture uniquement, ou rien pour
le build complet.

## Si le focus est "architecture"
```bash
./mvnw test -pl morpheus-architecture-tests 2>&1
```
Afficher tous les tests passants/échouants par milestone.

## Si le focus est un module (ex. "morpheus-api")
```bash
./mvnw test -pl morpheus-api 2>&1
```
Puis analyser le rapport Surefire dans `<module>/target/surefire-reports/`.

## Si aucun focus (build complet)
```bash
./mvnw clean verify 2>&1
```
Rapport complet : tous modules, coverage JaCoCo, gates enforced.

## Analyse du rapport

Pour chaque module testé :
```
<module>
  Tests:    <N> run, 0 failures, 0 errors, 0 skipped
  Coverage: <line%> lines (min: <ratchet lu en live>) | <branch%> branches (min: <ratchet lu en live>)
```

Si un test échoue : afficher le message d'erreur complet, identifier la classe/méthode de
test et la classe de production correspondante, proposer une hypothèse de cause basée sur
le stack trace.

## Floors de coverage (enforced)

Le gate applique `max(plancher D2 fixe, ratchet qualifié vivant)`. Le plancher D2 est une
constante fixe dans `CoverageQualityGateTest.java` (`D2_MIN_LINE_RATIO` /
`D2_MIN_BRANCH_RATIO`) ; le ratchet qualifié monte à chaque milestone — lire
`config/m21-quality-ratchets.properties`, jamais un pourcentage mémorisé.

