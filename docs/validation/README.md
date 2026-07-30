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
VALIDATION_M27.md  (NON QUALIFIÉ — dossier de gate en cours)
```

Chaque document conserve les décisions, SHA testés, commandes et gates connus au moment de la validation. Ils constituent des **preuves historiques** ; la documentation utilisateur et développeur active vit respectivement dans [`../user/`](../user/) et [`../developer/`](../developer/).

Un dossier marqué **NON QUALIFIÉ** décrit les preuves attendues et ne constitue pas une validation historique.

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

## Évolutions 1.x intégrées avant M26

```text
M21  473 PASS Windows + Linux | Architecture 187
M22  494 PASS Windows + Linux | Architecture 190
M23  507 PASS Windows + Linux | Architecture 195
M24  543 PASS Windows + Linux | Architecture 221
M25  565 PASS Windows + Linux | Architecture 231
```

Preuves : [`VALIDATION_M21.md`](VALIDATION_M21.md), [`VALIDATION_M22.md`](VALIDATION_M22.md), [`VALIDATION_M23.md`](VALIDATION_M23.md), [`VALIDATION_M24.md`](VALIDATION_M24.md), [`VALIDATION_M25.md`](VALIDATION_M25.md).

## Dernier jalon techniquement qualifié

**M26 — Optional Team / Remote Server Mode** est qualifié sur Windows et Linux/WSL sur le même SHA exact :

```text
Head exact qualifié     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
Windows                 PASS
Linux / WSL             PASS
Tests                   579 PASS Windows + Linux
Architecture            234 PASS Windows + Linux
Windows JaCoCo          44.3507% lignes / 37.8842% branches
Linux JaCoCo            44.3527% lignes / 37.8842% branches
Local-first             PASS
Remote TLS/auth/RBAC    PASS
Bounded concurrency     PASS / HTTP 429
Secret non-disclosure   PASS
Backup/restore          PASS
Schema compatibility    PASS / SQLite V015
CycloneDX/provenance    PASS Windows + Linux
Portable                PASS Windows + Linux
Surface convergence     PASS
Executable delta        NONE Windows + Linux
ADR-0094                Acceptée — M26
PR                      #110 vers develop
CI / GitHub Actions     non utilisé — juillet 2026
```

La preuve détaillée est [`VALIDATION_M26.md`](VALIDATION_M26.md).

## Jalon M27 en qualification

**M27 — Evidence-backed Assisted Reasoning** est implémenté sur la PR draft #112, mais n’est pas qualifié :

```text
Issue                    #111 OPEN
PR                       #112 OPEN / DRAFT
Tests attendus           >= 602
Architecture attendue    >= 238
Windows exact-head       NOT RUN
Linux / WSL exact-head   NOT RUN
ADR-0095                 PROPOSÉE
Merge                    BLOCKED
```

Le dossier de gate est [`VALIDATION_M27.md`](VALIDATION_M27.md). Il ne deviendra une preuve historique qu’après double qualification locale sur le même SHA et réconciliation des valeurs réelles.

Le SHA exact qualifié reste distinct des commits documentaires de consolidation ajoutés ensuite. Une modification de code/POM/contrat runtime/OpenAPI/packaging/validator exige un nouveau gate exact-head Windows + Linux.
