# Feuille de route — MORPHEUS

Statut : **C0 à M13 validés et intégrés ; M14 fonctionnellement complet, gate local pending**

Dernière mise à jour : 24 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

## 1. Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | `VALIDATION_C0.md` |
| M0 | Faisabilité technique | ✅ VALIDÉ | `VALIDATION_M0.md` |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | `VALIDATION_M1.md`, 42/42 |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | `VALIDATION_M2.md`, 94/94 |
| M3 | Temporalité, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M3.md`, 147/147 |
| M4 | Traçabilité typée | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M4.md`, 189/189 |
| M5 | Requêtes et contexte compact | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M5.md`, 227/227 |
| M6 | Qualité, couverture et diagnostics | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M6.md`, 261/261 |
| M7 | Synchronisation incrémentale et fraîcheur | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M7.md`, 282/282 |
| M8 | Analyse des changements | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M8.md`, 289/289 |
| M9 | CLI stabilisée et distribution locale | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M9.md`, 298/298 Windows + Linux |
| M10 | Serveur MCP STDIO natif | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M10.md`, 307/307 |
| M11 | API HTTP headless | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M11.md`, 314/314 |
| M12 | MINOS optionnel / intention → code | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M12.md`, 331/331 |
| M13 | NEXUS optionnel / intention → contexte technique | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M13.md`, 346/346 |
| **M14** | **JARVIS / contrat d'orchestration read-only** | **🚧 GATE PENDING** | `roadmap/M14_EXECUTION.md`, projection 357 tests |

Merges actifs :

```text
M12 = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
main baseline M14 = 5269fbf8ef5586e0e04a776293dda2bf46786d0d
```

Références M14 :

- [`roadmap/M14_EXECUTION.md`](roadmap/M14_EXECUTION.md)
- [`JARVIS.md`](JARVIS.md)
- [`API.md`](API.md)
- [`MCP.md`](MCP.md)
- [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml)
- [`../distribution/README.md`](../distribution/README.md)
- [`adr/README.md`](adr/README.md)

## 2. Responsabilités

```text
MORPHEUS owns specification facts + lifecycle rules
MINOS owns code intelligence
NEXUS owns context selection/ranking/fusion/compression
JARVIS owns orchestration and action sequencing
```

Invariants :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
Scenario != AcceptanceCriterion
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
optional engine absence != MORPHEUS failure
external live observation != published snapshot mutation
NEXUS ContextBundle != KnowledgeSnapshot persistence
lifecycle unavailable != lifecycle inferred
transition evaluation != lifecycle mutation
UNKNOWN != BLOCKED
MORPHEUS rules != JARVIS action sequencing
```

## 3. Gates validés

```text
M2   94/94
M3  147/147
M4  189/189
M5  227/227
M6  261/261
M7  282/282
M8  289/289
M9  298/298 Windows + Linux
M10 307/307
M11 314/314
M12 331/331 | Architecture 153/153 | packaging PASS
M13 346/346 | Architecture 154/154 | packaging PASS
```

## 4. M12 — MINOS ✅ / INTÉGRÉ

MORPHEUS résout les références code MINOS via MCP STDIO sans dépendance `com.minos.*`. Gate : **331/331**, architecture **153/153**.

## 5. M13 — NEXUS ✅ / INTÉGRÉ

MORPHEUS délègue sélection/ranking/fusion/compression du contexte technique à NEXUS via MCP STDIO sans dépendance `com.nexus.*`. Gate : **346/346**, architecture **154/154**.

## 6. M14 — JARVIS 🚧

Question de sortie :

> **MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?**

Réponse actuelle : **implémentation OUI ; preuve locale finale pending**.

Frontière :

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

Lifecycle :

```text
absent  -> UNAVAILABLE
fourni  -> CALLER_SUPPLIED
```

Décision read-only :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Surface :

```text
CLI  change-orchestration state|transition-check
MCP  get_change_orchestration_state
MCP  evaluate_change_transition
HTTP GET  /projects/{id}/changes/{changeId}/orchestration
HTTP POST /projects/{id}/changes/{changeId}/transition-check
```

Serveur M14 : **20 tools read-only**.

UC-16 :

```text
snapshot / change / lifecycle
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

Acceptance et blocking constraints non modélisés restent explicitement `UNAVAILABLE`; aucun fait n'est inventé.

Preuve cross-repo :

```text
FTurleque/jarvis
Issue #92
PR #93 draft
ChangeOrchestrationProvider
MorpheusOrchestrationClient
fail-open, aucun com.morpheus.*
```

Projection avant exécution :

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

ADR-0077..0080 restent **Proposées — M14 gate pending**.

## 7. Règle de pilotage

```text
1. documenter invariant / ADR
2. implémenter vertical slice cohérent
3. tester backend/adapters réels selon le contrat
4. lancer gate local complet
5. accepter ADR seulement après preuve
6. fermer issue / passer PR Ready
7. merger uniquement après autorisation explicite
```
