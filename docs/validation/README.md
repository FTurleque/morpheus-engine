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

## Évolutions 1.x intégrées avant M24

```text
M21  473 PASS Windows + Linux | Architecture 187
M22  494 PASS Windows + Linux | Architecture 190
M23  507 PASS Windows + Linux | Architecture 195
```

Preuves : [`VALIDATION_M21.md`](VALIDATION_M21.md), [`VALIDATION_M22.md`](VALIDATION_M22.md), [`VALIDATION_M23.md`](VALIDATION_M23.md).

## Dernier jalon techniquement qualifié

**M24 — Query DSL, Saved Views & Export/Reporting** est qualifié sur Windows et Linux sur le même SHA exécutable :

```text
Head exécutable       be69e47da0ae209d2246df9c67bc08caeafb2bb0
Windows               PASS
Linux WSL2            PASS
Tests                 543 PASS
Architecture          221 PASS
Windows JaCoCo        44.2936% lignes / 38.1166% branches
Linux JaCoCo          44.3037% lignes / 38.1166% branches
Query DSL             PASS
Saved views           PASS
SQLite V014           PASS
Canonical JSON        PASS
CSV                    PASS
Markdown               PASS
Query/export budgets  PASS
CycloneDX/provenance  PASS Windows + Linux
Portable              PASS Windows + Linux
Convergence           CLI/MCP/HTTP PASS
Executable delta      NONE Windows + Linux
ADR-0092              Acceptée — M24
PR                     #106
```

La preuve détaillée est [`VALIDATION_M24.md`](VALIDATION_M24.md).

Le SHA exécutable qualifié reste distinct des commits documentaires de consolidation ajoutés ensuite. Une modification de code/POM/contrat runtime/packaging/validator exige un nouveau gate exact-head Windows + Linux.