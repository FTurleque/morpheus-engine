# Proposition : tester le store SQLite dans son propre module, puis relever son seuil (AUD-TST-31)

## Why

`morpheus-store-sqlite` porte les données, les migrations et les écritures à révision attendue (CAS), et sa couverture
propre est la plus basse des modules produit. Preuve conservée de la dernière qualification
(`config/coverage-qualification/per-module/Linux-42306627340.txt`) : 3 229 lignes couvertes et 749 manquées, 860
branches couvertes et 357 manquées, soit 81,17 % / 70,67 % (Windows : 81,15 % / 70,58 %). Seuls deux modules
d'outillage de vérification (`morpheus-store-memory`, `morpheus-provider-testkit`) sont plus bas. Le module porte aussi
421 lignes et 125 branches des 801 et 380 du crédit emprunté qualifié (`BorrowedCoverageGateTest.QUALIFIED_BY_MODULE`) :
une régression d'un chemin SQLite n'est vue que par des tests d'autres modules, que l'échelle par module ne crédite pas.
La marge sur son seuil propre (`config/m21-per-module-coverage-floors.properties`) était, à `a04f32bd`, de 33 lignes et
16 branches.

Preuve : constat `AUD-TST-31` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd`. Revérifié le 17/11/2026 : chiffres de la preuve conservée et du seuil confirmés ; le rapport
JaCoCo propre du module présent dans l'arbre (`morpheus-store-sqlite/target/site/jacoco/jacoco.xml`, même total que la
mesure de l'audit à `a04f32bd`) **contredit la priorité** donnée par l'audit : `SqliteSnapshotBusinessContentWriter` a
0 ligne manquée et 1 branche manquée sur 32, couvert par les tests du store de contenu du module qui ne le nomment pas.
Les lignes manquées se concentrent ailleurs, d'abord dans `SqliteExternalReferenceStore` (173 manquées sur 189).

Le sprint 2 de l'audit place ce signal avant les sprints 5 et 6, qui modifient la couche SQLite.

## What Changes

- Des tests sont écrits dans `morpheus-store-sqlite`, par ordre de lignes et de branches manquées dans son rapport
  propre, en commençant par `SqliteExternalReferenceStore`, puis les chemins d'échec et de conflit des stores à CAS
  (`SqliteVersionedRequirementStore`, `SqliteChangeLifecycleMutationStore`, `SqliteSavedViewStore`,
  `SqliteCompositionStateStore`).
- Après mesure exact-head sur Linux et Windows, le seuil propre du module est relevé dans sa fenêtre qualifiée, et le
  maximum du crédit emprunté abaissé si la mesure le permet.
- Aucun code de production, aucune exigence, aucun gate ne change. Aucun ratchet n'est baissé.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change ; les règles de la fenêtre d'un seuil de module (`per-module-coverage-floor`) et du
crédit emprunté (`coverage-attribution-gap`) s'appliquent telles quelles. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** tests de `morpheus-store-sqlite` ; après requalification, les clés `morpheus-store-sqlite.*` de
`config/m21-per-module-coverage-floors.properties`, l'ensemble `config/coverage-qualification/` et, le cas échéant, les
clés du crédit emprunté de `config/m21-quality-ratchets.properties` et les constantes qualifiées de
`BorrowedCoverageGateTest`.

**Exclus :** tout code de production du store (un défaut trouvé par un test ouvre son propre changement) ; les seuils
des autres modules ; les plafonds et ratchets des deux échelles, sauf requalification menée à la même tête et dans le
même changement comme l'exige `CoverageQualificationEvidenceTest`.

## Acteurs et surfaces concernés

Le mainteneur (requalification sur les runners CI des deux plateformes) et la CI. Aucune surface publique.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Couverture propre 81,17 % / 70,67 % (Linux), 81,15 % / 70,58 % (Windows) | existant vérifié | `config/coverage-qualification/per-module/*-42306627340.txt` |
| 421 lignes et 125 branches du crédit emprunté | existant vérifié | `BorrowedCoverageGateTest.QUALIFIED_BY_MODULE` |
| `SqliteSnapshotBusinessContentWriter` est couvert par les tests du module | existant vérifié | rapport JaCoCo propre du module, 0 ligne manquée ; écart avec l'audit |
| `SqliteExternalReferenceStore` concentre les lignes manquées | existant vérifié | même rapport, 173 manquées sur 189 |
| Relever un seuil exige une mesure Linux et Windows à une seule tête | existant vérifié | `GEN-0167`, `CoverageQualificationEvidenceTest` |
| Les valeurs relevées sont celles que nomme le gate | décision | design.md, D3 |

## Impact

Tests de `morpheus-store-sqlite`. Fichiers de gouvernance touchés à la requalification seulement
(`config/m21-per-module-coverage-floors.properties`, `config/coverage-qualification/`, éventuellement
`config/m21-quality-ratchets.properties` et `BorrowedCoverageGateTest`) : justification dans la PR. `GEN-0167` reste la
décision en vigueur. Aucune dépendance stricte avec les autres constats du sprint 2 ; les changements SQLite des
sprints 5 et 6 (`AUD-PRF-23`, `AUD-PRF-24`, `AUD-PRF-25`…) profitent de ces tests.

## Questions ouvertes

- Le mainteneur lance-t-il la requalification (runs CI Linux et Windows à une même tête) dans ce changement, ou dans un
  changement de requalification séparé comme les précédents ? design.md, D3, propose de la garder dans ce changement
  pour satisfaire le critère de sortie du sprint 2.
