---
id: DATA-0041
title: "Références externes snapshot-scoped et traçabilité non résolue"
domain: data
rules: false
---

# Références externes snapshot-scoped et traçabilité non résolue

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

Le domaine possède `ExternalReference` et un resolver optionnel (ADR-0026). MORPHEUS doit relier ses entités à ces
références tout en conservant la preuve lorsque la cible externe est indisponible, supprimée ou sans resolver.

Persister la seule identité d'un endpoint `EXTERNAL_REFERENCE` ne suffit pas : ses coordonnées (`system`, `project`,
`resourceType`, `externalId`, `revision`) doivent rester observables après réouverture de la base.

## Decision

### Port snapshot-scoped

```text
ExternalReferenceStore
  putReference(snapshotId, reference)
  findReference(snapshotId, referenceId)
  findByOwner(snapshotId, ownerId)
```

Adaptateurs : `MemoryExternalReferenceStore`, `SqliteExternalReferenceStore`.

La clé logique est `ExternalReferenceId`. Dans un snapshot donné, la même identité est immuable :

```text
same snapshot + same id + same value       -> idempotent
same snapshot + same id + different value -> collision explicite
```

Le même `ExternalReferenceId` peut apparaître dans plusieurs snapshots avec un état de résolution différent.

### Persistance SQLite

La migration V006 est normalisée, sans JSON générique :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

La ligne principale conserve le snapshot, l'identité de la référence, l'identité de son propriétaire, les
coordonnées (`system`, `project`, `resource_type`, `external_id`, `revision`), l'état et la raison de résolution, la
cible résolue éventuelle et la provenance éventuelle. Les attributs de `ResolvedExternalTarget` sont stockés en table
clé/valeur. L'historique de résolution est ordonné par `event_index`.

### Lien externe canonique

La fabrique applicative provider-neutral `ExternalTraceabilityLinkFactory` reçoit explicitement :

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

et produit une arête vers `TraceabilityEntityKind.EXTERNAL_REFERENCE` / `ExternalReference.id.value`. Aucun
identifiant de lien n'est généré ou hashé implicitement.

Relations externes autorisées :

```text
LINKS_TO_CODE
LINKS_TO_TEST
VERIFIED_BY
SATISFIES
```

`ExternalReference.ownerId` doit correspondre à `source.identity`.

### Deux axes de résolution distincts

`TraceabilityResolutionState` décrit l'état de résolution de l'arête au moment de l'observation.
`ExternalReferenceResolutionState` décrit l'état observé de la cible externe : `UNVALIDATED`, `UNRESOLVED`, `RESOLVED`,
`STALE`.

Projection au moment de l'observation :

```text
UNVALIDATED -> TraceabilityResolutionState.UNRESOLVED
UNRESOLVED  -> TraceabilityResolutionState.UNRESOLVED
RESOLVED    -> TraceabilityResolutionState.RESOLVED
STALE       -> TraceabilityResolutionState.PARTIALLY_RESOLVED
```

`HEURISTIC` n'est jamais synthétisé à partir d'une indisponibilité externe : il reste une sémantique de lien
explicite distincte. Une résolution ultérieure d'une `ExternalReference` ne mute jamais un `TraceabilityLink` déjà
observé.

### Référence cassée

`ExternalTraceabilityQueryService` rend une `ExternalTraceabilityView` qui conserve toujours le `TraceabilityLink`
canonique et expose sa disponibilité (`ExternalTraceabilityAvailability`) :

```text
REFERENCE_UNVALIDATED
REFERENCE_UNRESOLVED
REFERENCE_RESOLVED
REFERENCE_STALE
BROKEN_REFERENCE
```

`BROKEN_REFERENCE` signifie que l'arête snapshot-scoped existe mais que l'`ExternalReference` correspondante manque
dans ce snapshot. L'arête reste visible et explicable ; elle n'est jamais supprimée silencieusement.

### Découplage cross-engine

La résolution passe par `ExternalReferenceResolver` (`system()`, `resolve(target)`). Aucune dépendance de compilation
à MINOS n'est introduite.

```text
MINOS absent/unavailable != MORPHEUS unavailable
NO_RESOLVER              != suppression du lien
TARGET_NOT_FOUND         != suppression du lien
TARGET_UNAVAILABLE       != suppression du lien
STALE                    != suppression du lien
```

La résolution live, sans mutation du snapshot, relève d'ADR-0071.

## Do's and Don'ts

### Do

- Scoper chaque `ExternalReference` persistée par snapshot.
- Traiter une réécriture identique comme idempotente et une valeur différente sous la même identité comme une collision
  explicite.
- Fournir explicitement l'identifiant de lien, l'origine, la confiance, les preuves et l'instant d'observation.
- Exiger que le propriétaire de la référence soit la source du lien.
- Garder visible une arête dont la référence manque, sous `BROKEN_REFERENCE`.
- Garder la même sémantique dans `store-memory` et `store-sqlite`.

### Don't

- Ne pas stocker une référence externe en JSON générique.
- Ne pas générer ni hasher implicitement un identifiant de lien.
- Ne pas créer un lien externe hors de `LINKS_TO_CODE`, `LINKS_TO_TEST`, `VERIFIED_BY` et `SATISFIES`.
- Ne pas synthétiser `HEURISTIC` depuis une indisponibilité externe.
- Ne pas muter un `TraceabilityLink` observé lors d'une résolution ultérieure.
- Ne pas supprimer un lien parce que le resolver est absent ou que la cible est introuvable, indisponible ou périmée.
- Ne pas dépendre de MINOS à la compilation.

## Consequences

### Positives

- Une référence externe reste observable, coordonnées et historique compris, après réouverture.
- L'indisponibilité d'un moteur externe n'efface aucune preuve de traçabilité.
- Une référence manquante est un état nommé, pas une disparition.

### Négatives

- Deux axes de résolution distincts à comprendre et à projeter.
- Trois tables normalisées à maintenir pour une référence.

### Risques

- L'état de résolution d'une arête est celui de son observation : un consommateur qui ne lirait que l'arête ignorerait
  qu'une référence a été résolue ou est devenue périmée après l'observation ; la disponibilité de la vue porte cet état.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ExternalTraceabilityContractTest.java` :
  round-trip Memory et SQLite, réouverture qui préserve coordonnées, attributs résolus, provenance, historique et lien,
  `BROKEN_REFERENCE` visible sur les deux stores, identité variable entre snapshots mais immuable dans un snapshot.
- `morpheus-application/src/test/java/com/morpheus/application/traceability/ExternalTraceabilityLinkFactoryTest.java` :
  relations autorisées, propriétaire, projection des états de résolution sans `HEURISTIC`, resolver absent ou
  indisponible sans mutation du lien canonique.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : aucune dépendance de
  `domain` ni d'`application` vers `com.minos..`.

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration cross-engine.
- [ADR-0026](ARCH-0026-optional-external-reference-resolution.md) — résolution optionnelle des références externes.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance snapshot-scoped de la traçabilité.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée.
- [ADR-0071](DATA-0071-live-external-resolution-without-snapshot-mutation.md) — résolution live sans mutation.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [VALIDATION_M4](../../project/validation/VALIDATION_M4.md)
