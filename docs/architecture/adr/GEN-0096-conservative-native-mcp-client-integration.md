---
id: GEN-0096
title: "Intégration conservatrice des clients MCP natifs"
domain: general
rules: false
---

# Intégration conservatrice des clients MCP natifs

- Statut : **Acceptée**
- Date : 2026-07-30

## Context

MORPHEUS expose un serveur MCP local sur STDIO :

```text
morpheus mcp --stdio
```

Les clients cibles sont GitHub Copilot JetBrains, GitHub Copilot CLI, Claude Code, Claude Desktop et OpenAI Codex.
Leur configuration automatique présente des risques d'écrasement, d'appropriation abusive d'une entrée existante ou
de suppression d'une modification utilisateur.

Alternatives rejetées :

- écrasement simple des fichiers JSON : détruit les données tierces ;
- suppression par nom : le nom ne prouve pas la propriété ;
- Docker comme point d'entrée local : contraire au natif d'abord ;
- configuration automatique sans opt-in : mutation silencieuse ;
- configuration universelle : formats clients incompatibles.

## Decision

MORPHEUS fournit une couche d'intégration **native, explicite, opt-in et conservatrice** :

```text
native MCP first
Docker not required
third-party config modification is opt-in
ownership is recorded, never guessed
uninstall is state-driven
manual changes are preserved
```

Gestionnaire : `integration/configure-mcp-clients.ps1` (actions `Install`, `Uninstall`, `Detect`). Wrapper du setup :
`integration/configure-mcp-clients-setup.ps1`.

### Définition commune

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

Tous les clients utilisent les mêmes racines persistantes (ADR-0088).

### Clients JSON

| Client | Conteneur |
|---|---|
| Copilot JetBrains | `servers.morpheus` |
| Claude Desktop | `mcpServers.morpheus` |

Avant écriture, le JSON est validé, sauvegardé, fusionné en conservant les propriétés et serveurs tiers, puis écrit en
UTF-8 sans BOM. Un JSON invalide est rejeté sans modification.

### Clients CLI

| Client | Commandes |
|---|---|
| Copilot CLI | `copilot mcp add/get/remove` |
| Claude Code | `claude mcp add/get/remove --scope user` |
| Codex | `codex mcp add/get/remove` |

Les commandes sont non interactives, bornées par un timeout, capturent stdout et stderr, et terminent l'arbre de
processus en cas de dépassement.

### Modèle de propriété

Registre persistant : `%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json`.

```text
managed      entrée créée par MORPHEUS
preexisting  entrée observée comme déjà compatible
```

Le nom `morpheus` ne prouve jamais la propriété.

### Upsert conservateur

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

### Désinstallation

La désinstallation parcourt uniquement le registre ; aucune recherche globale par nom ne remplace ce mécanisme.

```text
preexisting             remove state only
managed unchanged       remove client entry
managed modified        preserve client entry and state
already absent          clean stale state
CLI unavailable         preserve, warn
```

### Setup Windows

Le setup présente une page « Clients IA » avec une case par client, alimentée par une détection réelle
(`-Action Detect`, rapport INI) :

- un client disponible est proposé décoché ;
- une entrée déjà gérée par MORPHEUS est affichée cochée et non modifiable ;
- une entrée gérée à réparer est proposée cochée ;
- un client non détecté ou une entrée `morpheus` étrangère n'est jamais sélectionnable ;
- si la détection échoue, aucune case n'est sélectionnable.

Une installation sans interface sélectionne les clients par `/MORPHEUSMCPCLIENTS`, limité aux lignes que la détection
laisse sélectionnables. Le wrapper vérifie que chaque sélection apparaît dans le registre. La désinstallation appelle
le gestionnaire avant la suppression des fichiers du programme.

### Linux

La mutation des profils clients Linux n'est pas automatisée. L'archive TAR.GZ contient le guide et les scripts pour
la parité de distribution.

### Sécurité

Cette intégration n'ajoute aucun listener réseau, secret, token, capacité `WRITE`, dépendance Docker ni mutation
métier. Les contrôles applicatifs restent inchangés.

## Do's and Don'ts

### Do

- Ne modifier la configuration d'un client tiers que sur sélection explicite.
- Enregistrer la propriété de chaque entrée dans le registre persistant.
- Sauvegarder un fichier JSON client avant de l'écrire et fusionner en conservant les entrées tierces.
- Borner chaque commande CLI cliente par un timeout et terminer son arbre de processus au dépassement.
- Piloter la désinstallation par le registre seul.
- Donner à tous les clients les mêmes racines de données et de configuration.

### Don't

- Ne pas déduire la propriété du nom `morpheus`.
- Ne pas écraser une entrée étrangère, ni une entrée modifiée par l'utilisateur.
- Ne pas écrire un JSON client invalide ou non validé.
- Ne pas supprimer d'entrée par recherche globale sur le nom.
- Ne pas introduire Docker dans le gestionnaire ou dans l'installeur.
- Ne pas automatiser la mutation des profils clients Linux.

## Consequences

### Positives

- Réduction des erreurs manuelles.
- Conservation des configurations tierces.
- Rollback par sauvegarde, audit par registre.
- Cohérence des racines MORPHEUS entre clients.

### Négatives

- Maintenance du script PowerShell.
- Tests avec de faux clients.
- Adaptation aux commandes propres à chaque client.

### Risques

- Aucun risque n'est consigné au-delà des conséquences négatives.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java` :
  gestionnaire natif, opt-in et piloté par le registre, absence de `docker`, page de clients pilotée par la
  détection, désinstallation conservatrice, intégration présente dans les distributions portables, documentation et
  preuve du jalon présentes.
- `scripts/verify-m28-mcp-client-integration.ps1` et `scripts/verify-windows-setup-mcp-smoke.ps1` : vérification du
  gestionnaire et du setup avec de faux clients, lancées par `scripts/validate-m28.ps1` ; `scripts/validate-m28.sh`
  vérifie le contrat statique (présence du gestionnaire, du wrapper, des scripts et de la documentation).

## References

- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native d'abord.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution portable `jpackage`.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP Java officiel et STDIO natif.
- [ADR-0127](GEN-0127-un-launcher-natif-unique.md) — un launcher natif unique.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation programme /
  données persistantes.
- [Clients MCP](../../user/MCP_CLIENTS.md) — guide utilisateur.
- [VALIDATION_M28](../../project/validation/VALIDATION_M28.md) — preuve du jalon M28.
