# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans d’exécution des jalons MORPHEUS.

## État courant

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

La trajectoire active post-M14 est [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md).

État courant :

```text
D0   ✅ intégré
M15  ✅ intégré
M16  ✅ intégré
M17  ✅ intégré
M18  ✅ validé / intégré — PR #86
M19  ✅ validé techniquement — PR #89 non mergée
M20  ⏳ planifié, bloqué jusqu'au merge M19
```

Référence M18 :

```text
code validé = 7e8caacff567f51354fcb88bd7505a6d135071c0
merge       = 30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
tests       = 418/418 PASS
architecture= 170/170 PASS
packaging   = PASS
```

Les plans détaillés des jalons livrés restent disponibles comme historique d’exécution :

- [`D0_EXECUTION.md`](D0_EXECUTION.md)
- [`M15_EXECUTION.md`](M15_EXECUTION.md)
- [`M16_EXECUTION.md`](M16_EXECUTION.md)
- [`M17_EXECUTION.md`](M17_EXECUTION.md)
- [`M18_EXECUTION.md`](M18_EXECUTION.md)
- [`M19_EXECUTION.md`](M19_EXECUTION.md) — qualifié techniquement, non intégré

## Plans historiques C0→M18

Les fichiers `M*_EXECUTION.md` correspondant aux jalons déjà livrés sont des **archives d’exécution enrichies par leur état d’intégration final**. Ils conservent les SHA de code testés, les gates, les décisions ADR et les merges associés.

Pour l’état actuel :

```text
C0 → M18 = validés et intégrés
M19      = qualifié techniquement, PR #89 non mergée
M20      = après merge M19
```

Consulter [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

## Politique documentaire

La distinction entre documentation active, ADR normatives et preuves historiques est définie dans [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

Les fichiers `VALIDATION_M*.md` restent des **preuves historiques autoritatives** : leur résultat de gate n’est pas réécrit pour simuler un état post-merge. Une note post-merge peut être ajoutée explicitement, sans altérer la chronologie. Les roadmaps et index actifs doivent refléter l’état GitHub courant.

## Ordre de lecture recommandé

```text
ROADMAP globale
    ↓
POST_M14_EXECUTION
    ↓
M19_EXECUTION — exécution techniquement terminée
    ↓
VALIDATION_M19 — preuves Windows + Linux exact-head
```
