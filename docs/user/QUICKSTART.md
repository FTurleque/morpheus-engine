# Démarrage rapide MORPHEUS 1.0

Ce guide conduit un utilisateur depuis l’installation jusqu’à une première interrogation de spécification, puis présente les principales surfaces de composition, lifecycle, HTTP et MCP.

## 1. Installer ou extraire MORPHEUS

La distribution embarque son runtime Java : aucun JDK, Maven ou Git n’est requis chez l’utilisateur final.

### Windows — recommandé

Installer :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
```

Le setup est per-user et installe par défaut dans :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

L’option d’ajout au `PATH` utilisateur est explicite et décochée par défaut.

Sans PATH :

```powershell
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --version
```

Avec PATH :

```powershell
morpheus --version
morpheus help
```

### Windows — portable

Après extraction de `morpheus-1.0.0-windows-x64.zip` :

```powershell
.\morpheus\morpheus.exe --version
.\morpheus\morpheus.exe help
```

### Linux x64

```bash
sha256sum -c morpheus-1.0.0-linux-x64.tar.gz.sha256
tar -xzf morpheus-1.0.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
./morpheus/bin/morpheus help
```

Dans la suite, `morpheus` désigne le launcher de la plateforme.

Guide installation/upgrade/uninstall complet : [`INSTALLATION.md`](INSTALLATION.md).

## 2. Vérifier les chemins

```bash
morpheus paths
```

Sous Windows, le state PROD par défaut est séparé du programme :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Base explicite pour un test :

```powershell
morpheus --db "$env:TEMP\demo-morpheus.db" paths
```

Toutes les commandes d’un même scénario doivent utiliser la même base si `--db` est précisé.

## 3. Préparer un workspace compatible

Deux providers réels sont supportés dans la composition M18+ :

```text
OpenSpec
Structured Markdown
```

Le workspace doit contenir une structure reconnue par au moins un provider installé. L’enregistrement du projet ne publie encore aucun contenu.

```mermaid
flowchart LR
    W[Workspace] --> D[Découverte provider]
    D --> R[Projet enregistré]
    R --> S[Synchronisation]
    S --> A[Snapshot ACTIVE]
    A --> Q[Requêtes]
```

## 4. Enregistrer le projet

```bash
morpheus projects add --workspace /path/to/project
morpheus projects list
```

Conserver le `projectId` retourné.

## 5. Synchroniser et publier

```bash
morpheus sync --project <projectId>
morpheus sync-status --project <projectId>
```

Révision source optionnelle :

```bash
morpheus sync --project <projectId> --revision <revision>
```

Un snapshot candidat ne remplace l’`ACTIVE` qu’après validation réussie. Si le candidat échoue, l’ancien `ACTIVE` reste publié.

## 6. Composer plusieurs providers

```bash
morpheus composition sync --project <projectId>
morpheus --json composition status --project <projectId>
morpheus --json composition conflicts --project <projectId>
```

La composition conserve ownership, identité provider-scoped, precedence, provenance, candidats non sélectionnés et conflits explicites.

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
```

## 7. Faire les premières requêtes

Requirements :

```bash
morpheus requirements find --project <projectId> --query "session"
```

Changements :

```bash
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>
```

Artefacts :

```bash
morpheus constraints list --project <projectId> --change <changeId>
morpheus acceptance-criteria list --project <projectId> --change <changeId>
morpheus decisions list --project <projectId> --change <changeId>
morpheus tasks list --project <projectId> --change <changeId>
```

## 8. Traçabilité, analyse et qualité

```bash
morpheus trace-requirement --project <projectId> --requirement <requirementId> --depth 2
morpheus change-context --project <projectId> --change <changeId> --depth 2
morpheus analyze-change --project <projectId> --change <changeId> --depth 2
morpheus quality --project <projectId>
```

L’analyse ne promeut ni n’active le contenu proposé.

## 9. Évaluer puis appliquer explicitement un lifecycle

Évaluation read-only :

```bash
morpheus --json change-orchestration transition-check \
  --project <projectId> \
  --change <changeId> \
  --from DRAFT \
  --to PROPOSED
```

Application contrôlée :

```bash
morpheus --json lifecycle apply \
  --project <projectId> \
  --change <changeId> \
  --expected-revision 0 \
  --to PROPOSED \
  --idempotency-key demo-change-1 \
  --actor user \
  --confirm
```

```text
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
```

## 10. Mode JSON

```bash
morpheus --json requirements find --project <projectId> --query "session"
```

Pour automatiser : lire le code de sortie, parser le JSON de `stdout`, utiliser `stderr` pour le diagnostic humain.

| Code | Sens |
|---:|---|
| 0 | succès |
| 2 | usage/argument invalide |
| 3 | ressource absente |
| 4 | état incompatible |
| 5 | erreur I/O classifiée |
| 10 | erreur interne inattendue |

## 11. Démarrer l’API HTTP

```bash
morpheus api --host 127.0.0.1 --port 8765
```

Tests :

```bash
curl http://127.0.0.1:8765/api/v1/health
curl http://127.0.0.1:8765/api/v1/readiness
curl http://127.0.0.1:8765/api/v1/metrics
curl http://127.0.0.1:8765/api/v1/version
```

## 12. Vérifier les intégrations optionnelles

Sans configuration externe :

```bash
morpheus --json minos-status
morpheus --json nexus-status
```

Les deux doivent rester `DISABLED`. Les adapters sont présents, mais MINOS et NEXUS ne sont ni embarqués ni requis par MORPHEUS.
