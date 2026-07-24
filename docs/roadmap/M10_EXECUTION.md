# M10 — Plan d'exécution détaillé

Statut : **✅ VALIDÉ — serveur MCP STDIO natif**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M9 validés et intégrés
M9 merge = 2533f325c6ef55070857a8bf75808648d99da5a2
M9 gate  = Windows 298/298 + Linux 298/298 PASS
```

Issue : **#57 — M10 — Serveur MCP stdio natif**  
Branche : `m10/mcp-server`  
PR : **#58 — M10 — Serveur MCP stdio natif**

## Question de sortie

> **MORPHEUS peut-il exposer ses capacités de lecture d'intention/specification à des agents via un serveur MCP local stdio natif, avec des tools déterministes, des JSON Schemas stricts, des erreurs explicites et aucune logique métier essentielle dans les handlers MCP, tout en restant utilisable sans serveur HTTP, Docker, MINOS, NEXUS ou JARVIS ?**

**Réponse : OUI.**

## M10-S1 — SDK et transport ✅

```text
io.modelcontextprotocol.sdk:mcp-bom:2.0.0
io.modelcontextprotocol.sdk:mcp:2.0.0
McpServer.sync
StdioServerTransportProvider
validateToolInputs=true
morpheus mcp --stdio
```

Contrat de flux :

```text
stdout = protocole MCP uniquement
stderr = diagnostics launcher/runtime uniquement
HTTP/SSE = hors périmètre M10
Docker = non requis
```

## M10-S2 — Catalogue exact ✅

Quatorze tools read-only :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Aucun tool de mutation, sync, apply, promote ou activate.

## M10-S3 — Sémantique stricte ✅

```text
Scenario != AcceptanceCriterion
absence != fait inventé
lifecycle non inféré depuis snapshot
CURRENT/ACTIVE policy preserved
SQLite state shared with CLI
```

`get_acceptance_criteria` :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
criteria = []
```

`get_change_status` :

```text
status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
lifecycleState = UNAVAILABLE
observableFacts = tri-state facts M6
```

`get_blocking_conditions` réutilise `ChangeCompletenessService` et conserve `unavailableFacts`.

## M10-S4 — JSON Schemas ✅

```text
type = object
additionalProperties = false
required = explicite
limit          1..100
depth          1..20
maxAgeMinutes  1..525600
offset         >= 0
```

Validation SDK avant handler + défense en profondeur dans le service.

## M10-S5 — Requêtes agent-friendly ✅

Réutilisation des services existants :

```text
RequirementQueryService
BusinessContentQueryService
TraceRequirementQueryService
ChangeContextQueryService
ChangeCompletenessService
SyncFreshnessService
CompactQueryViewService
CanonicalJsonSerializer
```

Nouvelle agrégation application :

```text
SpecificationContextQueryService
SpecificationContextResult
```

`get_specification_context` expose uniquement :

```text
Specification du snapshot ACTIVE
Requirements CURRENT paginés
Scenarios explicitement rattachés
Changes reliés par AFFECTS persisté
```

## M10-S6 — Launcher / distribution ✅

```text
morpheus <CLI command>
morpheus mcp --stdio
```

`McpLaunchOptions` réutilise `CliLayout` :

```text
CLI option > MORPHEUS_* > OS default
```

`--json` est interdit en mode MCP.

Le shaded JAR doit contenir avant `jpackage` :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

## M10-S7 — Tests ✅

Tests M10 :

```text
MorpheusMcpServerContractTest       1/1 PASS
MorpheusMcpToolCatalogTest          3/3 PASS
MorpheusMcpToolServiceTest          1/1 PASS
MorpheusMcpStdioIntegrationTest     1/1 PASS
MorpheusMainTest                    5/5 PASS
```

Le test STDIO réel couvre :

```text
initialize
notifications/initialized
tools/list
tools/call
invalid depth=99 -> schema rejection avant handler
```

Architecture :

```text
com.morpheus.domain      -/-> com.morpheus.mcp
com.morpheus.application -/-> com.morpheus.mcp
```

## M10-S8 — Gate Maven ✅ PASS

Head Java testé :

```text
19d38faf9c1e8b576bfb289d4204ed50e331e6f9
```

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
MORPHEUS MCP              5/5 PASS
MORPHEUS CLI             10/10 PASS
Architecture Tests      149/149 PASS
TOTAL                   307/307 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Total time               30.934 s
Finished 2026-07-24T12:38:25+02:00
```

## M10-S9 — Packaging Windows ✅ PASS

Head packagé :

```text
042c5483889d63438bf78bf98346d62f0309210e
```

Le delta depuis le head Java ne modifie que :

```text
distribution/build-portable.ps1
docs/roadmap/M10_EXECUTION.md
```

Preuves :

```text
uber-JAR BUILD SUCCESS
MCP packaging proof: PASS
jpackage app-image PASS
morpheus.exe --version PASS
morpheus.exe --json version PASS
Portable archive creation: PASS (attempt 1/8, 77275075 bytes)
Windows ZIP PASS
runtime Java embarqué PASS
```

Artefact :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

Smoke :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

## ADR M10 ✅

```text
ADR-0062 — Acceptée — Java MCP SDK officiel + STDIO natif
ADR-0063 — Acceptée — catalogue MCP read-only et sémantique explicite
ADR-0064 — Acceptée — intégration au launcher natif et stdout protocol-clean
```

## Documentation

```text
docs/VALIDATION_M10.md
docs/MCP.md
docs/roadmap/M10_EXECUTION.md
docs/adr/0062-official-java-mcp-sdk-and-native-stdio.md
docs/adr/0063-read-only-mcp-tool-contract.md
docs/adr/0064-native-launcher-mcp-routing.md
```

## Hors périmètre confirmé

```text
Streamable HTTP
SSE
OAuth réseau
Docker obligatoire
write tools
sync mutation via MCP
RequirementDelta apply/promote/activate via MCP
MINOS
NEXUS
JARVIS
```

## Décision de sortie

**M10 est VALIDÉ.** La PR #58 peut quitter le mode draft. Sa fusion reste soumise à autorisation explicite.
