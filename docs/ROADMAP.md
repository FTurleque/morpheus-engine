# Feuille de route — MORPHEUS

Statut : **C0 à M12 validés et intégrés ; M13 fonctionnellement complet, gate local pending**

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
| M11 | API HTTP headless | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M11.md`, 314/314 + packaged health |
| M12 | MINOS optionnel / intention → code | ✅ VALIDÉ / INTÉGRÉ | `VALIDATION_M12.md`, 331/331 + packaging |
| **M13** | **NEXUS optionnel / intention → contexte technique** | **🚧 GATE PENDING** | `roadmap/M13_EXECUTION.md`, projection 346 tests |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

M12 merge :

```text
86dbb1d50e87ce354b7174156e9c8c5717722a17
```

Références actives :

- [`VALIDATION_M12.md`](VALIDATION_M12.md)
- [`roadmap/M13_EXECUTION.md`](roadmap/M13_EXECUTION.md)
- [`MINOS.md`](MINOS.md)
- [`NEXUS.md`](NEXUS.md)
- [`API.md`](API.md)
- [`MCP.md`](MCP.md)
- [`../distribution/README.md`](../distribution/README.md)
- [`adr/README.md`](adr/README.md)

## 2. Principes de séquencement

```text
Documenter d'abord
Décider ensuite
Implémenter après
Prouver avant de valider
Merger uniquement après autorisation explicite
```

Responsabilités :

```text
MORPHEUS owns intent/specification semantics
MINOS owns code intelligence
NEXUS owns context selection/ranking/fusion/compression
JARVIS owns orchestration
```

Invariants transverses :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
Scenario != AcceptanceCriterion
provider facts != MORPHEUS domain
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
optional engine absence != MORPHEUS failure
external live observation != published snapshot mutation
```

## 3. C0 à M11 — Fondations validées ✅

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
```

M11 fournit la CLI, le serveur MCP STDIO, l'API HTTP locale `/api/v1`, SQLite partagée et la distribution portable autonome.

## 4. M12 — MINOS ✅ / INTÉGRÉ

Question de sortie :

> MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?

**Réponse : OUI.**

Architecture :

```text
MORPHEUS Java 21
 -> morpheus-integration-minos
 -> MCP client 2.0.0 / STDIO
 -> process MINOS Java 24
```

Invariants :

```text
no com.minos.* dependency
exact symbolKey
optional activeSnapshotId revision
live resolution != snapshot mutation
MINOS absent != MORPHEUS failure
```

Surfaces :

```text
CLI  minos-status / external-references list|resolve
MCP  list_external_references / resolve_external_reference
HTTP /integrations/minos/status
HTTP /projects/{id}/external-references
HTTP /projects/{id}/external-references/{ref}/resolution
```

Gate : **331/331 PASS**, architecture **153/153**, packaging Windows et smokes optionnels PASS.

ADR : **0069..0072 acceptées**.  
Validation : [`VALIDATION_M12.md`](VALIDATION_M12.md).  
Merge : `86dbb1d50e87ce354b7174156e9c8c5717722a17`.

## 5. M13 — NEXUS 🚧

Question de sortie :

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

Réponse actuelle : **implémentation OUI ; preuve finale pending**.

Architecture implémentée :

```text
MORPHEUS Java 21
 -> TechnicalContextProvider
 -> morpheus-integration-nexus
 -> MCP client 2.0.0 / STDIO
 -> NEXUS MCP runner Java 21
 -> list_projects + build_context + explain_context
```

Frontière :

```text
MORPHEUS = intention structurée
NEXUS    = sélection / ranking / fusion / compression technique
```

MORPHEUS ne reranke et ne retronque aucun fragment NEXUS.

Mapping :

```text
nexusProject = UUID ou nom unique NEXUS, explicite par appel
```

Aucune création/indexation/rebuild NEXUS déclenchée par MORPHEUS.

Intention :

```text
REQUIREMENT -> key/title/statement
CHANGE      -> key/title/intent/scope + affected requirements + constraints + decisions + tasks
```

Options pass-through :

```text
tokenBudget 1..100000
requestedSources FILE|SYMBOL|TEST|DOCUMENTATION|INSTRUCTION|SKILL|GIT
constraints Map<String,String>
explain boolean
```

Résultat :

```text
ACTIVE snapshot
 -> intentContext
 -> technicalContext observation
 -> persisted=false
```

Surfaces M13 :

```text
CLI nexus-status
CLI augmented-context requirement|change
MCP get_augmented_requirement_context
MCP get_augmented_change_context
HTTP GET  /integrations/nexus/status
HTTP POST /projects/{id}/requirements/{requirementId}/augmented-context
HTTP POST /projects/{id}/changes/{changeId}/augmented-context
```

Serveur MCP M13 : **18 tools read-only**.

Optionalité : sans `MORPHEUS_NEXUS_JAR`, NEXUS est `DISABLED`, l'intention MORPHEUS reste disponible et CLI/MCP/API continuent de fonctionner.

Preuves implémentées :

```text
provider-neutral options validation
NEXUS settings disabled/configured/invalid
exact pass-through project/budget/sources/constraints/explain
real NEXUS MCP STDIO fixture
required tools compatibility check
NEXUS failure -> UNAVAILABLE observation
HTTP requirement/change augmentation
HTTP preserves external score/reasons/exclusions
CLI nexus-status standalone
real MORPHEUS MCP STDIO discovers M13 tools
architecture guards com.nexus.*
packaging embeds adapter but rejects com/nexus/*
MINOS + NEXUS standalone disabled smokes
API packaged health retained
```

Projection gate avant exécution :

```text
Domain              21
Application         87
OpenSpec             26
Synthetic             7
SQLite                7
MINOS Integration     8
NEXUS Integration     7
MCP                   5
API                   7
CLI                  17
Architecture        154
-----------------------
TOTAL attendu       346
```

**346 est une projection, pas une preuve.**

Gate restant :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

ADR candidates : **ADR-0073 à ADR-0076**, toutes proposées jusqu'au gate.

## 6. M14 — JARVIS ⏳

MORPHEUS expose états, transitions, blockers, acceptance status, références et contexte. JARVIS orchestre la séquence d'actions.

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
