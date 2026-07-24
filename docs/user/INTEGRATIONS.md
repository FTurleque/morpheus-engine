# Intégrations optionnelles

MORPHEUS fonctionne sans MINOS, NEXUS ni JARVIS. Chaque intégration enrichit une capacité précise sans déplacer la propriété du domaine.

```text
MORPHEUS = specification / intent / lifecycle rules
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing
```

## MINOS

MINOS permet à MORPHEUS de résoudre des `ExternalReference` vers des symboles de code.

### Activer

Variables d’environnement :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Le minimum est `MORPHEUS_MINOS_JAR`, qui doit pointer vers le JAR autonome `*-all.jar` de MINOS.

Exemple PowerShell :

```powershell
$env:MORPHEUS_MINOS_JAR = 'N:\workspace-dev\minos-code-intelligence\target\minos-code-intelligence-0.1.0-SNAPSHOT-all.jar'
```

Vérifier :

```bash
morpheus --json minos-status
```

Sans configuration : `DISABLED`. Un process MINOS indisponible ne rend pas MORPHEUS indisponible.

### Résoudre une référence

```bash
morpheus --json external-references resolve \
  --project <projectId> \
  --reference <externalReferenceId>
```

La résolution live expose séparément la référence stockée et l’observation. Elle ne réécrit pas le snapshot publié.

## NEXUS

NEXUS construit un contexte technique sous budget à partir d’une intention MORPHEUS.

### Activer

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

`MORPHEUS_NEXUS_JAR` pointe vers le runner Java NEXUS, typiquement :

```text
adapters/mcp-java/target/nexus-mcp-java-0.1.0-SNAPSHOT-runner.jar
```

Vérifier :

```bash
morpheus --json nexus-status
```

Sans configuration : `DISABLED`.

### Construire un contexte

```bash
morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name> \
  --budget 2000
```

MORPHEUS transmet l’intention ; NEXUS reste propriétaire de la sélection, du ranking, de la fusion, de la compression et du budget technique. Le bundle retourné reste live et `persisted=false`.

## JARVIS

JARVIS consomme le contrat HTTP read-only de MORPHEUS pour orchestrer des actions sans importer le domaine MORPHEUS.

### Côté MORPHEUS

Démarrer l’API :

```bash
morpheus api --host 127.0.0.1 --port 8765
```

Les routes M14 sont :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST évalue une transition ; il ne l’applique pas.

### Côté JARVIS

Configuration validée dans le client JARVIS :

```text
MORPHEUS_ENABLED=true
MORPHEUS_URL=http://127.0.0.1:8765
MORPHEUS_PROJECT_ID=<projectId>
MORPHEUS_TIMEOUT_SECONDS=3
```

Le client JARVIS est fail-open : si MORPHEUS est désactivé, non configuré ou indisponible, le provider retourne une absence de contexte plutôt que de faire tomber JARVIS.

## Optionalité résumée

| Intégration | Transport | Activée par défaut | En cas d’absence |
|---|---|---:|---|
| MINOS | MCP STDIO inter-processus | non | résolution code indisponible seulement |
| NEXUS | MCP STDIO inter-processus | non | contexte technique absent seulement |
| JARVIS | HTTP local | non côté client JARVIS | orchestration continue en fail-open côté JARVIS |

## Documentation développeur

Les contrats détaillés, invariants d’architecture et surfaces machine sont documentés dans [Intégrations développeur](../developer/INTEGRATIONS.md).
