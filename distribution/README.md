# MORPHEUS — Distribution locale M9

M9 retient une stratégie **native-first** avec une archive portable autonome comme artefact principal.

Statut M9 : **✅ VALIDÉ Windows + Linux — 24 juillet 2026**.

## Artefact principal

Les scripts `build-portable.ps1` et `build-portable.sh` :

1. construisent le JAR CLI autonome `morpheus-cli-*-all.jar` ;
2. créent une application `jpackage --type app-image` ;
3. embarquent un runtime Java généré par `jpackage`/`jlink` ;
4. exécutent `morpheus --version` puis `morpheus --json version` sur le launcher packagé ;
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
.\morpheus\morpheus.exe --json version
.\morpheus\morpheus.exe help
```

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

La génération EXE/MSI par `jpackage` dépend des outils natifs Windows requis par le JDK de packaging (WiX pour JDK 21). L'archive portable reste officielle et ne dépend pas de WiX chez l'utilisateur final.

Le script d'installateur détecte l'absence de WiX avant `jpackage` et termine alors par un skip explicite sans invalider l'app-image portable.

### Preuve Windows — 24 juillet 2026

```text
clean test                      298/298 PASS
Architecture Tests             149/149 PASS
uber-JAR                        BUILD SUCCESS
jpackage app-image             PASS
morpheus.exe --version         PASS
morpheus.exe --json version    PASS
Windows ZIP                    PASS
runtime Java embarqué          PASS
WiX absent                     installateur EXE optionnel SKIPPED proprement
```

Artefact produit :

```text
dist/morpheus-0.1.0-windows-x64.zip
```

Smoke observé :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

## Linux

Le packaging Linux doit être lancé **depuis un shell Linux**. Exécuter `./mvnw` ou `build-portable.sh` depuis PowerShell ne constitue pas une preuve Linux.

Sur une machine Linux/WSL :

```bash
export JAVA_HOME=/path/to/jdk-21
chmod +x mvnw distribution/build-portable.sh
./mvnw clean test
./distribution/build-portable.sh
```

Après extraction :

```bash
./morpheus/bin/morpheus --version
./morpheus/bin/morpheus --json version
./morpheus/bin/morpheus help
```

La distribution Linux M9 officielle est l'archive `tar.gz` autonome. Les paquets `deb`/`rpm` restent optionnels car ils dépendent des outils natifs de la distribution de build et n'apportent pas de sémantique MORPHEUS supplémentaire.

### Preuve Linux — 24 juillet 2026

Environnement observé :

```text
WSL / Ubuntu
OpenJDK 21.0.11
javac   21.0.11
jpackage 21.0.11
filesystem Linux local
```

Gate :

```text
clean test                      298/298 PASS
Architecture Tests             149/149 PASS
uber-JAR                        BUILD SUCCESS
jpackage app-image             PASS
morpheus --version             PASS
morpheus --json version        PASS
Linux tar.gz                   PASS
runtime Java embarqué          PASS
```

Artefact produit :

```text
dist/morpheus-0.1.0-linux-x64.tar.gz
```

Smoke observé :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Le script confirme :

```text
The archive contains its Java runtime; end users do not need a separately installed JDK.
```

## Fins de ligne cross-platform

`.gitattributes` impose :

```text
mvnw     LF
*.sh     LF
mvnw.cmd CRLF
*.ps1    CRLF
```

Cette règle prévient les erreurs `bash\r` dans les environnements Unix/WSL.

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

## Gate M9 — ✅ COMPLET

```text
Windows  298/298 PASS + app-image + ZIP + smoke human/JSON
Linux    298/298 PASS + app-image + tar.gz + smoke human/JSON
Runtime  embarqué sur les deux plateformes
```

Validation complète : [`../docs/VALIDATION_M9.md`](../docs/VALIDATION_M9.md).