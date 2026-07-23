# M4 — Plan d'exécution détaillé

Statut : **M4 actif — 1 slice validé sur 6 ; S2 prochain**

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
  S2   ⏳ persistance snapshot-scoped Memory + SQLite — prochain
  S3   ⏳ dérivation déterministe depuis le modèle normalisé
  S4   ⏳ direct / inverse / traversal / path
  S5   ⏳ références externes / unresolved / broken links
  S6   ⏳ validation finale trace(requirement)
M5     ⏳ bloqué par M4
```

Progression :

```text
M4 : [███░░░░░░░░░░░░░░░░░] 1 / 6 slices validés
```

Baseline d'entrée M4 :

```text
main = 30f4ea43c55b5f6ff7cf235d0d1acc75ab4053fa
147/147 PASS
BUILD SUCCESS
```

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

# 5. M4-S1 — VALIDÉ : Domaine et taxonomie contrôlée

ADR : **ADR-0037 — Acceptée — M4**.

Livrables :

```text
TraceabilityLinkId
TraceabilityEntityKind
TraceabilityEntityRef
TraceabilityRelationType
TraceabilityLinkOrigin
TraceabilityResolutionState
TraceabilityConfidence
TraceabilityTransitivityPolicy
TraceabilitySemanticClass
TraceabilityLink
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

Gate local Windows :

```text
.\mvnw.cmd clean test
javac release 21

TraceabilityLinkTest                    8/8 PASS
LayerDependencyTest                     2/2 PASS

Domain                                 21 tests
Application                            54 tests
OpenSpec provider                      26 tests
Synthetic provider                      7 tests
SQLite store                            7 tests
Architecture tests                     40 tests
----------------------------------------------
TOTAL                                 155/155 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
```

Gate terminé le **23 juillet 2026 à 12:17:43 +02:00**.

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

---

# 6. NOW — M4-S2 Persistance snapshot-scoped

Objectif : stocker les liens sans mélanger deux générations de connaissance.

Contrat cible :

```text
TraceabilityStore
  putLink(snapshotId, link)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes?)
  incoming(snapshotId, target, relationTypes?)
```

Invariants à prouver :

```text
KnowledgeSnapshotId membership obligatoire
ACTIVE history != candidate history
même TraceabilityLinkId ne change pas de définition silencieusement
inverse query != duplicate persisted edge
Memory == SQLite contract
snapshot A links != snapshot B links
```

Le schéma SQLite sera décidé uniquement après le contrat Java. Une migration V005 est autorisée si elle matérialise exactement ce contrat.

---

# 7. M4-S3 — Dérivation déterministe

Objectif : transformer uniquement les relations déjà prouvées dans le modèle normalisé en liens explicables.

Sources candidates directement démontrables :

```text
Requirement -> Specification        DERIVES_FROM
Scenario -> Requirement             REFINES
Constraint -> Change                CONSTRAINS
Change -> DesignDecision            DECIDED_BY
Change -> Requirement               AFFECTS, lorsqu'un RequirementDelta fournit l'identité
```

Règles :

```text
pas de fuzzy matching
pas de rapprochement par titre
pas de rapprochement par chemin
pas de LLM
pas d'inférence de Task -> Requirement sans fait source
```

Toute dérivation conserve l'evidence qui la justifie.

---

# 8. M4-S4 — Traversée

Capacités :

```text
outgoing
incoming
traverse
findPath
```

Invariants :

```text
maxDepth > 0 explicite
ordre déterministe
cycle-safe
relation filters explicites
bidirectional n'invente aucune seconde preuve
path conserve chaque arête réelle
transitivity policy != traversal permission
```

Un chemin A -> B -> C ne crée jamais implicitement une arête A -> C.

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
| persistance snapshot-scoped | ⬜ | S2 |
| Memory/SQLite même contrat | ⬜ | S2 |
| dérivation déterministe | ⬜ | S3 |
| aucun fuzzy matching | ⬜ | S3 |
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
