# Architecture MORPHEUS

## Vue d’ensemble

```text
Sources / workspaces
  -> providers
  -> normalisation MORPHEUS
  -> KnowledgeSnapshot / SpecificationVersion
  -> Memory | SQLite
  -> Query / Traceability / Quality / Change Analysis
  -> CLI | MCP | HTTP API
                    |
                    +-> MINOS optionnel via MCP STDIO
                    +-> NEXUS optionnel via MCP STDIO
                    +-> contrat d'orchestration read-only <- HTTP <- JARVIS
```

OpenSpec est le provider de référence initial. Il ne définit pas le domaine MORPHEUS.

## Sens des dépendances

Le cœur dépend vers l’intérieur :

```text
adapters -> application -> domain
```

Règles exécutables principales :

- `com.morpheus.domain..` ne dépend d’aucun provider, store, CLI, MCP, API, intégration ou implémentation MINOS/NEXUS/JARVIS ;
- `com.morpheus.application..` définit les use cases et ports sans dépendre des adapters ;
- l’API HTTP reste un sibling de CLI/MCP et ne dépend pas d’eux ;
- les intégrations MINOS/NEXUS implémentent des ports applicatifs sans dépendre des adapters externes MORPHEUS ;
- aucune classe MORPHEUS ne dépend de `com.jarvis.*`.

Ces règles sont contrôlées dans `morpheus-architecture-tests` avec ArchUnit.

## Domaine et temporalité

Identités distinctes :

```text
DomainIdentity
EntityVersionId
SourceLocator
ExternalReference
```

Temporalité :

```text
CURRENT
PROPOSED
HISTORICAL
```

Les snapshots publiés suivent une histoire explicite :

```text
RETIRED* -> ACTIVE
```

Une proposition ne fuit jamais implicitement dans CURRENT.

## RequirementDelta

La chaîne de mutation reste explicitement séparée :

```text
APPLY != PROMOTE != ACTIVATE
```

Une analyse, une requête, une résolution externe ou une évaluation lifecycle ne déclenche pas implicitement l’une de ces étapes.

## Traçabilité et qualité

MORPHEUS conserve uniquement des liens observables/persistés ou déterministement dérivables selon les contrats validés.

```text
absence de lien != lien inventé
Scenario != AcceptanceCriterion
DETERMINISTIC != HEURISTIC
```

Les diagnostics qualité sont des vues dérivées ; ils ne mutent pas les snapshots publiés.

## MINOS

```text
MORPHEUS ExternalReference
 -> application port
 -> morpheus-integration-minos
 -> Java MCP client / STDIO
 -> process MINOS
```

MORPHEUS ne dépend d’aucune classe `com.minos.*` et n’embarque pas MINOS.

Une résolution live :

```text
stored reference -> observed reference -> response
                                -X-> snapshot rewrite
```

## NEXUS

```text
MORPHEUS intent
 -> TechnicalContextProvider
 -> morpheus-integration-nexus
 -> Java MCP client / STDIO
 -> NEXUS
```

Responsabilités :

```text
MORPHEUS = intention structurée
NEXUS    = sélection / ranking / fusion / compression / budget
```

Le `ContextBundle` reste live et `persisted=false`.

## JARVIS

```text
MORPHEUS = specification facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

M14 expose un contrat HTTP read-only. MORPHEUS n’applique pas de transition à la demande de ce contrat et ne choisit pas l’action suivante.

Décisions de transition :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

`UNAVAILABLE` n’est jamais converti en `false` pour fabriquer une décision.

## Composition root

`morpheus-cli` porte le launcher officiel `MorpheusMain`. Il compose :

- SQLite ;
- providers ;
- intégrations MINOS/NEXUS optionnelles ;
- serveur MCP ;
- serveur HTTP ;
- surfaces CLI M14.

La composition ne déplace pas les règles métier dans l’adapter CLI.

## Décisions d’architecture

Voir [`../adr/README.md`](../adr/README.md). Les ADR M14 sont `ADR-0077` à `ADR-0080`.
