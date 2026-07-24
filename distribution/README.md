# MORPHEUS — Distribution locale M9/M10

M9 retient une stratégie **native-first** avec une archive portable autonome comme artefact principal. M10 conserve cette stratégie et embarque désormais le serveur MCP STDIO et le Java MCP SDK dans le même artefact.

Statut : **✅ M9 VALIDÉ Windows + Linux ; M10 VALIDÉ Windows avec MCP embarqué — 24 juillet 2026**.

## Artefacts principaux

Les scripts `build-portable.ps1` et `build-portable.sh` :

1. construisent le JAR CLI autonome `morpheus-cli-*-all.jar` ;
2. vérifient que le serveur MCP et ses classes SDK sont réellement présents dans l'uber-JAR ;
3. créent une application `jpackage --type app-image` ;
4. embarquent un runtime Java généré par `jpackage`/`jlink` ;
5. exécutent `morpheus --version` puis `morpheus --json version` sur le launcher packagé ;
6. produisent une archive portable.

Cibles :

```text
Windows x64 -> dist/morpheus-<version>-windows-x64.zip
Linux x64   -> dist/morpheus-<version>-linux-x64.tar.gz
```

L'utilisateur final n'a pas besoin d'installer/configurer manuellement un JDK.

## Preuve MCP dans l'artefact

Depuis M10, les scripts contrôlent avant `jpackage` :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

Le build échoue si une de ces classes manque.

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
.\morpheus\morpheus.exe mcp --stdio
```

Installateur optionnel :

```powershell
.\distribution\build-windows-installer.ps1
```

La génération EXE/MSI par `jpackage` dépend des outils natifs Windows requis par le JDK de packaging (WiX pour JDK 21). L'archive portable reste officielle et ne dépend pas de WiX chez l'utilisateur final.

### Preuve Windows M9

```text
clean test                      298/298 PASS
Architecture Tests             149/149 PASS
uber-JAR                        BUILD SUCCESS
jpackage app-image             PASS
morpheus.exe --version         PASS
morpheus.exe --json version    PASS
Windows ZIP                    PASS
runtime Java embarqué          PASS
```

### Preuve Windows M10

Gate Java :

```text
MORPHEUS MCP                    5/5 PASS
MORPHEUS CLI                  10/10 PASS
Architecture Tests           149/149 PASS
TOTAL                        307/307 PASS
BUILD SUCCESS
```

Packaging :

```text
uber-JAR                        BUILD SUCCESS
MCP packaging proof            PASS
jpackage app-image             PASS
morpheus.exe --version         PASS
morpheus.exe --json version    PASS
Portable archive creation      PASS (attempt 1/8)
Windows ZIP                    PASS — 77275075 bytes
runtime Java embarqué          PASS
```

Artefact validé M10 :

```text
dist/morpheus-0.1.0-windows-x64.zip
```

Smoke observé :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Le script Windows utilise un archivage robuste :

```text
Compress-Archive -ErrorAction Stop
jusqu'à 8 tentatives avec backoff
suppression des ZIP partiels
vérification existence + taille > 0
fail-fast après dernière tentative
```

## Linux

Le packaging Linux doit être lancé **depuis un shell Linux**. Exécuter `./mvnw` ou `build-portable.sh` depuis PowerShell ne constitue pas une preuve Linux.

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
./morpheus/bin/morpheus mcp --stdio
```

La distribution Linux officielle reste l'archive `tar.gz` autonome. Les paquets `deb`/`rpm` restent optionnels.

### Preuve Linux M9

Environnement : WSL/Ubuntu, OpenJDK/Javac/jpackage 21.0.11, filesystem Linux local.

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

Artefact :

```text
dist/morpheus-0.1.0-linux-x64.tar.gz
```

Le script Linux M10 utilise le même shaded JAR et vérifie les mêmes classes MCP avant `jpackage`.

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

La CLI et le serveur MCP séparent l'installation de l'état utilisateur.

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

Le serveur MCP utilise exactement la même résolution de base SQLite que la CLI.

## Upgrade / uninstall

L'état MORPHEUS (SQLite/config) est par défaut **hors du répertoire d'installation**. Une mise à jour consiste donc à remplacer l'app-image/archive ou installer une nouvelle version tout en conservant le répertoire de données.

La désinstallation du binaire ne doit pas supprimer implicitement la base SQLite utilisateur. La suppression des données est une action explicite séparée.

## Gates

```text
M9 Windows  298/298 PASS + app-image + ZIP
M9 Linux    298/298 PASS + app-image + tar.gz
M10 Windows 307/307 PASS + MCP proof + app-image + ZIP
Runtime     Java embarqué
```

Validations :

- [`../docs/VALIDATION_M9.md`](../docs/VALIDATION_M9.md)
- [`../docs/VALIDATION_M10.md`](../docs/VALIDATION_M10.md)
- [`../docs/MCP.md`](../docs/MCP.md)
