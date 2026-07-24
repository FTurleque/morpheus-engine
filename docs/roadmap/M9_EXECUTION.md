# M9 — Plan d'exécution détaillé

Statut : **M9 FONCTIONNELLEMENT COMPLET — gate Windows validé, gate Linux pending**

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

Réponse actuelle : **implémentation OUI ; preuve Windows OUI ; preuve Linux pending**.

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

Couverture :

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

Gate Windows observé le 24 juillet 2026 :

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
Memory Store              0 test
SQLite Store              7/7 PASS
MORPHEUS CLI              6/6 PASS
Architecture Tests      149/149 PASS
TOTAL                   298/298 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Finished 2026-07-24T10:49:08+02:00
```

Détails M9 :

```text
MorpheusCliTest                    4/4 PASS
MorpheusMainTest                   2/2 PASS
ProjectSnapshotImportContractTest  3/3 PASS
```

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

Ils construisent un `jpackage --type app-image`, smoke-testent le launcher en sortie humaine et JSON, puis produisent :

```text
Windows -> ZIP
Linux   -> tar.gz
```

Le runtime Java est embarqué dans l'app-image ; l'utilisateur final n'a pas de JDK manuel à configurer.

Preuve Windows du 24 juillet 2026 :

```text
morpheus.exe --version
  MORPHEUS 0.1.0-SNAPSHOT

morpheus.exe --json version
  {"version":"0.1.0-SNAPSHOT"}

archive
  dist/morpheus-0.1.0-windows-x64.zip

runtime Java embarqué
  OUI
```

## M9-S7 — Installateur Windows

```text
distribution/build-windows-installer.ps1
```

Produit un EXE depuis l'app-image lorsque WiX est disponible sur la machine de packaging.

L'installateur est optionnel ; le ZIP autonome reste l'artefact Windows officiel M9.

Le 24 juillet 2026, WiX était absent de la machine de validation. Le script a correctement détecté cette absence et a terminé en skip explicite sans invalider l'app-image portable déjà prouvée.

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

Le gate Windows est désormais prouvé. Les ADR restent proposées jusqu'à la preuve cross-platform finale incluant Linux.

## Gates requis

### Windows — ✅ VALIDÉ le 24 juillet 2026

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
# évaluation optionnelle installateur si WiX disponible
.\distribution\build-windows-installer.ps1
```

Résultat :

```text
298/298 PASS
Architecture 149/149 PASS
BUILD SUCCESS
portable ZIP produit
launcher --version PASS
launcher --json version PASS
runtime Java embarqué prouvé
WiX absent -> installateur optionnel SKIPPED proprement
```

### Linux — ⏳ PENDING

```bash
./mvnw clean test
./distribution/build-portable.sh
```

Preuves attendues : BUILD SUCCESS, mêmes tests, launcher packagé `--version` et `--json version`, tar.gz présent.

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
