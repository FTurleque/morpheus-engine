---
id: DATA-0125
title: "Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute"
domain: data
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/sync/SyncFreshness*.java", "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSyncStateStore.java"]
---

# Détecter les changements de source par inventaire SHA-256, reconstruire en entier en cas de doute

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0053, ADR-0054, ADR-0055

## Context

Une synchronisation doit savoir ce qui a changé dans les sources locales d'un projet depuis la dernière baseline
publiée, sans jamais présenter comme incrémental un état qu'elle ne sait pas démontrer. Trois questions se posent :
comment observer les sources, où garder ce qui a été observé, et quel rôle donner à la surveillance du système de
fichiers.

Options écartées pour l'observation : une empreinte fondée sur la date de modification ou la taille seule (elle
manque une réécriture de même taille et voit un changement là où il n'y en a pas), un rapprochement flou des
renommages et toute heuristique textuelle (elles devinent).

L'état de synchronisation porte le drapeau de reconstruction en attente, qui force la synchronisation suivante hors
du chemin incrémental : c'est un état d'autorité. Écrit en dernier écrivain gagnant, un plan dépassé effacerait le
drapeau posé par un plus récent. Options écartées pour le protéger : un verrou par projet dans l'application (il ne
couvre pas deux processus, CLI et API, sur la même base), un horodatage comme jeton (deux tentatives peuvent partager
un instant), un upsert conditionnel dont le résultat n'est pas inspecté (il ne garde rien).

`java.nio.file.WatchService` peut perdre des événements (`OVERFLOW`) et n'observe que le processus qui l'ouvre : il ne
peut pas être la preuve de l'état des sources.

## Decision

1. **Inventaire.** L'état observable des sources d'un projet est un `SourceInventory` : projet, instant de capture,
   `sourceRevision` facultative et entrées triées par chemin relatif normalisé (`SourcePath`). Chaque entrée porte son
   empreinte `SourceFingerprint`, SHA-256 des octets du fichier, et sa taille.
2. **Révision de source opaque.** MORPHEUS compare l'égalité de deux `sourceRevision` et n'invente aucun ordre entre
   elles.
3. **Diff conservateur.** `SourceInventoryDiffer` classe chaque source en `ADDED`, `MODIFIED`, `DELETED`, `MOVED` ou
   `UNCHANGED` (`SourceInventoryDiff.ChangeKind`), dans un ordre stable par chemin. Un déplacement n'est reconnu que
   lorsqu'un contenu supprimé (empreinte et taille) correspond à exactement un contenu ajouté ; toute correspondance
   multiple est ambiguë, n'est jamais devinée et impose la reconstruction complète.
4. **Reconstruction complète en cas de doute.** Toute condition qui empêche de démontrer la sûreté de l'incrémental
   planifie `SyncPlan.SyncMode.FULL_REBUILD` avec une raison `SyncPlan.FullRebuildReason` : aucune baseline,
   inventaire incomplet ou en échec, `OVERFLOW` du watcher, déplacement ambigu, même `sourceRevision` avec un
   inventaire différent, `sourceRevision` présente puis perdue, baseline incohérente, reconstruction précédente en
   attente, échec d'exécution, demande explicite. La liste exacte est l'énumération `FullRebuildReason`. Une
   reconstruction complète n'est jamais silencieuse : elle porte sa raison.
5. **État de synchronisation persisté.** Le port `SyncStateStore` conserve, par projet, un `ProjectSyncState`
   (dernière tentative, dernier succès et son mode, dernier changement observé, `sourceRevision`, raison de
   reconstruction en attente, nombre de sources, révision), l'inventaire courant et des `SourceArchiveRecord`
   immuables et ordonnés pour les sources supprimées ou déplacées. Le stockage mémoire et le stockage SQLite ont le
   même comportement. SQLite les garde dans des tables spécialisées (`sync_state`, `sync_inventory_entries`,
   `sync_source_archives`), sans JSON métier générique. Les invalidations sont explicites dans le plan de
   synchronisation ; elles ne suppriment ni historique publié ni snapshot.
6. **État d'autorité sous révision attendue.** `ProjectSyncState.revision` vaut 0 avant la première écriture et
   augmente de un à chaque écriture. `recordAttempt` et `commitSuccessfulSync` prennent la révision lue et rendent la
   nouvelle ; une divergence lève `SyncStateConflictException`, type nommé distinct d'une panne de stockage. En SQLite,
   la création est un `INSERT … ON CONFLICT DO NOTHING` et la mise à jour un `UPDATE … WHERE project_id = ? AND
   revision = ?` dont le nombre de lignes est vérifié ; la révision s'écrit dans la même transaction que l'inventaire
   et les archives. Le stockage mémoire applique la même garde sous son moniteur.
7. **Un plan écrit contre sa propre révision.** `SyncPlan.stateRevision` est la révision après l'enregistrement de la
   tentative ; `IncrementalSyncService.complete` et `fail` écrivent contre elle. Sur un conflit, `complete` ne réessaie
   pas et ne pose pas `BASELINE_INCONSISTENT` : un plan dépassé ne réécrit pas l'état d'un plus récent. Une panne de
   stockage reste réessayée une fois, sans changer la révision.
8. **Fraîcheur calculée, jamais lue d'une horloge cachée.** `SyncFreshnessService.assess` reçoit `now` et un âge
   maximal explicites et rend un `SyncFreshness.State` :

   | État | Condition |
   |---|---|
   | `UNKNOWN` | aucune synchronisation réussie et aucune reconstruction en attente |
   | `FRESH` | âge depuis le dernier succès inférieur ou égal à l'âge maximal |
   | `STALE` | âge depuis le dernier succès supérieur à l'âge maximal |
   | `REBUILD_REQUIRED` | une raison de reconstruction en attente est présente |

   `REBUILD_REQUIRED` domine `FRESH` et `STALE` : une baseline récente n'est pas présentée comme saine quand une
   tentative plus récente a établi qu'une reconstruction complète est requise.
9. **Le watcher n'est qu'un déclencheur.** `LocalSourceWatcher` est une optimisation locale et facultative. Il produit
   des `SourceWatchSignal` normalisés (`CREATE`, `MODIFY`, `DELETE`, `OVERFLOW`) qui ne servent qu'à déclencher ou
   borner un nouveau scan ; le scan SHA-256 reste la seule preuve de l'état des sources. Un appel manuel de scan et de
   synchronisation prend exactement les mêmes décisions que le watcher suivi d'un scan.

## Do's and Don'ts

### Do

- Calculer l'empreinte d'une source par SHA-256 de ses octets.
- Trier les entrées d'inventaire, les changements du diff, les invalidations et les archives par chemin canonique.
- Planifier `FULL_REBUILD` avec une `FullRebuildReason` dès que la sûreté de l'incrémental n'est pas démontrée.
- Écrire l'état de synchronisation en passant la révision lue, et laisser `SyncStateConflictException` remonter.
- Passer `now` et l'âge maximal en paramètres de tout calcul de fraîcheur.
- Garder les sources supprimées ou déplacées comme `SourceArchiveRecord` immuables.

### Don't

- Ne pas fonder une empreinte sur la date de modification ou la taille seule.
- Ne pas rapprocher un renommage par similarité, ni deviner entre plusieurs candidats de même contenu.
- Ne pas comparer deux `sourceRevision` autrement que par égalité.
- Ne pas écrire l'état de synchronisation par upsert aveugle (`ON CONFLICT DO UPDATE`).
- Ne pas réessayer ni marquer `BASELINE_INCONSISTENT` un plan dont l'écriture est refusée pour conflit de révision.
- Ne pas lire `Instant.now()` dans le calcul de fraîcheur.
- Ne pas traiter un signal du watcher comme la preuve d'un changement, ni sauter le scan qui le suit.
- Ne pas stocker l'état de synchronisation dans une colonne JSON générique.

## Consequences

### Positives

- Un reçu de synchronisation ne peut pas annoncer un incrémental que le diff ne démontre pas.
- Deux synchronisations concurrentes du même projet, même depuis deux processus, ne s'effacent pas : l'une échoue
  explicitement (HTTP `409`, `STATE_CONFLICT`).
- La fraîcheur est reproductible : le même état et le même `now` donnent le même verdict.

### Négatives

- Le calcul du SHA-256 lit chaque source en entier à chaque scan.
- Un projet qui déplace plusieurs fichiers de contenu identique paie une reconstruction complète.

### Risques

- `fail` appelé sur un plan dépassé lève le conflit à la place de la panne d'origine ; un appelant qui avale le
  conflit perd la cause première.
- Le watcher n'est branché sur aucune surface livrée ; s'il l'est, il doit garder le scan comme preuve.

## Compliance and Enforcement

- Règle Archgate `DATA-0125-detection-de-changement-des-sources.rules.ts` : `sync-freshness-reads-no-clock`, `sync-state-has-no-blind-upsert`.
- `morpheus-application/src/test/java/com/morpheus/application/sync/SourceSynchronizationCoreTest.java` : empreinte
  SHA-256 des seules racines choisies, diff stable, déplacement unique reconnu et ambigu jamais deviné, raisons de
  reconstruction (watcher `OVERFLOW`, scan incomplet, révision incohérente ou perdue), fraîcheur à `now` explicite,
  politique des signaux du watcher.
- `morpheus-application/src/test/java/com/morpheus/application/sync/SyncReliabilityFallbackTest.java` : révision de
  source opaque, baseline incohérente, retry unique, `BASELINE_INCONSISTENT` après publication.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SyncStateRevisionContractTest.java` : garde de
  révision sur les deux adaptateurs (drapeau non effacé, commit refusé sans effet, première écriture unique,
  révision +1, plan dépassé au `complete` et au `fail`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/IncrementalSyncPersistenceContractTest.java` :
  parité mémoire/SQLite des plans, de l'état, de l'inventaire, des archives et de la fraîcheur, réouverture SQLite,
  reconstruction en attente.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSyncStateRevisionMigrationTest.java` : une base
  existante reçoit la révision et reste inscriptible.
- Revue : absence de colonne JSON générique dans les migrations de synchronisation, et rôle de simple déclencheur du
  watcher s'il est branché sur une surface.

## References

- [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) et
  [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — SQLite et migrations.
- [ADR-0126](DATA-0126-publication-complete-d-un-snapshot.md) — ce qu'une synchronisation exécute et ce que son reçu
  en dit.
- [ADR-0067](BE-0067-explicit-conservative-http-sync.md) — synchronisation HTTP.
- [VALIDATION_M7](../../project/validation/VALIDATION_M7.md).
