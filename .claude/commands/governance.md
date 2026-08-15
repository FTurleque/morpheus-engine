# Commande /governance

Audit de gouvernance complet. Les règles sont exécutables — exécute-les, ne les devine pas.

## Étapes

### 1. Hygiène des dépendances
```bash
./mvnw dependency:analyze
```
`<failOnWarning>true</failOnWarning>` est actif → **0 warning** exigé.

### 2. Tous les gates d'architecture
```bash
./mvnw test -pl morpheus-architecture-tests
```
Rapporter par milestone : M19 M20 M21 M22 M23 M24 M25 M26 M27 M28 D2 + tests racine.

### 3. Coverage — vrais seuils
```bash
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
```
Seuils appliqués : **47% lignes / 40% branches** (ratchet qualifié), pas 40/35 (plancher D2).
Lire le résumé généré : `morpheus-architecture-tests/target/m21-coverage-summary.txt`.

### 4. Convergence des contrats
Vérifier la cohérence entre :
- `contracts/public-surfaces.tsv`
- `docs/openapi/morpheus-v1-*.yaml`

Signaler toute ligne du TSV avec une case vide — chaque absence doit porter un sentinelle explicite
(`EXPLICITLY_NOT_EXPOSED`, `EXPLICITLY_LOCAL_ONLY`, `EXPLICITLY_REMOTE_ONLY`, `EXPLICITLY_OFFLINE_ONLY`).

### 5. Source de vérité de la version
```bash
grep -rn "0.1.0-SNAPSHOT\|FALLBACK_VERSION" --include="*.java" .
```
Doit être vide sous `src/main/java/`. Version courante : **1.2.1**.

### 6. Complétude des milestones
Pour chaque milestone actif, vérifier le quadruplet :
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/`
- `scripts/validate-m<N>.ps1` **et** `scripts/validate-m<N>.sh`
- `docs/roadmap/M<N>_EXECUTION.md`
- `docs/validation/VALIDATION_M<N>.md`

## Rapport

```
═══════════════════════════════════════════════
  MORPHEUS ENGINE — AUDIT DE GOUVERNANCE
═══════════════════════════════════════════════

DÉPENDANCES     ✅ 0 warning
ARCHITECTURE    ✅ 70 tests / 0 violation
COVERAGE        ✅ 47.3% lignes (≥47%) | 40.5% branches (≥40%)
CONVERGENCE     ✅ N capacités, 0 case vide
VERSION         ✅ 1.2.1 — ProductMetadata unique
MILESTONES      ✅ M19–M28 + D2 complets

Gates: ████████████ 11/11
```

Pour chaque ❌ : citer le test exact, la ligne fautive, et la correction minimale.

Si $ARGUMENTS nomme un module, restreindre l'analyse à ce module.
