# Proposition : borner chaque job de workflow dans le temps (AUD-DEP-35)

## Why

Le job `verify` de `ci.yml` (`exact-head (ubuntu-latest)` et `exact-head (windows-latest)`, checks requis) ne déclare
pas `timeout-minutes` (`.github/workflows/ci.yml:17-23`, revérifié le 17/11/2026 sur `1a539c22`), alors que tous les
autres jobs de `ci.yml`, `codeql.yml`, `nightly.yml`, `release.yml` et `security.yml` en déclarent un
(`grep -n timeout-minutes .github/workflows/*.yml`). Sa voie Windows lance `validate-m28.ps1`, qui construit et installe
réellement `Setup.exe` puis démarre un pair MCP (`ci.yml:90-93`) : une installation ou un processus bloqué consomme le
défaut de GitHub Actions (360 minutes) avant d'échouer, et retarde d'autant le verdict d'une pull request. Le seul test
qui lit un `timeout-minutes` vise `security.yml` (`D2RepositoryHardeningArchitectureTest.java:430`).

Constat `AUD-DEP-35` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- `ci.yml` : le job `verify` déclare `timeout-minutes`, dimensionné sur la durée mesurée de ses deux voies.
- Gate de build : un test de contrat refuse tout job d'un workflow de `.github/workflows/` qui ne déclare pas
  `timeout-minutes`, et nomme le workflow et le job.
- Page durable nouvelle `docs/developer/specifications/workflow-job-time-limit.md`, indexée.

## Capabilities

### New Capabilities
- `workflow-job-time-limit` : tout job de workflow GitHub Actions du dépôt porte une limite de durée explicite, tenue
  par un test de contrat.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `ci.yml` (job `verify`) ; une méthode de `AuditHardeningWorkflowContractTest` ; la page durable de la
capacité et l'index de `docs/developer/specifications/README.md`.

**Exclus :** la valeur des limites déjà déclarées, inchangée (seule `security.yml` est épinglée, par
`D2RepositoryHardeningArchitectureTest`) ; les limites par étape (`timeout-minutes` d'un `step`) ; la concurrence de
`ci.yml`, objet du changement `give-every-merge-on-main-its-ci-verdict`.

## Acteurs et surfaces concernés

La CI (runners GitHub), l'auteur d'une pull request qui attend le verdict `exact-head`, le mainteneur.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `verify` est le seul job sans `timeout-minutes` | existant vérifié | `grep -n timeout-minutes .github/workflows/*.yml` |
| La voie Windows coûte 12 à 29 minutes sur 53 runs (relevé du 10/09/2026) | existant vérifié (mesure datée) | commentaire `ci.yml:78-80` |
| Une limite d'environ deux fois l'écart haut mesuré | proposition à valider | design.md, D1 ; la valeur suit la mesure de la tâche 1.1 |
| Aucun ADR | décision | `.claude/rules/adr.md` : réglage de CI, retour arrière immédiat |

## Impact

`.github/workflows/ci.yml` ; `AuditHardeningWorkflowContractTest` (fichier de gouvernance : justification de PR
requise) ; `docs/developer/specifications/workflow-job-time-limit.md` (à créer) et l'index du dossier. Aucune
dépendance envers un autre constat ; `give-every-merge-on-main-its-ci-verdict` touche le bloc `concurrency` du même
fichier, sans recouvrement de lignes.

## Questions ouvertes

Aucune : la valeur de la limite est dérivée d'une mesure faite par la première tâche.
