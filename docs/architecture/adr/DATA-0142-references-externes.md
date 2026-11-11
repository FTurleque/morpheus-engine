---
id: DATA-0142
title: "Références externes : persistées par snapshot, résolues sur demande, observées sans mutation"
domain: data
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/reference/*ResolutionService.java"]
---

# Références externes : persistées par snapshot, résolues sur demande, observées sans mutation

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0026, ADR-0041, ADR-0071

## Context

MORPHEUS conserve des liens vers MINOS, GitHub, Jira ou tout autre système sans rendre ces systèmes obligatoires. Une
référence vers un système externe doit exister, rester lisible et garder son historique même quand ce système est
absent, indisponible, ou a supprimé sa cible. MORPHEUS relie ses entités à ces références en conservant la preuve
lorsque la cible est indisponible, supprimée ou sans resolver.

Persister la seule identité d'un endpoint `EXTERNAL_REFERENCE` ne suffit pas : ses coordonnées (`system`, `project`,
`resourceType`, `externalId`, `revision`) doivent rester observables après réouverture de la base.

Une cible externe (un symbole MINOS, par exemple) peut évoluer, et l'utilisateur veut en connaître l'état présent ;
or la référence est immuable dans un snapshot et un `TraceabilityLink` conserve sa résolution au moment de
l'observation.

## Decision

### 1. Le domaine porte la référence, l'application porte une résolution optionnelle

Le domaine porte la référence et son état, sans connaître aucun système cible (`com.morpheus.domain.reference`) :

```text
ExternalReferenceId
ExternalReferenceTarget
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolutionReason
ExternalReferenceResolutionEvent
ResolvedExternalTarget
```

L'application porte la résolution, optionnelle (`com.morpheus.application.reference`) :

```text
ExternalReferenceResolver
ExternalReferenceResolverResult
ExternalReferenceResolverRegistry
ExternalReferenceResolutionService
```

```text
MORPHEUS domain
    ↓
ExternalReference
    ↓ optional
ExternalReferenceResolutionService
    ↓
ExternalReferenceResolverRegistry
    ↓
resolver adapter externe
```

Un resolver expose `system()` et `resolve(target)`. Le registre n'accepte qu'un resolver par système : un second
resolver pour le même système est refusé. Aucune dépendance de compilation à MINOS n'est introduite.

Invariants :

```text
DomainIdentity != ExternalReference
ExternalReference peut exister sans resolver
NO_RESOLVER est un résultat explicite, pas une panne
resolver indisponible != panne MORPHEUS
cible supprimée != suppression de la référence
historique de résolution conservé
provenance conservée
```

### 2. États et transitions de résolution

Une référence connaît quatre états : `UNVALIDATED`, `UNRESOLVED`, `RESOLVED`, `STALE`.

```text
UNVALIDATED -- no resolver --> UNRESOLVED / NO_RESOLVER
UNVALIDATED -- found -------> RESOLVED
UNRESOLVED  -- found -------> RESOLVED
RESOLVED    -- missing -----> STALE / TARGET_REMOVED
STALE       -- found -------> RESOLVED
```

Un échec de résolution ne produit `STALE` que pour une référence déjà résolue au moins une fois ; une référence
jamais résolue reste `UNRESOLVED`. Une indisponibilité temporaire produit `TARGET_UNAVAILABLE`. Les autres échecs
rendus par un resolver gardent chacun leur raison (`TARGET_NOT_FOUND`, `TARGET_AMBIGUOUS`,
`TARGET_REVISION_MISMATCH`, `TARGET_TYPE_UNSUPPORTED`) et suivent la même règle `UNRESOLVED` / `STALE`.

### 3. Persistance par snapshot

```text
ExternalReferenceStore
  putReference(snapshotId, reference)
  findReference(snapshotId, referenceId)
  findByOwner(snapshotId, ownerId)
```

Adaptateurs : `MemoryExternalReferenceStore`, `SqliteExternalReferenceStore`, de même sémantique.

La clé logique est `ExternalReferenceId`. Dans un snapshot donné, la même identité est immuable :

```text
same snapshot + same id + same value       -> idempotent
same snapshot + same id + different value -> collision explicite
```

Le même `ExternalReferenceId` peut apparaître dans plusieurs snapshots avec un état de résolution différent.

La migration SQLite V006 est normalisée, sans JSON générique :

```text
snapshot_external_references
snapshot_external_reference_attributes
snapshot_external_reference_history
```

La ligne principale conserve le snapshot, l'identité de la référence, l'identité de son propriétaire, les
coordonnées (`system`, `project`, `resource_type`, `external_id`, `revision`), l'état et la raison de résolution, la
cible résolue éventuelle et la provenance éventuelle. Les attributs de `ResolvedExternalTarget` sont stockés en table
clé/valeur. L'historique de résolution est ordonné par `event_index`.

### 4. Lien de traçabilité vers une référence externe

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
identifiant de lien n'est généré ou hashé implicitement. Relations externes autorisées : `LINKS_TO_CODE`,
`LINKS_TO_TEST`, `VERIFIED_BY`, `SATISFIES`. `ExternalReference.ownerId` doit correspondre à `source.identity`.

`TraceabilityResolutionState` décrit l'état de résolution de l'arête au moment de l'observation ;
`ExternalReferenceResolutionState` décrit l'état observé de la cible externe. Projection au moment de l'observation :

```text
UNVALIDATED -> TraceabilityResolutionState.UNRESOLVED
UNRESOLVED  -> TraceabilityResolutionState.UNRESOLVED
RESOLVED    -> TraceabilityResolutionState.RESOLVED
STALE       -> TraceabilityResolutionState.PARTIALLY_RESOLVED
```

`HEURISTIC` n'est jamais synthétisé à partir d'une indisponibilité externe : il reste une sémantique de lien explicite
distincte. Une résolution ultérieure d'une `ExternalReference` ne mute jamais un `TraceabilityLink` déjà observé.

`ExternalTraceabilityQueryService` rend une `ExternalTraceabilityView` qui conserve toujours le `TraceabilityLink`
canonique et expose sa disponibilité (`ExternalTraceabilityAvailability`) : `REFERENCE_UNVALIDATED`,
`REFERENCE_UNRESOLVED`, `REFERENCE_RESOLVED`, `REFERENCE_STALE`, `BROKEN_REFERENCE`. `BROKEN_REFERENCE` signifie que
l'arête snapshot-scoped existe mais que l'`ExternalReference` correspondante manque dans ce snapshot. L'arête reste
visible et explicable ; elle n'est jamais supprimée silencieusement.

```text
MINOS absent/unavailable != MORPHEUS unavailable
NO_RESOLVER              != suppression du lien
TARGET_NOT_FOUND         != suppression du lien
TARGET_UNAVAILABLE       != suppression du lien
STALE                    != suppression du lien
```

### 5. Observation live sans mutation du snapshot publié

```text
référence persistée dans le snapshot  !=  observation live de sa cible
```

Une résolution live (`ExternalReferenceResolutionService.resolve`) produit une copie transitionnée de la référence et
une vue de résultat, mais n'écrit jamais dans le snapshot publié : elle n'appelle pas `putReference`, et le service ne
détient aucun store.

```text
ACTIVE snapshot reference
       -> ExternalReferenceResolutionService.resolve(...)
       -> observed ExternalReference
       -> response
       -X-> mutation ACTIVE
```

Le résultat live est visible sans être persisté. Une cible indisponible (aucun résolveur, ou pair externe absent)
produit une observation non résolue, avec le même comportement sur la référence publiée. Seule une publication
ultérieure peut persister un nouvel état de référence, dans un nouveau snapshot.

### 6. Hors de cette décision

Le resolver concret MINOS (`MinosMcpExternalReferenceResolver`) et son transport relèvent d'ADR-0128 et d'ADR-0145 ;
les liens de traçabilité en général, d'ADR-0118. Restent exclus : la résolution réseau obligatoire, le retry et la
surveillance en tâche de fond.

## Do's and Don'ts

### Do

- Créer une `ExternalReference` en `UNVALIDATED` sans exiger la présence du système cible.
- Rendre `UNRESOLVED / NO_RESOLVER` quand aucun resolver n'est enregistré pour le système de la cible.
- Traiter l'indisponibilité d'un resolver comme un résultat (`TARGET_UNAVAILABLE`), jamais comme une panne de
  MORPHEUS.
- Conserver l'historique des transitions (`ExternalReferenceResolutionEvent`) et la provenance de la référence.
- Passer à `STALE` une référence déjà résolue dont la cible disparaît, sans supprimer la référence.
- Scoper chaque `ExternalReference` persistée par snapshot.
- Traiter une réécriture identique comme idempotente et une valeur différente sous la même identité comme une collision
  explicite.
- Fournir explicitement l'identifiant de lien, l'origine, la confiance, les preuves et l'instant d'observation.
- Exiger que le propriétaire de la référence soit la source du lien.
- Garder visible une arête dont la référence manque, sous `BROKEN_REFERENCE`.
- Retourner une copie transitionnée de la référence pour toute résolution live.
- Persister un nouvel état de référence uniquement par une nouvelle publication de snapshot.
- Garder la même sémantique dans `store-memory` et `store-sqlite`.

### Don't

- Ne faire dépendre `com.morpheus.domain` d'aucune classe MINOS ou NEXUS, d'aucun SDK GitHub, d'aucun client Jira, et
  ne pas dépendre de MINOS à la compilation.
- Ne pas confondre `DomainIdentity` et `ExternalReference`.
- Ne pas enregistrer deux resolvers pour le même système.
- Ne pas supprimer une référence ou un lien parce que le resolver est absent ou que la cible est introuvable,
  indisponible, périmée ou disparue.
- Ne pas stocker une référence externe en JSON générique.
- Ne pas générer ni hasher implicitement un identifiant de lien.
- Ne pas créer un lien externe hors de `LINKS_TO_CODE`, `LINKS_TO_TEST`, `VERIFIED_BY` et `SATISFIES`.
- Ne pas synthétiser `HEURISTIC` depuis une indisponibilité externe.
- Ne pas muter un `TraceabilityLink` observé, ni son `TraceabilityResolutionState`, lors d'une résolution ultérieure ou
  live.
- Ne pas appeler `putReference` depuis une résolution live.
- Ne pas faire dépendre la lecture d'une référence publiée de la disponibilité de sa cible.

## Consequences

### Positives

- Une référence et son historique survivent à l'absence ou à la disparition du système cible ; coordonnées et
  historique restent observables après réouverture.
- Un système externe se branche par un resolver sans toucher au domaine.
- Les échecs de résolution sont distincts et nommés ; une référence manquante est un état nommé, pas une disparition.
- L'indisponibilité d'un moteur externe n'efface aucune preuve de traçabilité.
- L'audit historique reste fidèle à l'observation publiée : une cible externe peut évoluer sans réécrire le passé
  MORPHEUS, et les arêtes existantes ne changent pas rétroactivement de `TraceabilityResolutionState`.

### Négatives

- Une référence peut rester durablement `UNRESOLVED` quand aucun resolver n'est configuré.
- Chaque système cible exige son propre adaptateur de résolution.
- Deux axes de résolution distincts à comprendre et à projeter ; trois tables normalisées à maintenir pour une
  référence.
- L'état live et l'état publié peuvent diverger ; le lecteur doit savoir lequel il consulte.

### Risques

- L'état de résolution d'une arête est celui de son observation : un consommateur qui ne lirait que l'arête ignorerait
  qu'une référence a été résolue ou est devenue périmée après l'observation ; la disponibilité de la vue porte cet état.
- Un appelant qui persisterait l'observation live dans le snapshot actif casserait l'immutabilité : le service de
  résolution ne détient aucun store.

## Compliance and Enforcement

- Règle Archgate `DATA-0142-references-externes.rules.ts` : `live-resolution-writes-no-reference`.
- `morpheus-application/src/test/java/com/morpheus/application/reference/ExternalReferenceResolutionServiceTest.java` :
  référence sans système cible, `NO_RESOLVER`, résolution optionnelle, passage à `STALE`, résolution différée,
  indisponibilité non fatale et refus d'un resolver dupliqué, raisons d'échec distinctes.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ExternalTraceabilityContractTest.java` :
  round-trip Memory et SQLite, réouverture qui préserve coordonnées, attributs résolus, provenance, historique et lien,
  `BROKEN_REFERENCE` visible sur les deux stores, identité variable entre snapshots mais immuable dans un snapshot.
- `morpheus-application/src/test/java/com/morpheus/application/traceability/ExternalTraceabilityLinkFactoryTest.java` :
  relations autorisées, propriétaire, projection des états de résolution sans `HEURISTIC`, resolver absent ou
  indisponible sans mutation du lien canonique.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LiveExternalReferenceResolutionContractTest.java`
  : sur mémoire et sur SQLite, la résolution live retourne une copie observée et laisse intacte la référence active, y
  compris après réouverture de la base.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`) : ni `domain` ni `application` ne dépendent des adaptateurs MORPHEUS ni de
  `com.minos..` / `com.nexus..`.
- Revue : aucune dépendance du domaine vers un SDK GitHub ou un client Jira (aucune règle ne nomme ces paquets) ;
  `ExternalReferenceResolutionService` ne dépend d'aucun port de stockage.

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration entre moteurs.
- [ADR-0038](DATA-0038-snapshot-scoped-traceability-persistence.md) — persistance snapshot-scoped de la traçabilité.
- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée bornée.
- [ADR-0145](BE-0145-ressource-de-pair-mcp-designee-explicitement.md) — ressource de pair MCP désignée explicitement (référence exacte de symbole MINOS).
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée et taxonomie contrôlée.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité de domaine, distincte d'une référence externe.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md), [VALIDATION_M4](../../project/validation/VALIDATION_M4.md),
  [VALIDATION_M12](../../project/validation/VALIDATION_M12.md)
