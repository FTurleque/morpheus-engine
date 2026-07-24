# M14 — Plan d'exécution détaillé

Statut : **🚧 EN COURS — contrat d'orchestration JARVIS read-only**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M13 validés et intégrés
main = 5269fbf8ef5586e0e04a776293dda2bf46786d0d
M13 = 346/346 PASS | Architecture 154/154 | packaging Windows PASS
```

Issue : **#66 — M14 — Contrat d’orchestration JARVIS read-only**  
Branche : `m14/jarvis-orchestration-contract`

## Question de sortie

> **MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?**

## Source fonctionnelle

UC-16 demande :

```text
lifecycleState
missingArtifacts
unverifiedCriteria
unresolvedLinks
blockingConstraints
nextAllowedTransitions
```

Règle : MORPHEUS expose les faits et règles lifecycle ; JARVIS orchestre.

## Invariants M14

```text
MORPHEUS != orchestrateur JARVIS
MORPHEUS -X-> com.jarvis.*
JARVIS -X-> détails provider-specific MORPHEUS
lifecycle non observable != lifecycle inventé
fact UNAVAILABLE != FALSE
transition UNKNOWN != BLOCKED
transition evaluation != mutation
readiness != permission d'écriture
COMPLETED != CURRENT
APPLY != PROMOTE != ACTIVATE
```

## M14-S1 — Port applicatif d'orchestration

Introduire un package provider-neutral `com.morpheus.application.orchestration` avec :

```text
ChangeLifecycleObservation
ChangeOrchestrationFact
ChangeOrchestrationState
ChangeOrchestrationStateService
ChangeTransitionEvaluation
ChangeTransitionEvaluationState
ChangeTransitionEvaluationService
```

## M14-S2 — Lifecycle explicite, jamais inféré

Le snapshot publié ne contient pas aujourd'hui un lifecycle explicite fiable.

Contrat :

```text
lifecycle absent -> state=UNAVAILABLE, source=UNAVAILABLE
lifecycle fourni -> state=<canonical>, source=CALLER_SUPPLIED
```

Aucun chemin, checkbox, archive, timestamp ou qualité ne devient implicitement lifecycle.

## M14-S3 — Transition tri-state

Le `ChangeLifecycleStateMachine` reste source de vérité pour les faits booléens connus.

M14 ajoute une couche d'analyse :

```text
ALLOWED        préconditions connues + machine autorise
BLOCKED        préconditions connues + machine bloque
UNKNOWN        au moins un fait requis est UNAVAILABLE
REQUIRES_INPUT information volontaire requise (ex. abandon reason)
```

Un fait `UNAVAILABLE` n'est jamais converti en `false` pour obtenir artificiellement un blocage.

## M14-S4 — Readiness JARVIS

Agrégation compacte :

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
nextTransitions
persisted=false
```

`blockingConstraints` reste explicitement indisponible tant que le modèle ne qualifie pas une contrainte comme bloqueur.

`acceptanceCriteria` reste `UNAVAILABLE_IN_NORMALIZED_MODEL` tant qu'aucune projection explicite n'existe ; Scenario n'est jamais converti en AcceptanceCriterion.

## M14-S5 — Références non résolues

Pour le `ChangeId` courant :

```text
ExternalReference owner == change identity
resolutionState != RESOLVED
```

Les références sont listées avec cible, état et raison sans résolution live forcée.

## M14-S6 — CLI

```text
morpheus --json change-orchestration state \
  --project <project-id> \
  --change <change-id> \
  [--lifecycle <state>]

morpheus --json change-orchestration transition-check \
  --project <project-id> \
  --change <change-id> \
  --from <state> \
  --to <state> \
  [--allow-backward] \
  [--allow-completed-reopen] \
  [--abandonment-reason <reason>]
```

Aucune commande de mutation lifecycle.

## M14-S7 — MCP

Deux tools read-only additifs :

```text
get_change_orchestration_state
evaluate_change_transition
```

Serveur cible : **20 tools read-only** = 14 M10 + 2 M12 + 2 M13 + 2 M14.

## M14-S8 — HTTP API

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration?lifecycleState=<optional>
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le POST est une évaluation pure, pas une mutation.

Body transition :

```json
{
  "fromState":"PLANNED",
  "targetState":"IMPLEMENTING",
  "allowBackwardTransitions":false,
  "allowCompletedReopen":false,
  "abandonmentReason":null
}
```

## M14-S9 — Client JARVIS cross-repo

Côté `FTurleque/jarvis`, ajouter un adaptateur HTTP optionnel vers MORPHEUS :

```text
MorpheusOrchestrationConfig
MorpheusOrchestrationClient
```

Objectif : prouver que JARVIS peut consommer le contrat sans dépendance Maven vers MORPHEUS et continue à fonctionner lorsque MORPHEUS est désactivé/indisponible.

Aucune décision d'orchestration n'est recopiée depuis MORPHEUS dans le client.

## M14-S10 — Architecture

```text
domain/application -X-> jarvis
api/mcp           -X-> jarvis
MORPHEUS           -X-> com.jarvis.*
JARVIS             -X-> com.morpheus.*
HTTP JSON = frontière cross-repo
```

## M14-S11 — Tests MORPHEUS

Projection cible avant exécution :

```text
M13 baseline        346
M14 delta prévu     ~27
------------------------
TOTAL attendu       ~373
```

Le total exact sera figé après implémentation des tests.

Preuves prévues :

```text
tri-state UNKNOWN sur fait indisponible
ALLOWED/BLOCKED via state machine sur faits connus
ABANDONED -> REQUIRES_INPUT sans raison
lifecycle absent -> UNAVAILABLE
lifecycle fourni -> CALLER_SUPPLIED
unresolved external references listées
acceptance/blocking constraints jamais inventés
HTTP state + transition-check
CLI state + transition-check
vrai MORPHEUS MCP STDIO découvre/calle les tools M14
architecture guard com.jarvis.*
```

## M14-S12 — Packaging

M14 ne rajoute aucun moteur externe à embarquer.

Le packaging M13 doit rester vert et le launcher packagé doit conserver :

```text
MINOS optional
NEXUS optional
MCP 20 read-only tools
API /health
change-orchestration CLI
```

## M14-S13 — ADR candidates

```text
ADR-0077 — responsabilité MORPHEUS/JARVIS et frontière HTTP read-only
ADR-0078 — lifecycle explicite + transition tri-state sans inférence
ADR-0079 — agrégation d'état d'orchestration non destructive
ADR-0080 — surfaces CLI/MCP/HTTP et client JARVIS optionnel
```

Toutes restent **Proposées — M14 gate pending** jusqu'à preuve.

## M14-S14 — Gate final

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

M14 ne sera marqué `VALIDÉ` qu'après preuves reproductibles. La fusion exige une autorisation explicite M14.