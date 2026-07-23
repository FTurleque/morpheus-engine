# M4 — Plan d'exécution détaillé

Statut : **M4 actif — 3 slices validés sur 6 ; S4 prochain**

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
  S4   ⏳ direct / inverse / traversal / path — prochain
  S5   ⏳ références externes / unresolved / broken links
  S6   ⏳ validation finale trace(requirement)
M5     ⏳ bloqué par M4
```

Progression :

```text
M4 : [██████████░░░░░░░░░░░] 3 / 6 slices validés
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
```

S3 est validé techniquement dans PR #31 et attend le signal explicite de merge.

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
aucune persistance/traversée en S1
```

Gate :

```text
TraceabilityLinkTest                    8/8 PASS
LayerDependencyTest                     2/2 PASS
TOTAL                                 155/155 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
```

Gate terminé le **23 juillet 2026 à 12:17:43 +02:00**.

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

Gate :

```text
TraceabilityPersistenceContractTest     5/5 PASS
LayerDependencyTest                     2/2 PASS
TOTAL                                 160/160 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
```

Gate terminé le **23 juillet 2026 à 12:47:46 +02:00**.

Le close/reopen SQLite conserve définition, evidence et memberships multi-snapshot.

---

# 7. M4-S3 — VALIDÉ TECHNIQUEMENT : Dérivation déterministe

ADR : **ADR-0039 — Acceptée — M4**.

PR : **#31 — Ready après gate ; merge en attente de signal explicite**.

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

Les scenarios imbriqués dans un `RequirementDelta` produisent aussi `REFINES` lorsque leur `RequirementId` est explicite.

Identité :

```text
TraceabilityDerivationKey
  fact: TraceabilityEntityRef
  source
  relationType
  target

TraceabilityLinkIdentityResolver
  resolve(key) -> Optional<TraceabilityLinkId>
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

Déduplication autorisée uniquement pour une même `TraceabilityDerivationKey` exacte ; les evidences de ce même fait peuvent être agrégées.

Deux faits distincts vers les mêmes endpoints restent deux observations distinctes.

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

DeterministicTraceabilityDerivationServiceTest   7/7 PASS
LayerDependencyTest                              2/2 PASS

Domain                                           21 tests
Application                                      61 tests
OpenSpec provider                                26 tests
Synthetic provider                                7 tests
SQLite store                                      7 tests
Architecture tests                               45 tests
----------------------------------------------------------
TOTAL                                           167/167 PASS
Failures                                          0
Errors                                            0
Skipped                                           0
BUILD SUCCESS
Total time                                     15.746 s
```

Gate terminé le **23 juillet 2026 à 13:09:18 +02:00**.

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

---

# 8. NOW — M4-S4 Traversée

Objectif : exposer une traversée snapshot-scoped bornée et déterministe sans transformer le domaine en backend graphe.

Capacités :

```text
outgoing
incoming
traverse
findPath
```

À décider explicitement :

```text
TraceabilityTraversalService
TraceabilityTraversalResult / subgraph
TraceabilityPath
maxDepth
relation filters
bidirectional policy
node/edge ordering
cycle handling
path tie-breaking
```

Invariants à prouver :

```text
maxDepth > 0 explicite
ordre déterministe
cycle-safe
relation filters explicites
snapshot-scoped
bidirectional n'invente aucune seconde preuve
path conserve chaque arête réelle
transitivity policy != traversal permission
un chemin A -> B -> C ne crée pas A -> C
Memory == SQLite observable semantics
```

S4 ne doit pas introduire de graph database ni de query language backend dans domain/application.

---

# 9. M4-S5 — Références externes et liens non résolus

Relations prioritaires :

```text
LINKS_TO_CODE
LINKS_TO_TEST
VERIFIED_BY
SATISFIES
```

Intégration :

```text
TraceabilityLink
        ↓
EXTERNAL_REFERENCE endpoint
        ↓
ExternalReference
        ↓ optional resolver
ResolvedExternalTarget
```

Invariants :

```text
MINOS indisponible != MORPHEUS indisponible
UNRESOLVED reste visible
STALE external reference reste explicable
resolution externe != relation semantics
```

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
| outgoing/incoming | ⬜ | S4 |
| traversal borné et cycle-safe | ⬜ | S4 |
| path explicable | ⬜ | S4 |
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
