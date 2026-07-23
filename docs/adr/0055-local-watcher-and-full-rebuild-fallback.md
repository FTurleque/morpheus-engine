# ADR-0055 — Watcher local conservateur et fallback full rebuild

- Statut : **Acceptée — M7**
- Date : 23 juillet 2026
- Acceptée : 24 juillet 2026
- Dépend de : ADR-0053, ADR-0054
- Portée : M7, WatchService local, invalidation et fallback

## Décision

MORPHEUS peut utiliser `java.nio.file.WatchService` comme optimisation locale, jamais comme source de vérité exclusive.

Le watcher produit des signaux normalisés :

```text
CREATE
MODIFY
DELETE
OVERFLOW
```

Les événements servent uniquement à déclencher/borner un nouveau scan d'inventaire. Le scan SHA-256 reste la preuve finale de l'état source.

Toute condition qui empêche de démontrer la sûreté de l'incrémental impose :

```text
SyncMode.FULL_REBUILD
```

Cas minimaux :

```text
aucune baseline
WatchService OVERFLOW
scan incomplet/échoué
move/rename ambigu
même sourceRevision mais inventaire différent
baseline incohérente
```

Le fallback est explicable par `FullRebuildReason`; il n'est jamais silencieux.

Le watcher est local-first et optionnel : un appel manuel de scan/sync doit fournir exactement les mêmes décisions que le watcher suivi d'un scan.

## Preuve d'acceptation

Gate local Windows final M7 exécuté sur le head :

```text
2e19ab104be18b98536eb871981d60e6b95e1e8c
```

Résultat :

```text
MORPHEUS Application  82/82 PASS
Architecture Tests   139/139 PASS
TOTAL                282/282 PASS
Failures               0
Errors                 0
Skipped                0
BUILD SUCCESS
Finished 2026-07-24T00:22:11+02:00
```

Les preuves couvrent le watcher local, `OVERFLOW`, les fallbacks conservateurs et l'absence de guessing. ADR acceptée après preuve.