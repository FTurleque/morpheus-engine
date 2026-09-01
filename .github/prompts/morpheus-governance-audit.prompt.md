---
mode: agent
description: "Audit de gouvernance complet Morpheus Engine — dépendances, gates ArchUnit, coverage, contrats, version, milestones."
---

# Audit de gouvernance Morpheus Engine

Les règles sont exécutables — exécute-les, ne les devine pas. Miroir du prompt Claude
`.claude/commands/governance.md`.

## Étapes

### 1. Hygiène des dépendances
```bash
./mvnw dependency:analyze
```
`failOnWarning` est actif dans `pom.xml` → **0 warning** exigé.

### 2. Tous les gates d'architecture
```bash
./mvnw test -pl morpheus-architecture-tests
```
Rapporter par milestone actif (lire `.claude/CLAUDE.md` section Milestones pour la liste
courante, elle évolue).

### 3. Coverage — seuils vivants
```bash
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
```
Le gate applique `max(plancher D2 fixe, ratchet qualifié vivant)`. Lire le ratchet actif
dans `config/m21-quality-ratchets.properties`, jamais un pourcentage mémorisé. Lire le
résumé généré : `morpheus-architecture-tests/target/m21-coverage-summary.txt`.

### 4. Convergence des contrats
Vérifier la cohérence entre `contracts/public-surfaces.tsv` et
`docs/openapi/morpheus-v1-*.yaml`. Signaler toute ligne du TSV avec une case vide — chaque
absence doit porter un sentinelle explicite (`EXPLICITLY_NOT_EXPOSED`,
`EXPLICITLY_LOCAL_ONLY`, `EXPLICITLY_REMOTE_ONLY`, `EXPLICITLY_OFFLINE_ONLY`).

### 5. Source de vérité de la version
```bash
grep -rn "0.1.0-SNAPSHOT\|FALLBACK_VERSION" --include="*.java" .
```
Doit être vide sous `src/main/java/`. Lire la version courante dans `pom.xml`
(`<version>`), jamais recopiée de mémoire.

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

DÉPENDANCES     ✅/❌ 0 warning attendu
ARCHITECTURE    ✅/❌ <N constaté> tests / <N> violations
COVERAGE        ✅/❌ <line%> lignes | <branch%> branches (ratchets lus en live)
CONVERGENCE     ✅/❌ <N> capacités, <N> cases vides
VERSION         ✅/❌ <version lue> — ProductMetadata unique
MILESTONES      ✅/❌ <liste constatée> complets
```

Pour chaque ❌ : citer le test exact, la ligne fautive, et la correction minimale. Tous les
chiffres du gabarit ci-dessus sont des emplacements à remplir en live, jamais des valeurs
à recopier depuis ce fichier (cf. `.claude/rules/meta.md`).
