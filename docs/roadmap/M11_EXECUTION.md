# M11 — Plan d'exécution détaillé

Statut : **M11 FONCTIONNELLEMENT COMPLET — gate local et packaging pending**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M10 validés et intégrés
M10 merge = cfa327e61ee9a843e3891c5600b73f50faa71f50
M10 gate  = 307/307 PASS + MCP STDIO + Windows ZIP
```

Issue : **#59 — M11 — API HTTP headless locale**  
Branche : `m11/api-headless`  
PR : **#60 — M11 — API HTTP headless locale** (draft jusqu'au gate)

## Question de sortie

> **MORPHEUS peut-il fonctionner comme service headless local via une API HTTP versionnée et stable, couvrant projets, spécifications, requirements, changements, contraintes, critères disponibles, traçabilité, versions, contexte, synchronisation et diagnostics, sans déplacer les règles métier hors de `morpheus-application` / `morpheus-domain` ?**

Réponse actuelle : **implémentation OUI ; preuve exécutable finale pending**.

## M11-S1 — Transport HTTP local ✅ implémenté

Transport : module JDK 21 `jdk.httpserver`, sans framework serveur externe.

```text
morpheus api --host 127.0.0.1 --port 8765
prefix = /api/v1
content-type = application/json; charset=utf-8
bind par défaut = loopback
```

`MorpheusHttpServer` utilise `HttpServer` et un executor de virtual threads. Les tests utilisent un vrai bind `127.0.0.1:0`.

## M11-S2 — Contrat HTTP v1 ✅ implémenté

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Codes implémentés :

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

## M11-S3 — Service / projets / sync ✅ implémenté

```text
GET  /api/v1/health
GET  /api/v1/version
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

Enregistrement projet idempotent par `SourceLocator` racine.

Pipeline sync :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService
```

La mutation HTTP utilise `SyncPlan.Trigger.manual().forced()` et publie donc volontairement **FULL_REBUILD**. Aucun receipt incrémental fictif.

Un test dédié corrompt un workspace après une première publication et vérifie qu'un sync en échec laisse l'ACTIVE précédent intact et n'ajoute aucun snapshot RETIRED.

## M11-S4 — Spécifications / requirements / contexte ✅ implémenté

```text
GET /api/v1/projects/{projectId}/specifications
GET /api/v1/projects/{projectId}/specifications/{specificationId}
GET /api/v1/projects/{projectId}/specifications/{specificationId}/context
GET /api/v1/projects/{projectId}/requirements
GET /api/v1/projects/{projectId}/requirements/{requirementId}
GET /api/v1/projects/{projectId}/requirements/{requirementId}/trace
```

Bornes :

```text
offset >= 0
1 <= limit <= 100
1 <= depth <= 20
```

Un projet absent retourne 404 ; un projet connu sans ACTIVE retourne 409 sur une query publiée.

## M11-S5 — Changements ✅ implémenté

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
acceptance absent -> UNAVAILABLE_IN_NORMALIZED_MODEL + criteria=[]
lifecycle non persisté -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

Les services M5/M6/M10 sont réutilisés ; le router HTTP ne recrée pas les règles métier.

## M11-S6 — Versions / historique publié ✅ implémenté

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare?fromSnapshotId=...&toSnapshotId=...
```

Services M3 réutilisés :

```text
PublishedSnapshotHistoryService
HistoricalRequirementQueryService
RequirementSnapshotComparisonService
```

Contrats :

```text
published history = RETIRED* -> ACTIVE
candidate snapshots never leak
historical query = explicit ACTIVE/RETIRED snapshot
comparison = ADDED/MODIFIED/REMOVED/UNCHANGED
ADDED source=null
REMOVED target=null
```

Aucun endpoint de rollback/apply/promote/activate.

## M11-S7 — Diagnostics ✅ implémenté

```text
GET /api/v1/projects/{projectId}/diagnostics
```

Réutilise :

```text
QualityReportService
RequirementQualityService
TaskQualityService
AcceptanceQualityService
ChangeCompletenessService
DecisionReferenceQualityService
CompactQualityReportService
```

## M11-S8 — DTO / JSON ✅ implémenté

Jackson 3.0.3 est aligné sur la stack déjà embarquée avec MCP :

```text
tools.jackson:jackson-bom:3.0.3
tools.jackson.core:jackson-databind:3.0.3
JsonMapper.builder()
FAIL_ON_UNKNOWN_PROPERTIES
FAIL_ON_TRAILING_TOKENS
```

Sorties : enveloppes M11 + `CanonicalJsonSerializer`.

Entrées :

```text
unknown JSON fields rejected
trailing JSON tokens rejected
missing required fields rejected
body <= 65536 bytes
Content-Type application/json required lorsqu'un body est présent
unknown query parameters rejected
```

## M11-S9 — Architecture ✅ implémentée

```text
morpheus-domain      -X-> morpheus-api
morpheus-application -X-> morpheus-api
morpheus-api         -X-> morpheus-cli
morpheus-api         -X-> morpheus-mcp
```

ArchUnit étendu et `morpheus-api` ajouté au classpath d'architecture.

## M11-S10 — Launcher / distribution ✅ implémenté

Launcher unique :

```text
morpheus <CLI command>
morpheus mcp --stdio
morpheus api --host 127.0.0.1 --port 8765
```

`ApiLaunchOptions` réutilise `CliLayout` et les options M9 :

```text
--data-dir
--config-dir
--db
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

`--json` est rejeté en mode serveur API.

Packaging Windows/Linux :

```text
workdirs .m11-windows / .m11-linux
MCP/API classes vérifiées dans l'uber-JAR
Jackson JsonMapper vérifié dans l'uber-JAR
jpackage --add-modules jdk.httpserver
runtime jdk.httpserver vérifié sur Linux
Windows packaged GET /api/v1/health smoke
```

L'installateur Windows optionnel pointe sur l'app-image M11.

## M11-S11 — Documentation ✅ implémentée

```text
docs/API.md
docs/openapi/morpheus-v1.yaml
docs/roadmap/M11_EXECUTION.md
docs/adr/0065-jdk-httpserver-local-api.md
docs/adr/0066-versioned-http-api-contract.md
docs/adr/0067-explicit-conservative-http-sync.md
docs/adr/0068-native-launcher-headless-api.md
```

OpenAPI 3.1 décrit l'intégralité de `/api/v1`.

## M11-S12 — Tests ✅ implémentés, exécution finale pending

```text
MorpheusApiContractTest
MorpheusApiProjectSyncIntegrationTest
  - workflow complet + SQLite reopen
  - failed sync preserves ACTIVE
MorpheusApiHistoryContractTest
MorpheusMainTest étendu API
LayerDependencyTest étendu API
```

Les tests démarrent un vrai serveur loopback et utilisent `java.net.http.HttpClient`.

Preuves codées :

```text
health/version
stable JSON envelope + content-type
400/404/405/409/415
strict JSON + query params
project registration idempotent
OpenSpec HTTP sync -> FULL_REBUILD
failed sync preserves ACTIVE
SQLite reopen
requirements/specifications/changes
trace/context/acceptance/lifecycle/blockers
quality diagnostics
published history + historical requirements + compare
launcher parsing/help
architecture boundaries
```

Total projet attendu si tous les tests passent : **314 tests** (projection, non preuve).

## M11-S13 — Gate final ⏳ PENDING

Gate local obligatoire, source de vérité :

```powershell
cd N:\workspace-dev\morpheus-engine
git fetch origin
git switch m11/api-headless
git pull --ff-only
.\mvnw.cmd clean test
```

Puis :

```powershell
.\distribution\build-portable.ps1
```

Preuves attendues :

```text
Maven BUILD SUCCESS
anciens tests M0-M10 verts
nouveau module MORPHEUS API vert
HTTP integration réelle verte
architecture verte
MCP/API packaging proof: PASS
jpackage app-image + jdk.httpserver PASS
launcher human/JSON PASS
Packaged API health smoke: PASS
Windows ZIP non vide
```

GitHub Actions M11 reste `workflow_dispatch` et optionnel.

M11 ne sera marqué **VALIDÉ** qu'après preuve reproductible. `VALIDATION_M11.md` ne sera créé qu'après le gate.

## ADR M11

```text
ADR-0065 — Proposée — JDK HttpServer pour l'API locale M11
ADR-0066 — Proposée — contrat HTTP /api/v1 et erreurs JSON stables
ADR-0067 — Proposée — sync HTTP explicite par full snapshot conservateur
ADR-0068 — Proposée — launcher/distribution headless avec jdk.httpserver embarqué
```

Les ADR restent **Proposées** tant que le gate n'est pas fourni.

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

## Décision de sortie actuelle

**M11 est fonctionnellement complet mais non validé.** La dernière porte est le gate local Maven + packaging portable. La PR #60 reste draft et non fusionnée.
