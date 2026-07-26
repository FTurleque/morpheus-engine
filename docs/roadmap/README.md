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
M18  ✅ intégré — PR #86
M19  ⏭ prochain jalon
```

Les plans détaillés des jalons livrés restent disponibles comme historique d’exécution :

- [`D0_EXECUTION.md`](D0_EXECUTION.md)
- [`M15_EXECUTION.md`](M15_EXECUTION.md)
- [`M16_EXECUTION.md`](M16_EXECUTION.md)
- [`M17_EXECUTION.md`](M17_EXECUTION.md)
- [`M18_EXECUTION.md`](M18_EXECUTION.md)

## Plans historiques C0→M18

Les fichiers `M*_EXECUTION.md` correspondant aux jalons déjà livrés sont des **archives d’exécution enrichies par leur état d’intégration final**. Ils conservent les SHA de code testés, les gates, les décisions ADR et les merges associés.

Pour l’état actuel :

```text
C0 → M18 = validés et intégrés
M19      = prochain
```

Consulter [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

## Politique documentaire

La distinction entre documentation active, ADR normatives et preuves historiques est définie dans [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

Les fichiers `VALIDATION_M*.md` restent des **preuves historiques autoritatives** : ils ne sont pas réécrits pour simuler un état post-merge. Les roadmaps et index actifs, eux, doivent refléter l’état GitHub courant. Une note post-merge distincte peut contextualiser une preuve sans changer le gate enregistré.

## Ordre de lecture recommandé

```text
ROADMAP globale
    ↓
POST_M14_EXECUTION
    ↓
plan du prochain jalon (M19)
    ↓
VALIDATION du jalon lorsque le gate est atteint
```
