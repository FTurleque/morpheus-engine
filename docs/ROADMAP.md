# Feuille de route — MORPHEUS

Statut : **C0 à M13 validés et intégrés**

Dernière mise à jour : 24 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

## 1. Vue globale

| Jalon | Sujet | Statut | Preuve |
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
| **M13** | **NEXUS optionnel / intention → contexte technique** | **✅ VALIDÉ / INTÉGRÉ** | `VALIDATION_M13.md`, 346/346 + packaging |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

Merges :

```text
M12 = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
```

Références actives :

- [`VALIDATION_M13.md`](VALIDATION_M13.md)
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
NEXUS ContextBundle != KnowledgeSnapshot persistence
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

Question de sortie :

> MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?

**Réponse : OUI.**

```text
MORPHEUS Java 21
 -> morpheus-integration-minos
 -> MCP client 2.0.0 / STDIO
 -> process MINOS Java 24
```

Gate : **331/331 PASS**, architecture **153/153**, packaging Windows PASS.  
ADR : **0069..0072 acceptées**.  
Validation : [`VALIDATION_M12.md`](VALIDATION_M12.md).  
Merge : `86dbb1d50e87ce354b7174156e9c8c5717722a17`.

## 5. M13 — NEXUS ✅ / INTÉGRÉ

Question de sortie :

> **MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?**

**Réponse : OUI.**

Architecture :

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
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

Mapping explicite :

```text
nexusProject = UUID ou nom unique NEXUS
```

Aucune création/indexation/rebuild NEXUS déclenchée par MORPHEUS.

Surfaces :

```text
CLI nexus-status
CLI augmented-context requirement|change
MCP get_augmented_requirement_context
MCP get_augmented_change_context
HTTP GET  /integrations/nexus/status
HTTP POST /projects/{id}/requirements/{requirementId}/augmented-context
HTTP POST /projects/{id}/changes/{changeId}/augmented-context
```

Serveur MCP : **18 tools read-only**.

Optionalité : sans `MORPHEUS_NEXUS_JAR`, NEXUS est `DISABLED`, l'intention MORPHEUS reste disponible et CLI/MCP/API continuent de fonctionner.

Gate autoritatif sur `a44e8938bfa03e8b8a1039c8271a8865b871ed7d` :

```text
Domain              21/21 PASS
Application         87/87 PASS
OpenSpec             26/26 PASS
Synthetic             7/7 PASS
SQLite                7/7 PASS
MINOS Integration     8/8 PASS
NEXUS Integration     7/7 PASS
MCP                    5/5 PASS
API                    7/7 PASS
CLI                  17/17 PASS
Architecture       154/154 PASS
--------------------------------
TOTAL              346/346 PASS
```

Packaging Windows :

```text
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
ZIP 33,654,379 bytes
```

ADR : **0073..0076 acceptées**.  
Validation : [`VALIDATION_M13.md`](VALIDATION_M13.md).  
Merge : `2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f`.

## 6. M14 — JARVIS ⏳

MORPHEUS expose états, transitions, blockers, acceptance status, références et contexte. JARVIS orchestre la séquence d'actions sans devenir propriétaire du domaine MORPHEUS, MINOS ou NEXUS.

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
