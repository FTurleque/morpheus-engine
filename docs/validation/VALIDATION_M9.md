# Validation M9 — CLI stabilisée et distribution locale

Statut : **VALIDÉ — intégration portée par PR #56**

Date : 24 juillet 2026

## Baseline

```text
C0 à M8 validés et intégrés
M8 merge = 6780fb024fe5b8645226f0aacecddb32bcfa7517
M8 gate  = 289/289 PASS
```

Head exécutable M9 testé :

```text
3b0fb46486cb28257d87d56084ef6e4fbe4cf7c7
```

## Question de sortie

> MORPHEUS peut-il être utilisé de façon fiable depuis une CLI locale stable, avec des commandes explicites et scriptables, des codes de sortie déterministes, une configuration de workspace/base de données claire, une archive portable reproductible, et une stratégie de runtime Java embarqué évaluée et prouvée sur Windows et Linux sans déplacer la logique métier dans l'adapter CLI ?

**Réponse : OUI.**

## Contrats validés

```text
MorpheusMain
MorpheusCli
CliRuntime
CliLayout
CliExitCode
ProjectSnapshotImportService
ProjectSnapshotImportResult
```

La CLI reste un adapter : les règles métier de requête, synchronisation publiée, qualité, traçabilité et analyse restent dans `morpheus-application` / `morpheus-domain`.

## Comportements validés

- help/version/paths ;
- registre projets ;
- sync et sync-status ;
- requirements/changes/constraints/decisions/tasks ;
- trace-requirement et change-context ;
- analyze-change M8 et quality M6 ;
- sorties humaines et `--json` ;
- stdout/stderr séparés ;
- exit codes stables ;
- layout Windows/Linux et overrides `MORPHEUS_*` ;
- SQLite persistant et reopen ;
- publication full snapshot avec ancien ACTIVE conservé jusqu'à activation réussie ;
- diagnostic ERROR -> candidat FAILED sans remplacement de l'ACTIVE ;
- CLI sync -> FULL_REBUILD cohérent avec l'exécution réelle ;
- JAR autonome ombré ;
- app-image `jpackage` Windows et Linux ;
- runtime Java embarqué ;
- archives ZIP Windows et tar.gz Linux ;
- smoke humain et JSON sur les launchers packagés ;
- données/config séparées de l'installation ;
- installateur Windows EXE optionnel et absence de WiX non bloquante.

## Tests M9 ciblés

```text
MorpheusCliTest                    4/4 PASS
MorpheusMainTest                   2/2 PASS
ProjectSnapshotImportContractTest  3/3 PASS
```

## Gate Windows

Commande de référence :

```powershell
.\mvnw.cmd clean test
.\distribution\build-portable.ps1
```

Résultat observé le 24 juillet 2026 :

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

Packaging Windows :

```text
uber-JAR                        PASS
jpackage app-image             PASS
morpheus.exe --version         PASS
morpheus.exe --json version    PASS
Windows ZIP                    PASS
runtime Java embarqué          PASS
WiX absent                     installateur EXE optionnel SKIPPED proprement
```

Artefact :

```text
dist/morpheus-0.1.0-windows-x64.zip
```

Smoke :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

## Gate Linux

Environnement de validation : WSL/Ubuntu, filesystem Linux local, OpenJDK 21.0.11.

```text
java     21.0.11
javac    21.0.11
jpackage 21.0.11
```

Le commit Git a été exporté dans `~/morpheus-m9-linux-gate` afin d'éviter les effets CRLF/metadata du working tree Windows monté sous `/mnt`.

Commandes de référence :

```bash
./mvnw clean test
./distribution/build-portable.sh

./dist/.m9-linux/image/morpheus/bin/morpheus --version
./dist/.m9-linux/image/morpheus/bin/morpheus --json version
```

Résultat observé le 24 juillet 2026 :

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

Packaging Linux :

```text
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

Smoke :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Le script confirme que l'archive contient son runtime Java et que l'utilisateur final n'a pas besoin d'un JDK séparé.

## Portabilité des scripts

M9 fixe les fins de ligne via `.gitattributes` :

```text
mvnw     LF
*.sh     LF
mvnw.cmd CRLF
*.ps1    CRLF
```

Cette règle évite les erreurs `bash\r` lors des checkouts cross-platform.

## ADR acceptées

```text
ADR-0059 — Contrat CLI local stable, scriptable et explicite
ADR-0060 — Sync CLI conservateur par publication complète de snapshot
ADR-0061 — Distribution portable autonome via JAR ombré et jpackage app-image
```

## Audit post-gate

Le head exécutable `3b0fb46486cb28257d87d56084ef6e4fbe4cf7c7` est celui exporté et testé sous Linux. Les changements de clôture ajoutés après ce head sont documentaires et ne modifient ni le code de production ni les tests validés.

## Décision finale

**M9 est VALIDÉ.**

L'intégration finale est portée par PR #56. La fusion reste soumise à l'autorisation explicite prévue par la gouvernance du dépôt.