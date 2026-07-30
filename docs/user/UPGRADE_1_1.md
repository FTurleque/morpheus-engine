# Mettre MORPHEUS à niveau de 1.0.0 vers 1.1.0

Statut : **GUIDE CANDIDAT — 1.1.0 NON ENCORE PUBLIÉE**

Ce guide décrit la procédure contrôlée d'upgrade depuis la release stable `v1.0.0` vers la candidate `v1.1.0`.

## Ce qui change

MORPHEUS 1.1.0 ajoute les capacités M21 à M27, notamment les plugins provider, portfolios, Query DSL, saved views, Policy Packs, mode serveur distant optionnel et raisonnement assisté fondé sur des preuves.

Le schéma SQLite évolue de V012 à V015 :

```text
V013  portfolio intelligence
V014  saved views
V015  policy packs
```

Les migrations sont appliquées automatiquement à l'ouverture de la base par MORPHEUS 1.1.0. Elles sont vérifiées par checksum et ne doivent être appliquées qu'une fois.

## Avant de commencer

1. Vérifier que la version installée est bien 1.0.0 :

```powershell
morpheus --version
```

ou :

```bash
morpheus --version
```

2. Arrêter toutes les instances MORPHEUS, y compris :

- CLI encore active ;
- serveur MCP ;
- API locale ;
- serveur remote d'équipe ;
- outil externe maintenant une connexion à la base.

3. Identifier les chemins persistants.

Windows :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Linux :

```text
${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

## Sauvegarde obligatoire

Le backup doit être créé avant le premier démarrage 1.1.0.

Avec les commandes de maintenance disponibles dans votre installation :

```bash
morpheus server backup --output <backup-file>
```

Puis vérifier le backup :

```bash
morpheus server backup-verify --input <backup-file>
```

Lorsque ces commandes ne sont pas disponibles dans votre mode d'exécution, arrêtez MORPHEUS et copiez le fichier SQLite avec ses éventuels fichiers associés dans un emplacement distinct. Ne copiez jamais une base pendant qu'un serveur écrit dedans.

Conservez ensemble :

- le backup SQLite vérifié ;
- la configuration 1.0.0 ;
- les tokens ou procédures de régénération nécessaires ;
- les binaires 1.0.0 ;
- les checksums de la release 1.0.0.

## Installer 1.1.0

### Windows setup

Télécharger et vérifier :

```text
MORPHEUS-1.1.0-windows-x64-setup.exe
MORPHEUS-1.1.0-windows-x64-setup.exe.sha256
```

Exécuter le setup per-user. L'installation programme est séparée des données persistantes ; la désinstallation ne doit pas supprimer `%LOCALAPPDATA%\MORPHEUS`.

### Windows portable

Extraire :

```text
morpheus-1.1.0-windows-x64.zip
```

Ne remplacez pas votre backup par le contenu de l'archive portable.

### Linux portable

Extraire :

```text
morpheus-1.1.0-linux-x64.tar.gz
```

Conserver les variables XDG identiques à celles utilisées avec 1.0.0 pour retrouver le même état persistant.

## Premier démarrage contrôlé

Exécuter d'abord une commande locale simple :

```bash
morpheus --version
```

Résultat attendu :

```text
1.1.0
```

Puis interroger l'état produit :

```bash
morpheus --json product-info
```

Ensuite, démarrer MORPHEUS avec les mêmes répertoires de données que 1.0.0. Le premier accès à la base applique V013, V014 et V015.

Ne lancez pas plusieurs instances simultanément pendant cette première migration.

## Vérifications après migration

Contrôler au minimum :

- la liste des projets ;
- le snapshot actif de chaque projet critique ;
- l'historique publié ;
- les références externes ;
- la synchronisation et la fraîcheur ;
- la composition multi-provider ;
- les audits de lifecycle ;
- l'API health/readiness ;
- la version retournée par CLI et HTTP.

Exemples :

```bash
morpheus projects list
morpheus --json product-info
```

Pour l'API locale :

```text
GET /api/v1/health
GET /api/v1/readiness
GET /api/v1/version
```

Les nouvelles tables portfolio, saved views et Policy Packs peuvent être vides après l'upgrade ; elles ne doivent pas modifier les faits publiés existants.

## Mode serveur distant

Le mode remote reste opt-in. Après l'upgrade :

- vérifier le keystore PKCS12 ;
- vérifier les permissions du fichier d'identités ;
- ne jamais recopier un token en clair dans la configuration ;
- vérifier les rôles READ/WRITE/ADMIN ;
- vérifier que le bind non-loopback exige toujours HTTPS et authentification ;
- tester avec une identité READ avant d'autoriser les écritures.

## Raisonnement assisté

L'upgrade n'active aucun LLM obligatoire et ne transforme aucune inférence en fait publié.

Mode facts-only :

```bash
morpheus reason analyze \
  --question "What is published?" \
  --evidence 'fact-1|PUBLISHED_FACT|requirement:req-1|Published requirement'
```

Le résultat doit conserver `mutated=false`.

## Retour arrière

### Retour arrière des binaires

Vous pouvez remettre les binaires 1.0.0, mais **une base déjà migrée en V015 ne doit pas être ouverte en écriture par 1.0.0** sans preuve explicite de compatibilité descendante.

### Retour arrière complet recommandé

1. arrêter MORPHEUS 1.1.0 ;
2. conserver une copie de diagnostic de la base V015 ;
3. restaurer offline le backup V012 réalisé avant upgrade ;
4. remettre les binaires et la configuration 1.0.0 ;
5. redémarrer localement ;
6. vérifier projets, snapshots actifs et historique.

Le restore doit rester offline. Ne restaurez jamais par-dessus une base utilisée par une instance active.

## Dépannage

### Migration history mismatch

Une migration historique ou son checksum a été modifié. N'essayez pas de réécrire manuellement `schema_migrations`. Arrêtez MORPHEUS et restaurez le backup vérifié, puis analysez le delta de distribution.

### Base verrouillée

Vérifiez qu'aucune autre CLI, API, MCP ou instance remote n'utilise la base.

### Version toujours égale à 1.0.0

Vérifiez le chemin du launcher réellement exécuté et l'ordre du `PATH`. Les archives 1.1.0 doivent contenir un runtime et un JAR portant la même version.

### Serveur remote refusé

Un bind non-loopback exige le mode remote explicite, TLS et authentification. Ce refus est une protection, pas une régression.

## Preuves de release

Avant utilisation en production, vérifier que la release GitHub 1.1.0 est stable, non draft et qu'elle contient exactement les huit assets attendus. Comparer chaque payload à son fichier `.sha256`.

La preuve autoritative sera :

[`../validation/VALIDATION_R2.md`](../validation/VALIDATION_R2.md)

Tant que cette preuve reste `NOT RUN` ou `IN PROGRESS`, 1.1.0 ne doit pas être considérée comme publiée.