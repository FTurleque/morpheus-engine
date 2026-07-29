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

## Évolutions 1.x intégrées avant M25

```text
M21  473 PASS Windows + Linux | Architecture 187
M22  494 PASS Windows + Linux | Architecture 190
M23  507 PASS Windows + Linux | Architecture 195
M24  543 PASS Windows + Linux | Architecture 221
```

Preuves : [`VALIDATION_M21.md`](VALIDATION_M21.md), [`VALIDATION_M22.md`](VALIDATION_M22.md), [`VALIDATION_M23.md`](VALIDATION_M23.md), [`VALIDATION_M24.md`](VALIDATION_M24.md).

## Dernier jalon techniquement qualifié

**M25 — Policy Packs & Governance Automation** est qualifié sur Windows et Linux/WSL sur le même SHA exact :

```text
Head exact qualifié     a392604fc9e8d00f4021351ab5ba53f8488ab920
Windows                 PASS
Linux / WSL             PASS
Tests                   565 PASS Windows + Linux
Architecture            231 PASS Windows + Linux
Windows JaCoCo          42.9925% lignes / 36.3983% branches
Linux JaCoCo            42.9945% lignes / 36.3983% branches
Policy packs            PASS
Versioning / CAS        PASS
Overrides / provenance  PASS
Dry-run                 PASS
Explainability          PASS
SQLite V015             PASS
CycloneDX/provenance    PASS Windows + Linux
Portable                PASS Windows + Linux
Convergence             CLI/MCP/HTTP PASS
Executable delta        NONE Windows + Linux
ADR-0093                Acceptée — M25
PR                       #108 vers develop
CI / GitHub Actions     non utilisé — juillet 2026
```

La preuve détaillée est [`VALIDATION_M25.md`](VALIDATION_M25.md).

Le SHA exact qualifié reste distinct des commits documentaires de consolidation ajoutés ensuite. Une modification de code/POM/contrat runtime/OpenAPI/packaging/validator exige un nouveau gate exact-head Windows + Linux.