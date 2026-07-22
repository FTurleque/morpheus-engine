# ADR-0026 — Références externes résolues par adapters optionnels

- Statut : **Proposée — validation M2 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0007, ADR-0009, ADR-0022, ADR-0023
- Portée : modèle M2, références cross-engine, résolution optionnelle

## Contexte

MORPHEUS doit pouvoir conserver des liens vers MINOS, GitHub, Jira ou tout autre système sans rendre ces systèmes obligatoires.

La preuve M0 E14 a validé les états :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

## Décision proposée

Le domaine introduit :

```text
ExternalReferenceId
ExternalReferenceTarget
ExternalReference
ExternalReferenceResolutionState
ExternalReferenceResolutionReason
ExternalReferenceResolutionEvent
ResolvedExternalTarget
```

L'application introduit :

```text
ExternalReferenceResolver
ExternalReferenceResolverResult
ExternalReferenceResolverRegistry
ExternalReferenceResolutionService
```

## Frontière

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

Interdit :

```text
com.morpheus.domain -> MINOS classes
com.morpheus.domain -> GitHub SDK
com.morpheus.domain -> Jira client
```

## Invariants

```text
DomainIdentity != ExternalReference
ExternalReference peut exister sans resolver
NO_RESOLVER est un résultat explicite, pas une panne
resolver indisponible != panne MORPHEUS
cible supprimée != suppression de la référence
historique de résolution conservé
provenance conservée
```

## Transitions

```text
UNVALIDATED -- no resolver --> UNRESOLVED / NO_RESOLVER
UNVALIDATED -- found -------> RESOLVED
UNRESOLVED  -- found -------> RESOLVED
RESOLVED    -- missing -----> STALE / TARGET_REMOVED
STALE       -- found -------> RESOLVED
```

Une indisponibilité temporaire produit `TARGET_UNAVAILABLE`. Une référence déjà résolue devient `STALE`; une référence jamais résolue reste `UNRESOLVED`.

## Hors périmètre

- adapters MINOS/GitHub/Jira réels ;
- persistance des références ;
- relation de traçabilité générique M4 ;
- ingestion de références depuis OpenSpec ;
- résolution réseau obligatoire ;
- retry/background monitoring.

## Critère d'acceptation

ADR-0026 passe à **Acceptée — M2** lorsque le build complet démontre :

1. une référence existe en `UNVALIDATED` sans système cible ;
2. absence de resolver -> `UNRESOLVED / NO_RESOLVER` ;
3. resolver optionnel -> `RESOLVED` ;
4. cible supprimée après résolution -> `STALE` ;
5. résolution différée `UNRESOLVED -> RESOLVED` ;
6. indisponibilité externe non fatale ;
7. historique des transitions conservé ;
8. registry refuse deux resolvers du même système ;
9. aucune dépendance directe vers un système concret ;
10. `.\mvnw.cmd clean test` est vert.
