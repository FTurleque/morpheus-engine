# M5 — Plan d'exécution détaillé

Statut : **M5 VALIDÉ — 6/6 slices validés ; S6 Ready, intégration finale en attente du merge #42**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M5.

---

# 1. Baseline finale

```text
C0 à M4 ✅ validés et intégrés
M4 gate  = 189/189 PASS

M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3
M5-S1 gate  = 196/196 PASS
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c
M5-S2 gate  = 202/202 PASS
M5-S3 merge = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73
M5-S3 gate  = 210/210 PASS
M5-S4 merge = a1be0820f16c077a33047eefb1e0deac0d5ab680
M5-S4 gate  = 217/217 PASS
M5-S5 merge = 330c7831dfe5261247fef98eef850d82c8f0e7c9
M5-S5 gate  = 227/227 PASS
M5-S6 gate  = 227/227 PASS
```

Issue de pilotage : **#36**.  
Validation finale : [`../VALIDATION_M5.md`](../VALIDATION_M5.md).

---

# 2. Question de sortie

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

**Réponse : OUI.**

---

# 3. Progression finale

```text
S1 ✅ find_requirements + pagination déterministe — PR #37 — ADR-0043 — 196/196 — MERGED
S2 ✅ projection métier snapshot-scoped — PR #38 — ADR-0044 — 202/202 — MERGED
S3 ✅ getters/lists déterministes — PR #39 — ADR-0045 — 210/210 — MERGED
S4 ✅ trace_requirement + get_change_context — PR #40 — ADR-0046 — 217/217 — MERGED
S5 ✅ vues compactes + warnings/provenance + JSON — PR #41 — ADR-0047 — 227/227 — MERGED
S6 ✅ validation finale — PR #42 — VALIDATION_M5.md — 227/227 — READY
```

```text
M5 = 6/6 VALIDÉ
S1-S5 = INTÉGRÉS
S6 = VALIDÉ / READY
M5 entièrement intégré après merge explicite #42
M6 = AUTORISÉ techniquement
```

---

# 4. Capacités validées

## S1 — Requirement query

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

```text
ACTIVE by default
ACTIVE/RETIRED explicit
CURRENT only
lexical key/title/statement
AND terms
stable RequirementId order
bounded pagination
Memory == SQLite
SQLite reopen
no fuzzy/semantic/LLM
```

## S2 — Projection métier

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

Familles :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence / Provenance
```

```text
snapshot/version ownership
immutable projection
stable DomainIdentity
no artificial temporal/version semantics
Memory == SQLite
SQLite reopen
V007 normalized
no generic business JSON persistence
```

## S3 — Getters/lists

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

```text
activeSpecification / snapshotSpecification
activeChange / snapshotChange
listActiveChanges / listSnapshotChanges
activeConstraints / snapshotConstraints
activeDesignDecisions / snapshotDesignDecisions
activeImplementationTasks / snapshotImplementationTasks
```

```text
ACTIVE by default
ACTIVE/RETIRED explicit only
not-found explicit
stable ordering
bounded pagination
Scenario != AcceptanceCriterion
```

## S4 — Trace et change context

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

```text
trace_requirement reuses M4
AFFECTS direct -> CURRENT Requirement
broken AFFECTS retained
constraints / decisions / tasks
bounded BIDIRECTIONAL trace
cycle-safe
external unresolved/stale/broken visible
Memory == SQLite
SQLite reopen
```

## S5 — Compact views

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
CanonicalJsonSerializer
```

```text
schemaVersion = 1
snapshot metadata
pagination metadata
RequirementId != EntityVersionId visible
SpecificationVersionId / TemporalState explicit
provenance/evidence retained
structured warnings
canonical deterministic JSON
no third-party JSON dependency
```

Warnings :

```text
CHANGE_NOT_FOUND
AFFECTED_REQUIREMENT_UNRESOLVED
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
EVIDENCE_NOT_FOUND
```

---

# 5. Gate final S6

Head testé :

```text
a91c925d32f3d6ee1901aa3495d37326bf7518ca
```

Commande :

```text
.\mvnw.cmd clean test
```

Résultat :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                     100 tests
-----------------------------------------------
TOTAL                                  227/227 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             19.206 s
Finished at                 2026-07-23T20:24:57+02:00
```

Warnings connus non bloquants : Xerial SQLite/JDK restricted native access et SLF4J NOP.

S6 n'ajoute aucun code, test, store, adapter, migration, dépendance ou contrat JSON.

---

# 6. Checklist de sortie M5

| Condition | État | Slice |
|---|---|---|
| recherche lexicale déterministe | ✅ | S1 |
| pagination bornée | ✅ | S1 |
| ACTIVE/CURRENT isolation | ✅ | S1 |
| historique ACTIVE/RETIRED explicite | ✅ | S1 |
| Memory/SQLite requirement query | ✅ | S1 |
| projection métier snapshot-scoped | ✅ | S2 |
| Memory/SQLite projection parity | ✅ | S2 |
| SQLite reopen projection | ✅ | S2 |
| getters/lists déterministes | ✅ | S3 |
| AcceptanceCriterion non inventé | ✅ | S3 |
| `trace_requirement` | ✅ | S4 |
| `get_change_context` | ✅ | S4 |
| broken AFFECTS retained | ✅ | S4 |
| external unresolved/broken visible | ✅ | S4 |
| compact DTOs | ✅ | S5 |
| structured warnings | ✅ | S5 |
| provenance/evidence retained | ✅ | S5 |
| canonical deterministic JSON | ✅ | S5 |
| Memory == SQLite | ✅ | S1-S5 |
| SQLite reopen | ✅ | S1-S5 |
| no semantic/LLM/NEXUS dependency | ✅ | S1-S5 |
| `VALIDATION_M5.md` | ✅ | S6 |
| final Maven gate | ✅ 227/227 | S6 |

---

# 7. Frontières confirmées

```text
semantic search / embeddings                -> future, optionnel
ranking global                              -> NEXUS
fusion multi-engine                         -> NEXUS
token-budget compression                    -> NEXUS
coverage diagnostics complets               -> M6
incremental sync / watcher                   -> M7
impact analysis complète                    -> M8
CLI publique stabilisée                     -> M9
MCP                                         -> M10
API/headless                                -> M11
MINOS production code resolution            -> M12
```

---

# 8. Décision

```text
M5 = VALIDÉ
6/6 = VALIDÉS
227/227 = PASS
M6 = AUTORISÉ
```

**Prochaine action de gouvernance : merge explicite de la PR #42 pour intégrer S6 à `main`, puis démarrage de M6 depuis ce merge exact.**
