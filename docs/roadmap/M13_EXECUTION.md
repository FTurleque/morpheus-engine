# M13 — Plan d'exécution détaillé

Statut : **EN COURS**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M12 validés et intégrés
M12 merge = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M12 gate  = 331/331 PASS + packaging Windows MINOS optional
```

Issue : **#63 — M13 — Intégration optionnelle NEXUS et contexte technique sous budget**  
Branche : `m13/nexus-integration`

## Question de sortie

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

## Source de vérité externe

NEXUS `main` expose déjà un serveur MCP STDIO Java 21 avec le SDK MCP officiel `2.0.0`.

Tools M13 requis :

```text
list_projects
build_context
explain_context
```

Contrat `build_context` NEXUS :

```text
project
query
tokenBudget
requestedSources
constraints
```

La réponse contient le `ContextBundle` déjà classé/sélectionné par NEXUS : `tokenBudget`, `estimatedTokens`, `items`, `excluded`, `metadata`.

## M13-S1 — Port applicatif de contexte technique

Introduire un contrat provider-neutral :

```text
TechnicalContextProvider
TechnicalContextRequest
TechnicalContextBundle
TechnicalContextItem
TechnicalContextObservation
```

Aucun type NEXUS dans application/domain.

## M13-S2 — Intention MORPHEUS déterministe

Deux sujets live sur snapshot ACTIVE :

```text
REQUIREMENT
CHANGE
```

Requirement seed :

```text
key + title + statement
```

Change seed :

```text
key + title + intent + scope
+ requirements affectés
+ contraintes
+ décisions
+ tâches
```

Le seed est déterministe, borné et sert uniquement de `query` NEXUS. MORPHEUS ne classe aucun fragment technique.

## M13-S3 — Résultat augmenté non destructif

```text
ACTIVE MORPHEUS snapshot
 -> intent context
 -> NEXUS build_context
 -> augmented response
 -X-> snapshot mutation
 -X-> technical-context persistence
```

Réponse :

```text
snapshotId
intentContext
technicalContextObservation
persisted=false
```

## M13-S4 — Mapping projet explicite

Chaque appel exige :

```text
nexusProject = UUID ou nom unique NEXUS
```

M13 ne fait jamais :

```text
project add NEXUS
index NEXUS
rebuild NEXUS
project mapping heuristique
```

## M13-S5 — Transport NEXUS MCP STDIO

```text
MORPHEUS Java 21
 -> morpheus-integration-nexus
 -> Java MCP client 2.0.0
 -> STDIO
 -> java [-Dnexus.home=...] -jar <nexus-mcp-runner.jar>
```

Aucune dépendance compile-time `com.nexus.*`.

Le client vérifie les trois tools requis avant usage.

## M13-S6 — Budget et sources

Pass-through contrôlé :

```text
tokenBudget     1..100000
requestedSources = FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
constraints     Map<String,String>
explain         boolean
```

Aucun quota supplémentaire, reranking ou troncature côté MORPHEUS.

## M13-S7 — Optionalité runtime

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Propriétés équivalentes : `morpheus.nexus.*`.

Sans JAR :

```text
NEXUS status = DISABLED
technical context = absent
MORPHEUS CLI/MCP/API = disponibles
```

Process absent/incompatible : `UNAVAILABLE`, non fatal.

## M13-S8 — CLI

```text
nexus-status
augmented-context requirement --project ID --requirement ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE]* [--constraint k=v]* [--explain]
augmented-context change --project ID --change ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE]* [--constraint k=v]* [--explain]
```

Sortie JSON : intention et contexte technique restent séparés.

## M13-S9 — MCP MORPHEUS

Deux tools read-only additifs :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Aucune mutation MORPHEUS/NEXUS.

## M13-S10 — HTTP API

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Body strict :

```json
{
  "nexusProject": "name-or-uuid",
  "tokenBudget": 2000,
  "requestedSources": ["FILE", "SYMBOL", "TEST", "DOCUMENTATION"],
  "constraints": {},
  "explain": false
}
```

## M13-S11 — Architecture

```text
domain/application -X-> integration-nexus
domain/application -X-> com.nexus..
api/mcp            -X-> integration-nexus
integration-nexus  -X-> cli/api/mcp/store
integration-nexus  -X-> com.nexus..
CLI = composition root
```

## M13-S12 — Tests

Preuves attendues :

```text
intent seed REQUIREMENT déterministe
intent seed CHANGE déterministe
provider disabled non fatal
NEXUS settings configured/invalid/disabled
exact pass-through budget/sources/constraints
real MCP STDIO subprocess fixture
required-tool compatibility check
NEXUS error -> UNAVAILABLE observation
CLI standalone sans NEXUS
MORPHEUS MCP STDIO sans NEXUS
HTTP status + augmented-context sans NEXUS
no snapshot mutation
architecture guards
```

## M13-S13 — Distribution

Le shaded JAR/portable archive doit contenir :

```text
morpheus-integration-nexus
MCP client SDK
```

Et ne doit contenir aucune classe :

```text
com/nexus/*
```

Smokes :

```text
--version
--json version
--json minos-status -> DISABLED sans config
--json nexus-status -> DISABLED sans config
API /health
```

NEXUS lui-même reste externe et optionnel.

## M13-S14 — Documentation

```text
docs/NEXUS.md
docs/MCP.md
docs/API.md
docs/openapi/morpheus-v1.yaml
docs/ROADMAP.md
README.md
distribution/README.md
```

## M13-S15 — Gate final

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Un smoke cross-repo réel avec le runner NEXUS pourra compléter la preuve autonome.

M13 ne sera `VALIDÉ` qu'après preuve reproductible.

## ADR candidates

```text
ADR-0073 — Proposée — intégration NEXUS par MCP STDIO inter-processus
ADR-0074 — Proposée — mapping projet NEXUS explicite sans ownership lifecycle
ADR-0075 — Proposée — intention MORPHEUS séparée du contexte technique NEXUS
ADR-0076 — Proposée — runtime/surfaces NEXUS optionnels
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