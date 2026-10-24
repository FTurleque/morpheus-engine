---
id: ARCH-0056
title: "Analyse déterministe CURRENT / proposé des changements"
domain: architecture
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/analysis/**/*.java"]
---

# Analyse déterministe CURRENT / proposé des changements

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS possède deux représentations volontairement distinctes :

```text
snapshot publié -> RequirementVersionRecord CURRENT
lecture provider -> RequirementDelta ADDED / MODIFIED / REMOVED
```

Le delta normalisé ne doit ni remplacer silencieusement la baseline ni être promu pour pouvoir être analysé.
MORPHEUS doit expliquer l'étendue d'un changement avant promotion, sans modifier l'état publié et sans confondre un
scénario avec un critère d'acceptation.

## Decision

La couche application porte une analyse dérivée et non persistée, dans `com.morpheus.application.analysis` :

```text
ProposedChangeSet
ChangeAnalysisService
RequirementChangeImpact
ChangeAnalysisResult
ChangeAnalysisSummary
ChangeAnalysisWarning
```

`ProposedChangeSet` est extrait d'un `NormalizedProjectContent` pour un `ChangeId` et contient uniquement les objets
appartenant explicitement au changement : `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision` et
`ImplementationTask`.

L'analyse confronte ce contenu à un snapshot publié :

```text
ACTIVE  -> autorisé
RETIRED -> autorisé explicitement
BUILDING / VALIDATING / READY / FAILED -> rejeté
```

`analyzeActive` retourne l'absence de snapshot ACTIVE comme une absence de résultat ; elle n'invente pas une baseline
vide. Une analyse qui traverse deux projets est rejetée.

### Comparaison des requirements

Chaque `RequirementDelta` produit exactement un `RequirementChangeImpact`.

**ADDED**

```text
baseline absente -> PRESENCE modifiée
baseline présente -> ADDED_REQUIREMENT_ALREADY_CURRENT
```

Un requirement uniquement proposé ne possède pas encore de nœud publié sur lequel démontrer une traçabilité
snapshot-scoped : l'analyse expose alors `PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE`. Aucun lien n'est
synthétisé.

**MODIFIED**

Si la baseline CURRENT existe, les dimensions normalisées suivantes sont comparées :

```text
SPECIFICATION
KEY
TITLE
STATEMENT
SCENARIOS
```

Les scénarios sont comparés par leur contenu comportemental normalisé : titre, préconditions, action et résultat
attendu. Leur identité d'occurrence et leur provenance ne transforment pas à elles seules un scénario en changement
fonctionnel.

Sans baseline CURRENT : `MODIFIED_REQUIREMENT_BASELINE_MISSING`.
Sans changement documentaire observable : `MODIFIED_WITHOUT_DOCUMENTARY_CHANGE`.

**REMOVED**

```text
baseline présente -> PRESENCE modifiée
baseline absente  -> REMOVED_REQUIREMENT_BASELINE_MISSING
```

Le contenu supprimé reste celui de la baseline ; aucun statement absent n'est reconstruit.

### Contraintes, décisions et tâches

Les `Constraint`, `DesignDecision` et `ImplementationTask` du `ProposedChangeSet` sont exposés comme faits explicites
appartenant au changement. L'analyse ne crée pas de relation supplémentaire entre ces objets et les requirements.

### Critères d'acceptation

ADR-0124 reste normative :

```text
Scenario != AcceptanceCriterion
```

Le modèle normalisé porte des `AcceptanceCriterion` (ADR-0081), mais le `ProposedChangeSet` n'en extrait aucun et
l'analyse de changement n'évalue pas la couverture d'acceptance. Chaque `ChangeAnalysisResult` porte :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE
```

Ce statut signifie que l'analyse de changement ne démontre pas cette dimension ; il ne signifie pas que la source ne
possède aucun critère. L'évaluation des critères d'acceptation relève d'ADR-0081, pas de cette analyse.

### Déterminisme

- deltas canoniquement ordonnés ;
- scénarios CURRENT et proposés ordonnés pour l'exposition ;
- comparaison des scénarios indépendante de l'ordre source ;
- warnings structurés et ordonnés ;
- aucune lecture d'horloge ;
- aucun LLM, embedding, fuzzy matching ou inférence sémantique.

### Persistance

L'analyse n'introduit aucune table, migration ou projection persistée.

```text
analysis = derived view
analysis != published state
analysis != promotion
```

La baseline est lue par les ports existants, implémentés par le store mémoire et le store SQLite.

### Frontières

L'analyse ne fait pas : promotion de `RequirementDelta`, mutation d'un snapshot, conversion
`Scenario -> AcceptanceCriterion`, analyse de code, matching sémantique, invention de relations de trace ou persistance
du résultat d'analyse. L'analyse de code reste la responsabilité de MINOS.

## Do's and Don'ts

### Do

- Produire exactement un `RequirementChangeImpact` par `RequirementDelta`.
- N'analyser que contre un snapshot `ACTIVE` ou `RETIRED`, et rejeter tout autre état.
- Rendre l'absence de snapshot ACTIVE comme une absence de résultat.
- Signaler par un warning nommé chaque incohérence de delta (baseline absente, requirement déjà courant, modification
  sans changement documentaire).
- Comparer les scénarios par leur contenu comportemental normalisé, indépendamment de l'ordre source.
- Porter `UNAVAILABLE_IN_NORMALIZED_MODEL` et le warning `ACCEPTANCE_CRITERIA_UNAVAILABLE` dans chaque résultat.

### Don't

- Ne pas promouvoir un delta ni muter un snapshot pour l'analyser.
- Ne pas inventer une baseline vide, un statement supprimé ni un lien de traçabilité.
- Ne pas convertir un scénario en critère d'acceptation.
- Ne pas persister le résultat d'une analyse.
- Ne pas lire l'horloge ni recourir à un LLM, un embedding ou un matching flou.
- Ne pas analyser à travers deux projets.

## Consequences

### Positives

- L'étendue d'un changement est expliquée avant promotion, sans toucher à l'état publié.
- Les incohérences de delta restent visibles au lieu d'être corrigées en silence.
- Le résultat est identique quel que soit le store de baseline.

### Négatives

- La couverture d'acceptance d'un changement proposé n'est pas évaluée par cette analyse.
- L'analyse est recalculée à chaque demande, faute de projection persistée.

### Risques

- **Lecture de `UNAVAILABLE_IN_NORMALIZED_MODEL` comme « aucun critère »** : mitigé par le warning qui l'accompagne et
  par la définition du statut ci-dessus.
- **Faux changement fonctionnel dû à l'ordre ou à la provenance des scénarios** : mitigé par la comparaison sur le seul
  contenu comportemental normalisé.

## Compliance and Enforcement

- Règle Archgate `ARCH-0056-deterministic-current-proposed-change-analysis.rules.ts` : `change-analysis-reads-no-clock`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeAnalysisContractTest.java` :
  comparaison sans mutation de la baseline et statut `UNAVAILABLE_IN_NORMALIZED_MODEL`
  (`comparesCurrentAndProposedWithoutMutatingPublishedBaseline`), incohérences explicites
  (`inconsistentDeltaSemanticsRemainExplicitInsteadOfBeingInventedAway`), états admis et refus inter-projets
  (`activeAndRetiredAreAllowedWhileReadyAndCrossProjectAreRejected`), parité mémoire / SQLite
  (`memoryAndSqliteProduceExactlySameAnalysis`) et déterminisme après réouverture SQLite
  (`sqliteReopenAndCompactJsonRemainByteDeterministic`).
- Revue : absence de persistance du résultat, absence de lecture d'horloge et d'inférence sémantique.

## References

- [ADR-0006](ARCH-0006-current-vs-proposed-state.md) — distinction état courant / changements proposés.
- [ADR-0034](DATA-0034-versioned-requirement-persistence.md) — persistance versionnée des requirements.
- [ADR-0044](DATA-0044-snapshot-business-content-projection.md) — projection du contenu métier d'un snapshot.
- [ADR-0057](ARCH-0057-explicit-bounded-dependency-impact-paths.md) — chemins d'impact de dépendance bornés.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation de première classe.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé et deltas.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vue compacte de l'analyse et JSON canonique.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — `Scenario != AcceptanceCriterion`.
- [VALIDATION_M8](../../project/validation/VALIDATION_M8.md) — preuve du jalon M8.
