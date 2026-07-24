# MORPHEUS — Roadmap de distribution et déploiement

Statut : **stratégie native-first acceptée ; M9 validé sur Windows et Linux**

Décisions de référence :

- [`../adr/0027-native-first-container-supported-distribution.md`](../adr/0027-native-first-container-supported-distribution.md)
- [`../adr/0059-stable-local-cli-contract.md`](../adr/0059-stable-local-cli-contract.md)
- [`../adr/0060-conservative-full-snapshot-cli-sync.md`](../adr/0060-conservative-full-snapshot-cli-sync.md)
- [`../adr/0061-self-contained-jpackage-portable-distribution.md`](../adr/0061-self-contained-jpackage-portable-distribution.md)

Les ADR M9 sont **Acceptées** après preuves reproductibles Windows/Linux.

Validation : [`../VALIDATION_M9.md`](../VALIDATION_M9.md).

---

# 1. Résumé

```text
Développeur local      -> native-first
CLI                     -> native-first, archive autonome
MCP stdio               -> native-first
MCP réseau              -> native ou Docker futur
API                     -> Docker support officiel futur
CI/CD headless          -> archive portable ou Docker futur
Écosystème multi-moteur -> Docker Compose possible, jamais obligatoire
```

Principe :

> **Même cœur MORPHEUS, plusieurs adapters et plusieurs formats de distribution.**

M9 matérialise la première distribution utilisateur : CLI locale + SQLite + runtime Java embarqué.

---

# 2. Cibles de distribution

| Cible | Usage principal | Statut M9 |
|---|---|---|
| Archive portable Windows x64 | développeur / CLI | **✅ officielle, validée** |
| Archive portable Linux x64 | développeur / CLI / CI | **✅ officielle, validée** |
| JAR exécutable autonome | debug / intégration avancée | **✅ validé** |
| Installateur Windows EXE | confort desktop | **optionnel, dépend de WiX au build** |
| Image Docker | headless / MCP réseau / API | **futur** |
| Docker Compose | écosystème multi-services | **futur / optionnel** |

L'archive portable est l'artefact de référence M9 car elle contient le launcher et son runtime et n'impose aucun installateur système à l'utilisateur final.

---

# 3. M9 — CLI et distribution locale ✅

## M9-S1 — CLI stabilisée

Main officiel :

```text
com.morpheus.cli.MorpheusMain
```

Commandes :

```text
help
version
paths
projects list
projects add
sync
sync-status
requirements find
changes list
changes get
constraints list
decisions list
tasks list
trace-requirement
change-context
analyze-change
quality
```

Contrats :

```text
human output par défaut
--json pour scripts/agents
stdout = résultat
stderr = erreur
exit codes stables
```

La CLI appelle les services applicatifs ; les règles métier restent dans `morpheus-application`.

Documentation : [`../CLI.md`](../CLI.md).

## M9-S2 — Layout runtime

Priorité :

```text
option CLI > variable MORPHEUS_* > default OS
```

Options : `--data-dir`, `--config-dir`, `--db`.

Variables : `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`.

Windows :

```text
data   = %LOCALAPPDATA%\Morpheus
config = %APPDATA%\Morpheus
db     = <data>\morpheus.db
logs   = <data>\logs
```

Linux :

```text
data   = $XDG_DATA_HOME/morpheus ou ~/.local/share/morpheus
config = $XDG_CONFIG_HOME/morpheus ou ~/.config/morpheus
db     = <data>/morpheus.db
logs   = <data>/logs
```

Avec `--data-dir`, la config par défaut devient `<data>/config`.

## M9-S3 — Synchronisation exécutable

```text
workspace OpenSpec
 -> NormalizedProjectContent
 -> ProjectSnapshotImportService
 -> BUILDING snapshot
 -> persistence CURRENT + business content + traceability
 -> validation
 -> activation atomique
```

L'ancien snapshot ACTIVE n'est remplacé qu'après validation réussie.

Le launcher officiel force actuellement `FULL_REBUILD` afin que l'état de synchronisation M7 reflète l'exécution réelle.

```text
reliability > incremental speed
receipt mode == real execution mode
```

## M9-S4 — JAR exécutable autonome

```text
morpheus-cli-<version>-all.jar
```

Le shade merge `META-INF/services` afin de conserver les providers de services nécessaires, notamment JDBC SQLite.

Cette forme requiert Java et reste destinée au debug/intégration avancée ; la distribution standard embarque son runtime.

## M9-S5 — Runtime Java embarqué

Choix M9 :

```text
jpackage --type app-image
```

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
```

Chaque script :

1. construit le JAR autonome ;
2. crée l'app-image ;
3. vérifie le launcher ;
4. exécute `--version` et `--json version` ;
5. archive l'app-image.

## M9-S6 — Distribution portable Windows ✅

```text
dist/morpheus-0.1.0-windows-x64.zip
```

Preuve :

```text
298/298 PASS
Architecture 149/149 PASS
jpackage app-image PASS
launcher human/JSON PASS
runtime embarqué PASS
```

## M9-S7 — Installateur Windows optionnel

```text
distribution/build-windows-installer.ps1
```

En JDK 21, WiX est nécessaire pour produire MSI/EXE. Son absence ne bloque pas l'archive portable. Le script effectue un skip explicite lorsque WiX est absent.

## M9-S8 — Distribution portable Linux ✅

```text
dist/morpheus-0.1.0-linux-x64.tar.gz
```

Preuve WSL/Ubuntu + OpenJDK/Javac/jpackage 21.0.11 :

```text
298/298 PASS
Architecture 149/149 PASS
jpackage app-image PASS
launcher human/JSON PASS
runtime embarqué PASS
```

Le `tar.gz` autonome est la cible Linux officielle M9. `deb`/`rpm` restent optionnels.

## M9-S9 — Upgrade et uninstall

Invariant :

```text
installation != data directory
installation != config directory
uninstall binary != delete user data
```

L'upgrade remplace l'app-image/binaire tout en réutilisant le même répertoire SQLite/config.

Documentation opérationnelle : [`../../distribution/README.md`](../../distribution/README.md).

---

# 4. Gate M9 — ✅ COMPLET

## Windows

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

```text
TOTAL 298/298 PASS
Architecture 149/149 PASS
ZIP produit
launcher --version PASS
launcher --json version PASS
runtime Java embarqué
```

## Linux

```bash
./mvnw clean test
./distribution/build-portable.sh
```

```text
TOTAL 298/298 PASS
Architecture 149/149 PASS
tar.gz produit
launcher --version PASS
launcher --json version PASS
runtime Java embarqué
```

M9 est **VALIDÉ**.

---

# 5. Portabilité du dépôt

`.gitattributes` fixe les fins de ligne :

```text
mvnw     LF
*.sh     LF
mvnw.cmd CRLF
*.ps1    CRLF
```

Le gate Linux de validation a utilisé un export Git vers un filesystem Linux local sous WSL, afin de ne pas dépendre de la représentation CRLF/metadata d'un working tree Windows monté sous `/mnt`.

---

# 6. M10 — MCP

## M10-S1 — MCP local

Mode privilégié :

```text
morpheus mcp --stdio
```

Usage : IDE/agents locaux. Aucun Docker obligatoire.

## M10-S2 — MCP réseau

Seulement si le transport et les consommateurs le justifient.

```text
native
ou
Docker
```

La stack réseau ne doit pas contaminer le domaine/application.

---

# 7. M11 — API et image Docker officielle

## M11-S1 — Serveur API

Framework choisi à ce jalon, pas avant.

## M11-S2 — Image Docker

Exigences :

```text
reproductible
versionnée
non-root si possible
configuration externe
healthcheck
arrêt propre
logs exploitables
```

## M11-S3 — Volumes

```text
workspace -> /workspace (read-only lorsque possible)
data      -> /data
SQLite    -> /data/morpheus.db
```

Test obligatoire : recréer le conteneur sans perdre la connaissance persistée.

## M11-S4 — Configuration

La priorité M9 constitue la base :

```text
CLI args
environment variables
config file futur si nécessaire
```

Les secrets éventuels ne doivent pas être intégrés à l'image.

---

# 8. Composition écosystème — future

Cible possible :

```text
services:
  morpheus
  minos
  nexus
  jarvis
```

Objectifs : environnement d'intégration reproductible, réseau local des moteurs, volumes séparés, démarrage indépendant et aucun moteur requis pour lancer MORPHEUS seul.

---

# 9. CI/CD

Workflow cross-platform versionné :

```text
.github/workflows/m9-validation.yml
```

Il reste optionnel. Le gate local constitue la preuve de référence :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Les preuves M9 finales ont été obtenues localement sur Windows et Linux/WSL.

---

# 10. Règle de déploiement

```text
native-first
portable archive is official M9 artifact
runtime embedded
user data outside installation
Docker supported later, never mandatory for local CLI
merge only after explicit authorization
```