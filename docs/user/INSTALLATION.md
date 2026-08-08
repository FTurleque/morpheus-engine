# Installer MORPHEUS 1.2.0

MORPHEUS 1.2.0 est distribué avec son propre runtime Java. **L’utilisateur final n’a besoin ni de Git, ni de Maven, ni d’un JDK.**

## Windows — installation recommandée

Artefacts publiés :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
MORPHEUS-1.2.0-windows-x64-setup.exe.sha256
```

Le setup est **per-user** et ne demande pas d’élévation administrative.

Installation par défaut :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

Le setup peut ajouter MORPHEUS au `PATH` utilisateur et connecter explicitement le MCP natif à certains clients. Toutes ces options sont opt-in.

Clients MCP proposés :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Après installation :

```powershell
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --version
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --json product-info
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" paths
```

Version attendue : `1.2.0`.

## Données persistantes Windows

```text
Programme
%LOCALAPPDATA%\Programs\MORPHEUS

État persistant
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Base SQLite par défaut :

```text
%LOCALAPPDATA%\MORPHEUS\data\morpheus.db
```

Mettre à jour ou désinstaller MORPHEUS ne supprime pas le knowledge store par défaut.

## Mise à jour Windows

Lancer le setup 1.2.0 avec le même utilisateur Windows. Le setup conserve le même AppId et remplace les fichiers programme sans supprimer `data`, `config`, `logs` ou `backups`.

Depuis 1.1.0, le schéma SQLite reste V015 ; 1.2.0 n’introduit pas de migration de données. Voir [UPGRADE_1_2.md](UPGRADE_1_2.md).

## Désinstallation Windows

Utiliser **Applications installées** ou le désinstalleur du répertoire programme.

L’uninstall retire les fichiers programme et les intégrations qu’il gère encore. Pour les clients MCP :

- une entrée étrangère `morpheus` n’est jamais supprimée ;
- une entrée modifiée manuellement après installation est conservée ;
- la suppression est pilotée par le registre de propriété MORPHEUS.

L’état persistant sous `%LOCALAPPDATA%\MORPHEUS` est conservé par défaut.

## Windows — archive portable

Artefacts :

```text
morpheus-1.2.0-windows-x64.zip
morpheus-1.2.0-windows-x64.zip.sha256
morpheus-1.2.0-windows-x64-release-manifest.json
```

Extraire puis lancer :

```powershell
.\morpheus\morpheus.exe --version
```

Le gestionnaire MCP est inclus sous `integration\configure-mcp-clients.ps1`.

## Linux x64

Artefacts :

```text
morpheus-1.2.0-linux-x64.tar.gz
morpheus-1.2.0-linux-x64.tar.gz.sha256
morpheus-1.2.0-linux-x64-release-manifest.json
```

Vérifier puis extraire :

```bash
sha256sum -c morpheus-1.2.0-linux-x64.tar.gz.sha256
tar -xzf morpheus-1.2.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Layout par défaut :

```text
${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

## Overrides de chemins

CLI :

```text
--data-dir PATH
--config-dir PATH
--db PATH
```

Environnement :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_LOGS_DIR
MORPHEUS_BACKUPS_DIR
MORPHEUS_DB
```

## MCP natif

Le serveur local est :

```text
morpheus mcp --stdio
```

Docker n’est pas requis. Guide détaillé : [MCP_CLIENTS.md](MCP_CLIENTS.md).

## Intégrations MINOS / NEXUS

MINOS et NEXUS ne sont pas embarqués. Les adapters clients sont présents mais restent optionnels. Sans configuration, les commandes de statut retournent `DISABLED`.

Voir [INTEGRATIONS.md](INTEGRATIONS.md).

## Vérification SHA-256

Windows :

```powershell
Get-FileHash .\MORPHEUS-1.2.0-windows-x64-setup.exe -Algorithm SHA256
Get-Content .\MORPHEUS-1.2.0-windows-x64-setup.exe.sha256
```

Linux :

```bash
sha256sum -c morpheus-1.2.0-linux-x64.tar.gz.sha256
```

La preuve exacte de publication et de parité des huit assets est conservée dans [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md).
