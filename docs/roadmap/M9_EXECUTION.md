# M9 — Plan d'exécution détaillé

Statut : **M9 FONCTIONNELLEMENT COMPLET — validation Windows/Linux pending**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M8 validés et intégrés
M8 merge = 6780fb024fe5b8645226f0aacecddb32bcfa7517
M8 gate  = 289/289 PASS
```

Issue : **#55 — M9 — CLI stabilisée et distribution locale**  
Branche : `m9/cli-distribution`

## Question de sortie

> **MORPHEUS peut-il être utilisé de façon fiable depuis une CLI locale stable, avec des commandes explicites et scriptables, des codes de sortie déterministes, une configuration de workspace/base de données claire, une archive portable reproductible, et une stratégie de runtime Java embarqué évaluée et prouvée sur Windows et Linux sans déplacer la logique métier dans l'adapter CLI ?**

Réponse actuelle : **implémentation OUI ; preuve finale pending**.

## M9-S1 — Contrat CLI

Main packagé :

```text
com.morpheus.cli.MorpheusMain
```

Adapter :

```text
MorpheusCli
CliLayout
CliRuntime
CliExitCode
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

Sorties : human par défaut, `--json` pour script/agent.

```text
stdout = résultat
stderr = erreur
```

Exit codes :

```text
0  SUCCESS
2  USAGE
3  NOT_FOUND
4  STATE_ERROR
5  IO_ERROR
10 INTERNAL_ERROR
```

## M9-S2 — Layout local

Options :

```text
--data-dir
--config-dir
--db
```

Environment :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Windows : LocalAppData/AppData.  
Linux : XDG avec fallback standard home.

Un data-dir explicite active naturellement un layout portable : DB + config restent regroupés hors de l'app-image.

## M9-S3 — Sync exécutable

Nouveaux contrats application :

```text
ProjectSnapshotImportService
ProjectSnapshotImportResult
```

Publication :

```text
NormalizedProjectContent
 -> SpecificationVersion
 -> BUILDING snapshot
 -> CURRENT RequirementVersionRecord
 -> SnapshotBusinessContent
 -> derived TraceabilityLink
 -> validation
 -> ACTIVE
```

Le launcher M9 force `sync` en FULL_REBUILD. Le moteur M7 conserve son planificateur incrémental, mais M9 n'annonce jamais un mode incrémental lorsque l'exécution réelle reconstruit tout le snapshot.

## M9-S4 — Tests

Nouveaux tests :

```text
MorpheusCliTest
MorpheusMainTest
ProjectSnapshotImportContractTest
```

Couverture prévue :

- help/version/paths ;
- exit codes + stdout/stderr ;
- layout Linux + portable ;
- projects add/list ;
- OpenSpec sync end-to-end ;
- SQLite reopen ;
- requirements query ;
- changes query ;
- change-context ;
- M8 analyze-change ;
- M6 quality ;
- launcher full-rebuild policy ;
- import Memory ;
- import SQLite ;
- predecessor/version sequence ;
- candidate FAILED ne remplace pas ACTIVE.

## M9-S5 — JAR autonome

`morpheus-cli/pom.xml` produit :

```text
morpheus-cli-0.1.0-SNAPSHOT-all.jar
```

Plugins fixés :

```text
maven-jar-plugin   3.5.0
maven-shade-plugin 3.6.2
```

`ServicesResourceTransformer` conserve les providers de services nécessaires dans l'uber-JAR.

## M9-S6 — Runtime embarqué / archive portable

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
```

Ils construisent un `jpackage --type app-image`, smoke-testent le launcher, puis produisent :

```text
Windows -> ZIP
Linux   -> tar.gz
```

Le runtime Java est embarqué dans l'app-image ; l'utilisateur final n'a pas de JDK manuel à configurer.

## M9-S7 — Installateur Windows

```text
distribution/build-windows-installer.ps1
```

Produit un EXE depuis l'app-image lorsque WiX est disponible sur la machine de packaging.

L'installateur est optionnel ; le ZIP autonome reste l'artefact Windows officiel M9.

## M9-S8 — Upgrade / uninstall

Documenté dans `distribution/README.md`.

Invariant :

```text
installation != data/config
uninstall binary != delete user data
```

## ADR M9

```text
ADR-0059 — Proposée — CLI stable
ADR-0060 — Proposée — sync full snapshot conservateur
ADR-0061 — Proposée — distribution portable jpackage
```

Elles passeront Acceptées uniquement après les preuves reproductibles.

## Gates requis

### Windows

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
# évaluation optionnelle installateur si WiX disponible
.\distribution\build-windows-installer.ps1
```

Preuves attendues : BUILD SUCCESS, comptes exacts, `MorpheusCliTest`, `MorpheusMainTest`, `ProjectSnapshotImportContractTest`, launcher packagé `--version`, ZIP présent.

### Linux

```bash
./mvnw clean test
./distribution/build-portable.sh
```

Preuves attendues : BUILD SUCCESS, mêmes tests, launcher packagé `--version`, tar.gz présent.

## Invariants

```text
CLI adapter only
business rules remain in application
stdout != stderr
stable exit codes
JSON deterministic
SQLite persistent state
FULL_REBUILD receipt == real execution
old ACTIVE survives failed candidate
portable archive contains runtime
no manual JDK for end user
Windows + Linux proof required
native-first, container-supported
```

## Clôture

M9 ne sera marqué **VALIDÉ** qu'après les gates Windows et Linux. Avant ces preuves, la PR doit rester draft et les ADR-0059/60/61 restent Proposées.