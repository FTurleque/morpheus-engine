# M8 — Plan d'exécution détaillé

Statut : **M8 VALIDÉ — PR #54 prête à intégration**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M7 validés et intégrés
main de départ = 34eacb3728cf4f43fb8f596b159c96e2cb662781
M7 final gate  = 282/282 PASS
```

Issue : **#53 — M8 — Analyse des changements**  
PR : **#54 — M8 — Analyse déterministe des changements**  
Branche : `m8/change-analysis`

Head exécutable testé :

```text
2fad890f3db956b548f4c96643b955e6b9971c36
```

## Question de sortie

> **MORPHEUS peut-il analyser de façon déterministe l'étendue fonctionnelle et documentaire d'un changement en confrontant la baseline CURRENT au contenu proposé, classifier les exigences ajoutées/modifiées/supprimées, exposer scénarios, contraintes, décisions et tâches associées, puis expliquer les dépendances et impacts transitifs par des chemins de traçabilité bornés, sans analyser le code ni inventer de relations ou de critères d'acceptation ?**

**Réponse : OUI.**

## M8-S1 — CURRENT / proposé

Contrats :

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

Règles :

```text
analyzeActive   -> ACTIVE
analyzeSnapshot -> ACTIVE ou RETIRED
READY/BUILDING/VALIDATING/FAILED -> rejet
cross-project -> rejet
analysis != promotion
published snapshot = read-only
```

Impacts :

```text
ADDED    -> PRESENCE
MODIFIED -> SPECIFICATION / KEY / TITLE / STATEMENT / SCENARIOS
REMOVED  -> PRESENCE
```

Les incohérences restent visibles par warnings structurés ; aucune baseline absente n'est inventée.

## M8-S2 — Scope et acceptance gap

Le résultat expose les `Constraint`, `DesignDecision` et `ImplementationTask` explicitement rattachés au changement.

Invariant :

```text
Scenario != AcceptanceCriterion
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Aucun critère d'acceptation synthétique n'est créé.

## M8-S3 — Dépendances et chemins

```text
relation = DEPENDS_ON uniquement
OUTGOING -> DEPENDENCY
INCOMING -> DEPENDENT
maxDepth > 0
shortest bounded path
persisted links only
```

Un chemin non `RESOLVED` reste visible et produit `TRACEABILITY_PATH_PARTIALLY_RESOLVED`. Un requirement uniquement proposé n'est pas injecté artificiellement dans le graphe publié.

## M8-S4 — Vue compacte

```text
CompactChangeAnalysisView
CompactChangeAnalysisViewService
schemaVersion = 1
operation = analyze_change
CanonicalJsonSerializer réutilisé
```

La vue expose baseline, changement, résumé, impacts requirement, scénarios CURRENT/proposés, contraintes, décisions, tâches, dependency paths, acceptance status et warnings structurés.

## M8-S5 — Preuves

`ChangeAnalysisContractTest` : **7/7 PASS**.

Cas prouvés :

1. CURRENT / proposé sans mutation ;
2. ADDED / MODIFIED / REMOVED ;
3. TITLE / STATEMENT / SCENARIOS ;
4. proposed-only sans trace inventée ;
5. dépendances directes/transitives ;
6. dépendants entrants ;
7. profondeur bornée ;
8. `DEPENDS_ON` uniquement ;
9. chemin non résolu explicite ;
10. résumé déterministe ;
11. incohérences de delta explicites ;
12. ACTIVE / RETIRED / READY ;
13. cross-project rejeté ;
14. Memory == SQLite ;
15. SQLite reopen ;
16. vue compacte stable ;
17. JSON/UTF-8 byte-déterministes ;
18. acceptance coverage explicitement indisponible.

## ADR M8

```text
ADR-0056 — Acceptée — CURRENT / proposé déterministe
ADR-0057 — Acceptée — DEPENDS_ON par chemins bornés
ADR-0058 — Acceptée — vue compacte + JSON canonique
```

## Gate final officiel

Exécuté localement sous Windows :

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

Memory Store et CLI n'ont actuellement aucun test propre ; leurs modules passent le reactor sans erreur.

Warnings connus non bloquants : Xerial SQLite native-access et SLF4J NOP.

## Audit post-gate

Après le head exécutable testé `2fad890f...`, les commits ajoutés pour la clôture sont exclusivement documentaires (acceptation ADR, validation et état M8). Aucun artefact exécutable n'est modifié après le gate.

## Invariants finaux

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

## Clôture

M8 satisfait sa question de sortie et est **VALIDÉ**. L'intégration est portée par la PR #54 ; après merge, M9 — CLI stabilisée et distribution locale — devient le jalon actif.