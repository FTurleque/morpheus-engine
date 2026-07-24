# M14 — Plan d'exécution détaillé

Statut : **FONCTIONNELLEMENT COMPLET — gate local MORPHEUS/JARVIS pending**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M13 validés et intégrés
main = 5269fbf8ef5586e0e04a776293dda2bf46786d0d
M13 = 346/346 PASS | Architecture 154/154 | packaging Windows PASS
```

Issue : **#66 — M14 — Contrat d’orchestration JARVIS read-only**  
Branche : `m14/jarvis-orchestration-contract`  
PR : **#67** (draft)

Preuve cross-repo JARVIS : issue **#92**, PR **#93** (draft), branche `feature/morpheus-orchestration-client`.

## Question de sortie

> **MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?**

Réponse actuelle : **implémentation OUI ; preuve finale pending**.

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

## M14-S1 — Port applicatif ✅ implémenté

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

## M14-S2 — Lifecycle explicite ✅ implémenté

```text
lifecycle absent -> state absent / source=UNAVAILABLE
lifecycle fourni -> canonical state / source=CALLER_SUPPLIED
```

Aucune inférence depuis task completion, archive, timestamps, quality ou conventions provider.

## M14-S3 — Transition tri-state ✅ implémenté

```text
ALLOWED        faits requis observables + ChangeLifecycleStateMachine autorise
BLOCKED        faits requis observables + ChangeLifecycleStateMachine bloque
UNKNOWN        au moins un fait requis est UNAVAILABLE
REQUIRES_INPUT information volontaire requise, ex. raison d'abandon
```

`UNAVAILABLE` n'est jamais converti en `false`.

## M14-S4 — Vue UC-16 non destructive ✅ implémentée

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

## M14-S5 — Liens non résolus ✅ implémenté

Pour le Change courant :

```text
TraceabilityLink outgoing + resolution != RESOLVED
ExternalReference owner == change identity + resolutionState != RESOLVED
```

Aucune résolution live forcée.

## M14-S6 — CLI ✅ implémenté

```text
morpheus --json change-orchestration state --project ID --change ID [--lifecycle STATE] [--abandonment-reason REASON]

morpheus --json change-orchestration transition-check --project ID --change ID --from STATE --to STATE [--from-abandonment-reason REASON] [--abandonment-reason REASON] [--allow-backward] [--allow-completed-reopen]
```

Aucune mutation lifecycle.

## M14-S7 — MCP ✅ implémenté

```text
get_change_orchestration_state
evaluate_change_transition
```

Serveur M14 : **20 tools read-only** = 14 M10 + 2 M12 + 2 M13 + 2 M14.

## M14-S8 — HTTP API ✅ implémenté

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une évaluation pure. OpenAPI : version `1.3.0`.

## M14-S9 — Client JARVIS cross-repo ✅ implémenté

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

## M14-S10 — Architecture ✅ implémentée

```text
domain/application -X-> com.jarvis.*
api/mcp/integrations -X-> com.jarvis.*
MORPHEUS -X-> JARVIS runtime
JARVIS -X-> com.morpheus.*
HTTP JSON = frontière cross-repo
```

`LayerDependencyTest` ajoute un garde dédié.

## M14-S11 — Tests MORPHEUS ✅ implémentés, exécution pending

Delta réel ajouté à la suite de tests :

```text
API
  MorpheusJarvisOrchestrationApiContractTest        2

CLI
  MorpheusJarvisOrchestrationCliTest                2
  MorpheusM14McpStdioIntegrationTest                1
                                                     = 3

Architecture
  JarvisOrchestrationContractTest                   5
  LayerDependencyTest                              +1
                                                     = 6
```

Projection :

```text
M13 baseline 346
M14 delta     11
----------------
TOTAL attendu 357
```

Détail projeté :

```text
Domain              21
Application         87
OpenSpec             26
Synthetic             7
SQLite                7
MINOS Integration     8
NEXUS Integration     7
MCP                    5
API                    9
CLI                   20
Architecture        160
-----------------------
TOTAL attendu       357
```

**357 est une projection, pas une preuve.**

Preuves codées :

```text
lifecycle absent -> UNAVAILABLE
lifecycle caller -> CALLER_SUPPLIED
DRAFT -> PROPOSED = ALLOWED
PROPOSED -> SPECIFIED = UNKNOWN si faits requis indisponibles
ABANDONED sans raison = REQUIRES_INPUT
missing != unavailable
applicable constraints visibles sans blocker inventé
trace + external unresolved links
HTTP state + transition-check
CLI state + transition-check
vrai MORPHEUS MCP STDIO découvre/calle les tools M14
architecture guard com.jarvis.*
```

## M14-S12 — Packaging ✅ implémenté, exécution pending

Windows/Linux :

```text
workdir .m14-windows / .m14-linux
M14 CLI/MCP/API/application classes required
MINOS/NEXUS adapters retained
com/minos/* forbidden
com/nexus/* forbidden
com/jarvis/* forbidden
MINOS/NEXUS not bundled
JARVIS not bundled
change-orchestration launcher help smoke
jdk.httpserver retained
```

Attendu Windows :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

## M14-S13 — Documentation ✅ implémentée

```text
docs/JARVIS.md
docs/API.md
docs/MCP.md
docs/openapi/morpheus-v1.yaml
docs/ROADMAP.md
docs/roadmap/M14_EXECUTION.md
README.md
distribution/README.md
ADR-0077..0080
```

## M14-S14 — ADR candidates

```text
ADR-0077 — Proposée — frontière read-only MORPHEUS / JARVIS
ADR-0078 — Proposée — lifecycle explicite + transition tri-state
ADR-0079 — Proposée — état d'orchestration non destructif
ADR-0080 — Proposée — surfaces + client JARVIS optionnel
```

Elles restent proposées jusqu'aux gates.

## M14-S15 — Gate final ⏳

MORPHEUS :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

JARVIS :

```powershell
.\mvnw.cmd -pl jarvis-core test
```

ou le gate complet JARVIS si l'environnement local le permet.

M14 ne sera `VALIDÉ` qu'après preuves reproductibles. Les PR #67 et #93 restent draft et non fusionnées jusqu'à autorisation explicite.