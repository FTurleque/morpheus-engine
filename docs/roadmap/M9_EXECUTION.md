# M9 — Plan d'exécution détaillé

Statut : **✅ M9 VALIDÉ — intégration portée par PR #56**

Dernière mise à jour : 24 juillet 2026

## Baseline

```text
C0 à M8 validés et intégrés
M8 merge = 6780fb024fe5b8645226f0aacecddb32bcfa7517
M8 gate  = 289/289 PASS
```

Issue : **#55 — M9 — CLI stabilisée et distribution locale**  
Branche : `m9/cli-distribution`  
Head exécutable validé : `3b0fb46486cb28257d87d56084ef6e4fbe4cf7c7`

## Question de sortie

> **MORPHEUS peut-il être utilisé de façon fiable depuis une CLI locale stable, avec des commandes explicites et scriptables, des codes de sortie déterministes, une configuration de workspace/base de données claire, une archive portable reproductible, et une stratégie de runtime Java embarqué évaluée et prouvée sur Windows et Linux sans déplacer la logique métier dans l'adapter CLI ?**

**Réponse : OUI.**

Validation complète : [`../VALIDATION_M9.md`](../VALIDATION_M9.md).

## M9-S1 — Contrat CLI ✅

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

Contrats :

```text
human + --json
stdout = résultat
stderr = erreur
stable exit codes
SQLite persistent state
```

## M9-S2 — Layout local ✅

```text
option CLI > variable MORPHEUS_* > default OS
```

Options : `--data-dir`, `--config-dir`, `--db`.

Variables : `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`.

Windows : LocalAppData/AppData.  
Linux : XDG avec fallback standard home.

Un `--data-dir` explicite permet un layout portable où DB/config restent hors de l'app-image.

## M9-S3 — Sync exécutable ✅

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

Le launcher M9 force `sync` en `FULL_REBUILD`. Le moteur M7 conserve son planificateur incrémental, mais M9 n'annonce jamais un mode incrémental lorsque l'exécution réelle reconstruit tout le snapshot.

```text
reliability > incremental speed
receipt mode == real execution mode
old ACTIVE survives failed candidate
```

## M9-S4 — Tests ✅

Tests ciblés :

```text
MorpheusCliTest                    4/4 PASS
MorpheusMainTest                   2/2 PASS
ProjectSnapshotImportContractTest  3/3 PASS
```

Couverture : help/version/paths, exit codes, stdout/stderr, layouts Windows/Linux, registre projets, OpenSpec sync end-to-end, SQLite reopen, requirements/changes, trace/change-context, analyse M8, qualité M6, full-rebuild policy, import Memory/SQLite et lifecycle du candidat.

## M9-S5 — JAR autonome ✅

```text
morpheus-cli-0.1.0-SNAPSHOT-all.jar
```

Plugins :

```text
maven-jar-plugin    3.5.0
maven-shade-plugin  3.6.2
```

`ServicesResourceTransformer` conserve les providers de services nécessaires dans l'uber-JAR.

## M9-S6 — Runtime embarqué / archives portables ✅

Scripts :

```text
distribution/build-portable.ps1
distribution/build-portable.sh
```

Ils construisent un `jpackage --type app-image`, smoke-testent le launcher en sortie humaine et JSON puis produisent :

```text
Windows -> dist/morpheus-0.1.0-windows-x64.zip
Linux   -> dist/morpheus-0.1.0-linux-x64.tar.gz
```

Le runtime Java est embarqué dans les deux app-images ; l'utilisateur final n'a pas de JDK manuel à configurer.

Smoke commun :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

## M9-S7 — Installateur Windows optionnel ✅ évalué

```text
distribution/build-windows-installer.ps1
```

WiX était absent sur la machine de validation. Le script détecte cette absence et termine par un skip explicite. L'installateur n'est pas une condition de validité : le ZIP autonome est l'artefact Windows officiel M9.

## M9-S8 — Upgrade / uninstall ✅

Invariant :

```text
installation != data/config
uninstall binary != delete user data
```

Documenté dans `distribution/README.md`.

## Gate Windows — ✅ VALIDÉ

24 juillet 2026 :

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
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

```text
portable ZIP produit
launcher --version PASS
launcher --json version PASS
runtime Java embarqué PASS
```

## Gate Linux — ✅ VALIDÉ

Environnement : WSL/Ubuntu, filesystem Linux local, OpenJDK/Javac/jpackage 21.0.11.

24 juillet 2026 :

```text
MORPHEUS Domain          21/21 PASS
MORPHEUS Application     82/82 PASS
OpenSpec Provider        26/26 PASS
Synthetic Provider        7/7 PASS
SQLite Store              7/7 PASS
MORPHEUS CLI              6/6 PASS
Architecture Tests      149/149 PASS
TOTAL                   298/298 PASS
Failures                   0
Errors                     0
Skipped                    0
BUILD SUCCESS
Total time               26.187 s
Finished 2026-07-24T11:21:50+02:00
```

Packaging :

```text
uber-JAR BUILD SUCCESS
jpackage app-image PASS
launcher --version PASS
launcher --json version PASS
tar.gz produit
runtime Java embarqué PASS
```

## Portabilité des scripts ✅

`.gitattributes` fixe :

```text
mvnw     LF
*.sh     LF
mvnw.cmd CRLF
*.ps1    CRLF
```

Le gate Linux a été exécuté depuis un export Git propre sur filesystem Linux afin d'écarter les effets CRLF du working tree Windows monté dans WSL.

## ADR M9

```text
ADR-0059 — ✅ Acceptée — CLI stable
ADR-0060 — ✅ Acceptée — sync full snapshot conservateur
ADR-0061 — ✅ Acceptée — distribution portable jpackage
```

## Décision finale

```text
Windows gate  ✅
Linux gate    ✅
Packaging     ✅
ADRs          ✅
M9            ✅ VALIDÉ
```

La PR #56 peut sortir du mode draft. Sa fusion reste soumise à l'autorisation explicite prévue par la gouvernance du dépôt.