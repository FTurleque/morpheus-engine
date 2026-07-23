# M3 — Plan d'exécution détaillé

Statut : **M3 actif — 5 slices validés sur 6 ; S6 prochain**

Dernière mise à jour : 23 juillet 2026

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
  S4   ✅ persistance métier versionnée — PR #24 — ADR-0034 — 134/134
  S5   ✅ application / promotion des deltas — PR #25 — ADR-0035 — 142/142
  S6   🚧 historique / comparaison / rétention — prochain
M4     ⏳ bloqué par M3
```

Progression :

```text
M3 : [█████████████████░░░] 5 / 6 slices validés
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

Invariants :

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

Preuve : `103/103 PASS`.

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

Invariants :

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Politique :

```text
SPECIFIED -> PLANNED uniquement si design_required=false + plan
backward transitions uniquement par policy explicite
ABANDONED exige une raison structurée
ABANDONED -> PROPOSED autorisé
ARCHIVED n'est pas rouvert implicitement
```

Preuve : `119/119 PASS`.

---

# 5. M3-S3 — VALIDÉ : KnowledgeSnapshot complet

Cycle :

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
```

Architecture :

```text
SpecificationKnowledgeStore
        ├── putSnapshot()
        ├── transitionSnapshotState()  // CAS
        ├── activeSnapshot()
        └── activateSnapshot()         // publication atomique
                    ↓
          SnapshotLifecycleService
```

Invariants :

```text
seul ACTIVE est observable comme snapshot courant
un projet possède au plus un ACTIVE
stale predecessor est rejeté
FAILED n'évince jamais l'ACTIVE existant
ACTIVE/RETIRED ne sont produits que par activateSnapshot
transitionSnapshotState applique un CAS explicite
```

Oracle :

```text
Vn ACTIVE
Vn+1 BUILDING -> VALIDATING -> FAILED
=> Vn reste ACTIVE
```

SQLite conserve `ACTIVE/RETIRED` après fermeture/réouverture.

Preuve : `127/127 PASS`.

---

# 6. M3-S4 — VALIDÉ : persistance métier versionnée

ADR : **ADR-0034 — Acceptée — M3**.

Premier vertical slice : `Requirement`.

Architecture persistante :

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

Migration V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Relation métier/technique :

```text
SpecificationVersion 1 <--- N KnowledgeSnapshot
```

Le binding snapshot/version est explicite. `RequirementVersionRecord` reconstruit exactement `EntityVersion<Requirement>` avec provenance.

Invariants validés :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
normalized Requirement != persisted occurrence
snapshot/version ownership obligatoire
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents autorisés
aucune payload JSON générique
```

Vue courante :

```text
project
  ↓
activeSnapshot(project)
  ↓
currentRequirement(snapshotId, DomainIdentity)
```

Donc un `PROPOSED` persiste mais reste invisible dans `CURRENT`.

Preuve de redémarrage SQLite :

```text
CURRENT  = 30 jours
PROPOSED = 60 jours
        ↓ close/reopen
CURRENT query = 30 jours
PROPOSED      = toujours conservé séparément
```

Preuve :

```text
VersionedRequirementPersistenceTest  7/7 PASS
SqliteSchemaMigrationTest            4/4 PASS
TOTAL                              134/134 PASS
Failures                              0
Errors                                0
Skipped                               0
BUILD SUCCESS
```

Le pattern S4 est désormais la référence pour les autres familles métier ; il n'impose pas de les persister toutes avant que leur usage le justifie.

---

# 7. M3-S5 — VALIDÉ : application / promotion explicite des deltas

ADR : **ADR-0035 — Acceptée — M3**.

Entrées :

```text
ADDED
MODIFIED
REMOVED
```

Séparation validée :

```text
APPLY != PROMOTE
PROMOTE != ACTIVATE
COMPLETED != PROMOTE
COMPLETED != ACTIVATE
```

Flux :

```text
ACTIVE snapshot / CURRENT baseline
        +
RequirementDelta[]
        ↓ APPLY explicite
nouvelle SpecificationVersion
        ↓
BUILDING candidate snapshot
        ↓
RequirementVersionRecord[] CURRENT
        ↓ PROMOTE explicite
VALIDATING
   ├── READY
   └── FAILED
        ↓ ACTIVATE explicite uniquement si READY
ACTIVE
```

Invariants validés :

```text
normalized delta != applied delta
CURRENT ne change pas avant activation
ADDED refuse une identité déjà existante
aucun fuzzy matching titre/chemin/contenu/similarité
MODIFIED conserve DomainIdentity
MODIFIED crée une nouvelle EntityVersionId
REMOVED retire seulement l'occurrence du candidat
ACTIVE n'est jamais muté par APPLY
lot incohérent/ambigu rejeté avant écriture
ordre d'entrée des deltas sans sémantique
promotion explicite et evidenced
FAILED candidate conserve l'ancien ACTIVE
Memory et SQLite respectent le même contrat
```

La construction candidate réutilise le schéma V004 ; aucune migration V005 n'est nécessaire pour ce vertical slice.

`RequirementDelta.scenarios` reste hors persistance S5 conformément à la frontière S4 : le slice ne généralise pas prématurément la persistance à toutes les familles métier.

Oracle de visibilité :

```text
avant activation
CurrentRequirementQueryService -> ancienne baseline ACTIVE

après activation
CurrentRequirementQueryService -> nouvelle baseline ACTIVE

candidate FAILED
CurrentRequirementQueryService -> ancienne baseline ACTIVE
```

Preuve locale Windows :

```text
.\mvnw.cmd clean test
javac release 21

RequirementDeltaApplicationContractTest  8/8 PASS

Domain                                  13 tests
Application                             54 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      35 tests
-----------------------------------------------
TOTAL                                  142/142 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
```

Les warnings Xerial SQLite/JDK24 native access et SLF4J NOP restent connus et non bloquants.

---

# 8. NOW — M3-S6 Historique / comparaison / rétention

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

S6 doit réutiliser les invariants désormais prouvés :

```text
DomainIdentity stable
EntityVersionId occurrence-specific
SpecificationVersion != KnowledgeSnapshot
ACTIVE observable atomiquement
CURRENT / PROPOSED / HISTORICAL explicites
APPLY / PROMOTE / ACTIVATE séparés
```

---

# 9. Checklist bloquante avant M4

| Condition | État | Slice |
|---|---|---|
| TemporalState explicite | ✅ | S1 |
| EntityVersion distinct de DomainIdentity | ✅ | S1 |
| SpecificationVersion | ✅ | S1 |
| CURRENT view sans fuite PROPOSED | ✅ | S1 |
| plusieurs PROPOSED concurrents conservés | ✅ | S1/S4 |
| unicité CURRENT par identité | ✅ | S1/S4 |
| ChangeLifecycleState complet | ✅ | S2 |
| lifecycle distinct de TemporalState | ✅ | S2 |
| `COMPLETED != CURRENT` | ✅ | S2 |
| KnowledgeSnapshot complet | ✅ | S3 |
| activation atomique observable | ✅ | S3 |
| échec avant activation conserve l'ancien ACTIVE | ✅ | S3 |
| stale predecessor rejeté | ✅ | S3 |
| état ACTIVE persiste après redémarrage SQLite | ✅ | S3 |
| persistance métier versionnée | ✅ | S4 |
| ownership snapshot/version explicite | ✅ | S4 |
| CURRENT/PROPOSED persistants et séparés après reopen | ✅ | S4 |
| application/promotion des deltas | ✅ | S5 |
| `APPLY != PROMOTE != ACTIVATE` | ✅ | S5 |
| CURRENT inchangé avant activation | ✅ | S5 |
| historique/comparaison/rétention | 🚧 | S6 |
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
