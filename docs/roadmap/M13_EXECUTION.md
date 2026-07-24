# M13 — Plan d'exécution détaillé

Statut : **FONCTIONNELLEMENT COMPLET — gate local pending**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M12 validés et intégrés
M12 merge = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M12 gate  = 331/331 PASS + packaging Windows MINOS optional
```

Issue : **#63 — M13 — Intégration optionnelle NEXUS et contexte technique sous budget**  
Branche : `m13/nexus-integration`  
PR : **#64 — M13 — intégration optionnelle NEXUS et contexte augmenté** (draft)

## Question de sortie

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

Réponse actuelle : **implémentation OUI ; preuve locale finale pending**.

## Source de vérité externe

NEXUS `main` expose un serveur MCP STDIO Java 21, SDK MCP `2.0.0`, dans :

```text
adapters/mcp-java/target/nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar
```

Tools M13 requis :

```text
list_projects
build_context
explain_context
```

Contrat `build_context` :

```text
project
query
tokenBudget
requestedSources
constraints
```

La réponse NEXUS est un `ContextBundle` déjà sélectionné/classé/fusionné sous budget : `tokenBudget`, `estimatedTokens`, `items`, `excluded`, `metadata`.

## M13-S1 — Port applicatif de contexte technique ✅ implémenté

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

## M13-S2 — Intention MORPHEUS déterministe ✅ implémenté

Deux sujets live sur snapshot ACTIVE :

```text
REQUIREMENT
CHANGE
```

Requirement seed :

```text
Requirement: <key?> <title>
Statement: <statement>
```

Change seed :

```text
Change: <key?> <title>
Intent: <intent>
Scope: ...
Affected requirement: ...
Constraint: ...
Design decision: ...
Implementation task: ...
```

`MAX_INTENT_QUERY_CHARS = 16000`. La borne porte seulement sur la requête d'intention ; le bundle NEXUS n'est ni reranké ni retronqué.

## M13-S3 — Résultat augmenté non destructif ✅ implémenté

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

`AugmentedContextService` ne possède aucune opération d'écriture.

## M13-S4 — Mapping projet explicite ✅ implémenté

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

## M13-S5 — Transport NEXUS MCP STDIO ✅ implémenté

```text
MORPHEUS Java 21
 -> morpheus-integration-nexus
 -> Java MCP client 2.0.0
 -> STDIO
 -> java [-Dnexus.home=...] -jar <nexus-mcp-runner.jar>
```

Aucune dépendance compile-time `com.nexus.*`.

`NexusMcpContextGateway` initialise MCP et refuse un serveur qui n'expose pas les trois tools requis.

## M13-S6 — Budget / sources / contraintes ✅ implémenté

Pass-through contrôlé :

```text
tokenBudget       1..100000, défaut 2000
requestedSources  FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
constraints       Map<String,String>
explain           boolean
```

Aucun quota supplémentaire, reranking, fusion ou troncature technique côté MORPHEUS.

## M13-S7 — Optionalité runtime ✅ implémenté

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Propriétés équivalentes : `morpheus.nexus.*`.

États :

```text
DISABLED
CONFIGURED (settings)
INVALID
AVAILABLE
UNAVAILABLE
```

Sans JAR : NEXUS `DISABLED`, bundle absent, MORPHEUS CLI/MCP/API disponibles.

Process absent/incompatible : `UNAVAILABLE`, non fatal.

## M13-S8 — CLI ✅ implémenté

```text
nexus-status
augmented-context requirement --project ID --requirement ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE]* [--constraint k=v]* [--explain]
augmented-context change --project ID --change ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE]* [--constraint k=v]* [--explain]
```

Les identifiants de sujet contradictoires sont rejetés. Sortie JSON : intention et contexte technique séparés.

## M13-S9 — MCP MORPHEUS ✅ implémenté

Deux tools read-only additifs :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Serveur M13 : **18 tools read-only** = 14 M10 + 2 M12 + 2 M13.

Aucune mutation MORPHEUS ou NEXUS.

## M13-S10 — HTTP API ✅ implémenté

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Body strict :

```json
{
  "nexusProject":"name-or-uuid",
  "tokenBudget":2000,
  "requestedSources":["FILE","SYMBOL","TEST","DOCUMENTATION"],
  "constraints":{},
  "explain":false
}
```

Body `null`, champs inconnus, sources inconnues et budget hors bornes sont rejetés en erreur client.

## M13-S11 — Architecture ✅ implémenté

```text
domain/application -X-> integration-nexus
domain/application -X-> com.nexus..
api                 -X-> integration-nexus
mcp                 -X-> integration-nexus
integration-nexus   -X-> cli/api/mcp/store
integration-nexus   -X-> com.nexus..
CLI = composition root
```

`morpheus-api` et `morpheus-mcp` reçoivent seulement `TechnicalContextProvider`.

## M13-S12 — Tests ✅ implémentés, exécution pending

Delta depuis M12 :

```text
Application
  TechnicalContextOptionsTest                         3

NEXUS integration
  NexusIntegrationSettingsTest                       3
  NexusMcpTechnicalContextProviderTest                3
  NexusMcpTransportIntegrationTest                    1
                                                       = 7

API
  MorpheusAugmentedContextApiContractTest             2

CLI
  MorpheusNexusCliTest                                1
  MorpheusM13McpStdioIntegrationTest                  1
                                                       = 2

Architecture
  LayerDependencyTest                                +1
```

Projection :

```text
M12 baseline 331
M13 delta     15
----------------
TOTAL attendu 346
```

Détail projeté :

```text
Domain              21
Application         87
OpenSpec             26
Synthetic             7
SQLite                7
MINOS Integration     8
NEXUS Integration     7
MCP                   5
API                   7
CLI                  17
Architecture        154
-----------------------
TOTAL attendu       346
```

**346 est une projection, pas une preuve tant que Maven n'a pas été exécuté.**

## M13-S13 — Distribution ✅ implémentée, exécution pending

Windows/Linux :

```text
workdir .m13-windows / .m13-linux
morpheus-integration-minos embedded
morpheus-integration-nexus embedded
MCP client SDK embedded
com/minos/* forbidden
com/nexus/* forbidden
MINOS/NEXUS eux-mêmes non bundled
jdk.httpserver retained
```

Smokes :

```text
--version
--json version
--json minos-status -> DISABLED without configuration
--json nexus-status -> DISABLED without configuration
packaged API /health
```

Installateur optionnel Windows aligné sur `.m13-windows`.

Smokes cross-repo disponibles :

```text
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

## M13-S14 — Documentation ✅ implémenté

```text
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

## M13-S15 — Gate final ⏳

Source de vérité :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Attendu :

```text
TOTAL ~346 tests, exact count reported by Maven
Failures 0
Errors 0
Skipped 0
BUILD SUCCESS
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Un smoke contre le vrai runner NEXUS complète utilement la preuve autonome, mais ne remplace pas le gate MORPHEUS.

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
