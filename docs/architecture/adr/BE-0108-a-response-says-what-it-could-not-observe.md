---
id: BE-0108
title: "Une réponse dit ce qu'elle n'a pas pu observer : traversée bornée, budget de policy, ratio indéfini"
domain: backend
rules: false
---

# Une réponse dit ce qu'elle n'a pas pu observer : traversée bornée, budget de policy, ratio indéfini

- Statut : **Acceptée**
- Date : 2026-09-23

## Context

Une réponse peut se présenter comme complète alors qu'elle a été tronquée, ou comme mesurée alors qu'aucune mesure
n'existe. Trois formes de cette faute concernent `morpheus-application` : une traversée de traçabilité dont seuls les
appelants bornent la profondeur, alors que nœuds et liens grandissent sans plafond ; une garde de policy qui parcourt
toutes les contraintes d'un change avant de conclure `PASS` ; et des ratios de couverture qui valent `1.0` sur une
population vide, lus comme 100 % par une règle « couverture ≥ 80 % ». La même faute existe à l'entrée, quand un lecteur
remplace un champ absent par une valeur, et dans une liste, quand une ligne illisible la fait échouer ou disparaît.

Le dépôt a la doctrine — le tri-état `UNKNOWN` (ADR-0119, ADR-0093) et la règle « borner toute traversée et exposer une
raison de troncature » — et un patron de code, `PortfolioTraversalService`.

Options écartées :

- **Des bornes plus hautes pour ne pas tronquer la fixture large de M19** : ce serait choisir la borne pour éviter de
  modifier un gate ; une fixture qui tronque est une information.
- **`OptionalDouble` dans les records de couverture, ou un ratio rendu `null`** : change le type de champs publiés et
  casse tout client qui les lit comme des nombres, pour tous les projets et pas seulement le cas fautif.
- **`notApplicable` pour un ratio indéfini** : la règle s'applique au projet ; c'est la mesure qui n'existe pas.
- **Deux budgets de policy** (évaluations et entrées d'evidence) : la garde enregistre exactement une entrée par
  évaluation, donc le second serait redondant, mort ou le vrai budget sous un autre nom.
- **Un tri-état pour `ImplementationTask.completed`** : changement du modèle publié (schéma, trois transports, contexte
  augmenté), à reconsidérer seulement si un provider ne peut structurellement pas dire l'achèvement.
- **Paginer MINOS jusqu'à épuisement** : l'outil ne fournit pas de curseur.
- **Sauter une vue enregistrée illisible dans une liste, ou la réparer au démarrage** : dégradation silencieuse, ou
  réécriture d'une donnée que le système ne sait pas lire.

## Decision

La règle commune : **une réponse ne se présente jamais comme complète quand elle a été tronquée, ni comme mesurée quand
la mesure n'existe pas.** Ce qu'elle n'a pas pu observer, elle le dit, avec une raison nommée.

### 1. La traversée de traçabilité est bornée et déclare sa troncature

`TraceabilityTraversalService` porte `MAX_NODES` et `MAX_LINKS`, aux valeurs du portefeuille ; la profondeur est celle
de l'appelant, bornée par les transports. Les raisons sont celles du portefeuille : `NODE_BUDGET_REACHED:<n>`,
`LINK_BUDGET_REACHED:<n>`, `DEPTH_BUDGET_REACHED:<n>`. `TraceabilitySubgraph` porte `Optional<String> truncationReason`
et `truncated()`. Les vues compactes de `trace_requirement` et `get_change_context`, chemin commun de MCP, de HTTP et du
JSON de la CLI, portent la même paire, `truncationReason` (chaîne ou `null`) et `truncated`.

Deux différences avec le portefeuille, dont le comportement n'est pas modifié ici :

- un lien n'est pas retenu s'il mène à un nœud refusé par le budget ;
- `DEPTH_BUDGET_REACHED` n'est déclaré que si un lien reste réellement non observé au-delà de la frontière : un nœud
  plus profond, ou un lien entre deux nœuds de la frontière.

L'analyse d'un change explique ses nœuds sans relancer de recherche : `traverseExplained()` garde, à la découverte de
chaque nœud, le pas qui l'a découvert, et `ExplainedTraversal` n'explique que les nœuds que le sous-graphe rapporte. Un
nœud non atteint n'a pas de chemin et n'est pas présenté comme « sans chemin ». `ChangeAnalysisService` émet
`TRACEABILITY_TRAVERSAL_TRUNCATED`, avec la raison, quand sa traversée est tronquée.

### 2. Un budget d'évaluation de policy, et une page qui n'évalue que sa tranche

`ConstraintEvaluationQueryService` filtre et trie l'ensemble des contraintes du change, ce qui donne le total et l'ordre
déterministe, puis n'évalue que la tranche de la page.

`PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT` borne le parcours d'une garde de contraintes, et donc son evidence.
Si une contrainte reste à observer au-delà du budget, le fait rend `UNKNOWN` avec la raison
`EVALUATION_BUDGET_REACHED:<budget> …`, jamais `PASS`. Un change dont toutes les contraintes sont observées dans le
budget rend `PASS` ; une contrainte `BLOCKING` rencontrée dans le budget rend `FAIL` immédiatement.

### 3. Un ratio sur une population vide n'est pas une mesure

`QualityReportMetrics#requirementCoverageStatus()` et `#taskCoverageStatus()` rendent `CoverageRatioStatus.MEASURED` ou
`UNDEFINED_EMPTY_POPULATION`. Ce prédicat est le seul ; la frontière policy, la vue compacte et la CLI le lisent.

- La policy rend `Fact.unknown(...)` en nommant la population vide pour `REQUIREMENT_COVERAGE_PERCENT` et
  `TASK_COVERAGE_PERCENT`, sans valeur observée. `notApplicable` reste réservé aux désaccords de portée.
- La vue compacte porte l'indéfinition dans des champs frères, `requirementCoverageStatus` et `taskCoverageStatus`,
  sur le patron d'`acceptanceCoverageStatus` ; le ratio garde son type et sa valeur.
- La sortie texte de `quality` n'imprime pas un nombre qui n'est pas une mesure :
  `requirementCoverage=UNDEFINED_EMPTY_POPULATION`.

Les métriques de comptage ne changent pas : `ORPHAN_REQUIREMENTS = 0` sur une population vide est un zéro vrai. Les
records de couverture gardent `1.0` comme convention de validation, et un commentaire y nomme le garde-fou.

### 4. Quand le modèle n'a pas d'« inconnu », la frontière d'entrée refuse l'absence

Une lecture ne remplace pas le silence d'une source par une valeur. Dans un bloc `morpheus task`, `completed` est
obligatoire comme `key`, `change` et `title`, contraint à `true`/`false`, et son absence est refusée par le même
message que les autres champs, qui nomme le bloc et sa ligne et aucun chemin d'hôte. `verification_status` d'un bloc
`acceptance` garde son défaut `UNKNOWN` : c'est un état du modèle, pas une valeur inventée.

### 5. Un flux vide n'est pas un fait affirmatif

`ChangeCompletenessService` rend `criticalConstraintsKnown = UNAVAILABLE` pour un change sans contrainte ingérée, et non
`TRUE`. Les critères d'acceptation d'un change comptent ceux qui lui sont rattachés et ceux des exigences courantes
qu'il affecte (liens `AFFECTS`) ; un compte nul vaut `FALSE` si au moins une exigence est résolue, `UNAVAILABLE` sinon.
`knownBlocker = FALSE` sur zéro contrainte signifie « aucun bloqueur connu » et reste une observation.

### 6. Une recherche bornée rend une indisponibilité, pas une absence

`MinosCodeGateway.findSymbols` rend un `SymbolSearch(symbols, possiblyTruncated)` : une page qui atteint la limite
demandée est déclarée possiblement tronquée, MINOS ne fournissant ni total ni drapeau. Sans correspondance exacte, le
résolveur rend `unavailable()` si la recherche est possiblement tronquée, `notFound()` sinon ; une correspondance exacte
présente dans une page tronquée reste `FOUND`. Une troncature n'est jamais présentée comme un symbole supprimé.

### 7. Ce que le système accepte d'écrire, il sait le relire ; une liste nomme ce qu'elle n'a pas pu lire

`QueryBudgets.MAX_PREDICATE_VALUES` borne le nombre de valeurs d'un prédicat, distinct de `MAX_PREDICATES`. La même
constante est appliquée par le parseur (message qui nomme la borne et le champ), par le validateur, donc par
l'encodeur, et par le décodeur.

`SavedViewStore.list` rend des `SavedViewEntry`, lisibles ou illisibles (identifiant, nom, révision, statut, dates,
raison de l'échec de décodage) : une ligne indécodable ne fait pas échouer la liste et n'en est pas omise. Les vues
publiques la rendent comme `UnreadableSavedViewView` avec `unreadableReason`, sans `query`, et la forme d'une vue
lisible ne change pas. `SavedViewStore.archive` change le statut sans décoder la définition, pour qu'une vue
illisible puisse être retirée.

### 8. La CLI

Le contrat des options de la CLI, de ses codes de sortie et de sa sortie texte (troncature dite en texte comme en JSON,
décision de policy portée par le code de sortie, refus d'état distincts d'un refus d'usage) relève d'ADR-0130.

## Do's and Don'ts

### Do

- Borner toute traversée et rendre une raison de troncature nommée quand une borne mord.
- Rendre `UNKNOWN`, avec la raison du budget, quand une garde n'a pas observé toutes les contraintes.
- Lire `CoverageRatioStatus` avant tout ratio de couverture, sur toute surface.
- Refuser l'absence d'un champ dont le modèle ne porte pas d'état inconnu.
- Rendre `UNAVAILABLE` pour un fait calculé sur un flux vide qui ne prouve rien.
- Appliquer la même borne à l'écriture et à la relecture d'une donnée persistée.
- Rendre dans une liste les entrées illisibles, marquées comme telles.

### Don't

- Ne pas présenter une traversée tronquée comme complète, ni un nœud non atteint comme sans chemin.
- Ne pas conclure `PASS` après un parcours interrompu par un budget.
- Ne pas publier `1.0` comme une mesure sur une population vide, ni réécrire ailleurs le prédicat de population vide.
- Ne pas inventer une valeur par défaut pour un champ absent d'une source.
- Ne pas convertir une recherche possiblement tronquée en `NOT_FOUND` ou en cible supprimée.
- Ne pas sauter ni réparer silencieusement une ligne illisible.

## Consequences

### Positives

- Deux verdicts de policy deviennent honnêtes : un change au-delà du budget sans contrainte bloquante observée, et un
  seuil de couverture sur un projet sans exigence ou sans tâche, rendent `UNKNOWN`. Une policy qui force `UNKNOWN` en
  `BLOCK` par override explicite (ADR-0093) bloque ces deux cas.
- Une trace, une analyse de change, une résolution MINOS et une liste de vues disent ce qu'elles n'ont pas vu.
- Une vue enregistrée illisible reste visible et peut être archivée.

### Négatives

- Les réponses de trace et de contexte de change portent deux champs de plus ; les routes concernées répondent la
  réponse générique `Success` de l'OpenAPI et les outils MCP ne déclarent pas de schéma de sortie.
- La fixture large de M19 se tronque sur `NODE_BUDGET_REACHED`, ce que son gate asserte.
- Un client qui ignore `requirementCoverageStatus` lit toujours `1.0`.
- Un fichier `morpheus/specification.md` dont un bloc `task` omet `completed` est refusé.
- Une page MINOS qui contient exactement autant de symboles que la limite est déclarée possiblement tronquée.

### Risques

- La garde de cycle de vie (`DefaultPolicyFactResolver.lifecycle()`) s'appuie sur `ChangeTransitionEvaluationService`,
  qui évalue toutes les contraintes du change sans budget ; la borner changerait sa sémantique et relève d'une décision
  séparée.
- Un ratio dont la population est dupliquée par une composition multi-provider est `UNKNOWN` pour la policy mais publié
  `MEASURED` par `quality`.
- Une correspondance exacte unique dans une page MINOS tronquée reste `FOUND` : un second exact au-delà ne serait pas vu.
- `versions(id)` d'une vue enregistrée illisible échoue.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/M19TraceabilityPerformanceGate.java` :
  troncature de la fixture large sur le budget de nœuds (lane nocturne).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/CoverageRatioHasOneEmptyPopulationPredicateTest.java` :
  un seul prédicat de population vide, aucun ratio lu sans son statut hors `application.quality`.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java`
  (`aProjectWithoutRequirementsOrTasksHasNoCoverageMeasurement`) : texte et JSON de `quality` sans exigence ni tâche.
- `morpheus-provider-markdown/src/test/java/com/morpheus/provider/markdown/StructuredMarkdownStrictValidationTest.java` :
  bloc `task` sans `completed` refusé avec bloc et ligne, sans chemin d'hôte.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeCompletenessAbsentObservationContractTest.java` :
  flux vide `UNAVAILABLE`, `FALSE` seulement observé.
- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosBoundedSearchResolutionTest.java` :
  recherche tronquée rendue indisponible, jamais supprimée.
- `morpheus-application/src/test/java/com/morpheus/application/query/dsl/QueryPredicateValuesBudgetTest.java`,
  `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSavedViewUnreadableRowTest.java`,
  `morpheus-application/src/test/java/com/morpheus/application/query/dsl/QueryPublicViewsUnreadableTest.java` :
  symétrie écriture-lecture du budget de valeurs, liste et archivage d'une vue illisible.
- Revue : les budgets de traversée et de policy, et la règle « un lien vers un nœud refusé n'est pas retenu ».

## References

- [ADR-0040](BE-0040-bounded-deterministic-traceability-traversal.md) — traversée de traçabilité bornée et déterministe
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md) — chemins d'impact de dépendance explicites et bornés
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs, `UNKNOWN` jamais `BLOCKED`
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — transition tri-état
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité, findings et faits tri-état
- [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) — contrat des options et des codes de sortie de la CLI
- [ADR-0131](BE-0131-pagination-des-collections-et-journaux.md) — pagination des collections et des journaux
