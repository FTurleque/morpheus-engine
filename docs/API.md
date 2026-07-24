# MORPHEUS API HTTP — M11

Statut : **implémentation fonctionnelle complète — gate local pending**

M11 expose MORPHEUS comme service headless local via une API HTTP JSON versionnée.

## Lancement

```text
morpheus api
morpheus api --host 127.0.0.1 --port 8765
morpheus --db /path/to/morpheus.db api --host 127.0.0.1 --port 8765
```

Valeurs par défaut :

```text
host = 127.0.0.1
port = 8765
base = /api/v1
```

Le bind par défaut est **loopback**. Une exposition réseau différente doit être demandée explicitement avec `--host`.

Le serveur API utilise exactement la même SQLite que la CLI et le serveur MCP.

## Architecture

```text
Domain / Application
        ↑
        │
   ┌────┼────┐
   │    │    │
  CLI  MCP  API
```

L'API est un adapter sibling :

```text
morpheus-api -X-> morpheus-cli
morpheus-api -X-> morpheus-mcp
```

Les règles de recherche, trace, contexte, qualité, synchronisation et historique restent dans `morpheus-application` / `morpheus-domain`.

## Transport

M11 utilise le serveur HTTP embarqué Java 21 `jdk.httpserver`.

```text
HTTP local
JSON UTF-8
Content-Type: application/json; charset=utf-8
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

Aucun Spring, servlet container, Netty, Docker, GraphQL, SSE ou WebSocket n'est requis.

## Enveloppes JSON

Succès :

```json
{
  "apiVersion": "v1",
  "data": {}
}
```

Erreur :

```json
{
  "apiVersion": "v1",
  "error": {
    "code": "NOT_FOUND",
    "message": "project not found: ...",
    "details": {}
  }
}
```

Codes HTTP :

```text
200 OK
201 CREATED
400 BAD_REQUEST
404 NOT_FOUND
405 METHOD_NOT_ALLOWED
409 STATE_CONFLICT
415 UNSUPPORTED_MEDIA_TYPE
500 INTERNAL_ERROR
```

Les erreurs internes n'exposent jamais de stacktrace.

## Service

```text
GET /api/v1/
GET /api/v1/health
GET /api/v1/version
```

`health` retourne `status=UP` si le process HTTP répond.

## Projets

```text
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
```

Enregistrement :

```json
{
  "workspace": "N:\\workspace-dev\\my-openspec-project"
}
```

Le workspace doit exister et être un répertoire. L'enregistrement est **idempotent par racine** :

```text
nouvelle racine  -> 201 CREATED
racine existante -> 200 OK + même projectId
```

## Synchronisation

```text
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

Body de sync optionnel :

```json
{
  "revision": "opaque-source-revision"
}
```

M11 utilise volontairement la même stratégie officielle que la CLI M9 :

```text
scan local
-> SyncPlan forcé
-> OpenSpecProjectContentReader
-> ProjectSnapshotImportService
-> validation candidate
-> activation atomique
-> SyncState complete
```

Le résultat exécuté est donc **FULL_REBUILD conservateur**. Il n'existe aucun faux receipt incrémental.

Un échec avant activation conserve l'ancien ACTIVE.

Query optionnelle de fraîcheur :

```text
GET /sync-status?maxAgeMinutes=60
```

Bornes : `1..525600`.

## Spécifications

```text
GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context
```

Pagination :

```text
offset >= 0
1 <= limit <= 100
```

`context` agrège uniquement les faits du snapshot ACTIVE : requirements CURRENT, scénarios explicitement rattachés et changements reliés par `AFFECTS` persisté.

## Requirements

```text
GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace
```

Recherche :

```text
GET /requirements?query=session&offset=0&limit=50
```

Trace :

```text
GET /requirements/{requirementId}/trace?depth=2
```

`depth` : `1..20`.

Les queries ACTIVE n'exposent que les occurrences `TemporalState.CURRENT`.

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

### Acceptance criteria

Le modèle normalisé ne persiste pas encore d'`AcceptanceCriterion` explicite :

```json
{
  "status": "UNAVAILABLE_IN_NORMALIZED_MODEL",
  "criteria": []
}
```

**Scenario != AcceptanceCriterion.**

### Lifecycle

Le snapshot métier publié ne persiste pas un état lifecycle explicite :

```text
status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
lifecycleState = UNAVAILABLE
```

MORPHEUS n'infère jamais un lifecycle absent.

### Blocking conditions

Réutilise `ChangeCompletenessService` et expose les facts tri-state, `unavailableFacts` et findings déterministes M6.

## Versions / historique

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare?fromSnapshotId=...&toSnapshotId=...
```

Contrat M3 préservé :

```text
published history = RETIRED* -> ACTIVE
BUILDING/VALIDATING/READY/FAILED jamais exposés comme historique
historical requirements = CURRENT relativement au snapshot adressé
comparison = ADDED / MODIFIED / REMOVED / UNCHANGED
```

Dans une différence :

```text
ADDED   -> source = null, target = requirement
REMOVED -> source = requirement, target = null
```

Aucun endpoint M11 ne réactive un snapshot RETIRED et aucun endpoint de rollback mutation n'est exposé.

## Diagnostics

```text
GET /api/v1/projects/{projectId}/diagnostics
```

Réutilise `QualityReportService` et `CompactQualityReportService` M6.

## JSON d'entrée

Les POST avec body exigent :

```text
Content-Type: application/json
body <= 65536 octets
JSON syntaxiquement valide
aucun champ inconnu
aucun token JSON supplémentaire
```

Les query parameters inconnus sont également rejetés.

## Frontières d'écriture

M11 expose seulement les mutations opérationnelles nécessaires au mode headless :

```text
register project
sync project
```

Il n'expose **pas** :

```text
RequirementDelta APPLY
PROMOTE
ACTIVATE direct
rollback mutation
write requirement/change
```

La publication de sync passe par `ProjectSnapshotImportService` et conserve le lifecycle candidat/activation validé depuis M3/M9.

## OpenAPI

Contrat machine-readable : [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml).

## Validation attendue

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Le packaging doit prouver :

```text
MCP/API packaging proof: PASS
jpackage avec jdk.httpserver
launcher --version PASS
launcher --json version PASS
GET /api/v1/health sur launcher packagé PASS
Windows ZIP PASS
```
