# Validation M11 — API HTTP headless locale

Statut : **VALIDÉ — intégration portée par PR #60**

Date : 24 juillet 2026

## Baseline validée

```text
M10 merge = cfa327e61ee9a843e3891c5600b73f50faa71f50
M11 tested head = a7daa9bb7eef1799926ea20b9e96606a388a301f
branch = m11/api-headless
PR = #60
```

## Question de sortie

> MORPHEUS peut-il fonctionner comme service headless local via une API HTTP versionnée et stable, couvrant projets, spécifications, requirements, changements, contraintes, critères disponibles, traçabilité, versions, contexte, synchronisation et diagnostics, sans déplacer les règles métier hors de `morpheus-application` / `morpheus-domain` ?

**Réponse : OUI.**

## Contrat validé

```text
morpheus api --host 127.0.0.1 --port 8765
prefix = /api/v1
transport = JDK 21 jdk.httpserver
JSON = UTF-8, enveloppes versionnées
SQLite = état partagé CLI / MCP / API
bind par défaut = loopback
```

L'API reste un adapter sibling de CLI/MCP. Elle ne déplace aucune règle métier hors de `morpheus-application` / `morpheus-domain`.

Invariants conservés :

```text
ACTIVE / CURRENT préservés
Scenario != AcceptanceCriterion
acceptance absente -> UNAVAILABLE_IN_NORMALIZED_MODEL
lifecycle absent -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
APPLY != PROMOTE != ACTIVATE
aucun endpoint apply/promote/activate/rollback mutation
HTTP sync = FULL_REBUILD conservateur
failed sync never replaces previous ACTIVE
```

## Surface validée

```text
GET  /api/v1/health
GET  /api/v1/version
GET  /api/v1/projects
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/sync
GET  /api/v1/projects/{projectId}/sync-status
GET  /api/v1/projects/{projectId}/specifications
GET  /api/v1/projects/{projectId}/specifications/{specificationId}
GET  /api/v1/projects/{projectId}/specifications/{specificationId}/context
GET  /api/v1/projects/{projectId}/requirements
GET  /api/v1/projects/{projectId}/requirements/{requirementId}
GET  /api/v1/projects/{projectId}/requirements/{requirementId}/trace
GET  /api/v1/projects/{projectId}/changes
GET  /api/v1/projects/{projectId}/changes/{changeId}
GET  /api/v1/projects/{projectId}/changes/{changeId}/constraints
GET  /api/v1/projects/{projectId}/changes/{changeId}/acceptance-criteria
GET  /api/v1/projects/{projectId}/changes/{changeId}/design-decisions
GET  /api/v1/projects/{projectId}/changes/{changeId}/implementation-tasks
GET  /api/v1/projects/{projectId}/changes/{changeId}/context
GET  /api/v1/projects/{projectId}/changes/{changeId}/status
GET  /api/v1/projects/{projectId}/changes/{changeId}/blocking-conditions
GET  /api/v1/projects/{projectId}/versions
GET  /api/v1/projects/{projectId}/versions/{snapshotId}/requirements
GET  /api/v1/projects/{projectId}/versions/compare
GET  /api/v1/projects/{projectId}/diagnostics
```

## Gate Windows officiel

Commande :

```powershell
.\mvnw.cmd clean test
```

Head testé :

```text
a7daa9bb7eef1799926ea20b9e96606a388a301f
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

Preuves API dédiées :

```text
MorpheusApiContractTest                1/1 PASS
MorpheusApiHistoryContractTest         1/1 PASS
MorpheusApiProjectSyncIntegrationTest  2/2 PASS
```

Le gate couvre un vrai `HttpServer` loopback + `java.net.http.HttpClient`, l'enregistrement idempotent, le sync OpenSpec réel, le reopen SQLite, l'historique publié, la comparaison, les erreurs HTTP et la conservation de l'ACTIVE lors d'un sync défaillant.

## Packaging Windows officiel

Commande :

```powershell
.\distribution\build-portable.ps1
```

Résultat :

```text
uber-JAR BUILD SUCCESS
MCP/API packaging proof: PASS
jpackage app-image + jdk.httpserver: PASS
morpheus.exe --version: PASS
morpheus.exe --json version: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS (attempt 1/8, 33533017 bytes)
```

Smoke HTTP réel :

```text
GET http://127.0.0.1:<ephemeral>/api/v1/health
-> 200
-> status=UP
```

Artefact :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

Le ZIP contient son runtime Java, MCP STDIO et l'API HTTP ; aucun JDK séparé n'est requis chez l'utilisateur final.

## Documentation et ADR

```text
docs/API.md
docs/openapi/morpheus-v1.yaml
docs/roadmap/M11_EXECUTION.md
ADR-0065 — Acceptée — M11
ADR-0066 — Acceptée — M11
ADR-0067 — Acceptée — M11
ADR-0068 — Acceptée — M11
```

## Décision finale

```text
M11 = VALIDÉ
question de sortie = OUI
314/314 PASS
Architecture = 150/150 PASS
packaging Windows = PASS
packaged API health = PASS
```

La fusion de PR #60 a été explicitement autorisée par l'utilisateur après validation.