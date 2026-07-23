# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 2/6 intégrés ; S3 implémenté, gate en attente**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M5.

---

# 1. Baseline

```text
C0 à M4 ✅ validés et intégrés
M4 gate  = 189/189 PASS
M4 final code merge = ac317eb63bbe0edb854c04660c5c143ba46e0c43
M4 final docs merge = d4a4c9f4816e42a8629d2f41cfe22703f53f210a

M5-S1 merge = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3
M5-S1 gate  = 196/196 PASS
M5-S2 merge = 3a39371518d9d327ea4cbee0994da65b218ec64c
M5-S2 gate  = 202/202 PASS
```

Issue de pilotage : **#36**.

---

# 2. Question de sortie M5

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

La porte finale doit démontrer :

```text
ACTIVE by default
explicit ACTIVE/RETIRED historical query
CURRENT isolation
deterministic lexical search
bounded pagination / limits
stable ordering
provider-neutral query contracts
backend-neutral query contracts
Memory == SQLite observable semantics
compact MORPHEUS context
provenance/evidence retained
structured warnings
no semantic-search dependency
no LLM dependency
no NEXUS dependency
```

---

# 3. Principes hérités

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
ACTIVE = vue publiée courante
RETIRED = historique explicite
Requirement persistence = versioned / snapshot-owned
Traceability = snapshot-scoped
trace(requirement) = bounded deterministic subgraph
Scenario != AcceptanceCriterion
```

M0 a déjà validé :

```text
lexical search deterministic = MVP
semantic search = NOT_REQUIRED_FOR_MVP
compact MORPHEUS context = yes
global ranking = NEXUS
multi-engine fusion = NEXUS
token-budget compression = NEXUS
```

---

# 4. Progression M5

```text
S1  ✅ find_requirements + pagination déterministe — PR #37 — ADR-0043 — 196/196 — MERGED
S2  ✅ projection métier requêtable snapshot-scoped — PR #38 — ADR-0044 — 202/202 — MERGED
S3  🚧 getters/lists déterministes — PR #39 — ADR-0045 proposée — gate attendu 210
S4  ⏳ get_change_context + query view trace
S5  ⏳ vues compactes + warnings/provenance + JSON déterministe
S6  ⏳ validation finale VALIDATION_M5.md
```

```text
M5 : 2 / 6 slices intégrés
```

---

# 5. M5-S1 — INTÉGRÉ

ADR : **ADR-0043 — Acceptée — M5**  
PR : **#37 — MERGED**  
Merge : `92b1321a0e23553641ea5dbe1f1c25c0acc874e3`

Contrats :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService

findActive(...)
findSnapshot(...)
```

Gate : **196/196 PASS**.

---

# 6. M5-S2 — INTÉGRÉ

ADR : **ADR-0044 — Acceptée — M5**  
PR : **#38 — MERGED**  
Merge : `3a39371518d9d327ea4cbee0994da65b218ec64c`

Contrats :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

Familles persistées :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence / Provenance
```

SQLite V007 normalisée sans payload JSON métier.  
Gate : **202/202 PASS**.

---

# 7. M5-S3 — IMPLÉMENTÉ / GATE EN ATTENTE

ADR : **ADR-0045 — Proposée — M5**  
PR : **#39 — Draft**  
Branche : `m5/deterministic-business-queries`

Contrats :

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Primitives :

```text
activeSpecification / snapshotSpecification
activeChange / snapshotChange
listActiveChanges / listSnapshotChanges
activeConstraints / snapshotConstraints
activeDesignDecisions / snapshotDesignDecisions
activeImplementationTasks / snapshotImplementationTasks
```

Sémantique :

```text
ACTIVE by default
snapshot explicit = ACTIVE/RETIRED only
no ACTIVE != entity not found
not-found explicit
published snapshot without S2 projection = error
stable ordering by domain identity
pagination after filtering + ordering
PageRequest reused from S1
offset >= 0
1 <= limit <= 100
provider-neutral
backend-neutral
no new persistence
no V008
```

`get_current_specification` est adressé par `SpecificationId` car un projet peut contenir plusieurs spécifications.

`AcceptanceCriterion` n'est pas exposé en S3 : aucune sémantique explicite n'existe dans le domaine et aucun `Scenario` n'est converti artificiellement.

Preuves ajoutées : **8 tests** :

```text
BusinessContentQueryContractTest         7 tests
BusinessContentQueryBackendParityTest    1 test
```

Ils couvrent ACTIVE, RETIRED, READY rejeté, not-found, absence d'ACTIVE, projection manquante, pagination/ordre, filtrage par ChangeId, parité Memory/SQLite, reopen SQLite et absence d'AcceptanceCriterion synthétique.

Baseline : `202/202 PASS`.  
Gate attendu : **210/210**.

---

# 8. M5-S4 — Contexte métier compact

Primitives :

```text
trace_requirement
get_change_context
```

`trace_requirement` réutilise M4. `get_change_context` agrège uniquement des faits MORPHEUS :

```text
change
requirement deltas / affected requirements
constraints
design decisions
tasks
traceability paths
external unresolved/broken refs
```

Aucune fusion NEXUS.

---

# 9. M5-S5 — Enveloppe compacte

Stabiliser :

```text
query metadata
snapshot metadata
pagination metadata
structured warnings
provenance/evidence
compact DTOs
stable deterministic JSON representation
```

Le JSON reste une vue d'exposition, jamais une payload métier générique de persistance.

---

# 10. M5-S6 — Validation finale

Créer :

```text
docs/VALIDATION_M5.md
```

Prouver :

```text
find_requirements lexical
pagination stable
CURRENT isolation
ACTIVE vs RETIRED
all planned getters/lists
trace_requirement
get_change_context
compact representation
warnings/provenance
Memory == SQLite
SQLite reopen
deterministic repeated results
no semantic/LLM/NEXUS dependency
```

---

# 11. Checklist bloquante avant M6

| Condition | État | Slice |
|---|---|---|
| recherche lexicale déterministe | ✅ | S1 |
| pagination/limites bornées | ✅ | S1 |
| ACTIVE/CURRENT isolation | ✅ | S1 |
| snapshot historique publié explicite | ✅ | S1 |
| Memory/SQLite requirement query | ✅ | S1 |
| SQLite reopen requirement query | ✅ | S1 |
| projection requêtable autres familles | ✅ | S2 |
| Memory/SQLite même contrat complet | ✅ | S2 |
| close/reopen SQLite familles métier | ✅ | S2 |
| V007 normalisée sans JSON métier | ✅ | S2 |
| getters/lists déterministes | 🚧 gate S3 | S3 |
| AcceptanceCriterion non inventé | 🚧 gate S3 | S3 |
| `trace_requirement` query view | ⬜ | S4 |
| `get_change_context` | ⬜ | S4 |
| compact DTOs | ⬜ | S5 |
| warnings structurés | ⬜ | S5 |
| provenance/evidence conservées | ⬜ | S5 |
| JSON déterministe | ⬜ | S5 |
| `VALIDATION_M5.md` | ⬜ | S6 |

---

# 12. Hors périmètre M5

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

# 13. Gouvernance

```text
1. branche dédiée depuis main exact
2. ADR proposée avant code si décision structurelle
3. PR Draft avant implémentation
4. tests contractuels ciblés
5. gate Windows .\mvnw.cmd clean test
6. ADR acceptée seulement après preuve
7. PR Ready seulement après preuve
8. merge seulement après signal explicite
9. issue #36 + roadmap mises à jour
```

**Prochaine porte : gate local M5-S3 attendu 210/210.**
