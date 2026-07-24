# M12 — Plan d'exécution détaillé

Statut : **EN COURS — intégration optionnelle MINOS**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M11 validés et intégrés
M11 merge = e30ed4095700b445fedc4517c22ff447c22238f4
M11 gate  = 314/314 PASS + API HTTP packagée
```

Issue : **#61 — M12 — Intégration optionnelle MINOS et résolution code**  
Branche : `m12/minos-integration`

## Question de sortie

> **MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?**

Réponse actuelle : **implémentation en cours ; gate pending**.

## M12-S1 — Contrat cross-engine

MORPHEUS conserve les contrats existants :

```text
ExternalReference
ExternalReferenceResolver
ExternalReferenceResolutionService
ExternalReferenceStore
ExternalTraceabilityQueryService
LINKS_TO_CODE
```

MINOS reste un système externe. Aucun type `com.minos.*` ne peut traverser le domaine/application MORPHEUS.

## M12-S2 — Transport MINOS

Transport candidat : **MINOS MCP STDIO**.

```text
MORPHEUS Java 21
    -> MCP Java client 2.0.0
    -> process MINOS Java 24
    -> MinosMcpServer
```

Raisons :

```text
pas de dépendance binaire au domaine MINOS
pas de conflit Java 21 / Java 24
process lifecycle séparé
MINOS absent reste normal
contrat tools déjà validé côté MINOS
```

Tools M12 requis uniquement :

```text
minos_index_status
minos_find_symbols
```

La connexion refuse silencieusement toute hypothèse si les tools requis manquent : l'intégration devient indisponible, MORPHEUS reste disponible.

## M12-S3 — Coordonnée MINOS exacte

Contrat M12 :

```text
system       = MINOS
resourceType = SYMBOL
project      = identifiant ou nom unique MINOS ; obligatoire
externalId   = symbolKey MINOS exact ; obligatoire
revision     = activeSnapshotId attendu ; optionnel
```

Aucun fuzzy matching.

`minos_find_symbols` peut être lexical, mais le resolver n'accepte ensuite qu'un résultat dont `symbolKey.equals(externalId)`.

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

## M12-S4 — Révision / stale

Avant la recherche symbole, `minos_index_status` fournit l'`activeSnapshotId`.

Lorsque la référence porte `revision` :

```text
revision == activeSnapshotId -> résolution autorisée
revision != activeSnapshotId -> REVISION_MISMATCH
```

Aucune résolution n'est promue contre une révision différente de celle explicitement demandée.

## M12-S5 — Sémantique de résolution enrichie

Le contrat générique ajoute sans casser les états existants :

```text
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

Raisons de domaine :

```text
TARGET_AMBIGUOUS
TARGET_REVISION_MISMATCH
TARGET_TYPE_UNSUPPORTED
```

Projection :

```text
FOUND             -> RESOLVED / RESOLVED
NOT_FOUND         -> UNRESOLVED/TARGET_NOT_FOUND ou STALE/TARGET_REMOVED
UNAVAILABLE       -> UNRESOLVED ou STALE / TARGET_UNAVAILABLE
AMBIGUOUS         -> UNRESOLVED ou STALE / TARGET_AMBIGUOUS
REVISION_MISMATCH -> UNRESOLVED ou STALE / TARGET_REVISION_MISMATCH
UNSUPPORTED       -> UNRESOLVED ou STALE / TARGET_TYPE_UNSUPPORTED
```

## M12-S6 — Résolution live, snapshot immuable

Invariant ADR-0041 :

```text
same snapshot + same ExternalReferenceId + different value = collision
```

M12 ne persiste donc jamais une nouvelle résolution dans le snapshot ACTIVE existant.

```text
stored reference
    -> resolve live
    -> observed copy
    -> response
    -X-> putReference(snapshot, changedReference)
```

Le `TraceabilityLink` historique reste également inchangé ; sa résolution au moment de l'observation est immuable.

## M12-S7 — Cible MINOS résolue

Le resolver canonise la cible avec la révision effectivement interrogée et expose des attributs contrôlés :

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

Aucun contenu source complet n'est importé dans MORPHEUS.

## M12-S8 — Configuration optionnelle

```text
MORPHEUS_MINOS_JAR              active l'intégration
MORPHEUS_MINOS_JAVA             défaut = java
MORPHEUS_MINOS_HOME             optionnel
MORPHEUS_MINOS_TIMEOUT_SECONDS  défaut = 20 ; borné 1..120
```

Priorité :

```text
propriété Java morpheus.minos.*
    > variable MORPHEUS_MINOS_*
    > défaut
```

Sans JAR configuré : aucun resolver MINOS n'est enregistré.

## M12-S9 — Surface CLI

Additif :

```text
external-references list --project ID --owner ID
external-references resolve --project ID --reference ID
minos-status
```

`resolve` est une lecture live ; il ne mute pas SQLite.

## M12-S10 — Surface MCP

Le catalogue MORPHEUS reste read-only et devient additivement :

```text
list_external_references
resolve_external_reference
```

Les tools retournent la référence persistée et, pour la résolution, l'observation live séparée.

## M12-S11 — Surface API HTTP

Ajouts compatibles `/api/v1` :

```text
GET /api/v1/integrations/minos/status
GET /api/v1/projects/{projectId}/external-references?ownerId=...
GET /api/v1/projects/{projectId}/external-references/{referenceId}/resolution
```

Aucun endpoint write MINOS.

## M12-S12 — Architecture

Nouveau module candidat :

```text
morpheus-integration-minos
```

Règles :

```text
domain/application -X-> integration-minos
domain/application -X-> com.minos..
api/mcp            -X-> integration-minos
integration-minos  -X-> cli/api/mcp/store
integration-minos  -X-> com.minos..
cli = composition root
```

Le module dépend uniquement du contrat application, du MCP SDK et du JSON déjà aligné.

## M12-S13 — Tests

Preuves minimales :

```text
ExternalReferenceResolutionServiceTest étendu
LiveExternalReferenceResolutionServiceTest
MinosIntegrationSettingsTest
MinosMcpExternalReferenceResolverTest
MinosMcpTransportIntegrationTest
Morpheus CLI M12 tests
Morpheus MCP M12 tests
Morpheus API M12 tests
LayerDependencyTest étendu
```

Scénarios :

```text
MINOS absent -> NO_RESOLVER, MORPHEUS reste vert
process MINOS indisponible -> TARGET_UNAVAILABLE
resourceType non SYMBOL -> TARGET_TYPE_UNSUPPORTED
project absent -> résultat non résolu explicite
revision mismatch -> TARGET_REVISION_MISMATCH
symbolKey exact unique -> RESOLVED
résultat lexical non exact -> rejeté
plusieurs exacts -> TARGET_AMBIGUOUS
attributs MINOS déterministes
stored reference inchangée après live resolution
SQLite reopen conserve la référence source inchangée
```

Le test transport démarre un vrai serveur MCP STDIO fixture, pas un mock de méthode Java.

## M12-S14 — Distribution

Le shaded JAR portable contient le client/adaptateur MINOS mais **pas MINOS lui-même**.

```text
MORPHEUS standalone = toujours fonctionnel
MINOS jar = dépendance runtime optionnelle externe
```

Le packaging doit vérifier :

```text
morpheus-integration-minos classes embedded
MORPHEUS --version sans MINOS
MCP/API M11 toujours fonctionnels sans MINOS
```

## M12-S15 — Gate final

Source de vérité :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

M12 ne sera `VALIDÉ` qu'après preuve reproductible.

## ADR candidates

```text
ADR-0069 — Proposée — intégration MINOS par MCP STDIO inter-processus
ADR-0070 — Proposée — symbolKey exact + révision explicite
ADR-0071 — Proposée — résolution live sans mutation de snapshot publié
ADR-0072 — Proposée — configuration/surfaces MINOS optionnelles
```

ADR-0007 pourra enfin passer d'état historique proposé à **Acceptée** après preuve M12 d'une intégration cross-engine réelle et optionnelle.

## Hors périmètre M12

```text
copie du domaine MINOS dans MORPHEUS
compile dependency com.minos.*
indexation SCIP pilotée par MORPHEUS
installation automatique de MINOS
MINOS embarqué dans le ZIP MORPHEUS
fuzzy code matching
source code complet copié dans MORPHEUS
NEXUS ranking/compression
JARVIS orchestration
```
