# ADR-0050 — Complétude des changements et qualité lifecycle sans faits inventés

- Statut : **Acceptée — M6**
- Date : 23 juillet 2026
- Dépend de : ADR-0032, ADR-0044, ADR-0048, ADR-0049
- Portée : M6-S3, change completeness et lifecycle blocking conditions

## Contexte

M6-S2 est validée et intégrée :

```text
merge = 916201c724722cf9ace50d44e55d001d8faf383c
gate  = 241/241 PASS
```

M3 fournit déjà une machine d'état métier déterministe :

```text
DRAFT -> PROPOSED -> SPECIFIED -> DESIGNED -> PLANNED
      -> IMPLEMENTING -> VERIFYING -> COMPLETED -> ARCHIVED
ABANDONED séparé
```

avec `ChangeLifecycleFacts` et `ChangeLifecycleBlocker`.

Le lifecycle reste orthogonal à :

```text
KnowledgeSnapshotState
TemporalState
ImplementationTask.completed
```

et `ChangeLifecycle` n'est pas persisté dans `SnapshotBusinessContent`.

Le snapshot publié permet de dériver certains faits, mais pas tous.

## Décision

S3 ajoute une projection de faits tri-state :

```text
QualityFactValue = TRUE / FALSE / UNAVAILABLE
```

et les contrats :

```text
ChangeLifecycleFactAssessment
ChangeCompletenessAssessment
ChangeCompletenessReport
ChangeCompletenessService
ChangeLifecycleQualityAssessment
ChangeLifecycleQualityService
```

`QualityFindingCode` est étendu avec :

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
LIFECYCLE_TRANSITION_BLOCKED
```

Aucune persistance lifecycle ou quality n'est ajoutée.

## Faits dérivés du snapshot

Pour chaque `ChangeProposal` d'un snapshot publié :

```text
requirementsIdentified
  TRUE  si >= 1 Change --AFFECTS--> Requirement CURRENT
  FALSE sinon

criticalConstraintsKnown
  UNAVAILABLE
  car Constraint ne modélise pas la criticité ni l'exhaustivité

acceptanceCriteriaDefined
  UNAVAILABLE
  car AcceptanceCriterion n'existe pas encore dans le modèle production

designRequired
  UNAVAILABLE
  car aucun signal normalisé explicite ne l'indique

designDecisionsAvailable
  TRUE  si >= 1 DesignDecision liée au change
  FALSE sinon

planPresent
  TRUE si >= 1 ImplementationTask liée au change
  UNAVAILABLE si aucune task
  car absence de task ne prouve pas l'absence d'un plan équivalent externe

knownBlocker
  UNAVAILABLE
  car ChangeProposal.risks != blocker lifecycle

blockingAcceptanceCriterionFailed
blockingAcceptanceCriterionUnverified
  UNAVAILABLE
  car AcceptanceCriterion n'est pas normalisé/persisté
```

## Change completeness

`ChangeCompletenessService` reste snapshot-scoped :

```text
active(projectId) -> ACTIVE
snapshot(snapshotId) -> ACTIVE ou RETIRED
```

Il expose pour chaque change :

```text
change
lifecycleFacts tri-state
currentRequirementCount
constraintCount
designDecisionCount
implementationTaskCount
findings
```

Finding déterministe si aucun requirement CURRENT n'est relié :

```text
CHANGE_WITHOUT_CURRENT_REQUIREMENT
WARNING
DETERMINISTIC
subject = CHANGE(changeId)
```

Un finding informatif unique signale aussi les dimensions de complétude non observables :

```text
CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE
INFO
DETERMINISTIC
```

Il ne signifie pas que le change est incorrect ; il signifie que MORPHEUS ne peut pas conclure sur toutes les dimensions lifecycle à partir du snapshot courant.

## Lifecycle quality — mode dérivé

`ChangeLifecycleQualityService` reçoit explicitement :

```text
ChangeLifecycle source
ChangeLifecycleState targetState
ChangeLifecyclePolicy policy
Optional<ChangeAbandonmentReason> abandonmentReason
```

Le source lifecycle n'est jamais reconstruit depuis le snapshot.

Pour chaque transition, S3 connaît la liste exacte des faits que la machine M3 consulte :

```text
PROPOSED -> SPECIFIED
  requirementsIdentified
  criticalConstraintsKnown
  acceptanceCriteriaDefined

SPECIFIED -> DESIGNED
  designRequired
  designDecisionsAvailable

SPECIFIED -> PLANNED
  designRequired
  planPresent

DESIGNED -> PLANNED
  planPresent

PLANNED -> IMPLEMENTING
  knownBlocker

VERIFYING -> COMPLETED
  blockingAcceptanceCriterionFailed
  blockingAcceptanceCriterionUnverified
```

Les autres transitions n'exigent aucun `ChangeLifecycleFacts` spécifique avant appel à la machine.

Si un fait requis est `UNAVAILABLE` :

```text
la machine M3 n'est PAS appelée
transitionDecision = empty
LIFECYCLE_REQUIRED_FACT_UNAVAILABLE
```

Aucune valeur de substitution n'est inventée.

Si tous les faits requis sont disponibles, la machine M3 est appelée et sa décision reste source de vérité.

Chaque `ChangeLifecycleBlocker` retourné devient un finding :

```text
LIFECYCLE_TRANSITION_BLOCKED
WARNING
DETERMINISTIC
blocker=<code M3 exact>
from=<state>
to=<state>
```

## Lifecycle quality — mode explicite

Une seconde opération accepte des `ChangeLifecycleFacts` explicitement fournis par l'appelant.

Dans ce mode :

```text
aucune dérivation des booléens n'est effectuée
la machine M3 reçoit exactement les faits fournis
les blockers sont projetés sans modification
```

Cela permet d'évaluer des faits que le modèle snapshot courant ne sait pas encore représenter, sans les inventer.

## Snapshot coherence

Le `ChangeLifecycle.changeId` doit exister dans le `SnapshotBusinessContent` du snapshot évalué.

Un lifecycle appartenant à un change absent est rejeté explicitement.

## Déterminisme

- changes triés par `ChangeId` ;
- counts calculés sur le même snapshot ;
- requirements pris uniquement en `CURRENT` ;
- findings triés par `QualityFinding` ;
- blockers M3 conservés dans leur ordre canonique ;
- Memory == SQLite ;
- SQLite reopen identique.

## Frontières

M6-S3 ne fait pas :

```text
persister ChangeLifecycle
persister QualityFinding
déduire le lifecycle depuis task.completed
déduire le lifecycle depuis snapshot state
déduire le lifecycle depuis TemporalState
interpréter risks comme blockers
déduire designRequired
déduire AcceptanceCriterion depuis Scenario
nouvelle migration
modification provider
LLM / fuzzy / semantic
```

## Preuves validées

`ChangeLifecycleQualityContractTest` : **7/7 PASS**.

Les preuves couvrent :

- facts tri-state exacts ;
- CURRENT requirement seulement ;
- change sans CURRENT requirement -> finding ;
- absence de task -> planPresent UNAVAILABLE, pas FALSE ;
- risks non vides -> knownBlocker UNAVAILABLE ;
- derived PROPOSED -> SPECIFIED non évalué car critical/acceptance indisponibles ;
- transition sans faits requis déléguée à la machine M3 ;
- explicit facts -> blockers M3 exacts conservés ;
- lifecycle changeId absent du snapshot rejeté ;
- ACTIVE / RETIRED / READY policy ;
- Memory == SQLite ;
- SQLite reopen ;
- gate Windows complet vert.

Gate local Windows complet :

```text
ChangeLifecycleQualityContractTest       7/7 PASS
Architecture tests                    121/121 PASS
TOTAL                                 248/248 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            20.687 s
Finished at                2026-07-23T22:29:23+02:00
```

Warnings connus non bloquants uniquement : Xerial SQLite/JDK restricted native access et SLF4J NOP.

Head de code effectivement testé :

```text
84f41498610af2d76236fe1e6c419a6234a5f8c9
```

## Acceptation

**Acceptée — M6.**

La complétude des changements et l'évaluation des blocages lifecycle sont déterministes, snapshot-cohérentes et explicables, sans transformer un fait indisponible en valeur négative ni déduire le lifecycle depuis d'autres états.