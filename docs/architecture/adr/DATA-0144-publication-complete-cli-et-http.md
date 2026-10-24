---
id: DATA-0144
title: "Publication complète d'un snapshot, en CLI comme en HTTP"
domain: data
rules: false
---

# Publication complète d'un snapshot, en CLI comme en HTTP

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0126, ADR-0067

## Context

La détection de changement (ADR-0125) sait scanner les sources, produire un diff et planifier `INCREMENTAL` ou
`FULL_REBUILD`. Aucun exécuteur incrémental du graphe métier n'existe : la seule publication est complète
(`ProjectSnapshotImportService.publishFull`). Il faut donc décider comment publier un snapshot complet sans exposer
une baseline partielle, ce que devient une tentative qui échoue, et ce qu'un reçu de synchronisation annonce.

`SpecificationVersion` et `KnowledgeSnapshot` sont deux concepts distincts (ADR-0136) : le modèle permet à une
reconstruction technique de réutiliser une version métier existante. `publishFull` est une commande de publication
sémantique, pas un rafraîchissement technique transparent.

Une API headless qui ne permettrait que la lecture obligerait un opérateur à repasser par la CLI pour enregistrer et
synchroniser un workspace. L'API couvre donc le workflow headless minimal :

```text
register project
-> sync
-> query
```

La mutation de synchronisation HTTP ne doit toutefois pas inventer un exécuteur incrémental.

Options écartées pour le reçu d'une synchronisation CLI dont une source a changé :

- forcer toujours `FULL_REBUILD` côté CLI : supprime la synchronisation sans changement, et publie un snapshot et une
  `SpecificationVersion` à chaque appel ;
- garder `mode` comme étiquette de planification et ajouter un champ d'exécution : change un contrat public (CLI, état
  de fraîcheur, OpenAPI) pour un cas que le vocabulaire existant exprime, et garde un `mode` trompeur ;
- réutiliser la raison `FORCED` : elle dit « demandé par l'opérateur », ce qui est faux.

## Decision

### 1. Publication complète

`ProjectSnapshotImportService` (`morpheus-application`) reçoit un `NormalizedProjectContent` complet et publie un
nouveau snapshot par reconstruction complète, dans cet ordre :

```text
enregistrer le projet
résoudre le snapshot ACTIVE précédent et sa version
enregistrer le candidat KnowledgeSnapshot en BUILDING
allouer une séquence durable unique de SpecificationVersion
créer et persister la SpecificationVersion
lier le snapshot à la version
persister les RequirementVersionRecord CURRENT
persister le SnapshotBusinessContent
dériver et persister la traçabilité déterministe
valider les diagnostics normalisés
READY puis ACTIVE, atomiquement
```

Le candidat `BUILDING` est la première ancre durable : il précède l'allocation et la persistance de la version, de
sorte qu'aucune `SpecificationVersion` ne peut exister sans candidat observable. L'ancien snapshot reste `ACTIVE`
jusqu'à l'activation finale du nouveau, qui le fait passer à `RETIRED` dans la même opération (ADR-0143).

Diagnostics : un `ERROR` fait passer le candidat à `FAILED` et conserve l'ancien `ACTIVE` ; un `WARNING` ou aucun
diagnostic mène le candidat à `READY` puis `ACTIVE`. Un échec ne transforme jamais une ingestion partielle en
baseline publiée.

La cohérence de publication est une orchestration applicative : `ProjectSnapshotImportService` appartient à
`morpheus-application`. La CLI et le handler HTTP choisissent la source et le provider et rendent le reçu ; ils ne
définissent pas les transitions de snapshot.

### 2. Sémantique de version

Chaque exécution acceptée de `publishFull` crée une nouvelle `SpecificationVersionId` et consomme une nouvelle
séquence. Le modèle et les ports de persistance restent capables de lier plusieurs snapshots techniques à une même
`SpecificationVersionId` ; un flux de reprojection purement technique qui ne doit pas créer de version métier passe
par un contrat distinct qui reçoit explicitement la `SpecificationVersionId` réutilisée, jamais par `publishFull`.

La séquence est unique par projet pour toute `SpecificationVersion` qui en porte une ; SQLite l'impose par un index
unique `(project_id, sequence)`. La suivante vaut `max(séquence de toutes les tentatives durables) + 1`, jamais
`séquence de la version ACTIVE + 1`.

Un candidat qui a reçu une `SpecificationVersion` avant un échec reste durable en `FAILED` ; sa version n'est jamais
publiée et sa séquence reste consommée. Un nouvel essai n'en réutilise jamais la séquence, et son prédécesseur métier
est la version du snapshot précédemment `ACTIVE`. Exemple : séquence 1 `ACTIVE`, séquence 2 `FAILED`, séquence 3
`ACTIVE` après un nouvel essai, de prédécesseur 1. La séquence est un ordre durable de création, pas une chaîne de
prédécesseurs. Les artefacts d'un candidat `FAILED` restent conservés comme trace de tentative.

Identités : le reader OpenSpec utilise `PersistentEntityIdentityResolver` branché sur SQLite, donc les identités
scopées par provider restent stables d'une invocation et d'un snapshot à l'autre. Les `TraceabilityLinkId` des
observations dérivées sont des identités d'occurrence générées à chaque publication ; aucun hash sémantique n'est
utilisé.

### 3. Ce que dit le reçu

Le `mode` d'un reçu de synchronisation, et `lastSuccessfulMode` dans l'état de fraîcheur, disent ce qui a été exécuté,
jamais ce que le planificateur avait envisagé. Tant qu'aucun exécuteur incrémental du graphe n'existe, **aucune
publication ne s'annonce `INCREMENTAL`**.

CLI, commande `sync` :

| Situation | `mode` | `fullRebuildReason` | `published` |
|---|---|---|---|
| aucun snapshot actif, ou `--force` | `FULL_REBUILD` | `FORCED` | `true` |
| aucune source modifiée | `INCREMENTAL` | `none` | `false`, `snapshotId` du snapshot actif |
| une source modifiée | `FULL_REBUILD` | `INCREMENTAL_EXECUTOR_UNAVAILABLE` | `true` |

La raison `INCREMENTAL_EXECUTOR_UNAVAILABLE` vient de l'attribut de déclencheur
`SyncPlan.Trigger.withoutIncrementalExecutor()`, que seul le CLI pose. Le planificateur des autres appelants est
inchangé, et toute raison plus spécifique (`FORCED`, `NO_BASELINE`, `AMBIGUOUS_MOVE`…) garde la priorité.

`fullRebuildReason` est une chaîne libre dans l'OpenAPI (`SyncResult`), pas une énumération.

### 4. Synchronisation HTTP

L'API expose `POST /api/v1/projects/{projectId}/sync`, avec une `revision` source optionnelle. Le handler
(`MorpheusProjectSyncApiService`) réutilise les briques applicatives de la détection de changement et de la
publication complète :

```text
LocalSourceInventoryScanner
IncrementalSyncService
PersistentEntityIdentityResolver
OpenSpecProjectContentReader
ProjectSnapshotImportService
```

Le déclencheur est manuel et forcé : la synchronisation HTTP est une mutation explicite qui publie toujours en
complet, un **FULL_REBUILD conservateur**, et la réponse annonce le mode et sa raison. Le CLI et le HTTP diffèrent
donc quand rien n'a changé : le CLI ne publie pas, le HTTP publie. Cet écart est voulu.

Invariants :

```text
HTTP sync != RequirementDelta APPLY
HTTP sync != PROMOTE
HTTP sync != ACTIVATE direct
old ACTIVE kept until candidate validation succeeds
failed candidate never replaces ACTIVE
sync state complete only after successful publication
sync state fail on runtime failure
```

L'activation reste celle encapsulée dans `ProjectSnapshotImportService`, après BUILDING / VALIDATING / READY. Un scan
de sources incomplet marque l'état de synchronisation en échec et refuse la synchronisation sans rien publier.

### 5. Idempotence de l'enregistrement d'un projet

`POST /api/v1/projects` est idempotent par `SourceLocator` racine : une racine déjà enregistrée retourne le projet
existant au lieu de créer une seconde identité.

## Do's and Don'ts

### Do

- Enregistrer le candidat `BUILDING` avant d'allouer la séquence et de persister la `SpecificationVersion`.
- Garder l'ancien snapshot `ACTIVE` jusqu'à la dernière étape, tant que le candidat n'est pas validé, et faire de
  l'activation l'opération finale.
- Calculer la séquence suivante sur toutes les tentatives durables, `FAILED` comprises.
- Prendre comme prédécesseur d'un nouvel essai la version du snapshot précédemment `ACTIVE`.
- Écrire dans le reçu et dans `lastSuccessfulMode` le mode réellement exécuté.
- Annoncer `FULL_REBUILD` avec `INCREMENTAL_EXECUTOR_UNAVAILABLE` quand le CLI publie après un changement de source.
- Publier la synchronisation HTTP par full snapshot conservateur (`FULL_REBUILD`) et l'annoncer dans la réponse.
- Marquer l'état de synchronisation complet seulement après une publication réussie, et en échec sur toute
  défaillance.
- Retourner le projet existant quand sa racine est déjà enregistrée.

### Don't

- Ne pas réutiliser la séquence d'un candidat `FAILED`.
- Ne pas publier comme baseline un candidat dont la validation porte un diagnostic `ERROR`, ni laisser un candidat
  échoué remplacer l'`ACTIVE`.
- Ne pas détourner `publishFull` pour une reprojection technique qui ne doit pas créer de version métier.
- Ne pas annoncer `INCREMENTAL` pour une synchronisation qui a publié un snapshot, ni de reçu incrémental pour une
  synchronisation HTTP.
- Ne pas définir de transition de snapshot dans la CLI ou dans un adaptateur.
- Ne pas appliquer, promouvoir ni activer directement un `RequirementDelta` depuis la route de synchronisation.
- Ne pas utiliser de hash sémantique comme `TraceabilityLinkId`.
- Ne pas créer une seconde identité de projet pour une même racine.

## Consequences

### Positives

- Un nouvel essai normal après un échec tardif ne produit aucune collision `(projet, séquence)`.
- Les tentatives `FAILED` restent inspectables pour le diagnostic et la récupération.
- Un seul snapshot `ACTIVE` à tout instant ; la distinction version métier / snapshot technique reste dans le modèle.
- Une synchronisation échouée laisse la baseline publiée lisible.
- Un reçu ne ment pas sur l'exécution, sur le CLI comme sur le HTTP.
- Le workflow headless enregistrer, synchroniser, interroger est complet sans repasser par la CLI.

### Négatives

- Chaque publication réécrit tout le snapshot, même pour un seul fichier modifié.
- Les séquences publiées ne sont pas contiguës : un `FAILED` laisse un trou.
- Le CLI et le HTTP n'ont pas la même politique quand aucune source n'a changé.

### Risques

- Un script qui lit `mode` doit lire `published` pour savoir si un snapshot a été créé.
- L'asymétrie CLI/HTTP peut être prise pour une dérive : elle est tenue par un test commun aux deux surfaces.
- Lecture d'un résultat HTTP comme incrémental : la réponse porte le mode `FULL_REBUILD` et sa raison.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/ingestion/ProjectSnapshotImportServiceFailureOrderingTest.java` :
  ancre `BUILDING` avant la version, candidat marqué `FAILED`, séquence consommée avant une liaison qui échoue.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/FailedPublishSequenceContractTest.java` :
  un candidat `FAILED` consomme sa séquence et le nouvel essai garde le prédécesseur publié.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/FailedPublishRecoveryContractTest.java` :
  un candidat invalide n'atteint jamais `ACTIVE`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProjectSnapshotImportContractTest.java` :
  publication complète, remplacement atomique de l'`ACTIVE`, diagnostics bloquants, réouverture SQLite.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java`
  (`firstSyncBootstrapsThenRepeatedSyncsStayIncrementalUntilForceIsRequested`,
  `aSyncOfModifiedSourcesPublishesInFullAndSaysSo`) : reçus du CLI.
- `morpheus-application/src/test/java/com/morpheus/application/sync/SourceSynchronizationCoreTest.java`
  (`aCallerWithoutAnIncrementalExecutor…`, `aMoreSpecificReasonKeepsPrecedenceOverTheMissingIncrementalExecutor`) :
  planification sans exécuteur incrémental.
- `morpheus-architecture-tests/src/test/java/com/morpheus/cli/SyncReceiptSaysWhatRanOnEverySurfaceTest.java` : le
  mode publié suit l'exécution sur le CLI et sur le HTTP, sur une même base, asymétrie comprise.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusApiProjectSyncIntegrationTest.java` : enregistrement
  idempotent, synchronisation, requête puis réouverture SQLite (`registersSyncsQueriesAndReopensEntireHeadlessSurface`) ;
  une synchronisation échouée ne remplace jamais l'`ACTIVE` publié
  (`failedSyncNeverReplacesPreviouslyPublishedActiveSnapshot`).
- `contracts/public-surfaces.tsv`, ligne `project.sync` : surface HTTP et CLI, `EXPLICITLY_NOT_EXPOSED` côté MCP.
- Revue : aucun flux de reprojection technique n'appelle `publishFull` ; déclencheur forcé et absence d'exécuteur
  incrémental dans le handler HTTP.

## References

- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — version métier et snapshot technique.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — cycle de vie des snapshots et activation
  atomique.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des exigences.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas,
  distinctes de la synchronisation.
- [ADR-0039](BE-0039-deterministic-traceability-derivation.md) — dérivation déterministe de la traçabilité.
- [ADR-0125](DATA-0125-detection-de-changement-des-sources.md) — inventaire, diff et état de synchronisation.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur distant.
- [VALIDATION_M9](../../project/validation/VALIDATION_M9.md), [VALIDATION_M11](../../project/validation/VALIDATION_M11.md).
