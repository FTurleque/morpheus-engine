# Proposition : un seuil de non-régression de couverture pour chaque module

## Why

Le constat `AUD-TRV-02` (Moyenne, sprint 13) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le
2026-11-06 sur `30520bd3`, reste `PARTIEL` pour une seule clause : il n'existe aucun seuil de non-régression **par
module**. `CoverageQualityGateTest.perModuleCoverageDoesNotRegressBelowQualifiedBaseline`
(`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java`, méthode aux
lignes 128 à 179) additionne les compteurs `LINE` et `BRANCH` de tous les rapports `target/site/jacoco/jacoco.xml` dans
deux accumulateurs, puis compare un ratio unique à la paire de clés `perModuleLineCoverageMinimum` /
`perModuleBranchCoverageMinimum` de `config/m21-quality-ratchets.properties`. Un module qui cesse de se tester reste
donc invisible tant que la somme tient : sa perte se dilue dans les lignes couvertes des autres modules. L'échelle par
module dit ce que chaque module couvre seul, mais ne le vérifie qu'en total.

## What Changes

- Le gate de l'échelle par module compare, en plus du ratio total, le ratio de **chaque** module de la population
  dérivée à un seuil propre à ce module, en lignes et en branches, et nomme le module qui passe sous son seuil.
- Les seuils par module vivent dans un fichier de configuration propre à l'échelle par module, une clé ligne et une
  clé branche par module ; le gate refuse un module de la population sans clé et une clé sans module.
- La preuve écrite par le gate de l'échelle par module (`m21-per-module-coverage-summary.txt`) porte, en plus des
  totaux, les compteurs de chaque module ; la preuve conservée sous `config/coverage-qualification/per-module/` les
  porte aussi, et un seuil de module se qualifie sur elle (plus basse mesure des deux plateformes, marge de trois fois
  la dispersion).
- `CoverageScaleSeparationTest` refuse que le gate agrégé ou le gate du crédit emprunté lise ces seuils.
- La paire de clés totale de l'échelle par module et les deux plafonds `PER_MODULE_QUALIFIED_*` restent : un plafond
  borne un cliquet, il n'est pas un seuil.
- Un ADR consolidé qui remplace GEN-0154 est à proposer (sa décision 1 fixe « une paire de clés » par échelle).

## Capabilities

### New Capabilities
- `per-module-coverage-floor` : le seuil de non-régression de couverture propre à chaque module de la population,
  sa déclaration, son application par le gate de l'échelle par module, sa séparation d'avec l'échelle agrégée et le
  sort d'un module ajouté ou retiré du réacteur.

### Modified Capabilities
- `coverage-qualification-evidence` : exigences ajoutées — la preuve conservée de l'échelle par module porte les
  compteurs de chaque module, et le seuil d'un module se qualifie sur cette preuve.

## Périmètre et exclusions

**Inclus :** l'échelle par module (`CoverageQualityGateTest`), son fichier de preuve, sa preuve conservée, le contrôle
de séparation des échelles, la documentation développeur et les règles du dépôt qui décrivent les seuils de couverture.

**Exclus :**
- l'échelle agrégée : aucun seuil par module n'y est ajouté ; ce qu'un module doit aux autres est déjà tenu par le
  crédit emprunté (`coverage-attribution-gap`) ;
- toute valeur de seuil : elle vient d'une mesure sur les deux plateformes, à l'implémentation ;
- les tests à écrire pour faire monter la couverture d'un module (autres clauses d'`AUD-TRV-02`, déjà faites) ;
- la réécriture du titre et de la section `preuve` du constat, qui relèvent de l'audit.

## Acteurs et surfaces concernés

Mainteneur qui modifie un module ou relève un cliquet ; CI (`ci.yml`, Linux et Windows) qui exécute le gate et publie
l'artefact `m21-integrity-<OS>`. Aucune surface publique (CLI, MCP, HTTP) ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le gate somme les rapports et compare un ratio unique à une paire de clés | existant vérifié | `CoverageQualityGateTest.java`, lignes 143 à 178 (deux `Counter`, une boucle sur `reports`) |
| La preuve de l'échelle par module ne porte que des totaux | existant vérifié | `config/coverage-qualification/per-module/*.txt` (`reports`, `lineCovered`, `lineMissed`… sans ligne par module) |
| La population est dérivée du POM racine | existant vérifié | `CoverageQualityGateTest.reportPopulation`, ADR GEN-0154 décision 2 |
| `PER_MODULE_QUALIFIED_*` est un plafond de cliquet, pas un seuil | existant vérifié | `CoverageQualityGateTest.assertRatchetWithinQualifiedWindow` ; skill `coverage-ratchet` |
| Un seuil par module est exigé | exigence | action d'`AUD-TRV-02`, clause 3 ; `reste` de la revérification du 2026-11-06 |
| Fichier de seuils propre à l'échelle par module | décision | `design.md`, D1 |
| Qualification et marge d'un seuil de module | décision | `design.md`, D3 et D4 |
| ADR consolidé remplaçant GEN-0154 | proposition à valider | `design.md`, D7 ; `.claude/rules/adr.md` |

## Impact

- Tests d'architecture : `CoverageQualityGateTest`, `CoverageQualificationEvidenceTest`, `CoverageScaleSeparationTest`
  (`morpheus-architecture-tests/.../m21/`).
- Configuration : un fichier de seuils par module sous `config/` ; la preuve conservée sous
  `config/coverage-qualification/` (format enrichi, requalification à une seule tête des deux échelles).
- Documentation : `docs/developer/specifications/per-module-coverage-floor.md` (à créer) et son index,
  `docs/developer/specifications/coverage-qualification-evidence.md`, `docs/developer/BUILD_AND_TEST.md`,
  `.claude/rules/testing.md`, `.claude/skills/coverage-ratchet/SKILL.md`.
- ADR : GEN-0154 à remplacer par un ADR consolidé (à proposer, acceptation humaine).

## Questions ouvertes

- `[QUESTION]` Amorçage de la preuve par module : requalifier les deux échelles à la tête de la PR qui livre le gate
  (recommandé), ou dériver les lignes par module des artefacts des runs de `d3580dc9` tant qu'ils n'ont pas expiré —
  à trancher par le mainteneur (`design.md`, D5).
