#!/usr/bin/env bash
# Lit tous les chiffres perissables de MORPHEUS depuis leur source vivante.
# Aucune valeur n'est codee ici : chaque ligne affiche d'ou elle vient.
# Parite exigee avec numbers.ps1 (cf. .claude/rules/governance.md).
set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT" || exit 1

section() { printf '\n== %s\n' "$1"; }
item()    { printf '  %-42s %s\n' "$1" "$2"; }

section "Version produit  <- pom.xml (racine)"
item "pom.xml <version>" "$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"

section "Ratchets  <- config/m21-quality-ratchets.properties"
grep -E '^[a-zA-Z]+=' config/m21-quality-ratchets.properties |
    while IFS='=' read -r key value; do item "$key" "$value"; done

section "Plafonds qualifies  <- les deux gates, une constante par echelle"
grep -hoE '(PER_MODULE|AGGREGATE)_QUALIFIED_(LINE|BRANCH)_RATIO = [0-9.]+d' \
    morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java \
    morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java |
    while read -r line; do item "${line%% =*}" "${line##*= }"; done

section "Preuves de couverture  <- ecrites par le dernier clean verify"
for proof in morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt \
             morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt; do
    if [ -f "$proof" ]; then item "$(basename "$proof")" "$(head -1 "$proof")"
    else item "$(basename "$proof")" "absente - lancer ./mvnw clean verify"; fi
done

section "ADR  <- docs/adr/ (compte, jamais recopie)"
item "records numerotes" "$(ls docs/adr/0*.md 2>/dev/null | wc -l | tr -d ' ')"
item "numero le plus haut attribue" "$(ls docs/adr/0*.md 2>/dev/null | sed 's:.*/::' | cut -c1-4 | sort -n | tail -1)"
item "doublons de numerotation" "$(ls docs/adr/0*.md 2>/dev/null | sed 's:.*/::' | cut -c1-4 | sort | uniq -d | tr '\n' ' ')"

section "Reacteur  <- pom.xml <module>"
item "modules declares" "$(grep -c '<module>' pom.xml)"

section "Manifeste de convergence  <- contracts/public-surfaces.tsv"
item "lignes (en-tete comprise)" "$(wc -l < contracts/public-surfaces.tsv | tr -d ' ')"
grep -o 'EXPLICITLY_[A-Z_]*' contracts/public-surfaces.tsv | sort | uniq -c |
    while read -r count sentinel; do item "$sentinel" "$count"; done

section "Gates d'architecture  <- repertoires reels"
item "suites par milestone" "$(ls -d morpheus-architecture-tests/src/test/java/com/morpheus/architecture/*/ 2>/dev/null | sed 's:.*/\([^/]*\)/$:\1:' | tr '\n' ' ')"

section "Schema SQLite  <- la constante qui le declare"
item "SUPPORTED_SCHEMA_VERSION" \
    "$(grep -oE 'SUPPORTED_SCHEMA_VERSION = [0-9]+' morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java | grep -oE '[0-9]+$')"

section "Versions pinnees  <- proprietes du pom.xml racine"
grep -oE '<(jackson|sqlite-jdbc|junit|archunit|mcp-sdk|slf4j|jacoco|dependency-check\.maven\.plugin)\.version>[^<]+' pom.xml |
    while read -r line; do item "${line%%>*}>" "${line##*>}"; done
