# MORPHEUS × JARVIS — M14

Statut : **✅ VALIDÉ**

M14 expose à JARVIS une vue machine des faits MORPHEUS et une évaluation read-only des transitions lifecycle. JARVIS reste l'orchestrateur : MORPHEUS ne choisit ni la prochaine action ni son séquencement.

Validation : [`VALIDATION_M14.md`](VALIDATION_M14.md).

## Responsabilités

```text
MORPHEUS = specification facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

Aucune dépendance binaire cross-repo : MORPHEUS n'importe aucun `com.jarvis.*` et le client JARVIS n'importe aucun `com.morpheus.*`.

## Lifecycle explicite

MORPHEUS ne persiste pas encore un lifecycle de changement suffisamment fiable dans le snapshot publié. M14 ne l'infère donc jamais.

```text
lifecycle absent -> state absent, source=UNAVAILABLE
lifecycle fourni -> canonical state, source=CALLER_SUPPLIED
```

Un task `completed=true`, un chemin d'archive, un timestamp ou un finding qualité ne devient jamais implicitement `COMPLETED`, `ARCHIVED` ou un autre état lifecycle.

États canoniques :

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

## Évaluation de transition

M14 réutilise `ChangeLifecycleStateMachine` lorsque les préconditions nécessaires sont observables.

Résultats :

```text
ALLOWED        faits requis connus + transition autorisée
BLOCKED        faits requis connus + transition bloquée
UNKNOWN        au moins un fait requis est UNAVAILABLE
REQUIRES_INPUT information explicite manquante, ex. raison d'abandon
```

`UNAVAILABLE` n'est jamais transformé en `false` pour fabriquer un blocage.

Les retours arrière et la réouverture de `COMPLETED` restent désactivés par défaut et ne sont évalués que si le demandeur les autorise explicitement.

## Vue UC-16

La réponse d'orchestration expose :

```text
snapshot
change
lifecycle
observableFacts
missingArtifacts
unavailableFacts
acceptanceCriteria
applicableConstraints
blockingConstraints
unresolvedLinks
qualityFindings
nextAllowedTransitions
transitionEvaluations
persisted=false
```

### Acceptance criteria

Le modèle normalisé n'a pas encore de projection `AcceptanceCriterion` explicite :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
```

`Scenario` n'est jamais converti en `AcceptanceCriterion`.

### Blocking constraints

MORPHEUS peut lister les contraintes applicables mais ne possède pas encore une sémantique explicite qualifiant une contrainte comme bloquante :

```text
status = UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED
```

Aucun blocage n'est inventé.

### Liens non résolus

M14 expose sans résolution live forcée :

```text
TraceabilityLink sortant du Change avec resolution != RESOLVED
ExternalReference appartenant au Change avec resolutionState != RESOLVED
```

## CLI

```text
morpheus --json change-orchestration state \
  --project <project-id> \
  --change <change-id> \
  [--lifecycle <state>] \
  [--abandonment-reason <reason>]

morpheus --json change-orchestration transition-check \
  --project <project-id> \
  --change <change-id> \
  --from <state> \
  --to <state> \
  [--from-abandonment-reason <reason>] \
  [--abandonment-reason <reason>] \
  [--allow-backward] \
  [--allow-completed-reopen]
```

Aucune commande M14 n'applique une transition.

## MCP

Deux tools read-only additifs :

```text
get_change_orchestration_state
evaluate_change_transition
```

Le catalogue M14 expose **20 tools read-only** : 14 M10 + 2 M12 + 2 M13 + 2 M14.

## API HTTP

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une requête de calcul, pas une mutation.

Exemple :

```json
{
  "fromState":"PROPOSED",
  "targetState":"SPECIFIED",
  "allowBackwardTransitions":false,
  "allowCompletedReopen":false
}
```

Une réponse `UNKNOWN` peut notamment contenir :

```json
{
  "state":"UNKNOWN",
  "blockers":[],
  "unavailableRequiredFacts":["criticalConstraintsKnown","acceptanceCriteriaDefined"]
}
```

## Preuve cross-repo JARVIS

Dépôt : `FTurleque/jarvis`.

```text
Issue #92
PR #93
branch feature/morpheus-orchestration-client
head 58899855bcd3446636c1f274ace8c1bfc8f46930
```

Le client JARVIS :

```text
ChangeOrchestrationProvider
MorpheusOrchestrationConfig
MorpheusOrchestrationClient
```

Configuration :

```text
jarvis.morpheus.enabled=${MORPHEUS_ENABLED:false}
jarvis.morpheus.url=${MORPHEUS_URL:http://127.0.0.1:8765}
jarvis.morpheus.project-id=${MORPHEUS_PROJECT_ID:}
jarvis.morpheus.timeout-seconds=${MORPHEUS_TIMEOUT_SECONDS:3}
```

Il est fail-open : disabled, mapping absent, MORPHEUS indisponible ou HTTP non-2xx => `Optional.empty()`. Il ne recode aucune règle lifecycle.

Contrats locaux :

```text
state(changeId, optionalLifecycleState, optionalLifecycleAbandonmentReason)

evaluateTransition(
  changeId,
  fromState,
  optionalFromAbandonmentReason,
  targetState,
  optionalTargetAbandonmentReason)
```

Les raisons d'abandon observation/source/cible sont conservées et jamais inventées.

## Validation

MORPHEUS :

```text
head d44d418ae0f1e528ea09a56cdd8c45647048c740
357/357 PASS
Architecture 160/160 PASS
Packaging Windows PASS
ZIP 33,702,405 bytes
```

JARVIS :

```text
head 58899855bcd3446636c1f274ace8c1bfc8f46930
jarvis-core 536 tests
Failures 0
Errors 0
Skipped 16
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

ADR-0077 à ADR-0080 : **Acceptées — M14**.
