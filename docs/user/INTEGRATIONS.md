# Intégrations optionnelles MORPHEUS

MORPHEUS fonctionne sans MINOS, NEXUS ni JARVIS. Chaque intégration enrichit une capacité précise sans déplacer la propriété du domaine.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 1. Principe d’optionalité

Une intégration externe :

- ne doit pas empêcher les fonctions MORPHEUS natives lorsqu’elle est absente ;
- ne doit pas transformer une observation live en snapshot persisté ;
- conserve des statuts d’indisponibilité explicites ;
- ne transfère pas à MORPHEUS les responsabilités du moteur externe.

| Intégration | Transport | Activée par défaut | En cas d’absence |
|---|---|---:|---|
| MINOS | MCP STDIO inter-processus | non | résolution code indisponible seulement |
| NEXUS | MCP STDIO inter-processus | non | contexte technique absent seulement |
| JARVIS | HTTP local côté JARVIS | non | MORPHEUS reste autonome |

M18 ajoute des **providers MORPHEUS réels** OpenSpec + Structured Markdown. Ces providers ne sont pas des moteurs externes MINOS/NEXUS/JARVIS.

# 2. MINOS

MINOS résout des `ExternalReference` MORPHEUS vers des symboles de code.

## Configuration

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAR_SHA256
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Vérifier :

```bash
morpheus --json minos-status
```

Résoudre :

```bash
morpheus --json external-references resolve \
  --project <projectId> \
  --reference <externalReferenceId>
```

Résultats possibles :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

`NOT_FOUND != UNAVAILABLE`. Une résolution live reste `persisted=false` et ne réécrit pas le snapshot.

# 3. NEXUS

NEXUS construit un contexte technique sous budget à partir d’une intention MORPHEUS.

## Configuration

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAR_SHA256
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Vérifier :

```bash
morpheus --json nexus-status
```

Exemple :

```bash
morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name> \
  --budget 2000
```

La frontière reste :

```text
MORPHEUS = construction de l’intention déterministe
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

Le `ContextBundle` est live et `persisted=false`.

# 4. JARVIS

JARVIS choisit et séquence les actions. MORPHEUS expose les faits et règles nécessaires pour évaluer ces actions, puis peut appliquer **une commande lifecycle distincte explicitement autorisée**.

## 4.1 Surfaces read-only d’orchestration

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le `transition-check` calcule :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Il n’applique aucune transition.

## 4.2 Surface write contrôlée M17

La mutation est une requête séparée :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions
```

Elle exige notamment :

```text
WRITE_CHANGE capability
confirmation explicite
expectedRevision / CAS
idempotencyKey
actor
transition réellement ALLOWED
append-only audit
```

Résultats :

```text
APPLIED
ALREADY_APPLIED
CONFLICT
NOT_AUTHORIZED
REQUIRES_CONFIRMATION
REJECTED
```

Invariants :

```text
transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
MORPHEUS rules != JARVIS action sequencing
```

JARVIS peut décider d’appeler la commande ; MORPHEUS reste seul responsable de vérifier ses invariants avant de muter son état opérationnel.

## 4.3 Configuration JARVIS validée historiquement

```text
MORPHEUS_ENABLED=true
MORPHEUS_URL=http://127.0.0.1:8765
MORPHEUS_PROJECT_ID=<projectId>
MORPHEUS_TIMEOUT_SECONDS=3
```

Le client JARVIS historique est fail-open pour le contexte : MORPHEUS indisponible ne transforme pas JARVIS en dépendance runtime obligatoire de MORPHEUS.

# 5. M18 et composition provider

La composition multi-provider MORPHEUS est distincte des intégrations externes :

```text
OpenSpec + Structured Markdown
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
```

Elle conserve provenance et conflits explicites.

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
```

Une absence de provider optionnel n’est pas assimilée à une panne du projet.

# 6. Diagnostic

### MINOS/NEXUS `DISABLED`

Vérifier que les variables `*_JAR` sont visibles dans le processus qui lance MORPHEUS.

### Résultat live différent du snapshot

C’est possible et volontaire : observation externe live et connaissance publiée sont distinctes.

### JARVIS reçoit `UNKNOWN`

Un fait requis est indisponible. MORPHEUS ne le convertit pas en `false`.

### Mutation JARVIS/MORPHEUS `NOT_AUTHORIZED`

Aucun provider du projet n’expose explicitement `WRITE_CHANGE`. Une capacité de lecture ne suffit pas.

### Conflit M18

Utiliser :

```bash
morpheus --json composition conflicts --project <projectId>
```

Ne pas résoudre côté client par last-write-wins implicite.

# 7. Baseline

```text
M18 code validé  7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge        30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
OpenAPI          1.7.0
MCP              22 read-only + 1 write
```

# 8. Documentation développeur

Les ports, adapters et frontières exécutables sont détaillés dans [Intégrations développeur](../developer/INTEGRATIONS.md).
