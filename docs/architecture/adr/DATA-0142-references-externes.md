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

Les types du domaine (`com.morpheus.domain.reference`) :

```text
ExternalReferenceId
ExternalReferenceTarget
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolutionReason
ExternalReferenceResolutionEvent
ResolvedExternalTarget
```

Les types de la résolution applicative (`com.morpheus.application.reference`) et leur chaîne :

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

Les invariants que la décision tient :

```text
DomainIdentity != ExternalReference
ExternalReference peut exister sans resolver
NO_RESOLVER est un résultat explicite, pas une panne
resolver indisponible != panne MORPHEUS
cible supprimée != suppression de la référence
historique de résolution conservé
provenance conservée
MINOS absent/unavailable != MORPHEUS unavailable
NO_RESOLVER              != suppression du lien
TARGET_NOT_FOUND         != suppression du lien
TARGET_UNAVAILABLE       != suppression du lien
STALE                    != suppression du lien
référence persistée dans le snapshot  !=  observation live de sa cible
```

Les transitions de résolution :

```text
UNVALIDATED -- no resolver --> UNRESOLVED / NO_RESOLVER
UNVALIDATED -- found -------> RESOLVED
UNRESOLVED  -- found -------> RESOLVED
RESOLVED    -- missing -----> STALE / TARGET_REMOVED
STALE       -- found -------> RESOLVED
```

Le port de persistance et sa règle d'immuabilité dans un snapshot :

```text
ExternalReferenceStore
  putReference(snapshotId, reference)
  findReference(snapshotId, referenceId)
  findByOwner(snapshotId, ownerId)

same snapshot + same id + same value       -> idempotent
same snapshot + same id + different value -> collision explicite
```

La ligne principale de `snapshot_external_references` conserve le snapshot, l'identité de la référence, celle de son
propriétaire, les coordonnées (`system`, `project`, `resource_type`, `external_id`, `revision`), l'état et la raison
de résolution, la cible résolue et la provenance éventuelles ; `snapshot_external_reference_attributes` tient en
clé/valeur les attributs de `ResolvedExternalTarget`.

Les entrées de `ExternalTraceabilityLinkFactory` :

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

La fabrique produit une arête vers `TraceabilityEntityKind.EXTERNAL_REFERENCE` / `ExternalReference.id.value`.
`TraceabilityResolutionState` décrit l'état de l'arête au moment de l'observation, `ExternalReferenceResolutionState`
l'état observé de la cible ; `HEURISTIC` reste une sémantique de lien explicite distincte. Une
`ExternalTraceabilityView` signale `BROKEN_REFERENCE` quand l'arête snapshot-scoped existe mais que la référence
manque dans ce snapshot : l'arête reste visible et explicable.

Le chemin d'une résolution live :

```text
ACTIVE snapshot reference
       -> ExternalReferenceResolutionService.resolve(...)
       -> observed ExternalReference
       -> response
       -X-> mutation ACTIVE
```

Une cible indisponible (aucun résolveur, ou pair externe absent) produit une observation non résolue, avec le même
comportement sur la référence publiée.

`TARGET_UNAVAILABLE` nomme une indisponibilité temporaire. Les adaptateurs du store sont
`MemoryExternalReferenceStore` et `SqliteExternalReferenceStore` ; la migration V006 crée
`snapshot_external_references`, `snapshot_external_reference_attributes` et `snapshot_external_reference_history`.
`ExternalTraceabilityAvailability` vaut `REFERENCE_UNVALIDATED`, `REFERENCE_UNRESOLVED`, `REFERENCE_RESOLVED`,
`REFERENCE_STALE` ou `BROKEN_REFERENCE`. Le resolver concret MINOS est `MinosMcpExternalReferenceResolver` ; la
surveillance en tâche de fond est exclue au même titre que le retry.

## Decision

1. **Couches** : le domaine porte la référence sans connaître de système cible ; l'application, une résolution
   optionnelle. Un resolver (`system()`, `resolve(target)`) par système ; aucune dépendance de compilation à MINOS ;
   `DomainIdentity` n'est pas une `ExternalReference`.
2. **États** : `UNVALIDATED`, `UNRESOLVED`, `RESOLVED`, `STALE`. Sans resolver : `UNRESOLVED` / `NO_RESOLVER`, pas
   une panne. Un échec garde sa raison (`TARGET_REMOVED`, `TARGET_UNAVAILABLE`, `TARGET_NOT_FOUND`,
   `TARGET_AMBIGUOUS`, `TARGET_REVISION_MISMATCH`, `TARGET_TYPE_UNSUPPORTED`) : `STALE` si déjà résolue, sinon
   `UNRESOLVED`. Historique (`ExternalReferenceResolutionEvent`) et provenance conservés.
3. **Persistance** : `ExternalReferenceStore` par snapshot, même sémantique en mémoire et SQLite ;
   `ExternalReferenceId` immuable dans un snapshot (identique : idempotent, différent : collision). V006 normalisée,
   sans JSON générique, historique ordonné par `event_index`.
4. **Lien** : `ExternalTraceabilityLinkFactory` reçoit tout, ne génère ni ne hashe d'identifiant ; seules
   `LINKS_TO_CODE`, `LINKS_TO_TEST`, `VERIFIED_BY`, `SATISFIES` ; `ownerId` = `source.identity`. Projection :
   `UNVALIDATED`/`UNRESOLVED` → `UNRESOLVED`, `RESOLVED` → `RESOLVED`, `STALE` → `PARTIALLY_RESOLVED`, jamais
   `HEURISTIC`. Un `TraceabilityLink` observé n'est jamais muté.
5. **Vue** : `ExternalTraceabilityQueryService` garde le lien et expose `ExternalTraceabilityAvailability`, dont
   `BROKEN_REFERENCE`. Ni lien ni référence n'est supprimé faute de resolver ou de cible.
6. **Live** : `ExternalReferenceResolutionService.resolve` rend une copie, sans store ni `putReference` ; seule une
   publication persiste un état. Lire une référence publiée ne dépend pas de sa cible.
7. **Hors décision** : resolver MINOS (ADR-0128, ADR-0145), liens (ADR-0118), réseau obligatoire, retry, surveillance.

## Do's and Don'ts

### Do

- Créer une `ExternalReference` en `UNVALIDATED` sans exiger la présence du système cible.
- Rendre `UNRESOLVED / NO_RESOLVER` sans resolver, et `TARGET_UNAVAILABLE` comme un résultat, jamais une panne.
- Conserver l'historique (`ExternalReferenceResolutionEvent`) et la provenance de la référence.
- Passer à `STALE`, sans la supprimer, une référence déjà résolue dont la cible disparaît.
- Scoper chaque référence persistée par snapshot : réécriture identique idempotente, valeur différente sous la même
  identité en collision explicite.
- Fournir explicitement identifiant de lien, origine, confiance, preuves et instant d'observation, et exiger que le
  propriétaire de la référence soit la source du lien.
- Garder visible sous `BROKEN_REFERENCE` une arête sans référence.
- Rendre une copie transitionnée pour toute résolution live ; persister un nouvel état par une nouvelle publication.
- Tenir la même sémantique dans `store-memory` et `store-sqlite`.

### Don't

- Ne faire dépendre `com.morpheus.domain` d'aucune classe MINOS ou NEXUS, d'aucun SDK GitHub ni client Jira, ni le
  produit de MINOS à la compilation.
- Ne pas confondre `DomainIdentity` et `ExternalReference`, ni enregistrer deux resolvers pour un système.
- Ne pas supprimer une référence ou un lien faute de resolver, ou parce que la cible est introuvable, indisponible,
  périmée ou disparue.
- Ne pas stocker une référence en JSON générique, ni générer ou hasher implicitement un identifiant de lien.
- Ne pas créer un lien externe hors de `LINKS_TO_CODE`, `LINKS_TO_TEST`, `VERIFIED_BY` et `SATISFIES`.
- Ne pas synthétiser `HEURISTIC` depuis une indisponibilité externe.
- Ne pas muter un `TraceabilityLink` observé ni son `TraceabilityResolutionState`, ni appeler `putReference`, lors
  d'une résolution ultérieure ou live.
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
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité de domaine, distincte d'une référence externe.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md), [VALIDATION_M4](../../project/validation/VALIDATION_M4.md),
  [VALIDATION_M12](../../project/validation/VALIDATION_M12.md)
