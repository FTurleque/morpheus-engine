# MORPHEUS API HTTP — M11 + M12 + M13

Statut : **M11/M12 validés ; extensions M13 implémentées — gate M13 pending**

MORPHEUS expose un service headless local via une API JSON versionnée. M12 ajoute MINOS ; M13 ajoute l'augmentation live d'une intention MORPHEUS par un contexte technique NEXUS sous budget.

## Lancement

```text
morpheus api
morpheus api --host 127.0.0.1 --port 8765
morpheus --db /path/to/morpheus.db api
```

Défauts :

```text
host = 127.0.0.1
port = 8765
base = /api/v1
```

Même SQLite que CLI/MCP. Bind loopback par défaut.

## Architecture

```text
Domain / Application
        ↑
   ┌────┼────┐
   │    │    │
  CLI  MCP  API
        ↑
 generic optional ports
   ├─ MINOS adapter
   └─ NEXUS adapter
```

`morpheus-api` ne dépend ni de CLI/MCP ni de `morpheus-integration-minos`/`morpheus-integration-nexus`.

## Transport / enveloppes

```text
JDK 21 jdk.httpserver
HTTP local
JSON UTF-8
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Codes : `200`, `201`, `400`, `404`, `405`, `409`, `415`, `500`.

## Surface M11 conservée

```text
GET /api/v1/
GET /api/v1/health
GET /api/v1/version

GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status

GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context

GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace

GET /api/v1/projects/{projectId}/changes
GET /api/v1/projects/{projectId}/changes/{changeId}
GET /api/v1/projects/{projectId}/changes/{changeId}/constraints
GET /api/v1/projects/{projectId}/changes/{changeId}/acceptance-criteria
GET /api/v1/projects/{projectId}/changes/{changeId}/design-decisions
GET /api/v1/projects/{projectId}/changes/{changeId}/implementation-tasks
GET /api/v1/projects/{projectId}/changes/{changeId}/context
GET /api/v1/projects/{projectId}/changes/{changeId}/status
GET /api/v1/projects/{projectId}/changes/{changeId}/blocking-conditions

GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare
GET /api/v1/projects/{projectId}/diagnostics
```

Invariants M11 : API = adapter, CURRENT/ACTIVE explicites, failed sync conserve ACTIVE, aucun endpoint APPLY/PROMOTE/ACTIVATE/rollback.

# Extensions M12 — MINOS

```text
GET /api/v1/integrations/minos/status
GET /api/v1/projects/{projectId}/external-references?ownerId=<domain-identity>
GET /api/v1/projects/{projectId}/external-references/{referenceId}/resolution
```

La résolution live retourne `stored`, `observed`, `persisted=false` et ne réécrit jamais la référence du snapshot publié.

# Extensions M13 — NEXUS / contexte augmenté

## Statut

```text
GET /api/v1/integrations/nexus/status
```

États :

```text
DISABLED     aucune configuration NEXUS
INVALID      configuration invalide
AVAILABLE    runner MCP joignable et tools compatibles
UNAVAILABLE  configuré mais process/transport/tools indisponibles
```

Le bootstrap HTTP ne lance pas NEXUS.

## Requirement augmenté

```text
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
```

## Change augmenté

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Body strict commun :

```json
{
  "nexusProject":"morpheus-engine",
  "tokenBudget":2000,
  "requestedSources":["FILE","SYMBOL","TEST","DOCUMENTATION"],
  "constraints":{"language":"java"},
  "explain":false
}
```

Required : `nexusProject`.

Défauts :

```text
tokenBudget = 2000
requestedSources = []
constraints = {}
explain = false
```

Bornes : `tokenBudget=1..100000` ; sources contrôlées `FILE|SYMBOL|TEST|DOCUMENTATION|INSTRUCTION|SKILL|GIT`.

## Sémantique

```text
ACTIVE snapshot
 -> MORPHEUS deterministic intent seed
 -> NEXUS build_context | explain_context
 -> augmented response
 -X-> KnowledgeSnapshot mutation
 -X-> ContextBundle persistence
```

Réponse conceptuelle :

```json
{
  "apiVersion":"v1",
  "data":{
    "snapshot":{"id":"...","state":"ACTIVE"},
    "intentContext":{"subjectType":"CHANGE","query":"..."},
    "technicalContext":{
      "status":{"system":"NEXUS","state":"AVAILABLE"},
      "bundle":{"tokenBudget":2000,"estimatedTokens":950,"items":[]}
    },
    "persisted":false
  }
}
```

MORPHEUS ne reranke, ne fusionne et ne retronque pas le bundle technique. Scores, raisons, exclusions et métadonnées restent attribués à NEXUS.

Sans NEXUS configuré, le même endpoint retourne l'intention MORPHEUS avec `technicalContext.status.state=DISABLED`, bundle absent et HTTP `200`.

## JSON d'entrée

Tous les POST avec body exigent :

```text
Content-Type application/json
body <= 65536 octets
JSON valide
aucun champ inconnu
aucun token JSON supplémentaire
```

Les query params inconnus sont rejetés.

## Frontières d'écriture

Mutations opérationnelles HTTP autorisées :

```text
register project
sync project
```

Toujours absents :

```text
RequirementDelta APPLY
PROMOTE
ACTIVATE direct
rollback mutation
write requirement/change
persist external-reference resolution
persist NEXUS ContextBundle
NEXUS project add/index/rebuild
```

## OpenAPI

Contrat machine-readable : [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml).

## Références

- M11 : [`VALIDATION_M11.md`](VALIDATION_M11.md)
- M12 : [`MINOS.md`](MINOS.md)
- M13 : [`NEXUS.md`](NEXUS.md)
- roadmap M13 : [`roadmap/M13_EXECUTION.md`](roadmap/M13_EXECUTION.md)

## Gate M13

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```
