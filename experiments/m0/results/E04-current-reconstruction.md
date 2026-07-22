# E04 — Current reconstruction

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut reconstruire une baseline `CURRENT` sans y fusionner silencieusement des changements actifs concurrents ni des deltas historiques.

## Dataset

```text
experiments/m0/fixtures/openspec-state-matrix
```

Le corpus contient :

```text
CURRENT baseline
  auth-session/session-expiration = 30 minutes

PROPOSED change A
  extend-timeout -> 60 minutes

PROPOSED change B
  shorten-timeout -> 15 minutes

HISTORICAL archived change
  completed-legacy-mode -> ajoute legacy-mode dans son delta historique uniquement
```

## Spike

```text
experiments/m0/spikes/e04_current_reconstruction_python/test_current_reconstruction.py
```

Le test réutilise uniquement le normaliseur expérimental E01/E02 ; aucune nouvelle stack n'est introduite.

## Protocole exécuté

```text
python -m unittest -v
```

Résultat :

```text
Ran 4 tests
4 PASS
0 FAIL
```

## Résultats

### La baseline reste CURRENT

La requête normalisée conserve :

```text
auth-session/session-expiration
TemporalState = CURRENT
statement = 30 minutes
```

Aucun delta actif n'est appliqué pendant l'ingestion.

### Deux changements concurrents restent distincts

Les changements :

```text
extend-timeout
shorten-timeout
```

modifient la même clé logique :

```text
auth-session/session-expiration
```

mais restent deux branches `PROPOSED` différentes avec deux contenus différents.

MORPHEUS ne les fusionne pas et ne choisit pas arbitrairement un gagnant.

### L'historique reste requêtable

Le changement archivé :

```text
completed-legacy-mode
```

est exposé comme :

```text
TemporalState = HISTORICAL
```

avec son requirement historique :

```text
auth-session/legacy-mode
```

### Archive != promotion CURRENT

`auth-session/legacy-mode` n'apparaît pas dans la baseline `CURRENT` de cette fixture.

Cela prouve l'invariant :

> **le fait qu'un changement soit terminé ou archivé ne suffit pas, à lui seul, à prouver que son delta a été promu dans la baseline courante.**

La promotion de baseline doit être observée dans la source courante ou établie par une preuve explicite.

## Ce que E04 démontre

- [x] `CURRENT` exclut les deltas actifs ;
- [x] plusieurs changements `PROPOSED` peuvent modifier la même exigence sans fusion silencieuse ;
- [x] `HISTORICAL` reste requêtable ;
- [x] un changement archivé ne devient pas automatiquement `CURRENT` ;
- [x] aucune résolution de conflit n'est inventée par le moteur.

## Impact ADR-0006

**Preuve positive forte.**

Les trois états temporels :

```text
CURRENT
PROPOSED
HISTORICAL
```

sont maintenant exercés sur un corpus comportant plusieurs changements concurrents.

ADR-0006 reste `Proposée` jusqu'à validation conjointe avec les snapshots et la persistance, mais son invariant principal est confirmé.

## Impact ADR-0013

E04 confirme uniquement une frontière importante du lifecycle :

```text
COMPLETED / ARCHIVED != promotion automatique en CURRENT
```

La machine d'état complète et les étapes facultatives restent à tester séparément ; ADR-0013 reste `Proposée`.

## Décision

```text
E04 = PASS
CURRENT_RECONSTRUCTION = RETAIN
SILENT_PROPOSED_MERGE = REJECT
ARCHIVE_IMPLIES_PROMOTION = REJECT
```

La prochaine étape logique est E05 — `KnowledgeSnapshot` et activation atomique observable.
