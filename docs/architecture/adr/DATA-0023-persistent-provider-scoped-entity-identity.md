---
id: DATA-0023
title: "Persister les identités d'entités par clé externe provider-scoped"
domain: data
rules: false
---

# Persister les identités d'entités par clé externe provider-scoped

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS normalise des entités (`Specification`, `Requirement`, `Scenario`, `Evidence`) à partir de sources provider.
Pour que leur identité soit stable entre deux exécutions, il doit mémoriser la relation entre une identité externe
observée et son `DomainIdentity` opaque.

Le corpus d'identité de référence (`experiments/m0/fixtures/identity-scenarios.json`) impose notamment :

```text
move_file               -> même identité si external id stable
rename_title            -> le titre n'est pas l'identité
modify_statement        -> même identité si external id stable
delete_restore          -> même identité si external id stable
provider_collision      -> namespaces providers indépendants
change_external_key     -> continuité uniquement si explicite
conflicting_identifiers -> collision explicite, jamais de fusion silencieuse
```

Trois approches sont dangereuses, parce qu'elles rendraient les références instables et feraient dépendre l'identité
de conventions provider ou d'heuristiques non prouvées : recalculer un UUID à chaque ingestion ; utiliser le chemin,
le titre ou le contenu comme identité métier ; fusionner automatiquement deux éléments sur similarité heuristique.

Un `providerId` fait partie de la clé et se range aussi en texte joint par `"\n"` (ensemble des fournisseurs d'une
adhésion de portefeuille dans SQLite) : un caractère de contrôle interne rendrait la relecture ambiguë et ferait
diverger les stores mémoire et SQLite. Deux autres options ont été écartées pour ce point : refuser aux seuls points
d'entrée du portefeuille (un appel direct au store resterait ambigu, et la parité ne tiendrait que tant que chaque
appelant passe par ces points) ; encoder l'ensemble en tableau JSON (supprime l'ambiguïté sans rien refuser, mais
coûte une migration de colonne pour accepter des identifiants qu'aucun fournisseur livré ne produit).

## Decision

### Clé et binding

La clé est provider-scoped :

```text
EntityIdentityKey = (providerId, entityType, externalId)
```

et le binding persisté est `EntityIdentityKey -> DomainIdentity (UUIDv7)`. Le resolver de production est
`PersistentEntityIdentityResolver`, appuyé sur le port `EntityIdentityStore`, implémenté par le store mémoire et par
SQLite.

### Résolution normale

`resolve(providerId, entityType, externalId)` retourne le binding existant ; sinon il génère un nouveau
`DomainIdentity` UUIDv7 et le persiste. La contrainte persistante est l'arbitre final : deux résolutions concurrentes
sur la même clé peuvent générer deux UUID candidats, seul le premier binding inséré gagne, et le perdant relit le
binding persistant et retourne l'identité gagnante.

### Continuité explicite

Un changement d'external ID n'est **jamais** rapproché automatiquement. La continuité se déclare par
`continueIdentity(providerId, entityType, previousExternalId, newExternalId)`, qui crée un nouvel alias vers le même
`DomainIdentity`, uniquement si l'ancien external ID possède déjà un binding. Plusieurs external IDs peuvent donc
pointer vers la même identité.

### Collision

Le triplet `(providerId, entityType, externalId)` est unique. S'il est déjà lié à une autre identité, MORPHEUS lève
`IdentityCollisionException` ; le code machine associé est `IDENTITY_COLLISION` (`DiagnosticCode`). Une collision
d'alias vers une identité différente reste une erreur et n'est jamais transformée en succès.

### Namespace provider

```text
openspec  + requirement + R-1 -> UUID A
synthetic + requirement + R-1 -> UUID B
```

est valide : un external ID n'est jamais supposé global à MORPHEUS.

### Ce qui ne participe jamais implicitement à l'identité

```text
title    file path / SourceLocator    statement text    content hash    similarity score    position in document
```

Ces informations peuvent servir d'indices ou de preuves, mais ne fusionnent jamais silencieusement deux entités.
Invariant : `DomainIdentity != SourceLocator != externalId`.

### Identifiant de provider

`ProviderId` refuse tout caractère de contrôle ISO (`Character.isISOControl` : U+0000–U+001F et U+007F–U+009F) qui
reste après le retrait des blancs d'extrémité, par une `IllegalArgumentException` dont le message nomme l'identifiant,
caractères de contrôle écrits `[U+XXXX]` :

```text
provider id must not contain control characters: "openspec[U+000A]markdown"
```

La notation ne porte pas de barre oblique inverse : les frontières HTTP lisent une barre oblique inverse comme un
chemin Windows (`ServerLocationDisclosure`) et remplaceraient tout le message par le nom de l'exception. La règle vit
dans le constructeur compact : elle vaut pour chaque chemin qui fabrique un identifiant — HTTP, MCP, CLI,
`provider.id` des métadonnées de plugin et du codec de probe, relecture des stores. Une ligne stockée qui porterait un
identifiant à caractère de contrôle, ou un segment vide, est refusée à la relecture par un message nommé, jamais
recomposée.

### Persistance SQLite

La migration suit ADR-0021. La table `entity_identity_bindings` (`V003__entity_identity_bindings.sql`) porte
`provider_id`, `entity_type`, `external_id`, `domain_identity`, avec
`PRIMARY KEY(provider_id, entity_type, external_id)` et un index non unique sur `domain_identity` qui admet plusieurs
alias vers la même identité. Cette décision n'ajoute aucune table d'entités métier.

### Hors périmètre

Résolution par similarité, auto-détection de renommage ou de déplacement, fusion automatique de doublons.

## Do's and Don'ts

### Do

- Inclure le `providerId` dans toute clé d'identité externe.
- Déclarer explicitement par `continueIdentity` toute continuité entre deux external IDs.
- Laisser la contrainte persistante arbitrer une course d'insertion, et relire le binding gagnant.
- Construire tout identifiant de provider par `ProviderId`, y compris à la relecture d'un store.

### Don't

- Ne pas recalculer un UUID à chaque ingestion.
- Ne pas dériver l'identité du titre, du chemin, du texte, d'une empreinte, d'un score de similarité ou d'une position.
- Ne pas fusionner deux entités sur une heuristique.
- Ne pas transformer une collision d'identité en succès.
- Ne pas accepter un caractère de contrôle dans un identifiant de provider.

## Consequences

### Positives

- Identité stable entre exécutions, external IDs découplés des UUID métier.
- Comportement identique mémoire/SQLite.
- Renommages d'external ID gérables explicitement, sans verrouillage OpenSpec.
- Un ensemble de fournisseurs relu depuis SQLite est exactement celui enregistré.

### Négatives

- Le mapping doit être sauvegardé avec la base MORPHEUS.
- Un external ID mal attribué exige une procédure explicite de réparation.
- Les heuristiques de continuité restent volontairement absentes.

### Risques

- Une base contenant un identifiant de provider à caractère de contrôle — seul un plugin tiers aurait pu l'y écrire —
  cesserait d'être lisible sur ces lignes, par un refus nommé et non par un repli silencieux. Aucun fournisseur livré
  n'en produit.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/identity/PersistentEntityIdentityResolverTest.java` :
  stabilité, alias explicite, collision.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/EntityIdentityStoreContractTest.java` : même
  contrat de binding sur mémoire et SQLite.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteEntityIdentityStoreTest.java` : binding qui
  survit à la réouverture de la base.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PersistentIdentityOpenSpecIntegrationTest.java`
  : le reader OpenSpec retrouve les mêmes identités après réouverture.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java`
  (`identicalExternalKeysRemainProviderScoped`) : clés externes identiques, identités distinctes par provider.
- `morpheus-domain/src/test/java/com/morpheus/domain/provider/ProviderIdTest.java` : refus des caractères de contrôle
  et forme du message.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m23/PortfolioPersistenceParityTest.java` :
  résultats identiques des deux stores, refus compris, et ensemble de fournisseurs relu égal à celui enregistré.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine indépendant des providers.
- [ADR-0009](DATA-0009-stable-domain-identity.md) — identité, version, localisateur et référence externe distincts.
- [ADR-0015](DATA-0015-domain-identity-uuidv7.md) — UUIDv7 canonique opaque.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations SQLite.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé provider-neutral.
- [VALIDATION_M2](../../project/validation/VALIDATION_M2.md).
