# Intégrations optionnelles MORPHEUS

MORPHEUS fonctionne sans MINOS, NEXUS ni JARVIS. Chaque intégration enrichit une capacité précise sans déplacer la propriété du domaine.

```text
MORPHEUS = specification facts + intent + lifecycle rules + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 1. Principe d’optionalité

Une intégration externe est une capacité additionnelle :

- elle ne doit pas empêcher les requêtes MORPHEUS natives ;
- elle ne doit pas réécrire un snapshot publié à partir d’une observation live ;
- son indisponibilité reste distincte d’un résultat négatif métier ;
- les données d’un moteur externe restent la propriété de ce moteur.

```text
optional engine absence != MORPHEUS failure
```

Cette règle est distincte de l’optionalité des providers M18 :

```text
optional provider absence != project failure when optional
```

## 2. MINOS

MINOS permet à MORPHEUS de résoudre des `ExternalReference` vers des symboles de code via MCP STDIO.

Configuration :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

État :

```bash
morpheus --json minos-status
```

Résolution :

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

`NOT_FOUND != UNAVAILABLE`. Une observation live expose `persisted=false` et ne réécrit pas le snapshot.

## 3. NEXUS

NEXUS construit un contexte technique sous budget à partir d’une intention MORPHEUS.

Configuration :

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

État :

```bash
morpheus --json nexus-status
```

Exemple change :

```bash
morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name> \
  --budget 2000
```

Frontière :

```text
MORPHEUS = construction de l'intention déterministe
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

Le `ContextBundle` reste live et `persisted=false`.

## 4. JARVIS

JARVIS consomme les faits et décisions MORPHEUS via l’API HTTP locale. MORPHEUS ne lance pas JARVIS et ne dépend pas de `com.jarvis.*`.

Routes read-only :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

La décision d’évaluation reste :

```text
ALLOWED | BLOCKED | UNKNOWN | REQUIRES_INPUT
```

Une décision `ALLOWED` n’applique aucune transition.

M17 ajoute une commande distincte de mutation contrôlée :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions
```

Elle exige notamment :

```text
WRITE_CHANGE
confirmation
expectedRevision / CAS
idempotencyKey
transition réellement ALLOWED
audit
```

```text
transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
MORPHEUS rules != JARVIS action sequencing
```

JARVIS choisit l’action et son ordre ; MORPHEUS protège les invariants et applique seulement une commande explicite autorisée.

## 5. Composition provider M18 et intégrations externes

La composition M18 n’est pas une fusion de responsabilités MINOS/NEXUS/JARVIS. Elle compose des **faits de spécification normalisés** issus de providers MORPHEUS tels qu’OpenSpec et Structured Markdown.

```text
OpenSpec + Structured Markdown
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
```

Invariants :

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
```

MINOS demeure code intelligence, NEXUS contexte technique, JARVIS orchestration.

## 6. Diagnostic

### MINOS/NEXUS reste `DISABLED`

Vérifier que la variable `*_JAR` est visible dans le même processus qui lance MORPHEUS.

### Processus externe présent mais appel en erreur

Vérifier chemin du JAR, Java, répertoire `*_HOME`, timeout, compatibilité MCP et `stderr` MORPHEUS.

### Résultat live différent du snapshot

C’est possible et volontaire : l’observation externe live ne remplace pas l’état publié.

### JARVIS ne reçoit aucun contexte

Vérifier l’API MORPHEUS, `MORPHEUS_URL`, `MORPHEUS_PROJECT_ID`, le même `--db`/layout et `/api/v1/health`.

## 7. Baseline

```text
M18             ✅ VALIDÉ / INTÉGRÉ — PR #86
OpenAPI         1.7.0
MCP             22 read-only + 1 write
Code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
Merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
```

Les ports, adapters et invariants de dépendance sont détaillés dans [Intégrations développeur](../developer/INTEGRATIONS.md).