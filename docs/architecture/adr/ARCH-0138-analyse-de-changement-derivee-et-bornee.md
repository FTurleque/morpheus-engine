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

Les types que porte `com.morpheus.application.analysis` :

```text
ProposedChangeSet
ChangeAnalysisService
RequirementChangeImpact
ChangeDependencyImpact
ChangeAnalysisResult
ChangeAnalysisSummary
ChangeAnalysisWarning
```

`ProposedChangeSet` est extrait d'un `NormalizedProjectContent` et ne contient que les objets appartenant
explicitement au changement : `ChangeProposal`, `RequirementDelta`, `Constraint`, `DesignDecision` et
`ImplementationTask`. La baseline est lue par les ports existants, implémentés par le store mémoire et le store
SQLite ; l'analyse n'introduit aucune table, migration ou projection persistée :

```text
analysis = derived view
analysis != published state
analysis != promotion
```

Les états de snapshot :

```text
ACTIVE  -> autorisé
RETIRED -> autorisé explicitement
BUILDING / VALIDATING / READY / FAILED -> rejeté
```

La comparaison des requirements, par type de delta :

```text
ADDED    baseline absente  -> PRESENCE modifiée
ADDED    baseline présente -> ADDED_REQUIREMENT_ALREADY_CURRENT
MODIFIED dimensions        -> SPECIFICATION, KEY, TITLE, STATEMENT, SCENARIOS
REMOVED  baseline présente -> PRESENCE modifiée
REMOVED  baseline absente  -> REMOVED_REQUIREMENT_BASELINE_MISSING
```

Un requirement uniquement proposé ne possède pas encore de nœud publié sur lequel démontrer une traçabilité limitée
au snapshot : l'analyse n'essaie pas de faire correspondre son texte à un nœud existant, aucun lien n'est synthétisé,
et il n'a pas d'impact de dépendance. Les scénarios sont comparés par leur contenu comportemental normalisé : titre,
préconditions, action et résultat attendu ; leur identité d'occurrence et leur provenance ne transforment pas à elles
seules un scénario en changement fonctionnel. `MODIFIED_REQUIREMENT_BASELINE_MISSING` signale l'absence de baseline
`CURRENT`, `MODIFIED_WITHOUT_DOCUMENTARY_CHANGE` l'absence de changement documentaire observable. Le contenu supprimé
reste celui de la baseline : aucun statement absent n'est reconstruit.

Le modèle normalisé porte des `AcceptanceCriterion` (ADR-0081), mais le `ProposedChangeSet` n'en extrait aucun et
l'analyse de changement n'évalue pas la couverture d'acceptance ; chaque `ChangeAnalysisResult` porte donc :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE
```

Ce statut signifie que l'analyse de changement ne démontre pas cette dimension ; il ne signifie pas que la source ne
possède aucun critère. L'évaluation des critères d'acceptation relève d'ADR-0081, pas de cette analyse.

Les impacts de dépendance, sur un exemple :

```text
OUTGOING DEPENDS_ON -> DEPENDENCY
INCOMING DEPENDS_ON -> DEPENDENT

A --DEPENDS_ON--> B --DEPENDS_ON--> C
D --DEPENDS_ON--> A

changement direct sur A :
B = DEPENDENCY depth 1
C = DEPENDENCY depth 2
D = DEPENDENT  depth 1
```

Chaque `ChangeDependencyImpact` contient :

```text
originRequirementId
DependencyImpactDirection
impactedEntity
TraceabilityPath
```

Le chemin est obtenu par la traversée BFS déterministe de traçabilité et représente le plus court chemin observé dans
la direction demandée. La relation `DEPENDS_ON` reste typée par le graphe de traçabilité : l'analyse expose le
`TraceabilityEntityRef` réellement atteint. `maxDepth = 1` limite aux voisins directs, `maxDepth = 2` à deux
relations `DEPENDS_ON` ; il n'existe ni profondeur implicite, ni profondeur illimitée. Les budgets de nœuds et de
liens sont ceux de `TraceabilityTraversalService` : la troncature est signalée au lieu de présenter la liste des
impacts comme complète. Les liens `PARTIALLY_RESOLVED`, `UNRESOLVED` ou `HEURISTIC` restent visibles et le chemin
qui en contient un est conservé avec l'état de résolution de chaque lien.

Le déterminisme se lit ainsi : deltas canoniquement ordonnés ; scénarios `CURRENT` et proposés ordonnés pour
l'exposition ; warnings structurés et ordonnés ; filtre fixe `DEPENDS_ON`, traversée bornée, ordre canonique des
voisins hérité de la traversée de traçabilité, plus court chemin déterministe ; aucune inférence sémantique.

L'analyse ne fait ni promotion de `RequirementDelta`, ni mutation d'un snapshot, ni conversion
`Scenario -> AcceptanceCriterion`, ni invention de relations de trace. Le partage avec MINOS :

```text
MORPHEUS -> dépendances de spécification/intention explicitement tracées
MINOS    -> structure, symboles, références et impact de code
```

## Decision

1. **Dérivée** : `ChangeAnalysisService` (`com.morpheus.application.analysis`) confronte à la baseline le
   `ProposedChangeSet` d'un `ChangeId`, réduit aux objets du changement. Rien n'est persisté, promu ni
   muté ; contraintes, décisions et tâches sont exposées sans relation ajoutée.
2. **Snapshot** `ACTIVE` ou `RETIRED`, sinon rejet, comme deux projets ; sans `ACTIVE`, `analyzeActive` ne rend
   rien, sans baseline vide.
3. **Requirements**, un `RequirementChangeImpact` par delta : `ADDED` en `PRESENCE` (alors
   `PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE`, sans rapprochement, lien ni impact) ou
   `ADDED_REQUIREMENT_ALREADY_CURRENT` ; `MODIFIED` sur `SPECIFICATION`, `KEY`, `TITLE`, `STATEMENT`, `SCENARIOS`
   (contenu comportemental), ou `MODIFIED_REQUIREMENT_BASELINE_MISSING`, `MODIFIED_WITHOUT_DOCUMENTARY_CHANGE` ;
   `REMOVED` en `PRESENCE` (contenu de la baseline) ou `REMOVED_REQUIREMENT_BASELINE_MISSING`.
4. **Acceptation** : `Scenario != AcceptanceCriterion` (ADR-0124) ; tout résultat porte
   `UNAVAILABLE_IN_NORMALIZED_MODEL` et `ACCEPTANCE_CRITERIA_UNAVAILABLE`.
5. **Dépendances** : seuls les liens persistés `DEPENDS_ON` d'un requirement affecté `CURRENT` (sortants
   `DEPENDENCY`, entrants `DEPENDENT`) ; un impact porte origine, direction, entité atteinte (pas forcément un
   `Requirement`) et le plus court `TraceabilityPath` démontré.
6. **Bornes** : `maxDepth` > 0 obligatoire, budgets d'ADR-0040 ; arrêt anticipé :
   `TRACEABILITY_TRAVERSAL_TRUNCATED` et sa raison.
7. **Partiel** : lien non `RESOLVED` visible, jamais promu ; son chemin émet `TRACEABILITY_PATH_PARTIALLY_RESOLVED`.
8. **Déterminisme** : sorties ordonnées (impacts par origine, direction, cible, profondeur), comparaison
   indépendante de l'ordre source ; ni horloge, LLM, embedding ni matching flou ou sémantique.
9. **Frontière** : ni AST, call graph, SCIP ni symboles (MINOS).

## Do's and Don'ts

### Do

- Produire exactement un `RequirementChangeImpact` par `RequirementDelta`.
- N'analyser que contre un snapshot `ACTIVE` ou `RETIRED` ; sans `ACTIVE`, rendre une absence de résultat.
- Signaler par un warning nommé chaque incohérence de delta (baseline absente, requirement déjà courant, modification
  sans changement documentaire).
- Comparer les scénarios par leur contenu comportemental normalisé, indépendamment de l'ordre source.
- Porter `UNAVAILABLE_IN_NORMALIZED_MODEL` et `ACCEPTANCE_CRITERIA_UNAVAILABLE` dans chaque résultat.
- Ne suivre que les liens persistés `DEPENDS_ON` du snapshot de référence, sous un `maxDepth` strictement positif.
- Donner à chaque impact son origine, sa direction, l'entité atteinte et le plus court chemin qui la démontre, triés
  par origine, direction, cible puis profondeur.
- Émettre `TRACEABILITY_PATH_PARTIALLY_RESOLVED` pour un chemin qui contient un lien non `RESOLVED`, et
  `TRACEABILITY_TRAVERSAL_TRUNCATED` avec sa raison pour une traversée tronquée par un budget.

### Don't

- Ne pas promouvoir un delta, muter un snapshot ni persister le résultat d'une analyse.
- Ne pas inventer une baseline vide, un statement supprimé ni un lien de traçabilité.
- Ne pas convertir un scénario en critère d'acceptation.
- Ne pas lire l'horloge ni recourir à un LLM, un embedding ou un matching flou.
- Ne pas analyser à travers deux projets.
- Ne pas déduire une dépendance d'une autre relation, d'une proximité de graphe ou d'une similarité textuelle.
- Ne pas calculer d'impact pour un requirement `ADDED` absent de la baseline, ni rapprocher son texte d'un nœud.
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
