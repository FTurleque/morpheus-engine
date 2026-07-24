# Architecture MORPHEUS

Cette page décrit l’architecture logique et technique active après M14. Elle précise le sens des dépendances, le modèle temporel, le lifecycle des snapshots et des changements, le chemin des requêtes ainsi que les frontières MINOS/NEXUS/JARVIS.

## 1. Vue système

MORPHEUS reçoit des sources de spécification, les normalise dans un modèle indépendant du provider, publie des snapshots versionnés, puis expose des services de requête, traçabilité, qualité, analyse et orchestration read-only.

```mermaid
flowchart LR
    SRC[Sources / workspaces] --> PROVIDERS[Providers]
    PROVIDERS --> NORM[Normalisation MORPHEUS]
    NORM --> APP[Services applicatifs]
    APP --> DOMAIN[Domain model]
    APP --> SNAP[KnowledgeSnapshot / SpecificationVersion]
    SNAP --> STORE[(Memory / SQLite)]

    CLI[CLI] --> APP
    MCP[MCP STDIO] --> APP
    API[HTTP /api/v1] --> APP

    APP -->|port| MINOS[MINOS adapter]
    APP -->|port| NEXUS[NEXUS adapter]
    MINOS -->|MCP STDIO| MINOSRT[MINOS process]
    NEXUS -->|MCP STDIO| NEXUSRT[NEXUS process]
    JARVIS[JARVIS] -->|HTTP read-only| API
```

OpenSpec est le provider de référence initial, mais il ne définit pas le domaine MORPHEUS.

## 2. Architecture en couches

Le cœur suit une architecture hexagonale/ports-adapters simple :

```text
adapters -> application -> domain
```

```mermaid
flowchart TB
    subgraph Adapters
        CLI
        API
        MCP
        OPENSPEC[OpenSpec provider]
        SQLITE[SQLite store]
        MEMORY[Memory store]
        MINOS[MINOS integration]
        NEXUS[NEXUS integration]
    end

    subgraph Application
        USECASES[Use cases / services]
        PORTS[Ports]
        LIFECYCLE[Lifecycle rules orchestration]
    end

    subgraph Domain
        MODEL[Entities / value objects]
        INVARIANTS[Domain invariants]
    end

    CLI --> USECASES
    API --> USECASES
    MCP --> USECASES
    OPENSPEC --> PORTS
    SQLITE --> PORTS
    MEMORY --> PORTS
    MINOS --> PORTS
    NEXUS --> PORTS
    USECASES --> PORTS
    USECASES --> MODEL
    LIFECYCLE --> MODEL
    PORTS --> MODEL
    MODEL --> INVARIANTS
```

### Règles exécutables principales

- `com.morpheus.domain..` ne dépend d’aucun provider, store, CLI, MCP, API, intégration ou implémentation externe ;
- `com.morpheus.application..` définit use cases et ports sans dépendre des adapters ;
- l’API HTTP reste un sibling de CLI/MCP ;
- les intégrations MINOS/NEXUS implémentent des ports applicatifs ;
- aucune classe MORPHEUS ne dépend de `com.jarvis.*` ;
- les adapters externes ne doivent pas déplacer dans leur couche des règles qui changent le sens métier.

Ces règles sont contrôlées dans `morpheus-architecture-tests` avec ArchUnit.

## 3. Modules Maven

```mermaid
flowchart TB
    D[morpheus-domain]
    A[morpheus-application]
    P1[morpheus-provider-openspec]
    P2[morpheus-provider-synthetic]
    S1[morpheus-store-memory]
    S2[morpheus-store-sqlite]
    I1[morpheus-integration-minos]
    I2[morpheus-integration-nexus]
    MCP[morpheus-mcp]
    API[morpheus-api]
    CLI[morpheus-cli]
    T[morpheus-architecture-tests]

    A --> D
    P1 --> A
    P2 --> A
    S1 --> A
    S2 --> A
    I1 --> A
    I2 --> A
    MCP --> A
    API --> A
    CLI --> A
    CLI --> P1
    CLI --> S2
    CLI --> I1
    CLI --> I2
    CLI --> MCP
    CLI --> API
    T -. vérifie .-> D
    T -. vérifie .-> A
    T -. vérifie .-> API
```

Le parent Maven agrège les modules ; `morpheus-cli` joue le rôle de composition root pour le launcher officiel.

## 4. Domaine : identités et objets principaux

MORPHEUS distingue systématiquement identité logique, version, emplacement source et référence externe.

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
```

Vue UML conceptuelle :

```mermaid
classDiagram
    class ProjectSpecification {
      +ProjectSpecificationId id
      +ProjectKey key
      +String displayName
    }

    class Specification {
      +SpecificationId id
      +TemporalState temporalState
      +SpecificationVersionId versionId
    }

    class Requirement {
      +RequirementId id
      +String title
      +String statement
      +TemporalState temporalState
    }

    class ChangeProposal {
      +ChangeId id
      +String title
      +ChangeLifecycleState lifecycleState
    }

    class Constraint {
      +ConstraintId id
      +String statement
    }

    class AcceptanceCriterion {
      +AcceptanceCriterionId id
      +String statement
    }

    class DesignDecision {
      +DesignDecisionId id
      +String decision
    }

    class ImplementationTask {
      +TaskId id
      +String description
    }

    class TraceabilityLink {
      +TraceabilityLinkId id
      +TraceabilityRelationType type
      +ResolutionState resolution
    }

    class ExternalReference {
      +String system
      +String resourceType
      +String externalId
      +String version
    }

    class SpecificationVersion {
      +SpecificationVersionId id
      +String sourceRevision
    }

    class KnowledgeSnapshot {
      +KnowledgeSnapshotId id
      +KnowledgeSnapshotState state
    }

    ProjectSpecification "1" --> "0..*" Specification
    ProjectSpecification "1" --> "0..*" ChangeProposal
    Specification "1" --> "0..*" Requirement
    ChangeProposal "1" --> "0..*" Constraint
    ChangeProposal "1" --> "0..*" AcceptanceCriterion
    ChangeProposal "1" --> "0..*" DesignDecision
    ChangeProposal "1" --> "0..*" ImplementationTask
    ProjectSpecification "1" --> "0..*" SpecificationVersion
    ProjectSpecification "1" --> "0..*" KnowledgeSnapshot
    TraceabilityLink --> Requirement
    TraceabilityLink --> ChangeProposal
    Requirement --> ExternalReference
    ChangeProposal --> ExternalReference
```

Ce diagramme exprime les responsabilités conceptuelles ; il ne remplace pas les types Java ni les ADR.

## 5. Temporalité : CURRENT / PROPOSED / HISTORICAL

L’état temporel décrit la position d’une information par rapport à la référence publiée.

```mermaid
stateDiagram-v2
    [*] --> CURRENT
    [*] --> PROPOSED
    CURRENT --> HISTORICAL: publication d'une nouvelle référence
    PROPOSED --> CURRENT: promotion explicite + activation
```

Règles :

- une proposition ne fuit jamais implicitement dans `CURRENT` ;
- une lecture de changement proposé ne modifie pas la référence ;
- une analyse n’est pas une promotion ;
- l’historique publié n’est pas réécrit.

## 6. SpecificationVersion et KnowledgeSnapshot

Ces concepts répondent à des questions différentes :

- `SpecificationVersion` : identité/version logique de la spécification ;
- `KnowledgeSnapshot` : ensemble cohérent de connaissances persistées avec un lifecycle technique.

```mermaid
classDiagram
    class SpecificationVersion {
      +SpecificationVersionId id
      +ProjectSpecificationId projectId
      +String sourceRevision
      +SpecificationVersionId predecessor
    }

    class KnowledgeSnapshot {
      +KnowledgeSnapshotId id
      +KnowledgeSnapshotState state
      +SpecificationVersionId specificationVersionId
    }

    SpecificationVersion "1" <-- "1" KnowledgeSnapshot : référence
```

`SpecificationVersion != KnowledgeSnapshot` reste un invariant explicite.

## 7. Lifecycle d’un KnowledgeSnapshot

États réels du domaine snapshot :

```text
BUILDING | VALIDATING | READY | ACTIVE | FAILED | RETIRED
```

```mermaid
stateDiagram-v2
    [*] --> BUILDING
    BUILDING --> VALIDATING
    VALIDATING --> READY: validation réussie
    VALIDATING --> FAILED: validation échouée
    READY --> ACTIVE: activation atomique
    ACTIVE --> RETIRED: un nouveau snapshot devient ACTIVE
    FAILED --> [*]
    RETIRED --> [*]
```

La publication doit respecter une propriété conservatrice : un candidat échoué ne détrône jamais l’ancien `ACTIVE`.

## 8. Synchronisation publiée

La synchronisation officielle suit une reconstruction complète conservatrice.

```mermaid
sequenceDiagram
    actor Caller
    participant Surface as CLI / API
    participant Registry as Project registry
    participant Provider as Provider
    participant App as Synchronisation application
    participant Store as Snapshot store

    Caller->>Surface: sync(projectId, revision?)
    Surface->>Registry: résoudre projet/workspace
    Registry-->>Surface: configuration projet
    Surface->>Provider: découvrir + lire source
    Provider-->>App: modèle normalisé
    App->>Store: créer BUILDING
    App->>Store: persister contenu candidat
    App->>Store: passer VALIDATING
    App->>App: valider invariants
    alt valide
        App->>Store: READY
        App->>Store: activation atomique
        Note over Store: ancien ACTIVE -> RETIRED
        App-->>Surface: succès + nouveau snapshot
    else invalide
        App->>Store: FAILED
        Note over Store: ancien ACTIVE inchangé
        App-->>Surface: erreur classifiée
    end
    Surface-->>Caller: résultat
```

Une synchronisation n’est pas une simple copie de fichiers : elle reconstruit un modèle normalisé cohérent avant publication.

## 9. RequirementDelta : APPLY, PROMOTE, ACTIVATE

La chaîne de mutation est explicitement séparée :

```text
APPLY != PROMOTE != ACTIVATE
```

```mermaid
sequenceDiagram
    participant Delta as RequirementDelta
    participant Proposed as Projection PROPOSED
    participant Current as Projection CURRENT
    participant Snapshot as Snapshot publication

    Delta->>Proposed: APPLY
    Note over Proposed: modifie la proposition ciblée
    Proposed->>Current: PROMOTE explicite
    Note over Current: changement de projection contrôlé
    Current->>Snapshot: ACTIVATE via publication
    Note over Snapshot: rend le résultat référence active
```

Une analyse, requête, résolution externe ou évaluation lifecycle ne déclenche implicitement aucune de ces étapes.

## 10. Lifecycle d’un ChangeProposal

La machine déterministe est `ChangeLifecycleStateMachine`.

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PROPOSED
    PROPOSED --> SPECIFIED: requirementsIdentified && criticalConstraintsKnown && acceptanceCriteriaDefined
    SPECIFIED --> DESIGNED: designRequired && designDecisionsAvailable
    SPECIFIED --> PLANNED: !designRequired && planPresent
    DESIGNED --> PLANNED: planPresent
    PLANNED --> IMPLEMENTING: !knownBlocker
    IMPLEMENTING --> VERIFYING
    VERIFYING --> COMPLETED: aucun critère bloquant failed/unverified
    COMPLETED --> ARCHIVED

    DRAFT --> ABANDONED: raison requise
    PROPOSED --> ABANDONED: raison requise
    SPECIFIED --> ABANDONED: raison requise
    DESIGNED --> ABANDONED: raison requise
    PLANNED --> ABANDONED: raison requise
    IMPLEMENTING --> ABANDONED: raison requise
    VERIFYING --> ABANDONED: raison requise
    ABANDONED --> PROPOSED
```

Transitions arrière canoniques possibles sous politique explicite :

```text
SPECIFIED     -> PROPOSED
DESIGNED      -> SPECIFIED
PLANNED       -> DESIGNED
IMPLEMENTING  -> PLANNED
VERIFYING     -> IMPLEMENTING
COMPLETED     -> VERIFYING
```

Elles exigent `allowBackwardTransitions=true`. `COMPLETED -> VERIFYING` exige en plus `allowCompletedReopen=true`.

`ARCHIVED` ne peut pas être rouvert par cette machine.

## 11. Évaluation de transition vs mutation

Le contrat M14 expose seulement une décision read-only :

```mermaid
sequenceDiagram
    participant C as Client/JARVIS
    participant O as ChangeOrchestrationStateService
    participant E as ChangeTransitionEvaluationService
    participant SM as ChangeLifecycleStateMachine

    C->>O: lire faits observables
    O-->>C: lifecycle/faits/manques
    C->>E: evaluate(from, target, policy)
    E->>SM: évaluer avec faits connus
    SM-->>E: allowed/blocked
    E-->>C: ALLOWED/BLOCKED/UNKNOWN/REQUIRES_INPUT
    Note over C,SM: aucune transition n'est appliquée
```

`UNKNOWN` est important : un fait indisponible ne doit jamais être converti en `false` pour fabriquer une décision.

## 12. Traçabilité

`TraceabilityLink` est un concept de premier ordre. MORPHEUS conserve des liens observables, persistés ou déterministement dérivables selon les contrats validés.

```text
absence de lien != lien inventé
DETERMINISTIC != HEURISTIC
Scenario != AcceptanceCriterion
```

```mermaid
classDiagram
    class TraceabilityLink {
      +EntityRef source
      +TraceabilityRelationType type
      +EntityRef target
      +ResolutionState resolution
      +LinkOrigin origin
      +Confidence confidence
    }
    class Evidence {
      +SourceReference source
      +SourceLocator locator
      +String excerptHash
    }
    class EntityRef

    TraceabilityLink --> EntityRef : source
    TraceabilityLink --> EntityRef : target
    TraceabilityLink --> Evidence : justifié par
```

Les diagnostics qualité et vues de contexte dérivent de ces faits ; ils ne mutent pas les snapshots.

## 13. Chemin d’une query

```mermaid
sequenceDiagram
    actor Caller
    participant Adapter as CLI / MCP / API
    participant Query as Application query service
    participant Repo as Repository port
    participant Store as SQLite/Memory

    Caller->>Adapter: requête + projectId
    Adapter->>Query: commande structurée
    Query->>Repo: lire snapshot ACTIVE
    Repo->>Store: requête persistée
    Store-->>Repo: entités snapshot-scoped
    Repo-->>Query: modèle domaine/application
    Query-->>Adapter: vue compacte
    Adapter-->>Caller: texte ou JSON
```

Les queries sont snapshot-scoped ; elles ne lisent pas directement le workspace source.

## 14. MINOS

```mermaid
sequenceDiagram
    participant App as MORPHEUS application
    participant Port as ExternalReferenceResolver
    participant Adapter as morpheus-integration-minos
    participant Minos as MINOS process

    App->>Port: resolve(reference)
    Port->>Adapter: appel adapter
    Adapter->>Minos: MCP STDIO
    Minos-->>Adapter: observation
    Adapter-->>Port: résultat typé
    Port-->>App: FOUND/NOT_FOUND/UNAVAILABLE/...
```

Contraintes :

- aucune dépendance `com.minos.*` ;
- MINOS n’est pas embarqué ;
- matching d’identité exact sur `symbolKey` ;
- observation live séparée de la référence persistée ;
- `persisted=false` pour la résolution live.

## 15. NEXUS

```mermaid
sequenceDiagram
    participant App as MORPHEUS application
    participant Port as TechnicalContextProvider
    participant Adapter as morpheus-integration-nexus
    participant Nexus as NEXUS MCP runner

    App->>App: construire MorpheusIntentContext
    App->>Port: request(context, budget, filters)
    Port->>Adapter: appel adapter
    Adapter->>Nexus: MCP STDIO
    Nexus->>Nexus: select/rank/fuse/compress
    Nexus-->>Adapter: ContextBundle
    Adapter-->>App: ContextBundle persisted=false
```

Frontière :

```text
MORPHEUS = intention structurée
NEXUS    = sélection / ranking / fusion / compression / budget
```

MORPHEUS ne reranke pas le bundle et ne le persiste pas dans `KnowledgeSnapshot`.

## 16. JARVIS

```mermaid
sequenceDiagram
    participant J as JARVIS
    participant API as MORPHEUS HTTP API
    participant O as Orchestration service
    participant E as Transition evaluation

    J->>API: GET orchestration
    API->>O: construire état observable
    O-->>API: facts + missing/unavailable
    API-->>J: JSON read-only
    J->>API: POST transition-check
    API->>E: évaluer
    E-->>API: décision
    API-->>J: ALLOWED/BLOCKED/UNKNOWN/REQUIRES_INPUT
    Note over J: choisit la prochaine action
```

Frontière :

```text
MORPHEUS = specification facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

MORPHEUS n’applique pas de transition à la demande de ce contrat et ne choisit pas l’action suivante.

## 17. Composition root

`morpheus-cli` porte le launcher officiel `MorpheusMain`. Il compose :

- SQLite ;
- providers ;
- intégrations MINOS/NEXUS optionnelles ;
- serveur MCP ;
- serveur HTTP ;
- surfaces CLI.

La composition root peut connaître les implémentations concrètes pour les assembler, mais ne doit pas héberger de nouvelle règle métier.

## 18. Comment décider où ajouter du code ?

| Besoin | Couche/module |
|---|---|
| nouvelle valeur/invariant métier pur | `morpheus-domain` |
| nouveau use case ou règle d’orchestration métier | `morpheus-application` |
| lire un nouveau format source | nouveau/ancien `morpheus-provider-*` |
| nouvelle persistance | `morpheus-store-*` via port applicatif |
| nouvelle surface HTTP | `morpheus-api` |
| nouveau tool MCP | `morpheus-mcp` |
| nouvelle commande et wiring | `morpheus-cli` |
| appel à un moteur externe | `morpheus-integration-*` via port |
| interdiction de dépendance | `morpheus-architecture-tests` |

## 19. Décisions d’architecture

Voir [`../adr/README.md`](../adr/README.md). Les ADR restent la source de vérité pour le **pourquoi** des choix ; cette page décrit leur architecture résultante.
