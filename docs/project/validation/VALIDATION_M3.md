# Validation M3 — MORPHEUS

Statut : **M3 VALIDÉE — M4 autorisée après intégration de la PR #26**

Date : 23 juillet 2026

---

# 1. Décision

La phase **M3 — État temporel, lifecycle, snapshots et versions** est validée techniquement.

Question de sortie :

> **MORPHEUS peut-il publier et requêter un état `CURRENT` cohérent tout en conservant séparément les propositions, l'historique et les changements en cours, sans jamais exposer un snapshot partiellement construit ?**

Réponse :

```text
OUI
```

Sous les invariants M3 validés :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
PROPOSED never leaks into CURRENT
ACTIVE observable atomiquement
BUILDING / VALIDATING / READY / FAILED non observables comme CURRENT
APPLY != PROMOTE != ACTIVATE
COMPLETED != CURRENT
COMPLETED != PROMOTE
COMPLETED != ACTIVATE
published history = RETIRED* -> ACTIVE
logical rollback != reactivate RETIRED
```

M4 pourra être démarrée après intégration de la PR #26 dans `main`.

---

# 2. Progression des preuves

| Slice | Sujet | PR | ADR | Gate |
|---|---|---|---|---|
| M3-S1 | TemporalState + SpecificationVersion | #21 | ADR-0031 | 103/103 PASS |
| M3-S2 | ChangeLifecycleState | #22 | ADR-0032 | 119/119 PASS |
| M3-S3 | KnowledgeSnapshot + activation atomique | #23 | ADR-0033 | 127/127 PASS |
| M3-S4 | persistance métier versionnée Requirement | #24 | ADR-0034 | 134/134 PASS |
| M3-S5 | application / promotion des RequirementDelta | #25 | ADR-0035 | 142/142 PASS |
| M3-S6 | historique / comparaison / rollback / rétention | #26 | ADR-0036 | 147/147 PASS |

---

# 3. Modèle temporel validé

M3 stabilise :

```text
DomainIdentity
    !=
EntityVersionId
    ↓ occurrence
SpecificationVersionId
    ↓ version métier
KnowledgeSnapshotId
    ↓ projection technique
TemporalState
```

États temporels :

```text
CURRENT
PROPOSED
HISTORICAL
```

Garanties :

```text
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents autorisés
PROPOSED never leaks into CURRENT
technical reingestion != implicit business version
```

`HISTORICAL` reste un état temporel explicite du modèle ; un snapshot `RETIRED` ne force pas rétroactivement ses occurrences `CURRENT` à devenir `HISTORICAL`.

---

# 4. Lifecycle des changements

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

Séparations validées :

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Une transition métier n'a aucun effet implicite sur la publication temporelle.

---

# 5. KnowledgeSnapshot et publication atomique

Lifecycle :

```text
BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED
                         \-> FAILED
```

Garanties :

```text
seul ACTIVE est observable comme snapshot courant
un projet possède au plus un ACTIVE
FAILED n'évince jamais l'ACTIVE existant
stale predecessor est rejeté
ACTIVE / RETIRED uniquement via activation
transition d'état technique par CAS explicite
```

Publication :

```text
ancien ACTIVE -> RETIRED
nouveau READY -> ACTIVE
```

La bascule observable est atomique.

---

# 6. Persistance métier versionnée

Vertical slice M3 : `Requirement`.

Schéma V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Ownership persistante :

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

Garanties :

```text
snapshot/version ownership obligatoire
1 CURRENT max par (snapshot, DomainIdentity)
N PROPOSED concurrents permis
aucune payload JSON générique
reopen SQLite conserve CURRENT / PROPOSED séparés
```

Vue courante :

```text
project
  ↓
ACTIVE snapshot
  ↓
CURRENT occurrence
```

---

# 7. Application, promotion et activation

M3-S5 valide :

```text
normalized delta != applied delta
APPLY != PROMOTE
PROMOTE != ACTIVATE
COMPLETED != PROMOTE
COMPLETED != ACTIVATE
```

`APPLY` :

```text
ACTIVE CURRENT baseline
    + RequirementDelta[]
    ↓
nouvelle SpecificationVersion
BUILDING candidate
RequirementVersionRecord[] CURRENT
```

`PROMOTE` :

```text
BUILDING -> VALIDATING -> READY
                     \-> FAILED
```

`ACTIVATE` :

```text
READY -> ACTIVE
ancien ACTIVE -> RETIRED
```

Sémantique des deltas :

```text
ADDED    -> nouvelle identité logique seulement si réellement nouvelle
MODIFIED -> même DomainIdentity, nouvel EntityVersionId
REMOVED  -> absent du candidat uniquement
```

Aucun fuzzy matching par titre, chemin, contenu ou similarité.

---

# 8. Historique publié

L'historique publié est une lignée explicite :

```text
RETIRED* -> ACTIVE
```

Les états suivants ne constituent jamais un historique publié :

```text
BUILDING
VALIDATING
READY
FAILED
```

Une requête historique doit adresser un snapshot explicite `ACTIVE` ou `RETIRED` et ne retourne que ses occurrences `CURRENT`.

Invariant :

```text
snapshot RETIRED != occurrence TemporalState.HISTORICAL
```

---

# 9. Comparaison de snapshots

Taxonomie validée :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

Continuité : `DomainIdentity`.

Classification : contenu `Requirement` normalisé complet.

Ne déterminent pas la classification :

```text
EntityVersionId
SpecificationVersionId
KnowledgeSnapshotId
TemporalState
```

Donc un nouvel `EntityVersionId` seul ne transforme pas un élément métier inchangé en `MODIFIED`.

`MOVED / RENAMED` ne sont pas introduits implicitement.

---

# 10. Rollback logique

Invariant :

```text
RETIRED -X-> ACTIVE
```

Un rollback vers un état historique ne réactive jamais l'ancien snapshot.

Il construit :

```text
ACTIVE current -> RETIRED target
        ↓ diff
RequirementDelta[]
        ↓ APPLY
nouvelle SpecificationVersion
nouveau BUILDING
        ↓ PROMOTE
READY
        ↓ ACTIVATE
nouvel ACTIVE
```

La reconstruction crée de nouveaux `EntityVersionId` et conserve le snapshot historique intact.

Un changement cross-specification reste comparable comme `MODIFIED`, mais son rollback est rejeté tant qu'une politique explicite `MOVED`/reparenting n'existe pas.

---

# 11. Politique de rétention

Politique M3 :

```text
KEEP_ALL_PUBLISHED
```

Sont conservés :

```text
ACTIVE
RETIRED
SpecificationVersion bindings
RequirementVersionRecord
```

M3 n'introduit ni TTL, ni purge destructive, ni limite de cardinalité, ni compactage.

---

# 12. Gate final M3-S6

Commande officielle :

```text
.\mvnw.cmd clean test
```

Compilation :

```text
javac release 21
```

Résultats :

```text
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

Gate terminé le **23 juillet 2026 à 10:50:47 +02:00**.

Le premier passage avait détecté un défaut de compilation Java 21 dans `RequirementLogicalRollbackService`. Il a été corrigé avant ce gate final ; la preuve de sortie est donc le second passage complet vert.

---

# 13. Warnings connus non bloquants

1. Xerial SQLite/JDK24 signale l'accès natif via `System::load` ; à traiter avant stabilisation runtime/CLI.
2. ArchUnit émet un warning SLF4J NOP ; aucun logger n'est ajouté uniquement pour le masquer.
3. GitHub Actions reste optionnelle et non bloquante ; la porte officielle est le Maven Wrapper local Windows.

---

# 14. Checklist de sortie M3

| Condition | État |
|---|---|
| TemporalState explicite | ✅ |
| DomainIdentity != EntityVersionId | ✅ |
| SpecificationVersion != KnowledgeSnapshot | ✅ |
| CURRENT sans fuite PROPOSED | ✅ |
| plusieurs PROPOSED concurrents | ✅ |
| ChangeLifecycleState complet et séparé | ✅ |
| `COMPLETED != CURRENT` | ✅ |
| KnowledgeSnapshot lifecycle complet | ✅ |
| activation atomique | ✅ |
| FAILED conserve l'ancien ACTIVE | ✅ |
| stale predecessor rejeté | ✅ |
| persistance métier versionnée | ✅ |
| ownership snapshot/version explicite | ✅ |
| reopen SQLite stable | ✅ |
| `APPLY != PROMOTE != ACTIVATE` | ✅ |
| ADDED/MODIFIED/REMOVED déterministes | ✅ |
| historique publié RETIRED* -> ACTIVE | ✅ |
| candidats non publiés exclus de l'historique | ✅ |
| comparaison ADDED/MODIFIED/REMOVED/UNCHANGED | ✅ |
| rollback logique sans réactivation RETIRED | ✅ |
| rétention KEEP_ALL_PUBLISHED | ✅ |
| gate final M3 | ✅ 147/147 |

---

# 15. Porte de sortie

```text
M3 = VALIDÉE
ADR-0036 = ACCEPTÉE — M3
M3-S6 = VALIDÉ
6/6 slices = VALIDÉS
M4 = AUTORISÉE APRÈS MERGE PR #26
```

L'issue #20 reste ouverte jusqu'à l'intégration de la PR #26 dans `main`. Sa fermeture administrative matérialisera la fin de M3 après merge explicite.
