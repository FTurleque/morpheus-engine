# Validation M8 — Analyse des changements

Statut : **VALIDÉ — intégration portée par PR #54**

Date : 24 juillet 2026

## Baseline

```text
C0 à M7 validés et intégrés
main de départ = 34eacb3728cf4f43fb8f596b159c96e2cb662781
M7 gate        = 282/282 PASS
```

Head exécutable M8 testé :

```text
2fad890f3db956b548f4c96643b955e6b9971c36
```

## Question de sortie

> MORPHEUS peut-il analyser de façon déterministe l'étendue fonctionnelle et documentaire d'un changement en confrontant la baseline CURRENT au contenu proposé, classifier les exigences ajoutées/modifiées/supprimées, exposer scénarios, contraintes, décisions et tâches associées, puis expliquer les dépendances et impacts transitifs par des chemins de traçabilité bornés, sans analyser le code ni inventer de relations ou de critères d'acceptation ?

**Réponse : OUI.**

## Contrats validés

```text
ProposedChangeSet
RequirementChangeField
RequirementChangeImpact
ChangeAnalysisWarningCode
ChangeAnalysisWarning
ChangeAnalysisSummary
ChangeAnalysisResult
ChangeAnalysisService
DependencyImpactDirection
ChangeDependencyImpact
CompactChangeAnalysisView
CompactChangeAnalysisViewService
```

## Comportements validés

- comparaison CURRENT / proposé sans promotion ni mutation ;
- classification ADDED / MODIFIED / REMOVED ;
- différences SPECIFICATION / KEY / TITLE / STATEMENT / SCENARIOS ;
- incohérences exprimées par warnings structurés ;
- `Scenario != AcceptanceCriterion` ;
- contraintes, décisions et tâches explicitement rattachées au changement ;
- dépendances uniquement via `DEPENDS_ON` persisté ;
- directions DEPENDENCY et DEPENDENT ;
- shortest paths déterministes bornés par `maxDepth` ;
- chemins non résolus conservés avec warning ;
- aucune trace inventée pour un requirement uniquement proposé ;
- Memory == SQLite ;
- SQLite reopen ;
- vue compacte `schemaVersion=1`, `operation=analyze_change` ;
- JSON canonique et UTF-8 byte-déterministes ;
- frontière code maintenue : impact de code = MINOS.

## Tests M8

```text
ChangeAnalysisContractTest  7/7 PASS
```

Le test couvre les invariants fonctionnels, la parité des stores, le reopen SQLite et l'exposition compacte.

## Gate final

Commande :

```text
.\mvnw.cmd clean test
```

Résultat :

```text
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

Warnings connus non bloquants uniquement :

```text
Xerial SQLite restricted native access
SLF4J no provider / NOP logger
```

## ADR acceptées

```text
ADR-0056 — Analyse déterministe CURRENT / proposé
ADR-0057 — Impacts DEPENDS_ON par chemins bornés
ADR-0058 — Vue compacte d'analyse + JSON canonique
```

## Audit post-gate

Les commits ajoutés après le head exécutable testé sont exclusivement documentaires. Aucun code de production ou de test n'est modifié après le gate.

## Décision finale

**M8 est VALIDÉ.**

L'intégration finale est portée par PR #54. Après merge, le prochain jalon est **M9 — CLI stabilisée et distribution locale**.