# Serveur MCP MORPHEUS

MORPHEUS expose un serveur Model Context Protocol natif sur STDIO pour IDE, agents et orchestrateurs locaux.

## Lancement

```bash
morpheus mcp --stdio
morpheus --db /path/to/morpheus.db mcp --stdio
```

Contrat transport :

```text
SDK        Java MCP SDK 2.0.0
transport  STDIO
stdout     JSON-RPC MCP uniquement
stderr     diagnostics
inputs     JSON Schemas stricts
```

`--json` n’est pas utilisé en mode MCP : stdout appartient au protocole.

## Catalogue actuel — 20 tools read-only

### Spécification et requêtes

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

### MINOS

```text
list_external_references
resolve_external_reference
```

### NEXUS

```text
get_augmented_requirement_context
get_augmented_change_context
```

### JARVIS / orchestration

```text
get_change_orchestration_state
evaluate_change_transition
```

## Sémantique conservatrice

```text
Scenario != AcceptanceCriterion
acceptance absente -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle absent -> indisponible, jamais inféré
queries snapshot-scoped / CURRENT
read-only
```

Un fait non observable reste `UNAVAILABLE`. Les handlers MCP ne doivent pas transformer cette absence en faux fait métier.

## `get_change_orchestration_state`

Input :

```json
{
  "projectId":"<morpheus-project-uuid>",
  "changeId":"<change-uuid>",
  "lifecycleState":"DRAFT",
  "abandonmentReason":null
}
```

Required : `projectId`, `changeId`.

`lifecycleState` est optionnel. Sans valeur :

```text
lifecycle.state  = absent
lifecycle.source = UNAVAILABLE
```

La réponse expose notamment :

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

Input :

```json
{
  "projectId":"<morpheus-project-uuid>",
  "changeId":"<change-uuid>",
  "fromState":"PROPOSED",
  "fromAbandonmentReason":null,
  "targetState":"SPECIFIED",
  "abandonmentReason":null,
  "allowBackwardTransitions":false,
  "allowCompletedReopen":false
}
```

Résultat :

```text
ALLOWED        faits requis connus + transition autorisée
BLOCKED        faits requis connus + transition bloquée
UNKNOWN        au moins un fait requis indisponible
REQUIRES_INPUT information explicite manquante
```

La décision connue réutilise `ChangeLifecycleStateMachine`. Le tool n’applique jamais la transition.

## JSON Schemas

Les inputs du catalogue sont des objets stricts :

```text
type = object
additionalProperties = false
required = explicite
```

Les erreurs de schéma doivent être rejetées avant l’exécution du handler.

## Absence de write tools

Le catalogue ne fournit pas de tool pour :

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

## Tests

La validation M14 inclut un vrai subprocess MCP STDIO qui initialise le serveur, liste les tools et appelle le contrat d’orchestration. Le module MCP est vert dans le gate complet M14.

Voir aussi : [API HTTP](API.md), [Architecture](ARCHITECTURE.md), [Intégrations](INTEGRATIONS.md).
