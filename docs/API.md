# MORPHEUS API HTTP — M11 à M14

Statut : **M11/M12/M13/M14 validés**

MORPHEUS expose une API JSON locale versionnée `/api/v1`. M12 ajoute MINOS, M13 NEXUS et M14 un contrat read-only destiné aux orchestrateurs comme JARVIS.

## Lancement

```text
morpheus api
morpheus api --host 127.0.0.1 --port 8765
morpheus --db /path/to/morpheus.db api
```

Défauts : `host=127.0.0.1`, `port=8765`. Même SQLite que CLI/MCP.

## Architecture

```text
Domain / Application
        ↑
   CLI  MCP  API
        ↑
 generic ports / use cases
```

`morpheus-api` ne dépend ni de CLI/MCP, ni des intégrations MINOS/NEXUS, ni de JARVIS.

## Enveloppes

```json
{"apiVersion":"v1","data":{}}
```

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

HTTP JSON UTF-8, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.

## M11 conservé

Les routes historiques projet/sync/specification/requirement/change/version/diagnostics restent inchangées, notamment :

```text
GET /projects/{projectId}/changes/{changeId}/status
GET /projects/{projectId}/changes/{changeId}/blocking-conditions
```

Ces routes historiques ne sont pas redéfinies par M14.

## M12 — MINOS

```text
GET /integrations/minos/status
GET /projects/{projectId}/external-references?ownerId=<domain-identity>
GET /projects/{projectId}/external-references/{referenceId}/resolution
```

Résolution live : `stored`, `observed`, `persisted=false`.

## M13 — NEXUS

```text
GET  /integrations/nexus/status
POST /projects/{projectId}/requirements/{requirementId}/augmented-context
POST /projects/{projectId}/changes/{changeId}/augmented-context
```

Le bundle NEXUS reste live et non persisté.

# M14 — contrat d'orchestration JARVIS

## État d'orchestration

```text
GET /api/v1/projects/{projectId}/changes/{changeId}/orchestration
```

Query optionnelle :

```text
lifecycleState=<DRAFT|PROPOSED|SPECIFIED|DESIGNED|PLANNED|IMPLEMENTING|VERIFYING|COMPLETED|ARCHIVED|ABANDONED>
abandonmentReason=<reason>   # uniquement avec ABANDONED
```

Sans `lifecycleState` :

```text
lifecycle.state  = absent
lifecycle.source = UNAVAILABLE
```

Avec état explicite :

```text
lifecycle.source = CALLER_SUPPLIED
```

MORPHEUS n'infère jamais le lifecycle depuis tasks, archives, timestamps ou qualité.

Réponse M14 :

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

`acceptanceCriteria.status=UNAVAILABLE_IN_NORMALIZED_MODEL` tant qu'aucune projection explicite n'existe. `Scenario` n'est jamais converti en `AcceptanceCriterion`.

`blockingConstraints.status=UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED` : les contraintes applicables sont listées séparément, sans fabriquer un caractère bloquant.

## Évaluation de transition

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Body strict :

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

Required : `fromState`, `targetState`.

Décisions :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

`UNKNOWN` signifie qu'au moins un fait requis est `UNAVAILABLE`. Il n'est pas transformé en `BLOCKED`.

`REQUIRES_INPUT` couvre une information volontaire manquante, notamment la raison nécessaire pour cibler `ABANDONED`.

Ce POST est une **évaluation pure** : aucune transition n'est appliquée, aucun snapshot/provider n'est muté.

## JSON d'entrée

Tous les POST avec body exigent :

```text
Content-Type application/json
body <= 65536 octets
JSON valide
aucun champ inconnu
aucun token supplémentaire
```

## Frontières d'écriture

Mutations opérationnelles HTTP autorisées historiquement :

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
apply lifecycle transition
persist external-reference live resolution
persist NEXUS ContextBundle
NEXUS project add/index/rebuild
JARVIS orchestration action
```

## Validation M14

Head MORPHEUS validé : `d44d418ae0f1e528ea09a56cdd8c45647048c740`.

```text
API                                      9/9 PASS
MorpheusJarvisOrchestrationApiContractTest 2/2 PASS
TOTAL MORPHEUS                        357/357 PASS
Architecture                         160/160 PASS
Packaging Windows                         PASS
```

Preuve cross-repo JARVIS sur `58899855bcd3446636c1f274ace8c1bfc8f46930` :

```text
jarvis-core 536 tests
0 failure
0 error
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

OpenAPI machine-readable : [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml).  
Validation : [`VALIDATION_M14.md`](VALIDATION_M14.md).  
Contrat M14 : [`JARVIS.md`](JARVIS.md).  
Roadmap : [`roadmap/M14_EXECUTION.md`](roadmap/M14_EXECUTION.md).
