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

MORPHEUS **1.1.0** a été consolidé via R2.

```text
main merge             31506029ded1101f0571edeb0d79c59bbf3f68c6
tag                    v1.1.0
Windows tests          603 PASS
Linux/WSL tests        603 PASS
architecture           238 PASS sur les deux plateformes
exact-tag builds       PASS Windows + Linux
GitHub Release         stable / 8 assets
published parity       8/8 PASS
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
```

## M28 — qualification intégrée

```text
baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       PASS
same executable SHA    PASS
PR                     #116 MERGED
merge commit           1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
issue                  #115 CLOSED / completed
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

## R3 — candidate MORPHEUS 1.2.0

```text
issue                  #117 OPEN
branch                 r3-release-1.2.0
main baseline          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop baseline       2080c99895115464dafefb6515541666c5d972d8
target version         1.2.0
target tag             v1.2.0
Windows result         NOT RUN
Linux/WSL result       NOT RUN
same executable SHA    NOT RUN
PR                     NOT CREATED
GitHub Release         NOT PUBLISHED
```

R3 doit prouver :

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
exact-tag Windows/Linux builds
published parity 8/8
```

Preuve active : [`VALIDATION_R3.md`](VALIDATION_R3.md).

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
```
