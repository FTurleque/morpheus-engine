# ADR-0023 — Persister les identités d'entités par clé externe provider-scoped

- Statut : **Proposée — validation M2 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0009, ADR-0015, ADR-0021, ADR-0022
- Portée : résolution d'identité M2, ingestion, persistance locale

## 1. Contexte

M2 normalise désormais des entités MORPHEUS (`Specification`, `Requirement`, `Scenario`, `Evidence`) à partir de sources provider.

Le premier slice M2 utilisait un `EntityIdentityResolver` injectable, mais la preuve initiale gardait les correspondances uniquement en mémoire pendant un test.

Pour que l'identité soit réellement stable entre deux exécutions, MORPHEUS doit mémoriser la relation entre une identité externe observée et son `DomainIdentity` opaque.

Le corpus M0 E03 impose notamment :

```text
move_file              -> même identité si external id stable
rename_title           -> le titre n'est pas l'identité
modify_statement       -> même identité si external id stable
delete_restore         -> même identité si external id stable
provider_collision     -> namespaces providers indépendants
change_external_key    -> continuité uniquement si explicite
conflicting_identifiers -> collision explicite, jamais de fusion silencieuse
```

## 2. Problème

Trois approches seraient dangereuses :

1. recalculer un UUID à chaque ingestion ;
2. utiliser le chemin, le titre ou le contenu comme identité métier ;
3. fusionner automatiquement deux éléments sur similarité heuristique.

Elles rendraient les références instables et feraient dépendre l'identité MORPHEUS de conventions provider ou de heuristiques non prouvées.

## 3. Décision proposée

Introduire une clé provider-scoped :

```text
EntityIdentityKey
- providerId
- entityType
- externalId
```

et persister le binding :

```text
EntityIdentityKey -> DomainIdentity(UUIDv7)
```

Le resolver de production est :

```text
PersistentEntityIdentityResolver
        ↓
EntityIdentityStore
      ┌─┴─┐
      ↓   ↓
   Memory SQLite
```

### 3.1 Résolution normale

`resolve(providerId, entityType, externalId)` :

- retourne le binding existant lorsqu'il existe ;
- sinon génère un nouveau `DomainIdentity` UUIDv7 et le persiste ;
- en cas de course d'insertion, le binding déjà persisté devient l'autorité.

### 3.2 Continuité explicite

Un changement d'external ID n'est **jamais** rapproché automatiquement.

La continuité doit être déclarée :

```text
continueIdentity(providerId, entityType, previousExternalId, newExternalId)
```

Cette opération crée un nouvel alias vers le même `DomainIdentity`.

Plusieurs external IDs peuvent donc légitimement pointer vers la même identité MORPHEUS.

### 3.3 Collision

La clé triplet est unique :

```text
(providerId, entityType, externalId)
```

Si cette clé est déjà liée à une autre identité, MORPHEUS lève explicitement `IdentityCollisionException`.

Le diagnostic machine associé est :

```text
IDENTITY_COLLISION
```

Le mapping vers un `Diagnostic` d'ingestion sera fait à la frontière applicative lorsque le pipeline M2 gérera les erreurs de normalisation de bout en bout.

## 4. Namespace provider

Le `providerId` fait partie de la clé.

Ainsi :

```text
openspec  + requirement + R-1 -> UUID A
synthetic + requirement + R-1 -> UUID B
```

est parfaitement valide.

Un external ID n'est jamais supposé global à MORPHEUS.

## 5. Ce qui ne participe jamais implicitement à l'identité

```text
title
file path / SourceLocator
statement text
content hash
similarity score
position in document
```

Ces informations peuvent constituer des indices ou des preuves pour une future politique de résolution, mais elles ne fusionnent jamais silencieusement deux entités.

Invariant maintenu :

```text
DomainIdentity != SourceLocator != externalId
```

## 6. Persistance SQLite

ADR-0021 reste la règle de migration.

Migration M2 :

```text
V003__entity_identity_bindings.sql
```

Schéma :

```text
entity_identity_bindings
- provider_id
- entity_type
- external_id
- domain_identity

PRIMARY KEY(provider_id, entity_type, external_id)
```

Un index non unique sur `domain_identity` permet plusieurs aliases vers la même identité.

Aucune table `requirements`, `specifications`, etc. n'est ajoutée par cette décision.

## 7. Concurrence

La contrainte persistante reste l'arbitre final.

Deux `resolve()` concurrents sur la même clé peuvent générer localement deux UUID candidats, mais seul le premier binding inséré gagne. Le perdant relit le binding persistant et retourne l'identité gagnante.

Une collision d'alias explicite vers une identité différente reste une erreur et n'est jamais transformée en succès.

## 8. Conséquences

### Positives

- identité stable entre exécutions ;
- external IDs découplés des UUID métier ;
- comportement identique Memory/SQLite ;
- renommages d'external ID gérables explicitement ;
- pas de verrouillage OpenSpec ;
- base prête pour les scénarios E03 plus riches.

### Négatives

- le mapping doit être sauvegardé avec la base MORPHEUS ;
- un external ID mal attribué nécessite une procédure explicite de réparation ;
- les heuristiques de continuité restent volontairement différées.

## 9. Hors périmètre

- résolution par similarité ;
- auto-détection rename/move ;
- fusion automatique de doublons ;
- temporalité M3 ;
- persistance complète des entités M2 ;
- traçabilité M4.

## 10. Critère d'acceptation

ADR-0023 passe à **Acceptée — M2** lorsque le build complet démontre :

1. même clé + même backend -> identité stable ;
2. même clé après nouvelle instance de resolver -> identité stable ;
3. même clé après réouverture SQLite -> identité stable ;
4. même external ID dans deux providers -> identités indépendantes ;
5. alias explicite -> même identité ;
6. alias vers une clé déjà possédée par une autre identité -> collision explicite ;
7. même contrat de binding sur Memory et SQLite ;
8. le reader OpenSpec retrouve les mêmes identités après réouverture SQLite ;
9. migration V3 appliquée et rejouable avec checksum immuable ;
10. `./mvnw clean test` / `.\mvnw.cmd clean test` est vert.
