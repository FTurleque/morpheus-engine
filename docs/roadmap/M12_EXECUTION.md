# M12 — Plan d'exécution détaillé

Statut : **✅ VALIDÉ — intégration optionnelle MINOS et résolution code**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M11 validés et intégrés
M11 merge = e30ed4095700b445fedc4517c22ff447c22238f4
M11 gate  = 314/314 PASS + API HTTP packagée
```

Issue : **#61 — M12 — Intégration optionnelle MINOS et résolution code**  
Branche : `m12/minos-integration`  
PR : **#62 — M12 — intégration optionnelle MINOS et résolution code**

## Question de sortie

> **MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?**

Réponse : **OUI — preuve locale finale reproductible obtenue.**

## M12-S1 — Contrat cross-engine ✅

MORPHEUS réutilise ses contrats `ExternalReference`, resolver/store et traçabilité. MINOS reste un système externe.

```text
domain/application -X-> com.minos..
```

## M12-S2 — Transport MINOS MCP STDIO ✅

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

Preuve : `MinosMcpTransportIntegrationTest` **1/1 PASS** avec vrai subprocess MCP fixture.

## M12-S3 — Coordonnée MINOS exacte ✅

```text
system       = MINOS
resourceType = SYMBOL
project      = projet MINOS obligatoire
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId attendu optionnel
```

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Aucun fuzzy matching.

## M12-S4 — Révision / stale ✅

```text
revision absente             -> ACTIVE MINOS observé
revision == activeSnapshotId -> résolution autorisée
revision != activeSnapshotId -> REVISION_MISMATCH
```

Aucune résolution n'est prétendue contre une baseline différente de celle demandée.

## M12-S5 — Taxonomie de résolution ✅

Resolver :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

Raisons MORPHEUS :

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

## M12-S6 — Résolution live sans mutation ✅

```text
stored reference
  -> resolve live
  -> observed copy
  -> response
  -X-> mutation du snapshot publié
```

`LiveExternalReferenceResolutionService` n'expose aucune écriture.

Preuves :

```text
Memory        -> stored unchanged
SQLite        -> stored unchanged
SQLite reopen -> stored unchanged
persisted     -> false
```

## M12-S7 — Faits MINOS contrôlés ✅

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

Le code source complet n'est pas copié dans MORPHEUS.

## M12-S8 — Configuration optionnelle ✅

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Propriétés Java `morpheus.minos.*` prioritaires sur l'environnement.

Sans JAR : intégration `DISABLED`, aucun resolver MINOS, `NO_RESOLVER` normal.

## M12-S9 — CLI ✅

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

Module CLI final : **15/15 PASS**.

## M12-S10 — MCP ✅

Les 14 tools M10 restent inchangés et deux tools read-only sont ajoutés :

```text
list_external_references
resolve_external_reference
```

Serveur M12 : **16 tools read-only**.

Vrai process MORPHEUS MCP STDIO validé sans MINOS installé.

## M12-S11 — HTTP API ✅

Routes additives `/api/v1` :

```text
GET /integrations/minos/status
GET /projects/{projectId}/external-references?ownerId=...
GET /projects/{projectId}/external-references/{referenceId}/resolution
```

`morpheus-api` ne dépend pas de `morpheus-integration-minos` ; le launcher injecte des ports applicatifs génériques.

Module API final : **5/5 PASS**.

## M12-S12 — Architecture ✅

Nouveau module :

```text
morpheus-integration-minos
```

Guards validés :

```text
domain/application -X-> integration-minos
domain/application -X-> com.minos..
api                -X-> integration-minos
integration-minos  -X-> cli/api/mcp/store
integration-minos  -X-> com.minos..
CLI = composition root
```

Architecture : **153/153 PASS**.

## M12-S13 — Documentation ✅

```text
docs/VALIDATION_M12.md
docs/MINOS.md
docs/API.md
docs/MCP.md
docs/openapi/morpheus-v1.yaml
docs/roadmap/M12_EXECUTION.md
docs/ROADMAP.md
docs/adr/0069-minos-mcp-stdio-integration.md
docs/adr/0070-exact-minos-symbol-reference-and-revision.md
docs/adr/0071-live-external-resolution-without-snapshot-mutation.md
docs/adr/0072-optional-minos-runtime-configuration-and-surfaces.md
README.md
distribution/README.md
```

## M12-S14 — Tests ✅

Head Java/packaging testé :

```text
ca0073a875bcf28114a2945b141fc8c45f88930e
```

Résultat final :

```text
Domain             21/21 PASS
Application        84/84 PASS
OpenSpec           26/26 PASS
Synthetic           7/7 PASS
SQLite              7/7 PASS
MINOS Integration   8/8 PASS
MCP                 5/5 PASS
API                 5/5 PASS
CLI                15/15 PASS
Architecture      153/153 PASS
-------------------------------
TOTAL             331/331 PASS
Failures             0
Errors               0
Skipped              0
BUILD SUCCESS
```

## M12-S15 — Distribution ✅

Windows :

```text
MCP/API/MINOS adapter packaging proof: PASS
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
MINOS state=DISABLED sans configuration
Packaged standalone MINOS-optional smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
33,587,925 bytes
```

Le ZIP embarque MORPHEUS, son runtime Java, MCP/API et le client/adaptateur MINOS. **MINOS lui-même n'est pas embarqué ni requis.**

## M12-S16 — Gate final ✅

Commandes exécutées :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Résultat : **PASS**.

Le smoke cross-repo `distribution/test-minos-compatibility.ps1` contre un vrai JAR MINOS reste une preuve additionnelle utile, mais n'est pas requis pour le gate autonome M12.

## ADR

```text
ADR-0069 — Acceptée — M12 — MCP STDIO inter-processus
ADR-0070 — Acceptée — M12 — symbolKey exact + révision explicite
ADR-0071 — Acceptée — M12 — résolution live sans mutation du snapshot publié
ADR-0072 — Acceptée — M12 — configuration/surfaces MINOS optionnelles
```

ADR-0007 est déjà **Acceptée — M0** ; M12 lui apporte une preuve cross-engine concrète sans réécrire son statut historique.

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

## Décision finale

**M12 est VALIDÉ.**

La PR #62 peut être passée en Ready for review et l'issue #61 clôturée. La fusion reste soumise à une autorisation explicite distincte.
