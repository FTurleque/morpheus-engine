# MORPHEUS × MINOS — M12

Statut : **implémentation fonctionnelle complète — gate local pending**

M12 relie l'intention/specification MORPHEUS à la connaissance de code MINOS sans fusionner leurs domaines ni leurs runtimes.

## Architecture

```text
MORPHEUS Java 21
  ExternalReference(system=MINOS, resourceType=SYMBOL)
        |
        v
morpheus-integration-minos
        |
        v
Java MCP client 2.0.0 / STDIO
        |
        v
MINOS process Java 24
  com.minos.mcp.MinosMcpServer
        |
        +-> minos_index_status
        +-> minos_find_symbols
```

MORPHEUS ne dépend d'aucune classe `com.minos.*`. Le JAR MINOS n'est jamais embarqué dans la distribution MORPHEUS.

## Optionalité

Sans configuration MINOS :

```text
MORPHEUS CLI  -> fonctionne
MORPHEUS MCP  -> fonctionne
MORPHEUS API  -> fonctionne
MINOS status  -> DISABLED
resolution    -> NO_RESOLVER
```

Un process MINOS absent ou incompatible rend seulement l'intégration `UNAVAILABLE`.

## Configuration

Variables d'environnement :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Propriétés Java équivalentes :

```text
morpheus.minos.jar
morpheus.minos.java
morpheus.minos.home
morpheus.minos.timeoutSeconds
```

Priorité : propriété Java > environnement > défaut.

Défauts :

```text
java = java
timeoutSeconds = 20
```

Timeout autorisé : `1..120` secondes.

`MORPHEUS_MINOS_JAR` doit désigner le JAR exécutable/classpath contenant `com.minos.mcp.MinosMcpServer` et ses dépendances.

## Coordonnée externe

Le contrat M12 est strict :

```text
system       = MINOS
resourceType = SYMBOL
project      = projet MINOS obligatoire
externalId   = symbolKey MINOS exact obligatoire
revision     = activeSnapshotId MINOS attendu, optionnel
```

Exemple :

```text
MINOS / SYMBOL / project=morpheus-engine
externalId=symbol:RequirementService
revision=019...
```

Le resolver peut utiliser la recherche lexicale MINOS pour récupérer des candidats, mais n'accepte que :

```text
candidate.symbolKey.equals(externalId)
```

Puis :

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Aucun fuzzy matching, `qualifiedName` approximatif ou score lexical ne remplace l'identité externe.

## Révision

Lorsque `revision` est présente, M12 appelle d'abord `minos_index_status` et impose :

```text
reference.revision == MINOS activeSnapshotId
```

Sinon :

```text
REVISION_MISMATCH
-> TARGET_REVISION_MISMATCH
```

MORPHEUS ne prétend jamais avoir résolu un symbole contre une autre baseline MINOS que celle demandée.

## Résolution live

La résolution M12 est une observation, pas une mutation du snapshot :

```text
ExternalReference persistée
      -> resolve live
      -> ExternalReference observée
      -> réponse
      -X-> réécriture du snapshot ACTIVE/RETIRED
```

La réponse expose séparément :

```text
stored
observed
persisted = false
```

Ainsi ADR-0041 reste intacte : historique, provenance et `TraceabilityLink` publiés ne sont jamais réécrits rétroactivement.

## États

Résultats de resolver :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

Raisons MORPHEUS :

```text
RESOLVED
TARGET_NOT_FOUND
TARGET_UNAVAILABLE
TARGET_REMOVED
TARGET_AMBIGUOUS
TARGET_REVISION_MISMATCH
TARGET_TYPE_UNSUPPORTED
NO_RESOLVER
```

Une référence déjà résolue peut devenir `STALE` lors d'une observation live ultérieure ; ce nouvel état observé n'est pas persisté dans l'ancien snapshot.

## Faits MINOS exposés

Une résolution `FOUND` fournit uniquement des métadonnées contrôlées :

```text
minos.projectId
minos.activeSnapshotId
minos.symbolId
minos.symbolKey
minos.qualifiedName
minos.kind
minos.language
minos.moduleId
minos.fileId
minos.resolutionStatus
minos.providerId
minos.providerVersion
minos.indexRunId
```

Le code source complet n'est pas copié dans MORPHEUS.

## CLI

```text
morpheus --json minos-status

morpheus --json external-references list \
  --project <morpheus-project-id> \
  --owner <domain-identity>

morpheus --json external-references resolve \
  --project <morpheus-project-id> \
  --reference <external-reference-id>
```

## MCP MORPHEUS

Deux tools read-only sont ajoutés au catalogue M10 :

```text
list_external_references
resolve_external_reference
```

Inputs :

```json
{"projectId":"<uuid>","ownerId":"<uuid>"}
```

```json
{"projectId":"<uuid>","referenceId":"<uuid>"}
```

## API HTTP

Routes additives `/api/v1` :

```text
GET /integrations/minos/status
GET /projects/{projectId}/external-references?ownerId=<uuid>
GET /projects/{projectId}/external-references/{referenceId}/resolution
```

Toutes restent read-only vis-à-vis de la référence externe persistée.

## Preuves M12 implémentées

```text
configuration disabled/configured/invalid
exact symbolKey
lexical non-exact rejeté
ambiguity
revision mismatch
unavailable gateway
real MCP STDIO subprocess fixture
live resolution Memory non-mutante
live resolution SQLite + reopen non-mutante
CLI standalone sans MINOS
MORPHEUS MCP STDIO sans MINOS
HTTP status/list/resolve + SQLite unchanged
architecture guards
portable packaging excludes com/minos/*
```

## Gate restant

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Les ADR-0069 à ADR-0072 et ADR-0007 ne seront acceptées qu'après ce gate.
