# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 4/6 intégrés ; S5 implémenté, gate en attente**

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
M5-S4 merge = a1be0820f16c077a33047eefb1e0deac0d5ab680
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
canonical deterministic JSON
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
S4  ✅ trace_requirement query view + get_change_context — PR #40 — ADR-0046 — 217/217 — MERGED
S5  🚧 vues compactes + warnings/provenance + JSON déterministe — PR #41 — ADR-0047 proposée — gate attendu 227
S6  ⏳ validation finale VALIDATION_M5.md
```

```text
M5 : 4 / 6 slices intégrés
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

Sémantique : ACTIVE par défaut, ACTIVE/RETIRED explicite, CURRENT only, recherche lexicale déterministe, pagination bornée, Memory == SQLite, SQLite reopen.

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

Primitives : specification, change, list changes, constraints, design decisions et implementation tasks sur ACTIVE par défaut ou ACTIVE/RETIRED explicite. Aucun `AcceptanceCriterion` synthétique.

---

# 8. M5-S4 — INTÉGRÉ

ADR : **ADR-0046 — Acceptée — M5**  
PR : **#40 — MERGED**  
Merge : `a1be0820f16c077a33047eefb1e0deac0d5ab680`  
Gate : **217/217 PASS**.

Contrats :

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

Sémantique validée :

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

---

# 9. M5-S5 — IMPLÉMENTÉ / GATE EN ATTENTE

ADR : **ADR-0047 — Proposée — M5**  
PR : **#41 — Draft**  
Branche : `m5/compact-query-views`

Objectif : stabiliser une vue d'exposition compacte sans modifier les sources de vérité S1-S4.

Contrats ajoutés :

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
CompactWarningCode
CanonicalJsonSerializer
```

Vues couvertes :

```text
find_requirements
trace_requirement
get_change_context
```

`RequirementSearchPage` conserve désormais la `RequirementSearchQuery` normalisée qui a produit la page ; le constructeur historique reste compatible en utilisant `RequirementSearchQuery.all()`.

Invariants S5 :

```text
schemaVersion = 1
snapshot metadata explicite
createdAt réel du KnowledgeSnapshot
pagination metadata conservée
RequirementId != EntityVersionId visible
SpecificationVersionId + TemporalState explicites
stable domain/link ordering
provenance conservée
evidence référencée uniquement, dédupliquée et triée
warnings code/severity/details
warnings uniquement issus de faits observables
WarningView severity = WARNING
resolved external -> aucun warning
same DTO -> byte-identical JSON
record fields = ordre de déclaration
map keys = ordre lexicographique
Optional.empty = null
strict JSON escaping
no third-party JSON dependency
no new persistence
no V008
no ranking/fusion/token-budget compression NEXUS
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

Preuves ajoutées :

```text
CompactQueryViewContractTest           6 tests
CanonicalJsonSerializerTest            3 tests
RequirementQueryMetadataRetentionTest  1 test
-----------------------------------------------
TOTAL S5                              10 tests
```

Baseline : **217/217 PASS**.  
Gate attendu : **227/227**, dont **100 tests d'architecture** attendus.

Aucune modification de `pom.xml`, aucun adapter de store et aucune migration SQLite dans le diff S5.

---

# 10. M5-S6 — PROCHAIN APRÈS MERGE S5

Créer :

```text
docs/VALIDATION_M5.md
```

Prouver la question de sortie complète :

```text
find_requirements lexical + pagination
CURRENT isolation
ACTIVE vs RETIRED
getters/lists métier
trace_requirement
get_change_context
compact views
structured warnings
provenance/evidence
canonical deterministic JSON
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
| compact DTOs | 🚧 gate S5 | S5 |
| warnings structurés | 🚧 gate S5 | S5 |
| provenance/evidence conservées | 🚧 gate S5 | S5 |
| JSON déterministe | 🚧 gate S5 | S5 |
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

**Prochaine porte : gate local M5-S5 attendu 227/227.**
