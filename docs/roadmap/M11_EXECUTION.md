# M11 — Plan d'exécution détaillé

Statut : **EN COURS — API/headless**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M10 validés et intégrés
M10 merge = cfa327e61ee9a843e3891c5600b73f50faa71f50
M10 gate  = 307/307 PASS + MCP STDIO + Windows ZIP
```

Issue : **#59 — M11 — API HTTP headless locale**  
Branche : `m11/api-headless`

## Question de sortie

> **MORPHEUS peut-il fonctionner comme service headless local via une API HTTP versionnée et stable, couvrant projets, spécifications, requirements, changements, contraintes, critères disponibles, traçabilité, versions, contexte, synchronisation et diagnostics, sans déplacer les règles métier hors de `morpheus-application` / `morpheus-domain` ?**

Réponse actuelle : **implémentation en cours ; gate pending**.

## M11-S1 — Transport HTTP local

Candidat : module JDK 21 `jdk.httpserver`, sans framework serveur externe.

```text
morpheus api --host 127.0.0.1 --port 8765
prefix = /api/v1
content-type = application/json; charset=utf-8
bind par défaut = loopback
```

`--host` permet une exposition explicite différente ; aucun bind réseau large n'est implicite.

## M11-S2 — Contrat HTTP v1

Succès :

```json
{"apiVersion":"v1","data":{}}
```

Erreur :

```json
{"apiVersion":"v1","error":{"code":"NOT_FOUND","message":"...","details":{}}}
```

Codes :

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

Toutes les réponses ont un JSON valide et déterministe. Les erreurs n'exposent pas de stacktrace.

## M11-S3 — Service / projets / sync

```text
GET  /api/v1/health
GET  /api/v1/version
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
```

`POST /projects` accepte :

```json
{"workspace":"..."}
```

L'enregistrement est idempotent par `SourceLocator` racine.

`POST /projects/{id}/sync` accepte un body optionnel :

```json
{"revision":"opaque-source-revision"}
```

Le sync headless réutilise :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService
```

M11 exécute volontairement une publication **FULL_REBUILD conservatrice** pour la mutation HTTP, comme le launcher officiel M9 ; aucun faux receipt incrémental.

## M11-S4 — Spécifications / requirements / contexte

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

Le filtre requirements utilise `query` et `RequirementQueryService`.

## M11-S5 — Changements

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
acceptance absent -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle non persisté -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
```

Les services M5/M6/M10 sont réutilisés ; aucune règle métier n'est recalculée par le router HTTP.

## M11-S6 — Versions / historique publié

```text
GET /api/v1/projects/{projectId}/versions
GET /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET /api/v1/projects/{projectId}/versions/compare?fromSnapshotId=...&toSnapshotId=...
```

Services M3 obligatoires :

```text
PublishedSnapshotHistoryService
HistoricalRequirementQueryService
RequirementSnapshotComparisonService
```

Donc :

```text
published history = RETIRED* -> ACTIVE
candidate snapshots never leak
historical query = explicit ACTIVE/RETIRED snapshot
comparison = ADDED/MODIFIED/REMOVED/UNCHANGED
```

Aucun endpoint de rollback/apply/promote/activate n'est ajouté en M11.

## M11-S7 — Diagnostics

```text
GET /api/v1/projects/{projectId}/diagnostics
```

Le résultat réutilise le rapport qualité agrégé M6 et sa vue compacte/canonique.

## M11-S8 — DTO / JSON

Entrées JSON : Jackson 3 aligné sur la version déjà présente avec le SDK MCP (`3.0.3`) pour éviter deux stacks JSON concurrentes dans l'uber-JAR.

Sorties : DTO/enveloppes M11 + `CanonicalJsonSerializer` pour un ordre stable lorsque les vues applicatives le permettent.

Contraintes d'entrée :

```text
unknown JSON fields rejected
missing required fields rejected
body size bounded
Content-Type application/json required on POST with body
```

## M11-S9 — Architecture

```text
morpheus-domain      -X-> morpheus-api
morpheus-application -X-> morpheus-api
morpheus-api         -X-> morpheus-cli
morpheus-api         -X-> morpheus-mcp
```

L'API dépend des ports/services application, du provider OpenSpec uniquement pour l'opération explicite de sync, et des adapters SQLite pour son runtime local.

## M11-S10 — Launcher / distribution

```text
morpheus api --host 127.0.0.1 --port 8765
```

Options M9 :

```text
--data-dir
--config-dir
--db
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Le shaded JAR M11 embarque `morpheus-api` + Jackson. `jpackage` doit inclure explicitement le module `jdk.httpserver` via `--add-modules jdk.httpserver`.

## M11-S11 — Tests

Preuves minimales :

```text
MorpheusApiContractTest
MorpheusApiProjectSyncIntegrationTest
MorpheusApiHistoryContractTest
MorpheusMain API routing test
architecture dependency tests
```

Les tests démarrent un vrai `HttpServer` sur `127.0.0.1:0` et utilisent `java.net.http.HttpClient`.

Preuves attendues :

```text
health/version
JSON envelope + content-type
404/405/409/415
project registration idempotent
OpenSpec sync -> FULL_REBUILD published snapshot
read after SQLite reopen
requirements/changes/trace/context/diagnostics
published history + historical requirements + compare
strict request JSON
```

## M11-S12 — Gate final

Gate local obligatoire :

```powershell
.\mvnw.cmd clean test
```

Puis :

```powershell
.\distribution\build-portable.ps1
```

Le packaging M11 doit vérifier la présence de l'adapter API dans l'uber-JAR et réussir un smoke health sur le launcher packagé.

M11 ne sera marqué **VALIDÉ** qu'après preuve reproductible.

## ADR M11

```text
ADR-0065 — Proposée — JDK HttpServer pour l'API locale M11
ADR-0066 — Proposée — contrat HTTP /api/v1 et erreurs JSON stables
ADR-0067 — Proposée — sync HTTP explicite par full snapshot conservateur
ADR-0068 — Proposée — launcher/distribution headless avec jdk.httpserver embarqué
```

## Hors périmètre M11

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
