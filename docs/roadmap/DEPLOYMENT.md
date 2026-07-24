# MORPHEUS — Roadmap de distribution et déploiement

Statut : **stratégie native-first acceptée ; M9 implémenté fonctionnellement, validation Windows/Linux en attente**

Décisions de référence :

- [`../adr/0027-native-first-container-supported-distribution.md`](../adr/0027-native-first-container-supported-distribution.md)
- [`../adr/0059-stable-local-cli-contract.md`](../adr/0059-stable-local-cli-contract.md)
- [`../adr/0060-conservative-full-snapshot-cli-sync.md`](../adr/0060-conservative-full-snapshot-cli-sync.md)
- [`../adr/0061-self-contained-jpackage-portable-distribution.md`](../adr/0061-self-contained-jpackage-portable-distribution.md)

Les ADR M9 restent **Proposées** tant que les preuves Windows/Linux finales ne sont pas obtenues.

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
| Archive portable Windows x64 | développeur / CLI | **officielle, gate pending** |
| Archive portable Linux x64 | développeur / CLI / CI | **officielle, gate pending** |
| JAR exécutable autonome | debug / intégration avancée | **implémenté** |
| Installateur Windows EXE | confort desktop | **optionnel, dépend de WiX au build** |
| Image Docker | headless / MCP réseau / API | **futur** |
| Docker Compose | écosystème multi-services | **futur / optionnel** |

L'archive portable est l'artefact de référence M9 car elle contient le launcher et son runtime et n'impose aucun installateur système à l'utilisateur final.

---

# 3. M9 — CLI et distribution locale

## M9-S1 — CLI stabilisée

Main officiel :

```text
com.morpheus.cli.MorpheusMain
```

Commandes implémentées :

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

Options :

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

Avec `--data-dir`, la config par défaut devient `<data>/config` : l'utilisateur peut donc regrouper tout l'état mutable dans un répertoire portable explicite.

Aucune donnée importante n'est stockée dans un répertoire temporaire.

## M9-S3 — Synchronisation exécutable

Le launcher M9 expose un `sync` OpenSpec conservateur :

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

Le launcher officiel force actuellement le chemin `FULL_REBUILD` afin que l'état de synchronisation M7 reflète l'exécution réelle. Le planificateur incrémental M7 reste intact mais n'est pas présenté comme exécuté par M9 tant qu'un exécuteur métier incrémental complet n'est pas disponible.

```text
reliability > incremental speed
receipt mode == real execution mode
```

## M9-S4 — JAR exécutable autonome

`morpheus-cli` produit :

```text
morpheus-cli-<version>-all.jar
```

Le shade merge `META-INF/services` afin de conserver les providers de services nécessaires, notamment pour l'adapter JDBC SQLite.

Usage avancé :

```text
java -jar morpheus-cli-<version>-all.jar help
```

Cette forme requiert Java et n'est donc pas la distribution standard destinée à l'utilisateur final.

## M9-S5 — Runtime Java embarqué

Choix M9 :

```text
jpackage --type app-image
```

`jpackage` construit l'app-image et son runtime associé ; cette voie matérialise l'objectif `jlink/jpackage` de M9 sans demander un JDK à l'utilisateur final.

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
```

Chaque script :

1. construit le JAR autonome ;
2. crée l'app-image ;
3. vérifie la présence du launcher ;
4. exécute `morpheus --version` avec le launcher packagé ;
5. archive l'app-image.

## M9-S6 — Distribution portable Windows

Commande :

```powershell
.\distribution\build-portable.ps1
```

Artefact :

```text
dist/morpheus-<version>-windows-x64.zip
```

Après extraction :

```powershell
.\morpheus\morpheus.exe --version
.\morpheus\morpheus.exe help
```

Le ZIP contient l'application et le runtime Java nécessaire.

## M9-S7 — Installateur Windows optionnel

Script :

```powershell
.\distribution\build-windows-installer.ps1
```

Il produit un installateur EXE depuis l'app-image déjà validée lorsque les outils natifs requis par `jpackage` sont présents sur la machine de packaging.

En JDK 21, WiX est nécessaire pour produire MSI/EXE. Cette contrainte ne concerne pas l'utilisateur de l'archive portable.

L'installateur n'est donc **pas** une condition de validité de la distribution portable M9.

## M9-S8 — Distribution portable Linux

Commande :

```bash
bash distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

Après extraction :

```bash
./morpheus/bin/morpheus --version
./morpheus/bin/morpheus help
```

Le `tar.gz` autonome est la cible Linux officielle M9. Les paquets `deb`/`rpm` restent optionnels : ils ajoutent des contraintes de packaging propres aux distributions sans changer la sémantique MORPHEUS.

## M9-S9 — Upgrade et uninstall

Invariant :

```text
installation != data directory
installation != config directory
uninstall binary != delete user data
```

L'upgrade de l'archive consiste à remplacer l'app-image/binaire tout en réutilisant le même répertoire de données SQLite/config.

La désinstallation du binaire n'efface pas intentionnellement les données utilisateur. Leur suppression doit rester une action explicite distincte.

Documentation opérationnelle : [`../../distribution/README.md`](../../distribution/README.md).

---

# 4. Gates M9

## Windows

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Preuves obligatoires :

```text
BUILD SUCCESS
MorpheusCliTest PASS
MorpheusMainTest PASS
ProjectSnapshotImportContractTest PASS
morpheus.exe --version PASS
ZIP produit
```

Installateur facultatif :

```powershell
.\distribution\build-windows-installer.ps1
```

## Linux

```bash
./mvnw clean test
bash distribution/build-portable.sh
```

Preuves obligatoires :

```text
BUILD SUCCESS
mêmes contrats Java
morpheus --version PASS
tar.gz produit
```

M9 reste **non validé** tant que les preuves obligatoires Windows et Linux ne sont pas enregistrées.

---

# 5. M10 — MCP

## M10-S1 — MCP local

Mode privilégié :

```text
morpheus mcp --stdio
```

Usage : IDE/agents locaux.

Aucun Docker obligatoire.

## M10-S2 — MCP réseau

Seulement si le transport et les consommateurs le justifient.

Distribution :

```text
native
ou
Docker
```

La stack réseau ne doit pas contaminer le domaine/application.

---

# 6. M11 — API et image Docker officielle

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

Topologie cible :

```text
workspace -> /workspace (read-only lorsque possible)
data      -> /data
SQLite    -> /data/morpheus.db
```

Test obligatoire : recréer le conteneur sans perdre la connaissance persistée.

## M11-S4 — Configuration

La priorité M9 constitue la base candidate :

```text
CLI args
environment variables
config file futur si nécessaire
```

Les secrets éventuels ne doivent pas être intégrés à l'image.

---

# 7. Composition écosystème — future

Cible possible :

```text
services:
  morpheus
  minos
  nexus
  jarvis
```

Objectifs :

- environnement d'intégration reproductible ;
- réseau local des moteurs ;
- volumes séparés ;
- démarrage indépendant ;
- aucun moteur requis pour lancer MORPHEUS seul.

Ce compose ne constitue pas une architecture monolithique : il ne fait qu'assembler des services autonomes.

---

# 8. CI/CD

Un workflow M9 cross-platform est versionné :

```text
.github/workflows/m9-validation.yml
```

Il vise :

```text
Windows -> clean test + portable build + smoke + ZIP
Linux   -> clean test + portable build + smoke + tar.gz
```

Le gate local de développement reste cependant la référence obligatoire du projet :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

GitHub Actions complète la preuve cross-platform ; il ne remplace pas la discipline de validation locale.

Une image Docker ne devient pas le moyen obligatoire de compiler/tester MORPHEUS.

---

# 9. Critères transverses

Toute distribution doit préserver :

```text
local-first
offline core
SQLite persistence
provider isolation
same MORPHEUS domain/application semantics
no mandatory cloud
no mandatory MINOS/NEXUS/JARVIS
user data outside replaceable binary/runtime
```

---

# 10. Ce qui reste volontairement ouvert après M9

```text
Windows MSI vs EXE comme format d'installateur préféré
deb/rpm éventuels
signature de code / notarisation
mécanisme d'auto-update
base Docker image
HTTP framework
MCP network transport
ports
Docker Compose production topology
release registry / GHCR publication
```

Ces décisions ne sont pas nécessaires pour valider la CLI et les archives portables M9.