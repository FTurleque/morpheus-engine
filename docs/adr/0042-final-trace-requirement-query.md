# ADR-0042 — Porte finale `trace(requirement)`

- Statut : **Proposée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0037, ADR-0038, ADR-0040, ADR-0041
- Portée : M4-S6, validation finale de la traçabilité

## Contexte

M4-S1 à S5 sont validés ; S5 est intégré sur :

```text
e25aebf0479dfa9d1f146df4d2af0f072b551d39
```

Dernier gate :

```text
184/184 PASS
```

M4 doit maintenant exposer une porte applicative unique qui prouve la question de sortie : partir d'une exigence stable, sélectionner un snapshot publié explicite, puis produire un sous-graphe borné, déterministe et explicable, incluant les références externes résolues, non résolues ou cassées.

## Décision candidate

Ajouter :

```text
TraceRequirementService
TraceRequirementResult
```

API :

```text
traceActive(projectId, requirementId, maxDepth, relationTypes)
traceSnapshot(snapshotId, requirementId, maxDepth, relationTypes)
```

`traceActive` résout uniquement le snapshot `ACTIVE` du projet.

`traceSnapshot` accepte uniquement un snapshot publié :

```text
ACTIVE
RETIRED
```

et rejette explicitement :

```text
BUILDING
VALIDATING
READY
FAILED
```

## Racine

La racine de traversal est toujours :

```text
TraceabilityEntityKind.REQUIREMENT
RequirementId.value
```

Avant de traverser, le service vérifie qu'une occurrence `CURRENT` de cette `DomainIdentity` existe dans le snapshot adressé via `VersionedRequirementStore.currentRequirement`.

Ainsi :

```text
RequirementId != simple endpoint arbitraire
trace(requirement) != traversal(any node)
```

## Direction

La porte finale utilise une traversée :

```text
BIDIRECTIONAL
```

Cela permet de voir simultanément :

```text
Scenario -> Requirement
Change -> Requirement
Requirement -> ExternalReference
```

sans persister d'arête inverse.

## Résultat

`TraceRequirementResult` conserve :

```text
KnowledgeSnapshotMetadata snapshot
RequirementVersionRecord requirement
TraceabilitySubgraph subgraph
List<ExternalTraceabilityView> externalLinks
```

Les vues externes sont produites pour toute arête du sous-graphe dont la cible canonique est `EXTERNAL_REFERENCE`.

Aucune arête n'est modifiée pendant l'enrichissement.

## Déterminisme

Le sous-graphe est celui de S4. Les vues externes sont triées par `TraceabilityLinkId`.

Le résultat ne dépend donc pas de l'ordre d'insertion Memory/SQLite.

## Snapshot et historique

`traceActive(projectId, ...)` :

```text
project -> ACTIVE snapshot -> CURRENT requirement -> traversal
```

`traceSnapshot(snapshotId, ...)` :

```text
ACTIVE/RETIRED snapshot explicite -> CURRENT requirement de ce snapshot -> traversal
```

Un snapshot technique non publié n'est jamais observable via cette porte.

Un snapshot `RETIRED` reste requêtable sans être réactivé.

## Frontières

S6 ne fait pas :

```text
recherche lexicale générale
ranking / compact context
analyse d'impact
CLI publique trace
MCP/API
résolution MINOS de production
nouvelle persistance graphe
nouvelle sémantique de relation
```

## Preuves attendues

Le gate final M4 devra démontrer au minimum :

1. `traceActive` choisit uniquement l'ACTIVE du projet ;
2. `traceSnapshot` accepte ACTIVE/RETIRED et rejette les états non publiés ;
3. Requirement CURRENT obligatoire dans le snapshot adressé ;
4. racine `REQUIREMENT + RequirementId.value` ;
5. traversal BIDIRECTIONAL borné et cycle-safe ;
6. profondeur >= 3 ;
7. incoming + outgoing dans le même résultat ;
8. `Scenario -> Requirement -> ExternalReference` observable ;
9. `Constraint -> Change -> Requirement` observable ;
10. `Change -> DesignDecision` observable ;
11. unresolved externe conservé ;
12. broken reference conservée ;
13. provenance/evidence des arêtes inchangées ;
14. snapshot historique explicite ;
15. ACTIVE isolation ;
16. Memory et SQLite produisent la même vue observable ;
17. close/reopen SQLite conserve le résultat ;
18. aucun backend graphe requis ;
19. création de `docs/VALIDATION_M4.md` ;
20. `.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
