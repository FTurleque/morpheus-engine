# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 5/6 intégrés ; S6 validation finale implémentée, gate en attente**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M5.

---

# 1. Baseline

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
```

Issue de pilotage : **#36**.

---

# 2. Question de sortie M5

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

Réponse fonctionnelle étayée par S1-S5 : **OUI**.

La clôture formelle reste bloquée sur le gate final S6.

---

# 3. Progression M5

```text
S1  ✅ find_requirements + pagination déterministe — PR #37 — ADR-0043 — 196/196 — MERGED
S2  ✅ projection métier requêtable snapshot-scoped — PR #38 — ADR-0044 — 202/202 — MERGED
S3  ✅ getters/lists déterministes — PR #39 — ADR-0045 — 210/210 — MERGED
S4  ✅ trace_requirement query view + get_change_context — PR #40 — ADR-0046 — 217/217 — MERGED
S5  ✅ vues compactes + warnings/provenance + JSON déterministe — PR #41 — ADR-0047 — 227/227 — MERGED
S6  🚧 validation finale — PR #42 Draft — VALIDATION_M5.md — gate attendu 227/227
```

```text
M5 : 5 / 6 slices intégrés
```

---

# 4. M5-S1 — INTÉGRÉ

Contrats :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

Sémantique : ACTIVE par défaut, ACTIVE/RETIRED explicite, CURRENT only, recherche lexicale déterministe, pagination bornée, Memory == SQLite, SQLite reopen.

Gate : **196/196 PASS**.  
ADR : **ADR-0043 — Acceptée — M5**.  
PR #37 : **MERGED**.

---

# 5. M5-S2 — INTÉGRÉ

Contrats :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

Projection snapshot-scoped de `Specification`, `Scenario`, `ChangeProposal`, `Constraint`, `DesignDecision`, `ImplementationTask`, `Evidence / Provenance`.

SQLite V007 normalisée sans payload JSON métier.

Gate : **202/202 PASS**.  
ADR : **ADR-0044 — Acceptée — M5**.  
PR #38 : **MERGED**.

---

# 6. M5-S3 — INTÉGRÉ

Contrats :

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Primitives : specification, change, list changes, constraints, design decisions et implementation tasks sur ACTIVE par défaut ou ACTIVE/RETIRED explicite.

Invariant :

```text
Scenario != AcceptanceCriterion
```

Aucun `AcceptanceCriterion` synthétique.

Gate : **210/210 PASS**.  
ADR : **ADR-0045 — Acceptée — M5**.  
PR #39 : **MERGED**.

---

# 7. M5-S4 — INTÉGRÉ

Contrats :

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

Sémantique :

```text
trace_requirement réutilise exactement M4
get_change_context = un seul snapshot publié
AFFECTS directs -> Requirement CURRENT
cibles AFFECTS cassées conservées
constraints / decisions / tasks par ChangeId
bounded BIDIRECTIONAL trace
external unresolved/broken visible
Memory == SQLite
SQLite reopen
no V008 / no new persistence
no semantic / LLM / NEXUS ranking-fusion
```

Gate : **217/217 PASS**.  
ADR : **ADR-0046 — Acceptée — M5**.  
PR #40 : **MERGED**.

---

# 8. M5-S5 — INTÉGRÉ

Contrats :

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
CanonicalJsonSerializer
```

Vues :

```text
find_requirements
trace_requirement
get_change_context
```

Invariants validés :

```text
schemaVersion = 1
snapshot + pagination metadata
RequirementId != EntityVersionId visible
SpecificationVersionId / TemporalState explicites
stable ordering
provenance/evidence conservées
warnings structurés issus de faits observables
resolved external -> aucun warning
canonical deterministic JSON
strict JSON escaping
map keys lexicographic
Optional.empty = null
no third-party JSON dependency
no pom change
no store adapter change
no V008 / no migration
no NEXUS ranking/fusion/token-budget compression
```

Gate : **227/227 PASS**.  
ADR : **ADR-0047 — Acceptée — M5**.  
PR #41 : **MERGED**.  
Merge : `330c7831dfe5261247fef98eef850d82c8f0e7c9`.

---

# 9. M5-S6 — VALIDATION FINALE / GATE EN ATTENTE

PR : **#42 — Draft**  
Branche : `m5/final-validation`

Document :

```text
docs/VALIDATION_M5.md
```

S6 est volontairement docs-only : aucun gap architectural n'a été découvert lors de la consolidation S1-S5.

Preuves consolidées :

```text
find_requirements lexical + pagination
CURRENT isolation
ACTIVE vs RETIRED
getters/lists métier
Scenario != AcceptanceCriterion
trace_requirement
get_change_context
compact DTOs
structured warnings
provenance/evidence
canonical deterministic JSON
Memory == SQLite
SQLite reopen
deterministic repeated results
no semantic/LLM/NEXUS dependency
```

Aucune modification S6 de :

```text
domain
application production code
provider
store adapter
SQLite schema
pom.xml
query semantics
JSON contract
```

Aucun nouveau test n'est ajouté par S6. Le gate final attendu reste donc :

```text
Architecture tests = 100/100
TOTAL              = 227/227 PASS
Failures           = 0
Errors             = 0
Skipped            = 0
BUILD SUCCESS
```

Après ce gate, `VALIDATION_M5.md` pourra être figé en `VALIDÉ`, la PR #42 passée Ready, puis mergée uniquement sur signal explicite.

---

# 10. Checklist bloquante avant M6

| Condition | État | Slice |
|---|---|---|
| recherche lexicale déterministe | ✅ | S1 |
| pagination/limites bornées | ✅ | S1 |
| ACTIVE/CURRENT isolation | ✅ | S1 |
| snapshot historique publié explicite | ✅ | S1 |
| Memory/SQLite requirement query | ✅ | S1 |
| SQLite reopen requirement query | ✅ | S1 |
| projection requêtable autres familles | ✅ | S2 |
| V007 normalisée sans JSON métier | ✅ | S2 |
| getters/lists déterministes | ✅ | S3 |
| AcceptanceCriterion non inventé | ✅ | S3 |
| Memory/SQLite business query parity | ✅ | S3 |
| SQLite reopen business queries | ✅ | S3 |
| `trace_requirement` query view | ✅ | S4 |
| `get_change_context` | ✅ | S4 |
| AFFECTS cassé conservé | ✅ | S4 |
| external unresolved/broken | ✅ | S4 |
| compact DTOs | ✅ | S5 |
| warnings structurés | ✅ | S5 |
| provenance/evidence conservées | ✅ | S5 |
| JSON déterministe | ✅ | S5 |
| `VALIDATION_M5.md` consolidé | ✅ candidat | S6 |
| gate final S6 | ⬜ | S6 |

---

# 11. Hors périmètre M5

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

# 12. Gouvernance

```text
1. branche dédiée depuis main exact
2. PR Draft avant rédaction substantielle
3. aucun changement architectural sans gap démontré
4. gate Windows .\mvnw.cmd clean test
5. PR Ready seulement après preuve
6. merge seulement après signal explicite
7. issue #36 + roadmap + VALIDATION_M5 mis à jour
```

**Prochaine porte : gate local final M5-S6 attendu 227/227.**
