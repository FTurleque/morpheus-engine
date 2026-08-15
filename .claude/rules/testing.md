# Règles — Tests & Gates

## ⚠️ Coverage : le vrai seuil est 47% / 40%, pas 40% / 35%

`CoverageQualityGateTest` applique **deux** niveaux :

| Niveau | Line | Branch | Rôle |
|---|---|---|---|
| Plancher D2 | 0.40 | 0.35 | Minimum absolu historique |
| **Ratchet qualifié (actif)** | **0.47** | **0.40** | Baseline réelle mesurée : 47.2781% / 40.4547% |

Le gate applique `max(plancher, ratchet)` = **47% lignes / 40% branches**.

- Un ratchet ne doit **jamais** être affaibli : `MIN_LINE_RATIO >= D2_MIN_LINE_RATIO` est lui-même asserté
- Un ratchet ne doit **jamais** dépasser sa baseline qualifiée
- `D2RepositoryHardeningArchitectureTest` vérifie **textuellement** que `MIN_LINE_RATIO = 0.40d` et
  `MIN_BRANCH_RATIO = 0.35d` sont présents et que les anciennes valeurs `0.25d` / `0.20d` ont disparu
- Le gate exige **≥ 8 rapports JaCoCo** — le reactor complet doit avoir tourné

**Ne jamais baisser un ratchet pour faire passer un build.** Écrire les tests manquants.

## TOUJOURS

- Écrire un test de reproduction qui échoue **avant** de corriger un bug
- JUnit 5 (Jupiter) exclusivement — le projet est sur `junit-bom` 6.1.0
- Utiliser `morpheus-store-memory` ou `morpheus-provider-synthetic` en test ; jamais SQLite en unitaire
- Utiliser les fixtures de `experiments/m0/fixtures/` (`openspec-basic`, `synthetic-basic`)
- Vérifier la parité de persistance quand un store change : les tests `*PersistenceParityTest`
  exigent un comportement identique entre `store-memory` et `store-sqlite`
- Construire `morpheus-provider-reference` **avant** les tests d'architecture — M22 charge son JAR depuis `target/`

## JAMAIS

- Jamais affaiblir un ratchet de coverage ni un budget de performance
- Jamais mocker SQLite — utiliser le store mémoire
- Jamais JUnit 4 (`org.junit.Test`, `@RunWith`, `@Rule`)
- Jamais `@Disabled` sans commentaire justificatif + ticket
- Jamais casser un gate passant (M19–M28, D2) — c'est un bloqueur prioritaire

## Anatomie d'un gate milestone

Chaque milestone livre un quadruplet **obligatoire** (asserté par le test lui-même) :

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/   ← suite ArchUnit
scripts/validate-m<N>.ps1  +  scripts/validate-m<N>.sh                     ← dual-platform
docs/roadmap/M<N>_EXECUTION.md                                             ← plan
docs/validation/VALIDATION_M<N>.md                                         ← preuve
```

Un milestone sans ses 4 artefacts est incomplet — `McpClientIntegrationArchitectureTest#validationAndUserDocumentationArePartOfTheContract` le vérifie explicitement.

## Budgets de performance (M19)

`M19PerformanceGate`, `M19QueryPerformanceGate`, `M19CompositionPerformanceGate`,
`M19TraceabilityPerformanceGate`, `M19FullPublishPerformanceGate` sont des budgets **prédéclarés**
sur fixtures larges déterministes (ADR-0085). Une régression de perf casse le build.

## Commandes

```bash
./mvnw clean verify                                              # reactor complet + coverage
./mvnw test -pl morpheus-architecture-tests                      # tous les gates
./mvnw test -pl morpheus-architecture-tests -Dtest=*M28*         # gate M28
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
```
