# M8 — Plan d'exécution détaillé

Statut : **IMPLÉMENTATION FONCTIONNELLE COMPLÈTE — GATE MAVEN À EXÉCUTER**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M7 validés et intégrés
main de départ = 34eacb3728cf4f43fb8f596b159c96e2cb662781
M7 final gate  = 282/282 PASS
```

Issue : **#53 — M8 — Analyse des changements**  
Branche : `m8/change-analysis`

## Question de sortie

> **MORPHEUS peut-il analyser de façon déterministe l'étendue fonctionnelle et documentaire d'un changement en confrontant la baseline CURRENT au contenu proposé, classifier les exigences ajoutées/modifiées/supprimées, exposer scénarios, contraintes, décisions et tâches associées, puis expliquer les dépendances et impacts transitifs par des chemins de traçabilité bornés, sans analyser le code ni inventer de relations ou de critères d'acceptation ?**

Réponse technique à confirmer par le gate : **implémentation présente ; validation Maven non encore enregistrée**.

## M8-S1 — Entrée proposée et comparaison CURRENT / proposé

Contrats introduits :

```text
ProposedChangeSet
RequirementChangeField
RequirementChangeImpact
ChangeAnalysisWarningCode
ChangeAnalysisWarning
ChangeAnalysisSummary
ChangeAnalysisResult
ChangeAnalysisService
```

### Entrée

`ProposedChangeSet` sélectionne, pour un `ChangeId` :

```text
ChangeProposal
RequirementDelta*
Constraint*
DesignDecision*
ImplementationTask*
```

à partir d'un `NormalizedProjectContent` ou par construction typée directe.

Invariant :

```text
une seule occurrence de RequirementDelta par RequirementId dans un ProposedChangeSet
chaque objet appartient au même ChangeId
```

### Baseline

```text
analyzeActive  -> snapshot ACTIVE du projet
analyzeSnapshot -> ACTIVE ou RETIRED explicite
READY/BUILDING/VALIDATING/FAILED -> rejet
cross-project -> rejet
```

L'analyse ne promeut pas le contenu proposé et ne modifie jamais le snapshot publié.

### ADDED / MODIFIED / REMOVED

```text
ADDED    -> PRESENCE ; baseline déjà présente => warning
MODIFIED -> compare SPECIFICATION / KEY / TITLE / STATEMENT / SCENARIOS
REMOVED  -> PRESENCE ; baseline absente => warning
```

Les scénarios sont comparés sémantiquement sur leurs champs comportementaux normalisés et exposés séparément en CURRENT et proposé.

## M8-S2 — Scope du changement et acceptance gap

Le résultat expose directement les faits normalisés du changement :

```text
constraints
designDecisions
implementationTasks
```

Aucune relation supplémentaire n'est créée.

Invariant M6 conservé :

```text
Scenario != AcceptanceCriterion
```

Statut exposé :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE
```

## M8-S3 — Dépendances et chemins explicatifs

Contrats :

```text
DependencyImpactDirection
ChangeDependencyImpact
```

Expansion autorisée :

```text
relation = DEPENDS_ON uniquement
OUTGOING -> DEPENDENCY
INCOMING -> DEPENDENT
maxDepth > 0
```

Chaque impact conserve un `TraceabilityPath` obtenu par le service M4 existant.

```text
shortest bounded path
persisted links only
no invented edge
```

Un chemin contenant un lien non `RESOLVED` reste visible et produit :

```text
TRACEABILITY_PATH_PARTIALLY_RESOLVED
```

Un requirement uniquement proposé n'est pas projeté artificiellement dans le graphe publié :

```text
PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE
```

## M8-S4 — Vue compacte et JSON canonique

Contrats :

```text
CompactChangeAnalysisView
CompactChangeAnalysisViewService
```

Métadonnées :

```text
schemaVersion = 1
operation = analyze_change
```

Le service réutilise `CanonicalJsonSerializer` et les DTOs compacts M5 existants.

La vue expose :

```text
baseline snapshot
change
summary
requirement impacts
CURRENT/proposed scenarios
constraints
decisions
tasks
dependency paths
acceptance status
structured warnings
```

Pour un chemin entrant, `PathStepView.from/into` conserve le sens de traversée explicatif tandis que `TraceLinkView` conserve le sens métier réellement persisté.

## M8-S5 — Preuves contractuelles implémentées

Test ajouté :

```text
ChangeAnalysisContractTest
```

Cas déclarés :

1. comparaison CURRENT / proposé sans mutation de baseline ;
2. ADDED / MODIFIED / REMOVED ;
3. TITLE / STATEMENT / SCENARIOS modifiés ;
4. requirement uniquement proposé sans trace inventée ;
5. dépendances directes et transitives ;
6. dépendants entrants ;
7. `maxDepth` strict et borné ;
8. chemins `DEPENDS_ON` uniquement ;
9. warning pour chemin non résolu ;
10. résumé agrégé déterministe ;
11. incohérences de delta explicites ;
12. ACTIVE / RETIRED / READY ;
13. cross-project rejeté ;
14. Memory == SQLite ;
15. SQLite reopen ;
16. vue compacte stable ;
17. JSON et UTF-8 byte-déterministes ;
18. acceptance coverage explicitement indisponible.

## ADR M8

```text
ADR-0056 — CURRENT / proposé déterministe
ADR-0057 — impacts DEPENDS_ON par chemins bornés
ADR-0058 — vue compacte + JSON canonique
```

Statut actuel : **Proposées — preuve Maven en attente**.

Elles ne doivent être acceptées qu'après gate reproductible.

## Hors périmètre

```text
analyse AST
call graph
symbol references
SCIP
impact de code
fuzzy matching
semantic matching
LLM
promotion des deltas
mutation du snapshot
nouvelle persistance
nouvelle migration SQLite
nouveau type AcceptanceCriterion
conversion Scenario -> AcceptanceCriterion
CLI M9
MCP M10
API M11
```

L'impact de code appartient à **MINOS**.

## Invariants M8

```text
analysis = derived view
CURRENT != proposed
no promotion during analysis
published snapshot is read-only
RequirementDeltaKind != TemporalState
Scenario != AcceptanceCriterion
absence of baseline != invented baseline
absence of trace != invented trace
DEPENDS_ON persisted only
bounded deterministic traversal
non-resolved path remains explicit
proposed-only trace gap remains explicit
Memory == SQLite
SQLite reopen
canonical JSON
code impact = MINOS
```

## Gate obligatoire restant

Le connecteur GitHub utilisé pour cette implémentation ne fournit pas de runtime Maven du dépôt et l'environnement d'exécution de cette session n'a ni accès réseau à GitHub ni `gh` installé. Aucun résultat de test n'est donc fabriqué.

Commande officielle à exécuter sur le checkout Windows :

```text
N:\workspace-dev\morpheus-engine
.\mvnw.cmd clean test
```

Équivalent Unix :

```text
./mvnw clean test
```

À la réception d'un gate vert, la clôture M8 doit :

```text
1. enregistrer les comptes de tests exacts
2. passer ADR-0056/57/58 à Acceptée — M8
3. créer VALIDATION_M8.md
4. mettre à jour docs/ROADMAP.md
5. mettre à jour l'issue #53
6. rendre la PR prête à review
7. fusionner seulement après autorisation explicite
```
