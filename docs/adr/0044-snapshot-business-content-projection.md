# ADR-0044 — Projection métier requêtable snapshot-scoped

- Statut : **Proposée — M5**
- Date : 23 juillet 2026
- Dépend de : ADR-0022, ADR-0030, ADR-0034, ADR-0036, ADR-0043
- Portée : M5-S2, persistance requêtable des familles métier hors `Requirement`

## Contexte

M5-S1 est intégré :

```text
PR #37
merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3
gate = 196/196 PASS
```

`Requirement` possède déjà une persistance versionnée complète via ADR-0034. Les autres familles normalisées nécessaires aux primitives M5 restent uniquement disponibles dans `NormalizedProjectContent` :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
```

ADR-0034 impose que l'extension de persistance conserve l'ownership explicite par snapshot/version. Cependant ces familles n'ont pas encore de `TemporalState` ni d'`EntityVersionId` de production. S2 ne doit donc pas inventer une temporalité artificielle.

## Décision candidate

Introduire :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
```

Une projection `SnapshotBusinessContent` est une occurrence immuable de contenu normalisé possédée par :

```text
KnowledgeSnapshotId
SpecificationVersionId
```

et contient :

```text
Specification[]
Scenario[]
ChangeProposal[]
Constraint[]
DesignDecision[]
ImplementationTask[]
Evidence[]
```

`Requirement` reste exclusivement dans `VersionedRequirementStore`.

## Identité et temporalité

Les identités métier restent les identités MORPHEUS existantes :

```text
SpecificationId
ScenarioId
ChangeId
ConstraintId
DesignDecisionId
TaskId
EvidenceId
```

S2 n'ajoute pas de `EntityVersionId` aux familles qui n'en ont pas encore besoin.

```text
DomainIdentity stable
snapshot occurrence != nouvelle DomainIdentity
snapshot occurrence != EntityVersion inventé
```

La cohérence temporelle de lecture est obtenue par le choix du snapshot publié ; elle n'est pas simulée par un faux `TemporalState`.

## Ownership

Avant tout `putSnapshotContent`, le store exige :

```text
snapshot existe
snapshot -> SpecificationVersion binding existe
binding.specificationVersionId == content.specificationVersionId
```

Les `Specification` et `ChangeProposal` de la projection doivent appartenir au même projet que le snapshot.

## Intégrité de projection

Une projection est complète et immuable par snapshot :

```text
0 ou 1 SnapshotBusinessContent par KnowledgeSnapshotId
```

Même snapshot + même contenu : idempotent.

Même snapshot + contenu différent : `KnowledgeStoreException`.

Les identités sont uniques dans chaque famille.

Relations internes conservées et validées :

```text
Constraint.changeId -> ChangeProposal
DesignDecision.changeId -> ChangeProposal
ImplementationTask.changeId -> ChangeProposal
```

`Scenario.requirementId` reste une référence typée vers la famille `Requirement`, persistée séparément. Le store conserve cette référence sans fabriquer de lien alternatif.

## Evidence et provenance

Toutes les entités qui portent une `Provenance` doivent référencer un `EvidenceId` présent dans la projection.

La reconstruction doit conserver exactement :

```text
providerId
providerVersion?
SourceLocator
externalId?
sourceRevision?
evidenceId
```

et pour `Evidence` :

```text
SourceLocator
SourceRange?
excerptHash?
```

## SQLite V007

Ajouter une migration normalisée, sans payload JSON métier :

```text
snapshot_business_content
snapshot_specifications
snapshot_scenarios
snapshot_scenario_preconditions
snapshot_changes
snapshot_change_scope
snapshot_change_out_of_scope
snapshot_change_risks
snapshot_constraints
snapshot_design_decisions
snapshot_implementation_tasks
snapshot_evidence
```

Les listes sont persistées dans des tables enfants avec `ordinal` afin de reconstruire leur ordre exact.

## Backend

Implémentations de référence :

```text
MemorySpecificationKnowledgeStore
SqliteSnapshotBusinessContentStore
```

Elles doivent exposer la même sémantique observable :

```text
putSnapshotContent(...)
findSnapshotContent(snapshotId)
```

SQLite doit reconstruire exactement la projection après fermeture/réouverture.

## Frontières

S2 ne fait pas :

```text
nouvelle temporalité métier
EntityVersionId artificiel
recherche lexicale supplémentaire
getters publics finaux M5
context aggregation
JSON de persistance
ORM
FTS
MCP / API / CLI
```

## Preuves attendues

Le gate S2 doit démontrer au minimum :

1. projection complète Memory == SQLite ;
2. ownership snapshot / SpecificationVersion obligatoire ;
3. projet incompatible rejeté ;
4. identités dupliquées rejetées ;
5. relations Change -> Constraint/Decision/Task invalides rejetées ;
6. provenance sans evidence correspondante rejetée ;
7. même snapshot/même projection idempotent ;
8. même snapshot/projection différente rejetée ;
9. listes ordonnées reconstruites exactement ;
10. `Scenario.requirementId` conservé ;
11. SQLite V007 normalisée et sans colonne JSON ;
12. SQLite close/reopen conserve la projection exacte ;
13. le store `Requirement` reste séparé ;
14. `\.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
