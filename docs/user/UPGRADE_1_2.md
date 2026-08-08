# Mise à niveau vers MORPHEUS 1.2.0

Statut : **ACTIVE — MORPHEUS 1.2.0 PUBLIÉ**

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

La release 1.2.0 a été publiée le 30 juillet 2026 et ses huit assets ont été vérifiés byte-for-byte lors de R3.

## Avant la mise à niveau

1. Arrêter les processus MORPHEUS en cours.
2. Conserver une copie de la base et de la configuration si une sauvegarde supplémentaire est souhaitée.
3. Fermer les clients MCP ciblés avant leur configuration.
4. Vérifier le SHA-256 de l’asset téléchargé.

## Windows — installateur

Lancer :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
```

Installation programme :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

État persistant :

```text
%LOCALAPPDATA%\MORPHEUS
```

Les cinq intégrations MCP sont proposées comme options indépendantes et décochées par défaut.

## Windows — archive portable

Extraire :

```text
morpheus-1.2.0-windows-x64.zip
```

Le gestionnaire est disponible sous :

```text
integration\configure-mcp-clients.ps1
```

## Linux / WSL

```bash
tar -xzf morpheus-1.2.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Le launcher est autonome avec runtime Java embarqué. Les données/configurations conservent les racines XDG existantes.

## Configurer un client MCP

Serveur :

```text
morpheus mcp --stdio
```

Le processus de configuration est conservateur :

- sauvegarde avant écriture ;
- préservation des autres entrées ;
- refus d’écraser une entrée `morpheus` étrangère ;
- idempotence ;
- registre de propriété ;
- désinstallation state-driven.

Voir [MCP_CLIENTS.md](MCP_CLIENTS.md).

## Vérifications après mise à niveau

```powershell
morpheus --version
morpheus --json product-info
morpheus help
```

Version attendue :

```text
1.2.0
```

## Retour à 1.1.0

Le schéma SQLite étant inchangé à V015, les données restent structurellement compatibles. Avant un rollback programme : sauvegarder la base/configuration, retirer ou adapter les intégrations MCP pointant vers le binaire 1.2.0, réinstaller 1.1.0 puis vérifier les launchers enregistrés.

Un rollback programme n’est jamais un rollback logique de snapshot MORPHEUS.

## D2 post-release

Le travail D2 effectué après R3 ne modifie pas le tag stable `v1.2.0`. Il durcit la future baseline de développement et doit être qualifié localement Windows + Linux/WSL avant intégration dans `develop`.

## Références

- [`MCP_CLIENTS.md`](MCP_CLIENTS.md)
- [`INSTALLATION.md`](INSTALLATION.md)
- [`../release/RELEASE_NOTES_1.2.0.md`](../release/RELEASE_NOTES_1.2.0.md)
- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md)
