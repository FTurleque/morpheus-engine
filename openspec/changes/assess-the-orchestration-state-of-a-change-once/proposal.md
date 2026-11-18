# Proposition : évaluer une seule fois l'état d'orchestration d'un changement (AUD-PRF-21)

## Why

L'état d'orchestration d'un changement, lu en routine par JARVIS, refait pour chaque transition évaluée le travail
qu'il a déjà fait pour lui-même. Constat `AUD-PRF-21` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré
par l'audit du 17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22`, ligne pour ligne :

- `ChangeOrchestrationStateService.active` calcule le rapport de complétude de **tout** le snapshot
  (`completenessService.assessSnapshot(active.id())`, `:78`) pour n'en garder qu'un changement, puis relit le contenu
  métier (`contentStore.findSnapshotContent`, `:82`).
- `transitionEvaluations` (`:159-171`) appelle `ChangeTransitionEvaluationService.evaluateActive` pour chacun des neuf
  états cibles (dix états de `ChangeLifecycleState`, moins l'état source). Chaque évaluation relit le snapshot actif,
  refait un rapport de complétude complet (`ChangeLifecycleQualityService.completeness`, `:176-184`) puis relit le
  contenu pour ses contraintes (`ChangeTransitionEvaluationService.constraintEvaluations`, `:162-171`).
- Un rapport de complétude lit le contenu (`ChangeCompletenessService.java:68`), la liste intégrale des requirements
  CURRENT (`:72`) et une lecture de traçabilité **par changement du snapshot** (`:93-94`).

Un appel avec un cycle de vie observé exécute donc dix rapports de complétude, vingt lectures du contenu métier, dix
listes intégrales de requirements et dix fois C lectures de traçabilité (C = changements du snapshot). Aucun budget M19
ni aucun test de coût ne mesure ce chemin. Le constat `AUD-PRF-22` réutilise la variante introduite ici : il vient
après.

## What Changes

- Un état d'orchestration lit le contenu métier du snapshot actif une fois et n'évalue la complétude **que du
  changement demandé**, une fois, quel que soit le nombre de transitions évaluées.
- Les neuf évaluations de transition reçoivent le snapshot, le contenu et l'évaluation de complétude déjà lus au lieu
  de les relire.
- Un test de coût de la suite ordinaire compte les lectures de stores d'un état d'orchestration et refuse un compte
  qui dépend du nombre de changements du snapshot ou du nombre de transitions.
- Aucune réponse ne change de forme ni de valeur ; aucune route, aucun outil, aucune commande, aucun format persisté.

## Capabilities

### New Capabilities

### Modified Capabilities
- `change-orchestration-state` : ajout d'une exigence de coût — l'état se construit sur une seule lecture du contenu
  et une seule évaluation de complétude du changement demandé.

## Périmètre et exclusions

**Inclus :** `ChangeOrchestrationStateService` (`com.morpheus.application.orchestration`),
`ChangeTransitionEvaluationService`, `ChangeLifecycleQualityService` et `ChangeCompletenessService`
(`com.morpheus.application.quality`) ; un test de coût sous `morpheus-architecture-tests` ; la page durable
`docs/developer/specifications/change-orchestration-state.md`.

**Exclus :**
- le coût d'une lecture de liens de traçabilité elle-même (`AUD-PRF-24`,
  `read-traceability-links-in-a-fixed-number-of-statements`) et la lecture des références externes du changement
  (`AUD-PRF-26`, `read-external-references-and-conflicts-in-a-fixed-number-of-statements`) ;
- l'évaluation de gouvernance, qui réutilise la variante introduite ici (`AUD-PRF-22`,
  `resolve-each-governance-report-once-per-evaluation`, à livrer après ce changement) ;
- la lecture du contenu métier en un nombre fixe de requêtes, qui tient et n'est pas touchée (`AUD-PRF-31`, Info) ;
- l'ajout d'un budget M19 sur ce chemin, déclaré par `AUD-PRF-30` (sprint 2), avant la correction.

## Acteurs et surfaces concernés

JARVIS et tout client de `orchestration.state` (`contracts/public-surfaces.tsv`) : CLI `change-orchestration state`,
outil MCP `get_change_orchestration_state`, `GET /api/v1/projects/{projectId}/changes/{changeId}/orchestration`. Le
contrôle `orchestration.transition` (une seule transition) bénéficie de la même variante sans changer de forme.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Dix rapports de complétude et vingt lectures de contenu par appel | existant vérifié | lignes citées au Why, revérifiées sur `1a539c22` |
| Le rapport de complétude lit la traçabilité une fois par changement du snapshot | existant vérifié | `ChangeCompletenessService.java:77-80`, `:93-94` |
| L'évaluation d'un seul changement égale celle que le rapport complet donne pour lui | proposition à valider | `assessChange` ne lit que le changement, le contenu et l'ensemble des requirements CURRENT (`:84-117`) ; tenu par un test d'égalité à écrire |
| Ordre de grandeur « dizaine de secondes » sur la fixture M19 | question ouverte | extrapolation non mesurée de l'audit |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : optimisation interne, retour arrière sans coût |

## Impact

`morpheus-application` (paquets `orchestration` et `quality`), un test sous `morpheus-architecture-tests` (fichier de
gouvernance : justification dans la description de PR). Aucun contrat, aucun OpenAPI, aucun schéma, aucun ADR.
`resolve-each-governance-report-once-per-evaluation` dépend de ce changement. Il vient **après** `AUD-PRF-30` (sprint 2),
qui déclare le budget M19 de l'état d'orchestration avant toute correction (`performance-budget-coverage`).

## Questions ouvertes

Aucune.
