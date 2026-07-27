# Installer MORPHEUS 1.0

MORPHEUS 1.0 est distribué avec son propre runtime Java. **L’utilisateur final n’a besoin ni de Git, ni de Maven, ni d’un JDK.**

## Windows — installation recommandée

Artefacts :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
MORPHEUS-1.0.0-windows-x64-setup.exe.sha256
```

Le setup est **per-user** et ne demande pas d’élévation administrative.

Installation par défaut :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

Le setup propose une option explicite pour ajouter MORPHEUS au `PATH` utilisateur. Cette option est décochée par défaut.

Après installation :

```powershell
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --version
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" paths
```

Avec l’option PATH activée :

```powershell
morpheus --version
morpheus paths
```

## Données persistantes Windows

Les fichiers programme et les données sont séparés.

```text
Programme
%LOCALAPPDATA%\Programs\MORPHEUS

État persistant
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

La base SQLite par défaut est :

```text
%LOCALAPPDATA%\MORPHEUS\data\morpheus.db
```

Cette séparation est un contrat produit : **mettre à jour ou désinstaller MORPHEUS ne supprime pas le knowledge store par défaut**.

## Mise à jour Windows

Lancer le setup d’une version plus récente avec le même utilisateur Windows.

Le setup conserve le même AppId et remplace les fichiers programme dans le répertoire d’installation existant. Il ne supprime ni :

```text
data
config
logs
backups
```

Après mise à jour :

```powershell
morpheus --version
morpheus paths
```

La migration du schéma SQLite reste prise en charge par MORPHEUS au démarrage selon les migrations versionnées du store.

## Désinstallation Windows

Utiliser **Applications installées** / **Applications et fonctionnalités**, ou le désinstalleur présent dans le répertoire programme.

L’uninstall retire :

```text
les fichiers programme
le raccourci installé
l’entrée PATH utilisateur si MORPHEUS l’avait ajoutée
```

Il conserve par défaut :

```text
%LOCALAPPDATA%\MORPHEUS
```

Pour supprimer définitivement les données, l’utilisateur doit le faire séparément et explicitement après avoir vérifié qu’aucun historique n’est à conserver.

## Windows — archive portable

Artefacts :

```text
morpheus-1.0.0-windows-x64.zip
morpheus-1.0.0-windows-x64.zip.sha256
```

Extraire le ZIP puis lancer :

```powershell
.\morpheus\morpheus.exe --version
```

L’archive contient également son runtime Java. Le mode portable n’implique pas que les données doivent être stockées dans le dossier extrait : sans override, les chemins PROD utilisateur restent utilisés.

Pour isoler explicitement les données :

```powershell
.\morpheus\morpheus.exe --data-dir 'D:\morpheus-data' paths
```

## Linux x64

Artefacts :

```text
morpheus-1.0.0-linux-x64.tar.gz
morpheus-1.0.0-linux-x64.tar.gz.sha256
```

Vérifier puis extraire :

```bash
sha256sum -c morpheus-1.0.0-linux-x64.tar.gz.sha256
tar -xzf morpheus-1.0.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Le runtime Java est embarqué.

Layout Linux par défaut :

```text
${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

## Overrides de chemins

CLI :

```text
--data-dir PATH
--config-dir PATH
--db PATH
```

Environnement :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_LOGS_DIR
MORPHEUS_BACKUPS_DIR
MORPHEUS_DB
```

Sous Linux, `XDG_DATA_HOME`, `XDG_CONFIG_HOME` et `XDG_STATE_HOME` sont également respectés lorsque les overrides MORPHEUS correspondants ne sont pas fournis.

## Intégrations MINOS / NEXUS

MINOS et NEXUS ne sont pas embarqués dans MORPHEUS. Les adapters clients sont présents, mais les moteurs restent **optionnels**.

Sans configuration :

```powershell
morpheus --json minos-status
morpheus --json nexus-status
```

retournent l’état `DISABLED`.

Le retrait de leur configuration revient à un MORPHEUS autonome ; aucune désinstallation du programme n’est nécessaire.

Voir aussi [`INTEGRATIONS.md`](INTEGRATIONS.md).

## Vérification SHA-256

Windows :

```powershell
Get-FileHash .\MORPHEUS-1.0.0-windows-x64-setup.exe -Algorithm SHA256
Get-Content .\MORPHEUS-1.0.0-windows-x64-setup.exe.sha256
```

Linux :

```bash
sha256sum -c morpheus-1.0.0-linux-x64.tar.gz.sha256
```

Les fichiers `.sha256` publiés font partie du contrat de release M20.
