# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans des jalons, consolidations et releases MORPHEUS.

## Autorité

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

Phase active :

- [`R3_EXECUTION.md`](R3_EXECUTION.md) — stabilisation et publication MORPHEUS 1.2.0, issue #117, PR draft #118.

Dernier jalon terminé :

- [`M28_EXECUTION.md`](M28_EXECUTION.md) — MCP Client Integration & Installer Wiring, issue #115 fermée, PR #116 mergée.

Dernière release terminée :

- [`R2_EXECUTION.md`](R2_EXECUTION.md) — MORPHEUS 1.1.0 publié.

## Baseline actuelle

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M28     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
R3            🚧 MORPHEUS 1.2.0 en préparation
```

```text
stable tag             v1.1.0
stable release commit  31506029ded1101f0571edb0d79c59bbf3f68c6
main post-release      8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop post-M28       2080c99895115464dafefb6515541666c5d972d8
M28 executable head    58adfeb13b79808da12830f2d0b0b24ec46f67e6
M28 merge commit       1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
R3 branch              r3-release-1.2.0
R3 issue               #117 OPEN
R3 PR                  #118 DRAFT
R3 target              1.2.0 / v1.2.0
```

## NOW / LATER

```text
NOW
  draft release PR #118
  Windows exact-head qualification
  Linux/WSL exact-head qualification on the same SHA

LATER
  merge toward main after all gates
  immutable v1.2.0 tag
  exact-tag Windows/Linux builds
  GitHub Release with eight verified assets
  post-publication reconciliation
  CI modernization from August 2026
```

## Plans d’exécution

### Cycle initial et 1.0

- [`D0_EXECUTION.md`](D0_EXECUTION.md)
- [`M15_EXECUTION.md`](M15_EXECUTION.md)
- [`M16_EXECUTION.md`](M16_EXECUTION.md)
- [`M17_EXECUTION.md`](M17_EXECUTION.md)
- [`M18_EXECUTION.md`](M18_EXECUTION.md)
- [`M19_EXECUTION.md`](M19_EXECUTION.md)
- [`M20_EXECUTION.md`](M20_EXECUTION.md)
- [`D1_EXECUTION.md`](D1_EXECUTION.md)

### Évolutions 1.x

- [`M21_EXECUTION.md`](M21_EXECUTION.md)
- [`M22_EXECUTION.md`](M22_EXECUTION.md)
- [`M23_EXECUTION.md`](M23_EXECUTION.md)
- [`M24_EXECUTION.md`](M24_EXECUTION.md)
- [`M25_EXECUTION.md`](M25_EXECUTION.md)
- [`M26_EXECUTION.md`](M26_EXECUTION.md)
- [`M27_EXECUTION.md`](M27_EXECUTION.md)
- [`M28_EXECUTION.md`](M28_EXECUTION.md)

### Releases

- R1 — MORPHEUS 1.0.0, preuve [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md)
- [`R2_EXECUTION.md`](R2_EXECUTION.md) — MORPHEUS 1.1.0, preuve [`../validation/VALIDATION_R2.md`](../validation/VALIDATION_R2.md)
- [`R3_EXECUTION.md`](R3_EXECUTION.md) — MORPHEUS 1.2.0 candidate, preuve [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)

## Qualification M28

```text
Windows exact-head      PASS
Linux/WSL exact-head    PASS
same executable SHA     PASS
qualified SHA           58adfeb13b79808da12830f2d0b0b24ec46f67e6
PR #116                 MERGED
issue #115              CLOSED / completed
```

## Qualification R3

```text
Windows exact-head      NOT RUN
Linux/WSL exact-head    NOT RUN
same executable SHA     NOT RUN
qualified SHA           NOT SET
PR #118                 DRAFT
issue #117              OPEN
release                 NOT PUBLISHED
```

## Politique documentaire

Les plans terminés restent des archives factuelles. En juillet 2026, GitHub Actions n’est pas utilisé comme gate ; les sorties locales Windows + Linux/WSL exact-head sont autoritatives.
