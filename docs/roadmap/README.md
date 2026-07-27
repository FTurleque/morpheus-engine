# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans d’exécution des jalons MORPHEUS.

## État courant

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

La trajectoire **active** après MORPHEUS 1.0 est :

- [`POST_M20_EVOLUTION.md`](POST_M20_EVOLUTION.md)

La trajectoire précédente est conservée comme historique :

- [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md) — D0 + M15→M20, cycle terminé.

Baseline :

```text
C0 → M20      ✅ validés et intégrés
D1            ✅ validé et intégré
M20 code      9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge     75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 merge      51f6a120f3461c8d8c24323f3db8211d28d6cb42
M20 tests     454/454 PASS Windows + Linux
Architecture  182/182 PASS Windows + Linux
MORPHEUS      1.0.0
v1.0.0        ✅ publié
GitHub Release ✅ publiée — 8/8 assets
```

## NOW / NEXT / LATER

```text
DONE
  R1   publication officielle v1.0.0 ✅
  D1   consolidation post-M20 (#94) ✅

NOW
  M21  Production Integrity & Surface Convergence

NEXT
  M22  Provider SDK & Plugin Discovery Platform
  M23  Multi-project / Portfolio Specification Intelligence
  M24  Query DSL, Saved Views & Export/Reporting

LATER
  M25  Policy Packs & Governance Automation
  M26  Optional Team/Remote Server Mode
  M27  Evidence-backed Assisted Reasoning
```

Le détail, les invariants, questions de sortie et exit criteria sont dans [`POST_M20_EVOLUTION.md`](POST_M20_EVOLUTION.md).

## Plans d’exécution

Plans conservés :

- [`D0_EXECUTION.md`](D0_EXECUTION.md)
- [`M15_EXECUTION.md`](M15_EXECUTION.md)
- [`M16_EXECUTION.md`](M16_EXECUTION.md)
- [`M17_EXECUTION.md`](M17_EXECUTION.md)
- [`M18_EXECUTION.md`](M18_EXECUTION.md)
- [`M19_EXECUTION.md`](M19_EXECUTION.md)
- [`M20_EXECUTION.md`](M20_EXECUTION.md)
- [`D1_EXECUTION.md`](D1_EXECUTION.md) — consolidation post-M20 terminée et intégrée.

Le plan `M21_EXECUTION.md` sera créé au lancement de M21.

Les plans terminés restent des **archives d’exécution enrichies par leur état d’intégration final**. Ils conservent les SHA testés, gates, ADR et merges associés.

## Preuves de release

- [`../validation/VALIDATION_M20.md`](../validation/VALIDATION_M20.md) — qualification technique M20 Windows + Linux ;
- [`../validation/VALIDATION_D1.md`](../validation/VALIDATION_D1.md) — consolidation documentaire D1 ;
- [`../validation/VALIDATION_R1.md`](../validation/VALIDATION_R1.md) — publication officielle `v1.0.0` et GitHub Release.

## Politique documentaire

La distinction entre documentation active, ADR normatives et preuves historiques est définie dans [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

Les fichiers `VALIDATION_M*.md` et `VALIDATION_R*.md` restent des preuves historiques autoritatives : ils ne sont jamais réécrits pour faire croire qu’un merge ou une publication existait au moment d’un gate antérieur. Les roadmaps et index actifs, eux, reflètent l’état GitHub courant.

## Ordre de lecture recommandé

```text
ROADMAP globale
    ↓
POST_M20_EVOLUTION — trajectoire active 1.x
    ↓
M21_EXECUTION — à créer au lancement de M21

Preuves 1.0 :
VALIDATION_M20 → VALIDATION_D1 → VALIDATION_R1

Historique :
POST_M14_EXECUTION → M15…M20
```
