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

Le port et ses opérations :

```text
TraceabilityStore
  putLink(snapshotId, link)
  putLinks(snapshotId, links)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes)
  incoming(snapshotId, target, relationTypes)
```

Les invariants de définition et de déduplication :

```text
TraceabilityLink identity/definition != KnowledgeSnapshot membership
snapshot A links != snapshot B links
same snapshot + same TraceabilityLinkId + same definition -> idempotent
same TraceabilityLinkId + different definition            -> collision
```

Le schéma SQLite de la migration `V005__snapshot_traceability_persistence.sql` :

| Table | Contenu | Clé |
|---|---|---|
| `traceability_links` | définition immuable : `source_kind`, `source_identity_id`, `relation_type`, `target_kind`, `target_identity_id`, `origin`, `resolution`, `confidence` (nullable), `observed_at` | `link_id` |
| `traceability_link_evidence` | preuves du lien | `(link_id, evidence_id)` |
| `snapshot_traceability_links` | membership, rattaché aux snapshots par clé étrangère | `(snapshot_id, link_id)` |

Ses index :

```text
traceability_links(source_kind, source_identity_id, relation_type, link_id)
traceability_links(target_kind, target_identity_id, relation_type, link_id)
```

`outgoing` et `incoming` sont des primitives de stockage indexables, déterministes, pas un moteur de traversée ; en
SQLite elles joignent le membership à la définition. SQLite reconstruit exactement endpoints typés, relation,
`origin`, `resolution`, `confidence`, identifiants de preuves et `observedAt`.

`MemoryTraceabilityStore` tient une table des définitions (`TraceabilityLinkId -> TraceabilityLink`) et une table des
memberships (`KnowledgeSnapshotId -> Set<TraceabilityLinkId>`).

## Decision

1. **Port** : `TraceabilityStore` (`putLink`, `putLinks`, `findLink`, `outgoing`, `incoming`), indépendant de SQLite
   et de tout moteur de graphe, implémenté par `MemoryTraceabilityStore` et `SqliteTraceabilityStore` sur la fondation
   snapshot, au même contrat.
2. **Lot** : `putLinks` écrit en tout ou rien ; un lien identique déjà défini rejoint le snapshot sans réécriture, un
   doublon identique du lot est stocké une fois ; une définition différente sous une identité déjà utilisée (store ou
   lot) refuse tout le lot par une `KnowledgeStoreException` qui nomme l'identité.
3. **Définition et membership** : un `TraceabilityLinkId` identifie une définition immuable, partageable entre
   snapshots si strictement identique ; réutilisé avec un autre contenu, il est rejeté. Deux `TraceabilityLinkId`
   distincts ne sont jamais fusionnés, même à endpoints et relation identiques.
4. **Snapshot obligatoire** : toute écriture exige un `KnowledgeSnapshotId` connu, pas forcément `ACTIVE` (`BUILDING`,
   `VALIDATING`, `READY` admis) ; l'isolation vient du membership explicite.
5. **Requêtes directes** : `outgoing`/`incoming` sont snapshot-scoped, filtrables par `TraceabilityRelationType`
   (ensemble vide = toutes), triées par `TraceabilityLinkId`, sans arête inverse ; la traversée relève d'ADR-0040.
6. **SQLite** : schéma normalisé de `V005__snapshot_traceability_persistence.sql`, sans JSON générique ; le
   membership `snapshot_traceability_links` (clé `(snapshot_id, link_id)`, sans index qui la répète) ne duplique aucun
   endpoint. Toutes les valeurs du lien survivent à la réouverture du fichier.
7. **Mémoire** : vérifie le snapshot par `SpecificationKnowledgeStore` ; adaptateur de référence.
8. **Hors décision** : traversée, dérivation depuis `NormalizedProjectContent`, mapping provider, résolution MINOS,
   fuzzy matching, invalidation incrémentale complète.

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
