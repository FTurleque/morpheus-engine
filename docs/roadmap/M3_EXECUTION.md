# M3 — Plan d'exécution détaillé

Statut : **M3 VALIDÉ — 6 slices sur 6 ; intégration PR #26 requise avant M4**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et sert de tableau de bord opérationnel pour M3.

---

# 1. Position finale

```text
C0     ✅ validé
M0     ✅ validé
M1     ✅ validé
M2     ✅ validé — 8/8 — 94/94
M3     ✅ validé techniquement
  S1   ✅ TemporalState + SpecificationVersion — PR #21 — ADR-0031 — 103/103
  S2   ✅ ChangeLifecycleState — PR #22 — ADR-0032 — 119/119
  S3   ✅ KnowledgeSnapshot complet — PR #23 — ADR-0033 — 127/127
  S4   ✅ persistance métier versionnée — PR #24 — ADR-0034 — 134/134
  S5   ✅ application / promotion des deltas — PR #25 — ADR-0035 — 142/142
  S6   ✅ historique / comparaison / rétention — PR #26 — ADR-0036 — 147/147
M4     ⏳ autorisé après merge de la PR #26
```

Progression :

```text
M3 : [████████████████████] 6 / 6 slices validés
```

Preuve de sortie : [`../VALIDATION_M3.md`](../VALIDATION_M3.md).

---

# 2. Question de sortie M3

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

Réponse validée :

```text
OUI
```

Oracle de visibilité :

```text
CURRENT query
    -> ACTIVE snapshot uniquement
    -> TemporalState.CURRENT uniquement

PROPOSED
    -> persiste séparément
    -> ne fuit jamais dans CURRENT

BUILDING / VALIDATING / READY / FAILED
    -> jamais observables comme CURRENT

RETIRED
    -> historique publié adressable explicitement
    -> jamais réactivé directement
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
```

Invariants :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
content normalization != temporal projection
PROPOSED never leaks into CURRENT
technical reingestion != implicit business version
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
SPECIFIED -> PLANNED si design_required=false + plan
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

Invariants :

```text
seul ACTIVE est observable comme snapshot courant
un projet possède au plus un ACTIVE
stale predecessor est rejeté
FAILED n'évince jamais l'ACTIVE existant
ACTIVE/RETIRED uniquement via activateSnapshot
transitionSnapshotState applique un CAS explicite
```

SQLite conserve `ACTIVE/RETIRED` après fermeture/réouverture.

Preuve : `127/127 PASS`.

---

# 6. M3-S4 — VALIDÉ : persistance métier versionnée

ADR : **ADR-0034 — Acceptée — M3**.

Premier vertical slice : `Requirement`.

Migration V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Ownership :

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

Relation :

```text
SpecificationVersion 1 <--- N KnowledgeSnapshot
```

Invariants :

```text
snapshot/version ownership obligatoire
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents autorisés
aucune payload JSON générique
reopen SQLite conserve CURRENT / PROPOSED séparés
```

Preuve : `134/134 PASS`.

---

# 7. M3-S5 — VALIDÉ : application / promotion explicite des deltas

ADR : **ADR-0035 — Acceptée — M3**.

Séparation :

```text
normalized delta != applied delta
APPLY != PROMOTE
PROMOTE != ACTIVATE
COMPLETED != PROMOTE
COMPLETED != ACTIVATE
```

Flux :

```text
ACTIVE CURRENT baseline
        +
RequirementDelta[]
        ↓ APPLY
nouvelle SpecificationVersion
        ↓
BUILDING candidate
        ↓
RequirementVersionRecord[] CURRENT
        ↓ PROMOTE
VALIDATING
   ├── READY
   └── FAILED
        ↓ ACTIVATE si READY
ACTIVE
```

Sémantique :

```text
ADDED    -> nouvelle identité réellement nouvelle
MODIFIED -> même DomainIdentity + nouvel EntityVersionId
REMOVED  -> absent du candidat uniquement
```

Aucun fuzzy matching.

Preuve : `142/142 PASS`.

---

# 8. M3-S6 — VALIDÉ : historique / comparaison / rétention

ADR : **ADR-0036 — Acceptée — M3**.

## Historique publié

```text
RETIRED* -> ACTIVE
```

`BUILDING`, `VALIDATING`, `READY` et `FAILED` ne sont jamais exposés comme historique publié.

Une requête historique adresse explicitement un snapshot `ACTIVE` ou `RETIRED` et ne retourne que ses occurrences `CURRENT`.

```text
snapshot RETIRED != occurrence TemporalState.HISTORICAL
```

## Comparaison

Taxonomie :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

Continuité par `DomainIdentity` ; comparaison du contenu `Requirement` normalisé.

Les métadonnées suivantes n'impliquent pas une modification :

```text
EntityVersionId
SpecificationVersionId
KnowledgeSnapshotId
TemporalState
```

Donc un nouvel `EntityVersionId` seul reste `UNCHANGED`.

`MOVED / RENAMED` ne sont pas introduits implicitement.

## Rollback logique

Invariant :

```text
RETIRED -X-> ACTIVE
```

Flux validé :

```text
ACTIVE current -> RETIRED target
        ↓ compare
RequirementDelta[]
        ↓ APPLY
nouvelle SpecificationVersion
nouveau BUILDING
        ↓ PROMOTE
READY
        ↓ ACTIVATE
nouvel ACTIVE
```

Le snapshot historique reste intact et de nouveaux `EntityVersionId` sont créés.

Un changement cross-specification est comparable comme `MODIFIED`, mais son rollback est rejeté sans politique `MOVED`/reparenting explicite.

## Rétention

```text
PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED
```

Aucune purge destructive, TTL, limite de cardinalité ou compactage en M3.

Aucune migration V005 : V004 suffit.

## Preuve locale Windows

```text
.\mvnw.cmd clean test
javac release 21

PublishedHistoryContractTest              5/5 PASS
RequirementDeltaApplicationContractTest   8/8 PASS

Domain                                  13 tests
Application                             54 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      40 tests
-----------------------------------------------
TOTAL                                  147/147 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
```

Gate terminé le 23 juillet 2026 à 10:50:47 +02:00.

Warnings connus non bloquants : Xerial SQLite/JDK24 native access et SLF4J NOP.

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
| historique/comparaison/rétention | ✅ | S6 |
| rollback logique sans réactivation RETIRED | ✅ | S6 |
| reopen SQLite conserve historique publié | ✅ | S6 |
| `VALIDATION_M3.md` | ✅ | clôture |

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

`MOVED/RENAMED` reste différé tant qu'une politique explicite de continuité/reparenting n'est pas décidée.

---

# 11. Porte de sortie M3

```text
M3 = VALIDÉE
6 / 6 slices = VALIDÉS
ADR-0036 = ACCEPTÉE — M3
Gate final = 147/147 PASS
M4 = AUTORISÉE APRÈS MERGE PR #26
```

La PR #26 doit rester non mergée jusqu'au signal explicite de poursuite. L'issue #20 reste ouverte jusqu'à cette intégration.

---

# 12. Règle de gouvernance

Après chaque gate vert :

```text
1. inscrire la preuve exacte dans l'ADR
2. mettre la PR Ready
3. merger seulement après signal explicite de poursuite
4. mettre à jour l'issue de milestone
5. avancer la roadmap vers le jalon suivant
6. conserver une validation de sortie explicite
```
