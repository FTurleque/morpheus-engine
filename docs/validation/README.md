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
```

Chaque document conserve les décisions, SHA, commandes et résultats réellement observés. Une preuve historique n’est jamais réécrite pour fabriquer un PASS.

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
PR                      #118 MERGED
issue                   #117 CLOSED / completed
```

Preuve : [`VALIDATION_R3.md`](VALIDATION_R3.md).

## Release stable précédente

```text
version                 1.1.0
tag                     v1.1.0
release commit          31506029ded1101f0571edeb0d79c59bbf3f68c6
Windows/Linux tests     603 / 603 PASS
architecture            238 PASS sur les deux plateformes
published parity        8/8 PASS
```

Preuve : [`VALIDATION_R2.md`](VALIDATION_R2.md).

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

## M28 — qualification intégrée et publiée

```text
qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       PASS
same executable SHA    PASS
PR                     #116 MERGED
merge commit           1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
issue                  #115 CLOSED / completed
release                MORPHEUS 1.2.0
```

Preuve : [`VALIDATION_M28.md`](VALIDATION_M28.md).

M28 prouve :

```text
reactor non-regression
same SHA Windows/Linux
five client integrations
JSON merge preservation
CLI registration
idempotency
foreign entry preservation
modified entry preservation
state-driven uninstall
invalid JSON protection
portable Windows/Linux packaging
Windows setup wiring
Docker required = false
post-gate executable delta = NONE
```

## R3 — MORPHEUS 1.2.0 publié

```text
issue                  #117 CLOSED / completed
PR                     #118 MERGED
qualified executable   d08542026817f0d743766656a0197790c6809eca
main release commit    3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                    v1.2.0
Windows result         PASS
Linux/WSL result       PASS
same executable SHA    PASS
reactor version        PASS — 1.2.0 across 17 POMs
exact-tag Windows      PASS
exact-tag Linux        PASS
GitHub Release         PUBLISHED / stable / latest
published assets       8/8
published parity       8/8 PASS
```

R3 prouve :

```text
17 POM version parity at 1.2.0
M28 client integration retained
Windows portable + installer
Linux portable
SBOM + provenance
no SQLite migration delta
no GitHub Actions workflow delta
same exact SHA Windows/Linux
post-gate executable delta = NONE
immutable tag on exact main release commit
exact-tag Windows/Linux builds
published parity 8/8
```

Preuve finale : [`VALIDATION_R3.md`](VALIDATION_R3.md).

## Politique de gate

Toute modification de code, packaging, contrat runtime ou validateur exige un nouveau gate Windows + Linux/WSL sur le même SHA exact. Après qualification, seul un delta exclusivement documentaire est accepté sans réexécution, après comparaison explicite.

En juillet 2026 :

```text
no GitHub Actions gate
no workflow inspection
no workflow rerun
no workflow dispatch
no .github/workflows modification
local exact-head logs are authoritative
exact-tag builds are authoritative for release assets
published byte-for-byte parity is required
```