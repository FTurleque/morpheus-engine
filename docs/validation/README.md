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

## M28 — qualification complète

```text
baseline               8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
branch                 m28-mcp-client-integration
issue                  #115
PR                     #116
qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Windows result         PASS
Linux/WSL result       PASS
same executable SHA    PASS
review threads         0
blocking reviews       0
merge                  AUTHORIZED
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

Le gate Linux valide le reactor, les contrats statiques et le packaging Linux. Les mutations de profils clients sont qualifiées sur Windows uniquement.

## Politique de gate

Toute modification de code, packaging, contrat runtime ou validateur exige un nouveau gate Windows + Linux/WSL sur le même SHA exact. Après qualification, seul un delta exclusivement documentaire est accepté sans réexécution, après comparaison explicite.

En juillet 2026 :

```text
no GitHub Actions gate
no workflow rerun
no workflow dispatch
no .github/workflows modification
local exact-head logs are authoritative
```