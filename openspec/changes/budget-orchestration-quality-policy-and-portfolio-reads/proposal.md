# Proposition : budgéter l'orchestration, le rapport qualité, l'évaluation de gouvernance et le portfolio (AUD-PRF-30)

## Why

Les budgets M19 (`docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`) mesurent le scan d'inventaire, le plan
incrémental, la recherche de requirements, la page de journal, la traversée, l'analyse d'un change, la composition, la
publication et la lecture du contenu d'un snapshot. Aucun gate du paquet `com.morpheus.architecture.m19` ne nomme
`ChangeOrchestrationStateService`, `QualityReportService`, `RequirementQualityService`, `PolicyEvaluationService` ni la
lecture des références d'un portfolio (`PortfolioQueryService.references`).

Ce sont pourtant les chemins les plus coûteux que l'audit relève ailleurs : `AUD-PRF-21` (l'état d'orchestration
recalcule dix fois la complétude du snapshot), `AUD-PRF-22` (un rapport complet par règle de gouvernance), `AUD-PRF-23`
(deux lectures complètes de liens par requirement pour le contrôle des orphelins), `AUD-PRF-25` (références d'un
portfolio chargées en entier puis paginées en mémoire). Sans budget, ils peuvent régresser sans qu'aucune lane ne le
voie, et leurs corrections ne seraient mesurées que par le test de comptage que chacune apporte. Or `GEN-0085`
(décision 9) et l'exigence en vigueur « A cost a correction claims to bound SHALL have a budget declared before the
correction » de `performance-budget-coverage` veulent qu'un coût trouvé sans budget en reçoive un, daté, **avant** la
modification du code qu'il juge.

Preuve : constat `AUD-PRF-30` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd` ; revérifié sur `1a539c22` le 17/11/2026 (`grep` des cinq noms dans
`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/` : aucun fichier ; aucune ligne du document
des budgets ne nomme ces opérations).

## What Changes

- Quatre lignes datées sont ajoutées à la section 4 bis du document des budgets, dans un commit à elles, avant toute
  correction : état d'orchestration d'un change, rapport qualité d'un snapshot, évaluation d'un pack de gouvernance,
  page de références d'un portfolio — chacune avec son opération, sa fixture, sa borne et la raison de la valeur.
- Quatre mesures sont ajoutées aux classes de gate M19 (ou à une classe nouvelle que les deux validateurs `validate-m19`
  nomment), chacune avec une garde qui refuse de mesurer une fixture trop petite.
- La première mesure est faite sur le code non corrigé et consignée, qu'elle passe ou échoue.
- Aucune ligne, fixture, protocole ni tas existant ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `performance-budget-coverage` : quatre opérations reçoivent un budget déclaré avant leur correction, mesuré sur une
  fixture qui tient les lignes avec lesquelles leur coût croît.

## Périmètre et exclusions

**Inclus :** `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` (section 4 bis), le paquet de test
`com.morpheus.architecture.m19`, `scripts/validate-m19.ps1` et `scripts/validate-m19.sh` si une classe de gate est
ajoutée, la page durable de la capacité (à créer).

**Exclus :** les corrections elles-mêmes (`AUD-PRF-21`, `AUD-PRF-22`, `AUD-PRF-23`, `AUD-PRF-25`), et leurs tests de
comptage dans la suite ordinaire, que chacune porte (`GEN-0085`, décision 9) ; les autres N+1 du sprint 5
(`AUD-PRF-24`, `AUD-PRF-26`), que l'audit ne demande pas de budgéter ici.

## Acteurs et surfaces concernés

Le mainteneur et la lane nocturne `M19 performance budgets` (Linux et Windows). Aucune surface publique.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucun gate M19 ne mesure ces quatre opérations | existant vérifié | `grep -ln` des noms de service dans le paquet `m19` |
| Un budget ajouté après le gel est daté et déclaré avant la correction | existant vérifié | `GEN-0085` décision 9 ; `performance-budget-coverage` |
| La borne d'un pack de gouvernance est `PolicyBudgets.MAX_RULES_PER_PACK` | existant vérifié | `PolicyBudgets`, OpenAPI M25 `maxItems: 128` |
| Budgets nocturnes plutôt que seuls tests de comptage | proposition à valider | design.md, D1 |
| Valeur de chaque borne et taille de la fixture de portfolio | question ouverte | design.md, D2 et D3 |

## Impact

Tests de `morpheus-architecture-tests` (paquet `m19`, fichiers de gouvernance : justification de PR), validateurs
`validate-m19` (parité `.ps1` / `.sh`, `GateReachabilityArchitectureTest`), document des budgets. `GEN-0085` couvre la
décision ; aucun ADR à proposer.

Ordre : ce changement **précède** les corrections `AUD-PRF-21`, `AUD-PRF-22`, `AUD-PRF-23` (sprint 5) et `AUD-PRF-25`
(sprint 6) ; le commit des quatre lignes doit être un ancêtre de chacune. Aucune dépendance avec les autres constats du
sprint 2.

## Questions ouvertes

- Le mainteneur retient-il des budgets nocturnes M19 (recommandé), ou les seuls tests de comptage de requêtes dans
  `morpheus-store-sqlite` que l'audit propose comme alternative moins chère ? (design.md, D1)
- Valeurs des quatre bornes (design.md, D2) et taille de la fixture de portfolio (D3) : `GEN-0085` veut une valeur tirée
  de l'usage, fixée par le mainteneur avant la première mesure.
