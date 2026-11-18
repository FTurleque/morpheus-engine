# Proposition : trouver les sujets liés du rapport qualité en lectures ensemblistes (AUD-PRF-23)

## Why

Le rapport qualité lit la traçabilité une fois (ou deux) par sujet pour en tirer un booléen. Constat `AUD-PRF-23`
(Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026 sur `a04f32bd` et revérifié
le 17/11/2026 sur `1a539c22`, ligne pour ligne :

- `RequirementQualityService.assessPublished` (`:56-75`) appelle, pour **chaque** requirement CURRENT,
  `traceabilityStore.outgoing(…)` puis `traceabilityStore.incoming(…)` (`:69-70`), construit tous les liens et leurs
  preuves, et n'en garde que `!isEmpty()`.
- Même motif par tâche (`TaskQualityService.java:82`, lecture `AFFECTS` du changement de la tâche, refaite pour chaque
  tâche d'un même changement), par décision (`DecisionReferenceQualityService.java:103`, lecture `DECIDED_BY`) et par
  racine de tout type pour trouver les liens vers une référence externe (`DecisionReferenceQualityService.java:201`).
- Revérification : `ChangeCompletenessService.java:93-94` lit aussi la traçabilité une fois par changement, dans le même
  rapport (`QualityReportService.java:58`). L'audit ne le nomme pas dans `AUD-PRF-23` ; ce changement le traite pour que
  le rapport entier tienne son exigence de coût.

Un rapport qualité coûte donc au moins 2R lectures de traçabilité (R = requirements CURRENT), chacune de 2 + L requêtes
sur SQLite (`AUD-PRF-24`). Il est atteint par la CLI `quality` et par chaque règle `QUALITY_THRESHOLD` (`AUD-PRF-22`).

## What Changes

- Le port de traçabilité gagne des lectures ensemblistes par snapshot : les identités d'un type qui portent au moins un
  lien, et les liens d'un snapshot choisis par types d'extrémité et de relation, en une lecture chacune.
- Les cinq consommateurs du rapport qualité (requirements, tâches, décisions, racines des références externes,
  complétude des changements) emploient ces lectures au lieu d'une lecture par sujet.
- Les stores mémoire et SQLite rendent le même résultat pour ces lectures.
- Les constats et métriques du rapport qualité ne changent pas.
- Un test de coût de la suite ordinaire compte les lectures de traçabilité d'un rapport et refuse un compte qui croît
  avec le nombre de requirements, de tâches, de décisions ou de changements.

## Capabilities

### New Capabilities
- `quality-report-traceability-cost` : ce que coûte la lecture de traçabilité d'un rapport qualité, et ce qu'elle
  établit pour chaque sujet.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** le port `TraceabilityStore` (`com.morpheus.application.store`), ses deux adaptateurs
(`com.morpheus.store.memory`, `com.morpheus.store.sqlite.adapter`), `RequirementQualityService`, `TaskQualityService`,
`DecisionReferenceQualityService`, `ChangeCompletenessService` (`com.morpheus.application.quality`) ; tests de coût et
de parité ; la page durable `docs/developer/specifications/quality-report-traceability-cost.md` (à créer).

**Exclus :**
- le coût d'une lecture `outgoing` / `incoming` par lien : `AUD-PRF-24`,
  `read-traceability-links-in-a-fixed-number-of-statements` ;
- la mémoïsation du rapport entre règles de gouvernance : `AUD-PRF-22`,
  `resolve-each-governance-report-once-per-evaluation` ;
- l'évaluation d'un seul changement pour l'orchestration : `AUD-PRF-21`,
  `assess-the-orchestration-state-of-a-change-once` ;
- l'écriture de traçabilité en lot et la lecture du contenu métier, qui tiennent (`AUD-PRF-31`, Info).

## Acteurs et surfaces concernés

Utilisateur de la CLI `quality` (`quality.report`, `contracts/public-surfaces.tsv`) ; clients de `policy.evaluate` et
`policy.dry-run` par les règles `QUALITY_THRESHOLD`. Aucune forme de réponse ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Deux lectures de liens complètes par requirement pour un booléen | existant vérifié | `RequirementQualityService.java:69-70` |
| Une lecture par tâche, par décision, par racine, par changement | existant vérifié | `TaskQualityService.java:82`, `DecisionReferenceQualityService.java:103,201`, `ChangeCompletenessService.java:93-94` |
| ≥ 40 000 requêtes sur la fixture M19 (10 000 requirements, 25 000 liens) | mesure datée de l'audit | arithmétique de l'audit du 17/11/2026, non mesurée |
| Forme des lectures ensemblistes du port | proposition à valider | design.md, D1 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : méthode de port interne, aucun format persisté |

## Impact

`morpheus-application`, `morpheus-store-memory`, `morpheus-store-sqlite` ; tests de ces modules et deux tests sous
`morpheus-architecture-tests` (justification de PR exigée). Aucun contrat, OpenAPI, schéma ni ADR ; aucune migration
(les index `idx_snapshot_traceability_links_snapshot`, `idx_traceability_links_source` et
`idx_traceability_links_target` existent, V005).

Après `AUD-PRF-30` (sprint 2), qui déclare le budget M19 du rapport qualité avant la correction. Sans ordre imposé avec
les autres changements du sprint ; `AUD-PRF-22` en profite (rapport qualité moins cher), et
`AUD-PRF-24` réduit le coût des lectures qui restent.

## Questions ouvertes

Aucune.
