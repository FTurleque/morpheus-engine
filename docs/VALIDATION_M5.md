# Validation M5 — Requêtes déterministes et contexte compact

Statut : **VALIDÉ — 6/6 slices — 227/227 PASS ; intégration S6 en attente du merge #42**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il exposer des requêtes métier déterministes, snapshot-cohérentes et bornées, puis produire un contexte compact avec provenance et warnings sans dépendre d'un moteur sémantique, d'un LLM ou de NEXUS ?**

**Réponse : OUI.**

M5 démontre des requêtes métier stables, déterministes, provider/backend-neutral et snapshot-cohérentes, avec recherche lexicale bornée, historique publié explicite, traçabilité/context agrégés, vues compactes, provenance/evidence, warnings structurés et JSON canonique déterministe.

Aucune dépendance obligatoire à un moteur sémantique, à un LLM, à NEXUS, à MINOS ou à un backend graphe n'est introduite.

---

# 1. Progression finale M5

| Slice | Contenu | PR | ADR | Gate | État |
|---|---|---|---|---|---|
| S1 | `find_requirements` + pagination | #37 | ADR-0043 | 196/196 | MERGED |
| S2 | projection métier snapshot-scoped | #38 | ADR-0044 | 202/202 | MERGED |
| S3 | getters/listes déterministes | #39 | ADR-0045 | 210/210 | MERGED |
| S4 | `trace_requirement` + `get_change_context` | #40 | ADR-0046 | 217/217 | MERGED |
| S5 | vues compactes + warnings/provenance + JSON canonique | #41 | ADR-0047 | 227/227 | MERGED |
| S6 | validation finale | #42 | — | **227/227** | **VALIDÉ / READY après finalisation documentaire** |

Merges déjà intégrés :

```text
M5-S1 = 92b1321a0e23553641ea5dbe1f1c25c0acc874e3
M5-S2 = 3a39371518d9d327ea4cbee0994da65b218ec64c
M5-S3 = 28c32ea2ede7b9144eb10a2a7fb60b0df44f2a73
M5-S4 = a1be0820f16c077a33047eefb1e0deac0d5ab680
M5-S5 = 330c7831dfe5261247fef98eef850d82c8f0e7c9
```

L'intégration de S6 dans `main` reste soumise au merge explicite de la PR #42.

---

# 2. Recherche Requirement déterministe

Contrats :

```text
PageRequest
RequirementSearchQuery
RequirementSearchPage
RequirementQueryService
```

Primitives :

```text
findActive(...)
findSnapshot(...)
```

Sémantique validée :

```text
ACTIVE par défaut
ACTIVE / RETIRED uniquement pour snapshot explicite
CURRENT only
PROPOSED never leaks into CURRENT
recherche lexicale key/title/statement
normalisation Locale.ROOT
AND entre termes
aucun fuzzy matching
ordre stable par RequirementId
pagination après filtrage + tri
offset >= 0
1 <= limit <= 100
Memory == SQLite
SQLite reopen
```

S5 renforce le contrat : `RequirementSearchPage` conserve la `RequirementSearchQuery` normalisée ayant réellement produit la page.

---

# 3. Projection métier snapshot-scoped

Contrats :

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

Invariants :

```text
KnowledgeSnapshotId ownership explicite
SpecificationVersionId binding explicite
DomainIdentity stable
aucun faux EntityVersionId / TemporalState
projection immuable par snapshot
idempotence exacte
relations Change -> Constraint/Decision/Task validées
Scenario.requirementId conservé
Requirement reste dans VersionedRequirementStore
Memory == SQLite
SQLite reopen
```

SQLite V007 reste normalisée sans payload JSON métier générique.

---

# 4. Getters et listes métier

Contrat principal :

```text
BusinessContentQueryService
SnapshotItemResult<T>
SnapshotPage<T>
```

Primitives validées :

```text
activeSpecification / snapshotSpecification
activeChange / snapshotChange
listActiveChanges / listSnapshotChanges
activeConstraints / snapshotConstraints
activeDesignDecisions / snapshotDesignDecisions
activeImplementationTasks / snapshotImplementationTasks
```

Règles :

```text
ACTIVE by default
ACTIVE / RETIRED explicite uniquement
absence d'ACTIVE != entity not found
not-found explicite
snapshot publié sans projection = erreur
tri stable par DomainIdentity
pagination bornée
provider-neutral
backend-neutral
```

`Scenario != AcceptanceCriterion` reste un invariant : aucun `AcceptanceCriterion` n'est inventé sans sémantique de source explicite.

---

# 5. `trace_requirement` et `get_change_context`

Contrats :

```text
TraceRequirementQueryService
ChangeContextQueryService
ChangeContextResult
```

`trace_requirement` réutilise exactement la traçabilité M4.

`get_change_context` agrège dans un seul snapshot publié :

```text
ChangeProposal
AFFECTS directs
Requirement CURRENT résolus
Constraint
DesignDecision
ImplementationTask
TraceabilitySubgraph borné
ExternalTraceabilityView
```

Règles :

```text
ACTIVE par défaut
ACTIVE / RETIRED explicite
CURRENT requirements uniquement
AFFECTS directs seulement
aucune inférence par titre/key/texte
cible AFFECTS cassée conservée
traversal BIDIRECTIONAL borné
cycle-safe
external unresolved/stale/broken visible
Memory == SQLite
SQLite reopen
```

Les `RequirementDelta` bruts ne sont pas présentés comme requêtables puisqu'ils n'existent pas comme collection persistée dédiée.

---

# 6. Vues compactes

Contrats :

```text
CompactQueryTypes
CompactRequirementSearchView
CompactTraceRequirementView
CompactChangeContextView
CompactQueryViewService
```

Les vues conservent explicitement :

```text
schemaVersion = 1
snapshot metadata
pagination metadata
RequirementId
EntityVersionId
SpecificationVersionId
TemporalState
provenance
evidence
trace nodes / links
external references
warnings
```

La compacité ne détruit donc pas :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
```

---

# 7. Provenance et evidence

La provenance compacte conserve :

```text
providerId
providerVersion?
source
externalId?
sourceRevision?
evidenceId
```

Les evidence exposées sont :

```text
référencées uniquement
dédupliquées
triées par EvidenceId
```

Une evidence référencée mais absente ne masque pas la donnée métier : elle produit `EVIDENCE_NOT_FOUND`.

---

# 8. Warnings structurés

Catalogue M5 :

```text
CHANGE_NOT_FOUND
AFFECTED_REQUIREMENT_UNRESOLVED
EXTERNAL_REFERENCE_UNVALIDATED
EXTERNAL_REFERENCE_UNRESOLVED
EXTERNAL_REFERENCE_STALE
EXTERNAL_REFERENCE_BROKEN
EVIDENCE_NOT_FOUND
```

Format :

```text
code
severity = WARNING
message
details
```

Les warnings sont dérivés exclusivement de faits stockés/observables. Une référence externe `RESOLVED` ne produit aucun warning.

---

# 9. JSON canonique déterministe

Contrat : `CanonicalJsonSerializer`.

Règles :

```text
record fields = ordre de déclaration
map keys = ordre lexicographique
map values null supportées
Optional.empty = null
collections = ordre DTO canonique
enum = name()
nombres finis uniquement
échappement JSON strict
même DTO -> même String
même String -> mêmes octets UTF-8
```

Sont rejetés explicitement :

```text
type non supporté
clé Map non String
NaN
Infinity
```

Aucune dépendance JSON tierce n'est ajoutée et aucun objet métier/store n'est sérialisé directement.

---

# 10. Déterminisme et parité backend

Les preuves cumulées démontrent :

```text
Memory == SQLite
SQLite close/reopen
stable ordering
pagination stable
snapshot isolation
CURRENT isolation
bounded traversal
cycle safety
byte-identical canonical JSON
```

Le comportement observable ne dépend pas du backend de référence.

---

# 11. Frontières de responsabilité

MORPHEUS fournit :

```text
facts métier
queries déterministes
pagination bornée
trace/context snapshot-scoped
provenance/evidence
warnings structurés
DTO compacts
JSON canonique
```

MORPHEUS ne prend pas en charge :

```text
ranking global
fusion multi-engine
compression selon budget de tokens
sélection globale inter-engines
semantic search / embeddings
```

Ces responsabilités restent NEXUS.

---

# 12. Dépendances explicitement non requises

M5 ne dépend pas obligatoirement de :

```text
LLM
semantic search
embeddings
NEXUS
MINOS
backend graphe
bibliothèque JSON tierce
```

MORPHEUS reste autonome.

---

# 13. Gate final M5

Commande exécutée :

```text
Windows : .\mvnw.cmd clean test
```

Head S6 testé :

```text
a91c925d32f3d6ee1901aa3495d37326bf7518ca
```

Preuve finale :

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

Warnings connus non bloquants uniquement :

```text
Xerial SQLite / JDK restricted native access
SLF4J NOP dans les tests d'architecture
```

S6 est une slice docs-only : aucun code de production, test, store, migration, dépendance ou contrat JSON n'a été modifié pour obtenir ce gate.

---

# 14. Décision de sortie

```text
Question de sortie M5 = OUI
M5 = VALIDÉ
6/6 slices = VALIDÉES
227/227 = PASS
M6 = AUTORISÉ
```

Intégration repository :

```text
S1-S5 = INTÉGRÉS
S6 = VALIDÉ / PR #42 Ready après finalisation documentaire
M5 = sera entièrement INTÉGRÉ après merge explicite de #42
```

La distinction est volontaire : **validation technique != merge GitHub**.
