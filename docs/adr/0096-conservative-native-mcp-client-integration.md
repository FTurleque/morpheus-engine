# ADR-0096 — Intégration conservatrice des clients MCP natifs

- Statut : **Proposée — M28, acceptation conditionnée aux gates Windows + Linux/WSL**
- Date : 30 juillet 2026
- Dépend de : ADR-0027, ADR-0061, ADR-0062, ADR-0064, ADR-0088
- Portée : M28 — configuration clients MCP, setup Windows, distributions portables

## 1. Contexte

MORPHEUS expose déjà un serveur MCP local sur STDIO :

```text
morpheus mcp --stdio
```

Le serveur est utilisable par tout client compatible, mais une configuration manuelle exige de connaître le launcher, les arguments, les répertoires persistants et le format propre à chaque client.

Les clients cibles sont :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Modifier automatiquement leurs profils présente des risques : écrasement d’autres serveurs, appropriation abusive d’une entrée existante, suppression d’une modification utilisateur ou blocage du setup lorsqu’un client optionnel est absent.

## 2. Décision

MORPHEUS fournit une couche d’intégration **native, explicite, opt-in et conservatrice**.

```text
native MCP first
Docker not required
third-party config modification is opt-in
ownership is recorded, never guessed
uninstall is state-driven
manual changes are preserved
```

Le gestionnaire Windows distribué est :

```text
integration/configure-mcp-clients.ps1
```

Le wrapper de setup est :

```text
integration/configure-mcp-clients-setup.ps1
```

## 3. Définition commune

```json
{
  "command": "<install-root>\\morpheus.exe",
  "args": ["mcp", "--stdio"],
  "env": {
    "MORPHEUS_DATA_DIR": "<persistent-data-root>",
    "MORPHEUS_CONFIG_DIR": "<persistent-config-root>"
  }
}
```

Tous les clients doivent utiliser les mêmes racines persistantes.

## 4. Clients JSON

```text
Copilot JetBrains  servers.morpheus
Claude Desktop     mcpServers.morpheus
```

Avant écriture :

1. le JSON est lu et validé ;
2. une sauvegarde est créée si le fichier existe ;
3. les autres propriétés et serveurs sont conservés ;
4. la sortie est écrite en UTF-8 sans BOM ;
5. un JSON invalide est rejeté sans modification.

## 5. Clients CLI

```text
Copilot CLI  copilot mcp add/get/remove
Claude Code  claude mcp add/get/remove --scope user
Codex        codex mcp add/get/remove
```

Les commandes sont exécutées :

```text
non-interactively
with bounded timeout
with stdout/stderr capture
with process-tree termination on timeout
```

Un launcher `.ps1` est routé par `pwsh`, jamais exécuté implicitement dans Windows PowerShell 5.1.

## 6. Modèle de propriété

Le registre persistant est :

```text
%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json
```

```text
managed      entrée créée par MORPHEUS
preexisting  entrée observée comme déjà compatible
```

Le nom `morpheus` ne prouve jamais la propriété.

## 7. Upsert conservateur

```text
absent                       create managed
compatible + no state        record preexisting
compatible + managed state   idempotent keep
incompatible + no state      preserve foreign entry
preexisting changed          preserve
managed changed              preserve
invalid JSON                 fail before write
missing optional CLI         warn or fail in strict mode
```

## 8. Désinstallation

La désinstallation parcourt uniquement le registre.

```text
preexisting             remove state only
managed unchanged       remove client entry
managed modified        preserve client entry and state
already absent          clean stale state
CLI unavailable         preserve, warn
```

Aucune recherche globale par nom ne remplace ce mécanisme.

## 9. Setup Windows

Le setup expose cinq tâches indépendantes et décochées par défaut.

Le wrapper vérifie après exécution que chaque sélection apparaît dans le registre. Un échec de câblage est signalé explicitement ; le binaire MORPHEUS reste utilisable directement.

L’uninstall appelle le gestionnaire avant la suppression des fichiers de programme.

## 10. Linux

M28 n’automatise pas la mutation des profils Linux. Le TAR.GZ contient le guide et les scripts pour parité de distribution, mais la configuration est documentée manuellement.

Cette asymétrie est explicite :

```text
Windows client profile mutation  qualified on Windows
Linux packaging/static contract  qualified on Linux
```

## 11. Sécurité

M28 n’ajoute :

```text
aucun listener réseau
aucun secret
aucun token
aucune capability WRITE
aucune dépendance Docker
aucune mutation métier
```

Le tool lifecycle write conserve ses contrôles applicatifs existants.

## 12. Alternatives rejetées

### Écrasement simple des fichiers JSON

Rejeté : détruit les serveurs/propriétés tierces.

### Suppression par nom à l’uninstall

Rejeté : le nom ne prouve pas la propriété.

### Docker comme point d’entrée MCP local

Rejeté : contraire à la stratégie native-first et inutile pour STDIO local.

### Configuration automatique sans opt-in

Rejeté : mutation silencieuse de produits tiers.

### Une configuration universelle pour tous les clients

Rejeté : les formats et commandes restent client-specific.

## 13. Conséquences

Avantages :

- expérience d’installation cohérente avec MINOS ;
- réduction des erreurs manuelles ;
- conservation des configurations tierces ;
- rollback et audit opérationnels ;
- même base MORPHEUS vue par tous les clients.

Coûts :

- script PowerShell significatif ;
- tests avec faux clients ;
- maintenance des commandes propres aux clients ;
- automatisation Linux différée.

## 14. Validation requise

Windows doit prouver :

```text
five clients
JSON merge
CLI registration
idempotency
backups
foreign entry preservation
modified entry preservation
state-driven uninstall
invalid JSON protection
portable + setup packaging
```

Linux/WSL doit prouver :

```text
reactor non-regression
static contracts
portable packaging
same exact SHA
```

L’ADR ne passe à **Acceptée — M28** qu’après inscription des deux preuves dans `VALIDATION_M28.md`.
