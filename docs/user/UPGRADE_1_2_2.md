# Mise à niveau vers MORPHEUS 1.2.2

Statut : **ACTIVE — MORPHEUS 1.2.2 EN PRÉPARATION**

Ce guide couvre la mise à niveau depuis MORPHEUS 1.2.1 vers MORPHEUS 1.2.2, qui n'est pas encore publiée. Il décrit ce
que fait la version en préparation de la base ; les dates et les fichiers à télécharger seront ceux de sa publication.

**À lire avant d'installer.** Comme 1.2.1, cette version fait avancer le schéma SQLite. La première ouverture de la base
par 1.2.2 la migre, et un programme plus ancien refuse ensuite de l'ouvrir. Le seul retour supporté passe par une
sauvegarde prise **avant** cette première ouverture.

## Résumé

```text
1.2.1 program files  -> remplacés par 1.2.2
persistent data      -> conservées, base migrée à la première ouverture
configuration        -> conservée
SQLite schema        -> V020 (1.2.1) vers V022
retour à 1.2.1       -> uniquement par restauration d'une sauvegarde prise avant la mise à niveau
```

Migrations appliquées, dans l'ordre, en une seule transaction :

```text
V021  policy audit order key
V022  serve ordered reads from an index
```

V021 ajoute à chaque enregistrement du journal d'audit des policies une clé d'ordre, calculée à partir de son instant,
et l'index qui permet de lire ce journal page par page. Les enregistrements eux-mêmes ne sont pas modifiés.

V022 ne change aucune donnée : elle retire onze index qui répètent le début d'un autre index de leur table, et remplace
deux index pour que la lecture des exigences courantes d'un instantané et le listage des vues enregistrées d'un
périmètre sortent de l'index dans l'ordre où ils sont rendus, sans tri temporaire.

Elles sont appliquées automatiquement, vérifiées par checksum, et ne s'appliquent qu'une fois. Si l'une échoue, aucune
n'est conservée : la base reste à son schéma de départ.

**Changement de forme à connaître.** L'audit d'un pack de policies (`policy audit`, `GET
/api/v1/policy-packs/{id}/audit`) rend désormais une **page** — `offset`, `limit`, `totalMatches`, `hasMore`, `items` —
au lieu d'un tableau. Un script qui lisait le tableau doit lire `items` et suivre `hasMore`. Voir
[`POLICY_PACKS.md`](POLICY_PACKS.md).

## Sauvegarde obligatoire

La sauvegarde n'est pas une précaution : c'est la seule façon de revenir à 1.2.1. Elle se prend
**avec la version déjà installée**, avant d'installer la nouvelle.

1. Arrêter tous les processus MORPHEUS : serveur local, serveur remote, sessions MCP ouvertes par un client.
2. Créer la sauvegarde avec le programme 1.2.1 :

   ```text
   morpheus server backup create
   ```

   Le fichier est écrit dans le répertoire `backups` de l'installation
   (`%LOCALAPPDATA%\MORPHEUS\backups` sous Windows, `${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups` sous
   Linux), ou dans le répertoire passé par `--output-dir`.
3. La vérifier, toujours avec 1.2.1 :

   ```text
   morpheus server backup verify --file <fichier de sauvegarde>
   ```

   La sortie donne la version de schéma de la sauvegarde : elle doit être celle de 1.2.1, soit 20.
4. Garder ce fichier dans le répertoire `backups` : mettre à jour ou désinstaller MORPHEUS ne le supprime pas. En garder
   une seconde copie hors du répertoire d'installation, sur un autre support si possible.

**Pourquoi il ne faut pas attendre.** `server backup create` ouvre la base avant de la copier, et l'ouvrir la migre :
une sauvegarde est toujours amenée au schéma du programme qui l'exécute. Créée avec 1.2.2, elle est déjà en V022, et
`server restore` de 1.2.1 la refuse. Une sauvegarde prise après le premier démarrage de 1.2.2 protège les données ;
elle ne permet pas de revenir en arrière.

**Le filet.** À sa première ouverture d'une base plus ancienne que lui, 1.2.2 en écrit lui-même une copie avant de la
migrer, à côté de la base :

```text
morpheus.db.pre-migration-v020-<horodatage UTC>-<8 caractères>.db
```

C'est la base telle qu'elle était, à son schéma d'origine : elle se vérifie et se restaure comme une sauvegarde
(`server backup verify`, `server restore`). S'il ne peut pas l'écrire, 1.2.2 refuse de migrer
(`PRE_MIGRATION_BACKUP_FAILED`) et la base reste intacte. Il y en a une par tentative de migration, elle n'est jamais
supprimée automatiquement, et elle est sur le même disque que la base : elle ne remplace pas l'étape ci-dessus, elle
rattrape celui qui l'a oubliée.

## Installation

Vérifier le SHA-256 de chaque asset téléchargé avant de l'utiliser. Les installer comme ceux de la version précédente,
en remplaçant les fichiers programme et sans toucher à l'état persistant :
[INSTALLATION.md](INSTALLATION.md).

## Premier démarrage et vérifications

La première commande qui ouvre la base la migre. Vérifier ensuite :

```text
morpheus --version
morpheus --json product-info
```

Une sauvegarde créée à partir de ce moment, puis vérifiée par `server backup verify`, annonce le schéma V022 dans son
champ `schemaVersion`. C'est le signe que la migration a eu lieu.

## Retour à 1.2.1

Réinstaller les fichiers 1.2.1 ne suffit pas : le programme 1.2.1 refuse d'ouvrir une base plus récente que lui, avec

```text
SQLite schema version 22 is newer than supported 20
```

Il n'existe pas de migration descendante. Le retour se fait ainsi :

1. arrêter tous les processus MORPHEUS 1.2.2 ;
2. mettre de côté une copie de la base V022, pour diagnostic ;
3. remettre le programme 1.2.1 ;
4. restaurer, avec 1.2.1, la sauvegarde prise avant la mise à niveau :

   ```text
   morpheus server restore --file <fichier de sauvegarde> --confirm
   ```

5. démarrer 1.2.1 et vérifier projets, snapshots actifs et historique ;
6. retirer ou adapter les intégrations MCP qui pointent vers le binaire 1.2.2.

La restauration est hors ligne : elle échoue tant qu'un serveur tient la base, et ne doit jamais être tentée par-dessus
une base utilisée par une instance active.

**Ce qui est perdu.** Tout ce qui a été enregistré par 1.2.2 après la sauvegarde — publications, changements,
politiques, vues — n'existe pas dans la sauvegarde et ne revient pas.

Sans sauvegarde antérieure à la mise à niveau, il n'y a pas de retour à 1.2.1 avec les données.

## Dépannage

### `is newer than supported`

Le programme est plus ancien que la base, ou que la sauvegarde, qu'on lui présente. Utiliser la version qui a migré
la base, ou suivre « Retour à 1.2.1 ».
