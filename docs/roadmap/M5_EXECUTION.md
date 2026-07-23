# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 5/6 validés ; S5 Ready, S6 prochain après intégration**

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
M5-S5 gate  = 227/227 PASS
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
S5  ✅ vues compactes + warnings/provenance + JSON déterministe — PR #41 — ADR-0047 — 227/227 — READY
S6  ⏳ validation finale VALIDATION_M5.md — PROCHAIN APRÈS MERGE S5
```

```text
M5 : 5 / 6 slices validés
```

---

# 5. M5-S1 — INTÉGRÉ

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

# 6. M5-S2 — INTÉGRÉ

```text
SnapshotBusinessContent
SnapshotBusinessContentStore
MemorySnapshotBusinessContentStore
SqliteSnapshotBusinessContentStore
SQLite V007
```

Projection snapshot-scoped de `Specification`, `Scenario`, `ChangeProposal`, `Constraint`, `DesignDecision`, `ImplementationTask`, `Evidence / Provenance`.

Gate : **202/202 PASS**.  
ADR : **ADR-0044 — Acceptée — M5**.  
PR #38 : **MERGED**.

---

# 7. M5-S3 — INTÉGRÉ

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Primitives : specification, change, list changes, constraints, design decisions et implementation tasks sur ACTIVE par défaut ou ACTIVE/RETIRED explicite. Aucun `AcceptanceCriterion` synthétique.

Gate : **210/210 PASS**.  
ADR : **ADR-0045 — Acceptée — M5**.  
PR #39 : **MERGED**.

---

# 8. M5-S4 — INTÉGRÉ

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

# 9. M5-S5 — VALIDÉ TECHNIQUEMENT

ADR : **ADR-0047 — Acceptée — M5**  
PR : **#41 — Ready après gate**  
Branche : `m5/compact-query-views`

Head complet testé :

```text
77df15e4ea5aaa93722b25d0f18f7c38214b0d9e
```

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

Évolution contrôlée S1 : `RequirementSearchPage` conserve désormais la `RequirementSearchQuery` normalisée ayant produit la page.

Invariants validés :

```text
schemaVersion = 1
snapshot metadata + createdAt
pagination metadata
RequirementId != EntityVersionId visible
SpecificationVersionId / TemporalState explicites
stable domain/link ordering
provenance conservée
evidence référencée uniquement, triée/dédupliquée
warnings code/severity/details
WarningView severity = WARNING
warnings issus uniquement de faits observables
resolved external -> aucun warning
canonical deterministic JSON
strict JSON escaping
map keys lexicographic
map null values supported
Optional.empty = null
no third-party JSON dependency
no pom change
no store adapter change
no V008 / no migration
no NEXUS ranking/fusion/token-budget compression
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

Preuves ciblées :

```text
CompactQueryViewContractTest           6 tests
CanonicalJsonSerializerTest            3 tests
RequirementQueryMetadataRetentionTest  1 test
-----------------------------------------------
TOTAL S5                              10 tests
```

Gate local Windows :

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
Total time                             19.928 s
Finished at                 2026-07-23T20:06:21+02:00
```

---

# 10. M5-S6 — PROCHAIN APRÈS MERGE S5

Créer :

```text
docs/VALIDATION_M5.md
```

S6 ne doit pas réinventer de nouvelle capacité métier. Il doit consolider les preuves S1-S5 et répondre explicitement à la question de sortie M5.

Preuves finales :

```text
find_requirements lexical + pagination
CURRENT isolation
ACTIVE vs RETIRED
getters/lists métier
Scenario != AcceptanceCriterion
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

Si aucun gap réel n'est découvert, S6 est une slice de **validation/documentation + gate final**, sans nouvelle architecture.

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
| compact DTOs | ✅ | S5 |
| warnings structurés | ✅ | S5 |
| provenance/evidence conservées | ✅ | S5 |
| JSON déterministe | ✅ | S5 |
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

**Prochaine ligne active après merge S5 : M5-S6 — validation finale de M5.**
