# ADR-0079 — État d'orchestration agrégé non destructif

- Statut : **Acceptée — M14**
- Date : 24 juillet 2026
- Dépend de : ADR-0041, ADR-0047, ADR-0050, ADR-0078
- Portée : M14 — vue machine UC-16

## Contexte

UC-16 exige une réponse compacte pour JARVIS : lifecycle, manques, critères non vérifiés, liens non résolus, contraintes bloquantes et transitions possibles.

Le modèle normalisé n'observe pas encore toutes ces dimensions avec la même précision. Une vue d'orchestration doit distinguer absence réelle et information non modélisée.

## Décision

Créer une agrégation read-only :

```text
snapshot
change
lifecycle
observableFacts
missingArtifacts
unavailableFacts
acceptanceCriteria
applicableConstraints
blockingConstraints
unresolvedLinks
qualityFindings
nextTransitions
persisted=false
```

### Missing artifacts

Un artefact n'est déclaré manquant que lorsque l'absence est déterministe.

### Acceptance criteria

Tant qu'aucune projection explicite n'existe :

```text
status = UNAVAILABLE_IN_NORMALIZED_MODEL
criteria = []
```

`Scenario` ne devient jamais `AcceptanceCriterion`.

### Blocking constraints

Le modèle sait lister les contraintes applicables mais ne qualifie pas aujourd'hui leur caractère bloquant. La vue expose donc :

```text
status = UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED
items = []
```

et liste séparément `applicableConstraints`.

### Unresolved links

Les `ExternalReference` snapshot-scoped appartenant au changement et dont l'état est différent de `RESOLVED` sont exposées sans forcer de résolution live.

## Temporalité

La vue est une observation du snapshot ACTIVE et n'est jamais persistée comme nouvel état métier.

## Preuve d'acceptation

```text
JarvisOrchestrationContractTest 5/5 PASS
MorpheusJarvisOrchestrationApiContractTest 2/2 PASS
MORPHEUS 357/357 PASS
JARVIS client 6/6 PASS
persisted=false conservé
```

Validation : `docs/VALIDATION_M14.md`.

## Critères d'acceptation

1. aucune mutation de snapshot ;
2. `persisted=false` ;
3. missing vs unavailable séparés ;
4. acceptance non inventée ;
5. blocking constraint non inventée ;
6. références non résolues visibles ;
7. sortie JSON stable ;
8. gate M14 vert.
