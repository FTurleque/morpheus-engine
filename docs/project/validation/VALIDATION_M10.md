# Validation M10 — Serveur MCP stdio natif

Date : 24 juillet 2026  
Statut : **VALIDÉ**

## Question de sortie

> **MORPHEUS peut-il exposer ses capacités de lecture d'intention/specification à des agents via un serveur MCP local stdio natif, avec des tools déterministes, des JSON Schemas stricts, des erreurs explicites et aucune logique métier essentielle dans les handlers MCP, tout en restant utilisable sans serveur HTTP, Docker, MINOS, NEXUS ou JARVIS ?**

**Réponse : OUI.**

## Baseline

```text
M9 merge = 2533f325c6ef55070857a8bf75808648d99da5a2
M9 gate  = Windows 298/298 + Linux 298/298 PASS
```

## Implémentation validée

```text
morpheus-mcp module
Java MCP SDK officiel 2.0.0
McpServer.sync
StdioServerTransportProvider
validateToolInputs=true
morpheus mcp --stdio
SQLite partagé avec CLI
stdout réservé au protocole MCP
stderr réservé aux diagnostics launcher/runtime
```

## Catalogue M10

Les quatorze tools exposés sont read-only :

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

Aucun tool de write, sync mutation, apply, promote ou activate n'est exposé.

## Sémantique validée

```text
Scenario != AcceptanceCriterion
get_acceptance_criteria -> UNAVAILABLE_IN_NORMALIZED_MODEL + criteria=[]
lifecycle non inféré depuis snapshot
get_change_status -> UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
ACTIVE/CURRENT policies preserved
absence != fait inventé
```

`get_blocking_conditions` réutilise les facts tri-state et findings déterministes M6.

`get_specification_context` agrège uniquement des faits du snapshot ACTIVE, des requirements CURRENT, des scenarios explicitement rattachés et des changes reliés par `AFFECTS` persisté.

## JSON Schemas

Les inputs MCP sont stricts :

```text
type = object
additionalProperties = false
required explicites
limit          1..100
depth          1..20
maxAgeMinutes  1..525600
offset         >= 0
```

La validation du SDK est active avant handler et les bornes sont également appliquées dans le service.

## Gate Windows Maven — PASS

Head Java testé :

```text
19d38faf9c1e8b576bfb289d4204ed50e331e6f9
```

Résultat :

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

Le module Memory Store n'a pas de tests propres et n'ajoute donc aucun test au total.

## Preuve MCP STDIO réelle

`MorpheusMcpStdioIntegrationTest` lance un processus Java réel et valide :

```text
initialize
notifications/initialized
tools/list
tools/call get_sync_status
trace_requirement depth=99 -> rejet schema avant handler
```

Les tests ciblés M10 :

```text
MorpheusMcpServerContractTest       1/1 PASS
MorpheusMcpToolCatalogTest          3/3 PASS
MorpheusMcpToolServiceTest          1/1 PASS
MorpheusMcpStdioIntegrationTest     1/1 PASS
MorpheusMainTest                    5/5 PASS
```

## Architecture

```text
Architecture Tests 149/149 PASS
```

`LayerDependencyTest` protège explicitement les dépendances entrantes :

```text
com.morpheus.domain      -/-> com.morpheus.mcp
com.morpheus.application -/-> com.morpheus.mcp
```

La logique métier essentielle reste dans domain/application ; MCP reste un adapter de validation, orchestration et mapping.

## Packaging Windows — PASS

Head final packagé :

```text
042c5483889d63438bf78bf98346d62f0309210e
```

Le delta depuis le head Java testé contient uniquement :

```text
distribution/build-portable.ps1
docs/roadmap/M10_EXECUTION.md
```

Aucun code Java ni test n'a changé entre le gate Maven et le gate packaging.

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

Smoke :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Artefact :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

Le shaded JAR est contrôlé avant `jpackage` et contient :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

Le correctif final d'archivage Windows ajoute retries/backoff, suppression des archives partielles et fail-fast si le ZIP n'est pas effectivement produit.

## Distribution Linux

Le script Linux M10 est aligné sur le même shaded JAR, vérifie les mêmes classes MCP et produit l'app-image/tar.gz avec runtime embarqué. Le gate Linux spécifique M10 n'est pas requis par la porte M10 : la portabilité jpackage Linux avait déjà été prouvée en M9, tandis que le changement M10 porte sur l'embarquement des dépendances MCP dans l'artefact commun vérifié au niveau du shaded JAR.

## ADR acceptées

```text
ADR-0062 — Acceptée — Java MCP SDK officiel + STDIO natif
ADR-0063 — Acceptée — catalogue MCP read-only et sémantique explicite
ADR-0064 — Acceptée — intégration au launcher natif et stdout protocol-clean
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

## Conclusion

M10 satisfait sa question de sortie. MORPHEUS dispose désormais d'un serveur MCP local STDIO natif, strictement read-only, déterministe, snapshot-aware, intégré au launcher portable M9 et protégé par des schemas, tests contractuels, test d'intégration protocolaire réel et gate de packaging.

**M10 est VALIDÉ.**
