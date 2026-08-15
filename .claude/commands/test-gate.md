# Commande /test-gate

Exécute les tests avec rapport de coverage pour un module ou le projet entier.

## Usage
- `/test-gate` → tests + coverage sur tous les modules
- `/test-gate morpheus-api` → tests + coverage sur `morpheus-api` uniquement
- `/test-gate arch` → tests d'architecture ArchUnit uniquement

## Ce que tu dois faire

### Si $ARGUMENTS = "arch" ou vide avec focus architecture
```bash
./mvnw test -pl morpheus-architecture-tests 2>&1
```
Afficher tous les tests passants/échouants par milestone (m19 à m28).

### Si $ARGUMENTS = un nom de module (ex: "morpheus-api", "api")
```bash
./mvnw test -pl morpheus-api 2>&1
```
Puis analyser le rapport Surefire généré dans `morpheus-api/target/surefire-reports/`.

### Si $ARGUMENTS est vide (build complet)
```bash
./mvnw clean verify 2>&1
```
Rapport complet : tous modules, coverage JaCoCo, gates enforced.

## Analyse du rapport

Pour chaque module testé, afficher :
```
morpheus-api
  Tests:    47 run, 0 failures, 0 errors, 0 skipped
  Coverage: 68% lines (min: 40%) ✅ | 52% branches (min: 35%) ✅
```

Si un test échoue :
1. Afficher le message d'erreur complet
2. Identifier la classe et méthode de test
3. Identifier la classe de production correspondante
4. Proposer une hypothèse de cause basée sur le stack trace

## Floors de coverage (enforced)
- Line coverage : minimum **40%** par module
- Branch coverage : minimum **35%** par module
- Défini dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/CoverageQualityGateTest.java`
