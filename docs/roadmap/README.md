# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans d’exécution des jalons, consolidations et releases MORPHEUS.

## Autorité

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md). La trajectoire post-1.0 reste [`POST_M20_EVOLUTION.md`](POST_M20_EVOLUTION.md).

Le plan actif de stabilisation est :

- [`R2_EXECUTION.md`](R2_EXECUTION.md) — MORPHEUS 1.1.0, issue #113, PR draft #114.

## Baseline actuelle

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
M24           ✅ validé et intégré
M25           ✅ validé et intégré dans develop
M26           ✅ validé et intégré dans develop
M27           ✅ validé et intégré dans develop
R2            🚧 stabilisation et publication 1.1.0 en cours
```

```text
main                    0e37d85fc7efe9843094416898b6fbdbc45b7da4
develop                 bccc118dda6fd818cf801750187afa4ad10b96e4
release branch          r2-release-1.1.0
published version       1.0.0
target version          1.1.0
published tag           v1.0.0
target tag              v1.1.0
```

## NOW / BLOCKED / LATER

```text
NOW
  R2   stabilisation develop -> main et préparation MORPHEUS 1.1.0

BLOCKED UNTIL EXACT-HEAD QUALIFICATION
  merge PR #114
  tag v1.1.0
  exact-tag builds
  GitHub Release 1.1.0

LATER
  modernisation CI à partir d'août 2026
  prochain jalon fonctionnel à cadrer séparément
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

### Releases

- R1 — publication officielle 1.0.0, preuve dans [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md)
- [`R2_EXECUTION.md`](R2_EXECUTION.md) — candidate 1.1.0, preuve en construction dans [`../validation/VALIDATION_R2.md`](../validation/VALIDATION_R2.md)

## Commandes R2

Windows :

```powershell
.\validate-r2.cmd -Version 1.1.0
```

Linux/WSL :

```bash
bash ./scripts/validate-r2.sh 1.1.0
```

Les deux plateformes doivent qualifier le même SHA exact. Les builds de release exact-tag restent interdits avant merge et création autorisée de `v1.1.0`.

## Politique documentaire

Les plans terminés restent des archives enrichies par leur état d’intégration final. Les fichiers `VALIDATION_*.md` conservent les faits observés au moment des gates ; ils ne sont jamais réécrits pour fabriquer rétroactivement un PASS, un merge ou une publication.

En juillet 2026, GitHub Actions n'est pas utilisé comme gate R2 et `.github/workflows` n'est pas modifié opportunément. Les sorties locales Windows + Linux/WSL exact-head sont autoritatives.