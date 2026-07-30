# Connecter MORPHEUS aux clients MCP

MORPHEUS expose un serveur **Model Context Protocol natif sur STDIO**. Docker n’est requis pour aucune intégration locale.

```text
command  <installation>/morpheus.exe
args     mcp --stdio
```

Sous Linux :

```text
command  <installation>/bin/morpheus
args     mcp --stdio
```

Tous les clients doivent utiliser les mêmes répertoires persistants afin d’interroger la même base SQLite et les mêmes snapshots publiés.

## 1. Installation Windows recommandée

Le setup MORPHEUS propose des choix indépendants, tous décochés par défaut :

```text
Connecter le MCP natif MORPHEUS à :
  ☐ GitHub Copilot — JetBrains / IntelliJ
  ☐ GitHub Copilot CLI
  ☐ Claude Code
  ☐ Claude Desktop
  ☐ OpenAI Codex
```

Le choix reste explicite : installer MORPHEUS ne modifie aucun client tiers sans sélection de l’utilisateur.

Le gestionnaire installé est :

```text
<installation>\integration\configure-mcp-clients.ps1
```

Il applique les garanties suivantes :

- sauvegarde du JSON avant toute écriture ;
- conservation des autres serveurs MCP et propriétés ;
- refus d’écraser une entrée étrangère nommée `morpheus` ;
- exécution idempotente ;
- registre de propriété sous `%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json` ;
- journal sous `%LOCALAPPDATA%\MORPHEUS\mcp-clients.log` ;
- sauvegardes sous `%LOCALAPPDATA%\MORPHEUS\backups\mcp-clients` ;
- désinstallation limitée aux entrées toujours conformes à celles créées par MORPHEUS ;
- conservation d’une entrée modifiée manuellement après installation.

## 2. Configuration commune Windows

Installation per-user par défaut :

```text
command = %LOCALAPPDATA%\Programs\MORPHEUS\morpheus.exe
args    = mcp --stdio
```

Environnement :

```text
MORPHEUS_DATA_DIR   = %LOCALAPPDATA%\MORPHEUS\data
MORPHEUS_CONFIG_DIR = %LOCALAPPDATA%\MORPHEUS\config
```

Exemple conceptuel :

```json
{
  "command": "C:\\Users\\<user>\\AppData\\Local\\Programs\\MORPHEUS\\morpheus.exe",
  "args": ["mcp", "--stdio"],
  "env": {
    "MORPHEUS_DATA_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\data",
    "MORPHEUS_CONFIG_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\config"
  }
}
```

## 3. GitHub Copilot dans IntelliJ / JetBrains

Le gestionnaire fusionne `servers.morpheus` dans :

```text
%LOCALAPPDATA%\github-copilot\intellij\mcp.json
```

Forme créée :

```json
{
  "servers": {
    "morpheus": {
      "command": "C:\\Users\\<user>\\AppData\\Local\\Programs\\MORPHEUS\\morpheus.exe",
      "args": ["mcp", "--stdio"],
      "env": {
        "MORPHEUS_DATA_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\data",
        "MORPHEUS_CONFIG_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\config"
      }
    }
  }
}
```

Dans Copilot Chat, utiliser le mode **Agent**, ouvrir les outils MCP et vérifier que le serveur `morpheus` est disponible.

## 4. GitHub Copilot CLI

Enregistrement manuel équivalent :

```powershell
$MorpheusExe = "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe"
$DataRoot = "$env:LOCALAPPDATA\MORPHEUS\data"
$ConfigRoot = "$env:LOCALAPPDATA\MORPHEUS\config"

copilot mcp add morpheus `
  --env "MORPHEUS_DATA_DIR=$DataRoot" `
  --env "MORPHEUS_CONFIG_DIR=$ConfigRoot" `
  -- "$MorpheusExe" mcp --stdio
```

Contrôle :

```powershell
copilot mcp get morpheus --json
copilot mcp list --json
```

Suppression manuelle :

```powershell
copilot mcp remove morpheus
```

## 5. Claude Code

Claude Code exige que les options de `mcp add` précèdent le nom du serveur :

```powershell
$MorpheusExe = "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe"
$DataRoot = "$env:LOCALAPPDATA\MORPHEUS\data"
$ConfigRoot = "$env:LOCALAPPDATA\MORPHEUS\config"

claude mcp add `
  --scope user `
  --env "MORPHEUS_DATA_DIR=$DataRoot" `
  --env "MORPHEUS_CONFIG_DIR=$ConfigRoot" `
  morpheus `
  -- "$MorpheusExe" mcp --stdio
```

Contrôle :

```powershell
claude mcp list
claude mcp get morpheus
```

Dans Claude Code :

```text
/mcp
```

Suppression :

```powershell
claude mcp remove morpheus
```

## 6. Claude Desktop

Le fichier Windows est :

```text
%APPDATA%\Claude\claude_desktop_config.json
```

Le gestionnaire fusionne uniquement `mcpServers.morpheus` :

```json
{
  "mcpServers": {
    "morpheus": {
      "command": "C:\\Users\\<user>\\AppData\\Local\\Programs\\MORPHEUS\\morpheus.exe",
      "args": ["mcp", "--stdio"],
      "env": {
        "MORPHEUS_DATA_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\data",
        "MORPHEUS_CONFIG_DIR": "C:\\Users\\<user>\\AppData\\Local\\MORPHEUS\\config"
      }
    }
  }
}
```

Quitter complètement Claude Desktop puis le relancer après modification.

## 7. OpenAI Codex

```powershell
$MorpheusExe = "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe"
$DataRoot = "$env:LOCALAPPDATA\MORPHEUS\data"
$ConfigRoot = "$env:LOCALAPPDATA\MORPHEUS\config"

codex mcp add morpheus `
  --env "MORPHEUS_DATA_DIR=$DataRoot" `
  --env "MORPHEUS_CONFIG_DIR=$ConfigRoot" `
  -- "$MorpheusExe" mcp --stdio
```

Contrôle :

```powershell
codex mcp get morpheus
codex mcp list
```

Suppression :

```powershell
codex mcp remove morpheus
```

Codex CLI et l’intégration IDE utilisent la configuration MCP du même environnement Codex.

## 8. Distribution ZIP / configuration manuelle

Depuis la racine extraite :

```powershell
& .\integration\configure-mcp-clients.ps1 `
  -InstallRoot (Resolve-Path .) `
  -CopilotJetBrains `
  -CopilotCli `
  -ClaudeCode `
  -ClaudeDesktop `
  -Codex
```

Configurer uniquement certains clients en ne passant que les switches concernés.

Pour retirer les intégrations gérées :

```powershell
& .\integration\configure-mcp-clients.ps1 `
  -InstallRoot (Resolve-Path .) `
  -Action Uninstall
```

## 9. Linux

La configuration est manuelle. Exemple générique :

```json
{
  "command": "/opt/morpheus/bin/morpheus",
  "args": ["mcp", "--stdio"],
  "env": {
    "MORPHEUS_DATA_DIR": "/home/<user>/.local/share/morpheus",
    "MORPHEUS_CONFIG_DIR": "/home/<user>/.config/morpheus"
  }
}
```

Avec XDG :

```text
MORPHEUS_DATA_DIR=$XDG_DATA_HOME/morpheus
MORPHEUS_CONFIG_DIR=$XDG_CONFIG_HOME/morpheus
```

Sans XDG :

```text
MORPHEUS_DATA_DIR=~/.local/share/morpheus
MORPHEUS_CONFIG_DIR=~/.config/morpheus
```

## 10. Diagnostic

Tester le launcher :

```powershell
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --version
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" paths
```

Ne pas tester `mcp --stdio` comme une commande interactive ordinaire : le processus attend une négociation JSON-RPC MCP sur stdin et réserve stdout au protocole.

Consulter :

```text
%LOCALAPPDATA%\MORPHEUS\mcp-clients.log
%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json
```

Une entrée sélectionnée mais non créée peut indiquer :

- client CLI absent du `PATH` ;
- entrée `morpheus` étrangère déjà présente ;
- JSON invalide ;
- client modifié depuis une installation précédente ;
- commande cliente ayant dépassé le timeout borné.

## 11. Sécurité et writes

Le câblage d’un client ne lui accorde pas automatiquement une capacité d’écriture.

```text
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
```

Le tool `apply_change_lifecycle_transition` conserve les garde-fous applicatifs : capability explicite, confirmation, CAS, idempotency et audit. Sans provider `WRITE_CHANGE`, la mutation est refusée.
