# M3 — Plan d'exécution détaillé

Statut : **M3 actif — 3 slices validés sur 6 ; S4 prochain**

Dernière mise à jour : 22 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et sert de tableau de bord opérationnel pour M3.

---

# 1. Position actuelle

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     ✅ validé — 8/8 — 94/94
M3     🚧 actif
  S1   ✅ TemporalState + SpecificationVersion — PR #21 — ADR-0031 — 103/103
  S2   ✅ ChangeLifecycleState — PR #22 — ADR-0032 — 119/119
  S3   ✅ KnowledgeSnapshot complet — PR #23 — ADR-0033 — 127/127
  S4   🚧 persistance métier versionnée — prochain
  S5   ⬜ application / promotion des deltas
  S6   ⬜ historique / comparaison / rétention
M4     ⏳ bloqué par M3
```

Progression de pilotage :

```text
M3 : [███████████░░░░░░░░░░] 3 / 6 slices validés
```

Cette barre mesure les slices validés, pas une estimation de charge.

---

# 2. Question de sortie M3

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

Porte technique finale :

```text
get_current_specification
```

ne doit jamais contenir un delta seulement proposé, y compris pendant :

```text
réingestion
construction de snapshot
validation de snapshot
activation concurrente
redémarrage du store
```

---

# 3. M3-S1 — VALIDÉ : TemporalState et SpecificationVersion

Architecture :

```text
normalized content M2
        ↓
EntityVersion<T>
├── EntityVersionId
├── DomainIdentity
├── SpecificationVersionId
├── TemporalState
└── content
        ↓
TemporalProjection<T>
```

Invariants validés :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
content normalization != temporal projection
PROPOSED never leaks into CURRENT
technical reingestion != implicit business version
```

Oracle :

```text
same logical requirement
├── CURRENT   30 minutes
├── PROPOSED  60 minutes
└── PROPOSED  15 minutes

CURRENT view => 30 minutes only
```

Preuve :

```text
TemporalVersioningTest  5/5 PASS
TemporalProjectionTest  4/4 PASS
TOTAL                  103/103 PASS
BUILD SUCCESS
```

---

# 4. M3-S2 — VALIDÉ : ChangeLifecycleState

Cycle canonique :

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

Architecture :

```text
ChangeProposal (contenu M2)
        │
        └── ChangeId
              ↓
       ChangeLifecycle
              ↓
ChangeLifecycleStateMachine
```

Invariants validés :

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Règles prouvées :

```text
PROPOSED -> SPECIFIED
  nécessite requirements + contraintes critiques + acceptance criteria

SPECIFIED -> PLANNED
  seulement si design_required=false + plan présent

PLANNED -> IMPLEMENTING
  bloqué par un bloqueur connu

VERIFYING -> COMPLETED
  bloqué par critère bloquant FAILED ou non vérifié

backward transitions
  uniquement par policy explicite

ABANDONED
  raison structurée obligatoire

ABANDONED -> PROPOSED
  réouverture canonique

ARCHIVED -> ...
  aucune réouverture implicite
```

Preuve :

```text
ChangeLifecycleTest               4/4 PASS
ChangeLifecycleStateMachineTest  12/12 PASS
TOTAL                           119/119 PASS
BUILD SUCCESS
```

---

# 5. M3-S3 — VALIDÉ : KnowledgeSnapshot complet

États techniques :

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

Architecture retenue :

```text
SpecificationKnowledgeStore
        │
        ├── putSnapshot()
        ├── transitionSnapshotState()  // CAS
        ├── activeSnapshot()
        └── activateSnapshot()         // publication atomique
                    │
                    ▼
          SnapshotLifecycleService
```

Flux de validation :

```text
register BUILDING
    ↓
BUILDING -> VALIDATING
    ↓
validator
    ├── valid   -> READY
    ├── invalid -> FAILED
    └── throws  -> FAILED + exception contrôlée
```

Activation :

```text
Vn ACTIVE
Vn+1 READY(predecessor=Vn)
        ↓ activateSnapshot
Vn RETIRED
Vn+1 ACTIVE
```

Invariants validés :

```text
SpecificationVersion != KnowledgeSnapshot
KnowledgeSnapshotState != TemporalState
KnowledgeSnapshotState != ChangeLifecycleState
seul ACTIVE est observable comme snapshot courant
un projet possède au plus un snapshot ACTIVE
stale predecessor est rejeté
FAILED n'évince jamais l'ACTIVE existant
ACTIVE/RETIRED ne sont produits que par activateSnapshot
transitionSnapshotState applique un CAS explicite
```

Oracle d'échec :

```text
Vn = ACTIVE
Vn+1 = BUILDING -> VALIDATING -> FAILED

résultat observable : Vn reste ACTIVE
```

Oracle succès :

```text
Vn = ACTIVE
Vn+1 = BUILDING -> VALIDATING -> READY -> ACTIVE

before activation -> Vn
after activation  -> Vn+1
never             -> mélange partiel
```

SQLite :

```text
aucune migration S3 nécessaire
index unique partiel -> au plus un ACTIVE par projet
CAS -> UPDATE ... WHERE id = ? AND state = ?
activation multi-lignes transactionnelle
ACTIVE/RETIRED survivent à fermeture/réouverture
```

Preuve :

```text
SnapshotLifecycleServiceTest             7/7 PASS
SqliteSnapshotLifecyclePersistenceTest   1/1 PASS
SpecificationKnowledgeStoreContractTest  4/4 PASS
TOTAL                                   127/127 PASS
Failures                                   0
Errors                                     0
Skipped                                    0
BUILD SUCCESS
```

Le store mémoire reste l'oracle contractuel. S3 ne crée pas encore les tables métier de S4.

---

# 6. NOW — M3-S4 Premières migrations métier versionnées

ADR-0030 impose la question :

> **Quelle version / quel snapshot possède ou expose cette occurrence de contenu ?**

avant toute table métier.

Objectif : persister les occurrences normalisées avec ownership explicite :

```text
DomainIdentity
    ↓
EntityVersionId
    ↓
SpecificationVersionId
    ↓
KnowledgeSnapshotId
    ↓
TemporalState
```

Chaque occurrence persistée doit donc répondre à :

```text
quelle identité logique ?
quelle occurrence/version d'entité ?
quelle SpecificationVersion ?
quel KnowledgeSnapshot ?
quel TemporalState ?
```

Familles candidates :

```text
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
external_references
provenance/evidence
```

S4 doit décider le plus petit schéma de production cohérent ; il ne doit pas créer mécaniquement toutes les familles si une preuve plus petite suffit à verrouiller le pattern.

Contraintes :

- SQLite reste derrière les ports ;
- memory store reste l'oracle contractuel ;
- aucune payload JSON générique ;
- ownership version/snapshot explicite ;
- `DomainIdentity != EntityVersionId` ;
- `SpecificationVersion != KnowledgeSnapshot` ;
- `TemporalState` persiste avec l'occurrence ;
- reopen SQLite reconstruit le même état observable ;
- aucune fuite d'un `PROPOSED` dans la vue `CURRENT` après redémarrage.

Gate de départ :

```text
127 tests
```

---

# 7. M3-S5 — Application / promotion des deltas

Entrées :

```text
ADDED
MODIFIED
REMOVED
```

Règles :

```text
normalized delta != applied delta
COMPLETED != automatic promotion
promotion must be explicit and evidenced
```

Preuves :

- baseline et propositions coexistent ;
- application déterministe ;
- promotion explicite ;
- `MODIFIED` conserve identité logique ;
- `REMOVED` retire uniquement la projection cible ;
- provenance de promotion conservée.

---

# 8. M3-S6 — Historique / comparaison / rétention

Comparaison minimale :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

`MOVED / RENAMED` seulement si la continuité d'identité est démontrée.

Décisions à figer :

```text
retention policy
snapshot comparison
logical rollback
reconstruction
historical query semantics
```

---

# 9. Checklist bloquante avant M4

| Condition | État | Slice |
|---|---|---|
| TemporalState explicite | ✅ | S1 |
| EntityVersion distinct de DomainIdentity | ✅ | S1 |
| SpecificationVersion | ✅ | S1 |
| CURRENT view sans fuite PROPOSED | ✅ | S1 |
| plusieurs PROPOSED concurrents conservés | ✅ | S1 |
| unicité CURRENT par identité dans une projection | ✅ | S1 |
| ChangeLifecycleState complet | ✅ | S2 |
| lifecycle distinct de TemporalState | ✅ | S2 |
| `COMPLETED != CURRENT` | ✅ | S2 |
| KnowledgeSnapshot complet | ✅ | S3 |
| activation atomique observable | ✅ | S3 |
| échec avant activation conserve l'ancien ACTIVE | ✅ | S3 |
| stale predecessor rejeté | ✅ | S3 |
| état ACTIVE persiste après redémarrage SQLite | ✅ | S3 |
| persistance métier versionnée | 🚧 | S4 |
| application/promotion des deltas | ⬜ | S5 |
| historique/comparaison/rétention | ⬜ | S6 |
| VALIDATION_M3.md | ⬜ | clôture |

---

# 10. Hors périmètre M3

```text
TraceabilityLink / graphe complet       -> M4
recherche métier / contexte compact     -> M5
sync incrémentale complète              -> M7
analyse d'impact documentaire           -> M8
CLI stabilisée                          -> M9
MCP / API                               -> M10 / M11
```

---

# 11. Règle de gouvernance

Après chaque gate vert :

```text
1. inscrire la preuve exacte dans l'ADR
2. mettre la PR Ready
3. merger seulement après signal explicite de poursuite
4. mettre à jour issue #20
5. avancer NOW vers le slice suivant
6. mettre à jour la checklist bloquante M4
```
