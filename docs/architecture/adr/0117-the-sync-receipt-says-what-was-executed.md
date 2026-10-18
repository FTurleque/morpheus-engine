# ADR-0117 — Le reçu d'un sync dit ce qui a été exécuté

- Statut : **Acceptée** (18 octobre 2026, par le mainteneur du dépôt)
- Date : 18 octobre 2026
- Remplace en partie : ADR-0060 (section « Sync M7 » seulement ; la séquence de publication, les diagnostics et la
  précision du 19 août 2026 restent en vigueur)
- Dépend de : ADR-0053 (inventaire et diff conservateur), ADR-0054 (état de synchronisation et fraîcheur), ADR-0060
  (publication complète d'un snapshot), ADR-0067 (sync HTTP explicite), ADR-0097 (sémantique d'une publication complète)
- Portée : `morpheus-cli` (commande `sync`), `IncrementalSyncService`, l'état de fraîcheur (`lastSuccessfulMode`) ;
  lecture seule pour l'API HTTP, qui n'est pas modifiée

> Acceptée le 18 octobre 2026 avec une question ouverte, la raison de reconstruction d'une source modifiée. Le correctif
> du CLI l'a tranchée (voir « Raison de reconstruction ») ; ce choix est à confirmer à la revue de ce correctif.

## Contexte

ADR-0060 (24 juillet 2026) demandait au lanceur officiel de forcer l'exécution CLI de `sync` en `FULL_REBUILD`, pour que
`SyncPlan.mode` soit égal à l'exécution réelle et qu'aucun reçu n'annonce un faux `INCREMENTAL`. Aucun exécuteur
incrémental du graphe métier n'existait, et il n'existe toujours que la publication complète
(`ProjectSnapshotImportService.publishFull`).

Le commit `e059e637` (2 septembre 2026) a retiré l'injection de `--force` du lanceur : elle forçait chaque sync et rendait
inatteignable le chemin « aucune source n'a changé ». `MorpheusCli.sync` force déjà le déclencheur quand `--force` est
donné ou quand le projet n'a aucun snapshot actif. Aucun ADR n'a suivi ce changement.

Ce que le CLI fait aujourd'hui (constaté le 18 octobre 2026 par un test exécuté, non conservé, sur la fixture
`openspec-basic`) :

| Situation | `mode` | `fullRebuildReason` | `published` | `lastSuccessfulMode` |
|---|---|---|---|---|
| premier sync (aucun snapshot actif) | `FULL_REBUILD` | `FORCED` | `true` | `FULL_REBUILD` |
| aucune source modifiée | `INCREMENTAL` | aucune | `false` | `INCREMENTAL` |
| `--force` | `FULL_REBUILD` | `FORCED` | `true` | `FULL_REBUILD` |
| une source modifiée | **`INCREMENTAL`** | aucune | **`true`** | **`INCREMENTAL`** |

La dernière ligne est le défaut : le CLI publie un nouveau snapshot complet (`ProjectSnapshotImportService.publishFull`,
`MorpheusCli.java`, branche qui suit le cas « aucune source modifiée ») mais le reçu et l'état de fraîcheur disent
`INCREMENTAL`, ce que l'invariant « no fake INCREMENTAL receipt » d'ADR-0060 interdisait. Le modèle admet pourtant cet état :
`IncrementalSyncService.publicationExpected` considère qu'un plan `INCREMENTAL` avec des changements de source attend une
publication. L'API HTTP, elle, force toujours `FULL_REBUILD` (`MorpheusProjectSyncApiService`, `Trigger.manual().forced()`),
conformément à ADR-0067.

Aucun test ne couvre « une source est modifiée, puis `sync` » : `MorpheusCliTest` fige les trois cas premier sync, aucune
modification et `--force`.

## Décision

1. **Le `mode` d'un reçu, et `lastSuccessfulMode`, disent ce qui a été exécuté**, jamais ce que le planificateur avait
   envisagé. Tant qu'aucun exécuteur incrémental du graphe n'existe, **aucune publication ne s'annonce `INCREMENTAL`**.
2. **Aucune source modifiée : le sync est un no-op.** Le reçu dit `INCREMENTAL`, `published=false`, le `snapshotId` est
   celui du snapshot actif, aucun snapshot n'est créé. C'est le comportement voulu par `e059e637`, conservé.
3. **Une source modifiée : le CLI publie en complet et le reçu dit `FULL_REBUILD`**, avec une raison de reconstruction.
   La raison est `INCREMENTAL_EXECUTOR_UNAVAILABLE` (voir « Raison de reconstruction »).
4. **L'API HTTP ne change pas.** `POST /api/v1/projects/{projectId}/sync` reste une mutation explicite qui publie toujours
   en complet (ADR-0067). Le CLI et le HTTP n'ont donc pas la même politique quand rien n'a changé : le CLI ne publie pas,
   le HTTP publie. Cet écart est voulu et nommé ici pour ne pas être redécouvert comme une dérive.
5. **La planification n'est pas modifiée.** `IncrementalSyncService` garde son planificateur et son diff ; seul l'exécuteur
   du CLI aligne le mode qu'il enregistre sur ce qu'il a fait.

## Raison de reconstruction pour cause de source modifiée

`FullRebuildReason` comptait dix valeurs (`NO_BASELINE`, `SCAN_INCOMPLETE`, `WATCH_OVERFLOW`, `AMBIGUOUS_MOVE`,
`REVISION_INCONSISTENCY`, `REVISION_SIGNAL_LOST`, `BASELINE_INCONSISTENT`, `PREVIOUS_REBUILD_PENDING`, `EXECUTION_FAILED`,
`FORCED`). Aucune ne disait « une source a changé et aucun exécuteur incrémental n'existe ». Deux issues avaient été
posées :

- **Réutiliser `FORCED`.** Aucun nouveau terme, mais `FORCED` dit « demandé par l'opérateur » et ce n'est pas le cas.
- **Ajouter une valeur.**

Le correctif du CLI retient la seconde : **`INCREMENTAL_EXECUTOR_UNAVAILABLE`**. Vérifié le 18 octobre 2026 : le champ
`fullRebuildReason` est une chaîne libre dans l'OpenAPI (`SyncResult`, « The name of the full-rebuild reason, or none »),
et non une énumération ; la valeur ne change donc ni l'OpenAPI ni `contracts/public-surfaces.tsv`. Elle apparaît dans la
sortie CLI, dans `pendingFullRebuildReason` de l'état de fraîcheur tant qu'une publication est en attente.
Elle vient d'un attribut du déclencheur, `Trigger.withoutIncrementalExecutor()`, que seul le CLI pose : le planificateur
des autres appelants est inchangé, et toute raison plus spécifique (`FORCED`, `NO_BASELINE`, `AMBIGUOUS_MOVE`…) garde la
priorité.

## Alternatives

Analyse du 18 octobre 2026 ; ce ne sont pas des options étudiées à l'époque d'ADR-0060.

- **Revenir au texte d'ADR-0060 : le CLI force toujours `FULL_REBUILD`.** Le plus simple et le plus proche de l'invariant
  d'origine. Il supprime le sync sans changement que `e059e637` a rendu atteignable et fait publier un snapshot et une
  `SpecificationVersion` à chaque appel. Écarté tant que ce choix du 2 septembre n'est pas retiré par une personne.
- **Garder `mode` comme étiquette de planification et ajouter un champ d'exécution.** Plus expressif. Il change un contrat
  public (CLI, état de fraîcheur, OpenAPI) pour un cas que le vocabulaire existant exprime, et il garde un `mode` que
  l'invariant d'origine tenait pour trompeur.

## Conséquences

- Un script qui lisait `mode=INCREMENTAL` après une modification de source lira `FULL_REBUILD`. Le comportement observable
  de la publication ne change pas, seule l'étiquette change.
- `sync-status` rend `lastSuccessfulMode=FULL_REBUILD` après un sync de sources modifiées ; la fraîcheur ne change pas.
- Une valeur de plus dans les raisons de reconstruction (`INCREMENTAL_EXECUTOR_UNAVAILABLE`), sans effet sur l'OpenAPI.
- Le CLI et le HTTP restent asymétriques quand rien n'a changé.

## Vérification

| Contrainte | Contrôle | Couverture |
|---|---|---|
| aucune source modifiée : `INCREMENTAL`, `published=false`, même `snapshotId` | `MorpheusCliTest#firstSyncBootstrapsThenRepeatedSyncsStayIncrementalUntilForceIsRequested` | existe |
| premier sync et `--force` : `FULL_REBUILD`, `FORCED` | même test | existe |
| une source modifiée : `FULL_REBUILD`, `INCREMENTAL_EXECUTOR_UNAVAILABLE` et `lastSuccessfulMode=FULL_REBUILD` | `MorpheusCliTest#aSyncOfModifiedSourcesPublishesInFullAndSaysSo` | existe |
| le planificateur : source modifiée → `FULL_REBUILD` pour un appelant sans exécuteur incrémental, inchangé sinon, raison plus spécifique prioritaire | `SourceSynchronizationCoreTest` (3 tests `aCallerWithoutAnIncrementalExecutor…`, `aMoreSpecificReason…`) | existe |
| le HTTP force toujours `FULL_REBUILD` | `MorpheusApiProjectSyncIntegrationTest` | existe |
| le mode publié par le CLI et par le HTTP suit l'exécution réelle | **aucun test commun aux deux** ; le HTTP force toujours, `MorpheusApiProjectSyncIntegrationTest` | à écrire si l'asymétrie CLI/HTTP doit être gardée |

Commande : `./mvnw test -pl morpheus-cli,morpheus-api -am`. Revue humaine : le choix de la raison de reconstruction.

## Traçabilité

- Remplace en partie : [ADR-0060](0060-conservative-full-snapshot-cli-sync.md), section « Sync M7 ». ADR-0060 porte
  « remplacée en partie par ADR-0117 » dans son en-tête ; son texte n'est pas réécrit.
- Non modifié : [ADR-0067](0067-explicit-conservative-http-sync.md).
- Commit d'origine du changement de comportement : `e059e637` (2 septembre 2026).
- Mis en œuvre par : `SyncPlan.Trigger.withoutIncrementalExecutor()`, `IncrementalSyncService` (raison
  `INCREMENTAL_EXECUTOR_UNAVAILABLE`) et la commande `sync` de `morpheus-cli`.
- Reste à faire, hors de cet ADR : la synthèse arc42 §9.
