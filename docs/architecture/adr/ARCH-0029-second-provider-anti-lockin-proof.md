---
id: ARCH-0029
title: "Prouver l'anti-lock-in avec un second provider synthétique"
domain: architecture
rules: true
files: ["**/pom.xml"]
---

# Prouver l'anti-lock-in avec un second provider synthétique

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

OpenSpec est le provider de référence (ADR-0002), mais MORPHEUS ne doit pas devenir un modèle OpenSpec déguisé.
Il faut prouver en code que deux formats distincts traversent les mêmes ports applicatifs et produisent le même
domaine MORPHEUS.

La fixture `experiments/m0/fixtures/synthetic-basic/morpheus-spec.json` est l'oracle du second format.

## Decision

Le module `morpheus-provider-synthetic` existe. Il est **verification-only** : il démontre l'architecture extensible,
il n'est pas un provider produit supporté publiquement.

Il implémente les mêmes ports que le provider OpenSpec :

```text
SpecificationProvider
SpecificationContentReader
```

```text
OpenSpec source ──> OpenSpec adapter ────┐
                                         │
                                         ├──> SpecificationContentReader
                                         │        ↓
Synthetic JSON ─> Synthetic adapter ─────┘    ProviderReadResult
                                                  ↓
                                         NormalizedProjectContent
```

### Invariants

```text
provider format != MORPHEUS domain
OpenSpec type -X-> application contract
Synthetic JSON type -X-> application contract
consumer does not branch on provider id
the same ReadCategory vocabulary applies to both providers
external identity is provider-scoped
```

Une external key identique dans deux providers ne constitue pas la même identité MORPHEUS :

```text
(openspec, requirement, X) != (synthetic-json, requirement, X)
```

### Provider synthétique

Le provider synthétique lit `morpheus-spec.json`. Ses capacités sont celles que déclare
`SyntheticSpecificationProvider` ; il ne revendique pas une catégorie qu'il ne normalise pas réellement.

Le parser JSON reste encapsulé dans `com.morpheus.provider.synthetic` et n'ajoute aucune dépendance externe.

La fixture `synthetic-basic` porte des métadonnées explicites (spécification, exigence, scénario, changement), afin
que le reader n'invente ni titre de spécification ni action de scénario.

### Ce que le provider synthétique doit permettre de vérifier

1. il est détecté via `SpecificationProvider` ;
2. son reader implémente `SpecificationContentReader` ;
3. il produit les concepts MORPHEUS (`Specification`, `Requirement`, `Scenario`, `ChangeProposal`, `Evidence`,
   `Provenance`) ;
4. OpenSpec et le format synthétique sont consommés par la même fonction sans branche provider-specific ;
5. les deux résultats exposent le même ensemble de `ReadCategory` demandé ;
6. une même external key produit des identités distinctes entre OpenSpec et le format synthétique ;
7. `com.morpheus.domain..` et `com.morpheus.application..` restent indépendants de `com.morpheus.provider..` ;
8. aucun type de SDK ou de provider ne fuit dans `NormalizedProjectContent` ou `ProviderReadResult` ;
9. l'accueil du second provider n'exige aucune modification du domaine.

### Hors périmètre

- publication du provider synthétique comme fonctionnalité utilisateur ;
- compatibilité ascendante de son schéma JSON ;
- écriture du format synthétique ;
- enrichissement fonctionnel au niveau d'OpenSpec.

## Do's and Don'ts

### Do

- Faire passer tout provider par `SpecificationProvider` et `SpecificationContentReader`.
- Écrire un consommateur de port qui fonctionne avec chaque provider, sans tester l'identifiant du provider.
- Scoper chaque identité externe par provider.
- Déclarer dans un provider les seules capacités qu'il normalise réellement.
- Garder `morpheus-provider-synthetic` en dépendance de test des modules de transport.

### Don't

- Ne pas faire dépendre `domain` ni `application` d'un paquet `com.morpheus.provider..`.
- Ne pas laisser un type propre à un format traverser un contrat de l'application.
- Ne pas brancher un consommateur sur l'identifiant d'un provider.
- Ne pas exposer le provider synthétique comme fonctionnalité utilisateur.
- Ne pas ajouter de dépendance externe au parser JSON synthétique.

## Consequences

### Positives

- La neutralité vis-à-vis des providers est prouvée en code, pas seulement affirmée.
- Le domaine n'a pas eu à changer pour accueillir un second format.
- Le provider synthétique sert de fixture déterministe aux tests des autres modules.

### Négatives

- Un module de vérification est maintenu avec le reste du réacteur.

### Risques

- Le schéma JSON synthétique n'a aucune garantie de compatibilité : un usage hors des tests serait sans contrat.

## Compliance and Enforcement

- Règle Archgate `ARCH-0029-second-provider-anti-lockin-proof.rules.ts` : `synthetic-provider-is-test-scoped`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : même port de
  lecture pour OpenSpec, synthétique et Markdown structuré ; consommateur neutre ; external keys identiques scopées
  par provider.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java` : les
  providers intégrés, synthétique compris, ne dépendent pas du SDK de plugins.
- `morpheus-provider-synthetic/src/test/java/com/morpheus/provider/synthetic/SyntheticSpecificationProviderTest.java`
  et `SyntheticSpecificationContentReaderTest.java`.
- Revue : la portée `test` de `morpheus-provider-synthetic` dans les POMs des modules de transport n'est tenue par
  aucun test.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md)
- [ADR-0002](ARCH-0002-openspec-reference-provider.md)
- [ADR-0009](DATA-0009-stable-domain-identity.md)
- [ADR-0011](BE-0011-provider-capability-negotiation.md)
- [ADR-0023](DATA-0023-persistent-provider-scoped-entity-identity.md)
- [ADR-0028](BE-0028-unified-provider-read-contract.md)
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md)
