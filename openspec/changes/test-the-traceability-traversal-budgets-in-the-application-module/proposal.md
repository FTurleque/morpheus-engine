# Proposition : tester les budgets de nœuds et de liens de la traversée dans le module application (AUD-TST-32)

## Why

`TraceabilityTraversalService` (`morpheus-application/src/main/java/com/morpheus/application/traceability/`) arrête
une traversée à `MAX_NODES` ou à `MAX_LINKS` et en donne la raison exacte, `NODE_BUDGET_REACHED:<n>` ou
`LINK_BUDGET_REACHED:<n>` (lignes 125-131). C'est un contrat de déterminisme (`.claude/rules/code-style.md`, « Borner
toute traversée … et exposer une raison de troncature ») que `trace_requirement` et `get_change_context` exposent.

Les seuls tests qui atteignent ces deux budgets vivent dans `morpheus-architecture-tests`
(`TraceabilityTruncationContractTest`, lignes 75-82 et 107-108). Dans `morpheus-application`,
`ChangeAnalysisServiceTest#aDependencyChainDeeperThanTheRequestedDepthIsReportedAsTruncated` ne couvre que
`DEPTH_BUDGET_REACHED`, `CompactQueryViewServiceTest` (ligne 250) recopie la chaîne sans traverser, et aucun test du
module ne nomme la classe. L'échelle de couverture par module ne crédite pas les tests du module d'architecture : la
preuve disparaîtrait avec une réorganisation de ce module sans qu'aucun gate de couverture d'application ne bouge.

Preuve : constat `AUD-TST-32` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` ; revérifié sur `1a539c22` le 17/11/2026 (aucun test de `morpheus-application/src/test` ne
nomme `TraceabilityTraversalService` ni `LINK_BUDGET_REACHED`).

## What Changes

- Un test de `TraceabilityTraversalService` dans `morpheus-application`, sur un double écrit à la main du port
  `TraceabilityStore`, atteint `MAX_NODES` puis `MAX_LINKS` et exige la raison exacte, le nombre de nœuds ou de liens
  rendus et l'ordre déterministe du résultat.
- Le test de contrat du module d'architecture reste en place.
- Aucun code de production, aucune exigence, aucun gate ni ratchet ne change.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** un test dans `morpheus-application/src/test/java/com/morpheus/application/traceability/`.

**Exclus :** le relèvement d'un seuil de couverture de `morpheus-application` ou d'un maximum du crédit emprunté, qui
relève d'une requalification (`.claude/skills/coverage-ratchet`) ; le retrait ou le déplacement de
`TraceabilityTruncationContractTest` ; la spécification de la troncature comme capacité, qu'aucune exigence ne change
ici (design.md, Écarts constatés).

## Acteurs et surfaces concernés

Le mainteneur et la CI. Aucune surface publique.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les budgets de nœuds et de liens ne sont testés que dans le module d'architecture | existant vérifié | `grep -rn "TraceabilityTraversalService\|LINK_BUDGET_REACHED" morpheus-application/src/test` |
| Le module application implémente à la main les ports qu'il lit | existant vérifié | `.claude/rules/testing.md`, exception de `morpheus-application` |
| La classe compte 211 lignes non vides sur l'arbre courant | existant vérifié | `grep -c .` sur le fichier ; l'audit en annonçait 177 (méthode de comptage différente) |
| Aucun comportement ne change | décision | seul un test est ajouté |

## Impact

Tests de `morpheus-application`. Aucun ADR, aucun contrat, aucune spécification, aucun ratchet, aucun test
d'architecture. Aucune dépendance avec les autres constats du sprint 2.

## Questions ouvertes

Aucune.
