# M11 — Plan d'exécution détaillé

Statut : **✅ M11 VALIDÉ — intégration portée par PR #60**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M10 validés et intégrés
M10 merge = cfa327e61ee9a843e3891c5600b73f50faa71f50
M11 tested head = a7daa9bb7eef1799926ea20b9e96606a388a301f
```

Issue : **#59 — M11 — API HTTP headless locale**  
Branche : `m11/api-headless`  
PR : **#60 — M11 — API HTTP headless locale**

## Question de sortie

> **MORPHEUS peut-il fonctionner comme service headless local via une API HTTP versionnée et stable, couvrant projets, spécifications, requirements, changements, contraintes, critères disponibles, traçabilité, versions, contexte, synchronisation et diagnostics, sans déplacer les règles métier hors de `morpheus-application` / `morpheus-domain` ?**

**Réponse : OUI.**

## M11-S1 — Transport HTTP local ✅

```text
morpheus api --host 127.0.0.1 --port 8765
prefix = /api/v1
transport = JDK 21 jdk.httpserver
bind par défaut = loopback
```

`MorpheusHttpServer` utilise `HttpServer` et un executor de virtual threads. Les tests utilisent un vrai bind `127.0.0.1:0`.

## M11-S2 — Contrat HTTP v1 ✅

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Codes validés :

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

Les réponses ajoutent `Cache-Control: no-store` et `X-Content-Type-Options: nosniff`. Aucune stacktrace n'est exposée.

## M11-S3 — Projets / synchronisation ✅

```text
GET  /api/v1/health
GET  /api/v1/version
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

Pipeline de sync réutilisé :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService
```

La mutation HTTP force **FULL_REBUILD conservateur**. Un test prouve qu'un sync défaillant après publication conserve l'ACTIVE précédent et n'ajoute aucun RETIRED fictif.

## M11-S4 — Spécifications / requirements / contexte ✅

```text
GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context
GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace
```

Bornes : `offset >= 0`, `1 <= limit <= 100`, `1 <= depth <= 20`.

## M11-S5 — Changements ✅

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
lifecycle non persisté -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

## M11-S6 — Versions / historique publié ✅

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare
```

Réutilise M3 :

```text
PublishedSnapshotHistoryService
HistoricalRequirementQueryService
RequirementSnapshotComparisonService
RETIRED* -> ACTIVE
ADDED / MODIFIED / REMOVED / UNCHANGED
ADDED source=null
REMOVED target=null
```

Aucun candidat non publié n'est exposé.

## M11-S7 — Diagnostics ✅

```text
GET /api/v1/projects/{projectId}/diagnostics
```

Réutilise le rapport qualité M6 et sa vue compacte.

## M11-S8 — DTO / JSON ✅

Entrées JSON : Jackson 3.0.3, champs inconnus et trailing tokens rejetés, body limité à 65536 octets, `application/json` obligatoire lorsqu'un body est présent.

Sorties : enveloppes M11 + `CanonicalJsonSerializer`.

## M11-S9 — Architecture ✅

```text
morpheus-domain      -X-> morpheus-api
morpheus-application -X-> morpheus-api
morpheus-api         -X-> morpheus-cli
morpheus-api         -X-> morpheus-mcp
```

L'API reste un adapter sibling de CLI/MCP.

## M11-S10 — Launcher / distribution ✅

Launcher unique :

```text
morpheus <CLI command>
morpheus mcp --stdio
morpheus api --host 127.0.0.1 --port 8765
```

Packaging :

```text
morpheus-api + Jackson dans shaded JAR
jpackage --add-modules jdk.httpserver
workdirs .m11-windows / .m11-linux
Windows packaged GET /api/v1/health smoke réel
```

## M11-S11 — Documentation ✅

```text
docs/API.md
docs/openapi/morpheus-v1.yaml
docs/VALIDATION_M11.md
docs/roadmap/M11_EXECUTION.md
```

OpenAPI 3.1 décrit la surface `/api/v1`.

## M11-S12 — Tests ✅

```text
MorpheusApiContractTest                1/1 PASS
MorpheusApiHistoryContractTest         1/1 PASS
MorpheusApiProjectSyncIntegrationTest  2/2 PASS
MorpheusMainTest                       7/7 PASS
LayerDependencyTest                    3/3 PASS
```

Les tests API utilisent un vrai serveur loopback et `java.net.http.HttpClient`.

## M11-S13 — Gate final ✅

Commande officielle :

```powershell
.\mvnw.cmd clean test
```

Résultat :

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
MORPHEUS MCP              5/5 PASS
MORPHEUS API              4/4 PASS
MORPHEUS CLI             12/12 PASS
Architecture Tests      150/150 PASS
-----------------------------------------------
TOTAL                   314/314 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Total time               49.750 s
Finished                 2026-07-24T14:14:06+02:00
```

Packaging :

```text
uber-JAR BUILD SUCCESS
MCP/API packaging proof: PASS
jpackage app-image + jdk.httpserver: PASS
morpheus.exe --version: PASS
morpheus.exe --json version: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS (attempt 1/8, 33533017 bytes)
```

Artefact :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

## ADR M11

```text
ADR-0065 — ✅ Acceptée — M11
ADR-0066 — ✅ Acceptée — M11
ADR-0067 — ✅ Acceptée — M11
ADR-0068 — ✅ Acceptée — M11
```

## Hors périmètre confirmé

```text
TLS terminé par MORPHEUS
OAuth/OIDC
API publique Internet par défaut
WebSocket/SSE
GraphQL
RequirementDelta apply/promote/activate via HTTP
rollback mutation via HTTP
MINOS/NEXUS/JARVIS obligatoire
```

## Décision finale

```text
M11 = VALIDÉ
question de sortie = OUI
314/314 PASS
Architecture = 150/150 PASS
portable Windows = PASS
packaged API health = PASS
```

La fusion de PR #60 a été explicitement autorisée par l'utilisateur.