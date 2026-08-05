# MORPHEUS 1.2.0 — Notes de version

Statut : **PUBLISHED / STABLE / LATEST**

Publication : 30 juillet 2026

MORPHEUS 1.2.0 consolide M28 au-dessus de 1.1.0 et rend le serveur MCP STDIO natif directement configurable dans les principaux clients d’agents, sans Docker obligatoire et sans migration SQLite supplémentaire.

## Publication vérifiée

```text
Tag                       v1.2.0
Main release commit       3ad9ebf030b58df97482e21e272c24feae6b9d86
Qualified executable SHA  d08542026817f0d743766656a0197790c6809eca
PR                        #118 MERGED
Issue                     #117 CLOSED / completed
Windows exact-head        PASS
Linux/WSL exact-head      PASS
Exact-tag builds          PASS Windows + Linux
Published assets          8/8
Published parity          8/8 PASS
```

Preuve : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md).

## Point fort — intégration native des clients MCP

Serveur :

```text
morpheus mcp --stdio
```

Clients :

- GitHub Copilot dans JetBrains / IntelliJ ;
- GitHub Copilot CLI ;
- Claude Code ;
- Claude Desktop ;
- OpenAI Codex.

Le gestionnaire `integration/configure-mcp-clients.ps1` fournit :

- activation explicitement opt-in ;
- sauvegarde avant toute écriture ;
- fusion JSON conservatrice ;
- UTF-8 sans BOM ;
- préservation des autres serveurs MCP ;
- refus d’écraser une entrée étrangère `morpheus` ;
- ownership explicite ;
- idempotence ;
- timeouts bornés pour les clients CLI ;
- conservation des modifications manuelles ;
- désinstallation state-driven.

## Installateur Windows

Le setup propose cinq tâches MCP indépendantes, toutes décochées par défaut. L’installation de MORPHEUS ne modifie donc aucun profil tiers sans consentement explicite.

La désinstallation retire uniquement les entrées toujours reconnues comme gérées par MORPHEUS. Une entrée préexistante, étrangère ou modifiée est conservée.

## Archives portables

Les distributions Windows ZIP et Linux TAR.GZ embarquent le runtime Java et la documentation/couche d’intégration MCP.

Docker n’est pas requis pour le MCP local.

## Compatibilité et données

```text
upgrade supported          1.1.0 -> 1.2.0
SQLite schema              V015 -> V015
business migration         NONE
identity preservation      PASS inherited gates
published facts            preserved
MCP client wiring          opt-in external configuration
```

## Assets publiés

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
MORPHEUS-1.2.0-windows-x64-setup.exe.sha256
morpheus-1.2.0-windows-x64.zip
morpheus-1.2.0-windows-x64.zip.sha256
morpheus-1.2.0-windows-x64-release-manifest.json
morpheus-1.2.0-linux-x64.tar.gz
morpheus-1.2.0-linux-x64.tar.gz.sha256
morpheus-1.2.0-linux-x64-release-manifest.json
```

Les huit assets ont été retéléchargés et comparés aux builds exact-tag par SHA-256 lors de R3.

## Après R3

D2 — Post-R3 Repository Hardening est une consolidation de développement post-release. D2 ne déplace pas `v1.2.0` et ne prétend pas republier les assets R3.

D2 met notamment à jour Jackson et sqlite-jdbc, remonte les floors qualité, ajoute un SCA local et réconcilie la documentation active. Voir [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md).

## Références

- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md)
- [`../user/UPGRADE_1_2.md`](../user/UPGRADE_1_2.md)
- [`../user/MCP_CLIENTS.md`](../user/MCP_CLIENTS.md)
