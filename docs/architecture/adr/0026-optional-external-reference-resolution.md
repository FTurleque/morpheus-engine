# ADR-0026 — Références externes résolues par adapters optionnels

- Statut : **Acceptée — M2**
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

## Décision

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

## Preuve d'acceptation — 22 juillet 2026

Gate exécuté sous Windows :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats observés :

```text
ExternalReferenceResolutionServiceTest  6/6 PASS

TOTAL                                  76/76 PASS
Failures                                   0
Errors                                     0
BUILD SUCCESS
```

Les six scénarios E14 sont exercés en Java : référence sans resolver, `NO_RESOLVER`, résolution optionnelle, cible supprimée vers `STALE`, résolution différée, indisponibilité externe non fatale et rejet d'un resolver dupliqué.

Le domaine ne dépend d'aucune classe MINOS, SDK GitHub ou client Jira. La référence et son historique survivent conceptuellement à l'absence ou à la disparition du système cible.

Le warning JDK 24 `--enable-native-access=ALL-UNNAMED` du driver SQLite reste non bloquant et relève de la stratégie runtime/packaging.
