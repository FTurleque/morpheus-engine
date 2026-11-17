# Tâches : compter l'échec de nettoyage après commit d'une transaction SQLite (AUD-QUA-37)

## 1. Tests de reproduction

- [ ] 1.1 Écrire dans `SqliteTransactionRunnerTest` (à écrire) trois cas sur une connexion qui porte un recorder de
  test (design.md, Écarts constatés) : quarantaine, reprise réussie, nettoyage réussi du premier coup ; exiger le
  compteur `failure.suppressed.sqlite-post-commit-cleanup` à un, à un et à zéro, la mutation rapportée réussie et, pour
  la quarantaine, le refus nommé de la transaction suivante — exigence : `suppressed-failure-trace` › L'échec de
  nettoyage après commit d'une transaction SQLite est compté ; vérification : les cas échouent (ou ne compilent pas)
  avant le correctif.

## 2. Famille et compte

- [ ] 2.1 Ajouter `SQLITE_POST_COMMIT_CLEANUP` (`sqlite-post-commit-cleanup`, `WARNING`, Javadoc qui dit ce que la
  famille signale) à `SuppressedFailureFamily` — exigence : idem ; vérification : `SuppressedFailureTraceTest` et
  `SuppressedFailureCountersServedTest` verts.
- [ ] 2.2 Passer `contention` à `restoreAfterSuccessfulCommit` et compter la première `cleanupFailure` une fois, avant
  la reprise (design.md, D1), sans toucher aux lignes `System.Logger` (D2) — exigence : idem ; vérification : 1.1 vert,
  les tests existants de `SqliteTransactionRunnerTest`, `SqliteConnectionScopeCloseFailureTest` et
  `SqliteRestoreQuarantineOrderingTest` verts.

## 3. Documentation

- [ ] 3.1 Ajouter la ligne `sqlite-post-commit-cleanup` à la table des familles de `docs/developer/OPERABILITY.md` —
  exigence : idem ; vérification : ligne relue contre la Javadoc.
- [ ] 3.2 Mettre à jour `docs/developer/specifications/suppressed-failure-trace.md` (exigence, traçabilité) —
  vérification : liens relus.

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate count-a-post-commit-cleanup-failure-of-a-sqlite-transaction --strict` —
  vérification : aucun avertissement.
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D3 ou diagramme → tâche →
  test) — vérification : chaque scénario a une preuve.
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-store-sqlite,morpheus-api -am` puis
  `./mvnw test -pl morpheus-architecture-tests -Dtest=SilentCatchArchitectureTest,SqliteStorePackagesArchitectureTest,SqliteStoreAdapterHoldsNoMonitorArchitectureTest`
  — vérification : 0 échec.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée `suppressed-failure-trace` et sa page durable.
