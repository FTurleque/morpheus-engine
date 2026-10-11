# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans des jalons, consolidations et releases MORPHEUS.

## Autorité

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

Dernière consolidation terminée :

- [`D2_EXECUTION.md`](D2_EXECUTION.md) — Post-R3 Repository Hardening, issue #120 CLOSED / completed, PR #121 MERGED, qualification locale Windows + Linux/WSL sans CI.

Dernière release terminée :

- [`R3_EXECUTION.md`](R3_EXECUTION.md) — MORPHEUS 1.2.0 publié, issue #117 fermée, PR #118 mergée, parité publiée 8/8 PASS.

Dernier jalon fonctionnel terminé :

- [`M28_EXECUTION.md`](M28_EXECUTION.md) — MCP Client Integration & Installer Wiring, livré dans MORPHEUS 1.2.0.

## Baseline actuelle

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           ✅ validé / livré dans 1.2.0
R3            ✅ MORPHEUS 1.2.0 publié
D2            ✅ Post-R3 Repository Hardening qualifié et intégré
```

```text
stable version           1.2.0
stable tag               v1.2.0
stable release commit    3ad9ebf030b58df97482e21e272c24feae6b9d86
qualified executable     d08542026817f0d743766656a0197790c6809eca
published assets         8/8
published parity         8/8 PASS
D2 issue                 #120 CLOSED / completed
D2 PR                    #121 MERGED
D2 qualified exact head  fa54b3d6a316357b2ef79afd2243619a64a05f3b
```

## NOW — baseline corrective 1.2.1

Aucun jalon n'est actuellement en cours. La priorité active est de faire
progresser la qualité de la baseline développement `1.2.1` sans déclarer de
nouvelle release avant une vraie exécution du pipeline attesté :

```text
version développement       1.2.1
suivi actif                 #185 (qualification réelle du workflow de release attestée)
```

Voir [`../governance/ROADMAP.md`](../governance/ROADMAP.md) pour le détail à jour.

## Plans

### Consolidations

- [`D0_EXECUTION.md`](D0_EXECUTION.md)
- [`D1_EXECUTION.md`](D1_EXECUTION.md)
- [`D2_EXECUTION.md`](D2_EXECUTION.md)

### Cycle initial / 1.0

- [`M15_EXECUTION.md`](M15_EXECUTION.md)
- [`M16_EXECUTION.md`](M16_EXECUTION.md)
- [`M17_EXECUTION.md`](M17_EXECUTION.md)
- [`M18_EXECUTION.md`](M18_EXECUTION.md)
- [`M19_EXECUTION.md`](M19_EXECUTION.md)
- [`M20_EXECUTION.md`](M20_EXECUTION.md)

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

- R1 — MORPHEUS 1.0.0
- [`R2_EXECUTION.md`](R2_EXECUTION.md) — MORPHEUS 1.1.0
- [`R3_EXECUTION.md`](R3_EXECUTION.md) — MORPHEUS 1.2.0

## Qualification D2 (historique — plan livré, commandes conservées pour rejouer la preuve)

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux/WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

Les deux sorties doivent qualifier le même SHA. D2 interdit tout delta `.github/workflows/**` et n’utilise aucun résultat CI.
