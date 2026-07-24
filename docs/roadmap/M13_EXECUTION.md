# M13 — Plan d'exécution détaillé

Statut : **✅ VALIDÉ — NEXUS optionnel et contexte technique sous budget**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M12 validés et intégrés
M12 merge = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M12 gate  = 331/331 PASS + packaging Windows MINOS optional
```

Issue : **#63 — M13 — Intégration optionnelle NEXUS et contexte technique sous budget**  
Branche : `m13/nexus-integration`  
PR : **#64 — M13 — intégration optionnelle NEXUS et contexte augmenté**

Validation : [`../VALIDATION_M13.md`](../VALIDATION_M13.md)

## Question de sortie

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

**Réponse : OUI.**

## Source de vérité externe

NEXUS expose un serveur MCP STDIO Java 21, SDK MCP `2.0.0`, dans :

```text
adapters/mcp-java/target/nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar
```

Tools requis :

```text
list_projects
build_context
explain_context
```

## M13-S1 — Port applicatif de contexte technique ✅

Contrats provider-neutral :

```text
TechnicalContextProvider
TechnicalContextRequest
TechnicalContextOptions
TechnicalContextBundle
TechnicalContextItem
TechnicalContextObservation
DisabledTechnicalContextProvider
```

Aucun type NEXUS dans application/domain.

## M13-S2 — Intention MORPHEUS déterministe ✅

Deux sujets live sur snapshot ACTIVE :

```text
REQUIREMENT
CHANGE
```

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

`MAX_INTENT_QUERY_CHARS = 16000`. Cette borne porte uniquement sur le seed d'intention.

## M13-S3 — Résultat augmenté non destructif ✅

```text
ACTIVE MORPHEUS snapshot
 -> intent context
 -> TechnicalContextProvider
 -> NEXUS build_context | explain_context
 -> augmented response
 -X-> snapshot mutation
 -X-> technical-context persistence
```

Réponse :

```text
snapshot
intentContext
technicalContext
persisted=false
```

## M13-S4 — Mapping projet explicite ✅

```text
nexusProject = UUID ou nom unique NEXUS
```

Aucune heuristique, aucun `project add`, index, rebuild ou remove NEXUS depuis MORPHEUS.

## M13-S5 — Transport NEXUS MCP STDIO ✅

```text
MORPHEUS Java 21
 -> morpheus-integration-nexus
 -> Java MCP client 2.0.0
 -> STDIO
 -> java [-Dnexus.home=...] -jar <nexus-mcp-runner.jar>
```

Aucune dépendance compile-time `com.nexus.*`.

## M13-S6 — Budget / sources / contraintes ✅

```text
tokenBudget       1..100000, défaut 2000
requestedSources  FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
constraints       Map<String,String>
explain           boolean
```

Aucun reranking, fusion ou second budget côté MORPHEUS.

## M13-S7 — Optionalité runtime ✅

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

États : `DISABLED`, `INVALID`, `AVAILABLE`, `UNAVAILABLE`.

Sans JAR : NEXUS `DISABLED`, bundle absent, MORPHEUS CLI/MCP/API disponibles.

## M13-S8 — CLI ✅

```text
nexus-status
augmented-context requirement --project ID --requirement ID --nexus-project ID_OR_NAME [...]
augmented-context change --project ID --change ID --nexus-project ID_OR_NAME [...]
```

## M13-S9 — MCP MORPHEUS ✅

```text
get_augmented_requirement_context
get_augmented_change_context
```

Serveur M13 : **18 tools read-only** = 14 M10 + 2 M12 + 2 M13.

## M13-S10 — HTTP API ✅

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Body strict et réponse live `persisted=false`.

## M13-S11 — Architecture ✅

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

## M13-S12 — Tests ✅ VALIDÉS

Head de code testé :

```text
a44e8938bfa03e8b8a1039c8271a8865b871ed7d
```

Résultats autoritatifs :

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

Le premier gate sur `a91af6288...` a identifié une projection JSON non sûre du snapshot. `AugmentedSnapshotView` a corrigé la frontière sans élargir `CanonicalJsonSerializer`; le second gate complet ci-dessus est vert.

## M13-S13 — Distribution ✅ VALIDÉE

Commande :

```powershell
.\distribution\build-portable.ps1
```

Preuves :

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

Le shaded JAR contient les adapters clients mais aucune classe `com/minos/*` ou `com/nexus/*`.

Les smokes cross-repo réels MINOS/NEXUS restent disponibles comme vérifications complémentaires ; ils ne font pas partie du gate M13 officiel.

## M13-S14 — Documentation ✅

```text
docs/VALIDATION_M13.md
docs/NEXUS.md
docs/MCP.md
docs/API.md
docs/openapi/morpheus-v1.yaml
docs/roadmap/M13_EXECUTION.md
docs/ROADMAP.md
README.md
distribution/README.md
docs/adr/README.md
```

## M13-S15 — Gate final ✅

```text
346/346 PASS
Architecture 154/154 PASS
Packaging Windows PASS
Standalone optional-engines PASS
Packaged API health PASS
```

## ADR acceptées

```text
ADR-0073 — Acceptée — M13
ADR-0074 — Acceptée — M13
ADR-0075 — Acceptée — M13
ADR-0076 — Acceptée — M13
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
