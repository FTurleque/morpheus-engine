# MORPHEUS MCP — M10 + M12 + M13

Statut : **M10/M12/M13 validés**

MORPHEUS expose un serveur Model Context Protocol natif sur STDIO pour IDE, agents et orchestrateurs locaux.

## Lancement

```text
morpheus mcp --stdio
morpheus --db /path/to/morpheus.db mcp --stdio
```

Stockage : même SQLite que CLI/API.

```text
stdout = protocole MCP JSON-RPC uniquement
stderr = diagnostics runtime uniquement
```

SDK : Java MCP SDK officiel `2.0.0`, `McpServer.sync`, `StdioServerTransportProvider`, `validateToolInputs=true`.

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

Contrats conservés :

```text
Scenario != AcceptanceCriterion
acceptance absente -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle absent -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
queries snapshot-scoped / CURRENT
read-only
```

## Extensions M12 — 2 tools read-only

```text
list_external_references
resolve_external_reference
```

Le serveur M12 porte le catalogue à **16 tools**. La résolution live retourne `stored`, `observed`, `persisted=false` et ne modifie jamais la référence persistée.

MINOS reste optionnel. Sans resolver : observation `UNRESOLVED / NO_RESOLVER`, serveur MCP toujours fonctionnel.

## Extensions M13 — 2 tools read-only

Le serveur M13 porte additivement le catalogue à **18 tools** :

```text
get_augmented_requirement_context
get_augmented_change_context
```

### `get_augmented_requirement_context`

Input strict :

```json
{
  "projectId":"<morpheus-project-uuid>",
  "requirementId":"<requirement-uuid>",
  "nexusProject":"<nexus-name-or-uuid>",
  "tokenBudget":2000,
  "requestedSources":["FILE","SYMBOL","TEST"],
  "constraints":{"language":"java"},
  "explain":false
}
```

MORPHEUS construit uniquement :

```text
Requirement: <key?> <title>
Statement: <statement>
```

Puis délègue sélection/ranking/fusion/compression au `TechnicalContextProvider`.

### `get_augmented_change_context`

Input identique avec `changeId`. Le seed contient les faits du snapshot ACTIVE : change title/intent/scope, requirements affectés, contraintes, décisions et tâches.

### Résultat M13

```json
{
  "snapshot":{"id":"...","state":"ACTIVE"},
  "intentContext":{"subjectType":"REQUIREMENT","query":"..."},
  "technicalContext":{
    "status":{"system":"NEXUS","state":"AVAILABLE"},
    "bundle":{"tokenBudget":2000,"estimatedTokens":900,"items":[]}
  },
  "persisted":false
}
```

Les scores, composants de score, raisons, exclusions et métadonnées du bundle sont des faits NEXUS ; MORPHEUS ne les reranke pas.

## NEXUS optionnel

Sans configuration :

```text
NEXUS provider = DISABLED
MORPHEUS intent context = disponible
technical bundle = absent
MCP server = entièrement fonctionnel
```

Avec `MORPHEUS_NEXUS_JAR` valide :

```text
get_augmented_*_context
 -> AugmentedContextService
 -> TechnicalContextProvider
 -> NexusMcpContextGateway
 -> MCP STDIO
 -> NEXUS build_context | explain_context
```

MORPHEUS ne dépend d'aucun type `com.nexus.*`.

## JSON Schemas

Tous les inputs MCP M10/M12/M13 :

```text
type = object
additionalProperties = false
required = explicite
```

Bornes :

```text
M10 limit          1..100
M10 depth          1..20
M10 maxAgeMinutes  1..525600
M10 offset         >= 0
M13 tokenBudget    1..100000
M13 sources        FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
```

## Absence de write tools

Toujours aucun tool pour :

```text
sync mutation
RequirementDelta apply
PROMOTE
ACTIVATE
rollback
persist external live resolution
persist NEXUS ContextBundle
index/rebuild NEXUS
```

## Validation M13

```text
MCP                         5/5 PASS
MorpheusM13McpStdioIntegrationTest 1/1 PASS
Architecture            154/154 PASS
TOTAL                    346/346 PASS
```

Le vrai subprocess MORPHEUS MCP STDIO découvre les deux tools M13 sans NEXUS installé.

Références : [`VALIDATION_M13.md`](VALIDATION_M13.md), [`NEXUS.md`](NEXUS.md), [`roadmap/M13_EXECUTION.md`](roadmap/M13_EXECUTION.md).
