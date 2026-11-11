# ADR-0026 — Références externes résolues par adapters optionnels

> **Archivée.** Cette décision est consolidée dans [ADR-0142](../DATA-0142-references-externes.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0142**
- Date : 2026-07-22

## Context

MORPHEUS doit pouvoir conserver des liens vers MINOS, GitHub, Jira ou tout autre système sans rendre ces systèmes
obligatoires. Une référence vers un système externe doit exister, rester lisible et garder son historique même quand
ce système est absent, indisponible, ou a supprimé sa cible.

Une référence connaît quatre états de résolution :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

## Decision

Le domaine porte la référence et son état, sans connaître aucun système cible
(`com.morpheus.domain.reference`) :

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

Frontière :

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

Transitions :

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

Le registre n'accepte qu'un resolver par système : un second resolver pour le même système est refusé.

Cette décision ne couvre pas, et renvoie à :

- la persistance des références, scopée par snapshot : ADR-0041 ;
- les liens de traçabilité vers une référence externe : ADR-0118 et ADR-0041 ;
- le resolver concret MINOS (`MinosMcpExternalReferenceResolver`) et son transport : ADR-0128, ADR-0070 ;
- l'observation live d'une référence publiée : ADR-0071.

Restent hors de cette décision : la résolution réseau obligatoire, le retry et la surveillance en tâche de fond.

## Do's and Don'ts

### Do

- Créer une `ExternalReference` en `UNVALIDATED` sans exiger la présence du système cible.
- Rendre `UNRESOLVED / NO_RESOLVER` quand aucun resolver n'est enregistré pour le système de la cible.
- Traiter l'indisponibilité d'un resolver comme un résultat (`TARGET_UNAVAILABLE`), jamais comme une panne de
  MORPHEUS.
- Conserver l'historique des transitions (`ExternalReferenceResolutionEvent`) et la provenance de la référence.
- Passer à `STALE` une référence déjà résolue dont la cible disparaît, sans supprimer la référence.

### Don't

- Ne faire dépendre `com.morpheus.domain` d'aucune classe MINOS ou NEXUS, d'aucun SDK GitHub, d'aucun client Jira.
- Ne pas confondre `DomainIdentity` et `ExternalReference`.
- Ne pas enregistrer deux resolvers pour le même système.
- Ne pas supprimer une référence parce que sa cible a disparu.

## Consequences

### Positives

- Une référence et son historique survivent à l'absence ou à la disparition du système cible.
- Un système externe se branche par un resolver sans toucher au domaine.
- Les échecs de résolution sont distincts et nommés.

### Négatives

- Une référence peut rester durablement `UNRESOLVED` quand aucun resolver n'est configuré.
- Chaque système cible exige son propre adaptateur de résolution.

### Risques

- Aucun risque propre n'est consigné par cette décision au-delà des conséquences négatives.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/reference/ExternalReferenceResolutionServiceTest.java` :
  référence sans système cible, `NO_RESOLVER`, résolution optionnelle, passage à `STALE`, résolution différée,
  indisponibilité non fatale et refus d'un resolver dupliqué, raisons d'échec distinctes.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`) : le domaine ne dépend ni des adaptateurs MORPHEUS ni de `com.minos..` /
  `com.nexus..`.
- Revue : aucune dépendance du domaine vers un SDK GitHub ou un client Jira (aucune règle ne nomme ces paquets).

## References

- [ADR-0007](../ARCH-0007-cross-engine-integration.md) — intégration entre moteurs.
- [ADR-0009](0009-stable-domain-identity.md) — identité stable, distincte d'une référence externe.
- [ADR-0023](0023-persistent-provider-scoped-entity-identity.md) — identité persistée scopée par provider.
- [ADR-0041](0041-snapshot-external-reference-traceability.md) — persistance des références par snapshot.
- [ADR-0070](0070-exact-minos-symbol-reference-and-revision.md) — référence exacte de symbole MINOS.
- [ADR-0071](0071-live-external-resolution-without-snapshot-mutation.md) — résolution live sans mutation.
- [ADR-0118](../ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0121](../ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé.
- [ADR-0128](../ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [VALIDATION_M2](../../../project/validation/VALIDATION_M2.md)
