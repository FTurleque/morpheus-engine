---
id: DATA-0110
title: "Une copie de la base est conservée avant que son schéma n'avance, et sans elle la migration n'a pas lieu"
domain: data
rules: false
---

# Une copie de la base est conservée avant que son schéma n'avance, et sans elle la migration n'a pas lieu

- Statut : **Acceptée**
- Date : 2026-10-11

## Context

La migration de schéma est sûre : atomique, registre à checksum, historique divergent refusé (ADR-0021). Ce qu'elle
ne sait pas faire, c'est revenir en arrière. Une fois le registre avancé, seule une copie prise avant l'avancée permet
de reprendre un programme antérieur. Trois faits rendent cette copie indispensable au produit lui-même :

- **Une procédure ne protège que l'opérateur qui la lit**, et le dommage n'est pas réversible.
- **La sauvegarde manuelle arrive trop tard si elle est prise avec le nouveau programme.**
  `SqliteServerMaintenance.createBackup` ouvre le store avant de copier, et ouvrir migre : sans autre mesure, la
  sauvegarde est déjà au nouveau schéma.
- **Le programme antérieur ne protège pas toujours.** Une version publiée antérieure au refus d'un schéma plus récent
  que le programme ouvre une base migrée par une version ultérieure sans la refuser.

Les stores s'ouvrent par opération (ADR-0109), pas une fois au démarrage : la première ouverture d'une base ancienne
peut survenir pendant un appel HTTP ou un outil MCP.

Alternatives écartées :

- **La procédure seule.** Elle est écrite (`docs/user/UPGRADE_1_2_1.md`) et reste la première étape ; elle ne protège
  que l'opérateur qui la lit.
- **Un contrôle par l'installeur Windows.** Il ne couvre qu'un des artefacts distribués et ne connaît pas la base.
- **Un nom fixe par version d'origine, vérifié puis réutilisé.** Après une restauration hors ligne ou un retour au
  programme précédent, la base en version N peut être une autre que celle dont la copie « de version N » a été tirée, et la
  réutiliser donnerait un mauvais chemin de retour. Elle supposait aussi un déplacement atomique sans remplacement que
  le JDK n'offre pas.
- **Le répertoire `backups` du layout (ADR-0088).** C'est sa place naturelle, mais le store ne le connaît pas : il est
  résolu dans `morpheus-cli` (`CliLayout`), alors que le store s'ouvre depuis chaque racine de composition. La
  décision 1 dit « une copie », pas « un fichier voisin » : la destination peut changer sans changer la décision.
- **Migrer quand même avec un avertissement.** Le produit n'a pas de journal qu'un opérateur lit à coup sûr, et le
  dépôt interdit la dégradation silencieuse.

## Decision

1. **Avant d'appliquer une migration à une base existante, le store en écrit une copie.** « Existante » veut dire :
   le registre `schema_migrations` existe et sa plus haute version est strictement comprise entre 0 et
   `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`. Une base neuve, une base à jour et une base en avance sur le
   programme ne sont pas copiées ; la dernière reste refusée par la migration, sous son message.
2. **Une copie par tentative, sous un nom que personne d'autre ne peut tenir** :
   `<base>.pre-migration-v<NNN>-<horodatage UTC>-<8 hex>.db`, à côté de la base. `VACUUM INTO` refuse une cible
   existante : une copie ne remplace jamais un fichier.
3. **La copie est contrôlée contre la version qu'elle porte dans son nom.** Elle doit passer `integrity_check` et
   enregistrer la version d'origine. La version est lue avant la copie, hors transaction : si un autre processus a
   migré entre-temps, la copie enregistre une autre version, n'est pas une copie pré-migration, et le processus qui
   vient de l'écrire la supprime.
4. **La copie ne passe pas par la vérification de sauvegarde.** Celle-ci refuse un registre que le programme
   n'accepte pas, et ce refus appartient à la migration : un historique divergent reste signalé comme tel.
5. **Sans copie, pas de migration.** Si la copie ne peut pas être écrite ou échoue à son contrôle, l'ouverture échoue
   avec une `KnowledgeStoreException` qui nomme `PRE_MIGRATION_BACKUP_FAILED`, la version d'origine et la version
   visée, **sans aucun chemin** ; la cause reste dans la chaîne d'exceptions. Le registre n'a pas bougé.
6. **`server backup create` migre avant de copier.** L'ouverture qu'il fait d'abord est celle qui écrit la copie
   pré-migration : l'opérateur qui sauvegarde trop tard garde quand même la copie utile.

Le mécanisme vit dans `morpheus-store-sqlite` : `SqlitePreMigrationCopy`, appelé par `SqliteStoreConnection` avant
`SqliteSchemaManager.migrate`, une fois par portée de connexion.

## Do's and Don'ts

### Do

- Écrire une copie avant toute migration d'une base dont la version est entre 0 et la version supportée, exclues.
- Nommer chaque copie avec la version d'origine, un horodatage UTC et un suffixe aléatoire.
- Vérifier l'intégrité de la copie et la version qu'elle enregistre ; supprimer une copie qui n'enregistre pas la
  version d'origine.
- Refuser l'ouverture, sans migrer, quand la copie ne peut pas être écrite ou contrôlée.
- Nommer `PRE_MIGRATION_BACKUP_FAILED` et les deux versions dans le refus.

### Don't

- Ne pas copier une base neuve, à jour, ou en avance sur le programme.
- Ne pas remplacer ni réutiliser un fichier existant comme copie.
- Ne pas faire passer la copie par la vérification de sauvegarde.
- Ne pas mettre de chemin dans le message du refus.
- Ne pas migrer avec un simple avertissement quand la copie manque.

## Consequences

### Positives

- Tout passage de schéma laisse un chemin de retour vers le programme antérieur, sans dépendre de l'opérateur.
- Une sauvegarde prise trop tard avec le nouveau programme laisse quand même la copie utile.
- Seules des copies vraies subsistent, y compris sous ouvertures concurrentes.

### Négatives

- **Espace disque** : une copie de la taille de la base par tentative, jamais purgée. Une migration qui échoue
  durablement en ajoute une à chaque nouvelle tentative.
- **Latence** : un `VACUUM INTO` sur la première ouverture qui suit une mise à niveau, éventuellement pendant une
  requête ; plus ensuite. Hors portée de connexion, chaque ouverture lit une fois de plus la version enregistrée.
- **Un répertoire de données non inscriptible** arrête une mise à niveau. C'est voulu : le refus nomme sa cause et
  rien n'a été migré.

### Risques

- **Ce n'est pas une sauvegarde de reprise après sinistre** : la copie est sur le même volume que la base.
- **Deux ouvertures concurrentes d'une même base ancienne** laissent chacune au plus une copie, et seules des copies
  vraies subsistent. La perdante peut encore échouer sur la migration elle-même ; cette décision ne change pas ce
  comportement.

## Compliance and Enforcement

`morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqlitePreMigrationCopyTest.java` :

| Méthode | Ce qu'elle tient |
|---|---|
| `anOutdatedDatabaseIsCopiedAsItWasThenMigrated` | copie intègre, à la version d'origine, sans objet d'une migration ultérieure |
| `anOutdatedDatabaseOpenedInsideARequestScopeIsCopiedOnTheScopedConnection` | une seule copie par portée, quel que soit le nombre de stores |
| `aNewDatabaseAndAnUpToDateDatabaseAreNotCopied` | aucun fichier ajouté |
| `aDatabaseAheadOfTheProgramIsRefusedWithoutACopy` | le refus existant passe d'abord |
| `aDivergentHistoryIsStillRefusedAsAHistoryMismatch` | le refus de la migration n'est pas masqué |
| `aCopyThatCannotBeWrittenIsRefusedByNameWithoutAPathAndReplacesNothing` | refus nommé, sans chemin, fichier existant intact |
| `aRetryAfterAFailedMigrationWritesANewCopyAndLeavesTheFirstAlone` | une copie par tentative |
| `aMigrationThatKeepsFailingKeepsOneCopyPerAttempt` | une copie par tentative, échecs répétés |
| `aCopyTakenAfterSomeoneElseMigratedIsDiscarded` | une copie qui n'enregistre pas sa version est supprimée |
| `aDatabaseReplacedByAnotherOfTheSameOldVersionIsCopiedAsItIs` | pas de réutilisation d'une copie de même version |
| `aServerBackupOfAnOutdatedDatabaseIsAlreadyMigratedAndOnlyTheKeptCopyIsNot` | décision 6 |
| `twoConcurrentOpeningsLeaveOnlyTrueCopies` | deux fils, deux connexions physiques |
| `twoProcessesOpeningOneOutdatedDatabaseLeaveOnlyTrueCopies` | deux processus |

## References

- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — store SQLite.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — fondation des migrations de schéma.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — concurrence SQLite
  bornée.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation programme /
  données persistantes.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées.
- [Guide de mise à niveau 1.2.1](../../user/UPGRADE_1_2_1.md).
