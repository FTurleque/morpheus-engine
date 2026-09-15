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
Rapporter par gate actif — lire la liste courante dans `.claude/CLAUDE.md` (section Milestones)
et dans les répertoires de `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`,
jamais une liste mémorisée : elle évolue.

### 3. Coverage — seuils vivants
```bash
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest    # échelle par module
./mvnw test -pl morpheus-coverage-report                                      # échelle agrégée
```
Chaque gate applique `max(plancher D2 fixe, ratchet qualifié vivant de son échelle)`. Lire les
ratchets actifs dans `config/m21-quality-ratchets.properties` (`aggregate*` pour la mesure
canonique, `perModule*` pour la somme par module), jamais un pourcentage mémorisé, et toujours en
nommant l'échelle du seuil cité.
Lire les résumés générés, dont la première ligne déclare l'échelle mesurée :
`morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt` (`coverageScope=aggregate`,
mesure canonique) et `morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt`
(`coverageScope=per-module`).

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
Doit être vide sous `src/main/java/`. Lire la version courante dans `ProductMetadata` et dans
`pom.xml` (`<version>`), jamais recopiée de mémoire.

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
ARCHITECTURE    ✅ <N constaté> tests / 0 violation
COVERAGE        ✅ <line%> lignes (≥ratchet lu en live) | <branch%> branches (≥ratchet lu en live)
CONVERGENCE     ✅ <N> capacités, 0 case vide
VERSION         ✅ <version lue dans ProductMetadata> — source unique
MILESTONES      ✅ <liste constatée> complets

Gates: <N passants>/<N total>
```

Tous les chiffres de ce gabarit sont des **exemples de format**, jamais des valeurs à
recopier — chaque exécution doit les remplacer par ce qui est réellement lu dans les
sources vivantes (cf. `.claude/rules/meta.md`).

Pour chaque ❌ : citer le test exact, la ligne fautive, et la correction minimale.

Si $ARGUMENTS nomme un module, restreindre l'analyse à ce module.
