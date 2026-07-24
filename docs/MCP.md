# MORPHEUS MCP — M10

Statut : **implémentation fonctionnelle complète — gate local pending**

MORPHEUS expose en M10 un serveur **Model Context Protocol natif sur STDIO**, destiné aux IDE, agents et orchestrateurs locaux.

## Lancement

Depuis une distribution portable M10 :

```text
morpheus mcp --stdio
```

Les options globales de stockage M9 restent disponibles :

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

En mode MCP :

```text
stdout = protocole MCP JSON-RPC uniquement
stderr = diagnostics de démarrage/runtime uniquement
```

Il n'y a ni banner, ni help, ni log applicatif volontaire sur stdout.

M10 n'expose pas de transport HTTP/SSE et ne requiert ni Docker ni framework serveur.

## Protocole / SDK

Implémentation : Java MCP SDK officiel `2.0.0`.

Le serveur :

- négocie MCP via `initialize` ;
- expose la capability `tools` ;
- valide les arguments selon JSON Schema avant le handler ;
- utilise un transport STDIO natif ;
- conserve la sémantique métier dans `morpheus-application` / `morpheus-domain`.

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

Aucun tool d'écriture, de promotion ou d'activation n'est exposé en M10.

## Arguments

### `get_current_specification`

```json
{"projectId":"<uuid>"}
```

Retourne le snapshot ACTIVE, la version de spécification, les spécifications normalisées et des compteurs de contenu CURRENT.

### `find_requirements`

```json
{
  "projectId":"<uuid>",
  "query":"session timeout",
  "offset":0,
  "limit":50
}
```

Recherche lexicale déterministe sur les requirements CURRENT du snapshot ACTIVE.

Bornes : `offset >= 0`, `1 <= limit <= 100`.

### `get_change`

```json
{"projectId":"<uuid>","changeId":"<uuid>"}
```

Retourne un `ChangeProposal` normalisé explicitement présent dans le snapshot ACTIVE.

### `list_changes`

```json
{"projectId":"<uuid>","offset":0,"limit":50}
```

### `get_constraints`

```json
{"projectId":"<uuid>","changeId":"<uuid>","offset":0,"limit":50}
```

### `get_acceptance_criteria`

```json
{"projectId":"<uuid>","changeId":"<uuid>"}
```

Le modèle normalisé M10 ne persiste pas d'`AcceptanceCriterion` explicite. Le résultat est donc volontairement :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
criteria = []
```

**Un `Scenario` n'est jamais converti en `AcceptanceCriterion`.**

### `get_design_decisions`

```json
{"projectId":"<uuid>","changeId":"<uuid>","offset":0,"limit":50}
```

### `get_implementation_tasks`

```json
{"projectId":"<uuid>","changeId":"<uuid>","offset":0,"limit":50}
```

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
{
  "projectId":"<uuid>",
  "specificationId":"<uuid>",
  "offset":0,
  "limit":50
}
```

Agrège, sans inventer de relation :

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

Le snapshot métier publié ne persiste pas un `ChangeLifecycle` explicite. MORPHEUS retourne donc :

```text
status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
lifecycleState = UNAVAILABLE
observableFacts = tri-state facts M6
```

Aucun état lifecycle n'est inféré.

### `get_blocking_conditions`

```json
{"projectId":"<uuid>","changeId":"<uuid>"}
```

Retourne les facts observables et findings déterministes de complétude existants. Les facts absents restent listés dans `unavailableFacts`.

### `get_sync_status`

```json
{"projectId":"<uuid>","maxAgeMinutes":60}
```

Retourne la fraîcheur M7 : `UNKNOWN`, `FRESH`, `STALE` ou `REBUILD_REQUIRED`, avec les métadonnées de synchronisation persistées.

## JSON Schemas

Tous les inputs sont des objets stricts :

```text
type = object
additionalProperties = false
required = explicite
```

Bornes principales :

```text
limit          1..100
depth          1..20
maxAgeMinutes  1..525600
offset         >= 0
```

Le SDK valide les arguments avant le handler MCP.

## Exemple de configuration d'un client MCP local

Le client doit lancer le binaire MORPHEUS en mode STDIO, par exemple :

```json
{
  "command": "morpheus",
  "args": ["--db", "/path/to/morpheus.db", "mcp", "--stdio"]
}
```

Le chemin exact et la forme du fichier de configuration dépendent du client MCP utilisé.

## Frontières M10

Hors périmètre :

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

## Validation

Gate local :

```powershell
.\mvnw.cmd clean test
```

Preuve STDIO automatisée attendue dans le gate :

```text
initialize
notifications/initialized
tools/list
tools/call
schema rejection avant handler
```

Packaging :

```powershell
.\distribution\build-portable.ps1
```

Le script vérifie que l'uber-JAR contient :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

avant de produire l'app-image autonome.
