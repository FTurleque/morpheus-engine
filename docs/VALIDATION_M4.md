# Validation M4 — Traçabilité typée, explicable et traversable

Statut : **VALIDÉ — 6/6 slices — 189/189 PASS**

Date : 23 juillet 2026

## Question de sortie

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

**Réponse : OUI.**

M4 démontre une traçabilité first-class, snapshot-scoped, provider/backend-neutral, déterministe et explicable, avec traversal borné, historique publié, références externes non résolues ou cassées conservées, et une porte finale `trace(requirement)` fonctionnant de manière équivalente sur Memory et SQLite.

---

# 1. Progression finale M4

| Slice | Contenu | PR | ADR | Gate |
|---|---|---|---|---|
| S1 | domaine `TraceabilityLink` + taxonomie contrôlée | #28 | ADR-0037 | 155/155 |
| S2 | persistance snapshot-scoped Memory + SQLite | #29 | ADR-0038 | 160/160 |
| S3 | dérivation déterministe depuis modèle normalisé | #31 | ADR-0039 | 167/167 |
| S4 | direct / inverse / traversal / path | #32 | ADR-0040 | 174/174 |
| S5 | références externes / unresolved / broken-reference | #33 | ADR-0041 | 184/184 |
| S6 | validation finale `trace(requirement)` | #34 | ADR-0042 | 189/189 |

Baselines intégrées avant S6 :

```text
M4-S1 merge = 07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f
M4-S2 merge = 32694f2c74aa9ce4248f9eea907d85460de93eff
M4-S3 merge = 4b3bb5c79e65b8f1501b9949b49f4940294c4312
M4-S4 merge = cafbc8e61a4af2ed204cd6fc24dcdd262f6ed9e4
M4-S5 merge = e25aebf0479dfa9d1f146df4d2af0f072b551d39
```

Le merge final S6 est inscrit après intégration de la PR #34.

---

# 2. Modèle de lien stabilisé

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

SQLite V005 :

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
inverse query != seconde arête persistée
Memory == SQLite
```

---

# 4. Dérivation déterministe

Relations structurelles validées :

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

SQLite V006 :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

Deux axes restent distincts :

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

# 7. Porte finale `trace(requirement)`

API validée :

```text
TraceRequirementService.traceActive(...)
TraceRequirementService.traceSnapshot(...)
```

Résultat :

```text
TraceRequirementResult
  KnowledgeSnapshotMetadata snapshot
  RequirementVersionRecord requirement
  TraceabilitySubgraph subgraph
  ExternalTraceabilityView[] externalLinks
```

Règles :

```text
traceActive -> ACTIVE uniquement
traceSnapshot -> ACTIVE ou RETIRED uniquement
BUILDING / VALIDATING / READY / FAILED rejetés
CURRENT requirement obligatoire dans le snapshot adressé
racine = REQUIREMENT + RequirementId.value
BIDIRECTIONAL
maxDepth > 0
relation filters explicites
external enrichment != mutation des arêtes
```

Le scénario final couvre simultanément :

```text
Scenario -> Requirement               REFINES
Change -> Requirement                 AFFECTS
Constraint -> Change                  CONSTRAINS
Change -> DesignDecision              DECIDED_BY
DesignDecision -> Specification       RELATED_TO   (profondeur 3)
DesignDecision -> Change              RELATED_TO   (cycle réel)
Requirement -> ExternalReference      LINKS_TO_CODE / UNRESOLVED
Requirement -> ExternalReference      LINKS_TO_TEST / BROKEN_REFERENCE
```

Il démontre également :

```text
incoming + outgoing
filtres
provenance/evidence conservées
RETIRED explicite
ACTIVE isolation
Memory == SQLite
close/reopen SQLite
```

---

# 8. Gate final M4

Commande exécutée :

```text
Windows : .\mvnw.cmd clean test
```

Preuve :

```text
TraceRequirementFinalValidationTest       5/5 PASS
LayerDependencyTest                       2/2 PASS

Domain                                   21 tests
Application                              66 tests
OpenSpec provider                        26 tests
Synthetic provider                        7 tests
SQLite store                              7 tests
Architecture tests                       62 tests
------------------------------------------------
TOTAL                                   189/189 PASS
Failures                                  0
Errors                                    0
Skipped                                   0
BUILD SUCCESS
Total time                              27.573 s
```

Gate terminé le **23 juillet 2026 à 14:57:23 +02:00**.

Head de code testé :

```text
d46b66b5c5c22baabcfe8cfcb53a2da2eff68782
```

Warnings connus non bloquants uniquement :

```text
Xerial SQLite / JDK native-access
SLF4J NOP dans les tests d'architecture
```

---

# 9. Décision de sortie

```text
M4 = VALIDÉ
6/6 slices = VALIDÉS
189/189 = PASS
M5 = AUTORISÉ
```

La question de sortie M4 reçoit une réponse positive sur les deux backends de référence sans dépendance à un backend graphe.

La PR #34 peut être intégrée sous le signal explicite utilisateur déjà donné : **« merge et finalise M4 »**.
