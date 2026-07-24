# MORPHEUS × NEXUS — M13

Statut : **✅ VALIDÉ**

M13 relie l'intention structurée MORPHEUS au moteur de contexte NEXUS sans recopier le ranking, la fusion, la sélection ou la compression technique.

Validation : [`VALIDATION_M13.md`](VALIDATION_M13.md).

## Responsabilités

```text
MORPHEUS = requirement / change / constraints / decisions / tasks / intent
NEXUS    = technical context selection / ranking / fusion / compression / budget
```

MORPHEUS ne recalcule jamais les scores NEXUS et n'applique pas un second budget technique.

## Architecture

```text
MORPHEUS Java 21
  -> TechnicalContextProvider
  -> morpheus-integration-nexus
  -> Java MCP client 2.0.0 / STDIO
  -> NEXUS Java 21 MCP runner
  -> list_projects
  -> build_context | explain_context
```

Runner NEXUS attendu :

```text
adapters/mcp-java/target/nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar
```

MORPHEUS n'importe aucune classe `com.nexus.*`. Le runner NEXUS n'est jamais embarqué dans la distribution MORPHEUS.

## Configuration

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Propriétés Java équivalentes :

```text
morpheus.nexus.jar
morpheus.nexus.java
morpheus.nexus.home
morpheus.nexus.timeoutSeconds
```

Priorité : propriété Java > environnement > défaut.

```text
java = java
timeoutSeconds = 20
```

Timeout autorisé : `1..120` secondes.

`MORPHEUS_NEXUS_JAR` désigne le `*-runner.jar` NEXUS. `MORPHEUS_NEXUS_HOME` est transmis comme `-Dnexus.home=<path>` avant `-jar`.

## Optionalité

Sans configuration :

```text
MORPHEUS CLI  -> fonctionne
MORPHEUS MCP  -> fonctionne
MORPHEUS API  -> fonctionne
NEXUS status  -> DISABLED
technical ctx -> absent explicitement
```

Un runner absent, arrêté ou incompatible produit `UNAVAILABLE` uniquement pour l'observation NEXUS.

## Mapping projet

Chaque demande impose :

```text
nexusProject = UUID ou nom unique du projet NEXUS
```

M13 ne déduit jamais le projet NEXUS depuis un chemin ou un nom MORPHEUS et n'appelle aucune mutation NEXUS : pas de `project add`, pas d'indexation, pas de rebuild.

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

Le seed est borné à 16 000 caractères. Cette borne limite uniquement la requête d'intention envoyée au moteur externe ; elle ne tronque ni ne reranke le `ContextBundle` retourné.

## Options pass-through

```text
tokenBudget      1..100000, défaut 2000
requestedSources FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
constraints      Map<String,String>
explain          boolean
```

`explain=false` appelle `build_context`. `explain=true` appelle `explain_context`.

## Résultat

```text
snapshot
intentContext
technicalContext
persisted=false
```

Le bundle conserve `projectId`, `projectName`, query, budget, `estimatedTokens`, items, scores, composants de score, raisons, exclusions et metadata.

Aucune persistance du bundle NEXUS dans `KnowledgeSnapshot`.

## CLI

```text
morpheus --json nexus-status
morpheus --json augmented-context requirement --project <id> --requirement <id> --nexus-project <name-or-uuid> [...]
morpheus --json augmented-context change --project <id> --change <id> --nexus-project <name-or-uuid> [...]
```

## MCP MORPHEUS

```text
get_augmented_requirement_context
get_augmented_change_context
```

Le serveur M13 expose **18 tools read-only** : 14 M10 + 2 M12 + 2 M13.

## API HTTP

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

## Preuves validées

Head testé : `a44e8938bfa03e8b8a1039c8271a8865b871ed7d`.

```text
TOTAL              346/346 PASS
Architecture       154/154 PASS
NEXUS Integration     7/7 PASS
API                    7/7 PASS
CLI                  17/17 PASS
Failures                 0
Errors                   0
Skipped                  0
BUILD SUCCESS
```

Packaging Windows :

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
ZIP 33,654,379 bytes
```

Le smoke `distribution/test-nexus-compatibility.ps1` contre un vrai runner NEXUS reste disponible comme preuve cross-repo complémentaire ; il n'était pas requis par le gate M13 officiel.

ADR-0073 à ADR-0076 : **Acceptées — M13**.
