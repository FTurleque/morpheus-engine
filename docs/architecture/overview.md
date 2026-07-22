# Vue d'ensemble de l'architecture — MORPHEUS

Statut : **Proposition — à valider pendant C0**

Date : 22 juillet 2026

La source de vérité fonctionnelle reste [`../CAHIER_DES_CHARGES.md`](../CAHIER_DES_CHARGES.md).

---

## 1. Finalité

MORPHEUS transforme des sources de spécification hétérogènes en une représentation normalisée, versionnée, traçable et interrogeable de l'intention d'un projet.

Il doit fonctionner sans dépendance obligatoire à un LLM, à un service cloud, à un provider particulier ou à une technologie de stockage particulière.

---

## 2. Objectifs architecturaux

MORPHEUS doit être :

- provider-agnostic ;
- backend-agnostic à la frontière du domaine ;
- local-first ;
- indépendant des fournisseurs d'IA ;
- utilisable sans LLM ;
- versionné ;
- traçable ;
- explicable ;
- capable de distinguer `CURRENT`, `PROPOSED` et `HISTORICAL` ;
- capable de représenter un cycle de vie indépendant de l'état temporel ;
- capable de conserver des références non résolues ;
- read-first ;
- extensible vers plusieurs providers ;
- consommable par CLI, MCP, API et autres moteurs.

---

## 3. Architecture générale candidate

```text
Sources / dépôts / workspaces
           │
           ▼
Discovery / Project Specification Registry
           │
           ▼
SpecificationProviderRegistry
           │
    probe + capability negotiation
           │
     ┌─────┼────────────────────────┐
     ▼     ▼                        ▼
 OpenSpec  Structured Markdown     Future providers
     │     │                        │
     └─────┴───────────┬────────────┘
                       ▼
              Provider Snapshot
                       │
                       ▼
               MORPHEUS Ingestion
                       │
            identity + normalization
                       │
                       ▼
            Normalized MORPHEUS Model
                       │
                       ▼
              KnowledgeSnapshot
                       │
               validate + activate
                       │
                       ▼
          SpecificationKnowledgeStore
                       │
          ┌────────────┼─────────────┐
          ▼            ▼             ▼
       Queries     Traceability    History
          │            │             │
          └────────────┼─────────────┘
                       ▼
             MORPHEUS Intelligence
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
     Coverage       Change scope    Diagnostics
     Orphans        Context views   Conflicts
                       │
                       ▼
              Application Services
                       │
             ┌─────────┼─────────┐
             ▼         ▼         ▼
            CLI       MCP       API
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       JARVIS        NEXUS         autres
```

---

## 4. Découverte des sources

Responsabilités :

- identifier la racine de projet ;
- détecter les sources de spécification candidates ;
- appliquer les exclusions ;
- construire un inventaire ;
- invoquer les probes de providers ;
- détecter les ambiguïtés ;
- conserver les diagnostics.

La découverte ne doit pas interpréter le domaine métier.

Elle ne doit contenir aucune logique OpenSpec en dehors des adaptateurs/providers dédiés.

---

## 5. `SpecificationProviderRegistry`

Le registre connaît les providers disponibles et organise leur sélection.

Il manipule conceptuellement :

```text
SpecificationProvider
ProviderCapability
ProviderCapabilitySet
ProviderSelectionPolicy
ProbeResult
```

La sélection tient compte :

- du support réel de la source ;
- de la version du format ;
- des capacités obligatoires du cas d'usage ;
- des capacités optionnelles ;
- de la configuration explicite ;
- de la préférence local-first ;
- des diagnostics de compatibilité.

Le détail du contrat est [`../contracts/SPECIFICATION_PROVIDER.md`](../contracts/SPECIFICATION_PROVIDER.md).

### Capacités candidates

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_CONSTRAINTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_ACCEPTANCE_CRITERIA
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
INCREMENTAL_READ
WATCH_CHANGES
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

Un provider read-only est un provider valide.

---

## 6. Provider Snapshot

Un provider produit une vue cohérente de sa source à un instant ou une révision donnés.

Cette vue peut contenir :

- artefacts externes ;
- identifiants externes ;
- emplacements ;
- version du format ;
- source revision ;
- diagnostics ;
- empreintes ;
- deltas si supportés.

Le `ProviderSnapshot` est une structure d'adaptation. Il ne fait pas partie du domaine public MORPHEUS.

---

## 7. Ingestion MORPHEUS

L'ingestion est la frontière anti-corruption entre providers et domaine.

Responsabilités :

1. valider les données importées ;
2. résoudre ou créer les identités MORPHEUS ;
3. séparer identité, version, locator et external ID ;
4. normaliser les concepts ;
5. mapper les statuts ;
6. mapper les relations ;
7. construire provenance et preuves ;
8. conserver les références non résolues ;
9. produire les diagnostics de normalisation ;
10. construire un `KnowledgeSnapshot` candidat.

Aucun type provider-specific ne doit sortir de cette frontière.

---

## 8. Identité

Le domaine distingue :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

Règles :

- un chemin source n'est pas l'identité ;
- un titre n'est pas l'identité ;
- un identifiant externe aide à la résolution mais ne devient pas automatiquement l'identité publique ;
- les ambiguïtés sont représentées ;
- une fusion heuristique silencieuse est interdite.

Voir [`../adr/0009-stable-domain-identity.md`](../adr/0009-stable-domain-identity.md).

---

## 9. Modèle normalisé

Concepts candidats :

```text
ProjectSpecification
Specification
Requirement
Scenario
ChangeProposal
Constraint
DesignDecision
AcceptanceCriterion
ImplementationTask
SpecificationVersion
KnowledgeSnapshot
Evidence
Provenance
TraceabilityLink
ExternalReference
```

Le détail est documenté dans [`../domain/MODEL.md`](../domain/MODEL.md).

### États orthogonaux

#### TemporalState

```text
CURRENT
PROPOSED
HISTORICAL
```

#### ChangeLifecycleState

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

#### ResolutionState

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
HEURISTIC
```

#### VerificationStatus

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Une seule enum universelle est explicitement exclue.

---

## 10. Cycle de vie des changements

Le cycle de vie est une machine d'état normalisée, pas un simple label.

MORPHEUS doit pouvoir à terme exposer :

```text
canTransition
validateTransition
getBlockingConditions
```

La lecture du cycle de vie reste possible sans capacité d'écriture.

Le détail est [`../domain/CHANGE_LIFECYCLE.md`](../domain/CHANGE_LIFECYCLE.md).

---

## 11. `TraceabilityLink`

La traçabilité est un concept de premier ordre.

```text
TraceabilityLink
├── source
├── relationType
├── target
├── resolution
├── origin
├── confidence
├── evidence
├── validFromVersion
└── validToVersion
```

Taxonomie initiale :

```text
REFINES
DERIVES_FROM
CONSTRAINS
IMPLEMENTS
SATISFIES
VALIDATES
VERIFIED_BY
DECIDED_BY
DEPENDS_ON
AFFECTS
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

Le type ne doit pas encoder la confiance.

Exemple correct :

```text
type = IMPLEMENTS
origin = HEURISTIC
resolution = HEURISTIC
confidence = 0.74
```

La direction est canonique et les inverses utiles peuvent être dérivés par la couche de requête.

Voir ADR-0005 et ADR-0010.

---

## 12. Provenance et Evidence

### `Provenance`

Répond à :

> D'où cette information vient-elle et dans quelle révision a-t-elle été observée ?

Champs candidats :

```text
providerId
providerVersion
source
externalId
sourceRevision
importedAt
```

### `Evidence`

Référence la preuve concrète :

```text
source
locator
excerptHash
observedAt
provider
interpretation
```

Le contenu complet de la source n'a pas besoin d'être dupliqué si un locator suffisamment stable permet de l'expliquer.

---

## 13. `KnowledgeSnapshot`

Un snapshot représente un état cohérent du modèle normalisé pour un projet.

Cycle technique candidat :

```text
BUILDING
   ↓
VALIDATING
   ↓
READY
   ↓
ACTIVE
   ↓
RETIRED

FAILED est possible avant activation.
```

Publication observable :

```text
consumers see Vn
       │
activation atomique
       ▼
consumers see Vn+1
```

Le backend peut implémenter cette propriété via transaction, MVCC, génération, staging ou autre mécanisme.

Voir ADR-0012.

---

## 14. `SpecificationKnowledgeStore`

`SpecificationKnowledgeStore` est un port possédé par MORPHEUS.

Le domaine ne doit pas connaître :

- SQL ;
- Cypher ;
- schéma documentaire ;
- graph database ;
- moteur de recherche particulier.

Familles d'opérations :

```text
WRITE
READ
SEARCH
TRAVERSE
SNAPSHOT / VERSION
MAINTENANCE
DIAGNOSTICS
```

Opérations conceptuelles :

```text
storeSnapshot
applyDelta
getCurrentVersion
getSpecification
getRequirement
getChange
findRequirements
findConstraints
findAcceptanceCriteria
findDesignDecisions
findImplementationTasks
search
findOutgoingLinks
findIncomingLinks
traverse
findPath
listVersions
compareVersions
getEvidence
getProvenance
```

Un backend mémoire doit implémenter le contrat.

Un backend persistant local sera sélectionné par M0.

Une graph database ne sera ajoutée que si les benchmarks de traversée démontrent un bénéfice significatif.

Voir [`../contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](../contracts/SPECIFICATION_KNOWLEDGE_STORE.md).

---

## 15. Intelligence MORPHEUS

La couche d'intelligence produit des informations dérivées à partir du modèle normalisé.

Capacités candidates :

```text
CHANGE_SCOPE
REQUIREMENT_COVERAGE
ACCEPTANCE_COVERAGE
ORPHAN_DETECTION
SPEC_CONFLICT
TRACEABILITY_PATH
CURRENT_STATE_RECONSTRUCTION
LIFECYCLE_DIAGNOSTICS
UNRESOLVED_REFERENCE_ANALYSIS
```

Toute information dérivée doit exposer :

```text
origin
resolution
confidence
evidence
path
```

Aucune heuristique ne doit être présentée comme certitude.

---

## 16. Application services

Les services correspondent aux cas d'usage, pas aux tables ou collections du backend.

MVP :

```text
getCurrentSpecification
findRequirements
getChange
listChanges
getConstraints
getAcceptanceCriteria
getImplementationTasks
traceRequirement
getChangeContext
```

Selon le dataset :

```text
getDesignDecisions
```

Plus tard :

```text
compareSpecificationVersions
findConflicts
findUncoveredRequirements
findUnverifiedAcceptanceCriteria
analyzeChangeScope
resolveExternalReference
```

Voir [`../USE_CASES.md`](../USE_CASES.md).

---

## 17. Provider OpenSpec candidat

Architecture :

```text
OpenSpec source
     │
     ▼
OpenSpecSpecificationProvider
     │
 ProviderSnapshot
     │
     ▼
MORPHEUS ingestion
     │
     ▼
MORPHEUS domain
```

Responsabilités de l'adaptateur :

- détecter la structure ;
- détecter la version ;
- lire specs courantes ;
- lire changements ;
- lire proposal/design/tasks lorsque disponibles ;
- lire archives ;
- conserver les emplacements ;
- exposer ses capacités effectives ;
- signaler clairement ce qui n'est pas compris.

OpenSpec reste un provider, pas le domaine.

---

## 18. Écriture et mutation

Le MVP privilégie lecture et compréhension.

Une future capacité d'écriture doit suivre :

```text
request
  ↓
MORPHEUS validation
  ↓
provider WRITE capability
  ↓
permission / conflict policy
  ↓
source mutation
  ↓
re-read / confirmation
```

Règles :

- aucune écriture implicite ;
- aucune correction silencieuse des sources ;
- aucune capacité d'écriture déduite d'une capacité de lecture ;
- idéalement preview/dry-run pour opérations sensibles.

---

## 19. Synchronisation et fraîcheur

Sources possibles de changement :

- empreintes de fichiers ;
- révision Git ;
- timestamp ;
- watcher local ;
- mécanisme natif du provider.

Un provider peut exposer `INCREMENTAL_READ`.

Deltas candidats :

```text
ADDED
MODIFIED
REMOVED
MOVED
UNCHANGED
UNKNOWN
```

Le full rebuild reste toujours un fallback officiel lorsque l'incrémental n'est pas fiable.

---

## 20. Intégration MINOS

Approche candidate :

```text
MORPHEUS entity
      │
      ▼
ExternalReference
      │
      ├── system = MINOS
      ├── resourceType = SYMBOL / FILE / MODULE / TEST
      ├── externalId
      └── version?
```

Un résolveur d'intégration peut enrichir la référence sans introduire de type MINOS dans le domaine.

MORPHEUS reste fonctionnel si MINOS est indisponible.

---

## 21. Intégration NEXUS

MORPHEUS expose des vues compactes :

```text
ChangeContext
├── objective
├── rationale
├── requirements[]
├── constraints[]
├── decisions[]
├── acceptanceCriteria[]
├── tasks[]
├── traceability[]
├── unresolvedReferences[]
├── warnings[]
└── provenance
```

NEXUS reste responsable du ranking global et du budget de contexte.

---

## 22. Intégration JARVIS

MORPHEUS expose les faits nécessaires à l'orchestration :

```text
change state
requirements
constraints
acceptance status
blocking conditions
allowed transitions
unresolved references
```

JARVIS décide quoi faire ensuite.

La logique d'orchestration ne doit pas être implémentée dans les services MORPHEUS.

---

## 23. Exposition

```text
Domain / Application Services
          │
   ┌──────┼──────┐
   ▼      ▼      ▼
  CLI    MCP    API
```

Les handlers ne doivent contenir :

- ni parsing de provider ;
- ni règles de traçabilité ;
- ni machine d'état métier ;
- ni logique de backend.

Les DTO publics doivent être stables et compacts.

---

## 24. Tests architecturaux attendus

M0 doit disposer de tests démontrant notamment :

- aucun type OpenSpec dans le domaine ;
- aucun type backend dans les services ;
- fake provider enregistrable ;
- backend mémoire et persistant passant les mêmes tests de contrat ;
- current/proposed isolation ;
- snapshot activation ;
- identité stable ;
- références non résolues conservées ;
- provenance préservée ;
- traversée de traçabilité stable.

---

## 25. Non-objectifs de C0 et M0

Ne pas construire immédiatement :

- un éditeur complet de spécifications ;
- une plateforme collaborative ;
- un moteur LLM ;
- une génération automatique de specs par IA ;
- un moteur vectoriel ;
- une intégration complète avec tous les trackers ;
- une composition multi-provider de production ;
- un event sourcing complet ;
- une graph database obligatoire ;
- une API publique de production ;
- une orchestration JARVIS complète ;
- un couplage runtime obligatoire à MINOS ou NEXUS.

C0 cadre.

M0 valide les choix structurants par des expérimentations mesurables définies dans [`../research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md).