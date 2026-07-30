# Mise à niveau vers MORPHEUS 1.2.0

Statut : **CANDIDATE — publication 1.2.0 non encore effectuée**

Ce guide couvre la mise à niveau depuis MORPHEUS 1.1.0 vers MORPHEUS 1.2.0.

## Résumé

MORPHEUS 1.2.0 ajoute le câblage conservateur des principaux clients MCP. Le schéma SQLite reste V015 : aucune migration métier ou de données n’est introduite.

```text
1.1.0 program files  -> remplacés par 1.2.0
persistent data      -> conservées
configuration        -> conservée
SQLite schema        -> V015 inchangé
MCP client wiring    -> opt-in
```

## Avant la mise à niveau

1. Arrêter les processus MORPHEUS en cours.
2. Conserver une copie de la base et de la configuration si une sauvegarde supplémentaire est souhaitée.
3. Fermer les clients MCP ciblés avant leur configuration : IntelliJ, Copilot CLI, Claude Code, Claude Desktop ou Codex.
4. Vérifier le SHA-256 de l’asset téléchargé.

## Windows — installateur

Lancer :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
```

L’installateur met à niveau le programme sous :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

Les données restent sous :

```text
%LOCALAPPDATA%\MORPHEUS
```

Les cinq intégrations MCP sont proposées comme options indépendantes et décochées par défaut. Sélectionner uniquement les clients à configurer.

## Windows — archive portable

Extraire :

```text
morpheus-1.2.0-windows-x64.zip
```

Le gestionnaire est disponible sous :

```text
integration\configure-mcp-clients.ps1
```

Consulter d’abord :

```text
integration\README.md
docs\user\MCP_CLIENTS.md
```

## Linux / WSL

Extraire :

```bash
tar -xzf morpheus-1.2.0-linux-x64.tar.gz
```

Le launcher reste autonome avec son runtime Java embarqué. Les données et configurations utilisent les racines XDG existantes.

Le gestionnaire PowerShell est embarqué comme référence commune. Son utilisation nécessite PowerShell lorsque la configuration automatique d’un client compatible est souhaitée ; la configuration manuelle reste documentée dans `docs/user/MCP_CLIENTS.md`.

## Configurer un client MCP après installation

Le serveur lancé par les clients est :

```text
morpheus.exe mcp --stdio
```

Le gestionnaire peut définir :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
```

Le processus est conservateur :

- sauvegarde avant écriture ;
- préservation des autres entrées ;
- refus d’écraser une entrée `morpheus` étrangère ;
- idempotence ;
- registre de propriété ;
- désinstallation state-driven.

## Vérifications après mise à niveau

```powershell
morpheus --version
morpheus --json product-info
morpheus help
```

La version attendue est :

```text
1.2.0
```

Pour vérifier le serveur MCP sans client graphique, utiliser le smoke ou la procédure de diagnostic décrite dans `MCP_CLIENTS.md`.

## Désinstallation des intégrations MCP

La suppression des intégrations est distincte de la suppression des données MORPHEUS.

Le gestionnaire retire uniquement une entrée encore conforme à celle qu’il a créée. Une entrée modifiée ou préexistante est conservée et signalée.

## Retour à 1.1.0

Le schéma SQLite étant inchangé à V015, les données 1.2.0 restent structurellement compatibles avec 1.1.0. Toutefois, avant tout retour arrière :

- retirer ou adapter les intégrations MCP qui pointent vers le binaire 1.2.0 ;
- sauvegarder la base et les profils ;
- réinstaller 1.1.0 ;
- vérifier les launchers enregistrés dans chaque client.

Un rollback du programme ne doit jamais être confondu avec un rollback logique de snapshot MORPHEUS.

## Points inchangés

- aucune dépendance Docker ;
- API HTTP locale inchangée ;
- serveur remote HTTPS toujours opt-in ;
- Policy Packs, portfolios et saved views conservés ;
- backups/restores existants conservés ;
- MINOS et NEXUS restent optionnels ;
- aucun client MCP n’est configuré silencieusement.

## Références

- [`MCP_CLIENTS.md`](MCP_CLIENTS.md)
- [`INSTALLATION.md`](INSTALLATION.md)
- [`../release/RELEASE_NOTES_1.2.0.md`](../release/RELEASE_NOTES_1.2.0.md)
- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
