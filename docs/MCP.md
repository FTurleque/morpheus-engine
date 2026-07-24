# MORPHEUS MCP — M10 à M14

Statut : **M10/M12/M13/M14 validés**

MORPHEUS expose un serveur Model Context Protocol natif sur STDIO pour IDE, agents et orchestrateurs locaux.

## Lancement

```text
morpheus mcp --stdio
morpheus --db /path/to/morpheus.db mcp --stdio
```

`stdout` est réservé au JSON-RPC MCP ; `stderr` aux diagnostics. SDK : Java MCP SDK `2.0.0`, transport STDIO, validation stricte des inputs.

## Catalogue historique M10 — 14 tools

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Contrats historiques conservés :

```text
Scenario != AcceptanceCriterion
acceptance absente -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle absent -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
queries snapshot-scoped / CURRENT
read-only
```

## M12 — 2 tools

```text
list_external_references
resolve_external_reference
```

Catalogue : **16 tools**.

## M13 — 2 tools

```text
get_augmented_requirement_context
get_augmented_change_context
```

Catalogue : **18 tools**. NEXUS reste optionnel et le `ContextBundle` live n'est pas persisté.

# M14 — 2 tools read-only

Catalogue validé : **20 tools read-only**.

```text
get_change_orchestration_state
evaluate_change_transition
```

Les tools M14 sont additifs et séparés du catalogue historique M10.

## `get_change_orchestration_state`

Input strict :

```json
{
  "projectId":"<morpheus-project-uuid>",
  "changeId":"<change-uuid>",
  "lifecycleState":"DRAFT",
  "abandonmentReason":null
}
```

Required : `projectId`, `changeId`.

`lifecycleState` est optionnel. Sans lui, MORPHEUS retourne explicitement :

```text
lifecycle.state  = absent
lifecycle.source = UNAVAILABLE
```

Avec lui : `source=CALLER_SUPPLIED`.

La réponse expose :

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

## `evaluate_change_transition`

Input strict :

```json
{
  "projectId":"<morpheus-project-uuid>",
  "changeId":"<change-uuid>",
  "fromState":"PROPOSED",
  "targetState":"SPECIFIED",
  "allowBackwardTransitions":false,
  "allowCompletedReopen":false
}
```

Optional : `fromAbandonmentReason`, `abandonmentReason`.

Résultat :

```text
ALLOWED        préconditions observables + machine autorise
BLOCKED        préconditions observables + machine bloque
UNKNOWN        fait requis UNAVAILABLE
REQUIRES_INPUT information explicite requise
```

M14 réutilise la `ChangeLifecycleStateMachine`. Un fait `UNAVAILABLE` n'est jamais transformé en `false`.

## JSON Schemas

Tous les inputs M10/M12/M13/M14 :

```text
type = object
additionalProperties = false
required = explicite
```

États lifecycle M14 :

```text
DRAFT | PROPOSED | SPECIFIED | DESIGNED | PLANNED
IMPLEMENTING | VERIFYING | COMPLETED | ARCHIVED | ABANDONED
```

## Absence de write tools

Toujours aucun tool pour :

```text
sync mutation
RequirementDelta apply
PROMOTE
ACTIVATE
rollback
apply lifecycle transition
persist external live resolution
persist NEXUS ContextBundle
index/rebuild NEXUS
orchestrate JARVIS actions
```

## Validation M14

```text
MorpheusM14McpStdioIntegrationTest
 -> vrai subprocess MORPHEUS MCP STDIO
 -> découvre les 2 tools M14
 -> conserve M12/M13
 -> appelle evaluate_change_transition
```

Gate MORPHEUS sur `d44d418ae0f1e528ea09a56cdd8c45647048c740` :

```text
MCP             5/5 PASS
CLI            20/20 PASS
Architecture 160/160 PASS
TOTAL         357/357 PASS
Packaging         PASS
```

Preuve JARVIS sur `58899855bcd3446636c1f274ace8c1bfc8f46930` :

```text
jarvis-core 536 tests
0 failure
0 error
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

Références : [`VALIDATION_M14.md`](VALIDATION_M14.md), [`JARVIS.md`](JARVIS.md), [`roadmap/M14_EXECUTION.md`](roadmap/M14_EXECUTION.md).
