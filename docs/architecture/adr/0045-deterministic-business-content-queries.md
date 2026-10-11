# ADR-0045 — Getters et listes métier déterministes sur snapshots publiés

- Statut : **Acceptée — M5**
- Date : 23 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0036, ADR-0043, ADR-0044
- Portée : M5-S3, lecture métier provider/backend-neutral

## Contexte

M5-S1 fournit la recherche lexicale des `Requirement` et M5-S2 rend persistantes les autres familles métier dans une projection `SnapshotBusinessContent` snapshot-scoped.

Baseline :

```text
M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3 — 196/196
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c — 202/202
```

S3 expose des lectures déterministes sans introduire de nouvelle persistance ni contourner la sémantique de publication des snapshots.

## Décision

Introduire :

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Le service dépend uniquement de :

```text
SpecificationKnowledgeStore
SnapshotBusinessContentStore
```

Aucune migration SQLite n'est ajoutée.

## Sélection de snapshot

Les méthodes `active...` sélectionnent exclusivement le snapshot `ACTIVE` du projet.

Les méthodes `snapshot...` acceptent uniquement :

```text
ACTIVE
RETIRED
```

et rejettent :

```text
BUILDING
VALIDATING
READY
FAILED
```

L'absence d'ACTIVE est distincte d'une entité absente :

```text
Optional.empty()                    = aucun snapshot ACTIVE
SnapshotItemResult(item=empty)      = snapshot publié trouvé, entité absente
```

## Primitives

```text
activeSpecification(projectId, specificationId)
snapshotSpecification(snapshotId, specificationId)

activeChange(projectId, changeId)
snapshotChange(snapshotId, changeId)

listActiveChanges(projectId, pageRequest)
listSnapshotChanges(snapshotId, pageRequest)

activeConstraints(projectId, changeId, pageRequest)
snapshotConstraints(snapshotId, changeId, pageRequest)

activeDesignDecisions(projectId, changeId, pageRequest)
snapshotDesignDecisions(snapshotId, changeId, pageRequest)

activeImplementationTasks(projectId, changeId, pageRequest)
snapshotImplementationTasks(snapshotId, changeId, pageRequest)
```

`get_current_specification` est adressé par `SpecificationId` car un projet peut contenir plusieurs spécifications.

## Résultats

`SnapshotItemResult<T>` conserve :

```text
KnowledgeSnapshotMetadata snapshot
Optional<T> item
```

`SnapshotPage<T>` conserve :

```text
KnowledgeSnapshotMetadata snapshot
List<T> items
PageRequest pageRequest
int totalMatches
boolean hasMore
```

## Ordre et pagination

Les listes sont ordonnées par identité domaine avant pagination :

```text
ChangeId
ConstraintId
DesignDecisionId
TaskId
```

La pagination réutilise `PageRequest` de S1 :

```text
offset >= 0
1 <= limit <= 100
```

Elle est appliquée après filtrage et tri stable.

## Not found

Une entité absente dans un snapshot publié n'est pas une erreur de store : elle est représentée explicitement par `SnapshotItemResult.item = Optional.empty()`.

Une référence de changement inexistante pour une liste retourne une page vide avec `totalMatches = 0` ; S3 ne fabrique aucun objet.

Un snapshot publié sans projection S2 attendue provoque une `KnowledgeStoreException` afin de ne pas confondre corruption/incomplétude de store et absence métier.

## AcceptanceCriterion

Aucun type `AcceptanceCriterion` explicite n'existe actuellement dans le domaine MORPHEUS.

Donc S3 **n'expose pas** `get_acceptance_criteria` et ne transforme jamais un `Scenario` en critère d'acceptation.

```text
Scenario != AcceptanceCriterion
```

Cette primitive ne sera ajoutée que lorsqu'une sémantique de source explicite aura été normalisée.

## Backend

La logique de lecture est entièrement applicative. Les mêmes contrats produisent les mêmes résultats avec :

```text
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

SQLite close/reopen conserve les résultats.

## Frontières

S3 ne fait pas :

```text
nouvelle migration
nouveau modèle temporel
recherche sémantique
context aggregation
traceability aggregation
DTO JSON final
CLI / MCP / API
AcceptanceCriterion synthétique
```

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur :

```text
branch = m5/deterministic-business-queries
head   = 755bbd394347e5a8de67aa7d5eb69234a6b0ba8b
.\mvnw.cmd clean test
javac release 21
```

Preuves S3 ciblées :

```text
BusinessContentQueryBackendParityTest    1/1 PASS
BusinessContentQueryContractTest         7/7 PASS
```

Résultat global :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      83 tests
-----------------------------------------------
TOTAL                                  210/210 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             18.127 s
Finished at                 2026-07-23T18:24:34+02:00
```

Warnings connus et non bloquants uniquement : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Les 14 critères de preuve sont satisfaits : ACTIVE par défaut, historique ACTIVE/RETIRED, distinction no-ACTIVE/not-found, `SpecificationId` explicite, change/getters/listes déterministes et bornés, filtrage par `ChangeId`, Memory == SQLite, reopen SQLite, aucune API `AcceptanceCriterion` synthétique, aucune migration S3 et gate Maven complet vert.

Décision finale :

```text
ADR-0045 = ACCEPTÉE — M5
M5-S3    = VALIDÉ — 210/210
```
