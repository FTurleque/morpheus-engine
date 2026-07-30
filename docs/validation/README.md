# Preuves de validation

Cette section regroupe les preuves de sortie des phases, jalons, consolidations et releases MORPHEUS.

## Index

```text
VALIDATION_C0.md
VALIDATION_M0.md → VALIDATION_M14.md
VALIDATION_D0.md
VALIDATION_M15.md → VALIDATION_M20.md
VALIDATION_D1.md
VALIDATION_R1.md
VALIDATION_M21.md → VALIDATION_M27.md
VALIDATION_R2.md
```

Chaque document conserve les décisions, SHA testés, commandes et gates réellement connus au moment de la validation. Ces fichiers constituent des **preuves historiques** ; ils ne sont jamais réécrits pour fabriquer rétroactivement un PASS, un merge ou une publication.

## Baseline publiée

MORPHEUS **1.0.0** a été qualifié via M20 puis consolidé et publié via D1/R1.

```text
M20 code qualifié  9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge           75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA    51f6a120f3461c8d8c24323f3db8211d28d6cb42
Tag                 v1.0.0
Tests M20           454 PASS Windows + Linux
Architecture        182 PASS Windows + Linux
GitHub Release      MORPHEUS 1.0.0 — 8/8 assets
```

Preuves : [`VALIDATION_M20.md`](VALIDATION_M20.md), [`VALIDATION_D1.md`](VALIDATION_D1.md), [`VALIDATION_R1.md`](VALIDATION_R1.md).

## Évolutions 1.x intégrées

```text
M21  473 PASS Windows + Linux | Architecture 187
M22  494 PASS Windows + Linux | Architecture 190
M23  507 PASS Windows + Linux | Architecture 195
M24  543 PASS Windows + Linux | Architecture 221
M25  565 PASS Windows + Linux | Architecture 231
M26  579 PASS Windows + Linux | Architecture 234
M27  602 PASS Windows + Linux | Architecture 238
```

Preuves : [`VALIDATION_M21.md`](VALIDATION_M21.md), [`VALIDATION_M22.md`](VALIDATION_M22.md), [`VALIDATION_M23.md`](VALIDATION_M23.md), [`VALIDATION_M24.md`](VALIDATION_M24.md), [`VALIDATION_M25.md`](VALIDATION_M25.md), [`VALIDATION_M26.md`](VALIDATION_M26.md), [`VALIDATION_M27.md`](VALIDATION_M27.md).

## Dernier jalon techniquement qualifié

**M27 — Evidence-backed Assisted Reasoning** est qualifié sous Windows et Linux/WSL sur le même SHA exact puis intégré dans `develop` :

```text
Head exact qualifié     f97307c878125550693699124ca717f64f305a3a
Head PR docs-only       026c1d5f8671cd7b879fa89d51af8e83a5f06272
Merge                   f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Develop réconcilié      bccc118dda6fd818cf801750187afa4ad10b96e4
Tests                   602 PASS Windows + Linux
Architecture            238 PASS Windows + Linux
Windows JaCoCo          45.2226% lignes / 38.4456% branches
Linux JaCoCo            45.2246% lignes / 38.4456% branches
Executable delta        NONE après qualification
ADR-0095                Acceptée — M27
PR                      #112 MERGED vers develop
Issue                   #111 CLOSED / completed
```

La preuve détaillée est [`VALIDATION_M27.md`](VALIDATION_M27.md).

## Release candidate active

**R2 — MORPHEUS 1.1.0** est en préparation sur `r2-release-1.1.0`.

```text
Issue                   #113 OPEN
PR                      #114 DRAFT vers main
Release baseline        develop@bccc118dda6fd818cf801750187afa4ad10b96e4
Target version          1.1.0
Target tag              v1.1.0
Windows gate            NOT RUN
Linux/WSL gate          NOT RUN
Merge main              NOT AUTHORIZED
Tag / GitHub Release    NOT CREATED
```

La structure de preuve active est [`VALIDATION_R2.md`](VALIDATION_R2.md). Elle ne déclare aucun PASS avant réception des sorties réelles des validateurs exact-head :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

## Politique de gate

Une modification de code, POM, contrat runtime, migration, OpenAPI, packaging ou validateur exige un nouveau gate Windows + Linux/WSL sur le même SHA exact. Les commits post-gate doivent être comparés au SHA qualifié ; seul un delta exclusivement documentaire peut être accepté sans réexécution.

En juillet 2026, GitHub Actions n'est pas utilisé comme gate R2. Les preuves locales exact-head restent autoritatives.