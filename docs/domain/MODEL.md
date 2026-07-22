# Modèle de domaine — MORPHEUS

Statut : **Proposition C0 — à valider avant implémentation**

Date : 22 juillet 2026

Ce document précise le modèle de domaine candidat de MORPHEUS. Il complète le cahier des charges sans le remplacer.

> Le domaine MORPHEUS décrit l'intention, les spécifications, les changements et leur traçabilité. Il ne reproduit ni le modèle d'un provider ni celui d'un outil de gestion de projet.

---

## 1. Principes

Le modèle doit :

- rester indépendant d'OpenSpec et de tout autre format ;
- distinguer identité logique, version, emplacement et identifiant externe ;
- distinguer l'état temporel d'un élément de son cycle de vie ;
- représenter la provenance et les preuves ;
- permettre une traçabilité intra-MORPHEUS et cross-engine ;
- supporter les éléments partiellement résolus ;
- produire des vues compactes sans perdre la possibilité d'expliquer leur origine.

---

## 2. Vue d'ensemble

```text
ProjectSpecification
        │
        ├── Specification
        │      ├── Requirement
        │      ├── Constraint
        │      ├── Scenario
        │      └── AcceptanceCriterion
        │
        ├── ChangeProposal
        │      ├── Requirement delta
        │      ├── Constraint delta
        │      ├── Scenario delta
        │      ├── DesignDecision
        │      ├── AcceptanceCriterion
        │      └── ImplementationTask
        │
        ├── SpecificationVersion
        ├── TraceabilityLink
        ├── ExternalReference
        └── Evidence
```

---

## 3. `ProjectSpecification`

Représente le périmètre de connaissance de MORPHEUS pour un projet ou workspace.

Attributs conceptuels :

```text
ProjectSpecification
- id: ProjectSpecificationId
- key: ProjectKey
- displayName: string
- rootReference: SourceReference?
- currentVersion: SpecificationVersionId?
- metadata: Map<String, Value>
```

### Invariants

- `id` est une identité MORPHEUS, jamais un chemin de fichier brut ;
- `key` est stable à l'échelle de l'installation ou du registre local ;
- les identifiants propres aux providers sont conservés comme références externes ;
- un projet peut agréger plusieurs sources de spécification si la stratégie de résolution l'autorise.

---

## 4. `Specification`

Représente un ensemble cohérent de règles ou comportements attendus dans l'état de référence d'un projet.

Attributs candidats :

```text
Specification
- id: SpecificationId
- projectId: ProjectSpecificationId
- key: SpecificationKey
- title: string
- description: string?
- temporalState: TemporalState
- lifecycleState: LifecycleState?
- versionId: SpecificationVersionId
- provenance: Provenance
- externalReferences: List<ExternalReference>
- evidence: List<EvidenceRef>
```

Une `Specification` n'est pas nécessairement équivalente à un fichier. Un provider peut reconstruire une spécification à partir de plusieurs fichiers ou sections.

---

## 5. `Requirement`

Décrit une obligation fonctionnelle ou non fonctionnelle observable ou vérifiable.

```text
Requirement
- id: RequirementId
- specificationId: SpecificationId
- key: RequirementKey?
- title: string
- statement: string
- category: RequirementCategory
- priority: Priority?
- temporalState: TemporalState
- resolution: ResolutionState
- provenance: Provenance
```

Catégories candidates :

```text
FUNCTIONAL
NON_FUNCTIONAL
SECURITY
PERFORMANCE
COMPATIBILITY
OPERABILITY
COMPLIANCE
OTHER
```

La taxonomie doit rester extensible ; elle ne doit pas imposer une liste fermée aux providers.

---

## 6. `Constraint`

Représente une règle qui limite les solutions acceptables sans nécessairement décrire une fonctionnalité.

Exemples :

- local-first ;
- aucune dépendance obligatoire à un LLM ;
- compatibilité avec une plateforme ;
- contrainte réglementaire ;
- interdiction d'une dépendance ;
- limite de latence.

```text
Constraint
- id: ConstraintId
- scope: EntityRef
- statement: string
- type: ConstraintType
- severity: ConstraintSeverity?
- temporalState: TemporalState
- provenance: Provenance
```

---

## 7. `Scenario`

Décrit un comportement attendu dans un contexte donné.

```text
Scenario
- id: ScenarioId
- requirementId: RequirementId?
- title: string
- preconditions: List<Condition>
- action: string
- expectedOutcome: string
- examples: List<ScenarioExample>
- temporalState: TemporalState
- provenance: Provenance
```

MORPHEUS ne doit pas exiger Gherkin. Un provider peut mapper Given/When/Then vers ce modèle, mais le domaine reste neutre.

---

## 8. `ChangeProposal`

Représente une intention de modifier l'état de référence du projet.

```text
ChangeProposal
- id: ChangeId
- projectId: ProjectSpecificationId
- key: ChangeKey
- title: string
- summary: string?
- rationale: string?
- lifecycleState: ChangeLifecycleState
- createdAt: Instant?
- updatedAt: Instant?
- provenance: Provenance
```

Un changement peut contenir ou référencer :

- des deltas de spécification ;
- de nouvelles exigences ;
- des exigences modifiées ou supprimées ;
- des décisions de conception ;
- des tâches d'implémentation ;
- des critères d'acceptation ;
- des références vers le code ou les tests.

### Principe essentiel

Un `ChangeProposal` ne devient jamais implicitement l'état courant. Le passage de `PROPOSED` à `CURRENT` doit être représentable et explicable.

---

## 9. `DesignDecision`

Représente une décision de conception locale à une évolution ou importée depuis une source telle qu'une ADR.

```text
DesignDecision
- id: DesignDecisionId
- title: string
- context: string?
- decision: string
- rationale: string?
- consequences: List<string>
- lifecycleState: DecisionState
- provenance: Provenance
```

MORPHEUS ne remplace pas le format ADR. Il normalise suffisamment la décision pour la rechercher, la relier et l'expliquer.

---

## 10. `AcceptanceCriterion`

Représente une condition permettant de juger qu'une exigence ou un changement est satisfait.

```text
AcceptanceCriterion
- id: AcceptanceCriterionId
- scope: EntityRef
- statement: string
- verificationKind: VerificationKind?
- status: VerificationStatus?
- provenance: Provenance
```

Types de vérification candidats :

```text
AUTOMATED_TEST
MANUAL_TEST
STATIC_ANALYSIS
REVIEW
MEASUREMENT
EXTERNAL_VALIDATION
UNSPECIFIED
```

Le statut d'un critère ne doit pas être déduit automatiquement de l'existence d'un test ; une preuve de vérification est nécessaire.

---

## 11. `ImplementationTask`

Représente une unité de travail explicitement associée à une évolution.

```text
ImplementationTask
- id: TaskId
- changeId: ChangeId
- key: TaskKey?
- description: string
- state: TaskState
- order: int?
- dependencies: List<TaskId>
- externalReferences: List<ExternalReference>
- provenance: Provenance
```

MORPHEUS ne doit pas devenir un gestionnaire général de tickets. Les tâches existent pour relier l'intention à l'implémentation et à la vérification.

---

## 12. `SpecificationVersion`

Représente un état cohérent et identifiable du modèle de spécification.

```text
SpecificationVersion
- id: SpecificationVersionId
- projectId: ProjectSpecificationId
- sequence: long?
- providerVersion: string?
- sourceRevision: string?
- createdAt: Instant
- predecessor: SpecificationVersionId?
- snapshotKind: SnapshotKind
```

`sourceRevision` peut référencer un commit Git ou une révision externe, mais ne constitue pas l'identité métier de la version.

---

## 13. `TraceabilityLink`

Concept de premier ordre représentant une relation explicite ou dérivée entre deux entités.

```text
TraceabilityLink
- id: TraceabilityLinkId
- source: EntityRef
- type: TraceabilityRelationType
- target: EntityRef
- resolution: ResolutionState
- origin: LinkOrigin
- confidence: Confidence?
- evidence: List<EvidenceRef>
- validFromVersion: SpecificationVersionId?
- validToVersion: SpecificationVersionId?
```

Relations candidates :

```text
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
DEPENDS_ON
AFFECTS
DECIDED_BY
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

Toute relation heuristique doit rester identifiable comme telle.

---

## 14. `ExternalReference`

Représente un lien vers un système que MORPHEUS ne possède pas.

```text
ExternalReference
- system: string
- resourceType: string
- externalId: string
- locator: string?
- version: string?
- metadata: Map<String, Value>
```

Exemples :

```text
system = MINOS, resourceType = SYMBOL
system = GITHUB, resourceType = ISSUE
system = GIT, resourceType = COMMIT
system = JIRA, resourceType = ISSUE
```

Le domaine ne doit pas contenir de type Java/Go/TypeScript spécifique à MINOS, GitHub ou Jira.

---

## 15. `Evidence`

Une preuve indique sur quoi repose une information ou une relation.

```text
Evidence
- id: EvidenceId
- source: SourceReference
- locator: SourceLocator?
- excerptHash: string?
- observedAt: Instant?
- providerId: string?
- providerVersion: string?
- interpretation: string?
```

Une preuve n'est pas nécessairement le contenu complet de la source. MORPHEUS doit pouvoir conserver un pointeur stable et suffisamment d'information pour expliquer le résultat.

---

## 16. `Provenance`

```text
Provenance
- providerId: string
- providerVersion: string?
- source: SourceReference
- externalId: string?
- sourceRevision: string?
- importedAt: Instant
```

La provenance répond à :

> D'où cette information vient-elle et dans quel état de la source a-t-elle été observée ?

---

## 17. États orthogonaux

MORPHEUS doit éviter un unique enum mélangeant plusieurs dimensions.

### 17.1 État temporel

```text
CURRENT
PROPOSED
HISTORICAL
```

### 17.2 Cycle de vie d'un changement

Candidat :

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

### 17.3 Résolution

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
HEURISTIC
```

### 17.4 Vérification

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Ces dimensions ne doivent pas être déduites les unes des autres sans règle explicite.

---

## 18. Identité logique

Chaque entité majeure possède une identité MORPHEUS opaque et stable dans les limites définies par la stratégie d'identité.

Le domaine distingue :

```text
identity     = qui est l'entité ?
version      = quel état de cette entité ?
locator      = où l'observer dans la source ?
externalId   = comment le provider la nomme ?
```

Conséquence : un renommage de fichier ou déplacement de section ne doit pas nécessairement créer une nouvelle entité.

Les règles détaillées sont portées par l'ADR dédiée à l'identité stable.

---

## 19. Agrégats candidats

Aucune décision d'implémentation DDD n'est encore engagée, mais les frontières suivantes sont à évaluer :

```text
ProjectSpecification
  ├── versions
  └── source registrations

Specification
  ├── requirements
  ├── constraints
  ├── scenarios
  └── acceptance criteria

ChangeProposal
  ├── deltas
  ├── decisions
  ├── tasks
  └── acceptance criteria
```

`TraceabilityLink` peut nécessiter un stockage transversal indépendant des agrégats pour permettre les traversées efficaces.

---

## 20. Invariants transversaux

1. Aucun identifiant provider n'est l'identité publique principale de MORPHEUS.
2. Aucun chemin de fichier n'est une identité logique suffisante.
3. Toute donnée importée possède une provenance.
4. Toute relation dérivée expose son origine et sa résolution.
5. Un élément `PROPOSED` n'est jamais présenté comme `CURRENT` sans transition explicite.
6. Une référence externe non résolue reste représentable.
7. Une suppression source ne doit pas effacer silencieusement l'historique nécessaire à l'explicabilité.
8. Le domaine doit rester sérialisable sans classes d'un provider externe.

---

## 21. Questions encore ouvertes pour C0 / M0

- algorithme exact d'identité stable ;
- politique de fusion lorsque plusieurs providers décrivent la même entité ;
- granularité exacte des versions ;
- taxonomie finale des relations ;
- règles de transition du cycle de vie ;
- représentation des deltas de changement ;
- niveau de stockage des preuves ;
- stratégie de résolution des références externes ;
- stratégie d'historisation et de rétention.

Ces points doivent être tranchés par ADR et, lorsque nécessaire, par expérimentation M0.