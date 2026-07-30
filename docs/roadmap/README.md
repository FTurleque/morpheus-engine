# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans des jalons, consolidations et releases MORPHEUS.

## Autorité

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

Dernière release terminée :

- [`R3_EXECUTION.md`](R3_EXECUTION.md) — MORPHEUS 1.2.0 publié, issue #117 fermée, PR #118 mergée, parité publiée 8/8 PASS.

Dernier jalon terminé :

- [`M28_EXECUTION.md`](M28_EXECUTION.md) — MCP Client Integration & Installer Wiring, issue #115 fermée, PR #116 mergée, livré dans MORPHEUS 1.2.0.

## Baseline actuelle

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           ✅ validé, intégré et livré dans 1.2.0
R3            ✅ MORPHEUS 1.2.0 publié
```

```text
stable version           1.2.0
stable tag               v1.2.0
stable release commit    3ad9ebf030b58df97482e21e272c24feae6b9d86
qualified executable     d08542026817f0d743766656a0197790c6809eca
R3 PR                    #118 MERGED
R3 issue                 #117 CLOSED / completed
published assets         8/8
published parity         8/8 PASS
previous stable tag      v1.1.0
```

## NOW / LATER

```text
NOW
  synchronize develop with the stable 1.2.0 baseline before new product work
  select the next milestone from the governance roadmap

LATER
  future product milestones from develop
  future release branch toward main after dual-platform qualification
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
- [`R3_EXECUTION.md`](R3_EXECUTION.md) — MORPHEUS 1.2.0, preuve [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)

## Qualification R3

```text
Windows exact-head      PASS
Linux/WSL exact-head    PASS
same executable SHA     PASS
qualified SHA           d08542026817f0d743766656a0197790c6809eca
main release commit     3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                     v1.2.0
exact-tag builds        PASS Windows + Linux
PR #118                 MERGED
issue #117              CLOSED / completed
release                 PUBLISHED / stable / latest
published parity        8/8 PASS
```

## Politique documentaire

Les plans terminés restent des archives factuelles. En juillet 2026, GitHub Actions n’est pas utilisé comme gate ; les sorties locales Windows + Linux/WSL exact-head, les builds exact-tag et les contrôles de parité publiés sont autoritatifs.