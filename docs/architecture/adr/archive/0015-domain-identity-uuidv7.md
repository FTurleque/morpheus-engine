# ADR-0015 — Utiliser UUIDv7 comme format canonique de `DomainIdentity`

> **Archivée.** Cette décision est consolidée dans [ADR-0140](../DATA-0140-identite-de-domaine.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0140**
- Date : 2026-07-22

## Context

ADR-0009 distingue l'identité logique MORPHEUS de la version de l'entité, de son emplacement source, de son
identifiant externe, de son contenu et de son titre. Il reste à fixer le format concret de l'identité opaque.

Ce format doit rester indépendant du provider, du backend, du langage, de MINOS / NEXUS / JARVIS et du contenu de
l'entité. MORPHEUS a besoin d'identifiants :

- générables localement et hors ligne, uniques sans coordinateur central ;
- sérialisables de manière stable et transportables par la CLI, MCP, l'API, les stores et les intégrations sans
  bibliothèque MORPHEUS spécifique ;
- sur 128 bits, compatibles avec les types UUID usuels ;
- dotés d'une certaine localité temporelle, favorable aux index ordonnés et au diagnostic ;
- opaques : ni provider, ni chemin, ni type métier encodés ;
- standardisés plutôt que propriétaires.

Options considérées :

- **UUIDv4** — non retenu comme premier choix : simple et universellement supporté, mais sans localité temporelle,
  alors que MORPHEUS crée des séries d'entités lors des ingestions. Il reste un repli techniquement valide si une
  contrainte de runtime rendait UUIDv7 disproportionné.
- **ULID** — crédible mais non retenu : mêmes propriétés (128 bits, horodatage milliseconde, tri lexical), mais
  UUIDv7 les apporte à l'intérieur du standard UUID et de son écosystème de types.
- **Identifiant numérique local** — rejeté comme identité générale : allocation liée au store, créations hors ligne
  ou multi-store compliquées.
- **Hash du contenu ou du locator** — rejeté : il confond identité, version et emplacement, en contradiction directe
  avec ADR-0009.
- **Format MORPHEUS propriétaire** — rejeté : aucun besoin métier ne justifie un nouveau standard d'identifiant.

## Decision

**UUIDv7** (RFC 9562) est le format canonique de `DomainIdentity`.

Représentation publique : la forme UUID canonique, en minuscules :

```text
xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
```

Structure UUIDv7 retenue :

```text
48 bits  Unix timestamp en millisecondes
4 bits   version = 7
12 bits  rand_a
2 bits   variant UUID
62 bits  rand_b
```

`DomainIdentity` (`morpheus-domain`, `com.morpheus.domain.identity`) refuse dans son constructeur compact toute valeur
dont la version n'est pas 7 ou la variante n'est pas celle de RFC 9562. `generate()` produit l'identifiant localement :
horodatage courant, `rand_a` et `rand_b` tirés d'un `SecureRandom`. Aucun ordre n'est garanti entre deux identifiants
créés dans la même milliseconde. `parse()` relit la forme canonique. Aucune bibliothèque tierce n'est utilisée.

Le domaine traite la valeur comme **opaque**. Invariants :

1. `DomainIdentity` est créé une fois puis conservé tant que la continuité logique est démontrée (ADR-0009).
2. UUIDv7 ne remplace pas `createdAt`.
3. L'ordre des UUID ne remplace ni `EntityVersion` ni `SpecificationVersion`.
4. Le timestamp encodé ne détermine aucun état métier.
5. Aucun provider, chemin, titre ou contenu n'est encodé dans l'identifiant.
6. Un backend peut utiliser une représentation native ou binaire différente tant que le round-trip produit le même
   UUID canonique.
7. Un identifiant n'est jamais un secret ni un mécanisme d'autorisation.

Cette décision ne fixe pas le format des identifiants externes fournis par les providers.

## Do's and Don'ts

### Do

- Créer une identité par `DomainIdentity.generate()` et relire sa forme texte par `DomainIdentity.parse()`.
- Sérialiser une identité sous sa forme UUID canonique en minuscules.
- Restituer exactement le même UUID après un aller-retour par un store.
- Trier et dater les entités par des champs explicites (`createdAt`, `EntityVersion`, `SpecificationVersion`).

### Don't

- N'accepter dans `DomainIdentity` aucun UUID dont la version n'est pas 7 ou la variante n'est pas RFC 9562.
- N'encoder ni provider, ni chemin, ni titre, ni contenu dans une identité.
- Ne pas lire le timestamp d'une identité comme un fait métier ni comme `createdAt`.
- Ne pas dériver une identité d'un hash de contenu ou de locator.
- Ne pas utiliser une identité comme secret ou comme preuve d'autorisation.

## Consequences

### Positives

- Standard UUID, 128 bits, génération locale sans coordination centrale.
- Sérialisation standard et compatibilité avec les types UUID des bases et des pilotes.
- Localité temporelle, aucun couplage provider, aucun format spécifique MORPHEUS.

### Négatives

- Le temps approximatif de génération est observable dans l'identifiant.
- L'ordre à l'intérieur d'une milliseconde n'est pas garanti.
- Représentation texte plus longue qu'un ULID.
- Les consommateurs doivent être empêchés d'utiliser le timestamp comme fait métier implicite.

### Risques

- Utilisation du timestamp comme `createdAt` : mitigée par un `createdAt` explicite et l'opacité de l'identité.
- Ordre trompeur : tout tri métier utilise des champs explicites, jamais l'ordre UUID seul.
- Fuite temporelle : acceptée pour des identifiants techniques non secrets ; à réévaluer seulement si une surface
  publique l'interdit.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/identity/DomainIdentityTest.java` : génération conforme UUIDv7
  RFC 9562, round-trip de la chaîne canonique, refus d'une valeur non UUIDv7, identifiants opaques et distincts.
- Le constructeur compact de `DomainIdentity` refuse toute valeur hors version 7 / variante RFC 9562.
- Revue : opacité de l'identité et absence d'usage métier de son timestamp.

## References

- [ADR-0009](0009-stable-domain-identity.md) — identité stable, distincte de la version et du locator.
- [ADR-0016](0016-java-21-production-baseline.md) — stack de production.
- [ADR-0023](0023-persistent-provider-scoped-entity-identity.md) — identité persistée scopée par provider.
- [VALIDATION_M0](../../../project/validation/VALIDATION_M0.md)
- [Étude du format d'identité](../../../project/research/domain-identity-format.md)
