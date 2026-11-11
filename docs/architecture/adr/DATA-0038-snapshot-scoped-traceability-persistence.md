---
id: DATA-0038
title: "Persistance de traçabilité snapshot-scoped"
domain: data
rules: false
---

# Persistance de traçabilité snapshot-scoped

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

`TraceabilityLink` et sa taxonomie provider-neutral sont stables (ADR-0118). Il faut les persister sans mélanger deux
générations de connaissance et sans réintroduire une sémantique de graphe dans les adaptateurs, et sans gonfler
artificiellement `SpecificationKnowledgeStore` ou `VersionedRequirementStore`.

## Decision

### Port

Un port applicatif dédié, indépendant de SQLite et de tout moteur de graphe :

```text
TraceabilityStore
  putLink(snapshotId, link)
  putLinks(snapshotId, links)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes)
  incoming(snapshotId, target, relationTypes)
```

Il est implémenté par `MemoryTraceabilityStore` et `SqliteTraceabilityStore`, qui s'appuient sur la fondation
snapshot existante. `putLinks` écrit un lot snapshot-scoped en tout ou rien : un lien déjà défini à l'identique
rejoint le snapshot sans être réécrit, un lien listé deux fois à l'identique est stocké une fois, et une définition
différente sous une identité déjà utilisée — dans le store ou plus tôt dans le lot — refuse tout le lot par une
`KnowledgeStoreException` qui nomme l'identité.

### Définition du lien distincte du membership

```text
TraceabilityLink identity/definition != KnowledgeSnapshot membership
```

Un `TraceabilityLinkId` identifie une définition immuable. Le même lien peut appartenir à plusieurs snapshots si sa
définition est strictement identique. Réutiliser un `TraceabilityLinkId` avec un contenu différent est rejeté
explicitement.

### Snapshot obligatoire

Toute écriture exige un `KnowledgeSnapshotId` déjà connu du store. Le snapshot n'a pas à être `ACTIVE` : les liens
peuvent être préparés dans un candidat `BUILDING`, `VALIDATING` ou `READY` avant activation. L'isolation vient du
membership explicite : `snapshot A links != snapshot B links`.

### Déduplication

```text
same snapshot + same TraceabilityLinkId + same definition -> idempotent
same TraceabilityLinkId + different definition            -> collision
```

Deux `TraceabilityLinkId` distincts ne sont jamais fusionnés, même si leurs endpoints et leur relation sont
identiques : l'identité explicite du lien est préservée.

### Requêtes directes

`outgoing` et `incoming` sont des primitives de stockage indexables, pas un moteur de traversée. Elles sont
snapshot-scoped, filtrables par ensemble de `TraceabilityRelationType` (un ensemble vide signifie « toutes les
relations »), déterministes et triées par `TraceabilityLinkId`, sans matérialisation d'une arête inverse. La traversée
multi-niveaux relève d'ADR-0040.

### SQLite

La migration `V005__snapshot_traceability_persistence.sql` crée un schéma normalisé, sans JSON générique :

| Table | Contenu | Clé |
|---|---|---|
| `traceability_links` | définition immuable : `source_kind`, `source_identity_id`, `relation_type`, `target_kind`, `target_identity_id`, `origin`, `resolution`, `confidence` (nullable), `observed_at` | `link_id` |
| `traceability_link_evidence` | preuves du lien | `(link_id, evidence_id)` |
| `snapshot_traceability_links` | membership, rattaché aux snapshots par clé étrangère | `(snapshot_id, link_id)` |

Index :

```text
traceability_links(source_kind, source_identity_id, relation_type, link_id)
traceability_links(target_kind, target_identity_id, relation_type, link_id)
```

La recherche du membership d'un snapshot est servie par la clé primaire `(snapshot_id, link_id)` ; aucun index
secondaire ne la répète. La requête directe joint le membership à la définition ; aucun endpoint n'est dupliqué dans la
table de membership.

SQLite reconstruit exactement endpoints typés, relation, `origin`, `resolution`, `confidence`, identifiants de preuves
et `observedAt` ; fermer puis rouvrir le fichier ne modifie aucune de ces valeurs.

### Mémoire

`MemoryTraceabilityStore` implémente le même port avec une table des définitions (`TraceabilityLinkId ->
TraceabilityLink`) et une table des memberships (`KnowledgeSnapshotId -> Set<TraceabilityLinkId>`). Il reçoit le
`SpecificationKnowledgeStore` comme dépendance pour vérifier l'existence du snapshot et reste l'adaptateur de
référence sémantique.

### Hors de cette décision

Traversée, dérivation depuis `NormalizedProjectContent`, mapping provider, résolution MINOS, fuzzy matching,
invalidation incrémentale complète.

## Do's and Don'ts

### Do

- Exiger un snapshot connu pour toute écriture de lien.
- Rejeter explicitement un `TraceabilityLinkId` réutilisé avec une autre définition.
- Trier les résultats de `outgoing` et `incoming` par `TraceabilityLinkId`.
- Écrire un lot de liens en tout ou rien.
- Garder le même contrat sur le store mémoire et sur SQLite.

### Don't

- Ne pas fusionner deux `TraceabilityLinkId` distincts.
- Ne pas matérialiser d'arête inverse.
- Ne pas stocker un lien en JSON générique, ni dupliquer ses endpoints dans le membership.
- Ne pas laisser un type SQLite atteindre `domain` ou `application`.
- Ne pas ajouter de traversée multi-niveaux au port de stockage.

## Consequences

### Positives

- Deux générations de connaissance ne se mélangent jamais.
- Un lien identique est partagé entre snapshots sans duplication de sa définition.
- Requêtes directes déterministes et indexées dans les deux sens.

### Négatives

- Une jointure membership/définition à chaque requête directe.
- Deux adaptateurs à maintenir en parité.

### Risques

- Une sémantique de graphe glissée dans un adaptateur ferait diverger les deux stores : le port s'en tient aux
  primitives directes, et la parité est testée.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityPersistenceContractTest.java` :
  round-trip et requêtes directes, snapshot inconnu et collision d'identité, lot en tout ou rien, sur mémoire et
  SQLite ; réouverture SQLite qui conserve définitions, preuves et memberships.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteSchemaMigrationTest.java` : tables et index de
  traçabilité, absence de l'index de membership redondant.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/adapter/SqliteTraceabilityStorePersistenceTest.java`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : aucun type SQLite
  dans `domain` ni `application`.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — persistance derrière un port.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations SQLite.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée.
- [VALIDATION_M4](../../project/validation/VALIDATION_M4.md).
