# MORPHEUS API HTTP — M11 + M12

Statut : **M11 validé ; extensions M12 implémentées — gate M12 pending**

MORPHEUS expose un service headless local via une API JSON versionnée. M12 ajoute la consultation et la résolution live des références externes MINOS sans modifier les contrats M11 existants.

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

Le serveur utilise la même SQLite que CLI/MCP. Le bind par défaut est loopback.

## Architecture

```text
Domain / Application
        ↑
   ┌────┼────┐
   │    │    │
  CLI  MCP  API
        ↑
 optional composition root -> MINOS adapter
```

`morpheus-api` ne dépend ni de CLI, ni de MCP, ni de `morpheus-integration-minos`. Il reçoit seulement des ports applicatifs génériques.

## Transport

```text
JDK 21 jdk.httpserver
HTTP local
JSON UTF-8
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

Aucun Spring, servlet container, Netty, Docker, GraphQL, SSE ou WebSocket n'est requis.

## Enveloppes

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Codes : `200`, `201`, `400`, `404`, `405`, `409`, `415`, `500`.

## Service

```text
GET /api/v1/
GET /api/v1/health
GET /api/v1/version
```

## Projets / synchronisation

```text
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

Enregistrement :

```json
{"workspace":"N:\\workspace-dev\\my-openspec-project"}
```

Le sync réutilise le pipeline M7/M9 et publie un **FULL_REBUILD conservateur**. Un échec avant activation conserve l'ancien ACTIVE.

## Spécifications

```text
GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context
```

Pagination : `offset >= 0`, `1 <= limit <= 100`.

## Requirements

```text
GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace
```

Recherche : `query`, `offset`, `limit`. Trace : `depth=1..20`.

Les queries ACTIVE n'exposent que `TemporalState.CURRENT`.

## Changements

```text
GET /api/v1/projects/{projectId}/changes
GET /api/v1/projects/{projectId}/changes/{changeId}
GET /api/v1/projects/{projectId}/changes/{changeId}/constraints
GET /api/v1/projects/{projectId}/changes/{changeId}/acceptance-criteria
GET /api/v1/projects/{projectId}/changes/{changeId}/design-decisions
GET /api/v1/projects/{projectId}/changes/{changeId}/implementation-tasks
GET /api/v1/projects/{projectId}/changes/{changeId}/context
GET /api/v1/projects/{projectId}/changes/{changeId}/status
GET /api/v1/projects/{projectId}/changes/{changeId}/blocking-conditions
```

Invariants :

```text
Scenario != AcceptanceCriterion
acceptance absente -> UNAVAILABLE_IN_NORMALIZED_MODEL + []
lifecycle absent -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

## Versions / historique

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare?fromSnapshotId=...&toSnapshotId=...
```

Contrat M3 : `RETIRED* -> ACTIVE`, candidats non publiés invisibles, comparaison `ADDED/MODIFIED/REMOVED/UNCHANGED`.

## Diagnostics

```text
GET /api/v1/projects/{projectId}/diagnostics
```

Réutilise `QualityReportService` M6.

# Extensions M12 — MINOS / références externes

## Statut d'intégration

```text
GET /api/v1/integrations/minos/status
```

États possibles :

```text
DISABLED     aucune configuration MINOS
INVALID      configuration invalide
AVAILABLE    serveur MINOS MCP joignable et compatible
UNAVAILABLE  configuré mais process/transport/tools indisponibles
```

L'appel status peut sonder MINOS ; le démarrage de l'API ne le fait pas.

## Liste de références externes

```text
GET /api/v1/projects/{projectId}/external-references?ownerId=<domain-identity>
```

Retourne les `ExternalReference` persistées dans le snapshot ACTIVE pour le propriétaire demandé.

## Résolution live

```text
GET /api/v1/projects/{projectId}/external-references/{referenceId}/resolution
```

Réponse conceptuelle :

```json
{
  "apiVersion":"v1",
  "data":{
    "snapshotId":"...",
    "stored":{"resolutionState":"UNVALIDATED"},
    "observed":{"resolutionState":"RESOLVED"},
    "persisted":false
  }
}
```

Invariant M12 : **l'observation live ne réécrit jamais la référence du snapshot publié**.

Sans MINOS configuré :

```text
stored   = référence persistée
observed = UNRESOLVED / NO_RESOLVER
persisted = false
HTTP = 200
```

## Coordonnée MINOS

```text
system       = MINOS
resourceType = SYMBOL
project      = projet MINOS obligatoire
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId attendu, optionnel
```

Le resolver filtre les résultats de `minos_find_symbols` par égalité exacte de `symbolKey`. Aucun fuzzy matching n'est accepté.

Si `revision` est fournie et diffère de `minos_index_status.activeSnapshotId`, l'observation retourne `TARGET_REVISION_MISMATCH`.

## JSON d'entrée

Les POST avec body exigent :

```text
Content-Type application/json
body <= 65536 octets
JSON valide
aucun champ inconnu
aucun token JSON supplémentaire
```

Les query params inconnus sont rejetés.

## Frontières d'écriture

Mutations opérationnelles autorisées :

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
write external-reference resolution
```

## OpenAPI

Contrat machine-readable : [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml).

## Références

- M11 : [`VALIDATION_M11.md`](VALIDATION_M11.md)
- M12 MINOS : [`MINOS.md`](MINOS.md)
- roadmap M12 : [`roadmap/M12_EXECUTION.md`](roadmap/M12_EXECUTION.md)

## Gate M12

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Le packaging M12 doit prouver que l'adapter client MINOS est présent, qu'aucune classe `com/minos/*` n'est embarquée, et que MORPHEUS reste fonctionnel avec MINOS `DISABLED`.
