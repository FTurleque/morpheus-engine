# M12 — Plan d'exécution détaillé

Statut : **FONCTIONNELLEMENT COMPLET — gate local pending**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M11 validés et intégrés
M11 merge = e30ed4095700b445fedc4517c22ff447c22238f4
M11 gate  = 314/314 PASS + API HTTP packagée
```

Issue : **#61 — M12 — Intégration optionnelle MINOS et résolution code**  
Branche : `m12/minos-integration`  
PR : **#62 — M12 — intégration optionnelle MINOS et résolution code** (draft)

## Question de sortie

> **MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?**

Réponse actuelle : **implémentation OUI ; preuve locale finale pending**.

## M12-S1 — Contrat cross-engine ✅ implémenté

MORPHEUS réutilise :

```text
ExternalReference
ExternalReferenceResolver
ExternalReferenceResolutionService
ExternalReferenceStore
ExternalTraceabilityQueryService
LINKS_TO_CODE
```

MINOS reste externe. Aucun type `com.minos.*` n'entre dans domain/application.

## M12-S2 — Transport MINOS MCP STDIO ✅ implémenté

```text
MORPHEUS Java 21
  -> Java MCP client 2.0.0
  -> STDIO
  -> process MINOS Java 24
  -> com.minos.mcp.MinosMcpServer
```

Tools requis :

```text
minos_index_status
minos_find_symbols
```

`MinosMcpCodeGateway` initialise un vrai client MCP, vérifie les deux tools et applique un timeout borné.

Preuve implémentée : `MinosMcpTransportIntegrationTest` démarre un vrai subprocess MCP fixture et appelle les deux tools.

## M12-S3 — Coordonnée MINOS exacte ✅ implémenté

```text
system       = MINOS
resourceType = SYMBOL
project      = projet MINOS obligatoire
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId attendu optionnel
```

Le resolver peut récupérer des candidats lexicalement, mais seul :

```text
candidate.symbolKey.equals(externalId)
```

est admissible.

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Aucun fuzzy matching.

## M12-S4 — Révision / stale ✅ implémenté

`minos_index_status.activeSnapshotId` est observé avant la recherche.

```text
revision absente                    -> résolution sur ACTIVE MINOS observé
revision == activeSnapshotId        -> autorisée
revision != activeSnapshotId        -> REVISION_MISMATCH
```

Aucune résolution n'est prétendue contre une autre baseline que celle explicitement demandée.

## M12-S5 — Taxonomie de résolution enrichie ✅ implémenté

Resolver :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

Raisons :

```text
RESOLVED
TARGET_NOT_FOUND
TARGET_UNAVAILABLE
TARGET_REMOVED
TARGET_AMBIGUOUS
TARGET_REVISION_MISMATCH
TARGET_TYPE_UNSUPPORTED
NO_RESOLVER
```

Une référence déjà résolue devient `STALE` lors d'une observation négative ultérieure ; une référence jamais résolue reste `UNRESOLVED`.

## M12-S6 — Résolution live sans mutation ✅ implémenté

Invariant ADR-0041 : même snapshot + même `ExternalReferenceId` + valeur différente = collision.

M12 introduit :

```text
LiveExternalReferenceResolutionService
LiveExternalReferenceResolutionResult
```

Flux :

```text
stored reference
  -> resolve live
  -> observed copy
  -> response
  -X-> putReference(snapshot, changedReference)
```

Le service applicatif ne possède aucune opération d'écriture.

Preuves :

```text
Memory: observed RESOLVED, stored UNVALIDATED unchanged
SQLite: même preuve
SQLite reopen: stored reference identique
```

## M12-S7 — Faits MINOS contrôlés ✅ implémenté

```text
minos.projectId
minos.activeSnapshotId
minos.symbolId
minos.symbolKey
minos.qualifiedName
minos.kind
minos.language
minos.moduleId
minos.fileId
minos.resolutionStatus
minos.providerId
minos.providerVersion
minos.indexRunId
```

Pas de copie du code source complet.

## M12-S8 — Configuration optionnelle ✅ implémenté

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Propriétés Java `morpheus.minos.*` prioritaires sur l'environnement.

États :

```text
DISABLED
CONFIGURED (settings)
INVALID
AVAILABLE (status runtime)
UNAVAILABLE (status runtime)
```

Sans JAR : aucun resolver MINOS n'est enregistré.

MINOS n'est jamais démarré lors d'un simple bootstrap MORPHEUS ; le process n'est ouvert qu'à la demande d'une résolution/status live.

## M12-S9 — CLI ✅ implémenté

```text
minos-status
external-references list --project ID --owner ID
external-references resolve --project ID --reference ID
```

Résolution :

```text
stored
observed
persisted=false
```

Preuve standalone sans MINOS : `NO_RESOLVER`, SQLite inchangée.

## M12-S10 — MCP ✅ implémenté

Le catalogue M10 de 14 tools reste inchangé. Deux tools additifs sont enregistrés :

```text
list_external_references
resolve_external_reference
```

Serveur M12 : **16 tools read-only**.

Preuve : vrai process `MorpheusMain ... mcp --stdio`, initialize, tools/list, tools/call des deux tools M12, sans MINOS installé.

## M12-S11 — HTTP API ✅ implémenté

Routes additives `/api/v1` :

```text
GET /integrations/minos/status
GET /projects/{projectId}/external-references?ownerId=...
GET /projects/{projectId}/external-references/{referenceId}/resolution
```

`morpheus-api` ne dépend pas de `morpheus-integration-minos` ; le launcher injecte des ports applicatifs génériques.

Preuve : vrai HttpServer loopback, status/list/resolve, stored UNVALIDATED + observed RESOLVED + `persisted=false`, SQLite inchangée.

## M12-S12 — Architecture ✅ implémenté

Nouveau module :

```text
morpheus-integration-minos
```

Guards :

```text
domain/application -X-> integration-minos
domain/application -X-> com.minos..
api                 -X-> integration-minos
integration-minos   -X-> cli/api/mcp/store
integration-minos   -X-> com.minos..
CLI = composition root
```

Le module MINOS dépend des contrats application/domain, du MCP SDK et de Jackson ; jamais de l'implémentation MINOS.

## M12-S13 — Documentation ✅ implémenté

```text
docs/MINOS.md
docs/API.md
docs/MCP.md
docs/openapi/morpheus-v1.yaml
docs/roadmap/M12_EXECUTION.md
docs/ROADMAP.md
README.md
distribution/README.md
```

## M12-S14 — Tests ✅ implémentés, exécution pending

Delta de tests attendu depuis M11 :

```text
Application
  ExternalReferenceResolutionServiceTest        +2

MINOS integration module
  MinosIntegrationSettingsTest                   3
  MinosMcpExternalReferenceResolverTest          4
  MinosMcpTransportIntegrationTest               1
                                                   = 8

API
  MorpheusExternalReferenceApiContractTest       1

CLI
  MorpheusMinosCliTest                           2
  MorpheusM12McpStdioIntegrationTest             1
                                                   = 3

Architecture
  LiveExternalReferenceResolutionContractTest    2
  LayerDependencyTest                            +1
                                                   = 3
```

Projection :

```text
M11 baseline 314
M12 delta     17
----------------
TOTAL attendu 331
```

**331 est une projection, pas une preuve tant que Maven n'a pas été exécuté.**

## M12-S15 — Distribution ✅ implémentée, exécution pending

Windows/Linux :

```text
workdir .m12-windows / .m12-linux
morpheus-integration-minos embedded
MCP client embedded
com/minos/* explicitly forbidden
MINOS itself not bundled
jdk.httpserver retained
```

Smokes M12 :

```text
--version
--json version
--json minos-status -> DISABLED without configuration
packaged API /health
```

Installateur optionnel Windows aligné sur `.m12-windows`.

## M12-S16 — Gate final ⏳

Source de vérité :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Attendu :

```text
TOTAL ~331 tests, exact count reported by Maven
Failures 0
Errors 0
Skipped 0
BUILD SUCCESS
MCP/API/MINOS adapter packaging proof: PASS
Packaged standalone MINOS-optional smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

M12 ne sera `VALIDÉ` qu'après preuve reproductible.

## ADR candidates

```text
ADR-0069 — Proposée — intégration MINOS par MCP STDIO inter-processus
ADR-0070 — Proposée — symbolKey exact + révision explicite
ADR-0071 — Proposée — résolution live sans mutation de snapshot publié
ADR-0072 — Proposée — configuration/surfaces MINOS optionnelles
```

ADR-0007 est **déjà Acceptée — M0**. M12 vise à fournir sa première preuve de production cross-engine réelle ; son statut historique n'est pas réécrit.

## Hors périmètre M12

```text
copie du domaine MINOS dans MORPHEUS
compile dependency com.minos.*
indexation SCIP pilotée par MORPHEUS
installation automatique de MINOS
MINOS embarqué dans le ZIP MORPHEUS
fuzzy code matching
source code complet copié dans MORPHEUS
NEXUS ranking/compression
JARVIS orchestration
```
