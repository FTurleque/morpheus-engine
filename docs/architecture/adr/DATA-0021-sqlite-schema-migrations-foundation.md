---
id: DATA-0021
title: "Versionner le schéma SQLite par migrations explicites et minimales"
domain: data
rules: true
files: [".gitattributes"]
---

# Versionner le schéma SQLite par migrations explicites et minimales

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

ADR-0018 retient SQLite derrière `SpecificationKnowledgeStore` et rejette un payload JSON monolithique comme schéma
de production. MORPHEUS a besoin d'un mécanisme de migration durable **avant** d'avoir besoin du schéma complet, et
deux erreurs doivent être évitées :

1. réutiliser un blob JSON générique et créer une dette de schéma ;
2. créer d'emblée toutes les tables métier à partir de concepts encore incomplets, et transformer des hypothèses en
   contraintes persistantes difficiles à migrer.

Options considérées :

- **Blob JSON générique** : rejeté. Il ne fournit pas le schéma relationnel de production recherché.
- **Créer toutes les tables métier d'emblée** : rejeté. Cela figerait des familles métier avant que leurs contrats
  Java soient stabilisés.
- **ORM et génération automatique de schéma** : non retenu. MORPHEUS a besoin d'un historique de migration explicite
  et contrôlable ; aucun besoin ne justifie un ORM.
- **Framework de migration externe (Flyway, Liquibase)** : différé. Un runner SQL déterministe interne suffit ; il
  pourra être réévalué si le nombre ou la complexité des migrations le rend disproportionné.

Le registre des migrations a la forme suivante, et l'identité se distingue du locator :

```text
schema_migrations
- version INTEGER PRIMARY KEY
- name TEXT NOT NULL
- checksum TEXT NOT NULL
- applied_at TEXT NOT NULL

DomainIdentity != SourceLocator
```

Le checksum identifie le **texte** d'une migration, pas les octets d'un checkout. Accepter la variante CRLF accepte
aussi une modification qui ne toucherait que les fins de ligne, qui ne peut pas changer le SQL exécuté. La règle
`.gitattributes` fait embarquer aux builds les mêmes octets quel que soit le système. Les mêmes scénarios de contrat
tournent sur mémoire et SQLite pour qu'aucune sémantique propre à JDBC ne devienne implicitement la sémantique
MORPHEUS.

## Decision

1. **Migrations** : SQL explicites, ordonnées, versionnées, immuables une fois appliquées, idempotentes au rejeu,
   listées par `SqliteSchemaManager` et `morpheus-store-sqlite/src/main/resources/db/migration/` ; version supportée :
   `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`.
2. **Checksum** : `schema_migrations` enregistre le SHA-256 du script normalisé en LF. Une migration enregistrée
   n'accepte que ce checksum ou celui du même texte en CRLF ; toute autre valeur est une divergence d'historique, en
   échec explicite. Une valeur acceptée reste telle quelle. `.gitattributes` fixe `*.sql text eol=lf`.
3. **Restauration** : le registre d'une sauvegarde (version, nom, checksum) suit cette règle, vérifiée **avant** la
   quarantaine de la base vivante, qui reste en place sur un refus « backup migration history is not accepted by
   this runtime for version N ».
4. **Familles** : `V001__foundation.sql` ne crée que `projects` et `knowledge_snapshots`, avec index et contraintes ;
   une famille métier a sa migration quand son contrat Java est stabilisé. Aucun blob JSON générique pour le domaine.
5. **Identités et snapshots** : identités en texte canonique UUIDv7 (ADR-0168), locators en colonnes distinctes ;
   snapshots persistés par leurs métadonnées (identifiant, projet, predecessor et révision source optionnels, état,
   création) ; une seule version `ACTIVE` observable par projet.
6. **Forward-only** : pas de down-migration ; secours par reconstruction, retour arrière par la copie prise avant que
   le schéma n'avance (ADR-0110). Une migration destructive documente sauvegarde, restauration et reconstruction.
7. **Adaptateur** : aucune API SQL dans `com.morpheus.domain` ni les services applicatifs ; foreign keys activées par
   l'adaptateur ; store reconstructible depuis les sources ; tests contractuels de référence sur le backend mémoire,
   rejoués sur SQLite.

## Do's and Don'ts

### Do

- Faire passer toute évolution de schéma par un nouveau script `Vnnn__nom.sql` enregistré dans `SqliteSchemaManager`.
- Enregistrer le SHA-256 du script normalisé en LF dans `schema_migrations`.
- Accepter pour une migration enregistrée le checksum canonique ou sa variante CRLF, et rien d'autre.
- Vérifier le registre d'une sauvegarde avant de mettre la base vivante en quarantaine lors d'une restauration.
- Stocker identités et locators dans des colonnes distinctes.
- Activer les foreign keys SQLite à l'ouverture d'une connexion.
- Exécuter les mêmes tests de contrat sur `store-memory` et `store-sqlite`.

### Don't

- Ne jamais modifier une migration déjà publiée.
- Ne jamais réécrire une valeur de checksum enregistrée.
- Ne pas persister le domaine sous forme de blob JSON générique.
- Ne pas créer une table métier avant que le contrat Java de sa famille soit stabilisé.
- Ne pas introduire d'ORM ni de génération automatique de schéma.
- Ne pas écrire de down-migration comme stratégie de retour arrière.

## Consequences

### Positives

- L'histoire du schéma est vérifiable : une migration modifiée ou un historique divergent est refusé explicitement.
- Les bases créées par un build Windows et par un build Linux du même commit s'ouvrent l'une l'autre.
- Le schéma ne grandit qu'avec des contrats stabilisés.

### Négatives

- Un runner interne est à maintenir à la place d'un outil du marché.
- Le retour à un schéma antérieur n'est possible que par une copie de la base ou par reconstruction depuis les sources.

### Risques

- Divergence mémoire / SQLite : atténuée par la suite commune de tests contractuels.
- Modification accidentelle d'une migration appliquée : atténuée par le checksum épinglé et vérifié.
- Migration destructive : sauvegarde et reconstruction documentées avant application.

## Compliance and Enforcement

- Règle Archgate `DATA-0021-sqlite-schema-migrations-foundation.rules.ts` : `sql-migrations-keep-lf`.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteSchemaMigrationTest.java` :
  `migrationsCreateVersionedNormalizedFoundationWithoutGenericJsonPayload`,
  `migrationReplayIsIdempotentAndLedgerContainsOneEntryPerSupportedMigration`, `modifiedMigrationHistoryIsRejected`,
  `projectAndActiveSnapshotSurviveStoreReopen`.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteMigrationChecksumGoldenTest.java` : épingle le
  checksum canonique de chaque migration ; une modification de contenu fait échouer le build.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteMigrationLineEndingCompatibilityTest.java` :
  ouvre un registre écrit en LF et un registre écrit en CRLF, refuse un contenu modifié.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/runtime/SqliteRestoreLedgerVerificationTest.java` : refuse une
  sauvegarde au registre refusé sans toucher à la base vivante, restaure une sauvegarde écrite par l'autre convention.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SpecificationKnowledgeStoreContractTest.java` :
  même contrat sur mémoire et SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : aucune dépendance de
  `domain` ou `application` vers `store..`.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — port `SpecificationKnowledgeStore`.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité UUIDv7.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite comme store persistant.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie de la base avant migration.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [VALIDATION_M1](../../project/validation/VALIDATION_M1.md)
