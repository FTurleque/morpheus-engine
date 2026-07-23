# ADR-0046 — Agrégation déterministe du contexte de changement

- Statut : **Acceptée — M5**
- Date : 23 juillet 2026
- Dépend de : ADR-0034, ADR-0038, ADR-0040, ADR-0041, ADR-0042, ADR-0044, ADR-0045
- Portée : M5-S4, `trace_requirement` query view et `get_change_context`

## Contexte

M5-S1 à S3 sont intégrés :

```text
M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3 — 196/196
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c — 202/202
M5-S3 merge = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73 — 210/210
```

M4 fournit déjà une traçabilité snapshot-scoped bornée et `TraceRequirementService`. S2/S3 fournissent les projections métier et leurs getters/lists.

S4 compose ces capacités sans créer une seconde source de vérité ni déplacer vers MORPHEUS les responsabilités de ranking/fusion de NEXUS.

## Décision

Introduire :

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

`TraceRequirementQueryService` est une façade de query M5 qui délègue exactement à `TraceRequirementService` M4 ; aucune nouvelle sémantique de trace n'est créée.

`ChangeContextQueryService` agrège, pour un seul snapshot publié :

```text
ChangeProposal
TraceabilityLink AFFECTS directs vers Requirement
RequirementVersionRecord CURRENT résolus
Constraint
DesignDecision
ImplementationTask
TraceabilitySubgraph borné
ExternalTraceabilityView unresolved/broken compris
```

## Sélection temporelle

Les méthodes actives sélectionnent exclusivement le snapshot `ACTIVE` du projet.

Les variantes explicites acceptent uniquement :

```text
ACTIVE
RETIRED
```

et rejettent `BUILDING`, `VALIDATING`, `READY`, `FAILED`.

L'absence d'ACTIVE reste distincte d'un `ChangeProposal` absent :

```text
Optional.empty()                  = aucun snapshot ACTIVE
ChangeContextResult.change empty  = snapshot publié trouvé, change absent
```

## Requirements affectés

Les `RequirementDelta` ne sont pas actuellement persistés comme une collection requêtable. S4 ne prétend donc pas restituer les deltas bruts.

La preuve publiée disponible est la relation déterministe M4 :

```text
Change --AFFECTS--> Requirement
```

Les requirements affectés sont obtenus exclusivement depuis les liens `AFFECTS` **directs sortants** du change dans le même snapshot, puis résolus via :

```text
VersionedRequirementStore.currentRequirement(snapshotId, requirementIdentity)
```

Invariants :

```text
CURRENT only
PROPOSED never returned
no fuzzy inference
no title/key matching
no text inference
```

Les liens `AFFECTS` bruts sont également conservés dans le résultat. Ainsi, une cible sans occurrence CURRENT reste visible et auditable même si elle ne peut pas être résolue dans `affectedRequirements`.

## Contenu métier lié

Les contraintes, décisions et tâches proviennent uniquement de `SnapshotBusinessContent` et sont filtrées par `ChangeId`.

Ordre stable :

```text
RequirementId / DomainIdentity
ConstraintId
DesignDecisionId
TaskId
TraceabilityLinkId
```

S4 ne fabrique aucune relation `ImplementationTask -> Change` dans le graphe lorsque M4 n'en contient pas ; les tâches restent présentes comme contenu métier du contexte.

## Sous-graphe

Le sous-graphe est produit par `TraceabilityTraversalService` :

```text
root = CHANGE(changeId)
direction = BIDIRECTIONAL
maxDepth > 0
relationTypes = filtre explicite appelant
```

Aucune arête transitive synthétique, aucune déduplication sémantique, aucun backend graphe.

Les requirements affectés directs restent calculés via `AFFECTS` même si le filtre du sous-graphe exclut `AFFECTS` : le filtre gouverne la vue de trace, pas les faits métier fondamentaux de l'agrégat.

## Références externes

Pour chaque lien du sous-graphe dont la cible est `EXTERNAL_REFERENCE`, S4 réutilise `ExternalTraceabilityQueryService.inspect(...)`.

Les états suivants restent visibles :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

Aucune résolution externe n'est déclenchée par la query.

## Résultat

`ChangeContextResult` contient :

```text
KnowledgeSnapshotMetadata snapshot
ChangeId changeId
Optional<ChangeProposal> change
List<TraceabilityLink> affectedRequirementLinks
List<RequirementVersionRecord> affectedRequirements
List<Constraint> constraints
List<DesignDecision> designDecisions
List<ImplementationTask> implementationTasks
TraceabilitySubgraph subgraph
List<ExternalTraceabilityView> externalLinks
```

Le résultat est immutable et valide la cohérence snapshot/root/changeId de ses composants.

## Backends

La logique reste entièrement applicative au-dessus des ports existants :

```text
SpecificationKnowledgeStore
SnapshotBusinessContentStore
VersionedRequirementStore
TraceabilityStore
ExternalReferenceStore
```

Aucune migration SQLite S4 n'est introduite.

Memory et SQLite produisent la même sémantique observable ; SQLite close/reopen conserve le contexte.

## Frontières

S4 ne fait pas :

```text
persistance RequirementDelta nouvelle
nouvelle migration SQLite
ranking global
fusion multi-engine
compression par budget de tokens
semantic search / embeddings
LLM
NEXUS
MINOS
DTO JSON final
warnings structurés finaux
```

Les vues compactes, warnings structurés et sérialisation déterministe restent M5-S5.

## Preuve d'acceptation — 23 juillet 2026

Gate local Windows exécuté sur le head complet candidat :

```text
branch = m5/change-context-query
head   = da1c0c53fdcf0e98b60cf7a46699bf014ee67091
.\mvnw.cmd clean test
javac release 21
```

Head code/test contenu dans ce candidat :

```text
6df77f79feeaf92e10b9848c333b1b756c8af33c
```

Preuve ciblée S4 :

```text
ChangeContextQueryContractTest    7/7 PASS
Architecture tests               90/90 PASS
```

Résultat global :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      90 tests
-----------------------------------------------
TOTAL                                  217/217 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             16.688 s
Finished at                 2026-07-23T19:22:37+02:00
```

Les 15 critères de preuve sont satisfaits : façade `trace_requirement` identique à M4, ACTIVE/RETIRED cohérents, distinction no-ACTIVE/not-found, agrégat mono-snapshot, `AFFECTS` directs uniquement, CURRENT only, cible AFFECTS cassée conservée, contenu métier filtré par `ChangeId`, sous-graphe borné/déterministe/cycle-safe, filtre de trace sans perte des faits métier, unresolved/broken externes visibles, Memory == SQLite, SQLite reopen, aucune migration S4 et gate Maven complet vert.

Décision finale :

```text
ADR-0046 = ACCEPTÉE — M5
M5-S4    = VALIDÉ — 217/217
```
