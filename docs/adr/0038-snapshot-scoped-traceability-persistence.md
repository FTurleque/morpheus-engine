# ADR-0038 — Persistance de traçabilité snapshot-scoped

- Statut : **Proposée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0003, ADR-0005, ADR-0010, ADR-0018, ADR-0021, ADR-0033, ADR-0037
- Portée : M4-S2, persistance Memory + SQLite

## Contexte

M4-S1 a stabilisé `TraceabilityLink` et sa taxonomie provider-neutral. S2 doit maintenant persister ces liens sans mélanger deux générations de connaissance et sans réintroduire une sémantique de graphe dans les adapters.

Baseline validée :

```text
M4-S1 = 155/155 PASS
main   = 07d9bb1c2c85501ad5a5f6a1eab562a27ec53e9f
```

## Décision candidate

Introduire un port applicatif dédié :

```text
TraceabilityStore
  putLink(snapshotId, link)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes)
  incoming(snapshotId, target, relationTypes)
```

Le port reste indépendant de SQLite et d'un moteur graphe.

## Définition du lien != membership snapshot

Invariant :

```text
TraceabilityLink identity/definition != KnowledgeSnapshot membership
```

Un `TraceabilityLinkId` identifie une définition immuable. Le même lien peut appartenir à plusieurs snapshots si sa définition est strictement identique.

Une tentative de réutiliser le même `TraceabilityLinkId` avec un contenu différent doit être rejetée explicitement.

## Snapshot obligatoire

Toute écriture exige un `KnowledgeSnapshotId` déjà connu du store.

S2 n'impose pas que le snapshot soit `ACTIVE` : les liens doivent pouvoir être préparés dans un candidat `BUILDING/VALIDATING/READY` avant activation. L'isolation provient du membership snapshot explicite.

```text
snapshot A links != snapshot B links
```

## Déduplication

Deux règles distinctes :

```text
same snapshot + same TraceabilityLinkId + same definition -> idempotent
same TraceabilityLinkId + different definition            -> collision
```

S2 ne fusionne pas deux `TraceabilityLinkId` distincts même si leurs endpoints et relation sont identiques. Cette distinction préserve l'identité explicite décidée en ADR-0037.

## Requêtes directes

`outgoing` et `incoming` sont des primitives de stockage indexables, pas encore un moteur de traversée.

Elles sont :

- snapshot-scoped ;
- filtrables par ensemble de `TraceabilityRelationType` ;
- déterministes, triées par `TraceabilityLinkId` ;
- sans matérialisation d'une arête inverse.

La traversée multi-niveaux et `findPath` restent M4-S4.

## SQLite

S2 utilise une migration **V005** normalisée :

```text
traceability_links
traceability_link_evidence
snapshot_traceability_links
```

### `traceability_links`

Contient la définition immuable :

```text
link_id PK
source_kind
source_identity_id
relation_type
target_kind
target_identity_id
origin
resolution
confidence nullable
observed_at
```

### `traceability_link_evidence`

```text
(link_id, evidence_id) PK
```

Aucun JSON générique n'est introduit.

### `snapshot_traceability_links`

```text
(snapshot_id, link_id) PK
```

Les foreign keys rattachent le membership aux snapshots existants. Des index couvrent `(snapshot_id, source_kind, source_identity_id)` et `(snapshot_id, target_kind, target_identity_id)`.

## Memory

`MemorySpecificationKnowledgeStore` implémente le même port avec :

```text
linkDefinitions: TraceabilityLinkId -> TraceabilityLink
snapshotMembership: KnowledgeSnapshotId -> Set<TraceabilityLinkId>
```

Il reste l'implémentation de référence sémantique.

## Reconstruction

SQLite doit reconstruire exactement :

- endpoints typés ;
- relation ;
- origin ;
- resolution ;
- confidence ;
- evidence IDs ;
- observedAt.

La fermeture/réouverture du fichier SQLite ne doit modifier aucune de ces valeurs.

## Frontières

S2 ne fait pas :

```text
traverse / findPath
dérivation NormalizedProjectContent
mapping provider
résolution MINOS
fuzzy matching
suppression/invalidation incrémentale complète
```

## Critères d'acceptation

ADR-0038 pourra passer à **Acceptée — M4** lorsque le gate local complet démontre :

1. snapshot obligatoire et connu ;
2. définition du lien distincte du membership ;
3. idempotence même snapshot/même lien ;
4. collision si même `TraceabilityLinkId` change de définition ;
5. isolation stricte entre snapshots ;
6. outgoing/incoming filtrés et déterministes ;
7. inverse query sans seconde arête ;
8. même contrat Memory et SQLite ;
9. close/reopen SQLite conserve les liens ;
10. migration V005 appliquée et rejouable ;
11. aucune payload JSON générique ;
12. aucun type SQLite dans domain/application ;
13. gate `\.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
