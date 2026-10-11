# ADR-0042 — Porte finale `trace(requirement)`

- Statut : **Acceptée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0033, ADR-0034, ADR-0037, ADR-0038, ADR-0040, ADR-0041
- Portée : M4-S6, validation finale de la traçabilité

## Contexte

M4-S1 à S5 sont validés et intégrés. S5 est intégré sur :

```text
e25aebf0479dfa9d1f146df4d2af0f072b551d39
```

Baseline avant S6 :

```text
184/184 PASS
```

M4 doit exposer une porte applicative unique permettant de partir d'une exigence stable, de sélectionner un snapshot publié explicite et de produire un sous-graphe borné, déterministe et explicable incluant les références externes résolues, non résolues ou cassées.

## Décision

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

`maxDepth` est validé par la façade elle-même et doit être strictement positif, même lorsqu'aucun ACTIVE ou aucune exigence n'est trouvé.

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

## Preuves S6

`TraceRequirementFinalValidationTest` apporte **5 tests d'intégration** :

1. Memory et SQLite produisent exactement le même résultat final ;
2. `traceActive` isole l'ACTIVE, tandis qu'un RETIRED reste explicitement requêtable et un READY est rejeté ;
3. les filtres relationnels sont respectés et une exigence absente retourne vide ;
4. un close/reopen SQLite conserve exactement le résultat final, y compris `UNRESOLVED` et `BROKEN_REFERENCE` ;
5. les snapshots techniques non publiés sont rejetés.

Le scénario final démontre notamment :

```text
Scenario -> Requirement               REFINES
Change -> Requirement                 AFFECTS
Constraint -> Change                  CONSTRAINS
Change -> DesignDecision              DECIDED_BY
DesignDecision -> Specification       RELATED_TO   (profondeur 3)
DesignDecision -> Change              RELATED_TO   (cycle réel)
Requirement -> ExternalReference      LINKS_TO_CODE / UNRESOLVED
Requirement -> ExternalReference      LINKS_TO_TEST / BROKEN_REFERENCE
```

La preuve couvre aussi :

```text
incoming + outgoing dans la même vue
provenance/evidence conservées
snapshot historique explicite
ACTIVE isolation
Memory == SQLite
close/reopen SQLite
aucun backend graphe
```

## Preuve d'acceptation

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

TraceRequirementFinalValidationTest       5/5 PASS
LayerDependencyTest                       2/2 PASS

Domain                                   21 tests
Application                              66 tests
OpenSpec provider                        26 tests
Synthetic provider                        7 tests
SQLite store                              7 tests
Architecture tests                       62 tests
------------------------------------------------
TOTAL                                   189/189 PASS
Failures                                  0
Errors                                    0
Skipped                                   0
BUILD SUCCESS
Total time                              27.573 s
```

Gate terminé le **23 juillet 2026 à 14:57:23 +02:00**.

Warnings connus et non bloquants uniquement :

```text
Xerial SQLite / JDK native-access
SLF4J NOP dans les tests d'architecture
```

Le head de code effectivement testé est :

```text
d46b66b5c5c22baabcfe8cfcb53a2da2eff68782
```

Conclusion : **ADR-0042 acceptée ; la porte finale `trace(requirement)` satisfait la question de sortie M4.**
