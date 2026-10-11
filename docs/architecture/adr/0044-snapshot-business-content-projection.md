# ADR-0044 — Projection métier requêtable snapshot-scoped

- Statut : **Acceptée — M5**
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

`Requirement` possède déjà une persistance versionnée complète via ADR-0034. Les autres familles nécessaires aux primitives M5 restaient disponibles uniquement dans `NormalizedProjectContent` :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
```

Ces familles disposent d'identités métier stables et de provenance mais n'ont pas de `TemporalState` ni d'`EntityVersionId` de production. M5-S2 ne leur invente donc pas une temporalité artificielle.

## Décision

Introduire :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
```

Une projection est une occurrence immuable de contenu normalisé possédée explicitement par :

```text
KnowledgeSnapshotId
SpecificationVersionId
```

Elle contient :

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

Les identités métier existantes restent canoniques :

```text
SpecificationId
ScenarioId
ChangeId
ConstraintId
DesignDecisionId
TaskId
EvidenceId
```

Invariants :

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

Les `Specification` et `ChangeProposal` doivent appartenir au même projet que le snapshot.

## Intégrité de projection

Une projection est complète et immuable par snapshot :

```text
0 ou 1 SnapshotBusinessContent par KnowledgeSnapshotId
```

Même snapshot + même contenu : idempotent.  
Même snapshot + contenu différent : `KnowledgeStoreException`.

Les collections top-level sont ordonnées canoniquement par identité et les identités sont uniques dans chaque famille.

Relations internes validées :

```text
Constraint.changeId -> ChangeProposal
DesignDecision.changeId -> ChangeProposal
ImplementationTask.changeId -> ChangeProposal
```

`Scenario.requirementId` reste une référence typée vers `Requirement`, persisté séparément. Aucun lien alternatif n'est fabriqué.

## Evidence et provenance

Toute entité portant une `Provenance` doit référencer un `EvidenceId` présent dans la projection.

La reconstruction conserve exactement :

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

Migration normalisée, sans payload JSON métier :

```text
snapshot_business_content
snapshot_evidence
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
```

Les listes sont persistées dans des tables enfants avec `ordinal` afin de reconstruire leur ordre exact.

## Backends

Implémentations de référence :

```text
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

L'adapter mémoire compose `SpecificationKnowledgeStore + VersionedRequirementStore` afin de réutiliser les mêmes règles d'ownership sans gonfler le store de fondation.

Les deux adapters exposent :

```text
putSnapshotContent(...)
findSnapshotContent(snapshotId)
```

et doivent produire la même sémantique observable. SQLite doit reconstruire exactement la projection après fermeture/réouverture.

## Frontières

M5-S2 ne fait pas :

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

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur la branche :

```text
m5/snapshot-business-content
head GitHub = 2740b5ae907ba5a33415ba2070cd01b7e3b43154
.\mvnw.cmd clean test
javac release 21
```

Résultat :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      75 tests
-----------------------------------------------
TOTAL                                  202/202 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             16.347 s
Finished at                 2026-07-23T17:52:59+02:00
```

Les 14 critères d'acceptation sont couverts : projection complète Memory/SQLite, ownership snapshot/version, projet, unicité, relations Change, evidence/provenance, idempotence/collision, ordre des listes, `Scenario.requirementId`, V007 sans JSON, reopen SQLite, séparation de `Requirement`, et gate Maven complet vert.

Décision finale :

```text
ADR-0044 = ACCEPTÉE — M5
M5-S2    = VALIDÉ — 202/202
```
