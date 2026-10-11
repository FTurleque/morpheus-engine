# Mise à niveau vers MORPHEUS 1.2.1

Statut : **EN PRÉPARATION — MORPHEUS 1.2.1 NON PUBLIÉ**

Ce guide couvre la mise à niveau depuis MORPHEUS 1.2.0 vers MORPHEUS 1.2.1. La dernière release publiée reste
`v1.2.0`, et rien ici ne doit être lu comme l'annonce d'une publication.

**Depuis 1.1.0.** Une base 1.1.0 est au même schéma V015 qu'une base 1.2.0 : les migrations appliquées, la sauvegarde
à prendre et le retour sont les mêmes, en lisant « 1.1.0 » là où ce guide écrit « 1.2.0 ». Ce chemin n'a pas été
qualifié séparément, et les changements propres à 1.2.0 (câblage des clients MCP) restent décrits dans
[`UPGRADE_1_2.md`](UPGRADE_1_2.md).

**À lire avant d'installer.** Contrairement à 1.2.0, cette version fait avancer le schéma SQLite. La première
ouverture de la base par 1.2.1 la migre, et **1.2.0 ne s'en aperçoit pas** : il ouvrirait la base migrée sans la
refuser. Le seul retour supporté passe par une sauvegarde prise **avant** cette première ouverture.

## Résumé

```text
1.2.0 program files  -> remplacés par 1.2.1
persistent data      -> conservées, base migrée à la première ouverture
configuration        -> conservée
SQLite schema        -> V015 (1.2.0) vers V020
retour à 1.2.0       -> uniquement par restauration d'une sauvegarde prise avant la mise à niveau
```

Migrations appliquées, dans l'ordre, en une seule transaction :

```text
V016  specification version sequence integrity
V017  specification version sequence reservations
V018  policy version sequence integrity
V019  sync state revision
V020  composition precedence recorded
```

Elles sont appliquées automatiquement, vérifiées par checksum, et ne s'appliquent qu'une fois. Si l'une échoue, aucune
n'est conservée : la base reste à son schéma de départ.

Les changements de comportement que doit connaître un utilisateur de 1.2.0 sont recensés dans les
[notes de version](release-notes/RELEASE_NOTES_1.2.1.md).

## Sauvegarde obligatoire

La sauvegarde n'est pas une précaution : c'est la seule façon de revenir à 1.2.0. Elle se prend
**avec la version déjà installée**, avant d'installer la nouvelle.

1. Arrêter tous les processus MORPHEUS : serveur local, serveur remote, sessions MCP ouvertes par un client.
2. Créer la sauvegarde avec le programme 1.2.0 :

   ```text
   morpheus server backup create
   ```

   Le fichier est écrit dans le répertoire `backups` de l'installation
   (`%LOCALAPPDATA%\MORPHEUS\backups` sous Windows, `${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups` sous
   Linux), ou dans le répertoire passé par `--output-dir`.
3. La vérifier, toujours avec 1.2.0 :

   ```text
   morpheus server backup verify --file <fichier de sauvegarde>
   ```

   La sortie donne la version de schéma de la sauvegarde : elle doit être celle de 1.2.0, soit 15.
4. Conserver ce fichier hors du répertoire du programme. Mettre à jour ou désinstaller MORPHEUS ne supprime pas
   `backups`, mais une copie ailleurs ne coûte rien.

**Pourquoi il ne faut pas attendre.** `server backup create` ouvre la base avant de la copier, et l'ouvrir la migre :
une sauvegarde est toujours amenée au schéma du programme qui l'exécute. Créée avec 1.2.1, elle est déjà en V020, et
`server restore` de 1.2.0 la refuse. Une sauvegarde prise après le premier démarrage de 1.2.1 protège les données ;
elle ne permet pas de revenir en arrière.

**Le filet de 1.2.1.** À sa première ouverture d'une base plus ancienne que lui, 1.2.1 en écrit lui-même une copie
avant de la migrer, à côté de la base :

```text
morpheus.db.pre-migration-v015-<horodatage UTC>-<8 caractères>.db
```

C'est la base telle qu'elle était, à son schéma d'origine : elle se vérifie et se restaure comme une sauvegarde
(`server backup verify`, `server restore`). S'il ne peut pas l'écrire, 1.2.1 refuse de migrer
(`PRE_MIGRATION_BACKUP_FAILED`) et la base reste intacte. Il y en a une par tentative de migration, elle n'est jamais
supprimée automatiquement, et elle est sur le même disque que la base : elle ne remplace pas l'étape ci-dessus, elle
rattrape celui qui l'a oubliée.

Si le programme a déjà été remplacé mais que 1.2.1 n'a **encore jamais été démarré**, la base est toujours en V015.
Copier alors, tous processus arrêtés, le fichier de base (`data/morpheus.db` par défaut, voir
[INSTALLATION.md](INSTALLATION.md)) et ses éventuels fichiers compagnons de même nom (`-journal`, `-wal`, `-shm`) avant
tout démarrage. C'est un pis-aller : cette copie n'a pas été vérifiée par MORPHEUS.

## Installation

Vérifier le SHA-256 de l'asset téléchargé avant de l'utiliser.

### Windows — installateur

```text
MORPHEUS-1.2.1-windows-x64-setup.exe
```

Lancer le setup avec le même utilisateur Windows que l'installation existante. Il remplace les fichiers programme sous
`%LOCALAPPDATA%\Programs\MORPHEUS` et ne touche pas à l'état persistant sous `%LOCALAPPDATA%\MORPHEUS`. Le setup ne
connaît pas la base : il ne prend aucune sauvegarde et ne migre rien. La migration a lieu au premier démarrage.

### Windows — archive portable

```text
morpheus-1.2.1-windows-x64.zip
```

Extraire dans un nouveau répertoire plutôt que par-dessus l'ancien : garder l'arbre 1.2.0 intact rend le retour plus
simple.

### Linux / WSL

```bash
tar -xzf morpheus-1.2.1-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Même conseil : extraire à côté de l'arbre 1.2.0, pas par-dessus.

## Premier démarrage et vérifications

La première commande qui ouvre la base la migre. Vérifier ensuite :

```text
morpheus --version
morpheus --json product-info
```

Version attendue :

```text
1.2.1
```

Une sauvegarde créée à partir de ce moment, puis vérifiée par `server backup verify`, annonce le schéma V020 dans son
champ `schemaVersion`. C'est le signe que la migration a eu lieu.

## Retour à 1.2.0

Réinstaller les fichiers 1.2.0 ne suffit pas, et c'est le cas dangereux : **1.2.0 ne refuse pas une base migrée**.
Le contrôle qui refuse un schéma plus récent que le programme n'existe qu'à partir de 1.2.1 ; 1.2.0 ne vérifie que les
migrations qu'il connaît, les trouve appliquées, et démarre. Il travaille alors sur un schéma qu'il ne connaît pas :

```text
V017  une table de réservation de séquences que 1.2.0 ne tient pas à jour
V018  des déclencheurs qui peuvent refuser ses écritures
V020  une valeur de résolution de conflit que 1.2.0 ne sait pas lire
```

Ce fonctionnement n'a été ni conçu ni qualifié. **Ne jamais démarrer 1.2.0 sur une base que 1.2.1 a ouverte.**

Pour savoir, avec 1.2.0, si un fichier est trop récent, le lui faire vérifier comme une sauvegarde — c'est le seul
endroit où 1.2.0 fait ce contrôle :

```text
morpheus server backup verify --file <fichier>
backup schema version <N> is newer than supported 15
```

À partir de 1.2.1, le programme refuse lui-même d'ouvrir une base plus récente que lui, avec
`SQLite schema version <N> is newer than supported <M>` : une future mise à niveau ne reproduira pas ce piège.

Il n'existe pas de migration descendante. Le retour se fait ainsi :

1. arrêter tous les processus MORPHEUS 1.2.1 ;
2. mettre de côté une copie de la base V020, pour diagnostic ;
3. remettre le programme 1.2.0 (setup 1.2.0, ou l'arbre portable conservé) ;
4. restaurer, avec 1.2.0, la sauvegarde prise avant la mise à niveau :

   ```text
   morpheus server restore --file <fichier de sauvegarde> --confirm
   ```

5. démarrer 1.2.0 et vérifier projets, snapshots actifs et historique ;
6. retirer ou adapter les intégrations MCP qui pointent vers le binaire 1.2.1.

La restauration est hors ligne : elle échoue tant qu'un serveur tient la base, et ne doit jamais être tentée par-dessus
une base utilisée par une instance active.

**Ce qui est perdu.** Tout ce qui a été enregistré par 1.2.1 après la sauvegarde — publications, changements,
politiques, vues — n'existe pas dans la sauvegarde et ne revient pas. Un retour de programme n'est jamais un retour
logique de snapshot MORPHEUS.

Sans sauvegarde antérieure à la mise à niveau, il n'y a pas de retour à 1.2.0 avec les données.

## Dépannage

### `is newer than supported`

Le programme est plus ancien que la base, ou que la sauvegarde, qu'on lui présente. Utiliser la version qui a migré
la base, ou suivre « Retour à 1.2.0 ». Ce message vient de 1.2.1 et des versions suivantes à l'ouverture d'une base ;
1.2.0 ne le donne que pour une sauvegarde.

### 1.2.0 a été démarré sur une base migrée

L'arrêter. Ne pas compter sur ce qu'il a écrit. Revenir à 1.2.1 sur cette base, ou restaurer la sauvegarde antérieure
à la mise à niveau.

### Migration history mismatch

Une migration historique ou son checksum ne correspond pas à ce que le programme attend. Ne pas réécrire
`schema_migrations` à la main. Arrêter MORPHEUS, restaurer la sauvegarde vérifiée, puis analyser la différence de
distribution.

### La migration échoue

Elle est annulée en entier : la base reste en V015 et 1.2.0 peut toujours l'ouvrir. Conserver le message d'erreur
avant toute nouvelle tentative.

## Références

- [`INSTALLATION.md`](INSTALLATION.md)
- [`UPGRADE_1_2.md`](UPGRADE_1_2.md) — mise à niveau vers 1.2.0, clients MCP
- [`release-notes/RELEASE_NOTES_1.2.1.md`](release-notes/RELEASE_NOTES_1.2.1.md)
- [`TEAM_REMOTE_SERVER.md`](TEAM_REMOTE_SERVER.md) — sauvegarde et restauration côté serveur
