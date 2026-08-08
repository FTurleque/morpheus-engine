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
VALIDATION_M21.md → VALIDATION_M28.md
VALIDATION_R2.md
VALIDATION_R3.md
VALIDATION_D2.md   ← actif / en attente de qualification locale
```

Chaque preuve historique conserve les décisions, SHA, commandes et résultats réellement observés. Une preuve historique n’est jamais réécrite pour fabriquer un PASS.

## Baseline stable publiée

MORPHEUS **1.2.0** a été consolidé via R3.

```text
qualified executable    d08542026817f0d743766656a0197790c6809eca
main release commit     3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                     v1.2.0
Windows tests           608 PASS
Linux/WSL tests         608 PASS
architecture            243 PASS sur les deux plateformes
same SHA                PASS
exact-tag builds        PASS Windows + Linux
GitHub Release          stable / latest / 8 assets
published parity        8/8 PASS
```

Preuve : [`VALIDATION_R3.md`](VALIDATION_R3.md).

## D2 — Post-R3 Repository Hardening

Statut : **PENDING LOCAL QUALIFICATION**.

```text
issue                   #120
branch                  d2-post-r3-hardening
stable product          remains 1.2.0
Jackson                 3.1.5 LTS
sqlite-jdbc             3.53.2.0
SCA                     OWASP Dependency-Check 12.2.2 local
coverage floors         40% line / 35% branch
dependency hygiene      blocking
CI                      NOT USED
```

Gates canoniques :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

D2 ne sera marqué PASS qu’après exécution des deux gates sur le même SHA exact. Les validateurs refusent tout delta `.github/workflows/**`.

Preuve en cours : [`VALIDATION_D2.md`](VALIDATION_D2.md).

## Évolutions 1.x qualifiées

```text
M21  473 tests | architecture 187
M22  494 tests | architecture 190
M23  507 tests | architecture 195
M24  543 tests | architecture 221
M25  565 tests | architecture 231
M26  579 tests | architecture 234
M27  602 tests | architecture 238
R2   603 tests | architecture 238
M28  608 tests | architecture 243
R3   608 tests | architecture 243
```

Les comptes D2 seront renseignés uniquement après le gate réel.

## Politique de preuve D2

```text
GitHub Actions inspection    NOT USED
workflow rerun/dispatch      NOT USED
CI result as gate            FORBIDDEN
.github/workflows delta      MUST BE NONE
Windows local exact-head     REQUIRED
Linux/WSL local exact-head   REQUIRED
same SHA                     REQUIRED
post-gate executable delta   MUST BE NONE
```
