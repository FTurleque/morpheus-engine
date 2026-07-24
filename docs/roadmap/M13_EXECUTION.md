# M13 — Plan d'exécution détaillé

Statut : **✅ VALIDÉ / INTÉGRÉ — NEXUS optionnel et contexte technique sous budget**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M13 validés et intégrés
M12 merge = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 merge = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
```

Issue : **#63 — completed**  
PR : **#64 — merged**  
Head de code validé : `a44e8938bfa03e8b8a1039c8271a8865b871ed7d`  
Validation : [`../VALIDATION_M13.md`](../VALIDATION_M13.md)

## Question de sortie

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

**Réponse : OUI.**

## Architecture validée

```text
MORPHEUS Java 21
 -> TechnicalContextProvider
 -> morpheus-integration-nexus
 -> Java MCP client 2.0.0 / STDIO
 -> NEXUS MCP runner Java 21
 -> list_projects + build_context + explain_context
```

Aucune dépendance compile-time `com.nexus.*`.

## Frontière de responsabilité

```text
MORPHEUS = intention structurée
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

MORPHEUS ne reranke, ne fusionne et ne retronque pas le `ContextBundle` NEXUS.

## Mapping projet

```text
nexusProject = UUID ou nom unique NEXUS
```

Aucune heuristique, aucun `project add`, index, rebuild ou remove NEXUS depuis MORPHEUS.

## Intention MORPHEUS

Requirement :

```text
Requirement: <key?> <title>
Statement: <statement>
```

Change :

```text
Change: <key?> <title>
Intent: <intent>
Scope: ...
Affected requirement: ...
Constraint: ...
Design decision: ...
Implementation task: ...
```

Le seed est borné à 16 000 caractères sans altérer le bundle technique retourné.

## Options pass-through

```text
tokenBudget       1..100000, défaut 2000
requestedSources  FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
constraints       Map<String,String>
explain           boolean
```

## Optionalité runtime

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Sans JAR : NEXUS `DISABLED`, bundle absent, MORPHEUS CLI/MCP/API disponibles. Process/transport/tools incompatibles : `UNAVAILABLE`, non fatal.

## Surfaces M13

CLI :

```text
nexus-status
augmented-context requirement
augmented-context change
```

MCP read-only :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Serveur M13 : **18 tools read-only**.

HTTP :

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Résultat live :

```text
snapshot
intentContext
technicalContext
persisted=false
```

## Architecture ✅

```text
domain/application -X-> integration-nexus
domain/application -X-> com.nexus..
api                 -X-> integration-nexus
mcp                 -X-> integration-nexus
integration-nexus   -X-> cli/api/mcp/store
integration-nexus   -X-> com.nexus..
CLI = composition root
```

Architecture : **154/154 PASS**.

## Gate final ✅

Commande :

```powershell
.\mvnw.cmd clean test
```

Résultats :

```text
Domain              21/21 PASS
Application         87/87 PASS
OpenSpec             26/26 PASS
Synthetic             7/7 PASS
SQLite                7/7 PASS
MINOS Integration     8/8 PASS
NEXUS Integration     7/7 PASS
MCP                    5/5 PASS
API                    7/7 PASS
CLI                  17/17 PASS
Architecture       154/154 PASS
--------------------------------
TOTAL              346/346 PASS
Failures                 0
Errors                   0
Skipped                  0
BUILD SUCCESS
```

Le premier gate sur `a91af6288...` a révélé une projection JSON non sûre du snapshot. `AugmentedSnapshotView` a corrigé cette frontière sans élargir `CanonicalJsonSerializer`; le second gate complet sur `a44e8938...` est vert.

## Distribution ✅

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
jpackage app-image: PASS
morpheus.exe --version: PASS
morpheus.exe --json version: PASS
minos-status -> DISABLED sans configuration
nexus-status -> DISABLED sans configuration
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,654,379 bytes
```

## ADR acceptées

```text
ADR-0073 — Acceptée — M13
ADR-0074 — Acceptée — M13
ADR-0075 — Acceptée — M13
ADR-0076 — Acceptée — M13
```

## Intégration ✅

```text
PR #64 merged
merge commit = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
```

## Hors périmètre M13

```text
copie du domaine NEXUS dans MORPHEUS
compile dependency com.nexus.*
indexation NEXUS pilotée par MORPHEUS
ranking/reranking technique MORPHEUS
fusion/troncature de fragments techniques MORPHEUS
persistance du ContextBundle NEXUS dans KnowledgeSnapshot
NEXUS obligatoire au bootstrap
JARVIS orchestration
```
