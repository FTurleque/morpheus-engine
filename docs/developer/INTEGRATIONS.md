# Intégrations cross-engine

MORPHEUS reste autonome. Les intégrations externes implémentent des ports explicites et ne transfèrent pas la propriété du domaine.

```text
MORPHEUS = specification / intent / lifecycle rules
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing
```

## MINOS

### Architecture

```text
ExternalReference(system=MINOS, resourceType=SYMBOL)
  -> ExternalReferenceResolver
  -> morpheus-integration-minos
  -> Java MCP client 2.0.0 / STDIO
  -> process MINOS
```

Contraintes :

- aucune dépendance `com.minos.*` ;
- MINOS n’est pas embarqué ;
- matching d’identité exact sur `symbolKey` ;
- révision optionnelle comparée à l’`activeSnapshotId` MINOS ;
- observation live séparée de la référence persistée ;
- `persisted=false` pour la résolution live.

Configuration :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Résultats resolver :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

MORPHEUS traduit ces états sans réécrire rétroactivement les snapshots publiés.

## NEXUS

### Architecture

```text
MorpheusIntentContext
  -> TechnicalContextProvider
  -> morpheus-integration-nexus
  -> Java MCP client 2.0.0 / STDIO
  -> NEXUS MCP runner
```

Frontière :

```text
MORPHEUS = construction de l’intention déterministe
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

MORPHEUS ne reranke pas le `ContextBundle`, ne réapplique pas de budget et ne le persiste pas dans `KnowledgeSnapshot`.

Configuration :

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Chaque requête exige un mapping explicite :

```text
nexusProject = UUID ou nom unique du projet NEXUS
```

Pas d’inférence depuis un chemin local ; MORPHEUS ne lance pas `project add`, index ou rebuild côté NEXUS.

## JARVIS

### Architecture

```text
JARVIS
  -> HTTP JSON local
  -> MORPHEUS /api/v1
  -> ChangeOrchestrationStateService
  -> ChangeTransitionEvaluationService
```

Frontière :

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

Aucune dépendance `com.jarvis.*` côté MORPHEUS et aucune dépendance `com.morpheus.*` dans le client JARVIS validé.

### Lifecycle explicite

```text
lifecycle absent -> source=UNAVAILABLE
lifecycle fourni -> source=CALLER_SUPPLIED
```

Un état n’est jamais inféré depuis tasks, timestamps, chemins d’archive ou findings qualité.

### Décisions

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Les raisons d’abandon observation/source/cible sont distinctes :

```text
abandonmentReason
fromAbandonmentReason
target abandonmentReason
```

### API

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST calcule une décision mais n’applique aucune transition.

### Client JARVIS validé

Configuration :

```text
jarvis.morpheus.enabled=${MORPHEUS_ENABLED:false}
jarvis.morpheus.url=${MORPHEUS_URL:http://127.0.0.1:8765}
jarvis.morpheus.project-id=${MORPHEUS_PROJECT_ID:}
jarvis.morpheus.timeout-seconds=${MORPHEUS_TIMEOUT_SECONDS:3}
```

Le client est fail-open et retourne une absence de contexte si MORPHEUS est désactivé, mal configuré, indisponible ou répond avec un contrat incompatible.

## Règles d’architecture exécutables

Les tests ArchUnit imposent notamment :

```text
domain -X-> provider/store/cli/mcp/api/integration
application -X-> provider/store/cli/mcp/api/integration
api -X-> cli/mcp/integration
MINOS adapter -X-> com.minos.*
NEXUS adapter -X-> com.nexus.*
MORPHEUS -X-> com.jarvis.*
```

## Tests et smokes

M14 a validé :

```text
MINOS Integration 8/8 PASS
NEXUS Integration 7/7 PASS
Architecture 160/160 PASS
JARVIS MorpheusOrchestrationClientTest 6/6 PASS
```

Smokes complémentaires :

```text
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

Voir aussi : [Architecture](ARCHITECTURE.md), [API](API.md), [MCP](MCP.md), [guide utilisateur](../user/INTEGRATIONS.md).
