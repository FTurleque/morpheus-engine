# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans des jalons, consolidations et releases MORPHEUS.

## Autorité

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

Plan actif :

- [`M28_EXECUTION.md`](M28_EXECUTION.md) — MCP Client Integration & Installer Wiring, issue #115.

Dernière release terminée :

- [`R2_EXECUTION.md`](R2_EXECUTION.md) — MORPHEUS 1.1.0 publié.

## Baseline actuelle

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           🚧 intégration clients MCP
```

```text
stable tag             v1.1.0
stable release commit  31506029ded1101f0571edeb0d79c59bbf3f68c6
post-release baseline  8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop baseline M28   8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
M28 branch             m28-mcp-client-integration
M28 issue              #115
```

## NOW / BLOCKED / LATER

```text
NOW
  M28  câblage MCP Copilot / Claude / Codex

BLOCKED UNTIL EXACT-HEAD QUALIFICATION
  PR Ready
  merge vers develop
  clôture issue #115

LATER
  consolidation release 1.2.0
  tag v1.2.0 et artefacts exact-tag
  modernisation CI à partir d’août 2026
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

## Commandes M28

Windows :

```powershell
.\validate-m28.cmd -Version 1.1.0 -BaseRef origin/develop
```

Linux/WSL :

```bash
MORPHEUS_M28_BASE_REF=origin/develop bash ./scripts/validate-m28.sh 1.1.0
```

Les deux plateformes doivent qualifier le même SHA exact.

## Politique documentaire

Les plans terminés restent des archives factuelles. Les fichiers `VALIDATION_*.md` ne sont jamais réécrits pour fabriquer rétroactivement un PASS.

En juillet 2026, GitHub Actions n’est pas utilisé comme gate. Les sorties locales Windows + Linux/WSL exact-head sont autoritatives.
