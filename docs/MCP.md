# MORPHEUS MCP — M10 + M12 + M13

Statut : **M10/M12 validés ; extensions M13 implémentées — gate M13 pending**

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

### `list_external_references`

```json
{"projectId":"<uuid>","ownerId":"<uuid>"}
```

### `resolve_external_reference`

```json
{"projectId":"<uuid>","referenceId":"<uuid>"}
```

MINOS reste optionnel. Sans resolver : observation `UNRESOLVED / NO_RESOLVER`, serveur MCP toujours fonctionnel.

## Extensions M13 — 2 tools read-only

Le serveur M13 porte additivement le catalogue à **18 tools** :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Les deux tools sont enregistrés séparément du catalogue historique M10 et des tools M12.

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

Required : `projectId`, `requirementId`, `nexusProject`.

MORPHEUS construit uniquement :

```text
Requirement: <key?> <title>
Statement: <statement>
```

Puis délègue la sélection/ranking/fusion/compression au `TechnicalContextProvider`.

### `get_augmented_change_context`

Input identique, avec `changeId` à la place de `requirementId`.

Le seed MORPHEUS contient uniquement les faits du snapshot ACTIVE :

```text
change title / intent / scope
affected requirements
constraints
design decisions
implementation tasks
```

### Résultat M13

Conceptuellement :

```json
{
  "snapshot":{"id":"...","state":"ACTIVE"},
  "intentContext":{"subjectType":"REQUIREMENT","query":"..."},
  "technicalContext":{
    "status":{"system":"NEXUS","state":"AVAILABLE"},
    "bundle":{
      "tokenBudget":2000,
      "estimatedTokens":900,
      "items":[]
    }
  },
  "persisted":false
}
```

Les scores, `scoreComponents`, raisons, exclusions et métadonnées du bundle sont des faits NEXUS ; MORPHEUS ne les reranke pas.

## NEXUS optionnel

Le launcher injecte un `TechnicalContextProvider` générique au serveur MCP.

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

## Preuves M13 implémentées

```text
vrai NEXUS MCP STDIO fixture : initialize/list/call
vrai MORPHEUS MCP STDIO : découverte des 2 tools M13
provider NEXUS absent -> non fatal
HTTP/CLI utilisent le même port applicatif
architecture interdit com.nexus.*
```

## Références

- M10 : [`VALIDATION_M10.md`](VALIDATION_M10.md)
- M12 : [`MINOS.md`](MINOS.md)
- M13 : [`NEXUS.md`](NEXUS.md)
- roadmap M13 : [`roadmap/M13_EXECUTION.md`](roadmap/M13_EXECUTION.md)

## Gate M13

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```
