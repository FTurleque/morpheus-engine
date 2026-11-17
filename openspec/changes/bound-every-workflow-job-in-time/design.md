# Conception : borner chaque job de workflow dans le temps

## Contexte

- [EXISTANT VÉRIFIÉ] `ci.yml:17-23` : job `verify`, matrice `ubuntu-latest` / `windows-latest`, aucun
  `timeout-minutes` ; tous les autres jobs des cinq workflows en portent un (`grep -n timeout-minutes
  .github/workflows/*.yml`, revérifié sur `1a539c22`).
- [EXISTANT VÉRIFIÉ] `ci.yml:78-80` : l'étape M28 de la voie Windows « costs roughly 17 minutes of runner time, spread
  12-29 across the 53 successful runs sampled on 10/09/2026 » ; cette mesure porte sur l'étape, pas sur le job entier
  (checkout, Java, téléversement compris), et elle n'existe pas pour la voie Linux.
- [EXISTANT VÉRIFIÉ] `D2RepositoryHardeningArchitectureTest.java:430` épingle `timeout-minutes: 90` de `security.yml` ;
  aucun test ne couvre les autres jobs.
- [EXISTANT VÉRIFIÉ] `AuditHardeningWorkflowContractTest` lit déjà `ci.yml` et extrait le job `sonar` (méthode
  `sonarJob`).

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `workflow-job-time-limit` | Every job of a workflow SHALL declare its own time limit | D1, D2 |

## Composants et frontières

`.github/workflows/ci.yml` ; `AuditHardeningWorkflowContractTest` (méthode nouvelle). Assertion textuelle sur des
`.yml` : le texte est la seule prise (`.claude/rules/architecture.md`).

Sans objet pour les autres rubriques de composants : aucun module Java de production.

## Modèle de domaine et invariants

Sans objet : configuration de CI.

## Traitements et interactions

Sans objet : aucune interaction nouvelle ; un job qui atteint sa limite est arrêté par GitHub Actions.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet.

## Persistance et intégrité

Sans objet.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Un job arrêté à sa limite échoue (`cancelled` par dépassement), ce qui fait échouer le check requis : aucun repli.

## Observabilité

Sans objet : la conclusion du job dit le dépassement.

## Performance et stratégie de mesure

La limite se fixe sur une mesure, pas sur un chiffre recopié : relever la durée du job `verify` par voie sur les runs
réussis des trente derniers jours (`gh run list --workflow ci.yml --status success --json databaseId`, puis
`gh run view <id> --json jobs`), noter le maximum de chaque voie, et retenir environ deux fois le maximum de la voie la
plus lente, arrondi à la dizaine. La mesure et sa date sont écrites dans le commentaire au-dessus de la clé, comme le
fait déjà le commentaire de l'étape M28.

## Compatibilité, migration et retour arrière

Retour arrière : retirer la clé. Un job légitimement plus long que la limite échouerait : la marge de deux fois la
mesure haute l'évite, et la limite se relève avec une nouvelle mesure.

## Décisions

### D1 : une limite au job `verify`, dérivée de la mesure
- **Nature** : [PROPOSITION]
- **Exigences servies** : `workflow-job-time-limit` › Every job of a workflow SHALL declare its own time limit
- **Choix** : `timeout-minutes` au niveau du job `verify`, valeur commune aux deux voies, fixée par la mesure décrite
  en Performance (ordre de grandeur attendu : 60, si la voie Windows reste sous 30 minutes de job).
- **Alternatives écartées** : une valeur par voie (`matrix` + `fromJSON`) — complexité sans gain tant que la voie
  Linux est plus courte ; une limite par étape seulement — l'étape d'installation n'est pas la seule à pouvoir bloquer
  (pair MCP, téléversement).
- **Compromis** : la voie Linux reçoit une limite plus large que son besoin.
- **ADR** : non concerné.

### D2 : le contrat porte sur tout job de tout workflow
- **Nature** : [DÉCISION]
- **Exigences servies** : `workflow-job-time-limit` › Every job of a workflow SHALL declare its own time limit
- **Choix** : la méthode lit chaque `*.yml` de `.github/workflows/`, découpe la section `jobs:` en jobs (clés indentées
  de deux espaces), ignore un job dont la clé `uses:` est au niveau du job, et exige une ligne `timeout-minutes:` au
  niveau du job (quatre espaces), pas dans un `step`. Le refus énumère tous les jobs fautifs en une fois.
- **Alternatives écartées** : épingler la valeur de chaque job — figerait des valeurs qui se requalifient sur mesure,
  et la dette de `security.yml` reste épinglée par son propre test ; un parseur YAML — aucune dépendance YAML dans
  `morpheus-architecture-tests`, et les autres contrats de workflow lisent déjà le texte.
- **Compromis** : un workflow écrit avec une autre indentation échapperait à la découpe ; la méthode refuse une section
  `jobs:` dont elle ne retire aucun job (garde contre la règle vide, `.claude/rules/architecture.md`).
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| La mesure citée par l'audit (écart haut du lane M28) porte sur une étape, pas sur le job | `ci.yml:78-80` | action d'`AUD-DEP-35` | mesure du job entier (Performance) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Every job of a workflow SHALL declare its own time limit | A job without a limit is refused | `AuditHardeningWorkflowContractTest#everyJobOfEveryWorkflowDeclaresItsOwnTimeLimit` (rouge sur l'arbre actuel, nomme `ci.yml` / `verify`) | à écrire |
| idem | A limit declared on a step only is not enough | même méthode, violation plantée (trace dans la PR) | manuel |
| idem | A new workflow is covered without editing the test | même méthode, workflow fictif planté puis retiré | manuel |
| idem | The required verification job is bounded | clé présente dans `ci.yml`, conclusion d'un run qui dépasse (non provoqué) | existant après 2.1 |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/workflow-job-time-limit.md` (à créer, gabarit de
`docs/developer/specifications/README.md`) et sa ligne d'index dans ce README. Aucun diagramme : règle statique sans
interaction.

## Questions ouvertes

Aucune.
