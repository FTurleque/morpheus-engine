# Feuille de route — MORPHEUS

Statut : **C0 à M11 validés et intégrés ; M12 fonctionnellement complet, gate pending**

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
| **M12** | **MINOS optionnel / intention → code** | **🚧 GATE PENDING** | `roadmap/M12_EXECUTION.md`, projection 331 tests |
| M13 | NEXUS | ⏳ PLANIFIÉ | MORPHEUS autonome |
| M14 | JARVIS | ⏳ PLANIFIÉ | orchestration seulement |

M11 merge :

```text
e30ed4095700b445fedc4517c22ff447c22238f4
```

Références actives :

- [`VALIDATION_M11.md`](VALIDATION_M11.md)
- [`roadmap/M12_EXECUTION.md`](roadmap/M12_EXECUTION.md)
- [`MINOS.md`](MINOS.md)
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
NEXUS owns context selection/ranking/compression
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
```

## 3. C0 à M2 — Fondation ✅

C0 fixe le domaine, les frontières et la stratégie de validation. M0 prouve la faisabilité. M1 stabilise discovery/providers/store. M2 stabilise le modèle normalisé et les références externes optionnelles.

Gate M2 : **94/94 PASS**.

## 4. M3 — Temporalité / lifecycle / snapshots ✅

```text
CURRENT / PROPOSED / HISTORICAL
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
```

Gate : **147/147 PASS**.

## 5. M4 — Traçabilité typée ✅

```text
TraceabilityLink typé et directionnel
snapshot-scoped persistence
bounded deterministic traversal
unresolved/broken external references
trace(requirement)
LINKS_TO_CODE réservé au code externe
```

Gate : **189/189 PASS**.

## 6. M5 — Requêtes / contexte compact ✅

Recherche déterministe, pagination, vues ACTIVE/CURRENT, contexte changement, traçabilité et JSON canonique.

Gate : **227/227 PASS**.

## 7. M6 — Qualité / diagnostics ✅

Qualité explicable, couverture requirement/task, gaps acceptance explicites, lifecycle non inféré, qualité références externes.

Gate : **261/261 PASS**.

## 8. M7 — Synchronisation / fraîcheur ✅

Inventaire SHA-256, diff incrémental conservateur, fallback full rebuild, état persisted et freshness.

Gate : **282/282 PASS**.

## 9. M8 — Analyse des changements ✅

CURRENT baseline vs proposal, impacts requirement/dependency explicites, pas d'analyse code locale : **code impact = MINOS**.

Gate : **289/289 PASS**.

## 10. M9 — CLI / distribution ✅

CLI stable, sync full snapshot conservateur, SQLite persistante, shaded JAR, jpackage Windows/Linux, runtime Java embarqué.

Gate : **298/298 Windows + Linux**.

## 11. M10 — MCP STDIO ✅

14 tools read-only, JSON Schemas stricts, Java MCP SDK 2.0.0, stdout protocol-only.

Gate : **307/307 PASS**.

## 12. M11 — API HTTP headless ✅ / INTÉGRÉ

Question de sortie :

> MORPHEUS peut-il fonctionner comme service headless local via une API HTTP versionnée et stable sans déplacer les règles métier hors application/domain ?

**Réponse : OUI.**

```text
morpheus api --host 127.0.0.1 --port 8765
/api/v1
JDK jdk.httpserver
SQLite shared CLI/MCP/API
OpenAPI 3.1
```

Surface : projets, sync, specifications, requirements, trace, changes, contexts, lifecycle facts, versions, historique, comparaison, diagnostics.

Invariants :

```text
API = adapter sibling CLI/MCP
Scenario != AcceptanceCriterion
lifecycle absent != inferred
HTTP sync = FULL_REBUILD conservateur
failed sync preserves ACTIVE
no apply/promote/activate/rollback endpoint
```

Gate :

```text
API                    4/4 PASS
CLI                   12/12 PASS
Architecture         150/150 PASS
TOTAL                314/314 PASS
packaged API health  PASS
```

Validation : [`VALIDATION_M11.md`](VALIDATION_M11.md).  
Merge : `e30ed4095700b445fedc4517c22ff447c22238f4`.

## 13. M12 — MINOS 🚧

Question de sortie :

> **MORPHEUS peut-il résoudre en production une `ExternalReference(system=MINOS, resourceType=SYMBOL, ...)`, enrichir la traçabilité intention → code avec des faits MINOS explicites et révisés, tout en restant totalement utilisable lorsque MINOS est absent, arrêté, incompatible ou sur une autre JVM ?**

Architecture implémentée :

```text
MORPHEUS Java 21
 -> morpheus-integration-minos
 -> MCP client 2.0.0 / STDIO
 -> process MINOS Java 24
```

Aucune dépendance compile-time à `com.minos.*`.

Coordonnée exacte :

```text
system       = MINOS
resourceType = SYMBOL
project      = obligatoire
externalId   = exact symbolKey
revision     = activeSnapshotId attendu optionnel
```

Sémantique :

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
revision mismatch -> REVISION_MISMATCH
transport/process failure -> UNAVAILABLE
```

Invariant temporel :

```text
stored reference
 -> live observation
 -> response
 -X-> mutation published snapshot
```

Surfaces additives :

```text
CLI  minos-status / external-references list|resolve
MCP  list_external_references / resolve_external_reference
HTTP /integrations/minos/status
HTTP /projects/{id}/external-references
HTTP /projects/{id}/external-references/{ref}/resolution
```

Optionalité : sans `MORPHEUS_MINOS_JAR`, MORPHEUS reste entièrement fonctionnel et les résolutions retournent `NO_RESOLVER`.

Preuves implémentées :

```text
generic resolution taxonomy
Memory + SQLite non-mutating live resolution
real MINOS MCP STDIO fixture
exact symbolKey / ambiguity / revision
real MORPHEUS MCP STDIO M12
HTTP M12
CLI standalone M12
architecture guards
packaging rejects com/minos/*
standalone packaged MINOS status = DISABLED
```

Projection : **331 tests** avant preuve.

Gate restant :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

ADR candidates : ADR-0069 à ADR-0072. Elles restent **Proposées** jusqu'au gate.

## 14. M13 — NEXUS ⏳

MORPHEUS fournit intention/specification ; NEXUS sélectionne, classe, fusionne et compresse le contexte global.

**MORPHEUS reste utilisable sans NEXUS.**

## 15. M14 — JARVIS ⏳

MORPHEUS expose états, transitions, blockers, acceptance status, références et contexte. JARVIS orchestre la séquence d'actions.

## 16. Règle de pilotage

```text
1. documenter invariant / ADR
2. implémenter vertical slice minimal cohérent
3. tester Memory / SQLite / adapter réel selon le contrat
4. lancer gate local complet
5. accepter ADR seulement après preuve
6. fermer issue / passer PR Ready
7. merger uniquement après autorisation explicite
```
