# M10 — Plan d'exécution détaillé

Statut : **M10 FONCTIONNELLEMENT COMPLET — Maven 307/307 PASS, packaging Windows à revalider**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M9 validés et intégrés
M9 merge = 2533f325c6ef55070857a8bf75808648d99da5a2
M9 gate  = Windows 298/298 + Linux 298/298 PASS
```

Issue : **#57 — M10 — Serveur MCP stdio natif**  
Branche : `m10/mcp-server`  
PR : **#58 — M10 — Serveur MCP stdio natif** (draft jusqu'au gate final)

## Question de sortie

> **MORPHEUS peut-il exposer ses capacités de lecture d'intention/specification à des agents via un serveur MCP local stdio natif, avec des tools déterministes, des JSON Schemas stricts, des erreurs explicites et aucune logique métier essentielle dans les handlers MCP, tout en restant utilisable sans serveur HTTP, Docker, MINOS, NEXUS ou JARVIS ?**

Réponse actuelle : **implémentation et gate Maven OUI ; archive portable Windows à revalider après correction du verrou transitoire**.

## M10-S1 — SDK et transport ✅ implémenté

SDK : Java MCP SDK officiel `2.0.0`, via BOM Maven.

```text
io.modelcontextprotocol.sdk:mcp-bom:2.0.0
io.modelcontextprotocol.sdk:mcp
```

Transport :

```text
morpheus mcp --stdio
McpServer.sync
StdioServerTransportProvider
validateToolInputs=true
stdout = protocole MCP uniquement
stderr = diagnostics launcher/runtime uniquement
HTTP = hors périmètre M10
Docker = non requis
```

Module : `morpheus-mcp`.

## M10-S2 — Catalogue de tools ✅ implémenté

Catalogue M10 exact :

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

Tous les tools M10 sont **read-only**. Aucun tool de mutation, sync, apply, promote ou activate n'est exposé.

Implémentation :

```text
MorpheusMcpToolCatalog
MorpheusMcpToolService
MorpheusMcpRuntime
MorpheusMcpServer
```

## M10-S3 — Sémantique stricte ✅ implémenté

Les handlers MCP ne recréent pas les règles métier. Ils valident les arguments, appellent les services/ports MORPHEUS existants puis traduisent le résultat en réponse MCP.

Invariants :

```text
Scenario != AcceptanceCriterion
lifecycle non inféré depuis snapshot
absence != fait inventé
CURRENT/ACTIVE policy preserved
SQLite state shared with CLI
no promotion / activation / write tool
```

`get_acceptance_criteria` vérifie l'existence du changement puis retourne explicitement :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
criteria = []
```

`get_change_status` retourne explicitement :

```text
status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT
lifecycleState = UNAVAILABLE
observableFacts = tri-state facts M6
```

`get_blocking_conditions` réutilise `ChangeCompletenessService` et conserve séparément `unavailableFacts`.

## M10-S4 — Schemas MCP ✅ implémenté

Chaque tool possède un JSON Schema d'entrée strict :

```text
type = object
additionalProperties = false
required = explicite
limit = 1..100
depth = 1..20
maxAgeMinutes = 1..525600
offset >= 0
```

La validation SDK est active avant handler. Le service applique les mêmes bornes en défense en profondeur.

## M10-S5 — Requêtes agent-friendly ✅ implémenté

Les tools réutilisent les services existants :

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

Nouvelle agrégation applicative :

```text
SpecificationContextQueryService
SpecificationContextResult
```

`get_specification_context` agrège uniquement des faits du snapshot ACTIVE :

```text
Specification
CURRENT Requirements paginés
Scenarios explicitement rattachés aux requirements de la page
Changes uniquement par AFFECTS persisté vers les requirements de la specification
```

Aucun lien n'est synthétisé.

## M10-S6 — Launcher / distribution ✅ implémenté

Le launcher natif M9 est le point d'entrée commun :

```text
morpheus <CLI command>
morpheus mcp --stdio
```

`McpLaunchOptions` réutilise `CliLayout` et les priorités M9 :

```text
CLI option > MORPHEUS_* > default OS
```

`--json` est interdit en mode MCP car stdout appartient au protocole.

Le help du launcher officiel documente `mcp --stdio`.

Le module CLI dépend de `morpheus-mcp`, donc le shaded JAR et les app-images embarquent le serveur. Les scripts de distribution vérifient avant `jpackage` :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

Workdirs :

```text
Windows -> dist/.m10-windows
Linux   -> dist/.m10-linux
```

L'installateur Windows optionnel est aligné sur l'app-image M10.

Le premier gate packaging Windows a prouvé :

```text
uber-JAR BUILD SUCCESS
MCP packaging proof PASS
jpackage app-image PASS
morpheus.exe --version PASS
morpheus.exe --json version PASS
```

L'étape `Compress-Archive` a ensuite rencontré un verrou Windows transitoire sur `morpheus.exe`. Le script imprimait à tort la ligne finale de succès malgré l'erreur du module PowerShell Archive. Le correctif `2afcba30c94e842937b0c336a1cf1f16c40fb139` ajoute :

```text
-ErrorAction Stop
8 tentatives avec backoff
suppression du ZIP partiel entre tentatives
vérification d'existence du ZIP
vérification taille > 0
échec explicite après épuisement des tentatives
```

Le ZIP doit donc être revalidé sur ce correctif.

## M10-S7 — Tests ✅ VALIDÉS WINDOWS

Tests M10 ajoutés :

```text
MorpheusMcpToolCatalogTest
MorpheusMcpToolServiceTest
MorpheusMcpServerContractTest
MorpheusMcpStdioIntegrationTest
MorpheusMainTest étendu
LayerDependencyTest étendu
```

`MorpheusMcpToolServiceTest` publie un fixture complet en SQLite et appelle les 14 tools.

Le test STDIO lance un vrai processus Java et couvre :

```text
initialize
notifications/initialized
tools/list
tools/call get_sync_status
trace_requirement depth=99 -> rejet schema avant handler
```

Les tests d'architecture interdisent désormais explicitement :

```text
com.morpheus.domain      -> com.morpheus.mcp
com.morpheus.application -> com.morpheus.mcp
```

Gate Windows exécuté sur le head code :

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

Warnings observés mais non bloquants : SQLite native-access futur, SLF4J NOP, API MCP dépréciée signalée par javac, et avertissements classiques du Maven Shade Plugin sur ressources/module-info.

## M10-S8 — Documentation ✅ implémentée

```text
docs/MCP.md
docs/roadmap/M10_EXECUTION.md
docs/adr/0062-official-java-mcp-sdk-and-native-stdio.md
docs/adr/0063-read-only-mcp-tool-contract.md
docs/adr/0064-native-launcher-mcp-routing.md
```

## M10-S9 — Gate final ⏳ PACKAGING WINDOWS À REVALIDER

Le gate Maven est **PASS 307/307**. Le seul point restant est la création vérifiée du ZIP après le correctif d'archivage.

Commandes :

```powershell
cd N:\workspace-dev\morpheus-engine
git pull --ff-only
git rev-parse HEAD
.\distribution\build-portable.ps1
```

Le head doit contenir au minimum :

```text
2afcba30c94e842937b0c336a1cf1f16c40fb139
```

Preuves restantes attendues :

```text
MCP packaging proof: PASS
jpackage app-image PASS
launcher --version PASS
launcher --json version PASS
Portable archive creation: PASS
Windows ZIP non vide produit
```

GitHub Actions M10 reste volontairement `workflow_dispatch` et optionnel.

M10 ne sera marqué **VALIDÉ** qu'après cette dernière preuve reproductible. `VALIDATION_M10.md` ne sera créé qu'après le gate packaging.

## ADR M10

```text
ADR-0062 — Proposée — Java MCP SDK officiel + STDIO natif
ADR-0063 — Proposée — catalogue MCP read-only et sémantique explicite
ADR-0064 — Proposée — intégration au launcher natif et stdout protocol-clean
```

Les trois ADR restent **Proposées** jusqu'à la validation finale du packaging.

## Hors périmètre M10

```text
Streamable HTTP
SSE
OAuth réseau
Docker obligatoire
write tools
sync mutation via MCP
RequirementDelta apply/promote/activate via MCP
code intelligence MINOS
context ranking/compression NEXUS
orchestration JARVIS
```

## Décision de sortie actuelle

**M10 est fonctionnellement complet et son gate Maven Windows est validé à 307/307.** La dernière porte est exclusivement le rerun du packaging portable Windows après correction du verrou transitoire. La PR #58 reste draft et non fusionnée.
