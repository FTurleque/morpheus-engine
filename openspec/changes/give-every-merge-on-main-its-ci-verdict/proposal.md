# Proposition : donner à chaque fusion sur main son verdict CI (AUD-DEP-34)

## Why

`ci.yml` déclare `concurrency: group: morpheus-ci-${{ github.workflow }}-${{ github.ref }}` et
`cancel-in-progress: true` pour tous ses événements (`.github/workflows/ci.yml:12-14`, revérifié le 17/11/2026 sur
`1a539c22`). Deux fusions rapprochées sur `main` partagent le groupe `refs/heads/main` : la seconde annule le run de la
première, qui reste sans verdict `exact-head` ni analyse SonarQube Cloud. Le workflow affirme pourtant que le job
`sonar` « gives every merge its verdict » (`ci.yml:131-132`), et l'exigence `single-integration-branch` › Chaque fusion
dans main DOIT recevoir une analyse statique le promet. Le défaut a déjà coûté : la qualification de couverture du
07/11/2026 a dû passer par deux `workflow_dispatch` parce que « le run `push` du même SHA a été annulé par le groupe de
concurrence de `ci.yml` » (`.claude/rules/testing.md`).

Le même motif existe dans `security.yml:28-30` : un push sur `main` annule le rafraîchissement planifié en cours (même
groupe `refs/heads/main`), donc la sauvegarde du cache NVD qu'il allait publier. `codeql.yml:16-18` le porte aussi
(absent de l'audit, constaté à la revérification).

Constat `AUD-DEP-34` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- `ci.yml` : un run déclenché par `push` (ou `workflow_dispatch`) n'est jamais annulé par un run suivant ; un run de
  pull request reste annulé par le push suivant de la même pull request.
- `security.yml` : un run planifié ou un push sur `main` n'annule plus l'autre et n'est plus annulé par lui ; un run de
  pull request reste annulé par le suivant.
- `codeql.yml` : même règle que `ci.yml`.
- Gate de build : un test de contrat refuse un workflow déclenché sur `main` dont la concurrence peut annuler, ou
  laisser en attente puis abandonner, un run de `push` ou de `schedule`.
- Le commentaire de `ci.yml` qui affirme le verdict de chaque fusion devient vrai sans changer de texte.

## Capabilities

### New Capabilities

### Modified Capabilities
- `single-integration-branch` : chaque fusion sur `main` reçoit un run complet de `ci.yml` et de `codeql.yml`, jamais
  annulé par une fusion suivante.
- `vulnerability-feed-freshness` : le rafraîchissement planifié n'est pas annulé par un push sur `main`.

## Périmètre et exclusions

**Inclus :** les blocs `concurrency` de `ci.yml`, `codeql.yml`, `security.yml` ; une méthode de test de contrat ; les
pages durables des deux capacités.

**Exclus :** `nightly.yml` et `release.yml`, déjà en `cancel-in-progress: false` et sans déclencheur concurrent sur le
même groupe ; le coût en minutes de runner de runs de `main` qui ne s'annulent plus (design.md, Performance) ; le ruleset
GitHub, hors dépôt.

## Acteurs et surfaces concernés

Le mainteneur qui fusionne, la CI (GitHub Actions), SonarQube Cloud, les pull requests qui restaurent le cache NVD.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Un push sur `main` annule le run en cours du push précédent | existant vérifié | `ci.yml:12-14`, `security.yml:28-30`, `codeql.yml:16-18` |
| Un run annulé a déjà privé une qualification de son run `push` | existant vérifié | `.claude/rules/testing.md`, qualification du 07/11/2026 |
| `cancel-in-progress: false` seul ne suffit pas : GitHub abandonne un run en attente quand un troisième arrive dans le groupe | décision fondée sur le comportement documenté de GitHub Actions | design.md, D1 |
| Aucun ADR | décision | `.claude/rules/adr.md` : réglage de workflow, retour arrière immédiat |

## Impact

`.github/workflows/ci.yml`, `codeql.yml`, `security.yml` ; `AuditHardeningWorkflowContractTest` et
`DependencyCheckWorkflowContractTest` (fichiers de gouvernance : justification de PR requise) ;
`docs/developer/specifications/single-integration-branch.md`, `vulnerability-feed-freshness.md`. Aucune dépendance
envers un autre constat ; le changement `bound-every-workflow-job-in-time` touche aussi `ci.yml` (job `verify`), sans
recouvrement de lignes.

## Questions ouvertes

Aucune.
