# M4 — Plan d'exécution détaillé

Statut : **M4 VALIDÉ — 6/6 slices — 189/189 PASS — M5 autorisé**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et constitue la trace d'exécution finale de M4.

---

# 1. Position finale

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     ✅ validé — 94/94
M3     ✅ validé — 6/6 — 147/147
M4     ✅ validé — 6/6 — 189/189
  S1   ✅ domaine TraceabilityLink + taxonomie contrôlée — PR #28 — ADR-0037 — 155/155
  S2   ✅ persistance snapshot-scoped Memory + SQLite — PR #29 — ADR-0038 — 160/160
  S3   ✅ dérivation déterministe depuis modèle normalisé — PR #31 — ADR-0039 — 167/167
  S4   ✅ direct / inverse / traversal / path — PR #32 — ADR-0040 — 174/174
  S5   ✅ références externes / unresolved / broken links — PR #33 — ADR-0041 — 184/184
  S6   ✅ validation finale trace(requirement) — PR #34 — ADR-0042 — 189/189
M5     ▶ autorisé
```

Progression :

```text
M4 : [████████████████████] 6 / 6 slices validés
```

Baseline d'entrée M4 :

```text
main = 30f4ea43c55b5f6ff7cf235d0d1acc75ab4053fa
147/147 PASS
BUILD SUCCESS
```

Baselines intégrées :

```text
M4-S1 merge = 07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f — 155/155 PASS
M4-S2 merge = 32694f2c74aa9ce4248f9eea907d85460de93eff — 160/160 PASS
M4-S3 merge = 4b3bb5c79e65b8f1501b9949b49f4940294c4312 — 167/167 PASS
M4-S4 merge = cafbc8e61a4af2ed204cd6fc24dcdd262f6ed9e4 — 174/174 PASS
M4-S5 merge = e25aebf0479dfa9d1f146df4d2af0f072b551d39 — 184/184 PASS
```

S6 est validé sur le head de code :

```text
d46b66b5c5c22baabcfe8cfcb53a2da2eff68782
189/189 PASS
```

Le SHA de merge final S6 est inscrit après intégration de la PR #34.

---

# 2. Question de sortie M4

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

**Réponse : OUI.**

La porte technique finale :

```text
trace(requirement)
```

retourne une vue :

```text
normalisée
bornée
cycle-safe
déterministe
explicable
snapshot-cohérente
```

Chaque arête observable conserve :

```text
relation type
direction
origin
resolution
confidence éventuelle
evidence
```

---

# 3. Invariants hérités M3

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
ACTIVE observable atomiquement
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
logical rollback != reactivate RETIRED
backend details != domain
provider facts != MORPHEUS domain
```

M4 ne crée aucune traçabilité qui contourne ces frontières.

---

# 4. M4-S1 — Domaine et taxonomie contrôlée

ADR : **ADR-0037 — Acceptée — M4**.  
PR : **#28 — merged**.  
Gate : **155/155 PASS**.

```text
TraceabilityLinkId explicite
TraceabilityLinkId != hash(source,type,target)
endpoint = EntityKind + DomainIdentity
14 relations MVP contrôlées
origin != relation type
resolution != relation type
confidence bornée [0,1]
heuristic => confidence obligatoire
evidence non vide et immuable
direction canonique
inverse = vue de requête, pas seconde preuve
```

---

# 5. M4-S2 — Persistance snapshot-scoped

ADR : **ADR-0038 — Acceptée — M4**.  
PR : **#29 — merged**.  
Gate : **160/160 PASS**.

```text
TraceabilityStore
MemoryTraceabilityStore
SqliteTraceabilityStore
TraceabilityLink definition != snapshot membership
same TraceabilityLinkId + different definition = collision
snapshot A links != snapshot B links
outgoing/incoming déterministes
inverse query != duplicate persisted edge
Memory == SQLite contract
```

SQLite V005 :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

---

# 6. M4-S3 — Dérivation déterministe

ADR : **ADR-0039 — Acceptée — M4**.  
PR : **#31 — merged**.  
Gate : **167/167 PASS**.

Relations structurelles :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS via RequirementDelta
```

Invariants :

```text
faits structurels uniquement
TraceabilityDerivationKey typée
TraceabilityLinkIdentityResolver explicite
aucun TraceabilityLinkId.generate() caché
aucun hash d'arête comme identité
origin = DERIVED
resolution = RESOLVED
confidence = empty
evidence du fait source conservée
ordre déterministe
aucun fuzzy matching
aucun Task -> Requirement implicite
```

---

# 7. M4-S4 — Traversée et chemins

ADR : **ADR-0040 — Acceptée — M4**.  
PR : **#32 — merged**.  
Merge : `cafbc8e61a4af2ed204cd6fc24dcdd262f6ed9e4`.  
Gate : **174/174 PASS**.

Application :

```text
TraceabilityTraversalDirection
TraceabilityPathStep
TraceabilityPath
TraceabilitySubgraph
TraceabilityTraversalService
```

API :

```text
direct(snapshotId, endpoint, direction, relationTypes)
traverse(snapshotId, start, maxDepth, direction, relationTypes)
findPath(snapshotId, start, target, maxDepth, direction, relationTypes)
```

Invariants :

```text
OUTGOING / INCOMING / BIDIRECTIONAL
maxDepth > 0
snapshot-scoped
BFS borné et cycle-safe
ordre déterministe
relation filters explicites
inverse query != persisted inverse edge
shortest path déterministe
path conserve persisted link + sens de parcours
traversal != transitivity
A -> B -> C != arête synthétique A -> C
Memory == SQLite observable semantics
aucune migration SQLite S4
```

---

# 8. M4-S5 — Références externes et liens non résolus

ADR : **ADR-0041 — Acceptée — M4**.  
PR : **#33 — merged**.  
Merge : `e25aebf0479dfa9d1f146df4d2af0f072b551d39`.  
Gate : **184/184 PASS**.

Persistance :

```text
ExternalReferenceStore
MemoryExternalReferenceStore
SqliteExternalReferenceStore
```

SQLite V006 :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

Traçabilité externe :

```text
ExternalTraceabilityLinkFactory
ExternalTraceabilityQueryService
ExternalTraceabilityView
ExternalTraceabilityAvailability
```

Sémantique :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

Invariants :

```text
ExternalReference snapshot-scoped
same snapshot + same id + different value = collision
same id peut évoluer entre snapshots
EXTERNAL_REFERENCE endpoint explicite
TraceabilityLinkId explicite
TraceabilityResolutionState != ExternalReferenceResolutionState
resolver externe != mutation du TraceabilityLink canonique
UNRESOLVED reste visible
STALE reste explicable
BROKEN_REFERENCE reste visible
aucun couplage obligatoire à MINOS
aucun JSON générique
Memory == SQLite observable semantics
close/reopen SQLite conserve coordonnées, attributs, provenance et historique
```

---

# 9. M4-S6 — Validation finale `trace(requirement)`

ADR : **ADR-0042 — Acceptée — M4**.  
PR : **#34 — gate final vert ; intégration autorisée par le signal explicite utilisateur**.  
Gate : **189/189 PASS**.

Application :

```text
TraceRequirementService
TraceRequirementResult
```

API :

```text
traceActive(projectId, requirementId, maxDepth, relationTypes)
traceSnapshot(snapshotId, requirementId, maxDepth, relationTypes)
```

Règles :

```text
traceActive -> ACTIVE uniquement
traceSnapshot -> ACTIVE/RETIRED uniquement
BUILDING/VALIDATING/READY/FAILED rejetés
CURRENT requirement obligatoire
racine = REQUIREMENT + RequirementId.value
BIDIRECTIONAL
maxDepth > 0
relation filters explicites
external enrichment != mutation des arêtes
```

Scénario final :

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

Preuves :

```text
Memory == SQLite
close/reopen SQLite
incoming + outgoing
profondeur >= 3
cycle réel
filtres
UNRESOLVED
BROKEN_REFERENCE
provenance/evidence
RETIRED explicite
ACTIVE isolation
snapshots non publiés rejetés
```

Gate final local Windows :

```text
.\mvnw.cmd clean test
javac release 21

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

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

Validation : [`../VALIDATION_M4.md`](../VALIDATION_M4.md).

---

# 10. Checklist bloquante avant M5

| Condition | État | Slice |
|---|---|---|
| `TraceabilityLink` first-class | ✅ | S1 |
| taxonomie contrôlée | ✅ | S1 |
| identité de lien explicite | ✅ | S1 |
| direction canonique | ✅ | S1 |
| origin/resolution/confidence séparés | ✅ | S1 |
| evidence obligatoire | ✅ | S1 |
| persistance snapshot-scoped | ✅ | S2 |
| Memory/SQLite même contrat | ✅ | S2 |
| migration V005 normalisée / reopen | ✅ | S2 |
| dérivation déterministe | ✅ | S3 |
| aucun fuzzy matching | ✅ | S3 |
| identité de lien dérivée explicitement via resolver | ✅ | S3 |
| evidence du fait structurel conservée | ✅ | S3 |
| outgoing/incoming | ✅ | S4 |
| traversal borné et cycle-safe | ✅ | S4 |
| path explicable | ✅ | S4 |
| shortest path déterministe | ✅ | S4 |
| aucune arête inverse/transitive synthétique | ✅ | S4 |
| unresolved externe conservé | ✅ | S5 |
| broken-reference visible | ✅ | S5 |
| cross-engine découplé | ✅ | S5 |
| V006 normalisée / reopen | ✅ | S5 |
| `trace(requirement)` | ✅ | S6 |
| `VALIDATION_M4.md` | ✅ | S6 |

**Aucun bloqueur M4 ne subsiste avant M5.**

---

# 11. Hors périmètre M4

```text
recherche lexicale générale               -> M5
contexte compact                          -> M5
coverage diagnostics complets             -> M6
sync/invalidation complète des liens      -> M7
analyse d'impact complète                 -> M8
CLI publique trace                        -> M9
MCP                                       -> M10
API                                       -> M11
résolution code MINOS de production       -> M12
```

---

# 12. Gouvernance finale

```text
1. ADR-0042 acceptée après preuve
2. VALIDATION_M4.md validée
3. PR #34 Ready après finalisation documentaire
4. merge #34 autorisé par le signal explicite utilisateur
5. issue #27 fermée après vérification du merge
6. ROADMAP globale alignée sur M4 VALIDÉ / M5 autorisé
```

**Prochaine ligne active : M5 — Requêtes et contexte compact.**
