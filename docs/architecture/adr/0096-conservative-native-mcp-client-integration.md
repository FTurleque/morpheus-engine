# ADR-0096 — Intégration conservatrice des clients MCP natifs

- Statut : **Acceptée — M28**
- Date : 30 juillet 2026
- Dépend de : ADR-0027, ADR-0061, ADR-0062, ADR-0064, ADR-0088
- Portée : M28 — configuration clients MCP, setup Windows, distributions portables
- Qualification : Windows + Linux/WSL PASS sur `58adfeb13b79808da12830f2d0b0b24ec46f67e6`

## 1. Contexte

MORPHEUS expose un serveur MCP local sur STDIO :

```text
morpheus mcp --stdio
```

Les clients cibles sont GitHub Copilot JetBrains, GitHub Copilot CLI, Claude Code, Claude Desktop et OpenAI Codex. Leur configuration automatique présente des risques d’écrasement, d’appropriation abusive d’une entrée existante ou de suppression d’une modification utilisateur.

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

Gestionnaire :

```text
integration/configure-mcp-clients.ps1
```

Wrapper setup :

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

Tous les clients utilisent les mêmes racines persistantes.

## 4. Clients JSON

```text
Copilot JetBrains  servers.morpheus
Claude Desktop     mcpServers.morpheus
```

Avant écriture, le JSON est validé, sauvegardé, fusionné en conservant les propriétés et serveurs tiers, puis écrit en UTF-8 sans BOM. Un JSON invalide est rejeté sans modification.

## 5. Clients CLI

```text
Copilot CLI  copilot mcp add/get/remove
Claude Code  claude mcp add/get/remove --scope user
Codex        codex mcp add/get/remove
```

Les commandes sont non interactives, bornées par timeout, capturent stdout/stderr et terminent l’arbre de processus en cas de dépassement.

## 6. Modèle de propriété

Registre persistant :

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

Le setup expose cinq tâches indépendantes et décochées par défaut. Le wrapper vérifie que chaque sélection apparaît dans le registre. L’uninstall appelle le gestionnaire avant suppression des fichiers de programme.

## 10. Linux

M28 n’automatise pas la mutation des profils Linux. Le TAR.GZ contient le guide et les scripts pour parité de distribution.

```text
Windows client profile mutation  qualified on Windows
Linux packaging/static contract  qualified on Linux
```

## 11. Sécurité

M28 n’ajoute aucun listener réseau, secret, token, capability WRITE, dépendance Docker ni mutation métier. Les contrôles applicatifs existants restent inchangés.

## 12. Alternatives rejetées

- écrasement simple des fichiers JSON : détruit les données tierces ;
- suppression par nom : le nom ne prouve pas la propriété ;
- Docker comme point d’entrée local : contraire à native-first ;
- configuration automatique sans opt-in : mutation silencieuse ;
- configuration universelle : formats clients incompatibles.

## 13. Conséquences

Avantages : réduction des erreurs manuelles, conservation des configurations tierces, rollback, audit et cohérence des racines MORPHEUS.

Coûts : maintenance du script PowerShell, tests avec faux clients et adaptation aux commandes propres à chaque client.

## 14. Validation observée

Windows :

```text
five clients                 PASS
JSON merge                   PASS
CLI registration             PASS
idempotency                  PASS
backups                      PASS
foreign entry preservation   PASS
modified entry preservation  PASS
state-driven uninstall       PASS
invalid JSON protection      PASS
portable + setup packaging   PASS
```

Linux/WSL :

```text
reactor non-regression       PASS
static contracts             PASS
portable packaging           PASS
same exact executable SHA    PASS
```

Les deux plateformes ont qualifié `58adfeb13b79808da12830f2d0b0b24ec46f67e6`. La décision est donc acceptée.