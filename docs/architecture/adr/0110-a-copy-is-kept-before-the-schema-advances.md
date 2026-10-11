# ADR-0110 — Une copie de la base est conservée avant que son schéma n'avance, et sans elle la migration n'a pas lieu

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 11 octobre 2026
- Dépend de : ADR-0018 (store SQLite), ADR-0021 (fondation des migrations de schéma), ADR-0086 (concurrence SQLite
  bornée), ADR-0088 (séparation programme / données persistantes)
- Portée : `morpheus-store-sqlite` (`SqlitePreMigrationCopy`, `SqliteStoreConnection`)

## Contexte

La migration de schéma est sûre : atomique, registre à checksum, historique divergent refusé (ADR-0021). Ce qu'elle
ne sait pas faire, c'est revenir en arrière. Une fois le registre avancé, seule une copie prise avant l'avancée permet
de reprendre un programme antérieur, et rien dans le produit ne garantissait qu'elle existe (audit du 9 octobre 2026,
constat AUD-DEP-16). Trois faits aggravent le constat :

- **La procédure s'était affaiblie d'une release à l'autre.** Le guide 1.1.0 rendait la sauvegarde obligatoire ; le
  guide 1.2.0 la proposait « si une sauvegarde supplémentaire est souhaitée ».
- **La sauvegarde manuelle arrive trop tard si elle est prise avec le nouveau programme.**
  `SqliteServerMaintenance.createBackup` ouvre le store avant de copier, et ouvrir migre : la sauvegarde est déjà au
  nouveau schéma.
- **Le programme antérieur ne protège pas.** Le refus d'un schéma plus récent que le programme a été ajouté le
  12 août 2026, après la publication de 1.2.0. La 1.2.0 publiée ouvre une base migrée par une version ultérieure sans
  la refuser (lu dans les sources du tag `v1.2.0`, non exécuté).

Les stores s'ouvrent par requête (ADR-0109), pas une fois au démarrage : la première ouverture d'une base ancienne
peut survenir pendant un appel HTTP ou un outil MCP.

## Décision

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
6. **`server backup create` continue de migrer avant de copier.** L'ouverture qu'il fait d'abord est désormais celle
   qui écrit la copie pré-migration : l'opérateur qui sauvegarde trop tard garde quand même la copie utile.

## Alternatives écartées

- **La procédure seule.** Elle est écrite (`docs/user/UPGRADE_1_2_1.md`) et reste la première étape ; elle ne protège
  que l'opérateur qui la lit, et le dommage n'est pas réversible.
- **Un contrôle par l'installeur Windows.** Il ne couvre qu'un des trois artefacts distribués et ne connaît pas la
  base.
- **Un nom fixe par version d'origine, vérifié puis réutilisé.** Première version de cette décision, retirée en revue :
  après une restauration hors ligne ou un retour au programme précédent, la base en version N n'est plus celle dont la
  copie « de version N » a été tirée, et la réutiliser donnerait un mauvais chemin de retour. Elle supposait aussi un
  déplacement atomique sans remplacement que le JDK n'offre pas.
- **Le répertoire `backups` du layout (ADR-0088).** C'est sa place naturelle, mais le store ne le connaît pas : il est
  résolu dans `morpheus-cli`, et douze sites de composition ouvrent un store. Le faire descendre jusqu'au store
  reproduirait douze fois le défaut qu'AUD-TRV-05 demande de corriger. Quand la politique de connexion deviendra une
  propriété du module, la destination pourra en devenir une ; la décision 1 dit « une copie », pas « un fichier
  voisin ».
- **Migrer quand même avec un avertissement.** Le produit n'a pas de journal qu'un opérateur lit, et le dépôt interdit
  la dégradation silencieuse.

## Conséquences

- **Espace disque** : une copie de la taille de la base par tentative, jamais purgée. Une migration qui échoue
  durablement en ajoute une à chaque nouvelle tentative.
- **Latence** : un `VACUUM INTO` sur la première ouverture qui suit une mise à niveau, éventuellement une requête ; plus
  jamais ensuite. Hors scope de connexion, chaque ouverture lit une fois de plus la version enregistrée.
- **Un répertoire de données non inscriptible** arrête désormais une mise à niveau qui passait. C'est voulu : le refus
  nomme sa cause et rien n'a été migré.
- **Ce n'est pas une sauvegarde de reprise après sinistre** : la copie est sur le même volume que la base.
- **Deux ouvertures concurrentes d'une même base ancienne** laissent chacune au plus une copie, et seules des copies
  vraies subsistent. La perdante peut encore échouer sur la migration elle-même ; ce comportement préexiste et cette
  décision ne le change pas.

## Preuves exécutables

`SqlitePreMigrationCopyTest` (`morpheus-store-sqlite`) :

| Méthode | Ce qu'elle tient |
|---|---|
| `anOutdatedDatabaseIsCopiedAsItWasThenMigrated` | copie intègre, à la version d'origine, sans objet d'une migration ultérieure |
| `anOutdatedDatabaseOpenedInsideARequestScopeIsCopiedOnTheScopedConnection` | une seule copie par scope, quel que soit le nombre de stores |
| `aNewDatabaseAndAnUpToDateDatabaseAreNotCopied` | aucun fichier ajouté |
| `aDatabaseAheadOfTheProgramIsRefusedWithoutACopy` | le refus existant passe d'abord |
| `aDivergentHistoryIsStillRefusedAsAHistoryMismatch` | le refus de la migration n'est pas masqué |
| `aCopyThatCannotBeWrittenIsRefusedByNameWithoutAPathAndReplacesNothing` | refus nommé, sans chemin, fichier existant intact |
| `aRetryAfterAFailedMigrationWritesANewCopyAndLeavesTheFirstAlone` | une copie par tentative |
| `aCopyTakenAfterSomeoneElseMigratedIsDiscarded` | une copie qui n'enregistre pas sa version est supprimée |
| `twoConcurrentOpeningsLeaveOnlyTrueCopies` | deux fils, deux connexions physiques ; pas deux processus |
