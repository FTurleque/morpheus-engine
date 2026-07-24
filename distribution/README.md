# MORPHEUS — Distribution locale M9

M9 retient une stratégie **native-first** avec une archive portable autonome comme artefact principal.

## Artefact principal

Les scripts `build-portable.ps1` et `build-portable.sh` :

1. construisent le JAR CLI autonome `morpheus-cli-*-all.jar` ;
2. créent une application `jpackage --type app-image` ;
3. embarquent un runtime Java généré par `jpackage`/`jlink` ;
4. exécutent `morpheus --version` sur le launcher packagé ;
5. produisent une archive portable.

Cibles :

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

L'utilisateur final de l'archive standard n'a pas besoin d'installer/configurer manuellement un JDK.

## Windows

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\distribution\build-portable.ps1
```

Après extraction :

```powershell
.\morpheus\morpheus.exe --version
.\morpheus\morpheus.exe help
```

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

La génération EXE/MSI par `jpackage` dépend des outils natifs Windows requis par le JDK de packaging (WiX pour JDK 21). L'installateur n'est donc pas la seule voie de distribution M9 : l'archive portable reste officielle et ne dépend pas de WiX chez l'utilisateur final.

## Linux

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
distribution/build-portable.sh
```

Après extraction :

```bash
./morpheus/bin/morpheus --version
./morpheus/bin/morpheus help
```

La distribution Linux M9 officielle est l'archive `tar.gz` autonome. Les paquets `deb`/`rpm` restent optionnels car ils dépendent des outils natifs de la distribution de build et n'apportent pas de sémantique MORPHEUS supplémentaire.

## Layout runtime

La CLI sépare l'installation de l'état utilisateur.

Priorité de configuration :

```text
CLI option > MORPHEUS_* environment > OS default
```

Options globales :

```text
--data-dir PATH
--config-dir PATH
--db PATH
```

Variables :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Defaults Windows :

```text
data   = %LOCALAPPDATA%\Morpheus
config = %APPDATA%\Morpheus
db     = <data>\morpheus.db
logs   = <data>\logs
```

Defaults Linux :

```text
data   = $XDG_DATA_HOME/morpheus ou ~/.local/share/morpheus
config = $XDG_CONFIG_HOME/morpheus ou ~/.config/morpheus
db     = <data>/morpheus.db
logs   = <data>/logs
```

Avec `--data-dir`, la config par défaut devient `<data>/config`, ce qui permet un mode entièrement portable.

## Upgrade / uninstall

L'état MORPHEUS (SQLite/config) est par défaut **hors du répertoire d'installation**. Une mise à jour consiste donc à remplacer l'app-image/archive ou installer une nouvelle version tout en conservant le répertoire de données.

La désinstallation du binaire ne doit pas supprimer implicitement la base SQLite utilisateur. La suppression des données est une action explicite séparée.

## Gate M9

Windows :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
# facultatif si WiX est installé :
.\distribution\build-windows-installer.ps1
```

Linux :

```bash
./mvnw clean test
./distribution/build-portable.sh
```

Les deux scripts portables doivent produire une archive et réussir le smoke test du launcher embarqué.