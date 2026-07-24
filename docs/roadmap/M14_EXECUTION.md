# M14 — Plan d'exécution détaillé

Statut : **✅ VALIDÉ — contrat d'orchestration JARVIS read-only**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M13 validés et intégrés
main baseline M14 = 5269fbf8ef5586e0e04a776293dda2bf46786d0d
M13 = 346/346 PASS | Architecture 154/154 | packaging Windows PASS
```

Issue MORPHEUS : **#66**  
Branche : `m14/jarvis-orchestration-contract`  
PR : **#67**  
Head MORPHEUS validé : `d44d418ae0f1e528ea09a56cdd8c45647048c740`

Preuve cross-repo JARVIS : issue **#92**, PR **#93**, branche `feature/morpheus-orchestration-client`.  
Head JARVIS validé : `58899855bcd3446636c1f274ace8c1bfc8f46930`.

Validation : [`../VALIDATION_M14.md`](../VALIDATION_M14.md).

## Question de sortie

> **MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?**

**Réponse : OUI.**

## Source fonctionnelle UC-16

```text
lifecycleState
missingArtifacts
unverifiedCriteria
unresolvedLinks
blockingConstraints
nextAllowedTransitions
```

Frontière :

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

## M14-S1 — Port applicatif ✅

```text
ChangeLifecycleObservationSource
ChangeLifecycleObservation
ChangeOrchestrationState
ChangeOrchestrationStateService
ChangeTransitionEvaluation
ChangeTransitionEvaluationState
ChangeTransitionEvaluationService
```

Aucun type JARVIS dans domain/application.

## M14-S2 — Lifecycle explicite ✅

```text
lifecycle absent -> state absent / source=UNAVAILABLE
lifecycle fourni -> canonical state / source=CALLER_SUPPLIED
```

Aucune inférence depuis task completion, archive, timestamps, quality ou conventions provider.

## M14-S3 — Transition tri-state ✅

```text
ALLOWED        faits requis observables + ChangeLifecycleStateMachine autorise
BLOCKED        faits requis observables + ChangeLifecycleStateMachine bloque
UNKNOWN        au moins un fait requis est UNAVAILABLE
REQUIRES_INPUT information volontaire requise, ex. raison d'abandon
```

`UNAVAILABLE` n'est jamais converti en `false`.

Les raisons d'abandon d'une observation, de l'état source et de la cible sont transmises explicitement et séparément au travers du client JARVIS.

## M14-S4 — Vue UC-16 non destructive ✅

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

`acceptanceCriteria.status=UNAVAILABLE_IN_NORMALIZED_MODEL` tant qu'aucune projection explicite n'existe. `Scenario` n'est jamais converti en `AcceptanceCriterion`.

`blockingConstraints.status=UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED` : contraintes applicables visibles, blocage jamais inventé.

## M14-S5 — Liens non résolus ✅

Pour le Change courant :

```text
TraceabilityLink outgoing + resolution != RESOLVED
ExternalReference owner == change identity + resolutionState != RESOLVED
```

Aucune résolution live forcée.

## M14-S6 — CLI ✅

```text
morpheus --json change-orchestration state --project ID --change ID [--lifecycle STATE] [--abandonment-reason REASON]

morpheus --json change-orchestration transition-check --project ID --change ID --from STATE --to STATE [--from-abandonment-reason REASON] [--abandonment-reason REASON] [--allow-backward] [--allow-completed-reopen]
```

Aucune mutation lifecycle.

## M14-S7 — MCP ✅

```text
get_change_orchestration_state
evaluate_change_transition
```

Serveur M14 : **20 tools read-only** = 14 M10 + 2 M12 + 2 M13 + 2 M14.

## M14-S8 — HTTP API ✅

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une évaluation pure. OpenAPI : version `1.3.0`.

## M14-S9 — Client JARVIS cross-repo ✅ VALIDÉ

Dans `FTurleque/jarvis` :

```text
ChangeOrchestrationProvider
MorpheusOrchestrationConfig
MorpheusOrchestrationClient
MorpheusOrchestrationClientTest
```

Configuration :

```text
jarvis.morpheus.enabled=false
jarvis.morpheus.url=http://127.0.0.1:8765
jarvis.morpheus.project-id=
jarvis.morpheus.timeout-seconds=3
```

Fail-open : disabled, mapping absent, MORPHEUS indisponible ou non-2xx -> `Optional.empty()`.

Aucune dépendance `com.morpheus.*` et aucune règle lifecycle recopiée dans JARVIS.

Contrats :

```text
state(changeId, optionalLifecycleState, optionalLifecycleAbandonmentReason)

evaluateTransition(
  changeId,
  fromState,
  optionalFromAbandonmentReason,
  targetState,
  optionalTargetAbandonmentReason)
```

## M14-S10 — Architecture ✅ VALIDÉE

```text
domain/application -X-> com.jarvis.*
api/mcp/integrations -X-> com.jarvis.*
MORPHEUS -X-> JARVIS runtime
JARVIS -X-> com.morpheus.*
HTTP JSON = frontière cross-repo
```

Architecture : **160/160 PASS**.

## M14-S11 — Tests MORPHEUS ✅ VALIDÉS

Head exact :

```text
d44d418ae0f1e528ea09a56cdd8c45647048c740
```

Commande :

```powershell
.\mvnw.cmd clean test
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

Preuves M14 spécifiques :

```text
MorpheusJarvisOrchestrationApiContractTest 2/2 PASS
MorpheusJarvisOrchestrationCliTest         2/2 PASS
MorpheusM14McpStdioIntegrationTest         1/1 PASS
JarvisOrchestrationContractTest            5/5 PASS
LayerDependencyTest                        6/6 PASS
```

## M14-S12 — Packaging ✅ VALIDÉ

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

## M14-S13 — Gate JARVIS ✅ VALIDÉ

Head exact :

```text
58899855bcd3446636c1f274ace8c1bfc8f46930
```

Commande :

```powershell
.\mvnw.cmd -pl jarvis-core test
```

Résultats :

```text
Tests run: 536
Failures: 0
Errors: 0
Skipped: 16
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

Les `Skipped: 16` appartiennent à la suite JARVIS existante ; Maven termine avec `BUILD SUCCESS` et les 6 tests M14 du client MORPHEUS sont exécutés et verts.

Le wrapper Maven JARVIS a été rendu compatible avec Windows PowerShell absent du PATH et PowerShell 7. Ces commits de bootstrap ne changent pas le contrat métier M14.

## M14-S14 — Documentation ✅

```text
docs/VALIDATION_M14.md
docs/JARVIS.md
docs/API.md
docs/MCP.md
docs/openapi/morpheus-v1.yaml
docs/ROADMAP.md
docs/roadmap/M14_EXECUTION.md
README.md
distribution/README.md
docs/adr/README.md
```

## M14-S15 — ADR acceptées ✅

```text
ADR-0077 — Acceptée — M14
ADR-0078 — Acceptée — M14
ADR-0079 — Acceptée — M14
ADR-0080 — Acceptée — M14
```

## Gate final ✅

```text
MORPHEUS 357/357 PASS
Architecture 160/160 PASS
Packaging Windows PASS
JARVIS jarvis-core 536 tests | 0 failure | 0 error | BUILD SUCCESS
JARVIS MorpheusOrchestrationClientTest 6/6 PASS
```

M14 est **VALIDÉ**. Les PR #67 et #93 peuvent passer Ready for review.

La fusion reste soumise à autorisation explicite.
