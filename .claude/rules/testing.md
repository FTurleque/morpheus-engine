# Règles — Tests & Gates

## ⚠️ Coverage : ne jamais faire confiance à un chiffre codé en dur ici

**Source de vérité unique et vivante** : `config/m21-quality-ratchets.properties`.
Ce fichier a déjà changé plusieurs fois sans que cette page ne soit mise à jour — trois
sources différentes du repo (`rules/testing.md`, `rules/governance.md`, `docs/README.md`)
citaient chacune un chiffre différent au 31/08/2026. **Avant toute décision de gouvernance
ou de coverage, relire le fichier properties, pas cette page.**

Valeur constatée en lisant `config/m21-quality-ratchets.properties` (02/09/2026) :

| Clé | Valeur constatée |
|---|---|
| `testsMinimum` | 1150 |
| `architectureTestsMinimum` | 310 |
| `lineCoverageMinimum` | 0.540 (54.0%) |
| `branchCoverageMinimum` | 0.470 (47.0%) |

`CoverageQualityGateTest` (`morpheus-architecture-tests/.../m21/`) applique **deux** niveaux :

| Niveau | Line | Branch | Rôle |
|---|---|---|---|
| Plancher D2 | 0.40 | 0.35 | Minimum absolu historique, asserté texto par `D2RepositoryHardeningArchitectureTest` |
| **Ratchet qualifié (actif)** | *voir `config/m21-quality-ratchets.properties`* | *idem* | Baseline mesurée la plus récente |

Le gate applique `max(plancher, ratchet)`.

- Un ratchet ne doit **jamais** être affaibli : `assertTrue(minLineRatio >= D2_MIN_LINE_RATIO, ...)` est lui-même asserté dans `CoverageQualityGateTest`
- Un ratchet ne doit **jamais** dépasser sa baseline qualifiée : `assertTrue(ratchets.lineCoverageMinimum() <= QUALIFIED_LINE_RATIO, ...)` (constatée : `QUALIFIED_LINE_RATIO = 0.545801d`, `QUALIFIED_BRANCH_RATIO = 0.477791d` — à revérifier, cf. `rules/meta.md`)
- `D2RepositoryHardeningArchitectureTest#coverageRatchetCannotSilentlyReturnToTheD2Floor` vérifie que
  `CoverageQualityGateTest.java` **ne contient pas** les chaînes `LINE_RATCHET = 0.40d` / `BRANCH_RATCHET = 0.35d`
  (le ratchet ne doit jamais être recodé en dur à la valeur plancher D2) et lit bien
  `config/m21-quality-ratchets.properties`
- Le gate exige **≥ 8 rapports JaCoCo** — le reactor complet doit avoir tourné

**Ne jamais baisser un ratchet pour faire passer un build.** Écrire les tests manquants.

**Si tu modifies `config/m21-quality-ratchets.properties`** (hausse justifiée par une
preuve reproductible), répercute immédiatement les nouvelles valeurs dans ce fichier,
dans `rules/governance.md` et dans `docs/README.md` — les trois doivent rester identiques.
Voir `rules/meta.md`.

## TOUJOURS

- Écrire un test de reproduction qui échoue **avant** de corriger un bug
- JUnit 5 (Jupiter) exclusivement — le projet est sur `junit-bom` (voir `rules/build.md` pour la version exacte, elle évolue)
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

## Fixtures disponibles

`experiments/m0/fixtures/` contient les jeux de données déterministes utilisés par les tests :
`openspec-basic`, `openspec-partial`, `openspec-state-matrix`, `openspec-unsupported-schema`,
`synthetic-basic`, plus `identity-scenarios.json` pour les cas d'identité scopée par provider.
Réutiliser ces fixtures plutôt qu'en inventer de nouvelles ad hoc — elles sont déjà
référencées par plusieurs suites et servent de baseline de non-régression.

## Commandes

```bash
./mvnw clean verify                                              # reactor complet + coverage
./mvnw test -pl morpheus-architecture-tests                      # tous les gates
./mvnw test -pl morpheus-architecture-tests -Dtest=*M28*         # gate M28
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest
```
