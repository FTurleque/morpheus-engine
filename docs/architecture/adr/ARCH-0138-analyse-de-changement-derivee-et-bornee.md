---
id: ARCH-0138
title: "Analyse de changement dérivée, déterministe et bornée"
domain: architecture
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/analysis/**/*.java"]
---

# Analyse de changement dérivée, déterministe et bornée

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0056, ADR-0057

## Context

MORPHEUS possède deux représentations volontairement distinctes :

```text
snapshot publié -> RequirementVersionRecord CURRENT
lecture provider -> RequirementDelta ADDED / MODIFIED / REMOVED
```

Le delta normalisé ne doit ni remplacer silencieusement la baseline ni être promu pour pouvoir être analysé.
MORPHEUS doit expliquer l'étendue d'un changement avant promotion, sans modifier l'état publié et sans confondre un
scénario avec un critère d'acceptation.

L'analyse doit aussi expliquer les dépendances d'un requirement directement modifié, sans transformer une proximité
dans le graphe en relation métier inventée. MORPHEUS dispose d'une taxonomie de relations de traçabilité contrôlée et
d'une traversée déterministe limitée à un snapshot publié (ADR-0118, ADR-0038, ADR-0040) ; la seule relation
autorisée pour cette explication est `TraceabilityRelationType.DEPENDS_ON`.

## Decision

### 1. Une analyse dérivée, non persistée

La couche application porte l'analyse dans `com.morpheus.application.analysis` :

```text
ProposedChangeSet
ChangeAnalysisService
RequirementChangeImpact
ChangeDependencyImpact
ChangeAnalysisResult
ChangeAnalysisSummary
ChangeAnalysisWarning
```

`ProposedChangeSet` est extrait d'un `NormalizedProjectContent` pour un `ChangeId` et contient uniquement les objets
appartenant explicitement au changement : `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision` et
`ImplementationTask`.

L'analyse n'introduit aucune table, migration ou projection persistée :

```text
analysis = derived view
analysis != published state
analysis != promotion
```

La baseline est lue par les ports existants, implémentés par le store mémoire et le store SQLite.

### 2. Snapshot de référence

L'analyse confronte le contenu proposé à un snapshot publié :

```text
ACTIVE  -> autorisé
RETIRED -> autorisé explicitement
BUILDING / VALIDATING / READY / FAILED -> rejeté
```

`analyzeActive` rend l'absence de snapshot `ACTIVE` comme une absence de résultat ; elle n'invente pas une baseline
vide. Une analyse qui traverse deux projets est rejetée.

### 3. Comparaison des requirements

Chaque `RequirementDelta` produit exactement un `RequirementChangeImpact`.

**ADDED**

```text
baseline absente -> PRESENCE modifiée
baseline présente -> ADDED_REQUIREMENT_ALREADY_CURRENT
```

Un requirement uniquement proposé ne possède pas encore de nœud publié sur lequel démontrer une traçabilité limitée
au snapshot : l'analyse expose alors `PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE`. Elle n'essaie pas de faire
correspondre son texte à un nœud existant, et aucun lien n'est synthétisé.

**MODIFIED**

Si la baseline `CURRENT` existe, les dimensions normalisées suivantes sont comparées :

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

Sans baseline `CURRENT` : `MODIFIED_REQUIREMENT_BASELINE_MISSING`.
Sans changement documentaire observable : `MODIFIED_WITHOUT_DOCUMENTARY_CHANGE`.

**REMOVED**

```text
baseline présente -> PRESENCE modifiée
baseline absente  -> REMOVED_REQUIREMENT_BASELINE_MISSING
```

Le contenu supprimé reste celui de la baseline ; aucun statement absent n'est reconstruit.

### 4. Contraintes, décisions et tâches

Les `Constraint`, `DesignDecision` et `ImplementationTask` du `ProposedChangeSet` sont exposés comme faits explicites
appartenant au changement. L'analyse ne crée pas de relation supplémentaire entre ces objets et les requirements.

### 5. Critères d'acceptation

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

### 6. Impacts de dépendance par `DEPENDS_ON`

Pour chaque requirement directement affecté par un `RequirementDelta` et possédant une occurrence `CURRENT` dans la
baseline publiée, l'analyse explore uniquement les liens persistés `DEPENDS_ON`, dans deux directions :

```text
OUTGOING DEPENDS_ON -> DEPENDENCY
INCOMING DEPENDS_ON -> DEPENDENT
```

Exemple :

```text
A --DEPENDS_ON--> B --DEPENDS_ON--> C
D --DEPENDS_ON--> A
```

Pour un changement direct sur A :

```text
B = DEPENDENCY depth 1
C = DEPENDENCY depth 2
D = DEPENDENT  depth 1
```

Un `ADDED` absent de la baseline n'a pas d'impact de dépendance (point 3).

Chaque `ChangeDependencyImpact` contient :

```text
originRequirementId
DependencyImpactDirection
impactedEntity
TraceabilityPath
```

Le chemin est obtenu par la traversée BFS déterministe de traçabilité et représente le plus court chemin observé dans
la direction demandée. Aucun chemin n'est créé si le graphe persistant ne permet pas de le démontrer. La relation
`DEPENDS_ON` reste typée par le graphe de traçabilité : l'analyse expose le `TraceabilityEntityRef` réellement
atteint et ne force pas toute cible à être un `Requirement`.

### 7. Profondeur et troncature

`maxDepth` est obligatoire et strictement positif :

```text
maxDepth = 1 -> voisins directs uniquement
maxDepth = 2 -> jusqu'à deux relations DEPENDS_ON
```

Il n'existe ni profondeur implicite, ni profondeur illimitée. La traversée porte en outre les budgets de nœuds et de
liens de `TraceabilityTraversalService` (ADR-0040) : quand elle s'arrête avant d'avoir tout observé dans la profondeur
demandée, l'analyse émet `TRACEABILITY_TRAVERSAL_TRUNCATED` avec la raison de troncature, au lieu de présenter la
liste des impacts comme complète.

### 8. Résolution partielle

Les liens `PARTIALLY_RESOLVED`, `UNRESOLVED` ou `HEURISTIC` restent visibles. Ils ne sont ni supprimés ni promus en
faits certains. Si un chemin contient au moins un lien `resolution != RESOLVED`, l'analyse émet
`TRACEABILITY_PATH_PARTIALLY_RESOLVED` et conserve le chemin avec l'état de résolution de chaque lien.

### 9. Déterminisme

- deltas canoniquement ordonnés ;
- scénarios `CURRENT` et proposés ordonnés pour l'exposition ;
- comparaison des scénarios indépendante de l'ordre source ;
- warnings structurés et ordonnés ;
- filtre fixe `DEPENDS_ON`, traversée bornée, ordre canonique des voisins hérité de la traversée de traçabilité, plus
  court chemin déterministe ;
- impacts de dépendance triés par requirement origine, direction, cible et profondeur ;
- aucune lecture d'horloge ;
- aucun LLM, embedding, fuzzy matching, semantic matching ni inférence sémantique.

### 10. Frontières

L'analyse ne fait pas : promotion de `RequirementDelta`, mutation d'un snapshot, conversion
`Scenario -> AcceptanceCriterion`, matching sémantique, invention de relations de trace ou persistance du résultat
d'analyse. Elle ne parcourt ni AST, ni call graph, ni index SCIP, ni graphe de symboles :

```text
MORPHEUS -> dépendances de spécification/intention explicitement tracées
MINOS    -> structure, symboles, références et impact de code
```

## Do's and Don'ts

### Do

- Produire exactement un `RequirementChangeImpact` par `RequirementDelta`.
- N'analyser que contre un snapshot `ACTIVE` ou `RETIRED`, et rejeter tout autre état.
- Rendre l'absence de snapshot `ACTIVE` comme une absence de résultat.
- Signaler par un warning nommé chaque incohérence de delta (baseline absente, requirement déjà courant, modification
  sans changement documentaire).
- Comparer les scénarios par leur contenu comportemental normalisé, indépendamment de l'ordre source.
- Porter `UNAVAILABLE_IN_NORMALIZED_MODEL` et le warning `ACCEPTANCE_CRITERIA_UNAVAILABLE` dans chaque résultat.
- Ne suivre que les liens persistés `DEPENDS_ON` du snapshot publié de référence.
- Exiger un `maxDepth` strictement positif et le refuser sinon.
- Associer à chaque impact de dépendance son requirement d'origine, sa direction (`DEPENDENCY` ou `DEPENDENT`),
  l'entité atteinte et le plus court chemin qui la démontre.
- Émettre `TRACEABILITY_PATH_PARTIALLY_RESOLVED` dès qu'un chemin contient un lien non `RESOLVED`, en gardant le
  chemin.
- Émettre `TRACEABILITY_TRAVERSAL_TRUNCATED`, avec sa raison, quand la traversée est tronquée par un budget.
- Trier les impacts de dépendance par requirement origine, direction, cible puis profondeur.

### Don't

- Ne pas promouvoir un delta ni muter un snapshot pour l'analyser.
- Ne pas inventer une baseline vide, un statement supprimé ni un lien de traçabilité.
- Ne pas convertir un scénario en critère d'acceptation.
- Ne pas persister le résultat d'une analyse.
- Ne pas lire l'horloge ni recourir à un LLM, un embedding ou un matching flou.
- Ne pas analyser à travers deux projets.
- Ne pas déduire une dépendance d'une autre relation de traçabilité, d'une proximité de graphe ou d'une similarité
  textuelle.
- Ne pas calculer d'impact de dépendance pour un requirement `ADDED` absent de la baseline, ni faire correspondre son
  texte à un nœud existant.
- Ne pas supprimer ni promouvoir en fait certain un lien partiellement résolu, non résolu ou heuristique.
- Ne pas forcer une entité atteinte à être un `Requirement`.
- Ne pas parcourir de structure de code (AST, call graph, symboles) : c'est le domaine de MINOS.

## Consequences

### Positives

- L'étendue d'un changement est expliquée avant promotion, sans toucher à l'état publié.
- Les incohérences de delta restent visibles au lieu d'être corrigées en silence.
- Chaque impact de dépendance est explicable par un chemin persisté et vérifiable.
- Le résultat est identique quel que soit le store de baseline (`store-memory` ou `store-sqlite`).
- La frontière entre intention (MORPHEUS) et code (MINOS) reste nette.

### Négatives

- La couverture d'acceptance d'un changement proposé n'est pas évaluée par cette analyse.
- L'analyse est recalculée à chaque demande, faute de projection persistée.
- Un requirement seulement proposé n'a pas d'impact de dépendance tant qu'il n'est pas publié.
- Une dépendance qui n'a pas été tracée explicitement par `DEPENDS_ON` n'apparaît pas.

### Risques

- **Lecture de `UNAVAILABLE_IN_NORMALIZED_MODEL` comme « aucun critère »** : mitigé par le warning qui l'accompagne et
  par la définition du statut ci-dessus.
- **Faux changement fonctionnel dû à l'ordre ou à la provenance des scénarios** : mitigé par la comparaison sur le seul
  contenu comportemental normalisé.
- **Graphe très connecté** : il atteint les budgets de traversée ; la liste d'impacts est alors partielle, et seul
  l'avertissement de troncature le signale à l'appelant.

## Compliance and Enforcement

- Règle Archgate `ARCH-0138-analyse-de-changement-derivee-et-bornee.rules.ts` : `change-analysis-reads-no-clock`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeAnalysisContractTest.java` :
  comparaison sans mutation de la baseline et statut `UNAVAILABLE_IN_NORMALIZED_MODEL`
  (`comparesCurrentAndProposedWithoutMutatingPublishedBaseline`), incohérences explicites
  (`inconsistentDeltaSemanticsRemainExplicitInsteadOfBeingInventedAway`), états admis et refus inter-projets
  (`activeAndRetiredAreAllowedWhileReadyAndCrossProjectAreRejected`), dépendances directes et transitives,
  dépendants entrants, coupure à `maxDepth`, chemins uniquement `DEPENDS_ON`, avertissement de résolution partielle,
  refus de `maxDepth = 0` (`expandsOnlyExplicitDependsOnAndKeepsShortestBoundedExplanationPaths`), parité mémoire /
  SQLite (`memoryAndSqliteProduceExactlySameAnalysis`) et déterminisme après réouverture SQLite
  (`sqliteReopenAndCompactJsonRemainByteDeterministic`).
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java` : restitution de
  `TRACEABILITY_TRAVERSAL_TRUNCATED` par la CLI.
- Revue : absence de persistance du résultat, absence d'inférence sémantique, absence de parcours de structure de
  code, et refus de faire correspondre un requirement `ADDED` à un nœud existant.

## References

- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — distinction état courant / changements proposés.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance de traçabilité par snapshot.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée et déterministe.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — projection du contenu métier d'un snapshot.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation de première classe.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — budgets de traversée et raison de troncature.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — taxonomie de traçabilité contrôlée.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé et deltas.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vue compacte de l'analyse et JSON canonique.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — `Scenario != AcceptanceCriterion`.
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — politique de snapshot des lectures métier.
- Preuve : [VALIDATION_M8](../../project/validation/VALIDATION_M8.md).
