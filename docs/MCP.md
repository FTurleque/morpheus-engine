# MORPHEUS MCP — M10

Statut : **✅ VALIDÉ — 24 juillet 2026**

MORPHEUS expose un serveur **Model Context Protocol natif sur STDIO**, destiné aux IDE, agents et orchestrateurs locaux.

## Lancement

```text
morpheus mcp --stdio
```

Options de stockage partagées avec la CLI :

```text
morpheus --db /path/to/morpheus.db mcp --stdio
morpheus --data-dir /path/to/data mcp --stdio
```

Variables équivalentes :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Le serveur ouvre la **même base SQLite** que la CLI. Les tools M10 sont read-only.

## Discipline STDIO

```text
stdout = protocole MCP JSON-RPC uniquement
stderr = diagnostics de démarrage/runtime uniquement
```

Il n'y a ni banner, ni help, ni log applicatif volontaire sur stdout.

M10 n'expose pas de transport HTTP/SSE et ne requiert ni Docker ni framework serveur.

## Protocole / SDK

Implémentation : Java MCP SDK officiel `2.0.0`.

```text
McpServer.sync
StdioServerTransportProvider
validateToolInputs=true
```

Le serveur négocie MCP via `initialize`, expose la capability `tools`, valide les arguments selon JSON Schema avant le handler et conserve la sémantique métier dans `morpheus-application` / `morpheus-domain`.

## Catalogue M10

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

Aucun tool d'écriture, de synchronisation mutante, de promotion ou d'activation n'est exposé.

## Contrats principaux

### `get_current_specification`

```json
{"projectId":"<uuid>"}
```

Retourne le snapshot ACTIVE, la version de spécification, les spécifications normalisées et les compteurs de contenu CURRENT.

### `find_requirements`

```json
{"projectId":"<uuid>","query":"session timeout","offset":0,"limit":50}
```

Recherche lexicale déterministe sur les requirements CURRENT du snapshot ACTIVE.

### Changements et contenu associé

```text
get_change
list_changes
get_constraints
get_design_decisions
get_implementation_tasks
```

Tous ces tools restent snapshot-scoped et read-only.

### `get_acceptance_criteria`

```json
{"projectId":"<uuid>","changeId":"<uuid>"}
```

Le modèle normalisé M10 ne persiste pas d'`AcceptanceCriterion` explicite :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
criteria = []
```

**Un `Scenario` n'est jamais converti en `AcceptanceCriterion`.**

### `trace_requirement`

```json
{"projectId":"<uuid>","requirementId":"<uuid>","depth":2}
```

Réutilise le contrat de traçabilité M4/M5. `depth` est borné de `1` à `20`.

### `get_change_context`

```json
{"projectId":"<uuid>","changeId":"<uuid>","depth":2}
```

Réutilise le contexte compact M5 sur le snapshot ACTIVE.

### `get_specification_context`

```json
{"projectId":"<uuid>","specificationId":"<uuid>","offset":0,"limit":50}
```

Agrège sans inventer de relation :

```text
Specification
CURRENT Requirements
Scenarios rattachés aux requirements de la page
Changes reliés aux requirements de la specification par AFFECTS persisté
```

### `get_change_status`

```json
{"projectId":"<uuid>","changeId":"<uuid>"}
```

Le snapshot métier publié ne persiste pas un `ChangeLifecycle` explicite :

```text
status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
lifecycleState = UNAVAILABLE
observableFacts = tri-state facts M6
```

Aucun état lifecycle n'est inféré.

### `get_blocking_conditions`

Retourne les facts observables et findings déterministes de complétude existants. Les facts absents restent listés dans `unavailableFacts`.

### `get_sync_status`

```json
{"projectId":"<uuid>","maxAgeMinutes":60}
```

Retourne la fraîcheur M7 : `UNKNOWN`, `FRESH`, `STALE` ou `REBUILD_REQUIRED` avec les métadonnées persistées.

## JSON Schemas

Tous les inputs sont stricts :

```text
type = object
additionalProperties = false
required = explicite
limit          1..100
depth          1..20
maxAgeMinutes  1..525600
offset         >= 0
```

Le SDK valide les arguments avant le handler MCP.

## Exemple client MCP

```json
{
  "command": "morpheus",
  "args": ["--db", "/path/to/morpheus.db", "mcp", "--stdio"]
}
```

Le chemin exact et la forme du fichier de configuration dépendent du client MCP utilisé.

## Frontières M10

```text
Streamable HTTP
SSE
OAuth réseau
write tools
sync mutation via MCP
RequirementDelta apply/promote/activate via MCP
Docker obligatoire
MINOS code intelligence
NEXUS ranking/compression
JARVIS orchestration
```

## Validation M10

Gate Maven :

```text
MORPHEUS MCP              5/5 PASS
MORPHEUS CLI             10/10 PASS
Architecture Tests      149/149 PASS
TOTAL                   307/307 PASS
BUILD SUCCESS
```

Preuve STDIO automatisée réelle :

```text
initialize
notifications/initialized
tools/list
tools/call
schema rejection avant handler
```

Packaging Windows :

```text
MCP packaging proof: PASS
jpackage app-image PASS
morpheus.exe --version PASS
morpheus.exe --json version PASS
Portable archive creation: PASS
Windows ZIP: PASS — 77275075 bytes
runtime Java embarqué: PASS
```

Le shaded JAR vérifié contient :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

Validation complète : [`VALIDATION_M10.md`](VALIDATION_M10.md).
