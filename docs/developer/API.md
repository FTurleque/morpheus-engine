# API HTTP MORPHEUS

MORPHEUS expose une API JSON locale versionnée.

```text
OpenAPI 3.1.0
API version 1.3.0
base /api/v1
server par défaut http://127.0.0.1:8765/api/v1
```

Le contrat machine complet est [`../openapi/morpheus-v1.yaml`](../openapi/morpheus-v1.yaml).

## Démarrage

```bash
morpheus api
morpheus api --host 127.0.0.1 --port 8765
morpheus --db /path/to/morpheus.db api
```

La CLI, MCP et l’API utilisent la même base SQLite lorsqu’ils reçoivent le même layout.

## Architecture

```text
Domain / Application
        ↑
   CLI  MCP  API
```

`morpheus-api` est un adapter sibling de CLI/MCP. Il ne dépend ni de ces adapters ni des implémentations MINOS/NEXUS/JARVIS.

## Enveloppes

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Réponses JSON UTF-8 avec `Cache-Control: no-store` et `X-Content-Type-Options: nosniff`.

## Endpoints système

```text
GET /api/v1/
GET /api/v1/health
GET /api/v1/version
```

## Projets et synchronisation

```text
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

Les mutations opérationnelles HTTP exposées restent limitées à l’enregistrement projet et à la synchronisation.

## Spécifications et requirements

```text
GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context
GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace
```

Contexte NEXUS live :

```text
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
```

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

Contexte NEXUS live :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

## Orchestration JARVIS read-only

État observable :

```text
GET /api/v1/projects/{projectId}/changes/{changeId}/orchestration
```

Query optionnelle :

```text
lifecycleState=<DRAFT|PROPOSED|SPECIFIED|DESIGNED|PLANNED|IMPLEMENTING|VERIFYING|COMPLETED|ARCHIVED|ABANDONED>
abandonmentReason=<reason>
```

Sans lifecycle explicite :

```text
lifecycle.state  = absent
lifecycle.source = UNAVAILABLE
```

Évaluation :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Body :

```json
{
  "fromState":"PROPOSED",
  "fromAbandonmentReason":null,
  "targetState":"SPECIFIED",
  "abandonmentReason":null,
  "allowBackwardTransitions":false,
  "allowCompletedReopen":false
}
```

Résultats :

```text
ALLOWED | BLOCKED | UNKNOWN | REQUIRES_INPUT
```

Le POST est une évaluation pure : aucune transition, aucun provider et aucun snapshot ne sont mutés.

## Versions et diagnostics

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare
GET /api/v1/projects/{projectId}/diagnostics
```

## MINOS

```text
GET /api/v1/integrations/minos/status
GET /api/v1/projects/{projectId}/external-references?ownerId=<domainIdentity>
GET /api/v1/projects/{projectId}/external-references/{referenceId}/resolution
```

La résolution live expose une observation avec `persisted=false` et ne réécrit pas l’historique publié.

## NEXUS

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Le `ContextBundle` NEXUS reste live et non persisté.

## Validation des bodies

Les POST JSON imposent :

```text
Content-Type: application/json
body <= 65536 octets
JSON valide
champs connus uniquement
aucun token supplémentaire
```

## Frontière d’écriture

L’API n’expose toujours pas de mutation pour :

```text
RequirementDelta APPLY
PROMOTE
ACTIVATE direct
rollback mutation
write requirement/change
apply lifecycle transition
persist external-reference live resolution
persist NEXUS ContextBundle
NEXUS project add/index/rebuild
JARVIS orchestration action
```

## Tests de contrat

La preuve M14 inclut `morpheus-api` **9/9 PASS**, dont le contrat d’orchestration JARVIS.

Voir aussi : [Architecture](ARCHITECTURE.md), [MCP](MCP.md), [OpenAPI](../openapi/morpheus-v1.yaml).
