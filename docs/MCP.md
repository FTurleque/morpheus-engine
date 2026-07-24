# MORPHEUS MCP — M10 + M12

Statut : **M10 validé ; extensions M12 implémentées — gate M12 pending**

MORPHEUS expose un serveur Model Context Protocol natif sur STDIO pour IDE, agents et orchestrateurs locaux.

## Lancement

```text
morpheus mcp --stdio
morpheus --db /path/to/morpheus.db mcp --stdio
```

Stockage : même SQLite que CLI/API.

Discipline :

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

Le serveur porte additivement le catalogue à **16 tools** :

```text
list_external_references
resolve_external_reference
```

Le catalogue M10 reste inchangé ; les deux specs M12 sont enregistrées séparément au bootstrap du serveur.

### `list_external_references`

Input strict :

```json
{"projectId":"<uuid>","ownerId":"<uuid>"}
```

Retourne les références externes persistées pour le propriétaire dans le snapshot ACTIVE.

### `resolve_external_reference`

Input strict :

```json
{"projectId":"<uuid>","referenceId":"<uuid>"}
```

Effectue une observation live via `LiveExternalReferenceResolutionService`.

Sortie conceptuelle :

```json
{
  "snapshotId":"...",
  "stored":{"resolutionState":"UNVALIDATED"},
  "observed":{"resolutionState":"RESOLVED"},
  "persisted":false
}
```

La résolution ne modifie jamais la référence persistée dans le snapshot.

## MINOS optionnel

Le launcher injecte un `ExternalReferenceResolverRegistry` générique au serveur MCP.

Sans configuration :

```text
MINOS resolver absent
resolve_external_reference -> observation UNRESOLVED / NO_RESOLVER
MCP server -> reste entièrement fonctionnel
```

Avec `MORPHEUS_MINOS_JAR` valide :

```text
resolve_external_reference
  -> ExternalReferenceResolutionService
  -> MinosMcpExternalReferenceResolver
  -> MCP client STDIO
  -> MINOS minos_index_status + minos_find_symbols
```

Le serveur MORPHEUS ne dépend d'aucun type `com.minos.*`.

## Identité MINOS

```text
system       = MINOS
resourceType = SYMBOL
project      = obligatoire
externalId   = exact symbolKey
revision     = activeSnapshotId attendu, optionnel
```

La recherche MINOS peut être lexicale, mais seul un `symbolKey` exactement égal est résolu.

## JSON Schemas

Inputs MCP :

```text
type = object
additionalProperties = false
required = explicite
```

Bornes M10 conservées :

```text
limit          1..100
depth          1..20
maxAgeMinutes  1..525600
offset         >= 0
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
```

## Preuves M12 implémentées

```text
vrai MINOS MCP STDIO fixture : initialize/list/call
vrai MORPHEUS MCP STDIO : tools/list + list_external_references + resolve_external_reference
standalone sans MINOS -> NO_RESOLVER non fatal
SQLite reference inchangée
```

## Références

- M10 : [`VALIDATION_M10.md`](VALIDATION_M10.md)
- M12 : [`MINOS.md`](MINOS.md)
- roadmap : [`roadmap/M12_EXECUTION.md`](roadmap/M12_EXECUTION.md)

## Gate M12

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```
