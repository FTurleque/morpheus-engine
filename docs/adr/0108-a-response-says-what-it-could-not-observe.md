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

## Amendement du 24 septembre 2026 (PRV-4) — quand le modèle n'a pas d'« inconnu », la frontière refuse l'absence

### Constat

L'audit indépendant du 24/09/2026 relève la même faute que les trois constats d'origine, à l'entrée plutôt qu'à la
sortie. `StructuredMarkdownSpecificationContentReader` lisait le champ `completed` d'un bloc `morpheus task` par
`parseStrictBoolean(parsed.block, "completed", false)` : la stricture portait sur la **valeur** (`true`/`false`, rien
d'autre), jamais sur la **présence**. Un bloc muet devenait `false`. Tous les autres champs du bloc passaient par
`required(...)`. Ce `false` inventé était ensuite persisté (`SqliteSnapshotBusinessContentWriter`), rendu
`[pending]` par le contexte augmenté (`AugmentedContextService`) et servi `"completed": false` par la route de
change (`MorpheusChangeQueryApiService`) : une observation, là où la source n'avait rien dit.

### Décision

**Quand le modèle publié ne porte pas d'état « inconnu » pour un champ, la frontière d'entrée refuse l'absence
plutôt que d'inventer une valeur.** C'est la règle de cet ADR appliquée à l'endroit où la réponse naît : une réponse
ne peut pas dire ce qu'elle n'a pas pu observer si la lecture a déjà remplacé le silence par une valeur.

`completed` est désormais obligatoire dans un bloc `morpheus task`, au même titre que `key`, `change` et `title`, et
reste contraint à `true`/`false`. L'absence est refusée par le même `required(...)` que les autres champs, donc avec le
même message, qui nomme le bloc et sa ligne (`missing 'completed' in task block at line N`) et aucun chemin d'hôte.
Refuser l'absence est la même discipline que refuser une valeur non canonique : les deux sont des entrées que le
lecteur ne sait pas traduire sans deviner.

`verification_status` du bloc `acceptance` garde son défaut `UNKNOWN` : ce défaut est un état du modèle, pas une
valeur inventée. C'est exactement la distinction que trace cet amendement.

### Alternatives écartées

- **Un tri-état au domaine.** `ImplementationTask.completed` est un `boolean`. Lui donner un état inconnu toucherait le
  schéma SQLite, les réponses HTTP, MCP et CLI, et le contexte augmenté : c'est un changement du modèle publié, pas un
  correctif de frontière. À reconsidérer si un besoin réel d'« achèvement inconnu » apparaît — par exemple un provider
  dont la source ne peut structurellement pas le dire.
- **Garder le défaut et ajouter un avertissement.** La réponse continuerait de porter `"completed": false` ; un
  avertissement que le champ contredit ne satisfait pas la règle de cet ADR. Le lecteur d'une réponse lit le champ,
  pas le diagnostic d'ingestion.

### Conséquences

- **Rupture de format annoncée.** Un fichier `morpheus/specification.md` dont un bloc `task` omet `completed` était
  accepté jusqu'à 1.2.0 ; il est refusé à partir de 1.2.1. La note de rupture et la migration (ajouter
  `completed=false` ou `completed=true`) sont dans `docs/release/RELEASE_NOTES_1.2.1.md`, le format du bloc dans
  `docs/user/QUICKSTART.md`. Vérifié à la date de cet amendement : aucune fixture, ressource de test, doc ni script
  du dépôt ne s'appuyait sur le défaut implicite.
- **Preuve.** `StructuredMarkdownStrictValidationTest` refuse un bloc sans `completed` en assertant le message
  exact (bloc et ligne) et l'absence de chemin d'hôte, et vérifie qu'un `completed` explicite est lu tel que déclaré.

## Amendement du 25 septembre 2026 (QLT-1, QLT-2) — un flux vide n'est pas un fait affirmatif

`ChangeCompletenessService` calculait `criticalConstraintsKnown` par `allMatch`, vrai d'un flux vide : un change sans aucune contrainte
ingérée affirmait `TRUE`, alors que le même bloc code la prudence inverse pour `planPresent` (`count > 0 ? TRUE : UNAVAILABLE`). Un
flux vide est explicitement `UNAVAILABLE` ; le test est écrit (`isEmpty()`) plutôt que caché dans l'expression.

QLT-2, même bloc : les critères d'acceptation ne comptaient que ceux dont le `changeId` correspond, alors qu'un critère ne doit référencer qu'un
changement **ou** une exigence ; et l'absence était convertie en `FALSE` définitif. Sont maintenant comptés aussi les critères rattachés aux
exigences courantes du change (celles de ses liens `AFFECTS`) ; un compte nul vaut `FALSE` si au moins une exigence est résolue,
`UNAVAILABLE` sinon (les critères d'exigence ne peuvent alors pas être énumérés).

### Verdict sur les autres faits de `ChangeLifecycleFactAssessment`

- `requirementsIdentified = of(count > 0)` : `FALSE` observe l'absence de lien `AFFECTS` vers une exigence courante dans le snapshot (le
  magasin de traçabilité est complet pour un snapshot publié) et le finding `CHANGE_WITHOUT_CURRENT_REQUIREMENT` le dit. Observation, inchangé.
- `designDecisionsAvailable = of(count > 0)` : décisions filtrées sur le change, requête complète. Observation ; le consommateur ne lit
  `FALSE` que si `designRequired` est `TRUE`, et il est `UNAVAILABLE` en dur. Inchangé.
- `planPresent` : déjà `TRUE`/`UNAVAILABLE`. `designRequired`, `blockingAcceptanceCriterionFailed`, `blockingAcceptanceCriterionUnverified` :
  déjà `UNAVAILABLE` en dur.
- `knownBlocker` : `FALSE` sur zéro contrainte signifie « aucun bloqueur *connu* », vrai littéralement ; c'est `criticalConstraintsKnown`, ici
  corrigé, qui porte l'affirmation de complétude. Inchangé, à reconsidérer si la transition `PLANNED → IMPLEMENTING` doit exiger les deux.

### Alternatives écartées

- **Traiter zéro contrainte comme « connu » explicitement.** C'est le comportement corrigé : l'absence d'ingestion et l'absence de contrainte
  sont indiscernables pour le snapshot.
- **Élargir aux sept autres faits.** Aucun n'est démontré comme une non-observation convertie ; la liste ci-dessus dit pourquoi.

### Conséquences

- **Rupture annoncée** (notes de version) : un change sans contrainte ne passe plus `PROPOSED → SPECIFIED` sans les avoir déclarées.
- **Résidu assumé.** Un change dont les critères sont tous rattachés à des exigences non liées par `AFFECTS` reste `UNAVAILABLE`, pas `TRUE` : le
  lien est la seule preuve d'appartenance que le snapshot fournit.
- **Preuve.** `ChangeCompletenessAbsentObservationContractTest` : flux vide → `UNAVAILABLE`, contraintes connues → `TRUE`, contrainte inconnue →
  `UNAVAILABLE`, transition non évaluée sur le change vide et permise avec critères d'exigence, `FALSE` observé, `UNAVAILABLE` sans lien.

## Amendement du 25 septembre 2026 (INT-1) — une recherche bornée rend une indisponibilité, pas une absence

Un résultat borné par une taille de page ne dit rien de ce qui se trouve au-delà. Le résolveur MINOS filtrait sur l'égalité
exacte une page d'au plus 1000 symboles (le filtre prouve que MINOS rend des correspondances non exactes, donc que la borne
peut mordre) et répondait `NOT_FOUND` ; le service de résolution en faisait `TARGET_REMOVED`. Rien ne transportait la troncature :
le port ne rendait qu'une liste, et le contrôle du gateway comparait la page à sa propre taille.

`MinosCodeGateway.findSymbols` rend désormais un `SymbolSearch(symbols, possiblyTruncated)`. MINOS ne fournit ni total ni drapeau
(l'enveloppe ne porte que `count`) : une page qui atteint la limite demandée est déclarée *possiblement tronquée*. Sans correspondance
exacte, le résolveur rend `unavailable()` si la recherche est possiblement tronquée, `notFound()` sinon. `ambiguous()` et
`revisionMismatch()`, en amont du filtre, ne changent pas ; une correspondance exacte présente dans une page tronquée reste `FOUND`.

### Alternatives écartées

- **Paginer jusqu'à l'épuisement.** L'outil MINOS ne fournit pas de curseur ; ce serait inventer un contrat.
- **Aligner sur NEXUS.** `NexusMcpContextGateway.requireCardinality` *refuse* le dépassement (échec de l'intégration entière). Ici le
  dépassement est le cas normal d'une recherche lexicale sur un grand projet et le résultat exact peut être présent : refuser
  perdrait la précision. Les deux intégrations partagent le principe (ne jamais présenter une troncature comme un fait) ; leur
  traduction diffère parce que la donnée diffère.
- **Traiter tout échec de correspondance comme indisponible.** Un symbole réellement supprimé ne serait plus jamais signalé :
  un faux positif échangé contre un faux négatif.

### Conséquences

- **Résidu assumé.** Une page qui contient *exactement* autant de symboles que la limite sans être tronquée est aussi
  déclarée possiblement tronquée : un « indisponible » de trop, jamais un « supprimé » faux.
- **Preuve.** `MinosBoundedSearchResolutionTest` (résolveur + service : `STALE`/`TARGET_UNAVAILABLE`, jamais `TARGET_REMOVED`),
  `MinosMcpExternalReferenceResolverTest`, `MinosMcpTransportIntegrationTest` (le gateway réel dérive le drapeau de la limite).
- **Résidu assumé.** Une correspondance exacte unique dans une page tronquée reste `FOUND` : un second exact au-delà de la page ne serait pas vu (`AMBIGUOUS` manqué). Un `symbolKey` exact est quasi unique et la donnée MINOS ne permet pas de le prouver ; c'est la même famille (« borne prise pour totalité ») que le constat, sur un cas dont la conséquence est moindre.

## Amendement du 25 septembre 2026 (CLI-1) — un refus atteint le code de sortie du processus

La règle de cet ADR vaut aussi pour le processus : un `UNKNOWN` écrit dans le JSON mais absent du code de sortie est
converti en succès par tout ce qui ne parse pas le JSON. `policy evaluate` et `policy dry-run` rendaient `0` pour les
quatre décisions, alors que `docs/user/CLI.md` publie `4` pour « résultat métier non applicable » et prescrit
`if ($LASTEXITCODE -ne 0) { throw }`.

`PASS` et `WARN` rendent `0` ; `BLOCK` et `UNKNOWN` rendent `4` (`STATE_ERROR`), via une seule méthode
(`MorpheusPolicyCli.exitCodeOf`). Le JSON reste imprimé dans tous les cas : un pipeline a besoin du code **et** de la
décision. Les autres actions de `policy` ne portent pas de décision et rendent `0` quand elles réussissent.

### Alternatives écartées

- **`UNKNOWN` → 0 avec un avertissement sur stderr.** C'est la conversion en `PASS` que l'ADR interdit, déplacée d'un
  flux à l'autre.
- **Un code distinct pour `UNKNOWN`.** Le tableau des codes de sortie est un contrat public ; `4` dit déjà « résultat
  métier non applicable ». Un code de plus est un changement de contrat que ce constat ne justifie pas.

### Conséquences

- **Rupture annoncée** dans `docs/release/RELEASE_NOTES_1.2.1.md` ; `scripts/validate-m25.*` attendent `4`.
- **Preuve.** `MorpheusPolicyCliTest` couvre les quatre décisions de bout en bout sur `evaluate`, le rapport agrégé, `dry-run`,
  le maintien de `0` pour les actions de configuration, et l'impression du JSON avec le code `4`.
- **Résidu assumé.** Un scope sans pack actif rend un rapport agrégé `PASS` et donc `0` : aucune règle n'a échoué d'être évaluée, ce n'est pas un `UNKNOWN`. Un pipeline qui croit avoir un contrôle actif ne le voit pas ; c'est une question de sémantique du service d'évaluation (le rapport ne dit pas « aucun pack »), pas de code de sortie, à décider séparément.

## Amendement du 25 septembre 2026 (QRY-1) — ce que le système accepte d'écrire, il sait le relire ; une liste nomme ce qu'elle n'a pas pu lire

`QueryDefinitionCodec` bornait au décodage le nombre de valeurs d'un prédicat avec `MAX_PREDICATES` (le nombre de prédicats d'une requête : deux
grandeurs différentes sous une constante) alors que la boucle `list()` du parseur n'avait aucun compteur, que le validateur ne faisait qu'itérer et
que l'encodage écrivait `values().size()` sans contrôle. Une vue de 65 clés passait parse, validation et écriture, puis n'était plus lisible ; et
`list(scope)` décodant chaque ligne, une seule vue empoisonnée rendait toute la liste du scope inutilisable, sans suppression possible.

- `MAX_PREDICATE_VALUES` (256) sépare les deux grandeurs. Il est appliqué par le parseur (le message nomme la borne et le champ), le validateur
  (donc l'encodeur, qui valide), et le décodeur : une seule constante, la symétrie est dans le code. 256 : la plus grande valeur qui reste sous
  `MAX_ENCODED_EXPRESSION_BYTES` pour des clés d'exigence usuelles avec marge, et qui ne rend lisible que ce qui l'était déjà (toute vue écrite
  avec ≤ 64 valeurs).
- `SavedViewStore.list` rend des `SavedViewEntry` : lisible, ou **illisible** (identifiant, nom, révision, statut, dates, raison de l'échec de
  décodage). Une ligne indécodable n'est ni cause d'échec de la liste ni omise : c'est la règle de cet ADR appliquée à une liste.
- `SavedViewStore.archive` change le statut sans décoder la définition (l'INSERT d'historique copie les colonnes brutes), pour qu'une vue déjà
  empoisonnée puisse être retirée. Refus dans l'ordre inconnu, déjà archivée, révision périmée, comme avant.
- Les vues publiques : une vue illisible est un enregistrement distinct (`UnreadableSavedViewView`, avec `unreadableReason`, sans `query`) ; la forme d'une vue lisible ne change pas d'un octet (une parité de réponses la fige).

### Alternatives écartées

- **Un `catch` dans `list` qui saute la ligne.** Dégradation silencieuse : le défaut d'origine sous une autre forme.
- **Une méthode `unreadable(scope)` à part.** Deux appels que l'appelant peut oublier ; la liste doit dire ce qu'elle n'a pas pu lire elle-même.
- **Une suppression de vue.** Non destructif par doctrine (identité et historique conservés) : l'archivage suffit à retirer de la liste active.
- **Réparer la ligne au démarrage.** Réécrire une donnée que le système ne sait pas lire est de la magie ; l'utilisateur décide.

### Conséquences

- **Résidus assumés.** `versions(id)` d'une vue illisible échoue (le décodage de ses versions passe par `get`) ; `QRY-4` (le budget de
  scope compte les vues archivées, donc monotone) n'est **pas** fermé ici : deux décisions dans une PR sont une PR qu'on ne peut refuser à moitié.
- **Preuve.** `QueryPredicateValuesBudgetTest` (refus au parse nommant borne et champ, aller-retour à exactement la borne, validateur et encodeur),
  `SqliteSavedViewUnreadableRowTest` (liste avec ligne empoisonnée, lecture par id qui dit d'archiver, archivage sans décodage avec historique,
  ordre des refus), `QueryPublicViewsUnreadableTest`.

## Amendement du 26 septembre 2026 (CLI-2) — une sortie texte dit la troncature comme la sortie machine

L'ADR annonçait que « la sortie texte de la CLI ajoute une ligne `truncationReason=…` quand il y en a une ». C'était vrai
de `trace-requirement` et de `change-context`, pas d'`analyze-change`. Dans ce résultat la troncature n'existe que
comme un avertissement `TRACEABILITY_TRAVERSAL_TRUNCATED` portant le détail `truncationReason`, et la sortie texte n'en
imprimait que le nombre (`warnings=N`). Ce nombre ne disait rien : `ACCEPTANCE_CRITERIA_UNAVAILABLE` est ajouté à chaque
analyse, le compteur ne descend jamais sous `1`, et un opérateur n'avait aucun seuil pour distinguer `1` de `2`. Le JSON
portait la liste entière ; les deux formats ne portaient pas la même information.

**Décision.** La sortie texte d'`analyze-change` ajoute, sous la ligne des compteurs qui ne change pas :

- `warningCodes=…` — un code par avertissement compté, dans l'ordre canonique du résultat, pour que le compteur se lise ;
- une ligne `truncationReason=…` par raison distincte portée par un avertissement, sous le nom que les deux commandes
  voisines emploient déjà. Deux noms pour la même chose sur trois commandes du même binaire obligeraient chaque lecteur
  à les connaître tous les deux.

La sélection se fait sur la présence du détail `truncationReason`, pas sur une liste de codes : un avertissement futur qui
porterait une raison de troncature serait dit sans que le CLI ait à le connaître. Le nom du détail est une constante
(`ChangeAnalysisWarning.TRUNCATION_REASON`) que le service et le CLI partagent : un renommage d'un seul côté rendrait la
ligne muette.

**Le code de sortie ne change pas.** CLI-1 a établi qu'un *refus* atteint le code de sortie. Une traversée tronquée n'est
pas un refus : c'est une observation partielle, que cet ADR exige de *dire*, pas de faire échouer. `analyze-change` rend
`0` avec ou sans troncature ; appliquer CLI-1 ici convertirait un résultat utilisable en échec.

### Alternatives écartées

- **Imprimer seulement `truncationReason=`** et laisser le compteur nu. La troncature serait dite, mais `warnings=2`
  resterait illisible pour tout autre avertissement.
- **Une ligne par avertissement avec son message.** Plus bavard sans rien ajouter à ce que le JSON porte déjà ; le texte
  reste une vue compacte.

### Ce que cela coûte

La ligne `warningCodes=` apparaît sur **toute** analyse, saine comprise, avec au moins `ACCEPTANCE_CRITERIA_UNAVAILABLE`.
C'est voulu : c'est ce qui rend le compteur lisible, et c'est le rappel honnête qu'un modèle normalisé ne porte pas de
critères d'acceptation. Un script qui lisait la sortie texte ligne à ligne voit une ligne de plus ; le JSON est produit
par le même chemin qu'avant (`CompactChangeAnalysisViewService`), qu'aucune ligne du correctif ne touche.

### Ce qui reste

- **Le texte ne dit pas où.** Il dit qu'une traversée a été tronquée et pourquoi, pas pour quelle exigence ni dans quelle
  direction ; le JSON porte `requirementId` et `details.direction`. Sur un change à plusieurs exigences, l'opérateur
  repasse en `--json`.
- **La ligne `warningCodes=` n'est pas bornée.** Elle grandit comme la liste d'avertissements du JSON, par exemple d'un
  `TRACEABILITY_PATH_PARTIALLY_RESOLVED` par cible non résolue. Aucun provider ne dérive encore de lien `DEPENDS_ON`, ce
  qui la garde courte aujourd'hui.
- **D'autres sorties texte comptent sans nommer**, hors du périmètre de ce constat : `quality` imprime `findings=N` sans
  les codes, `augmented-context` imprime `items=N` sans le drapeau `truncated` de chaque élément, `reason analyze` n'imprime
  que des compteurs.

**Preuve.** `MorpheusCliTest#aTruncatedAnalysisNamesItsTruncationInTextAsInJsonAndStillSucceeds` publie `openspec-basic`,
écrit dans le snapshot publié un cycle `DEPENDS_ON` entre ses deux exigences — aucun provider ne dérive encore ce type de
lien — et analyse à la profondeur `1` : code `0`, `DEPTH_BUDGET_REACHED:1` dans le JSON et exactement une ligne
`truncationReason=DEPTH_BUDGET_REACHED:1` en texte. Le cycle ne fait manquer aucune dépendance : à la profondeur `1`,
l'arc retour depuis la frontière n'est pas enregistré, ce que le service compte comme une troncature. Le test prouve qu'une
raison émise par le service atteint le texte, pas qu'une analyse a manqué un nœud ; c'est aussi le seul test qui fixe
qu'un arc retour orienté est une troncature `DEPTH`. Le test de l'analyse complète vérifie l'absence de cette ligne et
qu'il y a autant de codes que le compteur en annonce. Retirer la lecture des avertissements fait tomber les deux.

## Amendement du 26 septembre 2026 (CLI-4) — un ratio sur population vide n'est pas publié comme une mesure

`QualityReportMetrics` remplit par `1.0` un ratio dont la population est vide, pour que ses invariants tiennent. La
frontière policy le savait : `DefaultPolicyFactResolver#emptyRatioPopulation` rendait `UNKNOWN` pour un seuil posé sur
l'un des deux ratios. Mais ce prédicat était `private static`, et les autres surfaces ne pouvaient pas l'appeler. `morpheus
quality` imprimait `requirementCoverage=1.0`, et le JSON compact — celui du CLI comme de la route HTTP de diagnostics —
publiait `requirementCoverageRatio: 1.0` sans aucun drapeau. Une même valeur était `UNKNOWN` pour une policy et une
couverture complète pour un script.

**Décision.**

- **Un seul point de vérité.** `QualityReportMetrics#requirementCoverageStatus()` et `#taskCoverageStatus()` rendent
  `CoverageRatioStatus.MEASURED` ou `UNDEFINED_EMPTY_POPULATION`. La frontière policy, la vue compacte et le CLI les
  lisent. La méthode privée de la policy est supprimée, pas contournée : elle ne fait plus que nommer la population
  dans la raison. Une seconde implémentation du prédicat dériverait, et c'est la copie faible qui publierait `1.0` —
  la raison même de la promotion d'`IntegrationStatusDisclosure` et de `ServerLocationDisclosure`.
- **Le texte n'imprime pas un nombre qui n'est pas une mesure** : `requirementCoverage=UNDEFINED_EMPTY_POPULATION`.
- **Le JSON porte l'indéfinition dans un champ frère**, sur le patron d'`acceptanceCoverageStatus` du même objet :
  `requirementCoverageStatus` et `taskCoverageStatus`. Le ratio garde son type et sa valeur.

Les autres ratios ont été cherchés. `AcceptanceCoverageAssessment#verifiedCoverageRatio` remplit lui aussi `1.0` sur
zéro critère, mais aucune surface ne le publie : la vue compacte n'en expose que le statut, qui nomme déjà `NO_CRITERIA`.

### Alternative écartée

- **Rendre le ratio indéfinissable** (`null`, ou une valeur facultative). Il ferme le défaut plus fort — un client naïf
  ne peut plus lire `1.0` — mais change le type d'un champ publié et casse tout client qui le lit comme un nombre, y
  compris pour les projets qui ont des exigences, c'est-à-dire pour tout le monde sauf le cas fautif.

### Ce que cela coûte, et ce qui reste

- **Un client qui ignore le champ frère lit toujours `1.0`.** C'est le prix de la compatibilité ; les notes de version
  le disent et prescrivent de lire le statut d'abord.
- **Le JSON n'est pas identique octet pour octet** pour un projet qui a des exigences : le sérialiseur canonique écrit
  chaque composant d'un record, et un champ facultatif vide y devient `null`. Aucune forme de champ frère ne peut donc
  être absente quand le ratio est mesuré, et les deux statuts s'insèrent juste après leur ratio. Ce qui est garanti :
  aucun champ n'est retiré ni renommé, les ratios restent numériques, et les deux statuts sont les seuls ajouts
  (`aProjectWithRequirementsKeepsItsQualityFieldsAndGainsOnlyTheStatuses` compare l'ensemble des clés). Les valeurs
  viennent des mêmes accesseurs qu'avant ; aucun test ne les compare à une sortie antérieure.
- **La duplication de composition n'est pas couverte ici.** Depuis CMP-1, la policy rend aussi `UNKNOWN` un ratio dont
  la population est dupliquée par une composition multi-provider (`duplicatedPopulation`). Ce prédicat-là lit l'état de
  composition, que `QualityReportMetrics` ne connaît pas ; `quality` publie encore ce ratio comme `MEASURED`. C'est une
  seconde cause d'indéfinition, à porter par le même champ dans un changement distinct.

**Preuve.** `MorpheusCliTest#aProjectWithoutRequirementsOrTasksHasNoCoverageMeasurement` publie un workspace sans exigence
ni tâche et vérifie le texte et le JSON ; forcer `taskCoverageStatus()` à `MEASURED` le fait tomber sur la seule ligne
des tâches. `CoverageRatioHasOneEmptyPopulationPredicateTest` refuse toute comparaison de `totalRequirements()` ou
`totalTasks()` avec zéro ou un, dans les deux sens, hors du record ; le retour du nom `emptyRatioPopulation` ; toute
source hors `application.quality` qui lit un ratio des métriques sans son statut, ou le `coverageRatio()` des records par
population, qui portent le même remplissage sans statut. Réintroduire la copie dans la policy le fait tomber. Il ne voit
pas une vacuité testée sur une autre expression (`isEmpty()` d'une liste), ni un statut lu puis ignoré : il raisonne par
fichier.

## Amendement du 26 septembre 2026 — un refus sur l'état n'est pas un refus d'usage

L'amendement CLI-1 porte une décision sur le code de sortie ; celui-ci porte la même règle sur les refus. `morpheus help`
publie `2` pour l'usage, `3` pour l'entité absente, `4` pour l'état. Les adaptateurs `policy`, `views`/`export`/`query`,
`portfolio` et `server identity` rendaient `2` pour tout refus de leurs services, parce que ces services levaient
`IllegalArgumentException` et que la chaîne de `catch` de l'adaptateur traduit ce type en `USAGE`. Un identifiant qui ne
désigne rien se présentait comme un appel mal formé : le code disait autre chose que ce qui avait été observé. La garde de
l'aide intégrée (`MorpheusHelpInvocationsParseTest`) devait en tenir une liste d'exemptions.

### Décision

- **La règle.** `3` quand un identifiant passé en argument ne désigne rien ; `4` quand ce qui est désigné existe mais que
  la relation ou l'état résultant exigé par l'opération est refusé ; `2` seulement pour un appel mal formé. Un pack inactif
  dans un scope est `4` (le pack est désigné, sa relation au scope manque) ; un override ou une règle introuvable est `3`
  (l'identifiant `--rule` ne désigne rien).
- **Deux exceptions nommées**, dans `com.morpheus.application.store` à côté de `KnowledgeStoreException` :
  `EntityNotFoundException` et `EntityStateException`. Les services (`PolicyPackService`, `PolicyEvaluationService`,
  `SavedViewService`, `QueryExecutionService`, les trois services de portefeuille) **et** les stores mémoire et SQLite qui
  émettent les mêmes messages les lèvent : un refus levé par le store sous une course reçoit le même code que celui levé
  par le service. `MorpheusRemoteIdentityFile` et `RemoteIdentityFileStore` (fichier absent, principal absent ou déjà
  présent, dernier `ADMIN`) les lèvent aussi.
- **Elles restent des `IllegalArgumentException`.** HTTP et MCP ne distinguent pas ces refus aujourd'hui (`400
  BAD_REQUEST`, résultat d'outil en erreur) et leurs contrats ne déclarent pas de `404` sur ces routes : les en faire
  sortir aurait changé deux transports sans que leur contrat le demande. Le précédent existe (`QueryValidationException`).
  Les quatre adaptateurs CLI les interceptent **avant** `IllegalArgumentException`.
- **Un fichier d'identités absent** n'est plus décrit comme « must be a regular non-symbolic file » : l'absence est
  `remote auth file does not exist` (`3`) ; un répertoire ou un lien symbolique garde l'ancien message et le code `2`.
- **`PortfolioRegistryService.markMissing`** vérifiait l'appartenance sans vérifier le portefeuille : un portefeuille
  inconnu était rapporté comme « project is not a portfolio member ». Il passe par `requireMembership`, comme
  `observeFreshness` et `addReference`.
- **La garde de l'aide perd sa liste d'exemptions.** Contre un store vide, aucune invocation documentée ne rend plus `2` :
  tout `2` est désormais une faute de l'aide ou du parseur.

### Alternatives écartées

- **Traduire par préfixe de message dans l'adaptateur.** Le texte deviendrait un contrat implicite, et un message reformulé
  changerait un code de sortie sans que rien ne casse.
- **Des exceptions hors de la hiérarchie `IllegalArgumentException`.** Chaque `catch` HTTP et MCP (ADR-0102) qui
  traduit `IllegalArgumentException` aurait dû les ajouter pour rendre la même réponse qu'avant ; un `catch` oublié devenait un `500`.
- **Des `404`/`409` HTTP dans le même changement.** Décision de contrat HTTP (OpenAPI M23, M24, M25) qui mérite sa propre
  PR ; noté ci-dessous.
- **Une exception par sous-plateforme** (`UnknownPolicyPackException`, …). Les adaptateurs auraient intercepté autant de
  types que de sous-plateformes pour deux codes.

### Conséquences

- **Rupture annoncée** dans `docs/release/RELEASE_NOTES_1.2.1.md`, avec la table avant/après ; règle écrite au §19 de
  `docs/user/CLI.md`.
- **Preuve.** `MorpheusStateRefusalExitCodeTest` asserte le code et le message exact de chaque refus sur la commande qui le
  produit, et le maintien de `2` pour un identifiant malformé, un scope absent et un fichier d'identités qui est un
  répertoire. Six de ses sept tests échouent sur `develop` avant le changement (`expected <3|4> but was <2>`).
  `MorpheusHelpInvocationsParseTest` n'a plus d'exemption.
- **Résidus assumés.** Les refus de budget (packs actifs et overrides par scope, règles évaluées, identités par fichier) et
  `freshness observation must not move backwards` sont des refus sur l'état qui rendent toujours `2`. Un fichier
  d'identités illisible (ligne invalide) rend `2`. HTTP rend `400` pour une entité absente sur les routes M23, M24 et M25.
  `MorpheusServerCli` rend `10` pour une `IllegalStateException` ou une `KnowledgeStoreException`, là où les autres
  adaptateurs rendent `4`. Chacun est une décision séparée.
