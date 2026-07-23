# M5 — Plan d'exécution détaillé

Statut : **M5 actif — 0/6 ; S1 en cours**

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

# 6. Plan M5 — 6 slices

## M5-S1 — `find_requirements` et pagination déterministe

Objectif : livrer la première primitive M5 sur la seule famille déjà persistée complètement.

```text
RequirementQueryService
RequirementSearchQuery
RequirementSearchPage
PageRequest
```

Portée :

```text
ACTIVE current requirements by default
explicit ACTIVE/RETIRED snapshot query
CURRENT occurrences only
lexical query over key/title/statement
case-insensitive deterministic normalization
AND semantics across lexical terms
stable sort by RequirementId
bounded page size
stable offset pagination
explicit totalMatches
provenance remains in Requirement content
no fuzzy matching
no semantic ranking
no LLM/embedding
```

Aucune migration SQLite requise en S1.

## M5-S2 — Projection métier requêtable complète

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

## M5-S3 — Getters et listes déterministes

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

## M5-S4 — Contexte métier compact

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

## M5-S5 — Enveloppe compacte, warnings et sérialisation déterministe

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

## M5-S6 — Validation finale

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

# 7. Checklist bloquante avant M6

| Condition | État | Slice |
|---|---|---|
| recherche lexicale déterministe | ⬜ | S1 |
| pagination/limites bornées | ⬜ | S1 |
| ACTIVE/CURRENT isolation | ⬜ | S1 |
| snapshot historique publié explicite | ⬜ | S1 |
| projection requêtable autres familles | ⬜ | S2 |
| Memory/SQLite même contrat complet | ⬜ | S2 |
| close/reopen SQLite | ⬜ | S2 |
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

# 8. Hors périmètre M5

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

# 9. Gouvernance

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

---

# 10. NOW — M5-S1

```text
find_requirements
ACTIVE by default
ACTIVE/RETIRED explicit snapshot variant
CURRENT only
lexical key/title/statement
AND terms
case-insensitive
stable RequirementId order
bounded offset pagination
no migration
Memory == SQLite through VersionedRequirementStore behavior
```

ADR candidate : **ADR-0043 — Contrat de recherche lexicale et pagination déterministe des requirements**.
