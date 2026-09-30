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

## Amendement du 26 septembre 2026 (CLI-7) — une valeur fournie vide n'est pas une option absente

`SimpleOptions.optional` taillait la valeur puis la jetait si rien ne restait. Une option **fournie** avec une valeur
vide ou blanche devenait une option **non fournie** : la conversion d'une observation en absence d'observation que cet
ADR interdit, à la frontière même. Les conséquences sont celles de CLI-3, par la valeur au lieu du nom :
`portfolio references --project ""` rendait toutes les références du portefeuille, `add-project --workspace ""`
persistait une appartenance sans workspace, `query … --limit ""` retombait sur la valeur par défaut, `policy evaluate
--id ""` évaluait tous les packs de la portée — sans rien signaler, avec le code de la décision obtenue sur la mauvaise
population (`0` quand elle passe). `rejectUnknown` ne l'attrapait pas : la clé est juste.

**Décision.** `SimpleOptions` refuse une valeur vide ou blanche, avec le nom de l'option :
`--project requires a non-blank value; omit the option to leave it unset`. Le refus est une `IllegalArgumentException`,
qui tombe dans la branche `USAGE` des trois adaptateurs (`MorpheusPortfolioCli`, `MorpheusQueryCli`,
`MorpheusPolicyCli`) : code `2`. Il a lieu dans `rejectUnknown`, **après** le contrôle des clés : une première version le
faisait dans `parse`, et `--projet ""` recevait alors le conseil de renseigner une option qui n'existe pas au lieu de
`unknown option: --projet` ; une action inconnue perdait de même son message. Une lecture par `optional` ou `required`
refuse aussi le blanc, pour le cas où une valeur serait lue avant `rejectUnknown`. « Blanc » est ce que `trim()` vide —
le même test que celui du lecteur, pour qu'une valeur faite de caractères de contrôle ne passe pas la garde puis
devienne vide à la lecture.

Le refus est global parce que chacun des dix-neuf sites de lecture facultative a été examiné et qu'aucun ne donne à la
valeur vide un sens que l'omission n'a pas déjà :

| Adaptateur | Option (site) | Une valeur vide y avait-elle un sens ? |
|---|---|---|
| portfolio | `--workspace`, `--repository`, `--providers` (`add-project`) | Non : l'omission enregistre déjà l'appartenance sans ces observations. |
| portfolio | `--revision`, `--explanation` (`freshness`) | Non : chaque appel enregistre une observation neuve ; rien n'est à effacer, l'omission dit « aucune ». |
| portfolio | `--source-locator`, `--evidence` (`add-reference`) | Non : même raison, création seulement. |
| portfolio | `--project` (`references`) | Non : c'est le défaut constaté, le filtre disparaissait. |
| portfolio | `--direction` (`traverse`) | Non : l'omission vaut `BOTH`. |
| portfolio | `--offset`, `--limit`, `--depth`, `--nodes`, `--links` (lecture entière) | Non : l'omission vaut la valeur par défaut, un vide la masquait. |
| query / views / export | `--filter`, `--sort`, `--fields` | Non : `views update` reconstruit toute la définition, omettre l'option retire déjà le filtre, le tri ou la projection. |
| query / views / export | `--project`, `--portfolio` (portée) | Non : une portée vide n'existe pas. |
| query / views / export | `--offset`, `--limit` | Non : même raison que pour le portefeuille. |
| policy | `--id` (`evaluate`) | Non : l'omission évalue déjà tous les packs actifs. |
| policy | `--project`, `--portfolio` (portée) | Non : une portée vide n'existe pas. |

### Alternatives écartées

- **Un refus par site**, là où la valeur est lue. Il aurait laissé à chaque futur site le soin de s'en souvenir — la
  forme même du défaut — sans qu'aucun site actuel n'en ait besoin.
- **Traiter le vide comme une valeur** (le transmettre tel quel). Il ferait échouer plus loin, avec un message qui ne
  nomme pas l'option (`Invalid UUID string: `), ou persisterait une chaîne vide là où une absence était attendue.

### Ce qui reste

- **Les autres familles de parseurs** ont le même motif, là où elles ont une lecture facultative qui filtre le vide :
  `MorpheusCli.CommandOptions.optional`, `MorpheusCompositionCli.Options.optional` (`composition sync --revision ""` publie
  sans révision), et les `optional` des parseurs d'`acceptance-criteria`, de `constraints` et de `lifecycle`. Les `Options`
  d'`external-references` et d'`augmented-context` refusent déjà le blanc dans `required`. `GlobalArgs` lit
  `--data-dir ""` comme le répertoire courant. Rien de cela n'est touché ici — une PR qui change quatre parseurs ne peut
  pas être refusée à moitié — et reste à traiter avec la même décision.
- **Le transport HTTP garde le défaut** pour les mêmes capacités : `GET …/portfolios/{id}/references?projectId=` lit un
  paramètre vide comme absent et rend toutes les références, et un `filter` blanc dans une requête de query est ignoré.
  Le MCP refuse le blanc (`McpArguments.optionalString`). Après ce changement, le CLI et le MCP refusent là où le HTTP
  accepte en silence : c'est une divergence de convergence, à fermer sur la route.
- **Une liste faite de séparateurs** (`--providers ","`, `--sort ","`) passe le refus et vaut une liste vide, donc
  l'omission : le même défaut, au niveau des éléments.
- **Le message d'une option obligatoire passée vide change** : `--… requires a non-blank value` au lieu de
  `--… is required`. Le code reste `2`. Aucun test, validateur ni document du dépôt ne branchait sur l'ancien message
  pour une valeur vide.

**Preuve.** `SimpleOptionsTest` refuse `""`, `" "`, une tabulation, `"   "` et un caractère de contrôle, au contrôle des
clés comme à la lecture ; une option inconnue passée vide reste `unknown option` ; l'absence et le message d'obligation
sont inchangés. De bout en bout : `portfolio references --project ""` et `"   "` rendent `2` en nommant
`--project` ; `add-project --workspace ""` rend `2` et `members` ne contient pas le projet, alors que la même commande
sans l'option l'enregistre ; `query execute --limit ""` et `--filter "  "` rendent `2`, sans l'option `0` ;
`policy evaluate --id ""` rend `2`. Remettre l'ancien `SimpleOptions` fait tomber les quatre, chaque fois avec le code
`0` qui était le défaut.

## Amendement du 26 septembre 2026 (CLI-7, suite) — la même décision pour toutes les familles de parseurs

L'amendement CLI-7 a refusé la valeur vide dans `SimpleOptions` et laissé les autres familles de parseurs du CLI en
l'état. Elles portaient le même défaut, sous trois formes : un `optional` qui filtrait le vide et le changeait en option
absente ; une lecture de chemin où `Path.of("")` devenait **le répertoire courant du processus** ; et des lectures
obligatoires qui refusaient déjà le blanc, mais avec leur propre message (`--… is required`, `missing required option`,
`--host must not be blank`) et leur propre définition du blanc (`isBlank`, qui laisse passer un caractère de contrôle
que `trim` vide ensuite).

**Décision.** La décision CLI-7 s'applique à tous les parseurs du CLI : une option passée avec une valeur vide ou
blanche est refusée à la lecture, code `2`, avec le message de `SimpleOptions` :
`--x requires a non-blank value; omit the option to leave it unset`. Le refus a une seule implémentation,
`OptionValue.nonBlank` (et `OptionValue.path` pour une option qui désigne un chemin), que `SimpleOptions` utilise aussi.
Chaque famille l'appelle à son point d'entrée des valeurs :

- les parseurs **ouverts**, qui acceptent toute clé puis la vérifient (`MorpheusCli.CommandOptions`, les `Options` de
  `composition` et d'`external-references`), l'appellent comme `SimpleOptions` : dans `rejectUnknown`, **après** le
  contrôle des clés, et à la lecture. `CommandOptions` et les `Options` d'`external-references` gardent leurs valeurs
  dans un `Map.copyOf`, dont l'ordre d'itération est salé par JVM : le contrôle les parcourt dans l'ordre des clés, pour
  que l'option nommée quand deux sont vides ne dépende pas de l'exécution ;
- les parseurs **fermés par `switch`** (`acceptance-criteria`, `constraints evaluate`, `lifecycle`,
  `change-orchestration`, `augmented-context`) l'appellent dans leur `require`, qui n'est atteint que depuis la branche
  d'une option connue : le refus nomme toujours une option qui existe. Leur `optional` ne filtre plus rien ;
- les parseurs **ad hoc** (`reason`, `update-check`, `provider-plugins`, `server`, et les lanceurs `api`, `mcp --stdio`,
  `api --remote`) l'appellent dans la branche de chaque option reconnue ;
- les **options de disposition** `--data-dir`, `--config-dir`, `--db` passent par `OptionValue.path` dans `GlobalArgs`
  et dans chacune de leurs copies (les sept `Parsed`, `server`, les trois lanceurs, dans les deux orthographes
  `--x v` et `--x=v`). `reason`, `update-check` et `provider-plugins` n'utilisent pas leur valeur, mais la vérifient
  aussi : le même préfixe `morpheus --data-dir "$D"` doit échouer de la même façon quelle que soit la commande.

Chaque site de lecture a été examiné. Aucun ne donne à la valeur vide un sens que l'omission n'a pas déjà — sauf les
chemins, où ce sens existe mais n'est qu'un accident :

| Famille | Option (site) | Une valeur vide y avait-elle un sens ? |
|---|---|---|
| `MorpheusCli` | `sync --revision` | Non : le vide publiait sans révision, ce que fait déjà l'omission. |
| `MorpheusCli` | `requirements find --query` | Non : le vide et l'omission cherchaient tous deux sans texte. |
| `MorpheusCli` | `--offset`, `--limit`, `--depth`, `--max-age-minutes` | Non : déjà refusés (`must be an integer`) ; le message nomme maintenant le vide. |
| `MorpheusCli` | `--project`, `--change`, `--requirement`, `--workspace` (obligatoires) | Non : déjà refusés (`is required`). |
| `composition` | `sync --revision` | Non : le vide publiait sans révision, comme l'omission. |
| `external-references` | `--project`, `--owner`, `--reference` (obligatoires) | Non : déjà refusés, sauf un caractère de contrôle, que `isBlank` laissait passer et qui finissait en `Invalid UUID string`. |
| `acceptance-criteria` | `--change`, `--requirement` | Non : le vide retirait le filtre et listait **tous** les critères du projet ; l'omission le fait déjà. |
| `acceptance-criteria`, `constraints evaluate` | `--offset`, `--limit` | Non : le vide retombait sur la valeur par défaut. |
| `lifecycle apply` | `--abandonment-reason` | Non : le vide valait « pas de raison », ce que dit l'omission ; la politique réclame la raison quand elle est due. |
| `change-orchestration` | `state --lifecycle` | Non : le vide valait « cycle de vie non observé » (`UNAVAILABLE`) ; un appelant qui n'a rien observé omet l'option. |
| `change-orchestration` | `--abandonment-reason`, `--from-abandonment-reason` | Non : le vide valait l'omission (`REQUIRES_INPUT`). |
| `augmented-context` | `--source`, `--constraint`, `--budget`, `--nexus-project`, sujets | Non : tous déjà refusés, plus loin et sans nommer le vide (`unsupported technical context sources: []`, `constraint key must not be blank`, `is required`). |
| `reason analyze` | `--question`, `--evidence`, `--adapter`, `--param`, `--max-claims` | Non : tous déjà refusés par le parseur ou par le contrat de raisonnement. |
| `update-check`, `provider-plugins` | `--manifest` ; `--directory`, `--plugin`, `--workspace`, `--sha256` | Non : tous obligatoires, déjà refusés (`missing required option`). |
| `server` | `backup create --output-dir` | Non : le vide était `Path.of("")` ; la commande durcissait les permissions **du répertoire courant** puis y écrivait la sauvegarde, code `0`, au lieu du répertoire de sauvegarde configuré que donne l'omission. |
| `server` | `identity … --auth-file` | Non : le vide désignait le répertoire courant, pas le `remote-auth.txt` du répertoire de configuration que donne l'omission. |
| `server` | `migrate-legacy --principal`, et les obligatoires `--principal`, `--role`, `--expires-at`, `--file` | Non : déjà refusés (`is required`, ou le codec d'identité). |
| disposition | `--data-dir`, `--config-dir`, `--db` (toutes les familles) | **Oui, par accident** : `Path.of("")` est le répertoire courant, là où l'omission prend l'environnement ou la valeur par défaut de la plateforme. `paths` l'affichait ; une commande qui ouvre le store le faisait dans `./morpheus.db`. Ce sens a une orthographe explicite, `.` ; le vide est refusé. |
| `api`, `mcp --stdio` | `--host`, `--port`, disposition | Non : `--host` et `--port` déjà refusés ; la disposition, comme ci-dessus. |
| `api --remote` | `--workspace-root` | Non, et c'était le plus coûteux : le vide ajoutait **le répertoire courant** aux racines de workspace autorisées du serveur distant, un élargissement silencieux de sa liste d'autorisation. |
| `api --remote` | `--auth-file`, `--tls-keystore`, `--provider-plugin-dir` | Non : le répertoire courant tenait lieu de fichier d'identités, de keystore ou de répertoire de plugins découverts. |
| `api --remote` | `--host`, `--port`, `--max-concurrent` | Non : déjà refusés. |

Les variables d'environnement (`MORPHEUS_DATA_DIR` vide, par exemple) ne sont **pas** concernées : une variable vide
reste lue comme non définie. Beaucoup de shells et de gestionnaires de services ne distinguent pas une variable vide
d'une variable absente ; une option de ligne de commande, elle, est écrite par l'appelant.

### Alternatives écartées

- **Ne refuser que là où le vide changeait le résultat** (filtres, pagination, `--revision`, chemins) et laisser les
  lectures qui refusaient déjà. Elles refusaient avec des messages différents et deux définitions du blanc : un script
  qui passe une variable vide recevait `is required` d'une commande, `must be an integer` d'une autre, et un caractère
  de contrôle passait l'une et pas l'autre.
- **Un contrôle dans `CliLayout.resolve`** pour les trois options de disposition, en un seul endroit. `Path.of(" ")` lève
  déjà sous Windows (`InvalidPathException`, sans nom d'option) avant d'y arriver : le refus aurait dépendu de la
  plateforme. Le contrôle précède `Path.of`.
- **Refuser dans `parse` pour les parseurs ouverts.** C'est l'erreur corrigée dans CLI-7 : `--projet ""` aurait reçu le
  conseil de renseigner une option qui n'existe pas.

### Ce qui reste

- **Deux commandes ouvrent le store avant de vérifier leurs options.** `projects add --workspace ""` et
  `changes list --project P --limit ""` ouvrent (et créent au besoin) la base avant le refus ; rien n'est écrit, le test
  le vérifie pour `projects add`. `external-references list|resolve` n'en fait plus partie : CLI-8 y a placé le contrôle
  des options avant l'accès à l'état, et ce contrôle portant le refus du vide, `--owner ""` rend `2` même pour un projet
  inconnu.
- **Aucune garde ne vérifie qu'un futur parseur appelle `OptionValue`.** La preuve est comportementale et tenue par une
  table écrite à la main : une option ajoutée sans ligne dans la table n'est pas couverte. La garde de CLI-8
  (`CliOptionParsingRefusesUnknownOptionsTest`) vérifie la présence de `rejectUnknown`, pas celle du refus du vide.
- **Une option répétée** garde sa dernière valeur en silence dans `GlobalArgs`, les copies de disposition, les
  lanceurs, `update-check` et `provider-plugins` (`--data-dir a --data-dir b` vaut `b`) : un *last-write-wins* que les
  autres parseurs refusent (`duplicate option`). Défaut distinct, non traité ici.
- **Le blanc est ce que `trim()` vide**, comme dans CLI-7 : un espace Unicode (`U+2003`) n'est pas blanc et passe.
- Les points laissés par CLI-7 restent ouverts : la route HTTP `…/references?projectId=` et une liste faite de
  séparateurs (`--providers ","`).
- **Les messages changent** pour une valeur vide là où la lecture refusait déjà : `--… is required`,
  `missing required option --…`, `--host must not be blank`, `--… must be an integer`, `manifest must not be blank`
  deviennent `--… requires a non-blank value; omit the option to leave it unset`. Le code reste `2`. Un seul test du
  dépôt branchait sur l'ancien message (`MorpheusAugmentedContextCliTest`, `--project " "`) ; il est mis à jour.

**Preuve.** `BlankOptionValueRefusalTest` passe chaque option de chaque parseur hors `SimpleOptions`, avec `""` et
`" \t "`, par `MorpheusMain.run` : code `2`, message nommant l'option, et le répertoire propre à l'invocation — qui
contient son répertoire de données et tout fichier désigné — reste **absent** : le refus n'écrit rien. Les trois
lanceurs sont passés à leur `parse`, dans les deux orthographes. `projects add --workspace ""` n'enregistre aucun
projet. Tests ciblés, qui échouent tous avec le code `0` sur les sources de CLI-7 : `sync --revision ""` ne publie rien
(`lastSuccessfulSyncAt` reste nul) et la même commande sans l'option publie ; `--data-dir "" paths` est refusé ;
`composition sync --revision ""` laisse l'état de composition identique, et sans l'option la synchronisation passe ;
`acceptance-criteria list --change ""` et `--limit " "` sont refusés, sans l'option la liste rend les deux critères ;
`constraints evaluate --limit ""` est refusé ; `change-orchestration state --lifecycle ""` et
`transition-check --abandonment-reason "  "` sont refusés, sans l'option l'état reste `UNAVAILABLE`.
`server backup create --output-dir ""` est refusé sans rien écrire dans le répertoire courant ni dans le répertoire de
sauvegarde, et sans l'option la sauvegarde est créée dans ce dernier.

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

## Amendement du 26 septembre 2026 (CLI-7, répétition) — une option donnée deux fois est refusée

L'amendement CLI-7, suite, a laissé ouvert un défaut voisin, sur les mêmes parseurs : une option à valeur **répétée**
gardait sa dernière valeur en silence. `morpheus --data-dir a --data-dir b projects list` lisait le store de `b`, code
`0`, sans rien dire de `a`. Les parseurs qui rangent leurs options dans une table refusaient déjà la seconde clé
(`SimpleOptions`, `MorpheusCli.CommandOptions`, les `Options` de `composition`, d'`external-references`,
d'`acceptance-criteria`, de `constraints`, de `lifecycle`, de `change-orchestration` et d'`augmented-context`, avec
`duplicate option: --x`). Les autres affectaient chaque valeur à une variable, et la seconde écrasait la première :

| Parseur | Options qui gardaient la dernière valeur |
|---|---|
| `GlobalArgs` (`MorpheusCli`, `policy`, `query`/`views`/`export`, `portfolio`) | `--data-dir`, `--config-dir`, `--db` |
| les sept copies `Parsed` (`acceptance-criteria`, `augmented-context`, `composition`, `constraints`, `lifecycle`, `external-references`, `change-orchestration`) | `--data-dir`, `--config-dir`, `--db` |
| `server` (`MorpheusServerCli.parse`) | `--data-dir`, `--config-dir`, `--db`, dans les deux orthographes |
| `api` (`ApiLaunchOptions`) | `--host`, `--port`, `--data-dir`, `--config-dir`, `--db`, dans les deux orthographes |
| `mcp --stdio` (`McpLaunchOptions`) | `--data-dir`, `--config-dir`, `--db`, dans les deux orthographes |
| `api --remote` (`RemoteApiLaunchOptions`) | `--host`, `--port`, disposition, `--auth-file`, `--tls-keystore`, `--provider-plugin-dir`, `--max-concurrent`, dans les deux orthographes |
| `update-check` (`MorpheusProductCli`) | `--manifest` (`options.put`) ; la disposition, lue puis ignorée |
| `provider-plugins` (`MorpheusProviderPluginCli`) | `--directory`, `--plugin`, `--workspace`, `--sha256` (`options.put`) ; la disposition, lue puis ignorée |
| `reason analyze` (`MorpheusReasoningCli`) | `--max-claims` ; la disposition, lue puis ignorée |

`reason analyze --max-claims` n'était pas dans le constat de départ : il a été trouvé en balayant les affectations des
parseurs. Aucune de ces options ne donne à une répétition un sens — ce ne sont pas des listes. C'est le
*last-write-wins* silencieux que `rules/code-style.md` interdit : un conflit se signale.

**Décision.** Une option à valeur unique donnée plus d'une fois est refusée à la lecture des arguments, code `2`, avec
le libellé des parseurs à table : `duplicate option: --x`. Le refus a une seule implémentation,
`OptionOccurrence.once`, qui enregistre le **nom** de l'option, jamais le jeton :

- **Les deux orthographes sont une seule option.** Là où un parseur reconnaît `--x v` et `--x=v` (`server` et les trois
  lanceurs), `--data-dir a --data-dir=b`, `--data-dir=a --data-dir b` et `--data-dir=a --data-dir=b` sont des doublons
  comme `--data-dir a --data-dir b`. Une orthographe n'est qu'une façon d'écrire la même valeur ; laisser l'une
  remplacer l'autre aurait gardé le défaut sous une forme plus difficile à voir.
- **Là où l'orthographe `=` n'est pas reconnue**, elle n'est pas une seconde valeur : `GlobalArgs` ne connaît que
  `--x v`, et `--data-dir a --data-dir=b paths` prend `--data-dir=b` pour le nom de la commande, refusé
  (`unknown command`, code `2`, rien d'écrit). Ce n'était déjà pas un *last-write-wins* ; rien n'est changé.
- **Les sept copies `Parsed` délèguent à `GlobalArgs.parse`.** Elles en étaient des copies à l'octet près ; corriger
  huit boucles identiques aurait laissé à la prochaine copie le soin de s'en souvenir. `GlobalArgs` reste la seule
  lecture de la disposition pour ces familles.
- **`--workspace-root` accumule.** Il nomme une liste de racines autorisées ; le répéter, dans l'une ou l'autre
  orthographe, en ajoute une. C'est la seule option à valeur exemptée.
- **Les drapeaux sans valeur ne sont pas concernés.** `--json`, `--stdio` et `--remote` répétés ne perdent rien : il n'y
  a pas de première valeur à écraser. Ils restent acceptés. Les drapeaux de commande que leur parseur refusait déjà en
  double (`--confirm`, `--dry-run`, les drapeaux de `MorpheusCli.CommandOptions`) le restent.
- **Le libellé est unifié.** `server` refusait ses options de commande répétées avec `duplicate --x`, `reason` son
  `--question` répété de même ; ils disent maintenant `duplicate option: --x`. Le code ne change pas.

Le refus précède la lecture de la valeur : `--data-dir a --data-dir` est un doublon, pas une valeur manquante. Dans
`update-check`, `provider-plugins` et `reason`, la disposition n'est pas utilisée mais son doublon est refusé quand
même, pour la raison qui y fait refuser le blanc : le même préfixe `morpheus --data-dir "$D"` doit échouer de la même
façon quelle que soit la commande.

### Alternatives écartées

- **Garder la première valeur**, ou la dernière en l'annonçant sur la sortie d'erreur. L'une ou l'autre choisit à la
  place de l'appelant entre deux intentions contradictoires ; un avertissement que personne ne lit ne signale rien.
- **Refuser seulement quand les deux valeurs diffèrent.** `--data-dir a --data-dir a` ne perd rien, mais le refuser
  aussi garde une règle qui se lit dans la ligne de commande, sans comparer de chemins (`a` et `./a` sont-ils égaux ?).
  Les parseurs à table refusaient déjà deux valeurs égales.
- **Traiter `--x=v` comme une option distincte de `--x v`.** Chaque orthographe aurait refusé sa propre répétition, et
  `--data-dir a --data-dir=b` serait resté un *last-write-wins*.
- **Refuser aussi `--json --json`.** Aucune valeur n'est perdue ; un enveloppeur qui ajoute `--json` à une commande qui
  l'a déjà échouerait sans raison.

### Conséquences

- Une invocation qui répétait une option à valeur rend `2` au lieu de `0`. Rien n'est écrit : les lanceurs refusent
  avant d'ouvrir un port ou le transport STDIO, les autres commandes avant d'ouvrir le store.
- Le point « une option répétée » de l'amendement CLI-7, suite, est fermé. Les autres points de sa section « Ce qui
  reste » sont inchangés.
- **Aucune garde ne vérifie qu'un futur parseur appelle `OptionOccurrence`.** Comme pour le blanc, la preuve est une
  table écrite à la main : une option à valeur ajoutée à un parseur à variables sans ligne dans la table n'est pas
  couverte.

**Preuve.** `DuplicateOptionRefusalTest`, par `MorpheusMain.run` : chacune des trois options de disposition, répétée
devant une commande de chacune des quinze familles, rend `2`, nomme l'option, et le répertoire propre à l'invocation
reste **absent** ; `server` refuse la répétition dans les quatre combinaisons d'orthographes ; `update-check --manifest`,
`provider-plugins --directory|--plugin|--workspace|--sha256`, `reason analyze --max-claims|--question` et
`server … --file|--confirm` répétés rendent `2` avec `duplicate option: --x`. Les trois lanceurs sont passés à leur
`parse` pour chaque option à valeur unique, dans les quatre combinaisons, et à `MorpheusMain.runApi|runMcp|runRemoteApi`
pour le code `2`. `--workspace-root` répété dans les deux orthographes donne trois racines ; une option donnée une fois,
dans l'une ou l'autre orthographe, est toujours acceptée. Sur les sources de CLI-7, suite, 136 des 152 cas échouent, dont
`--data-dir first --data-dir second paths` : code `0`, chemins de `second` affichés ; et
`update-check --manifest a --manifest b`, qui lisait `b`. Les 16 cas qui passaient déjà sont ceux qui vérifient ce qui
ne doit pas changer (l'orthographe `=` hors `server`, `--workspace-root`, une option donnée une fois).

## Amendement du 30 septembre 2026 (CLI-10, CLI-11) — un seul prédicat du blanc, et le plus fort des deux

L'amendement CLI-7 a défini le blanc d'une option par `trim()` (« « Blanc » est ce que `trim()` vide »), et
l'amendement CLI-7, suite, en a écrit le résidu : « un espace Unicode (`U+2003`) n'est pas blanc et passe ». Ce résidu
supposait que le refus avait une seule implémentation. Ce n'était pas le cas : six lectures d'options gardaient leur
propre test, `value == null || value.isBlank()` — `MorpheusServerCli.required`, `MorpheusProductCli.Parsed.option`,
`MorpheusProviderPluginCli.Parsed.required`, `MorpheusReasoningCli.Parsed.required`, `ApiLaunchOptions.requireHost` et
`RemoteApiLaunchOptions.requireNonBlank`. La quatrième n'était pas dans le constat d'audit ; elle a été trouvée par la
garde décrite plus bas, qui balaie le module.

Or les deux définitions divergent dans les deux sens. `trim()` retire tout caractère inférieur ou égal à `U+0020`,
caractères de contrôle compris, que `isBlank()` ne tient pas pour blancs. `isBlank()` tient pour blanc tout caractère
que `Character.isWhitespace` reconnaît, dont les espaces Unicode (`U+2003`, `U+3000`), que `trim()` garde. Une valeur
faite d'un `U+2003` passait donc `OptionValue.nonBlank`, puis :

- **était signalée absente** par la copie : `server identity create --principal U+2003` répondait `--principal is
  required`, `reason analyze --question U+2003` répondait `missing required option --question`. C'est la forme exacte
  du défaut CLI-7, survivante dans la copie la plus faible (CLI-10) ;
- **traversait `trim()` intacte** jusqu'à un analyseur qui échouait sans nommer l'option :
  `external-references list --project U+2003` répondait `Invalid UUID string`, le message que l'amendement CLI-7 cite
  comme la chose à ne pas produire, et la base de données était créée avant ce refus (CLI-11) ;
- **ou était acceptée**, comme nom d'un fichier ou d'un répertoire du répertoire courant, ou comme texte.

**Mesure, sur les sources de `933a63fe`.** La table de `BlankOptionValueRefusalTest` (108 invocations, plus 18 options
de lanceur dans leurs deux orthographes) a été rejouée avec `U+2003` et `U+0001`. Avec `U+0001` : aucun échec, le
caractère de contrôle était déjà refusé partout. Avec `U+2003` : **108 invocations sur 108 et 36 cas de lanceur sur 36
échouent** — aucune lecture d'option ne refusait l'espace Unicode en le nommant. Parmi les 108 :

| Effet observé | Invocations |
|---|---|
| Option fournie **signalée absente** (`--… is required`, `missing required option --…`) | 9 |
| `Invalid UUID string`, sans nom d'option | 11 |
| Code `0` : `--data-dir`, `--config-dir`, `--db` devant `paths`, `reason adapters`, `version`, `provider-plugins discover` désignaient un répertoire nommé `U+2003` dans le répertoire courant | 12 |
| Valeur acceptée, échec plus loin pour une autre raison (autre option manquante, état, système de fichiers, codec, entier invalide) | 76 |

Parmi les 76, `--data-dir U+2003` devant une commande qui ouvre le store, et `server backup create --output-dir
U+2003`, ont tenté d'écrire **dans le répertoire courant** : le refus est venu du contrôle d'ACL sur ce répertoire, et
une exécution de la mesure y a laissé un répertoire vide nommé `U+2003`. C'est la conséquence que l'amendement CLI-7,
suite, avait fermée pour `Path.of("")`, rouverte par une autre orthographe du vide. Pour les lanceurs : `--host`
refusé avec son propre message (4 cas), `--port` et `--max-concurrent` refusés comme entiers invalides (6), toutes les
options de chemin acceptées (26).

**Décision.** Une valeur d'option est blanche si **chacun de ses points de code** est blanc pour l'une des deux
lectures : `c <= ' '`, ce que `trim()` retire, ou `Character.isWhitespace(c)`, ce que `isBlank()` retient —
`value.codePoints().allMatch(c -> c <= ' ' || Character.isWhitespace(c))`. C'est l'union des deux définitions, prise
**par caractère**. La première écriture de ce correctif la prenait par chaîne, `value.isBlank() ||
value.trim().isEmpty()`, et laissait passer un mélange : `U+0001` à côté de `U+2003` n'est blanc pour aucun des deux
tests de chaîne, bien que chacun de ses caractères soit blanc pour l'un d'eux. `external-references list --project
U+0001U+2003` rendait alors encore `Invalid UUID string` après avoir créé la base (`trim()` retire `U+0001` et laisse
`U+2003`) : CLI-11 survivait sous cette orthographe. La lecture par caractère est la forme exacte de « l'union » que
demandait le constat ; elle la précise, elle ne la contredit pas. Sur les chaînes pures (vide, contrôle seul, espace
Unicode seul, espace insécable), les deux formes donnent le même verdict — vérifié par exécution ; elles ne diffèrent
que sur les mélanges. `U+0001` suivi d'un vrai texte (`U+0001 x`) reste une valeur.

Le prédicat vit dans `OptionValue.nonBlank` et nulle part ailleurs ; sa Javadoc nomme les deux moitiés et ce que chacune
attrape. Les six copies sont supprimées :

- les quatre lectures d'options obligatoires ne gardent que la question de l'**absence** : `null` répond toujours
  `--x is required` ou `missing required option --x`. Le blanc n'y arrive jamais : il est refusé à l'analyse des
  arguments, par `OptionValue.nonBlank`, dans `MorpheusServerCli.options` (`:361`), `MorpheusProductCli.parse`
  (`:122`), `MorpheusProviderPluginCli.parse` (`:131`) et `MorpheusReasoningCli.parse` (`:158`). Les deux messages
  restent donc distincts — l'absence au lecteur, le blanc à l'analyse — et le lecteur ne refait pas un contrôle
  impossible. Une ligne de commentaire le dit à chaque lecteur. `MorpheusServerCli.required` rend toujours la valeur
  taillée, les trois autres telle quelle. Dans `update-check`, `provider-plugins` et `reason`, la présence est aussi
  vérifiée à l'analyse : la branche `null` y est conservée telle quelle et n'est pas atteignable depuis la ligne de
  commande ;
- les deux `require*` des lanceurs sont supprimés, pour la même raison : ils ne recevaient que des valeurs déjà passées
  par `nonBlank`, leur branche `null` était inatteignable et leur test du blanc était la copie. Le refus est celui de
  `nonBlank`, fait une ligne plus haut ; la valeur retournée reste `value.trim()`. Le message `--host must not be
  blank` disparaît.

`nonBlank` rend toujours la valeur **telle que donnée** : un appelant qui taillait taille encore, un chemin garde son
orthographe. `RemoteApiLaunchOptions.nonBlank(String)`, qui lit une variable d'environnement ou une propriété JVM et
n'appelait pas `OptionValue`, devient `presentSetting` : même comportement, sans l'homonymie qui la faisait lire, à ses
appels, comme la définition.

**Élargissement du refus, mesuré.** Le changement refuse une valeur aujourd'hui acceptée : une valeur faite
uniquement de caractères blancs pour l'une ou l'autre lecture, dont au moins un au-delà de `U+0020`. Les dix-neuf
sites de lecture facultative de l'amendement CLI-7 et la table de l'amendement CLI-7, suite, ont été repris sous cet
angle :

- **identifiants, entiers, énumérations, instants, empreintes, URI** : une telle valeur échouait déjà plus loin
  (`Invalid UUID string`, `must be an integer`, `valueOf`, codec) ; elle échoue maintenant en nommant l'option ;
- **textes libres** (`--query`, `--question`, `--revision`, `--explanation`, `--evidence`, `--actor`,
  `--idempotency-key`, `--filter`, `--sort`, `--fields`…) : une valeur faite de blancs ne porte rien.
  `requirements find --query U+2003` était exactement le défaut CLI-7 : `RequirementSearchQuery` normalise par
  `strip()`, qui vide `U+2003`, et la commande rendait, code `0`, une sortie identique à celle de la même commande
  sans l'option (mesuré sur `openspec-basic`) ;
- **chemins** : c'est le seul cas où un usage disparaît. Un fichier ou un répertoire dont le nom entier est un espace
  Unicode est légal sous Linux et sous Windows, et `--data-dir U+2003` le désignait. Il reste désignable, par une
  orthographe qui n'est pas blanche : `--data-dir ./U+2003`. Aucun test, validateur ni document du dépôt ne passait
  une telle valeur.

Les variables d'environnement ne changent pas (amendement CLI-7, suite) : `CliLayout` et `RemoteApiLaunchOptions`
gardent leur lecture, où une variable vide vaut une variable absente.

### La garde

`CliOptionValueHasOneBlankDefinitionTest` découvre chaque test du blanc de `morpheus-cli/src/main/java`
(`Files.walk`, aucun fichier nommé) — `isBlank()` ; `trim()`, `strip()`, `stripLeading()` ou `stripTrailing()` suivis
de `isEmpty()` ou de `length() == 0` ; `String::isBlank` ; `String::trim` suivi d'un `filter(v -> !v.isEmpty())` ;
`codePoints()` ou `chars()` suivis de `allMatch`, `anyMatch` ou `noneMatch` dont l'argument nomme `isWhitespace`,
`isSpaceChar` ou une comparaison `<=` — et exige que chacun soit dans exactement une classe :

1. **la définition**, dans `OptionValue.nonBlank`, qui doit décider **par point de code** et porter les deux moitiés,
   `c <= ' '` et `Character.isWhitespace` ; un test sur la chaîne entière y est refusé ;
2. **le message d'une exception** — les `safeMessage`, qui remplacent un message vide par le nom de la classe.
   Reconnu à ce que le test lit, jamais au nom de la méthode : `p.getMessage()` pour un paramètre `p` de la méthode
   dont le type déclaré est `Throwable` ou un nom finissant par `Exception` ou `Error`, directement, par une locale
   déclarée depuis lui, ou par le paramètre d'un `filter` sur `Optional.ofNullable(p.getMessage())`. Une variable
   nommée `message` lue ailleurs n'est pas reconnue ;
3. **une autre question nommée**, avec le nombre de tests de sa méthode : le *nom* d'une option
   (`MorpheusCli.consumeOption`), une variable d'environnement ou une propriété JVM (`CliLayout.envPath`,
   `RemoteApiLaunchOptions.presentSetting` et `resolveWorkspaceRoots`), le message d'une exécution d'adaptateur
   imprimé par `reason analyze`, et deux résidus ouverts qui lisent une **partie** d'une valeur d'option et non la
   valeur : les éléments de `--providers` (le point « une liste faite de séparateurs » de l'amendement CLI-7) et le
   champ de provenance d'un `--evidence`.

Elle refuse en outre toute méthode nommée `nonBlank` hors d'`OptionValue` qui ne délègue pas à `OptionValue.nonBlank` :
un homonyme doté de son propre test se lit, à ses appels, comme la définition.

Un test qui ne tombe dans aucune classe fait échouer la garde, et une méthode nommée qui gagne, perd ou n'a plus de test
aussi. Rejouée sur les sources de `933a63fe`, la première version de la garde nommait exactement les six copies et une
définition à qui manquait une moitié, et rien d'autre : les vingt autres `isBlank()` du module — seize messages
d'exception, quatre autres questions — et les trois `String::trim` suivis d'un `filter` sont classés.

Ce qu'elle ne couvre pas, écrit dans sa Javadoc :

- **l'appel d'une méthode classée sur une valeur d'option** : la garde classe l'endroit où un blanc est *testé*, pas
  la provenance de la valeur testée. `CliLayout.envPath` ou `RemoteApiLaunchOptions.presentSetting` appelée sur une
  valeur d'option lui donnerait leur définition, sans que rien ici ne le voie. Le renommage de l'homonyme et la règle
  sur les méthodes `nonBlank` ferment le cas qui se lisait comme la définition, pas les autres ;
- **le paramètre jetable est reconnu au suffixe du nom de son type**, jamais résolu : un paramètre d'un type non
  jetable nommé `…Error`, portant un `getMessage()`, serait classé message d'exception ;
- **les autres orthographes** : `isEmpty()` sans `trim()`, `equals("")`, une boucle sur `Character.isWhitespace`, un
  `map(String::trim)` séparé de son `filter` par une autre étape, un prédicat de point de code écrit comme référence à
  une méthode auxiliaire, un `trim()` et un `isEmpty()` en deux instructions, comme la clé et la valeur de `--param`
  dans `MorpheusReasoningCli.addAssignment` ;
- une locale déclarée depuis `getMessage()` puis réaffectée ; un test d'une méthode nommée remplacé par un autre au
  même compte ;
- les transports MCP et HTTP, qui ont leur propre définition ;
- que chaque valeur d'option atteigne la définition : cette preuve reste la table de `BlankOptionValueRefusalTest`,
  écrite à la main.

### Ce qui reste

- Le point « Le blanc est ce que `trim()` vide » de l'amendement CLI-7, suite, est fermé. Les autres points de sa section
  « Ce qui reste » sont inchangés.
- **Les invisibles qui ne sont pas blancs** restent des valeurs : l'espace insécable (`U+00A0`, `U+2007`, `U+202F`),
  l'espace sans chasse `U+200B`, la marque d'ordre des octets `U+FEFF`, le contrôle « ligne suivante » `U+0085` et le
  contrôle `U+007F` ne sont blancs pour aucune des deux lectures. Une valeur qui n'est faite que d'eux atteint le
  lecteur telle quelle, avec les conséquences qu'avait l'espace Unicode : un analyseur peut échouer plus loin sans
  nommer l'option, ou la prendre pour un nom de fichier.
- **Une partie d'une valeur d'option** garde sa propre définition : les éléments de `--providers` (`trim` puis
  `isEmpty`, un élément vide est ignoré), la provenance d'un `--evidence` (`isBlank`, une provenance blanche vaut
  aucune), la clé et la valeur d'un `--param` (`trim` puis `isEmpty`, refusées). Ce ne sont pas des valeurs d'option ;
  la garde les nomme au lieu de les ignorer.
- **Les variables d'environnement** ont leur propre lecture, hors de ce prédicat par décision. Un défaut y a été
  constaté en écrivant la garde, vérifié par exécution et **non corrigé ici** : dans
  `RemoteApiLaunchOptions.resolveWorkspaceRoots` (`:204-205`), un élément de `MORPHEUS_SERVER_WORKSPACE_ROOTS` placé
  entre deux autres et fait uniquement de contrôles C0 non blancs (`U+0000`–`U+0008`, `U+000E`–`U+001B`) échappe à
  `isBlank()` ; `trim()` le vide, et `Path.of("")` puis `toAbsolutePath()` (`:211`) donnent le répertoire courant du
  processus. `MorpheusMain` (`:287`) passe la liste à `AllowedWorkspaceRoots.of`, qui accepte ce répertoire réel comme
  racine ; il fonde ensuite `requireAllowedDirectory` pour l'enregistrement (`MorpheusProjectRegistryApiService:38`) et
  la synchronisation (`MorpheusProjectSyncApiService:130-131`) distants. Un élément en tête ou en queue de la variable
  n'a pas cet effet : le `trim()` de la variable entière l'efface avant le découpage. Si le processus a `/` pour
  répertoire courant — le défaut de systemd pour un service système ; le dépôt ne fournit pas d'unité — tout le
  système de fichiers devient racine autorisée. Déclencheur : une configuration d'opérateur, de confiance, mal formée.
  Sévérité estimée basse à moyenne. Correctif à instruire à part : refuser, et non écarter, un élément vide après
  `trim`/`isBlank`, et refuser une racine relative.
- **MCP et HTTP** ne partagent pas ce prédicat. `McpArguments.nonBlankString` teste `isBlank()` puis rend `trim()` : un
  caractère de contrôle passe le test et devient une chaîne vide — la copie faible dans l'autre sens, sur un autre
  transport. À instruire avec l'audit MCP ; non traité ici.

**Preuve.** `OptionValueTest` refuse `""`, `" "`, une tabulation, `U+2003`, `U+3000`, `U+0001`, `U+0000` et les trois
mélanges `U+0001U+2003`, `U+2003U+0001`, `U+0001U+2003U+0001` avec le message de `nonBlank` ; accepte `U+00A0`,
`U+2007`, `U+202F`, `U+200B`, `U+FEFF`, `U+0085`, `U+007F` et `U+0001 x`, rendus **identiques** (la même instance) ; et
un chemin avec des espaces significatifs garde son orthographe. `BlankOptionValueRefusalTest` passe chaque option de
chaque parseur avec `U+2003`, `U+0001` et les trois mélanges en plus de `""` et `" \t "`, par `MorpheusMain.run`, et les
trois lanceurs dans les deux orthographes : code `2`, message nommant l'option, rien d'écrit.
`external-references list|resolve --project` avec `U+2003`, `U+0001U+2003` ou `U+2003U+0001` répond `--project
requires a non-blank value` et jamais `Invalid UUID string`, sans créer la base. Une option omise répond toujours
`--principal is required`, une option blanche le message de `nonBlank`. `SimpleOptionsTest` ajoute `U+2003`, `U+3000` et
`U+0001U+2003`. Sur les sources de `933a63fe`, la table échoue sur ses 108 invocations et ses 36 cas de lanceur avec
`U+2003` ; avec le prédicat par chaîne, elle échoue sur les 432 cas des trois mélanges et le test bout-en-bout de
CLI-11 rend `Invalid UUID string`. La garde tombe quand on remet le prédicat par chaîne dans `OptionValue.nonBlank`,
quand on rend à `presentSetting` son nom `nonBlank`, et quand on écrit `value.trim().length() == 0` dans
`MorpheusServerCli.required`.
## Amendement du 30 septembre 2026 (CLI-9) — rien n'est créé sur disque avant que les options soient acceptées

L'amendement CLI-7, suite, nomme dans « Ce qui reste » deux commandes qui ouvrent le store avant de vérifier leurs
options : `projects add` et `changes list`. Quatre autres avaient le même défaut, plus largement : `policy`, `query`,
`views` et `export`. `MorpheusPolicyCli.run` ouvrait `SqlitePolicyRuntime` (`:51` à `933a63fe`) avant d'appeler
`execute`, seul à faire `SimpleOptions.parse` puis, dans chaque branche de son `switch`, `rejectUnknown` ;
`MorpheusQueryCli.run` ouvrait `SqliteQueryRuntime` (`:63`) avant `query`, `views` et `export`, qui faisaient de même.
L'ouverture crée le répertoire de données, le fichier de base et son schéma. Mesuré à `933a63fe` sur un `--data-dir`
inexistant : `policy evaluate --project P --id ""`, `policy pack list --bogus x`, `policy pack get --id a --id b`,
`policy frobnicate`, `query execute … --bogus x`, `views list … --bogus x`, `views frobnicate`,
`export query … --limit 5`, `export view --format json --bogus x` et `export view --format yaml --id x` rendent tous
`2` **et** laissent la base créée. Le contre-exemple était dans la même famille : `MorpheusPortfolioCli` résout son
action dans `ACTION_OPTIONS` et refuse les options inconnues avant `new SqlitePortfolioStore`.

Deux phrases du dépôt affirmaient déjà la propriété pour ces commandes : les Conséquences de l'amendement CLI-7,
répétition (« les autres commandes avant d'ouvrir le store ») et la note de version de la répétition (« aucune commande
n'écrit quoi que ce soit avant le refus »). Elles étaient fausses pour une option de commande répétée de ces quatre
commandes, que `SimpleOptions.parse` ne lisait qu'après l'ouverture. Elles ne sont pas réécrites : cet amendement les
rend vraies.

**Décision.** Ces quatre commandes acceptent leur action et leurs options avant d'ouvrir le store, sur le patron de
`portfolio` : action reconnue, options lues (doublon refusé), valeur blanche et option inconnue refusées, *puis* le
store. Les ensembles d'options autorisées deviennent des tables lues avant l'ouverture :
`MorpheusPolicyCli.ACTION_OPTIONS` (quatorze actions), `MorpheusQueryCli.EXECUTE_OPTIONS`, `VIEW_ACTION_OPTIONS` (sept
actions) et `EXPORT_ACTION_OPTIONS` (`query`, `view`). Chaque entrée recopie le `rejectUnknown` de sa branche, qui
disparaît : aucune option n'est ajoutée ni retirée, et la preuve compare chaque action à une liste écrite
indépendamment de ces tables. Pour `export`, le contrôle du format et le refus nommé de `--offset`/`--limit`
(`export query`) sont remontés aussi, dans l'ordre qu'ils avaient : chaque refus nomme la même chose qu'avant.
Une seule préséance change, et c'est le but : un refus d'usage (code `2`) précède désormais une erreur d'ouverture du
store (répertoire non inscriptible, base verrouillée), qui rendait jusqu'ici le code `4` et masquait l'option fautive.

Ce qui vient après l'ouverture n'a pas bougé, ici comme dans `portfolio` : une option obligatoire absente, un
identifiant mal formé, un entier qui n'en est pas un, une portée absente ou donnée deux fois. Ces refus ont besoin de
la valeur ; les remonter demanderait de construire chaque requête avant l'ouverture, et `views update` lit la vue
courante pour connaître sa portée. `policy evaluate --id x`, sans portée, crée donc toujours la base avant de répondre
`exactly one of --project or --portfolio is required`.

### Alternatives écartées

- **Une phase de validation qui refait le `switch` d'actions sans toucher au store**, comme `composition` et
  `external-references` le font (un `switch` pour `rejectUnknown`, un second pour exécuter). Chaque action serait
  écrite deux fois avec ses options d'un côté seulement, et aucune garde ne tient aujourd'hui d'accord les deux `switch`
  de ces deux adaptateurs.
- **Une table qui porte aussi l'exécution** (`action → options, traitement`). Elle supprimerait le dernier couple à tenir
  d'accord, mais réécrirait en lambda chacune des vingt-quatre branches et s'écarterait du patron de `portfolio`, que les
  trois adaptateurs `SimpleOptions` partagent désormais.
- **Ouvrir le store paresseusement**, au premier accès. La propriété dépendrait de l'ordre des lectures dans chaque
  branche, c'est-à-dire de ce que chaque futur `case` penserait à faire en premier.

### La table et le `switch`

La table ne supprime pas tout couplage : chaque action est encore écrite deux fois, comme clé de la table et comme
`case` du `switch` qui l'exécute. Un `case` sans clé est du code mort, refusé comme action inconnue avant l'ouverture.
Une clé sans `case` reproduit le défaut : l'action est acceptée, le store est ouvert, et la branche `default` refuse
après coup. La forme de `portfolio` n'avait pas de garde pour cela : `CliOptionParsingRefusesUnknownOptionsTest`
vérifie qu'un `rejectUnknown` suit chaque `parse`, pas que la table et le `switch` s'accordent, ni que la table précède
le store. La branche `default` des `switch` d'exécution de `policy`, `views` et `export`, comme celles, déjà
inatteignables, des deux `switch` sur la commande de `MorpheusQueryCli`, lève maintenant `IllegalStateException` et non
plus un message d'action ou de commande inconnue : atteinte, elle n'est plus une faute de l'appelant. `portfolio` garde la sienne ; sa table devient visible du paquetage pour que la
même garde la lise.

### Inventaire — les adaptateurs du CLI qui ouvrent un store

Relevé à `933a63fe` par lecture de chaque point d'entrée ; les éléments marqués † ont aussi été exécutés sur un
`--data-dir` inexistant, avec une option inconnue et, selon le cas, une action inconnue, une valeur vide ou une option
d'une autre action. « Oui » : le refus d'une option inconnue, vide ou répétée et d'une action inconnue précède
l'ouverture.

| Adaptateur | Commandes | Store ouvert | Options acceptées avant l'ouverture ? |
|---|---|---|---|
| `MorpheusCli` | `sync`†, `sync-status`, `requirements find`, `constraints list`, `decisions list`, `tasks list`, `trace-requirement`, `change-context`, `analyze-change`, `quality`† | `CliRuntime` | Oui ; les identifiants obligatoires sont lus avant aussi. |
| `MorpheusCli` | `projects list`†, `projects add`† | `CliRuntime` | **Non** : seul le doublon est refusé avant ; option inconnue, sous-commande inconnue et valeur vide après. |
| `MorpheusCli` | `changes list`†, `changes get`† | `CliRuntime` | **Non** : `--project` est lu avant ; option inconnue, sous-commande inconnue et valeur vide (`--limit ""`) après. |
| `portfolio`† | toutes | `SqlitePortfolioStore` | Oui — sans qu'aucun test ne le tienne jusqu'ici. |
| `policy`†, `query`†, `views`†, `export`† | toutes | `SqlitePolicyRuntime`, `SqliteQueryRuntime` | **Oui depuis cet amendement** ; non à `933a63fe`. |
| `composition`† | `sync`, `status`, `conflicts` | `CliRuntime` | Oui. |
| `external-references`† | `list`, `resolve` | `CliRuntime` | Oui (CLI-8). |
| `acceptance-criteria`†, `constraints evaluate`†, `lifecycle apply`†, `augmented-context`† | toutes | `CliRuntime` | Oui : parseurs fermés, toutes les valeurs lues avant. |
| `change-orchestration`† | `state`, `transition-check` | `CliRuntime` | **En partie** : une option hors du vocabulaire de la famille est refusée avant ; une action inconnue et une option d'une autre action (`state --from`†, `transition-check --lifecycle`†, les drapeaux `--allow-*` sous `state`) le sont après — et après `findProject` : sur un store vide elles répondent `project not found`, code `4`, au lieu du refus d'usage. |
| `server backup create`†, `server restore` | — | `SqliteServerMaintenance` | Oui : l'allowlist d'options précède la maintenance. |
| lanceurs `api`, `mcp --stdio`, `api --remote` | — | serveur | Oui : options analysées avant tout démarrage. |

`server identity …` n'ouvre que le fichier d'identités ; `reason`, `update-check`, `provider-plugins`, `paths`,
`version`, `help`, `nexus-status` et `minos-status` n'ouvrent aucun store.

**L'inventaire est complet, pas seulement élargi** — complet au sens d'une lecture de chaque adaptateur de
`morpheus-cli/src/main/java` à `933a63fe`, pas d'une garde. Aux deux commandes nommées par CLI-7, suite, il ajoute une
famille qu'elle ne nommait pas, `change-orchestration`, et précise les deux premières : `projects list` et `changes get`
en sont aussi. Rien ne le tient à jour : un adaptateur ajouté demain n'y est pas.

### Ce qui reste

- `projects`, `changes` et `change-orchestration` ouvrent le store avant de refuser une partie de leurs options. Non
  traités ici.
- Les refus qui lisent une valeur viennent après l'ouverture dans les trois adaptateurs `SimpleOptions` (voir la
  décision).
- `composition` et `external-references` écrivent chaque action dans deux `switch`, sans garde qui les accorde.
- Aucune garde ne découvre un futur adaptateur qui ouvrirait le store avant d'accepter ses options.

**Preuve.** `NothingIsCreatedBeforeTheOptionsAreAcceptedTest`, par `MorpheusMain.run`, chaque cas sur un répertoire de
données inexistant :

- chaque option du vocabulaire de l'adaptateur, plus une faute de frappe, donnée seule à chacune des vingt-quatre
  actions : acceptée, elle atteint le store et le fichier de base existe ; refusée, le code est `2`, le message nomme
  l'option (ou, pour `export query`, le refus de page), et le répertoire du cas reste absent ;
- une valeur vide et une option répétée sur chaque action qui prend une option, une action inconnue ou absente pour
  chacune des quatre commandes, et `portfolio` : code `2`, répertoire absent ;
- le chemin heureux crée toujours la base (`policy pack list`, `policy evaluate`, `query execute`, `views list`,
  `export query`) ;
- chaque table est égale, clé par clé **et valeur par valeur**, à la liste écrite dans le test ; le sondage du premier
  point envoie l'union de cette liste et de toutes les valeurs des tables, pour qu'une option connue d'une seule table
  soit envoyée à chaque action ;
- chaque clé des tables, `portfolio` compris, atteint un `case` : lancée seule sur un store vide, elle rend `0`, ou `2`
  avec `--x is required` ou le refus de portée — ce que fait la première lecture de chaque `case`. L'assertion est
  positive et ne dépend pas du message de la branche `default` ;
- une option inconnue passée à un store qu'on ne peut pas ouvrir (répertoire de données sous un fichier ordinaire) rend
  `2` et nomme l'option ; la même commande valide rend `4`.

Ce que la preuve ne couvre pas : un `case` sans clé (code mort, refusé avant l'ouverture, non détecté) ; une branche
`default` dont le message imiterait un refus d'option obligatoire ; une option absente à la fois de la liste et de toutes
les tables, qui n'est pas sondée et que seule la comparaison de valeurs tient ; un futur adaptateur, que rien ne découvre.

Casser la règle en remettant l'ouverture avant toute validation dans `policy` et dans `query` — l'ordre de
`933a63fe` — fait tomber 261 des 395 cas : tous les refus des quatre commandes et la préséance sur un store qu'on ne peut
pas ouvrir (`4` au lieu de `2`) ; restent verts les 92 cas d'option acceptée, le chemin heureux, les tables et les deux
cas de `portfolio`. Élargir `policy evaluate` de `format` et `views get` de `bogus` — deux options qu'aucune action ne
lit, qu'une première version de la preuve ne sondait pas et laissait passer — fait tomber 3 cas : l'égalité des tables et
les deux sondages. Ajouter à `policy` et à `portfolio` une clé sans `case`, en changeant le message de la branche
`default` de `policy`, fait tomber 3 cas : l'égalité des tables et les deux clés, rendues `4` et `2` sans atteindre leur
`case`.
