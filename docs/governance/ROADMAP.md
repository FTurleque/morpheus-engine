# Feuille de route — MORPHEUS

Statut : **C0 à M14 + D0 intégrés — M15 validé techniquement, PR #77 prête à intégrer**

Dernière mise à jour : 26 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

La baseline C0→M14 est acquise. La suite officielle est définie dans [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md). La politique documentaire post-M14 est [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md).

## 1. Vue globale

| Jalon | Sujet | Statut | Preuve / prochaine porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | [`VALIDATION_C0.md`](../validation/VALIDATION_C0.md) |
| M0 | Faisabilité technique | ✅ VALIDÉ | [`VALIDATION_M0.md`](../validation/VALIDATION_M0.md) |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | [`VALIDATION_M1.md`](../validation/VALIDATION_M1.md), 42/42 |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | [`VALIDATION_M2.md`](../validation/VALIDATION_M2.md), 94/94 |
| M3 | Temporalité, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M3.md`](../validation/VALIDATION_M3.md), 147/147 |
| M4 | Traçabilité typée | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M4.md`](../validation/VALIDATION_M4.md), 189/189 |
| M5 | Requêtes et contexte compact | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M5.md`](../validation/VALIDATION_M5.md), 227/227 |
| M6 | Qualité, couverture et diagnostics | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M6.md`](../validation/VALIDATION_M6.md), 261/261 |
| M7 | Synchronisation incrémentale et fraîcheur | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M7.md`](../validation/VALIDATION_M7.md), 282/282 |
| M8 | Analyse des changements | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M8.md`](../validation/VALIDATION_M8.md), 289/289 |
| M9 | CLI stabilisée et distribution locale | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M9.md`](../validation/VALIDATION_M9.md), 298/298 Windows + Linux |
| M10 | Serveur MCP STDIO natif | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M10.md`](../validation/VALIDATION_M10.md), 307/307 |
| M11 | API HTTP headless | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M11.md`](../validation/VALIDATION_M11.md), 314/314 |
| M12 | MINOS optionnel / intention → code | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M12.md`](../validation/VALIDATION_M12.md), 331/331 |
| M13 | NEXUS optionnel / intention → contexte technique | ✅ VALIDÉ / INTÉGRÉ | [`VALIDATION_M13.md`](../validation/VALIDATION_M13.md), 346/346 |
| **M14** | **JARVIS / contrat d'orchestration read-only** | **✅ VALIDÉ / INTÉGRÉ** | [`VALIDATION_M14.md`](../validation/VALIDATION_M14.md), 357/357 + JARVIS 536 tests |

### Roadmap post-M14

| Étape | Sujet | Statut | Porte |
|---|---|---|---|
| **D0** | Réconciliation documentaire post-M14 | **✅ VALIDÉ / INTÉGRÉ — PR #75** | [`VALIDATION_D0.md`](../validation/VALIDATION_D0.md) |
| **M15** | Acceptance Criteria, Verification & Evidence | **✅ VALIDÉ TECHNIQUEMENT — PR #77 READY** | [`VALIDATION_M15.md`](../validation/VALIDATION_M15.md), 371/371 + packaging PASS |
| **M16** | Constraint Semantics & Policy Enforcement | ⏳ PROCHAIN APRÈS INTÉGRATION M15 | contraintes bloquantes explicables sans `UNKNOWN -> BLOCKED` |
| **M17** | Controlled Lifecycle & Write Operations | ⏳ PLANIFIÉ | mutations opt-in, CAS, conflits, audit, permissions |
| **M18** | Real Providers & Multi-Provider Composition | ⏳ PLANIFIÉ | deuxième provider réel + composition/provenance/conflits |
| **M19** | Production Hardening, Scale & Operability | ⏳ PLANIFIÉ | performances, robustesse et observabilité mesurées |
| **M20** | Release Engineering, Installation PROD & MORPHEUS 1.0 | ⏳ PLANIFIÉ | setup Windows, releases, checksums, upgrade/uninstall, Linux |

Plan détaillé : [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md).  
Exécution D0 : [`D0_EXECUTION.md`](../roadmap/D0_EXECUTION.md).  
Exécution M15 : [`M15_EXECUTION.md`](../roadmap/M15_EXECUTION.md).

Merges actifs de la baseline :

```text
M12 = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
M14 MORPHEUS = 88e4e4d83c25035b9441e78d0ac8145db83306c4
M14 JARVIS   = 1bf2612e616f3323814caf60e76525b4808cd400
D0 = ec75d3963422d6281f2904c5ebd547124db92ad6
```

Références actives :

- [`VALIDATION_M15.md`](../validation/VALIDATION_M15.md)
- [`M15_EXECUTION.md`](../roadmap/M15_EXECUTION.md)
- [`VALIDATION_M14.md`](../validation/VALIDATION_M14.md)
- [Intégration JARVIS](../developer/INTEGRATIONS.md#jarvis)
- [API HTTP](../developer/API.md)
- [MCP](../developer/MCP.md)
- [`morpheus-v1.yaml`](../openapi/morpheus-v1.yaml)
- [`distribution/README.md`](../../distribution/README.md)
- [`adr/README.md`](../adr/README.md)

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
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
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
M14 357/357 | Architecture 160/160 | packaging PASS | JARVIS 536 tests BUILD SUCCESS
D0  documentation authority PASS | primary links PASS | historical evidence preserved
M15 371/371 | Architecture 157/157 | packaging + smokes PASS
```

## 4. M12 — MINOS ✅ / INTÉGRÉ

MORPHEUS résout les références code MINOS via MCP STDIO sans dépendance `com.minos.*`. Gate : **331/331**, architecture **153/153**.

## 5. M13 — NEXUS ✅ / INTÉGRÉ

MORPHEUS délègue sélection/ranking/fusion/compression du contexte technique à NEXUS via MCP STDIO sans dépendance `com.nexus.*`. Gate : **346/346**, architecture **154/154**.

## 6. M14 — JARVIS ✅ / INTÉGRÉ

Question de sortie :

> **MORPHEUS peut-il fournir à JARVIS un contrat machine stable et explicable indiquant l'état observable d'un changement, les faits manquants, les références non résolues, les contraintes applicables et les transitions lifecycle autorisées/bloquées/inconnues, sans devenir lui-même l'orchestrateur ni inventer des faits non observables ?**

**Réponse : OUI.**

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

### Gate MORPHEUS

Head : `d44d418ae0f1e528ea09a56cdd8c45647048c740`.

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
```

Packaging Windows : **PASS**, archive `33,702,405 bytes`.

### Gate JARVIS cross-repo

Head : `58899855bcd3446636c1f274ace8c1bfc8f46930`.

```text
jarvis-core
Tests run: 536
Failures: 0
Errors: 0
Skipped: 16
BUILD SUCCESS
MorpheusOrchestrationClientTest 6/6 PASS
```

Les raisons d'abandon observation/source/cible sont transmises séparément et jamais inventées.

ADR-0077..0080 : **Acceptées — M14**.

Validation : [`VALIDATION_M14.md`](../validation/VALIDATION_M14.md).

Intégration :

```text
MORPHEUS PR #67 merged -> 88e4e4d83c25035b9441e78d0ac8145db83306c4
JARVIS   PR #93 merged -> 1bf2612e616f3323814caf60e76525b4808cd400
```

## 7. Direction post-M14

La plateforme technique fondamentale est acquise : stockage, snapshots, traçabilité, requêtes, diagnostics, synchronisation, analyse, CLI, MCP, HTTP et intégrations MINOS/NEXUS/JARVIS.

D0 est intégré et M15 est maintenant **validé techniquement**. Sa PR #77 doit être intégrée avant d'ouvrir M16 afin que la sémantique des contraintes s'appuie sur le modèle d'acceptance réellement publié.

```text
D0   documentation reconciliation        ✅ intégré
M15  acceptance / verification / evidence ✅ validé techniquement / PR #77 Ready
M16  constraint semantics / blocking policy ⏳ prochain après merge M15
M17  controlled write / lifecycle mutations
M18  real providers / multi-provider composition
M19  production hardening / scale / operability
M20  release engineering / PROD installation / 1.0
```

Principes de séquencement :

```text
rich business semantics before write operations
write invariants before provider composition
provider composition before large-scale qualification
large-scale qualification before stable product release
```

### Standard d'installation cible M20

Le parcours Windows utilisateur normal doit être aligné avec le standard MINOS :

```text
GitHub Release
  -> MORPHEUS-<version>-windows-x64-setup.exe
  -> %LOCALAPPDATA%\Programs\MORPHEUS
  -> PATH utilisateur optionnel
  -> morpheus.cmd
```

Données séparées :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
```

Le ZIP portable reste supporté pour l'automatisation, le diagnostic et les usages portables. Le mode utilisateur normal ne doit plus recommander `C:\Tools\Morpheus` comme emplacement d'installation produit.

Artefacts cible :

```text
MORPHEUS-<version>-windows-x64-setup.exe
MORPHEUS-<version>-windows-x64-setup.exe.sha256
morpheus-<version>-windows-x64.zip
morpheus-<version>-windows-x64.zip.sha256
morpheus-<version>-linux-x64.tar.gz
morpheus-<version>-linux-x64.tar.gz.sha256
```

Détails et gates : [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md).

## 8. Règle de pilotage

```text
1. documenter invariant / ADR
2. implémenter vertical slice cohérent
3. tester backend/adapters réels selon le contrat
4. lancer gate local complet
5. accepter ADR seulement après preuve
6. fermer issue / passer PR Ready
7. merger uniquement après autorisation explicite
```