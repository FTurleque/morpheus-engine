# Tâches : vérifier le schéma SQLite sans relire ses migrations

Prérequis : le sprint 5 de l'audit du 17/11/2026 (`AUD-PRF-21` à `AUD-PRF-26`) est appliqué ; il modifie les mêmes
fichiers de `morpheus-store-sqlite`.

## 1. Reproduction

- [ ] 1.1 Écrire `SqliteSchemaVerificationCostTest` (nom proposé, test à écrire) : un catalogue dont le chargeur compte
  ses lectures, une connexion dont les requêtes sont comptées, deux ouvertures successives d'une base à jour —
  exigence : `sqlite-schema-history` › La vérification de schéma d'une opération lit le registre en une requête sans
  relire les migrations ; vérification : ne compile pas, ou échoue (une lecture de ressource par migration et par
  ouverture), avant 2.1
- [ ] 1.2 Écrire les cas « registre altéré entre deux opérations » dans `SqliteMigrationLineEndingCompatibilityTest` et
  « version future apparue entre deux opérations » dans `SqliteFutureSchemaCompatibilityTest`, chacun en asserte le
  message (tests à écrire) — exigence : `sqlite-schema-history` › La vérification de schéma refuse à chaque opération
  un registre que le programme ne reconnaît pas ; vérification : verts avant 2.1 (ils fixent le comportement à garder),
  verts après
- [ ] 1.3 Compléter `SqliteMigrationLineEndingCompatibilityTest#aLedgerWrittenByACrlfCheckoutOpensAndIsLeftAsRecorded`
  d'une seconde ouverture et `SqliteRestoreLedgerVerificationTest` d'un cas précédé d'ouvertures dans le même processus
  — exigence : même exigence ; vérification : verts avant et après 2.1

## 2. Correctif

- [ ] 2.1 Introduire le catalogue immuable des migrations (texte canonique, empreintes LF et CRLF), calculé une fois
  par processus, et le constructeur package-private qui le reçoit (D1, D2) — exigence : `sqlite-schema-history` › La
  vérification de schéma d'une opération lit le registre en une requête sans relire les migrations ; vérification : 1.1
  vert
- [ ] 2.2 Remplacer `rejectFutureSchema` et la boucle de `findAppliedMigration` par une lecture unique du registre
  comparée au catalogue, en gardant les deux messages de refus — exigence : `sqlite-schema-history` › La vérification
  de schéma refuse à chaque opération un registre que le programme ne reconnaît pas ; vérification : 1.2 et 1.3 verts
- [ ] 2.3 Faire lire le catalogue par `accepts` (D3) — exigence : `sqlite-schema-history` › Offline restore verifies
  the ledger before replacing the database ; vérification : `SqliteRestoreLedgerVerificationTest`,
  `SqliteOfflineRestoreEntryTest` verts
- [ ] 2.4 Vérifier qu'aucun type du runtime n'est devenu public — vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=SqliteStorePackagesArchitectureTest`

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/sqlite-schema-history.md` : les deux exigences, le diagramme de
  séquence de design.md, la traçabilité — vérification : `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate verify-the-sqlite-schema-without-rereading-its-migrations --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D3 → tâche → test) —
  vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw clean verify` — vérification : vert ; `CliCommandConnectionCountTest`,
  `HttpRequestConnectionCountTest` et les tests de connexion MCP comptent toujours une vérification de schéma par
  opération

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire `openspec/specs/sqlite-schema-history/spec.md` et sa page durable.
