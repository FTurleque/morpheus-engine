# ADR-0067 — Synchronisation HTTP explicite par full snapshot conservateur

> **Archivée.** Cette décision est consolidée dans [ADR-0144](../DATA-0144-publication-complete-cli-et-http.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0144**
- Date : 2026-07-24

## Context

Une API headless qui ne permettrait que la lecture obligerait un opérateur à repasser par la CLI pour enregistrer et
synchroniser un workspace. L'API couvre donc le workflow headless minimal :

```text
register project
-> sync
-> query
```

La mutation de synchronisation ne doit toutefois pas inventer un exécuteur incrémental.

## Decision

L'API expose :

```text
POST /api/v1/projects/{projectId}/sync
```

avec une `revision` source optionnelle.

Le handler (`MorpheusProjectSyncApiService`) réutilise les briques applicatives de la détection de changement et de la
publication complète :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService (publication complète, ADR-0126)
```

Le déclencheur est manuel et forcé : la publication exécutée est toujours un **FULL_REBUILD conservateur**, et la
réponse annonce le mode et sa raison.

### Invariants

```text
HTTP sync != RequirementDelta APPLY
HTTP sync != PROMOTE
HTTP sync != ACTIVATE direct
old ACTIVE kept until candidate validation succeeds
failed candidate never replaces ACTIVE
sync state complete only after successful publication
sync state fail on runtime failure
```

L'activation reste celle encapsulée dans `ProjectSnapshotImportService`, après BUILDING / VALIDATING / READY. Un scan de
sources incomplet marque l'état de synchronisation en échec et refuse la synchronisation sans rien publier.

### Idempotence projet

`POST /api/v1/projects` est idempotent par `SourceLocator` racine : une racine déjà enregistrée retourne le projet
existant au lieu de créer une seconde identité.

## Do's and Don'ts

### Do

- Publier par full snapshot conservateur (`FULL_REBUILD`) et l'annoncer dans la réponse.
- Garder l'ancien `ACTIVE` tant que le candidat n'est pas validé.
- Marquer l'état de synchronisation complet seulement après une publication réussie, et en échec sur toute
  défaillance.
- Retourner le projet existant quand sa racine est déjà enregistrée.

### Don't

- Ne pas annoncer de receipt incrémental pour une synchronisation HTTP.
- Ne pas appliquer, promouvoir ni activer directement un `RequirementDelta` depuis cette route.
- Ne pas laisser un candidat échoué remplacer l'`ACTIVE`.
- Ne pas créer une seconde identité de projet pour une même racine.

## Consequences

### Positives

- Le workflow headless enregistrer, synchroniser, interroger est complet sans repasser par la CLI.
- Une synchronisation échouée laisse la baseline publiée lisible.

### Négatives

- Chaque synchronisation HTTP reconstruit le snapshot complet, même quand peu de sources ont changé.

### Risques

- **Lecture du résultat comme incrémental** : mitigé, la réponse porte le mode `FULL_REBUILD` et sa raison.

## Compliance and Enforcement

- `morpheus-api/src/test/java/com/morpheus/api/MorpheusApiProjectSyncIntegrationTest.java` : enregistrement,
  synchronisation, requête puis réouverture SQLite (`registersSyncsQueriesAndReopensEntireHeadlessSurface`) ; une
  synchronisation échouée ne remplace jamais l'`ACTIVE` publié (`failedSyncNeverReplacesPreviouslyPublishedActiveSnapshot`).
- `contracts/public-surfaces.tsv`, ligne `project.sync` : surface HTTP et CLI, `EXPLICITLY_NOT_EXPOSED` côté MCP.
- Revue : déclencheur forcé et absence d'exécuteur incrémental dans le handler.

## References

- [ADR-0125](../DATA-0125-detection-de-changement-des-sources.md) — inventaire des sources, état de synchronisation et
  repli sur reconstruction complète.
- [ADR-0126](0126-publication-complete-d-un-snapshot.md) — publication complète d'un snapshot et receipt de
  synchronisation.
- [ADR-0035](../BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas,
  distinctes de la synchronisation.
- [VALIDATION_M11](../../../project/validation/VALIDATION_M11.md) — preuve du jalon M11.
