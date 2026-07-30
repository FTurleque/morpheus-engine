# Preuves de validation

Cette section regroupe les preuves de sortie des phases, jalons, consolidations et releases MORPHEUS.

```text
VALIDATION_C0.md
VALIDATION_M0.md
VALIDATION_M1.md
VALIDATION_M2.md
VALIDATION_M3.md
VALIDATION_M4.md
VALIDATION_M5.md
VALIDATION_M6.md
VALIDATION_M7.md
VALIDATION_M8.md
VALIDATION_M9.md
VALIDATION_M10.md
VALIDATION_M11.md
VALIDATION_M12.md
VALIDATION_M13.md
VALIDATION_M14.md
VALIDATION_D0.md
VALIDATION_M15.md
VALIDATION_M16.md
VALIDATION_M17.md
VALIDATION_M18.md
VALIDATION_M19.md
VALIDATION_M20.md
VALIDATION_D1.md
VALIDATION_R1.md
VALIDATION_M21.md
VALIDATION_M22.md
VALIDATION_M23.md
VALIDATION_M24.md
VALIDATION_M25.md
VALIDATION_M26.md
VALIDATION_M27.md
```

Chaque document conserve les décisions, SHA testés, commandes et gates connus au moment de la validation. Ils constituent des **preuves historiques** ; la documentation utilisateur et développeur active vit respectivement dans [`../user/`](../user/) et [`../developer/`](../developer/).

## Baseline publiée

MORPHEUS **1.0.0** a été qualifié via M20 puis consolidé/publié via D1/R1.

```text
M20 code qualifié  9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge           75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA    51f6a120f3461c8d8c24323f3db8211d28d6cb42
Tag                 v1.0.0
Tests M20           454/454 PASS Windows + Linux
Architecture        182/182 PASS Windows + Linux
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

**M27 — Evidence-backed Assisted Reasoning** est qualifié sur Windows et Linux/WSL sur le même SHA exact puis intégré dans `develop` :

```text
Head exact qualifié     f97307c878125550693699124ca717f64f305a3a
Head PR docs-only       026c1d5f8671cd7b879fa89d51af8e83a5f06272
Merge                   f8810803bd5ae7d57c4858e1e384c6a0132e1a45
Windows                 PASS
Linux / WSL             PASS
Tests                   602 PASS Windows + Linux
Architecture            238 PASS Windows + Linux
Windows JaCoCo          45.2226% lignes / 38.4456% branches
Linux JaCoCo            45.2246% lignes / 38.4456% branches
Facts / claims          séparation PASS
Confidence              bornée et explicite PASS
Evidence / provenance   PASS
Adapters                optionnels + fault isolation PASS
No silent mutation      PASS / mutated=false
CLI/MCP/HTTP             convergence PASS
Remote READ RBAC        PASS
CycloneDX/provenance    PASS Windows + Linux
Portable                PASS Windows + Linux
Packaged smokes         PASS Windows + Linux
Executable delta        NONE Windows + Linux
ADR-0095                Acceptée — M27
PR                      #112 MERGED vers develop
Issue                   #111 CLOSED / completed
CI / GitHub Actions     non utilisé — juillet 2026
```

La preuve détaillée est [`VALIDATION_M27.md`](VALIDATION_M27.md).

Le SHA exact qualifié reste distinct des commits documentaires de consolidation ajoutés ensuite. Une modification de code, POM, contrat runtime, OpenAPI, packaging ou validateur exige un nouveau gate exact-head Windows + Linux.