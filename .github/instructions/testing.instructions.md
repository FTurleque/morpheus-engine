---
applyTo: "**/src/test/**,morpheus-architecture-tests/**,scripts/validate-*.ps1,scripts/validate-*.sh,config/*ratchets*.properties"
---

# Tests & Gates

Détail complet, procédures et anatomie d'un gate milestone : `.claude/rules/testing.md`
(source partagée avec Claude Code).

## ⚠️ Coverage : ne jamais citer un seuil de mémoire

**Source de vérité unique et vivante** : `config/m21-quality-ratchets.properties`
(`testsMinimum`, `architectureTestsMinimum`, puis **une paire de clés par échelle de
mesure** : `aggregateLineCoverageMinimum` / `aggregateBranchCoverageMinimum` et
`perModuleLineCoverageMinimum` / `perModuleBranchCoverageMinimum`). Ce projet a déjà eu
trois chiffres différents pour le même ratchet dans trois fichiers de documentation —
relire systématiquement le fichier `.properties` avant toute décision de gouvernance ou
de coverage, jamais cette page ni `.claude/rules/testing.md`.

Deux gates mesurent la couverture sur deux grandeurs différentes et chacun applique
`max(plancher D2 fixe, ratchet qualifié vivant de son échelle)` :
`AggregateCoverageGateTest` lit le rapport canonique `jacoco-aggregate` et les clés
`aggregate*` ; `CoverageQualityGateTest` somme les rapports JaCoCo par module et lit les
clés `perModule*`. Chacun écrit sa propre preuve, dont la première ligne déclare
`coverageScope=`. **Ne jamais comparer un ratio d'une échelle au seuil de l'autre** —
`CoverageScaleSeparationTest` fait échouer le build sur cette confusion.
Les deux échelles portent sur la **même population** — tout module du réacteur qui porte une
classe sous `src/main/java`, outillage de vérification compris — et ne diffèrent que par les
exécutions qui créditent une ligne ; `AggregateCoverageGateTest` refuse un rapport agrégé qui
en mesure une autre.

## TOUJOURS

- Écrire un test de reproduction qui échoue **avant** de corriger un bug
- JUnit 5 (Jupiter) exclusivement — jamais JUnit 4 (`org.junit.Test`, `@RunWith`, `@Rule`)
- Utiliser `morpheus-store-memory` ou `morpheus-provider-synthetic` en test ; jamais
  SQLite en unitaire (mocker SQLite est interdit — utiliser le store mémoire)
- Vérifier la parité de persistance quand un store change : les tests
  `*PersistenceParityTest` exigent un comportement identique `store-memory`/`store-sqlite`
- Construire `morpheus-provider-reference` **avant** les tests d'architecture (M22 charge
  son JAR depuis `target/`)
- Jamais `@Disabled` sans commentaire justificatif + ticket
- Jamais affaiblir un ratchet de coverage ni un budget de performance pour faire passer un
  build — écrire les tests manquants

## Anatomie d'un gate milestone (obligatoire, vérifiée par test)

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/   ← suite ArchUnit
scripts/validate-m<N>.ps1  +  scripts/validate-m<N>.sh                     ← dual-platform
docs/roadmap/M<N>_EXECUTION.md                                             ← plan
docs/validation/VALIDATION_M<N>.md                                         ← preuve
```

## Commandes

```bash
./mvnw clean verify                                              # reactor complet + coverage
./mvnw test -pl morpheus-architecture-tests                      # tous les gates
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest    # échelle par module
./mvnw test -pl morpheus-coverage-report                                      # échelle agrégée
```
