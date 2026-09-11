# Règles — Tests & Gates

## ⚠️ Coverage : ne jamais faire confiance à un chiffre codé en dur ici

**Source de vérité unique et vivante** : `config/m21-quality-ratchets.properties`.
Ce fichier a déjà changé plusieurs fois sans que cette page ne soit mise à jour — trois
sources différentes du repo (`rules/testing.md`, `rules/governance.md`, `docs/README.md`)
citaient chacune un chiffre différent au 31/08/2026. **Avant toute décision de gouvernance
ou de coverage, relire le fichier properties, pas cette page.**

Valeur constatée en lisant `config/m21-quality-ratchets.properties` (08/09/2026) :

| Clé | Valeur constatée |
|---|---|
| `testsMinimum` | 1550 |
| `architectureTestsMinimum` | 385 |
| `aggregateLineCoverageMinimum` | 0.850 (85.0%) |
| `aggregateBranchCoverageMinimum` | 0.680 (68.0%) |
| `perModuleLineCoverageMinimum` | 0.620 (62.0%) |
| `perModuleBranchCoverageMinimum` | 0.535 (53.5%) |

## Deux échelles de couverture, deux jeux de seuils

Deux gates mesurent la couverture et ne mesurent **pas la même grandeur**. Jusqu'au 09/09/2026 ils lisaient les deux mêmes clés et écrivaient le même fichier de preuve, donc un seuil qualifié sur une échelle gouvernait l'autre :

| Gate | Rapport lu | Clés | Preuve écrite |
|---|---|---|---|
| `AggregateCoverageGateTest` (`morpheus-coverage-report`) | `jacoco-aggregate` (mesure canonique, fusionne l'exécution cross-module des tests d'architecture) | `aggregate*` | `target/m21-aggregate-coverage-summary.txt` (`coverageScope=aggregate`) |
| `CoverageQualityGateTest` (`morpheus-architecture-tests/.../m21/`) | somme des `*/target/site/jacoco/jacoco.xml`, module de tests d'architecture exclu | `perModule*` | `target/m21-per-module-coverage-summary.txt` (`coverageScope=per-module`) |

`CoverageScaleSeparationTest` fait échouer le build si un gate lit une clé, un rapport ou une preuve de l'autre échelle, et prouve que les validateurs `validate-m21.*` refusent une preuve dont la première ligne n'est pas `coverageScope=aggregate`. **Ne jamais comparer un ratio par module à un seuil agrégé, ni l'inverse.**

**Même population, attribution différente.** Les deux échelles portent sur les mêmes modules — tout module du réacteur qui porte une classe sous `src/main/java`, **outillage de vérification compris** (`morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit`, `morpheus-provider-reference`) — et ne diffèrent que par les exécutions autorisées à créditer une ligne. `AggregateCoverageGateTest` dérive cette population du POM racine, refuse un rapport agrégé qui en mesure une autre, et la nomme dans sa preuve (`population=`). Garder l'outillage dans le dénominateur est un choix : mesuré le 11/09/2026, il déplace le ratio agrégé de 0,15 point, et l'en retirer laisserait `AGGREGATE_QUALIFIED_*` plafonner une population sur laquelle il n'a jamais été mesuré.

La décision et ses raisons — séparation des échelles, population commune, maintien de l'outillage, exigences d'une hausse de plafond — sont consignées dans **ADR-0104** (`docs/adr/0104-two-coverage-scales-share-one-population.md`). La lire avant de toucher à une clé, à un plafond qualifié ou à la population.

Chaque gate applique **deux** niveaux :

| Niveau | Line | Branch | Rôle |
|---|---|---|---|
| Plancher D2 | 0.40 | 0.35 | Minimum absolu historique, asserté texto par `D2RepositoryHardeningArchitectureTest` |
| **Ratchet qualifié (actif)** | *voir `config/m21-quality-ratchets.properties`* | *idem* | Baseline mesurée la plus récente |

Le gate applique `max(plancher, ratchet)`.

- Un ratchet ne doit **jamais** être affaibli : `assertTrue(minLineRatio >= D2_MIN_LINE_RATIO, ...)` est lui-même asserté dans `CoverageQualityGateTest`
- Un ratchet ne doit **jamais** dépasser la baseline qualifiée **de sa propre échelle** : `PER_MODULE_QUALIFIED_LINE_RATIO` / `PER_MODULE_QUALIFIED_BRANCH_RATIO` dans `CoverageQualityGateTest`, `AGGREGATE_QUALIFIED_LINE_RATIO` / `AGGREGATE_QUALIFIED_BRANCH_RATIO` dans `AggregateCoverageGateTest` (valeurs à relire dans les sources, cf. `rules/meta.md`)
- `D2RepositoryHardeningArchitectureTest#coverageRatchetCannotSilentlyReturnToTheD2Floor` vérifie que
  `CoverageQualityGateTest.java` **ne contient pas** les chaînes `LINE_RATCHET = 0.40d` / `BRANCH_RATCHET = 0.35d`
  (le ratchet ne doit jamais être recodé en dur à la valeur plancher D2) et lit bien
  `config/m21-quality-ratchets.properties`
- Le gate **dérive** la population attendue au lieu de la compter : tout module déclaré dans les
  `<modules>` du POM racine qui porte au moins une classe sous `src/main/java` doit avoir produit son
  `target/site/jacoco/jacoco.xml`. Aucun seuil n'est écrit en dur — le compte observé (**16** au
  10/09/2026, sur un `clean verify` réel) est un constat, pas une constante : déclarer un module le rend
  obligatoire du même geste, et les deux modules sans sources principales
  (`morpheus-architecture-tests`, `morpheus-coverage-report`) sont *expliqués* par la règle au lieu d'être
  exclus par leur nom. L'ancienne garde `>= 8` se trompait dans le sens permissif : retirer un module
  retire ses lignes manquées avec ses lignes couvertes, donc un module moins bien couvert que la moyenne
  **faisait monter** le ratio en disparaissant.
- Ce gate exige donc un `./mvnw clean verify` complet **préalable** — il n'a jamais su conclure d'un arbre
  fraîchement nettoyé, et il refuse désormais aussi un réacteur à moitié construit. Le refus **nomme** les
  modules manquants et sépare les deux causes : jamais construit par cette invocation (erreur
  d'invocation, relancer le réacteur) ou construit sans produire de rapport (régression du module).

### Pourquoi le ratchet plafonne sous la mesure réelle

Les constantes `*_QUALIFIED_LINE_RATIO` / `*_QUALIFIED_BRANCH_RATIO` plafonnent le ratchet de leur
échelle, et leur commentaire impose qu'ils soient qualifiés sur **la plus basse mesure
reproductible des deux plateformes**, jamais sur la meilleure : Linux et Windows exécutent le
même nombre de tests, mais certains no-opent hors de leur OS, donc Linux couvre légèrement
moins de lignes à nombre de tests identique.

Relever le plafond exige donc une preuve Linux **et** Windows, citée dans le commentaire.
Mesure du 08/09/2026 sur `fix/audit-hardening-2026-09-08`, deux runs complets par plateforme :

```text
Windows   62,5328 % / 62,5432 % lignes   53,8092 % / 53,8188 % branches
Linux     62,5083 % / 62,5013 % lignes   53,7997 % / 53,7997 % branches   <- plafond sur la plus basse
```

Le plafond précédent (54,5801 % / 47,7791 %) avait dérivé loin sous la réalité mesurée :
`develop` était déjà à 60,41 % lignes sur Linux **avant** que cette branche n'ajoute un test.

Le **ratchet** se place délibérément *sous* le plafond, pas dessus : deux runs du même commit
sur la même machine ont différé de deux lignes couvertes, donc un ratchet collé à la mesure
transformerait une variation ordinaire en échec de build. `0.620 / 0.535` laisse environ
140 lignes et 30 branches de marge **sur l'échelle par module** ; l'échelle agrégée compte une autre
population de lignes et sa marge se lit dans `AggregateCoverageGateTest`.

Une session qui ne dispose que d'une plateforme ne peut relever que le ratchet, dans la marge
déjà qualifiée ; elle ne touche pas au plafond.

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
./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest    # échelle par module
./mvnw test -pl morpheus-coverage-report                                      # échelle agrégée
```
