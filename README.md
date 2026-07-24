# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

> Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?

## Écosystème

```text
MORPHEUS = specification / intent
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = orchestration
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
```

Merges :

```text
M12 = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
```

## CLI

Commandes principales :

```text
projects list | add
sync / sync-status
requirements find
changes list | get
constraints list
decisions list
tasks list
trace-requirement
change-context
analyze-change
quality
```

M12 :

```text
minos-status
external-references list
external-references resolve
```

M13 :

```text
nexus-status
augmented-context requirement --project ID --requirement ID --nexus-project ID_OR_NAME [...]
augmented-context change --project ID --change ID --nexus-project ID_OR_NAME [...]
```

Options M13 : `--budget`, `--source`, `--constraint key=value`, `--explain`.

## MCP

```text
morpheus mcp --stdio
```

```text
M10 14 tools read-only
M12 +2 external-reference tools
M13 +2 augmented-context tools
M13 = 18 tools read-only
```

M13 ajoute :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Voir [`docs/MCP.md`](docs/MCP.md).

## API HTTP

```text
morpheus api --host 127.0.0.1 --port 8765
base = /api/v1
```

M13 ajoute :

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Voir [`docs/API.md`](docs/API.md).

## MINOS optionnel — M12

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Référence code : `system=MINOS`, `resourceType=SYMBOL`, `externalId=symbolKey exact`, révision optionnelle. Résolution live : `stored`, `observed`, `persisted=false`.

Voir [`docs/MINOS.md`](docs/MINOS.md).

## NEXUS optionnel — M13

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Chaque appel fournit explicitement `nexusProject`. MORPHEUS construit seulement l'intention depuis l'ACTIVE ; NEXUS possède la sélection, le ranking, la fusion, la compression et le budget technique. Le `ContextBundle` est live et non persisté (`persisted=false`).

Gate M13 :

```text
346/346 PASS
Architecture 154/154 PASS
NEXUS Integration 7/7 PASS
API 7/7 PASS
CLI 17/17 PASS
Packaging Windows PASS
```

Voir [`docs/NEXUS.md`](docs/NEXUS.md).

## Distribution

```text
morpheus-cli-<version>-all.jar
morpheus-<version>-windows-x64.zip
morpheus-<version>-linux-x64.tar.gz
```

Les archives embarquent les adapters clients MINOS/NEXUS, jamais les moteurs eux-mêmes.

M13 Windows validé :

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
ZIP 33,654,379 bytes
```

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
distribution/build-windows-installer.ps1
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

## Références

- [`docs/ROADMAP.md`](docs/ROADMAP.md)
- [`docs/VALIDATION_M13.md`](docs/VALIDATION_M13.md)
- [`docs/roadmap/M13_EXECUTION.md`](docs/roadmap/M13_EXECUTION.md)
- [`docs/MCP.md`](docs/MCP.md)
- [`docs/API.md`](docs/API.md)
- [`docs/MINOS.md`](docs/MINOS.md)
- [`docs/NEXUS.md`](docs/NEXUS.md)
- [`distribution/README.md`](distribution/README.md)
