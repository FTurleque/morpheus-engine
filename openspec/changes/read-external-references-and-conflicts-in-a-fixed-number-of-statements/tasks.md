# Tâches : lire références externes et conflits de composition en un nombre fixe de requêtes

## 1. Reproduire avant de corriger

- [ ] 1.1 Écrire `SqliteExternalReferenceReadCostTest` (à écrire, `morpheus-store-sqlite`, paquet `com.morpheus.store.sqlite.adapter`, patron de `SqliteTraceabilityBatchWriteCostTest`) : `prepareStatement` comptés pour `findByOwner` d'un propriétaire à deux références et d'un autre à deux cents, moitié résolues, avec attributs et historiques — exigence : `external-reference-and-conflict-read-cost` › Reading the external references of an owner SHALL cost a number of statements that does not depend on them ; vérification : rouge sur `HEAD`, message qui nomme les deux comptes
- [ ] 1.2 Écrire `SqliteCompositionStateReadCostTest` (à écrire, même paquet) : `find` d'un état à deux conflits et d'un état à trois cents conflits de trois candidats ; et un snapshot sans état (une seule requête) — exigence : › Reading the composition state of a snapshot SHALL cost a number of statements that does not depend on its conflicts ; vérification : rouge sur `HEAD` pour les deux tailles, vert pour le snapshot sans état
- [ ] 1.3 Dans les deux tests, comparer chaque valeur lue à ce qui a été écrit (ordres non triviaux, référence sans historique entre deux qui en ont, snapshot inconnu refusé par `snapshot not found for external reference: `) — exigence : › The references and composition state read SHALL be the values that were stored ; vérification : vert sur `HEAD` (référence)

## 2. La correction

- [ ] 2.1 `SqliteExternalReferenceStore.findByOwner` : attributs et historique du propriétaire lus en une requête chacun, regroupés par référence (design.md, D1) ; `findReference` inchangé — exigences : › Reading the external references…, › The references and composition state read… ; vérification : 1.1 et 1.3 verts
- [ ] 2.2 `SqliteCompositionStateStore.find` : candidats du snapshot lus en une requête jointe, regroupés par conflit (D2) — exigences : › Reading the composition state…, › The references and composition state read… ; vérification : 1.2 et 1.3 verts ; `SqliteCompositionResolutionMigrationTest` vert sans modification
- [ ] 2.3 Ajouter un `EXPLAIN QUERY PLAN` par lecture groupée, refusant un tri temporaire — vérification : vert
- [ ] 2.4 Casser chaque règle une fois : rétablir `history(...)` dans `mapReference`, puis `readCandidates` dans la boucle — vérification : 1.1 puis 1.2 rouges en nommant les comptes, puis revert ; trace dans la Javadoc des tests ou la PR
- [ ] 2.5 Écrire `ExternalReferenceAndConflictReadParityTest` (à écrire, `morpheus-architecture-tests`) : mêmes références et état de composition dans les stores mémoire et SQLite, valeurs comparées — exigence : › The references and composition state read SHALL be the values that were stored ; vérification : vert ; justification de PR pour un test ajouté sous `morpheus-architecture-tests`

## 3. Documentation durable

- [ ] 3.1 Créer `docs/developer/specifications/external-reference-and-conflict-read-cost.md` (gabarit de `docs/developer/specifications/README.md`, flowchart de design.md) et l'ajouter à la section « Persistance SQLite et stores » de l'index — vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate read-external-references-and-conflicts-in-a-fixed-number-of-statements --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-store-sqlite -Dtest=SqliteExternalReferenceReadCostTest,SqliteCompositionStateReadCostTest,SqliteCompositionResolutionMigrationTest`, puis `./mvnw clean verify` — vérification : vert sur Windows et Linux
- [ ] 4.4 Lancer `scripts\validate.cmd m19` et `bash ./scripts/validate-m19.sh` (sans `-Version`) — vérification : budget de composition tenu, métrique citée dans la PR

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
