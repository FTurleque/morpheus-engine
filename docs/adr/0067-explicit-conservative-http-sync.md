# ADR-0067 — Synchronisation HTTP explicite par full snapshot conservateur

- Statut : **Acceptée — M11**
- Date : 24 juillet 2026
- Dépend de : ADR-0053, ADR-0054, ADR-0055, ADR-0060
- Portée : M11 — mutation headless de synchronisation

## Contexte

Une API headless qui ne permettrait que la lecture obligerait un opérateur à repasser par la CLI pour enregistrer et synchroniser un workspace. M11 doit donc couvrir le workflow headless minimal :

```text
register project
-> sync
-> query
```

La mutation de synchronisation ne doit toutefois pas inventer un exécuteur incrémental que M9 n'a pas encore validé.

## Décision

Exposer :

```text
POST /api/v1/projects/{projectId}/sync
```

avec une `revision` source optionnelle.

Le handler réutilise exactement les briques applicatives M7/M9 :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService
```

La publication exécutée est forcée en **FULL_REBUILD conservateur**.

## Invariants

```text
HTTP sync != RequirementDelta APPLY
HTTP sync != PROMOTE
HTTP sync != ACTIVATE direct
old ACTIVE kept until candidate validation succeeds
failed candidate never replaces ACTIVE
sync state complete only after successful publication
sync state fail on runtime failure
```

L'activation reste celle encapsulée dans `ProjectSnapshotImportService`, après BUILDING/VALIDATING/READY.

## Idempotence projet

`POST /api/v1/projects` est idempotent par `SourceLocator` racine. Une racine déjà enregistrée retourne le projet existant au lieu de créer une seconde identité.

## Critères d'acceptation

1. projet enregistré via HTTP ;
2. fixture OpenSpec synchronisée via HTTP ;
3. résultat annonce `FULL_REBUILD` ;
4. snapshot ACTIVE publié ;
5. état sync persiste en SQLite ;
6. queries HTTP lisent la nouvelle baseline après reopen ;
7. échec de sync ne remplace pas l'ACTIVE précédent ;
8. aucun faux receipt incrémental.

## Preuve d'acceptation — 24 juillet 2026

`MorpheusApiProjectSyncIntegrationTest` passe **2/2** sur le head `a7daa9bb7eef1799926ea20b9e96606a388a301f`.

Le premier scénario prouve :

```text
register -> FULL_REBUILD sync -> query -> close SQLite -> reopen -> query
```

Le second publie une baseline, rend ensuite le workspace OpenSpec invalide, force un nouveau sync et vérifie :

```text
failed sync
-> previous ACTIVE unchanged
-> no extra RETIRED snapshot
-> previous requirements still readable
```

Gate complet : **314/314 PASS**, aucune failure/error/skipped.

Décision : **ADR-0067 ACCEPTÉE — M11**. Voir `docs/VALIDATION_M11.md`.