# Validation M4 — Traçabilité typée, explicable et traversable

Statut : **CANDIDATE — gate final M4-S6 en attente**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

Réponse finale : **à confirmer par le gate S6**.

---

# 1. Baseline validée avant S6

```text
M4-S1 PR #28 / ADR-0037 / 155/155 PASS
M4-S2 PR #29 / ADR-0038 / 160/160 PASS
M4-S3 PR #31 / ADR-0039 / 167/167 PASS
M4-S4 PR #32 / ADR-0040 / 174/174 PASS
M4-S5 PR #33 / ADR-0041 / 184/184 PASS
```

S5 merge :

```text
e25aebf0479dfa9d1f146df4d2af0f072b551d39
```

---

# 2. Modèle de lien

M4 stabilise un `TraceabilityLink` first-class :

```text
TraceabilityLinkId
source: TraceabilityEntityRef
relationType: TraceabilityRelationType
target: TraceabilityEntityRef
origin
resolution
confidence?
evidenceIds
observedAt
```

Invariants :

```text
TraceabilityLinkId != hash(source,type,target)
relation type != origin != resolution
evidence obligatoire
confidence obligatoire pour HEURISTIC
direction canonique
inverse = vue de requête, pas seconde preuve
```

Taxonomie MVP contrôlée :

```text
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
DEPENDS_ON
AFFECTS
DECIDED_BY
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

---

# 3. Persistance snapshot-scoped

V005 :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

Contrat :

```text
TraceabilityStore
MemoryTraceabilityStore
SqliteTraceabilityStore
```

Invariants :

```text
link definition != snapshot membership
same link id + different definition = collision
snapshot A links != snapshot B links
outgoing/incoming déterministes
Memory == SQLite
```

---

# 4. Dérivation déterministe

Relations de production validées :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS
```

Invariants :

```text
faits structurels uniquement
aucun fuzzy matching
aucun matching titre/statement/path
aucun LLM / embedding
aucun TraceabilityLinkId.generate() caché
aucun hash d'arête comme identité
identity resolver explicite
evidence source conservée
```

---

# 5. Traversal et chemins

API :

```text
direct(...)
traverse(...)
findPath(...)
```

Directions :

```text
OUTGOING
INCOMING
BIDIRECTIONAL
```

Invariants :

```text
maxDepth > 0
BFS borné
cycle-safe
ordre déterministe
relation filters explicites
shortest path déterministe
path conserve l'arête persistée réelle + sens de parcours
traversal != transitivity
A -> B -> C != arête synthétique A -> C
aucun backend graphe requis
```

---

# 6. Références externes

V006 :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

Deux axes distincts :

```text
TraceabilityResolutionState
ExternalReferenceResolutionState
```

Vue externe :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

Invariants :

```text
UNRESOLVED reste visible
STALE reste explicable
BROKEN_REFERENCE reste visible
resolver externe != mutation du TraceabilityLink canonique
MINOS indisponible != MORPHEUS indisponible
aucun couplage compile-time obligatoire à MINOS
close/reopen SQLite conserve coordonnées, attributs, provenance et historique
```

---

# 7. Porte finale M4-S6

API candidate :

```text
TraceRequirementService.traceActive(...)
TraceRequirementService.traceSnapshot(...)
```

Résultat :

```text
TraceRequirementResult
  snapshot
  requirement CURRENT
  subgraph
  externalLinks
```

La porte doit démontrer :

```text
Requirement <- REFINES - Scenario
Requirement <- AFFECTS - Change
Constraint - CONSTRAINS -> Change
Change - DECIDED_BY -> DesignDecision
Requirement - LINKS_TO_CODE -> ExternalReference
```

et :

```text
Memory == SQLite
close/reopen SQLite
profondeur >= 3
incoming + outgoing
cycle réel
filtres
UNRESOLVED
BROKEN_REFERENCE
provenance/evidence
RETIRED explicite
ACTIVE isolation
READY/BUILDING non observables
```

Les **5 tests S6** portent le total attendu de `184` à **189 tests**.

---

# 8. Gate final

Commande obligatoire :

```text
Windows : .\mvnw.cmd clean test
```

Attendu avant acceptation :

```text
TraceRequirementFinalValidationTest  5/5 PASS
TOTAL                              189/189 PASS
Failures                            0
Errors                              0
Skipped                             0
BUILD SUCCESS
```

La preuve exacte, le timestamp, le total réel et la réponse finale à la question de sortie seront inscrits uniquement après exécution locale verte.

---

# 9. Décision de sortie

Tant que le gate S6 n'est pas vert :

```text
M4 != validé final
M5 != autorisé
ADR-0042 reste proposée
PR S6 reste Draft
issue #27 reste ouverte
```
