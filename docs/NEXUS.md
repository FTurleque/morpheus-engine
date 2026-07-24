# MORPHEUS × NEXUS — M13

Statut : **implémentation fonctionnelle complète — gate local pending**

M13 relie l'intention structurée MORPHEUS au moteur de contexte NEXUS sans recopier le ranking, la fusion, la sélection ou la compression technique.

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

Variables d'environnement :

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

Défauts :

```text
java = java
timeoutSeconds = 20
```

Timeout autorisé : `1..120` secondes.

`MORPHEUS_NEXUS_JAR` doit désigner le `*-runner.jar` NEXUS. Lorsque `MORPHEUS_NEXUS_HOME` est défini, MORPHEUS le transmet comme `-Dnexus.home=<path>` avant `-jar`.

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

### Requirement

Seed déterministe :

```text
Requirement: <key?> <title>
Statement: <statement>
```

### Change

Seed déterministe :

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

Le bundle technique conserve :

```text
projectId / projectName
query / explain / durationMs
tokenBudget / estimatedTokens
items:
  type / path / symbol / startLine / endLine / content
  score / scoreComponents / reasons / estimatedTokens / truncated
excluded
metadata
```

Aucune persistance du bundle NEXUS dans `KnowledgeSnapshot`.

## CLI

```text
morpheus --json nexus-status

morpheus --json augmented-context requirement \
  --project <morpheus-project-id> \
  --requirement <requirement-id> \
  --nexus-project <name-or-uuid> \
  [--budget 2000] [--source FILE] [--constraint language=java] [--explain]

morpheus --json augmented-context change \
  --project <morpheus-project-id> \
  --change <change-id> \
  --nexus-project <name-or-uuid> [...]
```

## MCP MORPHEUS

Deux tools read-only additifs :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Le serveur M13 expose donc 18 tools : 14 M10 + 2 M12 + 2 M13.

## API HTTP

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Body :

```json
{
  "nexusProject":"morpheus-engine",
  "tokenBudget":2000,
  "requestedSources":["FILE","SYMBOL","TEST"],
  "constraints":{"language":"java"},
  "explain":false
}
```

## Preuves implémentées

```text
settings disabled/configured/invalid
provider pass-through exact
provider unavailable non fatal
real MCP STDIO subprocess fixture
required NEXUS tools verified
exact NEXUS ContextBundle projection
HTTP Requirement + Change augmentation
HTTP pass-through preserves NEXUS score/reasons
CLI nexus-status standalone
real MORPHEUS MCP STDIO discovers M13 tools
architecture guards com.nexus.*
portable packaging excludes com/nexus/*
standalone packaged nexus-status = DISABLED
```

## Smoke avec le vrai NEXUS

Après packaging MORPHEUS :

```powershell
.\distribution\test-nexus-compatibility.ps1 `
  -NexusRunnerJar N:\workspace-dev\nexus-context-engine\adapters\mcp-java\target\nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar `
  -NexusJava <java-21-or-newer> `
  -NexusHome <optional-nexus-home>
```

Attendu :

```text
Real NEXUS MCP compatibility smoke: PASS
```

## Gate M13 restant

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

ADR-0073 à ADR-0076 restent **Proposées** jusqu'à ces preuves.