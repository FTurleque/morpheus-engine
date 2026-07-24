# Validation M14 — Contrat d'orchestration JARVIS read-only

Statut : **✅ VALIDÉ / INTÉGRÉ**

Date : 24 juillet 2026

Issue MORPHEUS : #66 — completed  
PR MORPHEUS : #67 — merged  
Head MORPHEUS validé : `d44d418ae0f1e528ea09a56cdd8c45647048c740`  
Merge MORPHEUS : `88e4e4d83c25035b9441e78d0ac8145db83306c4`

Preuve cross-repo JARVIS : issue #92 — completed / PR #93 — merged  
Head JARVIS validé : `58899855bcd3446636c1f274ace8c1bfc8f46930`  
Merge JARVIS : `1bf2612e616f3323814caf60e76525b4808cd400`

## Question de sortie

> MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?

**Réponse : OUI.**

## Frontière validée

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

Aucune dépendance binaire cross-repo : MORPHEUS n'importe aucun `com.jarvis.*` et JARVIS n'importe aucun `com.morpheus.*`. La frontière est HTTP JSON locale et read-only.

## Contrat lifecycle

```text
lifecycle absent -> state absent / source=UNAVAILABLE
lifecycle fourni -> canonical state / source=CALLER_SUPPLIED
```

Aucune inférence depuis tasks, archives, timestamps, findings qualité ou conventions provider.

Évaluation :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

`UNAVAILABLE` n'est jamais converti silencieusement en `false`. Lorsque les faits requis sont connus, la décision délègue à `ChangeLifecycleStateMachine`.

Les raisons d'abandon source et cible sont transmises séparément dans le contrat JARVIS.

## Vue UC-16

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
nextAllowedTransitions
transitionEvaluations
persisted=false
```

`Scenario != AcceptanceCriterion`. Les blocking constraints non modélisées restent explicitement indisponibles ; aucun blocker n'est inventé.

## Surfaces validées

CLI :

```text
change-orchestration state
change-orchestration transition-check
```

MCP :

```text
get_change_orchestration_state
evaluate_change_transition
```

Catalogue : **20 tools read-only**.

HTTP :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une évaluation pure, sans mutation.

## Gate MORPHEUS autoritatif

Commande :

```powershell
.\mvnw.cmd clean test
```

Head :

```text
d44d418ae0f1e528ea09a56cdd8c45647048c740
```

Résultats :

```text
Domain              21/21 PASS
Application         87/87 PASS
OpenSpec             26/26 PASS
Synthetic             7/7 PASS
SQLite                7/7 PASS
MINOS Integration     8/8 PASS
NEXUS Integration     7/7 PASS
MCP                    5/5 PASS
API                    9/9 PASS
CLI                  20/20 PASS
Architecture       160/160 PASS
--------------------------------
TOTAL              357/357 PASS
Failures                 0
Errors                   0
Skipped                  0
BUILD SUCCESS
```

## Packaging Windows

Commande :

```powershell
.\distribution\build-portable.ps1
```

Preuves :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
MORPHEUS 0.1.0-SNAPSHOT
MINOS status -> DISABLED sans configuration
NEXUS status -> DISABLED sans configuration
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,702,405 bytes
```

MINOS, NEXUS et JARVIS ne sont pas embarqués.

## Gate JARVIS cross-repo

Commande :

```powershell
.\mvnw.cmd -pl jarvis-core test
```

Head :

```text
58899855bcd3446636c1f274ace8c1bfc8f46930
```

Résultat :

```text
Tests run: 536
Failures: 0
Errors: 0
Skipped: 16
BUILD SUCCESS
```

Preuve spécifique :

```text
MorpheusOrchestrationClientTest 6/6 PASS
```

Le client JARVIS est optionnel et fail-open. Les états `ABANDONED` transmettent les raisons d'abandon observation/source/cible sans en inventer.

Le Maven Wrapper JARVIS a également été rendu compatible avec Windows PowerShell hors PATH et PowerShell 7 ; ce correctif n'altère pas le comportement métier M14.

## ADR acceptées

```text
ADR-0077 — Acceptée — M14
ADR-0078 — Acceptée — M14
ADR-0079 — Acceptée — M14
ADR-0080 — Acceptée — M14
```

## Intégration

```text
MORPHEUS PR #67 merged
merge commit = 88e4e4d83c25035b9441e78d0ac8145db83306c4

JARVIS PR #93 merged
merge commit = 1bf2612e616f3323814caf60e76525b4808cd400
```

## Conclusion

M14 est **VALIDÉ / INTÉGRÉ**. MORPHEUS expose les faits et décisions lifecycle read-only ; JARVIS les consomme pour orchestrer sans duplication de règles ni dépendance binaire cross-repo.
