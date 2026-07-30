# MORPHEUS 1.2.0 — Notes de version

Statut : **CANDIDATE — NON PUBLIÉE**

Date de préparation : 30 juillet 2026

MORPHEUS 1.2.0 transforme le serveur MCP STDIO natif déjà livré en une intégration directement exploitable depuis les principaux clients d’agents. Cette release consolide M28 au-dessus de MORPHEUS 1.1.0 sans migration SQLite supplémentaire et sans modifier les contrats métier publiés.

La publication reste bloquée jusqu’à la qualification exacte Windows/Linux, au merge dans `main`, au tag immuable `v1.2.0`, aux builds exact-tag et à la vérification des huit assets GitHub Release.

## Point fort — intégration native des clients MCP

MORPHEUS expose toujours son serveur local avec :

```text
morpheus.exe mcp --stdio
```

ou, sous Linux :

```text
morpheus mcp --stdio
```

La version 1.2.0 ajoute un gestionnaire conservateur pour cinq clients :

- GitHub Copilot dans JetBrains / IntelliJ ;
- GitHub Copilot CLI ;
- Claude Code ;
- Claude Desktop ;
- OpenAI Codex.

## Garanties de configuration

Le gestionnaire `integration/configure-mcp-clients.ps1` fournit :

- une activation explicitement opt-in ;
- une sauvegarde avant toute écriture ;
- une fusion JSON conservatrice ;
- une écriture UTF-8 sans BOM ;
- la préservation des autres serveurs MCP ;
- le refus d’écraser une entrée étrangère nommée `morpheus` ;
- un ownership explicite `managed` / `preexisting` ;
- une installation idempotente ;
- des délais d’exécution bornés pour les clients CLI ;
- la conservation d’une entrée modifiée manuellement ;
- une désinstallation exclusivement pilotée par le registre de propriété.

## Installateur Windows

Le setup Windows propose cinq tâches indépendantes :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Toutes les cases restent décochées par défaut. L’installation de MORPHEUS ne modifie donc aucun profil client sans consentement explicite.

La désinstallation retire uniquement les entrées encore reconnues comme appartenant à MORPHEUS. Une configuration modifiée par l’utilisateur est préservée.

## Archives portables

Les distributions Windows ZIP et Linux TAR.GZ embarquent :

```text
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
integration/README.md
```

Le même gestionnaire peut ainsi être utilisé avec l’installateur Windows ou avec une archive portable.

## Configuration runtime

Les configurations générées utilisent le launcher natif et peuvent définir :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
```

Le knowledge store et la configuration restent séparés de l’installation du programme.

## Docker

Docker n’est ni requis ni utilisé pour l’intégration MCP 1.2.0. Le client lance directement le binaire MORPHEUS via le transport STDIO local.

## Compatibilité et données

- mise à niveau supportée depuis MORPHEUS 1.1.0 ;
- schéma SQLite inchangé à V015 ;
- aucune migration de données R3 ;
- identités, snapshots, Policy Packs, backups et états publiés conservés ;
- mode local, API HTTP et serveur remote optionnel inchangés ;
- contrats CLI/MCP/HTTP métier inchangés hors ajout du câblage client.

## Sécurité et contrôle

- aucune auto-configuration silencieuse ;
- aucune suppression globale de profil ;
- aucune lecture de secrets métier ;
- aucune entrée étrangère écrasée ;
- backups traçables avant mutation ;
- logs dédiés au gestionnaire d’intégration ;
- désinstallation fail-safe.

## Assets attendus

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

## Limites intentionnelles

- aucune configuration client n’est appliquée sans sélection explicite ;
- les formats de profils sont manipulés uniquement selon les contrats documentés ;
- une entrée étrangère ou modifiée est signalée et conservée ;
- Linux embarque le gestionnaire et la documentation, mais ne possède pas d’installateur graphique ;
- le serveur MCP reste local STDIO ; aucun transport réseau MCP n’est introduit par R3.

## Qualification requise

```text
Windows exact-head       REQUIRED
Linux/WSL exact-head     REQUIRED
same executable SHA      REQUIRED
17 POMs = 1.2.0          REQUIRED
M28 client manager       REQUIRED
5 clients                REQUIRED
portable Windows/Linux   REQUIRED
installer Windows        REQUIRED
SBOM + provenance        REQUIRED
post-gate delta          NONE
exact-tag builds         REQUIRED
published parity         8/8 REQUIRED
```

Preuve de release : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md).
Plan : [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md).
Guide d’upgrade : [`../user/UPGRADE_1_2.md`](../user/UPGRADE_1_2.md).
