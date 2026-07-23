# ADR-0045 — Getters et listes métier déterministes sur snapshots publiés

- Statut : **Proposée — M5**
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

S3 doit exposer des lectures déterministes sans introduire de nouvelle persistance ni contourner la sémantique de publication des snapshots.

## Décision candidate

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

## AcceptanceCriterion

Aucun type `AcceptanceCriterion` explicite n'existe actuellement dans le domaine MORPHEUS.

Donc S3 **n'expose pas** `get_acceptance_criteria` et ne transforme jamais un `Scenario` en critère d'acceptation.

```text
Scenario != AcceptanceCriterion
```

Cette primitive ne sera ajoutée que lorsqu'une sémantique de source explicite aura été normalisée.

## Backend

La logique de lecture est entièrement applicative. Les mêmes contrats doivent produire les mêmes résultats avec :

```text
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

SQLite close/reopen doit conserver les résultats.

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

## Preuves attendues

Le gate S3 doit démontrer :

1. ACTIVE par défaut ;
2. ACTIVE/RETIRED explicite uniquement ;
3. absence d'ACTIVE distincte de not-found ;
4. spécification adressée explicitement par `SpecificationId` ;
5. `get_change` déterministe ;
6. `list_changes` borné et ordonné ;
7. contraintes filtrées par `ChangeId`, triées et paginées ;
8. décisions filtrées par `ChangeId`, triées et paginées ;
9. tâches filtrées par `ChangeId`, triées et paginées ;
10. Memory == SQLite ;
11. SQLite reopen ;
12. aucune API `AcceptanceCriterion` synthétique ;
13. aucune migration S3 ;
14. `\.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
