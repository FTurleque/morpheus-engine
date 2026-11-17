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

Principes que l'intégration tient :

```text
native MCP first
Docker not required
third-party config modification is opt-in
ownership is recorded, never guessed
uninstall is state-driven
manual changes are preserved
```

La définition écrite pour chaque client :

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

Les clients JSON et le conteneur de l'entrée :

| Client | Conteneur |
|---|---|
| Copilot JetBrains | `servers.morpheus` |
| Claude Desktop | `mcpServers.morpheus` |

Les clients CLI et leurs commandes :

| Client | Commandes |
|---|---|
| Copilot CLI | `copilot mcp add/get/remove` |
| Claude Code | `claude mcp add --scope user`, `claude mcp get/remove` |
| Codex | `codex mcp add/get/remove` |

## Decision

Intégration **native, explicite, opt-in et conservatrice**, sans Docker, par `integration/configure-mcp-clients.ps1`
(`Install`, `Uninstall`, `Detect`) et le wrapper `integration/configure-mcp-clients-setup.ps1`.

1. **Définition** : `morpheus.exe mcp --stdio`, `MORPHEUS_DATA_DIR` et `MORPHEUS_CONFIG_DIR` sur les racines
   d'ADR-0088, pour tous les clients.
2. **Écriture** : JSON client validé, sauvegardé, fusionné en gardant le tiers, écrit en UTF-8 sans BOM, refusé
   invalide ; commande CLI non interactive, sous timeout, stdout et stderr capturés, arbre tué au dépassement.
3. **Propriété** dans `%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json` : `managed` (créée par MORPHEUS) ou
   `preexisting` (déjà compatible). Le nom `morpheus` ne la prouve jamais.
4. **Upsert** : absente, créée `managed` ; compatible sans état, `preexisting` ; compatible et `managed`, gardée ;
   incompatible sans état ou modifiée, préservée ; CLI optionnelle absente, avertissement (échec en strict).
5. **Désinstallation** par le registre seul, jamais par le nom : `preexisting`, état retiré ; `managed` intacte,
   entrée retirée ; `managed` modifiée, tout gardé ; déjà absente, état nettoyé ; CLI indisponible, gardée et
   signalée.
6. **Setup Windows** : page « Clients IA » issue de `-Action Detect` (rapport INI) ; client disponible décoché,
   entrée gérée cochée (figée, sauf à réparer) ; rien de sélectionnable pour un client absent, une entrée `morpheus`
   étrangère ou une détection en échec. `/MORPHEUSMCPCLIENTS` sélectionne sans interface dans ces limites ; le
   wrapper retrouve chaque sélection au registre ; le désinstalleur appelle d'abord le gestionnaire.
7. **Linux** : profils jamais modifiés automatiquement ; l'archive TAR.GZ porte guide et scripts.
8. **Sécurité** : ni listener réseau, ni secret, ni token, ni `WRITE`, ni mutation métier, ni contrôle modifié.

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

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java` :
  gestionnaire natif, opt-in et piloté par le registre, absence de `docker`, page de clients pilotée par la
  détection, désinstallation conservatrice, intégration présente dans les distributions portables, documentation et
  preuve du jalon présentes.
- `scripts/verify-m28-mcp-client-integration.ps1` et `scripts/verify-windows-setup-mcp-smoke.ps1` : vérification du
  gestionnaire et du setup avec de faux clients, lancées par `scripts/validate-m28.ps1` ; `scripts/validate-m28.sh`
  vérifie le contrat statique (présence du gestionnaire, du wrapper, des scripts et de la documentation).

## References

- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native d'abord, distribution portable
  `jpackage`.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP Java officiel et STDIO natif.
- [ADR-0127](GEN-0127-un-launcher-natif-unique.md) — un launcher natif unique.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation programme /
  données persistantes.
- [Clients MCP](../../user/MCP_CLIENTS.md) — guide utilisateur.
- [VALIDATION_M28](../../project/validation/VALIDATION_M28.md) — preuve du jalon M28.
