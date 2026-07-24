# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

> Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?

## Écosystème

```text
MORPHEUS = specification / intent / lifecycle rules
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration / sequencing
```

Chaque moteur reste autonome.

## Architecture

```text
Sources / workspaces
 -> providers
 -> normalisation MORPHEUS
 -> KnowledgeSnapshot / SpecificationVersion
 -> Memory / SQLite
 -> Query / Traceability / Quality / Change Analysis
 -> CLI | MCP | HTTP API
              |
              +-> optional MINOS adapter -> MCP STDIO -> MINOS
              +-> optional NEXUS adapter -> MCP STDIO -> NEXUS
              +-> read-only orchestration contract <- HTTP <- JARVIS
```

OpenSpec est le provider de référence initial, pas le domaine MORPHEUS.

## Invariants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
optional engine absence != MORPHEUS failure
live external observation != snapshot mutation
NEXUS ranking != MORPHEUS ranking
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
MORPHEUS facts/rules != JARVIS action sequencing
```

## Fondation technique

```text
Java                 21
Build                Maven Wrapper
Persistent store     SQLite
DomainIdentity       UUIDv7
MCP SDK              Java MCP SDK 2.0.0
MCP transport        STDIO
HTTP server          JDK jdk.httpserver
Distribution         jpackage portable app-image
LLM required         no
```

## État du projet

```text
C0-M2  fondation                                ✅ VALIDÉ
M3     temporalité / lifecycle                  ✅ VALIDÉ / INTÉGRÉ — 147/147
M4     traçabilité                              ✅ VALIDÉ / INTÉGRÉ — 189/189
M5     requêtes / contexte compact              ✅ VALIDÉ / INTÉGRÉ — 227/227
M6     qualité / diagnostics                    ✅ VALIDÉ / INTÉGRÉ — 261/261
M7     sync incrémentale                        ✅ VALIDÉ / INTÉGRÉ — 282/282
M8     analyse changements                      ✅ VALIDÉ / INTÉGRÉ — 289/289
M9     CLI / distribution                       ✅ VALIDÉ / INTÉGRÉ — 298/298
M10    MCP STDIO                                ✅ VALIDÉ / INTÉGRÉ — 307/307
M11    API HTTP                                 ✅ VALIDÉ / INTÉGRÉ — 314/314
M12    MINOS optionnel                          ✅ VALIDÉ / INTÉGRÉ — 331/331
M13    NEXUS optionnel                          ✅ VALIDÉ / INTÉGRÉ — 346/346
M14    JARVIS orchestration contract             ✅ VALIDÉ — 357/357
```

M14 baseline : `5269fbf8ef5586e0e04a776293dda2bf46786d0d`.

M14 validation : [`docs/VALIDATION_M14.md`](docs/VALIDATION_M14.md).

## CLI

M12 :

```text
minos-status
external-references list|resolve
```

M13 :

```text
nexus-status
augmented-context requirement|change
```

M14 :

```text
change-orchestration state --project ID --change ID [--lifecycle STATE] [...]
change-orchestration transition-check --project ID --change ID --from STATE --to STATE [...]
```

M14 est read-only : aucune commande n'applique une transition.

## MCP

```text
morpheus mcp --stdio
```

```text
M10 14 tools read-only
M12 +2 external-reference tools
M13 +2 augmented-context tools
M14 +2 orchestration tools
M14 = 20 tools read-only
```

M14 ajoute :

```text
get_change_orchestration_state
evaluate_change_transition
```

## API HTTP

```text
morpheus api --host 127.0.0.1 --port 8765
base = /api/v1
```

M14 ajoute :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une évaluation pure, pas une mutation.

## JARVIS — M14

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

Lifecycle :

```text
absent -> source=UNAVAILABLE
fourni -> source=CALLER_SUPPLIED
```

Décisions :

```text
ALLOWED | BLOCKED | UNKNOWN | REQUIRES_INPUT
```

Le contrat UC-16 expose les manques déterministes, les faits indisponibles, contraintes applicables, liens non résolus, findings qualité et transitions évaluées, avec `persisted=false`.

Preuve cross-repo JARVIS : issue #92 / PR #93 dans `FTurleque/jarvis`, client HTTP optionnel et fail-open, aucune dépendance `com.morpheus.*`.

Le client conserve séparément les raisons d'abandon observation/source/cible.

Voir [`docs/JARVIS.md`](docs/JARVIS.md).

## MINOS optionnel — M12

Voir [`docs/MINOS.md`](docs/MINOS.md).

## NEXUS optionnel — M13

Voir [`docs/NEXUS.md`](docs/NEXUS.md).

## Distribution M14

```text
morpheus-cli-<version>-all.jar
morpheus-<version>-windows-x64.zip
morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent MORPHEUS et les adapters clients MINOS/NEXUS, jamais MINOS, NEXUS ou JARVIS.

Packaging M14 validé :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
ZIP 33,702,405 bytes
```

## Gate M14

MORPHEUS sur `d44d418ae0f1e528ea09a56cdd8c45647048c740` :

```text
TOTAL         357/357 PASS
Architecture  160/160 PASS
API              9/9 PASS
CLI            20/20 PASS
Packaging          PASS
```

JARVIS sur `58899855bcd3446636c1f274ace8c1bfc8f46930` :

```text
jarvis-core 536 tests
Failures 0
Errors 0
Skipped 16
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

ADR-0077..0080 : **Acceptées — M14**.

## Références

- [`docs/VALIDATION_M14.md`](docs/VALIDATION_M14.md)
- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`docs/roadmap/M14_EXECUTION.md`](docs/roadmap/M14_EXECUTION.md)
- [`docs/JARVIS.md`](docs/JARVIS.md)
- [`docs/MCP.md`](docs/MCP.md)
- [`docs/API.md`](docs/API.md)
- [`docs/openapi/morpheus-v1.yaml`](docs/openapi/morpheus-v1.yaml)
- [`distribution/README.md`](distribution/README.md)
