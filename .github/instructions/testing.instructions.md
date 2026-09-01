---
applyTo: "**/src/test/**,morpheus-architecture-tests/**,scripts/validate-*.ps1,scripts/validate-*.sh,config/*ratchets*.properties"
---

# Tests & Gates

Détail complet, procédures et anatomie d'un gate milestone : `.claude/rules/testing.md`
(source partagée avec Claude Code).

## ⚠️ Coverage : ne jamais citer un seuil de mémoire

**Source de vérité unique et vivante** : `config/m21-quality-ratchets.properties`
(`testsMinimum`, `architectureTestsMinimum`, `lineCoverageMinimum`,
`branchCoverageMinimum`). Ce projet a déjà eu trois chiffres différents pour le même
ratchet dans trois fichiers de documentation — relire systématiquement le fichier
`.properties` avant toute décision de gouvernance ou de coverage, jamais cette page ni
`.claude/rules/testing.md`. Le gate `CoverageQualityGateTest` applique
`max(plancher D2 fixe, ratchet qualifié vivant)`.

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
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
```
