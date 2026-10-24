---
id: ARCH-0124
title: "Findings de qualité explicables et faits tri-état"
domain: architecture
rules: false
---

# Findings de qualité explicables et faits tri-état

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0048, ADR-0049, ADR-0050, ADR-0051, ADR-0052

## Context

MORPHEUS signale les lacunes de qualité d'un projet : exigences sans lien de traçabilité, tâches qu'aucune exigence
ne couvre, critères d'acceptation non vérifiés, changements incomplets, décisions de conception non tracées,
références externes dégradées. Ces constats sont dérivés d'un snapshot publié ; ce ne sont ni des diagnostics
d'ingestion ni des entités persistées.

Le snapshot ne permet pas d'observer tout ce que la machine de cycle de vie des changements consulte (ADR-0119) : le
besoin d'une conception, par exemple, n'est porté par aucune donnée normalisée. Un fait non observable converti en
`FALSE`, ou une absence d'ingestion lue comme une absence réelle, produirait un verdict faux.

Options écartées :

- étendre le catalogue des diagnostics d'ingestion : mélange découverte de sources et qualité métier ;
- persister les findings : ils deviennent une seconde vérité qui vieillit avec le snapshot ;
- inférer un lien, une justification ou un critère depuis le texte (correspondance approchée, recherche sémantique,
  LLM) : contraire à ADR-0004 ;
- reconstruire le cycle de vie d'un changement depuis l'état du snapshot, l'état temporel ou l'achèvement des
  tâches : ces états sont orthogonaux au cycle de vie.

## Decision

1. **Un finding est un résultat dérivé et explicable.** `QualityFinding` porte un code `QualityFindingCode`, une
   gravité `DiagnosticSeverity`, une nature de preuve `QualityEvidenceKind`, un sujet `TraceabilityEntityRef`, un
   message, des détails, une confiance facultative et des identifiants d'evidence dédupliqués et triés. Les findings
   se trient par sujet, code puis message. Aucun finding n'est persisté.
2. **Preuve déterministe ou heuristique.** Un finding `DETERMINISTIC` n'a pas de confiance ; un finding `HEURISTIC`
   exige une confiance finie dans `[0, 1]`. Un score n'est jamais présenté comme une preuve. Les services de qualité
   ne produisent que des findings `DETERMINISTIC`.
3. **Politique de snapshot.** Chaque service a une forme par projet (`ACTIVE`, `Optional.empty()` sans `ACTIVE`) et
   une forme par snapshot explicite limitée à `ACTIVE` ou `RETIRED` ; un snapshot sans projection métier est un
   échec (ADR-0122). « Pas d'`ACTIVE` » reste distinct d'un rapport vide. Seules les occurrences `CURRENT` des
   exigences sont prises en compte.
4. **Couverture des exigences** (`RequirementQualityService`). Une exigence `CURRENT` est liée si elle a au moins un
   lien de traçabilité direct, entrant ou sortant, de n'importe quelle relation, dans le snapshot ; sinon elle est
   orpheline et produit `ORPHAN_REQUIREMENT` (`WARNING`), avec l'evidence de sa provenance.
5. **Couverture des tâches** (`TaskQualityService`). Une tâche est couverte si son changement a au moins un lien
   `AFFECTS` publié vers une exigence qui a une occurrence `CURRENT` dans le même snapshot. Aucun lien tâche →
   exigence n'est synthétisé. Une cible seulement `PROPOSED`, une cible absente ou l'absence de lien `AFFECTS` laissent
   la tâche non couverte : `IMPLEMENTATION_TASK_WITHOUT_REQUIREMENT` (`WARNING`, détails `taskId`, `changeId`,
   `snapshotId`). Le finding dit seulement qu'aucun `AFFECTS` vers une exigence `CURRENT` n'est publié pour ce
   changement dans ce snapshot.
6. **Ratio sur une population vide.** Le ratio de couverture des exigences et celui des tâches valent `1.0` sur une
   population vide par convention de validation des records ; ce n'est pas une mesure. `QualityReportMetrics` le dit
   par `CoverageRatioStatus.UNDEFINED_EMPTY_POPULATION` (sinon `MEASURED`), et toute surface lit ce statut avant le
   ratio. Les comptes restent de vrais zéros (ADR-0108).
7. **Couverture d'acceptance** (`AcceptanceQualityService`). Les critères sont ceux du modèle (ADR-0081). Sans
   critère, le statut est `NO_CRITERIA` ; sinon `EVALUATED`, avec un compte par `VerificationStatus` et le ratio des
   critères `VERIFIED`. Chaque critère non vérifié produit un finding : `ACCEPTANCE_CRITERION_FAILED` (`ERROR`),
   `ACCEPTANCE_CRITERION_PARTIALLY_VERIFIED` et `ACCEPTANCE_CRITERION_NOT_VERIFIED` (`WARNING`),
   `ACCEPTANCE_CRITERION_UNKNOWN` (`INFO`), avec les evidence de vérification du critère ou, à défaut, celle de sa
   provenance. Un `Scenario` n'est jamais converti en critère.
8. **Faits tri-état d'un changement** (`ChangeCompletenessService`). Les neuf faits que consulte la machine de cycle
   de vie sont projetés en `QualityFactValue` (`TRUE`, `FALSE`, `UNAVAILABLE`) dans
   `ChangeLifecycleFactAssessment`, à partir du seul snapshot :

   | Fait | Dérivation |
   |---|---|
   | `requirementsIdentified` | `TRUE` si au moins un `AFFECTS` du changement atteint une exigence `CURRENT`, sinon `FALSE` |
   | `criticalConstraintsKnown` | `TRUE` si le changement a des contraintes et qu'aucune n'a d'applicabilité ni de sévérité `UNKNOWN` (ADR-0082) ; `UNAVAILABLE` sinon, y compris sans contrainte |
   | `acceptanceCriteriaDefined` | `TRUE` si au moins un critère est rattaché au changement ou à une de ses exigences `CURRENT` ; sinon `FALSE` si une exigence est résolue, `UNAVAILABLE` si aucune |
   | `designRequired` | toujours `UNAVAILABLE` : aucune donnée normalisée ne l'indique |
   | `designDecisionsAvailable` | `TRUE` si au moins une décision de conception est rattachée au changement, sinon `FALSE` |
   | `planPresent` | `TRUE` si au moins une tâche est rattachée, sinon `UNAVAILABLE` : l'absence de tâche ne prouve pas l'absence de plan |
   | `knownBlocker` | évaluation des contraintes pour `IMPLEMENTING` (ADR-0082) : `TRUE` si l'une est bloquante, sinon `UNAVAILABLE` si l'une est inconnue, sinon `FALSE` ; les risques d'un changement ne sont pas des bloqueurs |
   | `blockingAcceptanceCriterionFailed` | toujours `UNAVAILABLE` |
   | `blockingAcceptanceCriterionUnverified` | toujours `UNAVAILABLE` |

   Le rapport de complétude donne, par changement trié par identité, ces faits et les comptes d'exigences `CURRENT`,
   de contraintes, de décisions et de tâches. Il émet `CHANGE_WITHOUT_CURRENT_REQUIREMENT` (`WARNING`) quand aucune
   exigence `CURRENT` n'est reliée, et un seul `CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE` (`INFO`, détail
   `unavailableFacts`) quand au moins un fait est `UNAVAILABLE` : MORPHEUS ne peut pas conclure sur toutes les
   dimensions, ce qui ne veut pas dire que le changement est incorrect.
9. **`UNAVAILABLE` n'est jamais converti.** `ChangeLifecycleQualityService` reçoit explicitement le cycle de vie
   source, l'état cible, la politique et la raison d'abandon facultative ; il ne reconstruit jamais le cycle de vie.
   Il connaît les faits que chaque transition consulte (`PROPOSED → SPECIFIED` : exigences, contraintes critiques,
   critères ; `SPECIFIED → DESIGNED` : besoin et décisions de conception ; `SPECIFIED → PLANNED` : besoin de
   conception et plan ; `DESIGNED → PLANNED` : plan ; `PLANNED → IMPLEMENTING` : bloqueur connu ;
   `VERIFYING → COMPLETED` : les deux faits de critère bloquant). En mode dérivé, si un fait requis est
   `UNAVAILABLE`, la machine n'est pas appelée, la décision est vide et chaque fait manquant produit
   `LIFECYCLE_REQUIRED_FACT_UNAVAILABLE` (`WARNING`) ; aucune valeur de substitution n'est inventée. Sinon la machine
   décide, et chaque bloqueur qu'elle rend devient `LIFECYCLE_TRANSITION_BLOCKED` (`WARNING`, détails `blocker`,
   `from`, `to`, `factSource`). En mode explicite, la machine reçoit exactement les `ChangeLifecycleFacts` fournis
   par l'appelant et ses bloqueurs sont projetés sans modification. Un cycle de vie dont le changement est absent du
   snapshot est refusé.
10. **Décisions et références externes** (`DecisionReferenceQualityService`). Une décision est tracée si le snapshot
    contient `Change --DECIDED_BY--> DesignDecision` depuis son changement ; sinon `DESIGN_DECISION_WITHOUT_TRACE`
    (`WARNING`). La justification n'est jamais déduite du texte : chaque décision porte
    `DecisionJustificationStatus.UNAVAILABLE_IN_NORMALIZED_MODEL` et le finding `DECISION_JUSTIFICATION_UNAVAILABLE`
    (`INFO`), qui ne juge pas la décision. Les références externes sont lues par
    `ExternalTraceabilityQueryService.inspect(...)`, sans nouvelle résolution, et chaque état dégradé donne son code
    (`EXTERNAL_REFERENCE_UNVALIDATED`, `_UNRESOLVED`, `_STALE`, `_BROKEN`, en `WARNING`, avec les evidence du lien) ;
    `REFERENCE_RESOLVED` n'en donne aucun. Un lien dont la référence manque reste visible comme `BROKEN`.
11. **Rapport agrégé sans recalcul** (`QualityReportService`). Le rapport résout le snapshot une fois puis délègue à
    `RequirementQualityService`, `TaskQualityService`, `AcceptanceQualityService`, `ChangeCompletenessService` et
    `DecisionReferenceQualityService`, qui restent la source de vérité. Chaque composant doit porter le même snapshot :
    une divergence est une erreur, jamais un avertissement. Les findings sont concaténés, dédupliqués et triés, sans
    réinterprétation de code. Les métriques donnent le total des findings, les comptes et ratios d'exigences et de
    tâches avec leur statut de ratio, le statut d'acceptance, les nombres de changements, de décisions et de
    références externes, et des comptes par code, gravité et nature de preuve limités aux valeurs observées. La
    qualité de cycle de vie, qui exige un cycle de vie fourni par l'appelant, n'est pas agrégée :
    `LifecycleQualityAggregationStatus.REQUIRES_EXPLICIT_LIFECYCLE_INPUT`. Le rapport est calculé à la demande ; sa
    vue compacte (`get_quality_report`) suit ADR-0123.
12. **Parité.** Toute la logique est dans `morpheus-application` ; le store mémoire et le store SQLite rendent les
    mêmes findings, y compris après réouverture.

## Do's and Don'ts

### Do

- Marquer tout finding `DETERMINISTIC` sans confiance, ou `HEURISTIC` avec une confiance dans `[0, 1]`.
- Rendre `UNAVAILABLE` tout fait que le snapshot ne permet pas d'observer, y compris sur une population vide.
- Lire `CoverageRatioStatus` avant le ratio de couverture.
- Ne pas appeler la machine de cycle de vie en mode dérivé tant qu'un fait requis est `UNAVAILABLE`.
- Déléguer le rapport agrégé aux services spécialisés et exiger le même snapshot de chacun.
- Joindre à chaque finding l'evidence de la provenance de son sujet ou du lien concerné.

### Don't

- Ne jamais convertir `UNAVAILABLE` en `FALSE` ni en `TRUE`, ni inventer une valeur de substitution.
- Ne jamais présenter un ratio sur une population vide comme une mesure.
- Ne jamais persister un finding ni un rapport de qualité.
- Ne jamais synthétiser un lien de traçabilité, une justification ou un critère d'acceptation.
- Ne jamais déduire le cycle de vie de l'état du snapshot, de l'état temporel ou de l'achèvement des tâches.
- Ne jamais lire les risques d'un changement comme des bloqueurs.
- Ne jamais recalculer dans le rapport agrégé une règle d'un service spécialisé.

## Consequences

### Positives

- Chaque constat nomme son sujet, sa preuve et le snapshot dont il vient.
- Un fait manquant est dit manquant : un verdict de transition n'est jamais rendu sur une donnée inventée.
- Le rapport agrégé et ses composants ne peuvent pas diverger.

### Négatives

- Plusieurs faits restent `UNAVAILABLE` en permanence (`designRequired`, les deux faits de critère bloquant) : une
  transition qui les consulte n'est évaluable qu'en mode explicite.
- Un changement sans contrainte ne passe pas `PROPOSED → SPECIFIED` en mode dérivé.

### Risques

- Un critère rattaché à une exigence que le changement ne relie pas par `AFFECTS` n'est pas compté pour ce
  changement : le lien est la seule preuve d'appartenance.
- `knownBlocker` vaut `FALSE` sur un changement sans contrainte (« aucun bloqueur connu ») ; c'est
  `criticalConstraintsKnown` qui porte l'affirmation de complétude.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RequirementQualityContractTest.java` :
  politique de snapshot, `CURRENT` seul, lien entrant ou sortant, orphelin déterministe, contrat de confiance, parité.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TaskAcceptanceQualityContractTest.java` :
  couverture des tâches par `AFFECTS` vers `CURRENT`, cibles `PROPOSED` ou absentes, aucun `Scenario` converti.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeLifecycleQualityContractTest.java` :
  faits tri-état, transition non évaluée sur fait indisponible, mode explicite, changement absent refusé.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeCompletenessAbsentObservationContractTest.java` :
  population vide de contraintes `UNAVAILABLE`, critères rattachés aux exigences, `FALSE` observé.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/DecisionReferenceQualityContractTest.java` :
  décisions tracées ou non, justification indisponible, code exact par état externe.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AggregateQualityReportContractTest.java` et
  `QualityReportSnapshotCoherenceTest.java` (même répertoire) : métriques, findings distincts et triés, cohérence
  stricte du snapshot, cycle de vie hors agrégat.
- Les records `QualityFinding`, `RequirementTraceabilityCoverage`, `QualityReportMetrics` et `QualityReport`
  refusent à la construction une confiance incohérente, un ratio incohérent avec ses comptes et des findings agrégés
  différents de ceux des composants.
- Revue : aucune règle transverse n'interdit à une nouvelle analyse de qualité de rendre `FALSE` pour un fait non
  observé.

## References

- [ADR-0004](SEC-0004-local-first-no-llm-core.md)
- [ADR-0039](BE-0039-deterministic-traceability-derivation.md)
- [ADR-0041](DATA-0041-snapshot-external-reference-traceability.md)
- [ADR-0044](DATA-0044-snapshot-business-content-projection.md)
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md)
- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md)
- [ADR-0108](BE-0108-a-response-says-what-it-could-not-observe.md)
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md)
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md)
- [ADR-0122](ARCH-0122-lectures-metier-sur-le-snapshot-publie.md)
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md)
- Preuve de jalon : [VALIDATION_M6](../../project/validation/VALIDATION_M6.md)
