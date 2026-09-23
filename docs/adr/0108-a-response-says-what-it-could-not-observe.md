# ADR-0108 — Une réponse dit ce qu'elle n'a pas pu observer : traversée bornée, budget de policy, ratio indéfini

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 23 septembre 2026
- Dépend de : ADR-0078 (évaluation de transition tri-state), ADR-0093 (policy packs : `UNKNOWN` n'est jamais
  implicitement `BLOCKED`), ADR-0057 (chemins d'impact de dépendance explicites et bornés)
- Précédent d'implémentation : `PortfolioTraversalService` (M23)
- Portée : `morpheus-application` — `TraceabilityTraversalService`, `TraceabilitySubgraph`,
  `ChangeAnalysisService`, `ConstraintEvaluationQueryService`, `DefaultPolicyFactResolver`, `PolicyBudgets` ;
  les vues compactes `trace_requirement` et `get_change_context` et la sortie texte de la CLI pour la restitution

## Contexte

L'audit de code du 22/09/2026 a relevé trois constats qui sont la même faute sous trois formes : une réponse
présentée comme complète alors qu'elle ne l'était pas, ou comme mesurée alors qu'aucune mesure n'existait.

- **APP-1.** `TraceabilityTraversalService.traverse()` n'avait qu'une borne, la profondeur demandée. Les nœuds et
  les liens découverts grandissaient sans plafond, et `TraceabilitySubgraph` n'avait aucun champ pour déclarer une
  troncature : une borne ajoutée n'aurait pas eu de moyen de se dire. `findPath()` avait le même trou, et son
  `Optional.empty()` ne distinguait pas « aucun chemin » de « pas trouvé dans le budget ».
- **APP-2.** `DefaultPolicyFactResolver.constraint()` parcourait toutes les pages d'évaluations de contraintes d'un
  change sans budget, et son evidence grandissait avec le change. Chaque page rechargeait et **réévaluait toutes**
  les contraintes avant de découper la tranche : le parcours était quadratique. Un change gigantesque finissait
  en `PASS` après avoir tout parcouru.
- **APP-4.** `RequirementTraceabilityCoverage`, `TaskRequirementCoverage` et `QualityReportMetrics` valent `1.0`
  sur une population vide. Le résolveur de faits lisait ce `1.0` comme 100 % : un projet sans aucune exigence
  (ingestion vide ou échouée) passait une règle « couverture ≥ 80 % ».

Le dépôt avait déjà la doctrine : le tri-state `UNKNOWN` (ADR-0078, ADR-0093), et la règle « borner toute
traversée et exposer une raison de troncature » (`rules/code-style.md`). Il en avait aussi le patron de code,
`PortfolioTraversalService`. Il manquait de l'appliquer à ces trois endroits.

## Décision

La règle commune : **une réponse ne se présente jamais comme complète quand elle a été tronquée, ni comme mesurée
quand la mesure n'existe pas.** Ce qu'elle n'a pas pu observer, elle le dit, avec une raison nommée.

### 1. La traversée de traçabilité est bornée et déclare sa troncature (APP-1)

`TraceabilityTraversalService` porte `MAX_NODES = 1_000` et `MAX_LINKS = 5_000`. La profondeur reste celle de
l'appelant, déjà bornée à 20 par les trois transports. Les raisons sont celles du portefeuille, à l'identique :
`NODE_BUDGET_REACHED:<n>`, `LINK_BUDGET_REACHED:<n>`, `DEPTH_BUDGET_REACHED:<n>`. Deux traversées du même dépôt qui
s'arrêtent pour la même raison le disent de la même façon.

Les **valeurs** sont aussi celles du portefeuille. Une trace est model-facing : mille nœuds sont déjà bien plus
que ce qu'un modèle peut exploiter dans une réponse. La fixture large de M19 montre qu'une traversée à ce plafond
tient dans le budget de temps figé. Aucune population réelle ne justifiait de s'écarter du portefeuille.

`TraceabilitySubgraph` gagne `Optional<String> truncationReason` et `truncated()`, les noms de
`PortfolioTraversalResult`. Les vues compactes des deux outils en portent la même paire, `truncationReason`
(chaîne ou `null`) et `truncated`, comme la vue publique du portefeuille. Elles sont le chemin commun de MCP,
de HTTP et du JSON de la CLI ; la sortie texte de la CLI ajoute une ligne `truncationReason=…` quand il y en a une.
`ChangeAnalysisService` émet `TRACEABILITY_TRAVERSAL_TRUNCATED`, avec la raison, quand sa traversée de dépendances
est tronquée.

Deux précisions par rapport au précédent :

- **Un lien n'est pas retenu s'il mène à un nœud refusé par le budget.** Le portefeuille enregistre le lien avant
  de vérifier le budget de nœuds, et peut donc rendre un lien dont une extrémité est absente du résultat.
- **`DEPTH_BUDGET_REACHED` n'est déclaré que si un lien reste réellement non observé** au-delà de la frontière :
  un nœud plus profond, ou un lien entre deux nœuds de la frontière. Le portefeuille le déclare dès qu'un nœud de
  la frontière a un voisin, y compris le lien qui mène à son parent, déjà observé. En traversée bidirectionnelle,
  cela revient à dire « tronqué » à chaque fois que la frontière est atteinte. Ce comportement du portefeuille
  n'est pas modifié ici ; il est noté comme écart connu.

`findPath()` est traité dans le même lot, pas reporté. S'il épuise le budget de nœuds avant de trouver sa cible, il
lève `TraceabilityTraversalBudgetException` (message : la raison, `NODE_BUDGET_REACHED:1000`) au lieu de répondre
`Optional.empty()`. `empty()` garde un seul sens : aucun chemin dans la profondeur demandée, qui est la question
posée par l'appelant. Le budget de nœuds, lui, est imposé par le service : ce n'est pas à l'appelant de le lire
comme une absence. Son seul appelant de production, `ChangeAnalysisService`, cherche un chemin vers des nœuds que
la traversée bornée a déjà découverts dans le même ordre BFS, donc dans le même budget. L'exception ne peut donc
pas y survenir, et n'y est pas interceptée.

### 2. Un budget d'évaluation de policy, et une page qui n'évalue que sa tranche (APP-2)

`ConstraintEvaluationQueryService` filtre et trie toujours l'ensemble des contraintes du change : ces deux étapes
donnent le total et l'ordre déterministe, et elles sont bon marché. L'évaluation, elle, se fait après le découpage,
sur la tranche seule. Rien d'observable ne change : mêmes items, même total, même ordre.

`PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT = 1_024` borne le parcours d'une garde de contraintes. Si une
contrainte reste à observer au-delà du budget, le fait rend `UNKNOWN` avec la raison
`EVALUATION_BUDGET_REACHED:1024 …`, jamais `PASS`. Une garde qui n'a pas vu toutes les contraintes du change ne
peut pas affirmer qu'aucune ne bloque. Un change d'exactement 1 024 contraintes, toutes observées, rend toujours
`PASS`. Une contrainte `BLOCKING` rencontrée dans le budget rend toujours `FAIL` immédiatement.

**Un seul budget, pas deux.** La demande initiale prévoyait un plafond d'évaluations et un plafond d'entrées
d'evidence. Or la garde enregistre exactement une entrée d'evidence par évaluation observée, donc un second
plafond serait redondant (s'il est égal), mort (s'il est plus grand) ou le vrai budget de parcours sous un autre
nom (s'il est plus petit). Le Javadoc de la constante dit qu'elle borne aussi l'evidence.

### 3. Un ratio sur une population vide n'est pas une mesure (APP-4)

La correction est à la frontière policy, pas dans les records. Avant de construire un fait mesuré, le résolveur
vérifie si la métrique demandée est un ratio (`REQUIREMENT_COVERAGE_PERCENT`, `TASK_COVERAGE_PERCENT`) calculé sur
une population vide. Si c'est le cas, il rend `Fact.unknown(...)` en nommant la population vide, sans valeur
observée.

**`unknown`, pas `notApplicable`.** La règle s'applique bien au projet ; c'est la mesure qui n'existe pas.
`notApplicable` reste réservé aux désaccords de portée (« constraint guard requires project scope »).

Les **métriques de comptage** ne changent pas. `ORPHAN_REQUIREMENTS = 0` sur une population vide est un zéro
vrai : il n'y a aucun orphelin. Seuls les ratios sont indéfinis sur l'ensemble vide.

Les trois records gardent `1.0` comme convention de validation. Un commentaire, là où la convention est écrite,
dit que ce n'est pas une mesure et nomme le garde-fou côté policy.

## Conséquences

- **Deux verdicts de policy changent**, et c'est voulu. Un change de plus de 1 024 contraintes sans contrainte
  bloquante observée passe de `PASS` à `UNKNOWN`. Un seuil de couverture sur un projet sans exigence (ou sans
  tâche) passe de `PASS` à `UNKNOWN`. Une policy qui force `UNKNOWN` en `BLOCK` par override explicite
  (ADR-0093) bloque désormais ces deux cas.
- **Deux réponses publiques gagnent deux champs de sortie**, `truncationReason` et `truncated`. Vérifié : les
  routes `…/trace` et `…/changes/{changeId}/context` de `docs/openapi/morpheus-v1.yaml` répondent la réponse
  générique `Success`, sans schéma fermé, et `additionalProperties: false` ne porte que sur des entrées. Les
  outils MCP ne déclarent pas de schéma de sortie.
- **La fixture large de M19 se tronque désormais.** Depuis le nœud 0, à profondeur 4 et en bidirectionnel, elle
  atteignait 1 583 des 5 000 nœuds et 2 730 liens. La traversée s'arrête maintenant à 1 000 nœuds sur
  `NODE_BUDGET_REACHED:1000`. `M19TraceabilityPerformanceGate` l'asserte explicitement ; son budget de temps n'est
  pas modifié.
- **Hors périmètre, noté.** `DefaultPolicyFactResolver.lifecycle()` construit son evidence à partir de
  `ChangeTransitionEvaluationService`, qui évalue lui-même toutes les contraintes du change. Ce chemin n'est pas
  borné ici. Son verdict y repose sur une observation complète : le borner changerait sa sémantique, pas seulement
  sa taille. Cette décision mérite donc un examen à part.

## Alternatives écartées

- **Des bornes plus hautes pour ne pas tronquer la fixture M19.** Ce serait choisir la borne pour éviter de
  modifier un gate. La fixture qui tronque est une information, pas un obstacle.
- **Un type résultat pour `findPath()`** (chemin, ou raison de troncature). L'unique appelant de production ne
  peut pas rencontrer le cas ; un échec nommé suffit, et il est plus difficile à ignorer qu'un champ.
- **`OptionalDouble` dans les records de couverture.** Cela aurait modifié `QualityReport`, la vue compacte et
  trois sorties publiques pour un gain nul. Le mal est là où la convention devient un verdict.
- **`notApplicable` pour un ratio indéfini** : voir §3.
- **Deux budgets de policy** : voir §2.
