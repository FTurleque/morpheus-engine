# ADR-0046 — Agrégation déterministe du contexte de changement

- Statut : **Proposée — M5**
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

S4 doit composer ces capacités sans créer une seconde source de vérité ni déplacer vers MORPHEUS les responsabilités de ranking/fusion de NEXUS.

## Décision candidate

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

Aucune migration SQLite S4 n'est prévue.

Memory et SQLite doivent produire la même sémantique observable ; SQLite close/reopen doit conserver le contexte.

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

## Preuves attendues

Le gate S4 doit démontrer :

1. façade `trace_requirement` identique aux sémantiques M4 ;
2. ACTIVE par défaut et ACTIVE/RETIRED explicite ;
3. absence d'ACTIVE distincte de change absent ;
4. `ChangeContextResult` cohérent sur un seul snapshot ;
5. requirements affectés issus uniquement des liens `AFFECTS` directs ;
6. seules les occurrences CURRENT sont résolues ;
7. cible AFFECTS cassée conservée dans les liens bruts ;
8. contraintes/décisions/tâches filtrées par `ChangeId` ;
9. sous-graphe borné, déterministe et cycle-safe ;
10. filtre de trace n'altère pas les faits métier de base ;
11. external unresolved/broken visible ;
12. Memory == SQLite ;
13. SQLite reopen ;
14. aucune migration S4 ;
15. `.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
