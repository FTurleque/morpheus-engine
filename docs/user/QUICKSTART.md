# Démarrage rapide

## 1. Lancer la distribution portable

### Windows

Après extraction de l’archive `morpheus-<version>-windows-x64.zip` :

```powershell
.\morpheus\morpheus.exe help
```

### Linux

Après extraction de `morpheus-<version>-linux-x64.tar.gz` :

```bash
./morpheus/bin/morpheus help
```

La distribution portable embarque son runtime Java.

Pour les exemples ci-dessous, `morpheus` désigne le launcher correspondant à votre plateforme.

## 2. Enregistrer un projet

MORPHEUS découvre le provider compatible à partir du workspace. OpenSpec est le provider de référence initial.

```bash
morpheus projects add --workspace /path/to/project
```

La commande retourne un `projectId` MORPHEUS stable dans la base locale.

Lister les projets :

```bash
morpheus projects list
```

## 3. Synchroniser

```bash
morpheus sync --project <projectId>
```

Le launcher officiel utilise une reconstruction complète conservatrice lors de la synchronisation publiée. Un snapshot candidat ne remplace l’ACTIVE qu’après validation réussie.

Révision source optionnelle :

```bash
morpheus sync --project <projectId> --revision <revision>
```

Vérifier la fraîcheur :

```bash
morpheus sync-status --project <projectId>
```

## 4. Interroger la spécification

```bash
morpheus requirements find --project <projectId> --query "session"
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>
morpheus constraints list --project <projectId> --change <changeId>
morpheus decisions list --project <projectId> --change <changeId>
morpheus tasks list --project <projectId> --change <changeId>
```

Pour un usage scriptable, ajouter `--json` :

```bash
morpheus --json requirements find --project <projectId> --query "session"
```

## 5. Traçabilité, contexte et qualité

```bash
morpheus trace-requirement --project <projectId> --requirement <requirementId> --depth 2
morpheus change-context --project <projectId> --change <changeId> --depth 2
morpheus analyze-change --project <projectId> --change <changeId> --depth 2
morpheus quality --project <projectId>
```

## 6. Démarrer l’API HTTP

```bash
morpheus api
```

Defaults :

```text
host = 127.0.0.1
port = 8765
base = /api/v1
```

Ou explicitement :

```bash
morpheus api --host 127.0.0.1 --port 8765
```

## 7. Démarrer le serveur MCP

```bash
morpheus mcp --stdio
```

En mode MCP, `stdout` est réservé au protocole JSON-RPC. Les diagnostics sont envoyés sur `stderr`.

## 8. Activer les moteurs optionnels

Sans configuration supplémentaire, MORPHEUS reste entièrement utilisable et expose MINOS/NEXUS comme `DISABLED`.

Consulter : [Intégrations optionnelles](INTEGRATIONS.md).

## 9. Vérifier l’installation

```bash
morpheus --version
morpheus --json version
```

Référence complète : [CLI](CLI.md).
