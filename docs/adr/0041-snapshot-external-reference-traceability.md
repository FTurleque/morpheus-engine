# ADR-0041 — Références externes snapshot-scoped et traçabilité non résolue

- Statut : **Proposée — M4**
- Date : 23 juillet 2026
- Dépend de : ADR-0005, ADR-0007, ADR-0026, ADR-0037, ADR-0038, ADR-0040
- Portée : M4-S5, external / unresolved / broken-reference semantics

## Contexte

M4-S4 est intégré avec un gate `174/174 PASS`.

```text
M4-S4 merge = cafbc8e61a4af2ed204cd6fc24dcdd262f6ed9e4
```

Le domaine M2 possède déjà `ExternalReference` et un resolver optionnel. M4 doit maintenant relier des entités MORPHEUS à ces références tout en conservant la preuve lorsque la cible externe est indisponible, supprimée ou sans resolver.

V005 persiste l'identité d'un endpoint `EXTERNAL_REFERENCE`, mais pas ses coordonnées (`system`, `project`, `resourceType`, `externalId`, `revision`). S5 doit donc rendre la référence elle-même observable après reopen SQLite.

## Décision candidate

Introduire un port snapshot-scoped dédié :

```text
ExternalReferenceStore
  putReference(snapshotId, reference)
  findReference(snapshotId, referenceId)
  findByOwner(snapshotId, ownerId)
```

Adapters :

```text
MemoryExternalReferenceStore
SqliteExternalReferenceStore
```

La clé logique reste `ExternalReferenceId`. Dans un snapshot donné, la même identité est immuable :

```text
same snapshot + same id + same value      -> idempotent
same snapshot + same id + different value -> collision explicite
```

Le même `ExternalReferenceId` peut apparaître dans plusieurs snapshots avec un état de résolution différent.

## Persistance SQLite candidate

Migration V006 normalisée, sans JSON générique :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

La ligne principale conserve :

```text
snapshot_id
reference_id
owner_identity_id
system
project
resource_type
external_id
revision
resolution_state
resolution_reason
resolved target éventuelle
provenance éventuelle
```

Les attributs de `ResolvedExternalTarget` sont stockés en table clé/valeur. L'historique de résolution est ordonné par `event_index`.

## Lien externe canonique

Ajouter un factory applicatif provider-neutral :

```text
ExternalTraceabilityLinkFactory
```

Il reçoit explicitement :

```text
TraceabilityLinkId
source TraceabilityEntityRef
relationType
ExternalReference
origin
confidence
EvidenceId set
observedAt
```

et produit une arête vers :

```text
TraceabilityEntityKind.EXTERNAL_REFERENCE
ExternalReference.id.value
```

Aucun ID de lien n'est généré ou hashé implicitement.

Relations externes S5 autorisées :

```text
LINKS_TO_CODE
LINKS_TO_TEST
VERIFIED_BY
SATISFIES
```

`ExternalReference.ownerId` doit correspondre à `source.identity`.

## Deux axes de résolution distincts

`TraceabilityResolutionState` décrit l'état de résolution de l'arête au moment de l'observation.

`ExternalReferenceResolutionState` décrit l'état courant/observé de la cible externe :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

Projection initiale candidate :

```text
UNVALIDATED -> TraceabilityResolutionState.UNRESOLVED
UNRESOLVED  -> TraceabilityResolutionState.UNRESOLVED
RESOLVED    -> TraceabilityResolutionState.RESOLVED
STALE       -> TraceabilityResolutionState.PARTIALLY_RESOLVED
```

`HEURISTIC` n'est jamais synthétisé à partir d'une indisponibilité externe. Il reste une sémantique de lien explicite distincte.

Une résolution ultérieure de `ExternalReference` ne mute jamais un `TraceabilityLink` déjà observé.

## Broken-reference semantics

Ajouter une vue applicative :

```text
ExternalTraceabilityView
ExternalTraceabilityQueryService
```

La vue conserve toujours le `TraceabilityLink` canonique et expose :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

`BROKEN_REFERENCE` signifie que l'arête snapshot-scoped existe mais que l'`ExternalReference` correspondante manque dans ce snapshot. L'arête reste visible et explicable ; elle n'est jamais supprimée silencieusement.

## Découplage cross-engine

S5 réutilise `ExternalReferenceResolver` :

```text
ExternalReferenceResolver
  system()
  resolve(target)
```

Aucune dépendance compile-time à MINOS n'est introduite.

```text
MINOS absent/unavailable != MORPHEUS unavailable
NO_RESOLVER              != suppression du lien
TARGET_NOT_FOUND         != suppression du lien
TARGET_UNAVAILABLE       != suppression du lien
STALE                    != suppression du lien
```

## Frontières

S5 ne fait pas :

```text
résolution MINOS de production
synchronisation/invalidation complète
analyse d'impact
CLI publique trace
MCP/API
```

## Preuves attendues

Le gate S5 devra démontrer :

1. round-trip snapshot-scoped Memory + SQLite d'un `ExternalReference` complet ;
2. close/reopen SQLite conserve coordonnées, resolved attributes, provenance et history ;
3. isolation entre snapshots ;
4. collision explicite dans un même snapshot ;
5. arêtes `LINKS_TO_CODE/LINKS_TO_TEST/VERIFIED_BY/SATISFIES` vers `EXTERNAL_REFERENCE` ;
6. owner mismatch et relation non autorisée rejetés ;
7. mapping RESOLVED / UNRESOLVED / STALE sans synthèse HEURISTIC ;
8. resolver absent ou indisponible ne supprime ni ne mute le lien canonique ;
9. `BROKEN_REFERENCE` reste visible ;
10. Memory et SQLite exposent la même sémantique ;
11. aucune dépendance obligatoire à MINOS ;
12. migration V006 idempotente dans le ledger ;
13. `\.\mvnw.cmd clean test` vert.

## Preuve d'acceptation

À compléter uniquement après gate local complet vert.
