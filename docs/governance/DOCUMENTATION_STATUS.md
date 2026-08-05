# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.2.0 PUBLIÉ — D2 EN COURS**

Dernière mise à jour : 5 août 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/D2_EXECUTION.md
        ↓
docs/validation/VALIDATION_D2.md
        ↓
code + tests + logs exact-head locaux Windows/Linux
```

Pour la release stable déjà publiée :

```text
docs/validation/VALIDATION_R3.md
        ↓
v1.2.0 + exact-tag assets publiés
```

## Documentation active

```text
README.md
docs/README.md
docs/user/README.md
docs/user/INSTALLATION.md
docs/user/MCP_CLIENTS.md
docs/user/UPGRADE_1_2.md
docs/developer/README.md
docs/developer/BUILD_AND_TEST.md
docs/developer/MCP.md
distribution/README.md
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/D2_EXECUTION.md
docs/validation/VALIDATION_D2.md
docs/validation/VALIDATION_R3.md
docs/release/RELEASE_NOTES_1.2.0.md
integration/README.md
scripts/README.md
```

Les plans et preuves des jalons terminés restent des archives factuelles et ne sont pas réécrits pour adopter les commandes modernes.

## Release stable publiée

```text
Version                    1.2.0
Tag                        v1.2.0
Tag target                 3ad9ebf030b58df97482e21e272c24feae6b9d86
Qualified executable SHA   d08542026817f0d743766656a0197790c6809eca
PR                         #118 MERGED
Issue                      #117 CLOSED / completed
GitHub Release             stable / latest
Assets                     8/8 uploaded
Published parity           8/8 PASS
Exact-tag Windows          PASS
Exact-tag Linux            PASS
```

## D2 actif

```text
Issue                      #120 OPEN
Branch                     d2-post-r3-hardening
Goal                       post-R3 repository hardening
Stable product version     remains 1.2.0
CI                         NOT USED
Windows local gate         REQUIRED
Linux/WSL local gate       REQUIRED
Same exact SHA             REQUIRED
```

Hardening prévu/implémenté sur la branche :

```text
Jackson                    3.1.5 LTS
sqlite-jdbc                3.53.2.0
OWASP Dependency-Check     local explicit scan
coverage floors            40% lines / 35% branches
dependency hygiene         blocking
active docs                reconciled to 1.2.0
```

## Autorité des commandes Windows

Le dispatcher actif est :

```powershell
.\scripts\validate.cmd <target> [arguments]
```

Pour D2 :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Les anciens wrappers `validate-*.cmd` à la racine ne font plus partie de la documentation active.

## Politique D2 — aucune CI

```text
GitHub Actions inspection    NOT USED
workflow rerun/dispatch      NOT USED
.github/workflows mutation   FORBIDDEN
CI status as proof           FORBIDDEN
```

Les preuves D2 seront uniquement les sorties exact-head locales Windows + Linux/WSL sur le même SHA.

## État fonctionnel

```text
C0 → M28       ✅ validés / intégrés
D0 + D1        ✅ validés / intégrés
R1             ✅ 1.0.0 publié
R2             ✅ 1.1.0 publié
R3             ✅ 1.2.0 publié
D2             🚧 implementation / local qualification pending
```
