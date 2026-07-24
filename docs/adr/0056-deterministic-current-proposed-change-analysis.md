# ADR-0056 — Analyse déterministe CURRENT / proposé des changements

- Statut : **Acceptée — M8**
- Date : 24 juillet 2026
- Dépend de : ADR-0006, ADR-0025, ADR-0034, ADR-0044, ADR-0049
- Portée : M8 — analyse fonctionnelle et documentaire des changements

## Contexte

MORPHEUS possède deux représentations volontairement distinctes :

```text
snapshot publié -> RequirementVersionRecord CURRENT
lecture provider -> RequirementDelta ADDED / MODIFIED / REMOVED
```

Le delta normalisé ne doit ni remplacer silencieusement la baseline ni être promu pour pouvoir être analysé. M8 doit expliquer l'étendue d'un changement avant promotion, sans modifier l'état publié et sans confondre un scénario avec un critère d'acceptation.

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

`ProposedChangeSet` est extrait d'un `NormalizedProjectContent` pour un `ChangeId` et contient uniquement les objets appartenant explicitement au changement : `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision` et `ImplementationTask`.

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
baseline présente -> ADDED_REQUIREMENT_ALREADY_CURRENT
```

Un requirement uniquement proposé ne possède pas encore de nœud publié sur lequel démontrer une traçabilité snapshot-scoped. M8 expose alors `PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE`. Aucun lien n'est synthétisé.

### MODIFIED

Si la baseline CURRENT existe, les dimensions normalisées suivantes sont comparées :

```text
SPECIFICATION
KEY
TITLE
STATEMENT
SCENARIOS
```

Les scénarios sont comparés par leur contenu comportemental normalisé : titre, préconditions, action et résultat attendu. Leur identité d'occurrence et leur provenance ne transforment pas à elles seules un scénario en changement fonctionnel.

Sans baseline CURRENT : `MODIFIED_REQUIREMENT_BASELINE_MISSING`.
Sans changement documentaire observable : `MODIFIED_WITHOUT_DOCUMENTARY_CHANGE`.

### REMOVED

```text
baseline présente -> PRESENCE modifiée
baseline absente  -> REMOVED_REQUIREMENT_BASELINE_MISSING
```

Le contenu supprimé reste celui de la baseline ; aucun statement absent n'est reconstruit.

## Contraintes, décisions et tâches

Les `Constraint`, `DesignDecision` et `ImplementationTask` du `ProposedChangeSet` sont exposés comme faits explicites appartenant au changement. M8 ne crée pas de relation supplémentaire entre ces objets et les requirements.

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
- scénarios CURRENT et proposés ordonnés pour l'exposition ;
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

M8 ne fait pas : promotion de `RequirementDelta`, mutation d'un snapshot, conversion `Scenario -> AcceptanceCriterion`, analyse de code, matching sémantique, invention de relations de trace ou persistance du résultat d'analyse. L'analyse de code reste la responsabilité de MINOS.

## Preuve d'acceptation — 24 juillet 2026

Gate local Windows exécuté sur `m8/change-analysis` :

```text
.\mvnw.cmd clean test
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
Architecture Tests      146/146 PASS
TOTAL                   289/289 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Total time               26.406 s
Finished 2026-07-24T09:44:51+02:00
```

`ChangeAnalysisContractTest` : **7/7 PASS**.

Les preuves couvrent ADDED/MODIFIED/REMOVED, différences documentaires, non-mutation de baseline, incohérences explicites, ACTIVE/RETIRED/READY, `Scenario != AcceptanceCriterion`, Memory == SQLite et reopen SQLite.

Warnings connus non bloquants uniquement : Xerial SQLite native-access et SLF4J NOP.

**Décision : Acceptée — M8.**