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

## Decision

Le schéma SQLite évolue par migrations SQL explicites, ordonnées et immuables après application.

### Ledger de migrations

SQLite conserve :

```text
schema_migrations
- version INTEGER PRIMARY KEY
- name TEXT NOT NULL
- checksum TEXT NOT NULL
- applied_at TEXT NOT NULL
```

Le checksum identifie le **texte** d'une migration, pas les octets d'un checkout : c'est le SHA-256 du script dont les
fins de ligne sont normalisées en LF. C'est la valeur enregistrée pour toute migration appliquée.

Pour une migration déjà enregistrée, deux valeurs sont acceptées, pas plus : le checksum canonique et celui du même
texte en CRLF. Toute autre valeur est une divergence d'historique et provoque un échec explicite. Une valeur acceptée
reste telle qu'enregistrée : MORPHEUS ne réécrit jamais l'histoire du schéma. Accepter la variante CRLF accepte aussi
une modification qui ne toucherait que les fins de ligne, qui ne peut pas changer le SQL exécuté.

`.gitattributes` fixe `*.sql text eol=lf`, pour que les builds embarquent les mêmes octets quel que soit le système.

La restauration d'une sauvegarde confronte chaque ligne de son registre (version, nom, checksum) à cette même règle
**avant** de mettre la base vivante en quarantaine ; un registre refusé l'est avec la raison « backup migration history
is not accepted by this runtime for version N », et la base vivante reste en place.

### Migrations et familles métier

La migration initiale `V001__foundation.sql` crée uniquement `projects` et `knowledge_snapshots`, avec les index et
contraintes de leurs invariants, sans payload JSON métier générique. Chaque famille métier reçoit sa propre migration
lorsque son contrat Java est stabilisé. La liste des migrations fait foi dans `SqliteSchemaManager` et dans
`morpheus-store-sqlite/src/main/resources/db/migration/` ; la version de schéma supportée est la constante
`SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`.

### Identités

Les identités MORPHEUS sont stockées sous leur représentation texte canonique UUIDv7 (ADR-0140).

```text
DomainIdentity != SourceLocator
```

Un locator de source est persisté dans des colonnes distinctes de l'identité.

### Snapshots

Les snapshots sont persistés par leurs **métadonnées** : identifiant, projet, predecessor optionnel, état, révision
source optionnelle, date de création. Le contenu métier d'un snapshot relève des migrations de ses familles.

### Stratégie de migration

Le chemin normal est **forward-only**. Pour les données dérivées et reconstructibles, la reconstruction depuis les
sources reste la stratégie de secours privilégiée plutôt qu'un système de down-migrations. Le retour à un programme
antérieur repose sur la copie de la base prise avant que son schéma n'avance (ADR-0110). Toute migration destructive
documente explicitement sauvegarde, restauration et stratégie de reconstruction.

### Invariants

1. SQLite reste un adaptateur ;
2. aucune API SQL ne fuit dans `com.morpheus.domain` ou les services applicatifs ;
3. toute évolution de schéma passe par une migration versionnée ;
4. une migration appliquée est immuable par checksum ;
5. rejouer les migrations est idempotent ;
6. aucun blob JSON générique ne représente le domaine ;
7. les identités UUIDv7 et les locators restent séparés ;
8. une seule version `ACTIVE` est observable par projet ;
9. les foreign keys SQLite sont activées par l'adaptateur ;
10. le store reste reconstructible depuis les sources.

### Backend mémoire

Le backend mémoire reste la référence des tests contractuels. Les mêmes scénarios de contrat sont exécutés sur mémoire
et SQLite, pour qu'aucune sémantique propre à JDBC ne devienne implicitement la sémantique MORPHEUS.

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
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité UUIDv7.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite comme store persistant.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie de la base avant migration.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [VALIDATION_M1](../../project/validation/VALIDATION_M1.md)
