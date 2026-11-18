# Proposition : résoudre chaque rapport de gouvernance une fois par évaluation (AUD-PRF-22)

## Why

Une évaluation de gouvernance refait, pour chaque règle, des rapports identiques sur le même snapshot actif, et lit les
overrides de la portée une requête par règle. Constat `AUD-PRF-22` (Moyenne), `docs/project/audits/2026-11-17/findings.md`,
mesuré par l'audit du 17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22` :

- `PolicyEvaluationService.evaluateVersion` résout le fait de chaque règle (`facts.resolve(scope, rule)`, `:65`) et
  lit son override (`store.findOverride(scope, version.packId(), rule.id())`, `:67`) dans la boucle sur
  `version.rules()`. Le port offre pourtant `listOverrides(scope)` (`PolicyPackStore.java:52`), servi par
  `LIST_OVERRIDES_SQL` (`SqlitePolicyPackStore.java:41-46`).
- `DefaultPolicyFactResolver` refait un rapport qualité complet par règle `QUALITY_THRESHOLD`
  (`quality.assessActive`, `:132`), relit l'état de composition pour une métrique de couverture (`compositions.findActive`,
  `:198`) et réévalue une transition, donc un rapport de complétude de tout le snapshot, par règle `LIFECYCLE_GUARD`
  (`lifecycle.evaluateActive`, `:107`, cf. `AUD-PRF-21`).
- `QualityReportService.assessPublished` (`:54-60`) enchaîne cinq sous-évaluations, dont quatre relisent le contenu et
  la liste des requirements CURRENT.
- Les budgets bornent le nombre de règles, pas le travail par règle : `MAX_RULES_PER_PACK`,
  `MAX_ACTIVE_PACKS_PER_SCOPE`, `MAX_DRY_RUN_EVALUATIONS` (`PolicyBudgets.java:5-6,10`).

Un pack de règles de qualité calcule donc autant de rapports identiques qu'il a de règles. Aucun test de coût ni aucun
budget M19 ne mesure une évaluation de gouvernance.

## What Changes

- Une évaluation (`evaluate`, `evaluatePack`, `dryRun`) lit les overrides de sa portée **une fois** et les indexe par
  (pack, règle) ; elle ne lit plus d'override par règle.
- Une évaluation calcule au plus une fois, pour le snapshot actif de son projet, le rapport qualité, l'état de
  composition et les entrées d'une évaluation de cycle de vie (contenu, requirements CURRENT, complétude par
  changement), et les partage entre ses règles.
- Les règles de qualité et de cycle de vie d'une évaluation partagent ainsi un même snapshot actif (conséquence, pas
  une garantie étendue aux règles de contrainte et de requête : design.md, D3).
- Les décisions, raisons, valeurs observées et preuves de chaque règle ne changent pas.
- Un test de coût de la suite ordinaire compte les lectures de stores d'une évaluation et refuse un compte qui croît
  avec le nombre de règles qui partagent un rapport.

## Capabilities

### New Capabilities
- `policy-evaluation-cost` : ce que coûte une évaluation de gouvernance en lectures de stores, pour les rapports que
  ses règles partagent et pour les overrides de sa portée.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `PolicyEvaluationService`, `PolicyFactResolver`, `DefaultPolicyFactResolver`
(`com.morpheus.application.policy`) ; un test de coût sous `morpheus-architecture-tests` ; la page durable
`docs/developer/specifications/policy-evaluation-cost.md` (à créer) et son entrée d'index.

**Exclus :**
- le coût interne du rapport qualité (lectures de liens par requirement, tâche, décision) : `AUD-PRF-23`,
  `find-linked-quality-subjects-in-one-traceability-read` ;
- le coût de lecture des candidats d'un conflit de composition : `AUD-PRF-26`,
  `read-external-references-and-conflicts-in-a-fixed-number-of-statements` ;
- les budgets de `PolicyBudgets`, qui ne changent pas ;
- l'ajout d'un budget M19, déclaré par `AUD-PRF-30` (sprint 2), avant la correction.

## Acteurs et surfaces concernés

Clients de `policy.evaluate` et `policy.dry-run` (`contracts/public-surfaces.tsv`) : CLI `policy evaluate` /
`policy dry-run`, outils MCP `evaluate_policies` / `dry_run_policy_pack`, `POST /api/v1/policies/evaluate` et
`POST /api/v1/policies/dry-run`. Aucune forme de réponse ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Un override est lu par règle, alors que `listOverrides` existe | existant vérifié | `PolicyEvaluationService.java:67`, `PolicyPackStore.java:52` |
| Un rapport qualité complet est recalculé par règle `QUALITY_THRESHOLD` | existant vérifié | `DefaultPolicyFactResolver.java:132`, `QualityReportService.java:54-60` |
| Le résolveur est construit par opération | existant vérifié | `PolicyRuntimeServices.from`, appelé par `SqliteOperationRuntime.java:205` |
| Les rapports partagés viennent d'un seul snapshot actif | décision | design.md, D3 |
| La mémoïsation vit le temps d'une évaluation | proposition à valider | design.md, D1 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : optimisation interne au cœur de policy, retour arrière sans coût |

## Impact

`morpheus-application` (paquet `policy`), un test sous `morpheus-architecture-tests` (justification de PR exigée).
Aucun contrat, OpenAPI, schéma ni ADR. Les cœurs purs de `application.policy..` restent sans transport ni persistance
(`.claude/rules/architecture.md`).

Dépendance : **après** `assess-the-orchestration-state-of-a-change-once` (`AUD-PRF-21`), dont il réutilise la variante
d'évaluation de transition qui reçoit snapshot, contenu et complétude ; et après `AUD-PRF-30` (sprint 2), qui déclare
le budget M19 de l'évaluation de gouvernance avant la correction. Le gain de `AUD-PRF-23` s'ajoute sans ordre
imposé.

## Questions ouvertes

Aucune.
