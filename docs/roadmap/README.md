# Plans d’exécution MORPHEUS

Ce répertoire conserve les plans d’exécution des jalons MORPHEUS.

## État courant

La source de vérité globale est [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

La trajectoire active après M14 est [`POST_M14_EXECUTION.md`](POST_M14_EXECUTION.md).

Le jalon documentaire en cours est [`D0_EXECUTION.md`](D0_EXECUTION.md).

## Plans historiques C0→M14

Les fichiers `M*_EXECUTION.md` correspondant aux jalons déjà livrés sont des **archives d’exécution**. Ils conservent volontairement l’état exact du gate au moment de leur rédaction : branche, PR, SHA, tests, décision de passage Ready et autorisation de merge.

Par conséquent, une phrase historique telle que :

```text
la PR reste non mergée jusqu’au signal explicite
```

ne signifie pas que la PR est encore ouverte aujourd’hui.

Pour l’état actuel :

```text
C0 → M14 = validés et intégrés
```

Consulter [`../governance/ROADMAP.md`](../governance/ROADMAP.md).

## Politique documentaire

La distinction entre documentation active, ADR normatives et preuves historiques est définie dans [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

## Ordre de lecture recommandé

```text
ROADMAP globale
    ↓
POST_M14_EXECUTION
    ↓
plan du jalon actif
    ↓
VALIDATION du jalon lorsque le gate est atteint
```
