# ADR-0033 — Compléter le lifecycle KnowledgeSnapshot et préserver l'activation atomique

- Statut : **Proposée — validation M3-S3 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0012, ADR-0021, ADR-0030, ADR-0031, ADR-0032
- Portée : M3-S3, lifecycle technique des snapshots, validation, activation atomique

## Contexte

M1 a déjà introduit `KnowledgeSnapshotMetadata`, les états techniques et une activation atomique minimale dans `SpecificationKnowledgeStore`.

M3-S3 doit compléter le lifecycle avant activation sans dupliquer le store ni introduire prématurément les tables métier de M3-S4.

Cycle retenu :

```text
BUILDING
   ↓
VALIDATING
   ↓
READY
   ↓
ACTIVE
   ↓
RETIRED

FAILED
```

`FAILED` est atteignable lorsqu'une construction/validation échoue avant publication.

## Décision proposée

### 1. Conserver le port existant

`SpecificationKnowledgeStore` reste l'unique port de persistance des métadonnées de snapshot.

Il est étendu avec une transition compare-and-set :

```text
transitionSnapshotState(snapshotId, expectedState, targetState)
```

Cette opération :

- exige l'état source attendu ;
- échoue si le snapshot a changé entre-temps ;
- ne peut pas produire `ACTIVE` ou `RETIRED` directement ;
- laisse `activateSnapshot(...)` propriétaire de la publication atomique.

### 2. Introduire SnapshotLifecycleService

Le service applicatif orchestre :

```text
BUILDING -> VALIDATING
VALIDATING -> READY
VALIDATING -> FAILED
READY -> ACTIVE     // via activateSnapshot
old ACTIVE -> RETIRED
```

Il ne contient aucune logique SQLite.

### 3. Validation explicite

Introduire :

```text
SnapshotValidator
SnapshotValidationResult
SnapshotValidationException
```

Un résultat valide mène à `READY`.

Un résultat invalide mène à `FAILED`.

Une exception du validator mène également à `FAILED` avant propagation contrôlée de l'erreur.

### 4. Snapshot courant

La seule vue courante est :

```text
store.activeSnapshot(projectId)
```

Donc :

```text
BUILDING   invisible
VALIDATING invisible
READY      invisible
FAILED     invisible
ACTIVE     visible
RETIRED    historique
```

### 5. Activation atomique

L'activation reste :

```text
Vn ACTIVE
Vn+1 READY(predecessor=Vn)
        ↓ activation atomique
Vn RETIRED
Vn+1 ACTIVE
```

Le store vérifie simultanément :

```text
target.state == READY
target.predecessor == expectedActive
currentActive == expectedActive
```

Un predecessor stale produit `SnapshotConflictException` et ne modifie pas l'actif.

### 6. Échec avant activation

Oracle :

```text
Vn ACTIVE
Vn+1 BUILDING -> VALIDATING -> FAILED
=> Vn reste ACTIVE
```

La présence d'un candidat invalide ne modifie jamais le snapshot courant.

## Frontières

```text
KnowledgeSnapshotState != TemporalState
KnowledgeSnapshotState != ChangeLifecycleState
SpecificationVersion != KnowledgeSnapshot
```

S3 ne crée pas encore le membership persistant entre contenu métier, `SpecificationVersion` et snapshot. Cette responsabilité reste M3-S4 conformément à ADR-0030.

## SQLite

Aucune migration SQL n'est nécessaire : `knowledge_snapshots.state` supporte déjà les six états et l'unicité `ACTIVE` par projet est protégée par un index unique partiel.

Le CAS de transition utilise une mise à jour conditionnelle sur `(id, state)`.

L'activation multi-lignes reste transactionnelle.

## Hors périmètre S3

- tables métier versionnées : S4 ;
- snapshot/content membership persistant : S4 ;
- application/promotion de deltas : S5 ;
- rétention et comparaison complètes : S6 ;
- event sourcing ;
- multi-writer distribué ;
- traçabilité M4.

## Critères d'acceptation

ADR-0033 passe à **Acceptée — M3** lorsque le build complet démontre :

1. cycle `BUILDING -> VALIDATING -> READY -> ACTIVE -> RETIRED` ;
2. validation invalide -> `FAILED` ;
3. exception de validation -> `FAILED` ;
4. seul `ACTIVE` est retourné comme snapshot courant ;
5. un échec avant activation conserve l'ancien `ACTIVE` ;
6. activation réussie retire l'ancien actif et publie le nouveau ;
7. predecessor stale rejeté sans changer l'actif ;
8. activation avant `READY` rejetée ;
9. transition CAS rejette un état source devenu stale ;
10. memory et SQLite respectent le même contrat ;
11. aucune migration métier ni payload JSON générique n'est ajoutée ;
12. `.\mvnw.cmd clean test` est vert.
