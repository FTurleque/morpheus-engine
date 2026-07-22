# E11 — Incremental ingestion

Statut : **PASS sur la détection/invalidation — watcher non requis**

Date : 22 juillet 2026

## Objectif

Valider une stratégie déterministe minimale de détection des changements de sources avant toute décision de watcher ou d'indexation incrémentale complexe.

## Spike

```text
experiments/m0/spikes/e11_incremental_ingestion_python/
```

## Résultat

```text
Ran 8 tests
8 PASS
0 FAIL
```

## Capacités validées

- fichier inchangé ;
- ajout ;
- modification ;
- suppression ;
- renommage exact détecté par SHA-256 ;
- déplacement + modification **non** présenté silencieusement comme simple rename ;
- exclusions par suffixe ;
- invalidation des entités à partir de la provenance source.

## Stratégie expérimentée

```text
old inventory
     +
new inventory
     ↓
SHA-256 / path comparison
     ↓
ADDED / MODIFIED / REMOVED / RENAMED / UNCHANGED
     ↓
provenance index
     ↓
affected domain entities
```

Un rename n'est reconnu automatiquement que lorsque le hash permet une correspondance univoque 1→1.

Un déplacement avec contenu modifié reste :

```text
REMOVED + ADDED
```

jusqu'à ce qu'une preuve d'identité plus forte permette de le rapprocher via E03.

## Décision

```text
E11 = PASS
FILE_FINGERPRINT_INCREMENTAL_BASE = RETAIN
WATCHER_REQUIRED_FOR_MVP = NO
```

Le watcher reste différé. Une ingestion complète doit rester le fallback de sécurité.
