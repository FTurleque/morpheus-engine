# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 4/6 validés ; S4 Ready, S5 prochain après intégration**

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
M5-S3 merge = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73
M5-S3 gate  = 210/210 PASS
M5-S4 gate  = 217/217 PASS
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
compact context MORPHEUS = yes
global ranking = NEXUS
multi-engine fusion = NEXUS
token-budget compression = NEXUS
```

---

# 4. Progression M5

```text
S1  ✅ find_requirements + pagination déterministe — PR #37 — ADR-0043 — 196/196 — MERGED
S2  ✅ projection métier requêtable snapshot-scoped — PR #38 — ADR-0044 — 202/202 — MERGED
S3  ✅ getters/lists déterministes — PR #39 — ADR-0045 — 210/210 — MERGED
S4  ✅ trace_requirement query view + get_change_context — PR #40 — ADR-0046 — 217/217 — READY
S5  ⏳ vues compactes + warnings/provenance + JSON déterministe — PROCHAIN APRÈS MERGE S4
S6  ⏳ validation finale VALIDATION_M5.md
```

```text
M5 : 4 / 6 slices validés
```

---

# 5. M5-S1 — INTÉGRÉ

ADR : **ADR-0043 — Acceptée — M5**  
PR : **#37 — MERGED**  
Merge : `92b1321a0e23553641ea5dbe1f1c25c0acc874e3`  
Gate : **196/196 PASS**.

Contrats :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

---

# 6. M5-S2 — INTÉGRÉ

ADR : **ADR-0044 — Acceptée — M5**  
PR : **#38 — MERGED**  
Merge : `3a39371518d9d327ea4cbee0994da65b218ec64c`  
Gate : **202/202 PASS**.

Contrats :

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
```

SQLite V007 normalisée sans payload JSON métier.

---

# 7. M5-S3 — INTÉGRÉ

ADR : **ADR-0045 — Acceptée — M5**  
PR : **#39 — MERGED**  
Merge : `28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73`  
Gate : **210/210 PASS**.

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

---

# 8. M5-S4 — VALIDÉ TECHNIQUEMENT

ADR : **ADR-0046 — Acceptée — M5**  
PR : **#40 — Ready après gate**  
Branche : `m5/change-context-query`

Head complet testé :

```text
da1c0c53fdcf0e98b60cf7a46699bf014ee67091
```

Head code/test inclus :

```text
6df77f79feeaf92e10b9848c333b1b756c8af33c
```

Contrats :

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

`TraceRequirementQueryService` réutilise exactement `TraceRequirementService` M4.

`ChangeContextQueryService` agrège un seul snapshot publié :

```text
ChangeProposal
AFFECTS directs
Requirement CURRENT résolus
Constraint
DesignDecision
ImplementationTask
TraceabilitySubgraph borné
ExternalTraceabilityView unresolved/broken
```

Les `RequirementDelta` bruts ne sont pas exposés : ils ne sont pas persistés comme collection requêtable. Les liens `AFFECTS` bruts restent dans `ChangeContextResult`, y compris lorsque la cible n'a aucune occurrence CURRENT.

Sémantique validée :

```text
ACTIVE by default
snapshot explicit = ACTIVE/RETIRED only
no ACTIVE != change not found
CURRENT requirements only
PROPOSED never leaks
AFFECTS direct outgoing only
no title/key/text inference
stable domain/link ordering
bounded BIDIRECTIONAL traversal
relation filter shapes subgraph only
core business/AFFECTS facts remain available
external unresolved/broken visible
Memory == SQLite
SQLite reopen
no new persistence
no V008
no fuzzy / semantic search / LLM
no NEXUS ranking/fusion
```

Preuves ciblées :

```text
ChangeContextQueryContractTest    7/7 PASS
Architecture tests               90/90 PASS
```

Gate local Windows :

```text
Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      90 tests
-----------------------------------------------
TOTAL                                  217/217 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             16.688 s
Finished at                 2026-07-23T19:22:37+02:00
```

---

# 9. M5-S5 — PROCHAIN APRÈS MERGE S4

Objectif : stabiliser l'enveloppe compacte de query sans modifier les sources de vérité S1-S4.

Cibles :

```text
query metadata
snapshot metadata
pagination metadata
structured warnings
provenance/evidence
compact DTOs
stable deterministic JSON representation
```

Invariants :

```text
compact != lossy semantics
warnings structurés et ordonnés
provenance/evidence conservées
JSON déterministe
pas de payload JSON de persistance
pas de ranking global
pas de fusion multi-engine
pas de compression par budget de tokens
pas de dépendance NEXUS / LLM
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
| getters/lists déterministes | ✅ | S3 |
| AcceptanceCriterion non inventé | ✅ | S3 |
| Memory/SQLite business query parity | ✅ | S3 |
| SQLite reopen business queries | ✅ | S3 |
| `trace_requirement` query view | ✅ | S4 |
| `get_change_context` | ✅ | S4 |
| AFFECTS cassé conservé | ✅ | S4 |
| external unresolved/broken dans change context | ✅ | S4 |
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

**Prochaine ligne active après merge S4 : M5-S5 — vues compactes, warnings/provenance et JSON déterministe.**
