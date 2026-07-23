# ADR-0049 — Couverture task → requirement et gap explicite d'acceptance coverage

- Statut : **Proposée — M6**
- Date : 23 juillet 2026
- Dépend de : ADR-0037, ADR-0038, ADR-0039, ADR-0044, ADR-0048
- Portée : M6-S2, qualité des `ImplementationTask` et capacité d'acceptance coverage

## Contexte

M6-S1 est validée et intégrée :

```text
merge = 5b0984ec7777eabb6f2d1417b4c900c08a038947
gate  = 234/234 PASS
```

M6-S2 doit détecter les tâches d'implémentation qui ne sont reliées à aucun requirement pertinent et rendre explicite l'impossibilité actuelle de mesurer une couverture d'acceptance criteria sans inventer de concept métier.

Le modèle existant impose :

```text
ImplementationTask -> ChangeId
```

mais M4 ne dérive pas de relation `ImplementationTask -> Requirement`.

Les requirements affectés par un changement sont au contraire explicitement représentés par des liens persistés :

```text
Change --AFFECTS--> Requirement
```

Par ailleurs :

```text
ProviderCapability.READ_ACCEPTANCE_CRITERIA existe
```

mais le modèle normalisé/persisté production ne contient pas encore de type `AcceptanceCriterion`.

Donc :

```text
Scenario != AcceptanceCriterion
```

reste un invariant bloquant.

## Décision candidate

Étendre la couche `application.quality` avec :

```text
TaskRequirementCoverage
TaskQualityService
AcceptanceCoverageStatus
AcceptanceCoverageAssessment
AcceptanceQualityService
```

et étendre `QualityFindingCode` avec :

```text
IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
ACCEPTANCE_COVERAGE_UNAVAILABLE
```

Aucune nouvelle entité métier, persistance ou relation de trace n'est créée.

## Couverture des ImplementationTask

Population analysée :

```text
SnapshotBusinessContent.tasks
snapshot publié ACTIVE ou RETIRED
```

Pour une task `T` :

```text
T.changeId = C
```

`T` est **couverte** si le snapshot contient au moins un lien persisté :

```text
Change(C) --AFFECTS--> Requirement(R)
```

et si `R` possède une occurrence `RequirementVersionRecord` `CURRENT` dans le même snapshot.

Aucun `TraceabilityLink` `Task -> Requirement` n'est synthétisé.

Une task est **non couverte** si aucun `Requirement CURRENT` ne peut être atteint par ce mécanisme structurel.

Calcul :

```text
totalTasks
coveredTasks
uncoveredTasks
coverageRatio = covered / total
```

Pour `totalTasks = 0` :

```text
coverageRatio = 1.0
```

## Finding task

Chaque task non couverte produit :

```text
code = IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT
severity = WARNING
evidenceKind = DETERMINISTIC
subject = TraceabilityEntityRef(IMPLEMENTATION_TASK, taskId)
confidence = empty
evidenceIds = task.provenance.evidenceId
```

Les détails doivent conserver au minimum :

```text
taskId
changeId
```

La finding ne prétend pas que le changement est sans requirement au sens absolu : elle affirme uniquement qu'aucun `AFFECTS -> Requirement CURRENT` n'est publié pour ce change dans le snapshot analysé.

## Acceptance coverage

Le statut S2 est explicite :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Tant qu'aucun `AcceptanceCriterion` normalisé/persisté n'existe, MORPHEUS ne calcule :

```text
ni acceptance criteria count
ni verified count
ni coverage ratio
```

Il ne transforme jamais un `Scenario` en `AcceptanceCriterion`.

`AcceptanceQualityService` retourne une `AcceptanceCoverageAssessment` snapshot-scoped avec le statut ci-dessus et des findings déterministes par `Specification` :

```text
code = ACCEPTANCE_COVERAGE_UNAVAILABLE
severity = WARNING
evidenceKind = DETERMINISTIC
subject = TraceabilityEntityRef(SPECIFICATION, specificationId)
confidence = empty
evidenceIds = specification.provenance.evidenceId
```

Le finding signifie : **la couverture d'acceptance ne peut pas être évaluée avec le modèle normalisé courant**. Il ne signifie pas que la source ne contient nécessairement aucun critère.

## Snapshot policy

```text
active(projectId) -> ACTIVE uniquement
snapshot(snapshotId) -> ACTIVE ou RETIRED uniquement
BUILDING / VALIDATING / READY / FAILED rejetés
```

L'absence de snapshot ACTIVE reste distincte d'un rapport vide.

## Déterminisme

- tasks triées par `TaskId` ;
- requirements résolus par `RequirementId` ;
- findings triées selon l'ordre canonique `QualityFinding` ;
- résultat identique Memory / SQLite ;
- reopen SQLite identique.

## Frontières

M6-S2 ne fait pas :

```text
nouvelle relation Task -> Requirement
inférence textuelle
fuzzy matching
semantic search
LLM
conversion Scenario -> AcceptanceCriterion
persistance de finding
nouvelle migration
modification du provider
change completeness
lifecycle blockers
```

## Preuves attendues

- task couverte via `task.changeId` + `Change --AFFECTS--> Requirement CURRENT` ;
- task non couverte -> finding déterministe ;
- AFFECTS vers requirement PROPOSED uniquement ne couvre pas la task ;
- AFFECTS cassé/non résolu ne couvre pas la task s'il n'existe aucune occurrence CURRENT ;
- zero task -> 100 % ;
- ACTIVE par défaut ;
- RETIRED explicite autorisé ;
- READY rejeté ;
- Memory == SQLite ;
- SQLite reopen ;
- acceptance status explicite `UNAVAILABLE_IN_NORMALIZED_MODEL` ;
- aucun type `AcceptanceCriterion` production ;
- aucun Scenario converti ;
- gate Windows complet vert.

## Acceptation

À compléter uniquement après le gate local complet M6-S2.
