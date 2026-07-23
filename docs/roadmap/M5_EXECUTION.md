# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 1/6 validé ; S1 Ready, S2 prochain après intégration**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M5.

---

# 1. Baseline d'entrée

```text
C0 à M4 ✅ validés et intégrés
M4 final = 6/6
M4 gate  = 189/189 PASS
M4 final code merge = ac317eb63bbe0edb854c04660c5c143ba46e0c43
M4 final docs merge = d4a4c9f4816e42a8629d2f41cfe22703f53f210a
```

M5 démarre depuis :

```text
main = d4a4c9f4816e42a8629d2f41cfe22703f53f210a
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

# 3. Héritage M0

M0 a validé :

```text
E10 lexical search  = PASS
E13 compact context = PASS
```

Décisions conservées :

```text
lexical search deterministic = MVP
semantic search = NOT_REQUIRED_FOR_MVP
compact MORPHEUS context = yes
global ranking = NEXUS
multi-engine fusion = NEXUS
token-budget compression = NEXUS
```

M5 transforme ces preuves expérimentales en contrats Java de production.

---

# 4. Héritage M3/M4

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
ACTIVE est la vue courante publiée
RETIRED est historique explicite
published history = RETIRED* -> ACTIVE
Requirement persistence = versioned / snapshot-owned
Traceability = snapshot-scoped
trace(requirement) = bounded deterministic subgraph
```

Les requêtes M5 ne doivent jamais contourner ces frontières.

---

# 5. Gap de persistance à traiter

`NormalizedProjectContent` contient :

```text
Specification
Requirement
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
```

ADR-0034 a volontairement validé `Requirement` comme **premier vertical slice** de persistance métier versionnée et impose que les autres familles réutilisent le même pattern après validation.

Donc M5 ne doit pas prétendre que `get_change`, `get_constraints`, `get_design_decisions` ou `get_implementation_tasks` sont persistants avant S2.

---

# 6. Progression M5

```text
S1  ✅ find_requirements + pagination déterministe — PR #37 — ADR-0043 — 196/196
S2  ⏳ projection métier requêtable des autres familles — prochain après merge S1
S3  ⏳ getters/lists déterministes
S4  ⏳ get_current_specification + get_change_context + query view trace
S5  ⏳ vues compactes + warnings/provenance + JSON déterministe
S6  ⏳ validation finale VALIDATION_M5.md
```

Progression :

```text
M5 : [███░░░░░░░░░░░░░░░░] 1 / 6 slices validés
```

---

# 7. M5-S1 — VALIDÉ TECHNIQUEMENT : `find_requirements`

ADR : **ADR-0043 — Acceptée — M5**.

PR : **#37 — Ready après finalisation documentaire ; merge en attente de signal explicite**.

Head de code testé :

```text
e81525403cd413df8db2d4df3e1d0aa9f22dbf4b
```

Application :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

API :

```text
findActive(projectId, query, pageRequest)
findSnapshot(snapshotId, query, pageRequest)
```

Sémantique validée :

```text
ACTIVE by default
ACTIVE/RETIRED explicit snapshot
CURRENT only
PROPOSED never leaks into CURRENT
lexical corpus = key + title + statement
Locale.ROOT lowercase
Unicode-aware strip / whitespace split
AND semantics across terms
substring deterministic matching
empty query = all CURRENT
stable RequirementId order
pagination after filter + sort
offset >= 0
1 <= limit <= 100
totalMatches + hasMore
```

Frontières :

```text
no semantic search
no fuzzy matching
no stemming
no LLM / embeddings
no provider-specific query
no SQLite FTS dependency
no new migration
no query of non-Requirement families yet
```

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

RequirementQueryContractTest             7/7 PASS
LayerDependencyTest                      2/2 PASS

Domain                                  21 tests
Application                             66 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      69 tests
-----------------------------------------------
TOTAL                                  196/196 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
Total time                             16.263 s
```

Gate terminé le **23 juillet 2026 à 16:20:02 +02:00**.

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

---

# 8. M5-S2 — PROCHAIN : Projection métier requêtable complète

Étendre le pattern ADR-0034 aux familles nécessaires aux requêtes :

```text
Specification
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence/provenance nécessaires
```

Invariants :

```text
snapshot/version ownership explicite
DomainIdentity stable
EntityVersionId distinct lorsque versionné
CURRENT isolation
Memory == SQLite
close/reopen SQLite
no generic business JSON payload
```

S2 ne doit pas créer de getter public incomplet : il établit d'abord la source de vérité persistante sur laquelle S3 s'appuiera.

---

# 9. M5-S3 — Getters et listes déterministes

Primitives :

```text
get_current_specification
get_change
list_changes
get_constraints
get_acceptance_criteria (uniquement si sémantique explicite disponible)
get_design_decisions
get_implementation_tasks
```

Règles :

```text
ACTIVE by default
published snapshot explicit variant where meaningful
stable ordering
bounded lists
not-found explicit
provider-neutral
```

---

# 10. M5-S4 — Contexte métier compact

Primitives :

```text
trace_requirement
get_change_context
```

`trace_requirement` réutilise M4 ; M5 fournit une vue de query compacte et stable.

`get_change_context` agrège uniquement des faits MORPHEUS :

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

# 11. M5-S5 — Enveloppe compacte, warnings et sérialisation déterministe

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

Le JSON est une vue d'exposition ; il ne devient pas une payload métier générique de persistance.

---

# 12. M5-S6 — Validation finale

Créer :

```text
docs/VALIDATION_M5.md
```

Prouver au minimum :

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

# 13. Checklist bloquante avant M6

| Condition | État | Slice |
|---|---|---|
| recherche lexicale déterministe | ✅ | S1 |
| pagination/limites bornées | ✅ | S1 |
| ACTIVE/CURRENT isolation | ✅ | S1 |
| snapshot historique publié explicite | ✅ | S1 |
| Memory/SQLite même résultat requirement query | ✅ | S1 |
| SQLite reopen conserve requirement query | ✅ | S1 |
| projection requêtable autres familles | ⬜ | S2 |
| Memory/SQLite même contrat complet | ⬜ | S2 |
| close/reopen SQLite familles métier | ⬜ | S2 |
| getters/lists déterministes | ⬜ | S3 |
| AcceptanceCriterion non inventé | ⬜ | S3 |
| `trace_requirement` query view | ⬜ | S4 |
| `get_change_context` | ⬜ | S4 |
| compact DTOs | ⬜ | S5 |
| warnings structurés | ⬜ | S5 |
| provenance/evidence conservées | ⬜ | S5 |
| JSON déterministe | ⬜ | S5 |
| `VALIDATION_M5.md` | ⬜ | S6 |

---

# 14. Hors périmètre M5

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

# 15. Gouvernance

Après chaque slice :

```text
1. branch dédiée depuis main exact
2. ADR proposée avant code si décision structurelle
3. PR Draft avant implémentation
4. tests contractuels ciblés
5. gate Windows .\mvnw.cmd clean test
6. ADR acceptée seulement après preuve
7. PR Ready seulement après preuve
8. merge seulement après signal explicite
9. issue #36 + roadmap mises à jour
```

Prochaine ligne active après merge S1 : **M5-S2 — projection métier requêtable complète**.
