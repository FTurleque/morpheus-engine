# ADR-0056 — Analyse déterministe CURRENT / proposé des changements

- Statut : **Proposée — M8, preuve Maven en attente**
- Date : 24 juillet 2026
- Dépend de : ADR-0006, ADR-0025, ADR-0034, ADR-0044, ADR-0049
- Portée : M8 — analyse fonctionnelle et documentaire des changements

## Contexte

MORPHEUS possède déjà deux représentations volontairement distinctes :

```text
snapshot publié -> RequirementVersionRecord CURRENT
lecture provider -> RequirementDelta ADDED / MODIFIED / REMOVED
```

Le delta normalisé ne doit ni remplacer silencieusement la baseline ni être promu pour pouvoir être analysé.

M8 doit expliquer l'étendue d'un changement avant promotion, sans modifier l'état publié et sans confondre un scénario avec un critère d'acceptation.

## Décision

Introduire dans la couche application une analyse dérivée et non persistée :

```text
ProposedChangeSet
ChangeAnalysisService
RequirementChangeImpact
ChangeAnalysisResult
ChangeAnalysisSummary
ChangeAnalysisWarning
```

`ProposedChangeSet` est extrait d'un `NormalizedProjectContent` pour un `ChangeId`. Il contient uniquement les objets appartenant explicitement au changement :

```text
ChangeProposal
RequirementDelta
Constraint
DesignDecision
ImplementationTask
```

L'analyse confronte ce contenu à un snapshot publié :

```text
ACTIVE  -> autorisé
RETIRED -> autorisé explicitement
BUILDING / VALIDATING / READY / FAILED -> rejeté
```

`analyzeActive` retourne l'absence de snapshot ACTIVE comme une absence de résultat ; elle n'invente pas une baseline vide.

## Comparaison des requirements

Chaque `RequirementDelta` produit exactement un `RequirementChangeImpact`.

### ADDED

```text
baseline absente -> PRESENCE modifiée
baseline présente -> warning ADDED_REQUIREMENT_ALREADY_CURRENT
```

Un requirement uniquement proposé ne possède pas encore de nœud publié sur lequel démontrer une traçabilité snapshot-scoped. M8 expose alors :

```text
PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE
```

Aucun lien n'est synthétisé.

### MODIFIED

Si la baseline CURRENT existe, les dimensions normalisées suivantes sont comparées :

```text
SPECIFICATION
KEY
TITLE
STATEMENT
SCENARIOS
```

Les scénarios sont comparés par leur contenu comportemental normalisé :

```text
title
preconditions
action
expectedOutcome
```

Leur identité d'occurrence et leur provenance ne transforment pas à elles seules un scénario en changement fonctionnel.

Si aucune baseline CURRENT n'existe :

```text
MODIFIED_REQUIREMENT_BASELINE_MISSING
```

Si le delta `MODIFIED` ne modifie aucun champ documentaire observable :

```text
MODIFIED_WITHOUT_DOCUMENTARY_CHANGE
```

### REMOVED

```text
baseline présente -> PRESENCE modifiée
baseline absente  -> REMOVED_REQUIREMENT_BASELINE_MISSING
```

Le contenu supprimé reste celui de la baseline ; aucun statement absent n'est reconstruit.

## Contraintes, décisions et tâches

Les `Constraint`, `DesignDecision` et `ImplementationTask` du `ProposedChangeSet` sont exposés comme faits explicites appartenant au changement.

M8 ne crée pas de relation supplémentaire entre ces objets et les requirements.

## Critères d'acceptation

ADR-0049 reste normative :

```text
Scenario != AcceptanceCriterion
```

Le modèle production ne contenant toujours pas d'`AcceptanceCriterion`, M8 expose :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ACCEPTANCE_CRITERIA_UNAVAILABLE
```

Ce statut signifie que MORPHEUS ne peut pas démontrer cette dimension à partir du modèle normalisé actuel ; il ne signifie pas que la source ne possède aucun critère.

## Déterminisme

- deltas canoniquement ordonnés ;
- scénarios CURRENT et proposés ordonnés par `ScenarioId` pour l'exposition ;
- comparaison des scénarios indépendante de l'ordre source ;
- warnings structurés et ordonnés ;
- aucun clock read ;
- aucun LLM, embedding, fuzzy matching ou inférence sémantique.

## Persistance

Aucune nouvelle table, migration ou projection persistée n'est introduite.

```text
analysis = derived view
analysis != published state
analysis != promotion
```

La baseline continue d'être lue via les ports existants Memory / SQLite.

## Frontières

M8 ne fait pas :

```text
promotion de RequirementDelta
mutation d'un snapshot
conversion Scenario -> AcceptanceCriterion
analyse de code
matching sémantique
invention de relations de trace
persistance du résultat d'analyse
```

L'analyse de code reste la responsabilité de MINOS.

## Preuve attendue

L'ADR pourra passer à **Acceptée — M8** lorsque `ChangeAnalysisContractTest` et le gate Maven complet démontreront :

1. ADDED / MODIFIED / REMOVED comparés à la baseline CURRENT ;
2. différences TITLE / STATEMENT / SCENARIOS détectées sans mutation ;
3. incohérences de delta exposées par warnings structurés ;
4. ACTIVE / RETIRED autorisés et READY rejeté ;
5. absence ACTIVE distincte d'une analyse vide ;
6. `Scenario != AcceptanceCriterion` préservé ;
7. parité Memory / SQLite ;
8. reopen SQLite identique ;
9. `./mvnw clean test` vert.
