# M3 — Plan d'exécution détaillé

Statut : **M3 actif — S1 en cours**

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
  S1   🚧 TemporalState + SpecificationVersion
  S2   ⬜ ChangeLifecycleState
  S3   ⬜ KnowledgeSnapshot complet
  S4   ⬜ persistance métier versionnée
  S5   ⬜ application / promotion des deltas
  S6   ⬜ historique / comparaison / rétention
M4     ⏳ bloqué par M3
```

Progression de pilotage :

```text
M3 : [███░░░░░░░░░░░░░░░░░] 0 / 6 slices validés ; S1 actif
```

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

# 3. NOW — M3-S1

## Objectif

Introduire la projection temporelle sans contaminer le contenu normalisé M2.

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

États :

```text
CURRENT
PROPOSED
HISTORICAL
```

Invariants :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
content normalization != temporal projection
PROPOSED never leaks into CURRENT
technical reingestion != implicit business version
```

Oracle principal :

```text
same logical requirement
├── CURRENT   30 minutes
├── PROPOSED  60 minutes
└── PROPOSED  15 minutes

CURRENT view => 30 minutes only
```

Livrables :

```text
TemporalState
EntityVersionId
SpecificationVersionId
EntityVersion<T>
SpecificationVersion
TemporalProjection<T>
ADR-0031
tests domaine + application
```

Gate attendu depuis baseline M2 :

```text
94 tests M2
+ 5 TemporalVersioningTest
+ 4 TemporalProjectionTest
--------------------------------
103 tests attendus
```

---

# 4. NEXT — M3-S2 ChangeLifecycleState

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

Preuves :

- transitions autorisées et interdites ;
- `SPECIFIED -> PLANNED` seulement si `design_required=false` ;
- transitions backward selon politique explicite ;
- `COMPLETED != CURRENT` ;
- `ARCHIVED != promotion` ;
- lifecycle distinct de `TemporalState` et de `KnowledgeSnapshotState`.

---

# 5. M3-S3 — KnowledgeSnapshot complet

États techniques :

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

Livrables candidats :

```text
KnowledgeSnapshot
SnapshotActivationService
snapshot validation contract
predecessor / activation policy
```

Preuves :

```text
Vn ACTIVE
build Vn+1
failure before activation
=> Vn stays ACTIVE
```

et :

```text
before activation -> consumers see Vn
after activation  -> consumers see Vn+1
never partial
```

---

# 6. M3-S4 — Premières migrations métier versionnées

ADR-0030 impose la question :

> **Quelle version / quel snapshot possède ou expose cette occurrence de contenu ?**

avant toute table métier.

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

Contraintes :

- SQLite reste derrière les ports ;
- memory store reste l'oracle contractuel ;
- aucune payload JSON générique ;
- ownership version/snapshot explicite ;
- reopen SQLite reconstruit le même état observable.

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
| TemporalState explicite | 🚧 | S1 |
| EntityVersion distinct de DomainIdentity | 🚧 | S1 |
| SpecificationVersion | 🚧 | S1 |
| CURRENT view sans fuite PROPOSED | 🚧 | S1 |
| ChangeLifecycleState complet | ⬜ | S2 |
| KnowledgeSnapshot complet | ⬜ | S3 |
| activation atomique observable | ⬜ | S3 |
| persistance métier versionnée | ⬜ | S4 |
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
3. merger
4. mettre à jour issue #20
5. avancer NOW vers le slice suivant
6. mettre à jour la checklist bloquante M4
```
