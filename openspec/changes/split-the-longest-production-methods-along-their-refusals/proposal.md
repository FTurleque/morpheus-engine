# Proposition : découper les plus longues méthodes de production selon leurs refus (AUD-QUA-35)

## Why

Un scan des méthodes de production (accolades appariées) relève, revérifié le 17/11/2026 sur `1a539c22`, une
cinquantaine de méthodes de plus de 60 lignes ; les plus longues :

- `MorpheusMain.run` (`morpheus-cli`), 191 lignes, dont 113 `println` d'aide ;
- `LocalSourceInventoryScanner.scanRoot` (`morpheus-application`, paquet `com.morpheus.application.sync`), 183 lignes,
  un visiteur anonyme dans lequel le bloc « refus `WORKSPACE_BOUNDARY_ESCAPED` puis `budget.exhaust()` » est recopié
  quatre fois (racine, répertoire, fichier avant lecture, fichier pendant l'empreinte) ;
- `SyntheticSpecificationContentReader.normalize` (`morpheus-provider-synthetic`), 149 lignes ;
- `ChangeCompletenessService.assessChange` (`morpheus-application`), 115 lignes.

`scanRoot` est sur un chemin de sécurité, la frontière physique de l'espace de travail : quatre copies d'un refus
peuvent diverger (texte, code, épuisement du budget) sans qu'aucun test ne les compare entre elles.

Preuve : constat `AUD-QUA-35` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd` (51 méthodes) ; le scan de revérification, qui compte aussi les méthodes d'une classe anonyme, en trouve
52 et retrouve les mêmes longueurs pour les méthodes citées. La comparaison avec `AUD-QUA-16` (09/10/2026, autre outil)
reste indicative.

## What Changes

- `scanRoot` : le visiteur anonyme devient une classe imbriquée nommée de `LocalSourceInventoryScanner`, dont une
  méthode unique écrit le refus `WORKSPACE_BOUNDARY_ESCAPED` et épuise le budget ; les quatre sites l'appellent. Les
  codes, textes, chemins affichés et l'ordre des refus ne changent pas.
- `normalize` : découpée par catégorie lue (spécification, exigences et scénarios, critères, changements), sans
  changer l'ordre de lecture ni la dérivation des identités.
- `assessChange` : découpée par volet évalué (contraintes, implémentation, constats), sans changer l'ordre des
  constats.
- `MorpheusMain.run` : réduit par `serve-the-whole-cli-help-from-the-help-branch` (constat `AUD-ARC-31`), dont ce
  changement dépend ; il n'est pas retouché ici.
- Aucune surface publique, aucun format persisté, aucun gate modifié.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change. Le refus de frontière du scan est déjà spécifié par
`workspace-boundary-scan-cost` et reste prouvé par ses tests. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** les trois méthodes nommées ci-dessus et leurs tests.

**Exclus :** les autres méthodes de plus de 60 lignes (`RemoteApiLaunchOptions.parse`, `SqliteSqlStatements.split`,
`OpenSpecRequirementDeltaReader.normalizeRequirementDelta`, `MorpheusRemoteHttpServer.serve`…), à reprendre au fil des
changements qui les touchent ; un gate de longueur de méthode (question ouverte de design.md, non retenue par défaut).

## Acteurs et surfaces concernés

Le contributeur qui modifie le scan d'inventaire, le provider synthétique ou l'évaluation de complétude. Aucun acteur
externe : les sorties sont inchangées.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Quatre copies du refus de frontière dans `scanRoot` | existant vérifié | `LocalSourceInventoryScanner.java`, `1a539c22` |
| Longueurs 191 / 183 / 149 / 115 | existant vérifié | scan de revérification, mêmes valeurs que l'audit |
| Une classe d'accès fichier de l'application est nommée par ARCH-0150 | existant vérifié | `ApplicationTechnologyBoundaryArchitectureTest`, `TechnologyFamily.topLevelName` |
| Le visiteur reste imbriqué, donc compté sous `LocalSourceInventoryScanner` | décision | design.md, D1 |
| Aucun ADR n'est requis | décision | refactorisation interne, `.claude/rules/adr.md` |

## Impact

`morpheus-application` (`com.morpheus.application.sync`, `com.morpheus.application.quality`),
`morpheus-provider-synthetic` ; leurs tests. Aucun test d'architecture modifié, aucun contrat.

Dépendance : `AUD-ARC-31` d'abord (ordre interne du sprint 9), soit le changement
`serve-the-whole-cli-help-from-the-help-branch`.

## Questions ouvertes

- Faut-il un gate qui plafonne le nombre de méthodes de plus de 60 lignes (compteur qui ne fait que baisser) ? Non
  proposé par l'audit ; le mainteneur peut le demander plus tard sans changer ce changement (design.md).
