# ADR-0038 — Persistance de traçabilité snapshot-scoped

- Statut : **Acceptée — M4**
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

## Décision

Introduire un port applicatif dédié :

```text
TraceabilityStore
  putLink(snapshotId, link)
  findLink(snapshotId, linkId)
  outgoing(snapshotId, source, relationTypes)
  incoming(snapshotId, target, relationTypes)
```

Le port reste indépendant de SQLite et d'un moteur graphe.

Les adapters sont dédiés :

```text
MemoryTraceabilityStore
SqliteTraceabilityStore
```

Ils s'appuient sur la fondation snapshot existante sans gonfler artificiellement `SpecificationKnowledgeStore` ou `VersionedRequirementStore`.

## Définition du lien != membership snapshot

Invariant :

```text
TraceabilityLink identity/definition != KnowledgeSnapshot membership
```

Un `TraceabilityLinkId` identifie une définition immuable. Le même lien peut appartenir à plusieurs snapshots si sa définition est strictement identique.

Une tentative de réutiliser le même `TraceabilityLinkId` avec un contenu différent est rejetée explicitement.

## Snapshot obligatoire

Toute écriture exige un `KnowledgeSnapshotId` déjà connu du store.

S2 n'impose pas que le snapshot soit `ACTIVE` : les liens peuvent être préparés dans un candidat `BUILDING/VALIDATING/READY` avant activation. L'isolation provient du membership snapshot explicite.

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

Un ensemble de relations vide signifie « toutes les relations ».

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

Les foreign keys rattachent le membership aux snapshots existants.

Les index sont adaptés au schéma normalisé :

```text
traceability_links(source_kind, source_identity_id, relation_type, link_id)
traceability_links(target_kind, target_identity_id, relation_type, link_id)
snapshot_traceability_links(snapshot_id, link_id)
```

La requête directe joint le membership snapshot à la définition ; aucun endpoint n'est dupliqué dans la table de membership.

## Memory

`MemoryTraceabilityStore` implémente le même port avec :

```text
linkDefinitions: TraceabilityLinkId -> TraceabilityLink
snapshotMembership: KnowledgeSnapshotId -> Set<TraceabilityLinkId>
```

Il reçoit le `SpecificationKnowledgeStore` snapshot comme dépendance pour vérifier l'existence du snapshot et reste l'adapter de référence sémantique.

## Reconstruction

SQLite reconstruit exactement :

- endpoints typés ;
- relation ;
- origin ;
- resolution ;
- confidence ;
- evidence IDs ;
- observedAt.

La fermeture/réouverture du fichier SQLite ne modifie aucune de ces valeurs.

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

## Preuves ajoutées

`TraceabilityPersistenceContractTest` exerce cinq scénarios :

1. round-trip + queries directes Memory ;
2. même contrat SQLite ;
3. snapshot inconnu + collision d'identité Memory ;
4. même contrat SQLite ;
5. close/reopen SQLite avec conservation définition/evidence/memberships.

`SqliteSchemaMigrationTest` est étendu à V005 et vérifie les trois tables, les index, le ledger à cinq entrées et l'absence de colonne JSON.

## Critères d'acceptation — validés

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

Gate local Windows exécuté le **23 juillet 2026 à 12:47:46 +02:00** :

```text
.\mvnw.cmd clean test
javac release 21

TraceabilityPersistenceContractTest     5/5 PASS
LayerDependencyTest                     2/2 PASS

Domain                                 21 tests
Application                            54 tests
OpenSpec provider                      26 tests
Synthetic provider                      7 tests
SQLite store                            7 tests
Architecture tests                     45 tests
----------------------------------------------
TOTAL                                 160/160 PASS
Failures                                0
Errors                                  0
Skipped                                 0
BUILD SUCCESS
Total time                            17.136 s
```

Warnings connus non bloquants : Xerial SQLite/JDK native-access et SLF4J NOP.

Conclusion : **ADR-0038 acceptée — M4-S2 validé techniquement**.