# M4 — Plan d'exécution détaillé

Statut : **M4 actif — 4 slices validés sur 6 ; S5 prochain**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M4.

---

# 1. Position actuelle

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     ✅ validé — 94/94
M3     ✅ validé — 6/6 — 147/147
M4     🚧 actif
  S1   ✅ domaine TraceabilityLink + taxonomie contrôlée — PR #28 — ADR-0037 — 155/155
  S2   ✅ persistance snapshot-scoped Memory + SQLite — PR #29 — ADR-0038 — 160/160
  S3   ✅ dérivation déterministe depuis modèle normalisé — PR #31 — ADR-0039 — 167/167
  S4   ✅ direct / inverse / traversal / path — PR #32 — ADR-0040 — 174/174
  S5   ⏳ références externes / unresolved / broken links — prochain
  S6   ⏳ validation finale trace(requirement)
M5     ⏳ bloqué par M4
```

Progression :

```text
M4 : [██████████████░░░░░░░] 4 / 6 slices validés
```

Baseline d'entrée M4 :

```text
main = 30f4ea43c55b5f6ff7cf235d0d1acc75ab4053fa
147/147 PASS
BUILD SUCCESS
```

Baselines intégrées :

```text
M4-S1 merge = 07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f
M4-S1 gate  = 155/155 PASS
M4-S2 merge = 32694f2c74aa9ce4248f9eea907d85460de93eff
M4-S2 gate  = 160/160 PASS
M4-S3 merge = 4b3bb5c79e65b8f1501b9949b49f4940294c4312
M4-S3 gate  = 167/167 PASS
```

Dernier gate validé :

```text
M4-S4 PR #32 = Ready après finalisation documentaire
M4-S4 gate   = 174/174 PASS
```

S4 reste non mergé tant qu'aucun nouveau signal explicite de merge n'est donné.

---

# 2. Question de sortie M4

> **MORPHEUS peut-il relier les éléments d'intention/specification par des relations typées, directionnelles et explicables, conserver les liens non résolus, puis produire un sous-graphe borné et déterministe sans dépendre d'un backend graphe ?**

Porte technique finale :

```text
trace(requirement)
```

doit retourner un sous-graphe :

```text
normalisé
borné
cycle-safe
déterministe
explicable
snapshot-cohérent
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

# 3. Evidence héritée de M0

E06 : **PASS**

```text
taxonomie contrôlée
direction canonique
incoming/outgoing
traversée profondeur 3
unresolved conservé
origin/resolution/confidence séparés
déduplication d'une observation identique
```

E06b : **PASS**

```text
même contrat Memory / SQLite
snapshot comme frontière de cohérence
index source / target suffisants pour le MVP
aucun langage backend dans le domaine
pas de graph database requise par le corpus MVP
```

Ces résultats sont des preuves de faisabilité. Ils ne remplacent pas les contrats Java de production M4 et sont réalignés avec M3.

---

# 4. Invariants hérités M3

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

M4 ne doit jamais créer une traçabilité qui contourne ces frontières.

---

# 5. M4-S1 — VALIDÉ ET INTÉGRÉ : Domaine et taxonomie contrôlée

ADR : **ADR-0037 — Acceptée — M4**.

PR : **#28 — merged**.

Merge :

```text
07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f
```

Décisions validées :

```text
TraceabilityLinkId explicite
TraceabilityLinkId != hash(source,type,target)
endpoint = EntityKind + DomainIdentity
relation type fermé : 14 relations MVP
origin != relation type
resolution != relation type
confidence bornée [0,1]
heuristic => confidence obligatoire
evidence non vide et immuable
direction canonique
inverse = vue de requête, pas seconde preuve
```

Gate : **155/155 PASS**.

---

# 6. M4-S2 — VALIDÉ ET INTÉGRÉ : Persistance snapshot-scoped

ADR : **ADR-0038 — Acceptée — M4**.

PR : **#29 — merged**.

Merge :

```text
32694f2c74aa9ce4248f9eea907d85460de93eff
```

Contrat :

```text
TraceabilityStore
  putLink(snapshotId, link)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes)
  incoming(snapshotId, target, relationTypes)
```

Adapters :

```text
MemoryTraceabilityStore
SqliteTraceabilityStore
```

Invariants validés :

```text
KnowledgeSnapshotId membership obligatoire
link definition != snapshot membership
same TraceabilityLinkId + same definition = idempotent
same TraceabilityLinkId + different definition = collision
snapshot A links != snapshot B links
candidate snapshot autorisé
empty relation filter = all relations
outgoing/incoming déterministes
inverse query != duplicate persisted edge
Memory == SQLite contract
```

Migration SQLite V005 :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

Gate : **160/160 PASS**.

Le close/reopen SQLite conserve définition, evidence et memberships multi-snapshot.

---

# 7. M4-S3 — VALIDÉ ET INTÉGRÉ : Dérivation déterministe

ADR : **ADR-0039 — Acceptée — M4**.

PR : **#31 — merged**.

Merge :

```text
4b3bb5c79e65b8f1501b9949b49f4940294c4312
```

Application :

```text
TraceabilityDerivationKey
TraceabilityLinkIdentityResolver
DeterministicTraceabilityDerivationService
```

Relations dérivées uniquement depuis des faits structurels :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS via RequirementDelta
```

Invariants validés :

```text
aucun TraceabilityLinkId.generate() caché
aucun hash d'arête transformé en identité
identité manquante = échec explicite
même link id pour deux faits = échec explicite
origin = DERIVED
resolution = RESOLVED
confidence = empty
observedAt explicite
evidence = entité qui encode le fait
ordre canonique (source, relation, target, fact)
aucun fuzzy matching
aucun matching titre/statement/path
aucun LLM / embedding
aucun Task -> Requirement sans fait structurel
```

Gate : **167/167 PASS**.

---

# 8. M4-S4 — VALIDÉ TECHNIQUEMENT : Traversée et chemins

ADR : **ADR-0040 — Acceptée — M4**.

PR : **#32 — Ready après gate ; merge en attente de signal explicite**.

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

Directions :

```text
OUTGOING
INCOMING
BIDIRECTIONAL
```

Invariants validés :

```text
maxDepth > 0 explicite
snapshot-scoped
BFS borné
cycle-safe
ordre de voisins déterministe
relation filters explicites
empty filter = toutes les relations
incoming/bidirectional = vues de requête
aucune arête inverse persistée
path step conserve persisted TraceabilityLink + from/into
shortest path déterministe
transitivity policy != traversal permission
A -> B -> C != arête synthétique A -> C
Memory == SQLite observable semantics
```

S4 reste exclusivement applicatif au-dessus de `TraceabilityStore.outgoing/incoming` :

```text
aucune migration SQLite
aucune modification des adapters S2
aucune graph database
aucun backend query language dans domain/application
```

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

TraceabilityTraversalContractTest      7/7 PASS
LayerDependencyTest                    2/2 PASS

Domain                                21 tests
Application                           61 tests
OpenSpec provider                     26 tests
Synthetic provider                     7 tests
SQLite store                           7 tests
Architecture tests                    52 tests
---------------------------------------------
TOTAL                                174/174 PASS
Failures                               0
Errors                                 0
Skipped                                0
BUILD SUCCESS
Total time                           15.256 s
```

Gate terminé le **23 juillet 2026 à 13:26:39 +02:00**.

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

---

# 9. NOW — M4-S5 Références externes et liens non résolus

Objectif : rendre les relations vers des cibles externes explicitement représentables et explicables même lorsque leur résolution échoue ou n'est pas disponible.

Relations prioritaires :

```text
LINKS_TO_CODE
LINKS_TO_TEST
VERIFIED_BY
SATISFIES
```

Intégration cible :

```text
TraceabilityLink
        ↓
EXTERNAL_REFERENCE endpoint
        ↓
ExternalReference
        ↓ optional resolver
ResolvedExternalTarget
```

Invariants à prouver :

```text
MINOS indisponible != MORPHEUS indisponible
UNRESOLVED reste visible
référence cassée reste explicable
resolution externe != relation semantics
resolution externe != mutation de la preuve canonique
origin / resolution / confidence / evidence restent séparés
snapshot-scoped
aucun couplage obligatoire à MINOS
Memory == SQLite observable semantics
```

S5 devra expliciter la différence entre :

```text
UNRESOLVED
PARTIALLY_RESOLVED
HEURISTIC
RESOLVED
```

sans confondre l'état de résolution du lien avec la disponibilité instantanée d'un moteur externe.

---

# 10. M4-S6 — Validation finale

Porte :

```text
trace(requirement)
```

Doit démontrer au minimum :

```text
Requirement <- REFINES - Scenario
Requirement <- AFFECTS - Change
Change - DECIDED_BY -> DesignDecision
Constraint - CONSTRAINS -> Change
Requirement - LINKS_TO_CODE -> ExternalReference (resolved ou unresolved)
```

Avec :

```text
Memory + SQLite
close/reopen SQLite
profondeur >= 3
incoming + outgoing
cycle
filtres
unresolved
provenance/evidence
snapshot historique explicite
ACTIVE isolation
```

S6 crée `docs/VALIDATION_M4.md` et répond à la question de sortie.

---

# 11. Checklist bloquante avant M5

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
| unresolved externe conservé | ⬜ | S5 |
| cross-engine découplé | ⬜ | S5 |
| `trace(requirement)` | ⬜ | S6 |
| `VALIDATION_M4.md` | ⬜ | S6 |

---

# 12. Hors périmètre M4

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

# 13. Gouvernance

Après chaque gate vert :

```text
1. inscrire la preuve exacte dans l'ADR
2. mettre la PR Ready
3. merger seulement après signal explicite
4. mettre à jour issue #27
5. avancer NOW vers le slice suivant
6. mettre à jour la checklist bloquante M5
```
