# Proposition : compter l'échec de nettoyage après commit d'une transaction SQLite (AUD-QUA-37)

## Why

Après un commit réussi, `SqliteTransactionRunner.restoreAfterSuccessfulCommit`
(`morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/runtime/SqliteTransactionRunner.java`, lignes 99-134)
remet la connexion en auto-commit. Si cette remise échoue, il la retente, puis remplace la connexion physique de la
portée, puis, en dernier recours, met la connexion directe en quarantaine : toute transaction suivante sur elle est
refusée. Ces trois issues ne sont écrites que par `System.Logger` (lignes 14, 110, 119 et 131), donc en texte sur la
sortie d'erreur. Aucune n'est comptée sur l'`OperationalRecorder` de la connexion, qui compte pourtant les autres échecs
écartés du store (`SQLITE_LEASE_RELEASE`, `SQLITE_CONFLICT_READ`) et que `SqliteContentionMetrics.of(connection)`
retrouve déjà (ligne 41 du même fichier).

Une connexion mise en quarantaine n'apparaît donc ni sur la route `metrics` ni dans le journal opérationnel structuré,
contrairement au modèle de `SilentCatchArchitectureTest` (« Record the failure (OperationalRecorder.suppressed) »).

Preuve : constat `AUD-QUA-37` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` ; revérifié sur `1a539c22` le 17/11/2026 (lignes inchangées, aucune famille correspondante
dans `SuppressedFailureFamily`). Le sprint 2 de l'audit pose ce signal avant les changements SQLite des sprints 5 et 6.

## What Changes

- Une famille d'échec écarté `sqlite-post-commit-cleanup` est ajoutée.
- Un échec de la remise en auto-commit après commit est compté une fois par transaction, sur le recorder de la
  connexion, quelle que soit l'issue (reprise réussie, connexion de portée remplacée, quarantaine).
- La mutation reste rapportée comme réussie ; la quarantaine et son refus nommé ne changent pas ; les lignes
  `System.Logger` restent.
- Le compteur est servi par la route `metrics` existante, sans route nouvelle.

## Capabilities

### New Capabilities

### Modified Capabilities
- `suppressed-failure-trace` : l'échec de nettoyage après commit d'une transaction SQLite est compté sous une famille
  nommée.

## Périmètre et exclusions

**Inclus :** `SqliteTransactionRunner` (`morpheus-store-sqlite`), `SuppressedFailureFamily` (`morpheus-application`),
`SqliteTransactionRunnerTest`, `docs/developer/OPERABILITY.md` (table des familles), la page durable de la capacité.

**Exclus :** le retrait des lignes `System.Logger` de `SqliteTransactionRunner` (design.md, D2) ; les échecs de
nettoyage d'une transaction en échec (`rollbackSuppressing`, `restoreAfterFailure`), déjà ajoutés en supprimé à
l'échec propagé ; l'ordre de la quarantaine et de la restauration (`SqliteRestoreQuarantineOrderingTest`).

## Acteurs et surfaces concernés

L'opérateur qui lit la route `metrics` du serveur local ou distant (rôle `ADMIN`) ou le journal opérationnel.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les trois issues ne sont écrites que par `System.Logger` | existant vérifié | `SqliteTransactionRunner.java:14,110,119,131` |
| Le recorder de la connexion est déjà connu du runner | existant vérifié | `SqliteTransactionRunner.java:41`, `SqliteContentionMetrics.of` |
| Une connexion que MORPHEUS n'a pas ouverte compte sur un recorder muet | existant vérifié | `SqliteContentionMetrics.of` rend `OperationalRecorder.noop()` hors `Carrier` |
| Un compteur par transaction, quelle que soit l'issue | décision | design.md, D1 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : un compteur de plus, retour arrière sans coût |

## Impact

`morpheus-store-sqlite` (`com.morpheus.store.sqlite.runtime`), `morpheus-application`
(`com.morpheus.application.operability`). Aucun test d'architecture modifié, aucune surface de
`contracts/public-surfaces.tsv` ni de l'OpenAPI, aucun schéma SQLite. Aucune dépendance avec les autres constats du
sprint 2 ; `answer-an-unforeseen-cli-failure-as-an-internal-error` ajoute aussi une famille à `SuppressedFailureFamily`
et une exigence à `suppressed-failure-trace` : les deux s'ajoutent sans se recouvrir.

## Questions ouvertes

Aucune.
