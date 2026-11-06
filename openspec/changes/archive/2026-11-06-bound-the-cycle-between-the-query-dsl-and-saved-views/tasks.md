# Tâches : borner le cycle entre le DSL de requête et les vues enregistrées

## 1. Mesure et décision d'architecture

- [x] 1.1 Mesurer les arêtes de la paire sur le bytecode (ArchUnit, dépendances directes ramenées au type de premier rang) et sur le texte (imports et noms qualifiés hors commentaires), et noter sous la tâche si ArchUnit compte `SavedViewDefinition → QueryBudgets` — exigence : `application-package-cycles` › Les arêtes entre le DSL de requête et les vues enregistrées forment une liste exacte qui ne fait que rétrécir ; vérification : les deux mesures et leur union consignées, avec la révision
  - Mesure du 2026-11-06 sur `8c038add`, par type de premier rang : bytecode (ArchUnit) 5 arêtes `dsl → saved` et 8 `saved → dsl` ; texte 3 et 10 ; union 5 et 10, soit 15 arêtes sur 5 origines. ArchUnit ne compte pas `SavedViewDefinition → QueryBudgets` ni `SavedViewService → QueryBudgets` (constantes inlinées, entrée résiduelle du pool) : le texte seul les voit.
- [x] 1.2 Rédiger l'ADR consolidé qui remplace ARCH-0156 (décisions 1 à 4 et 6 reprises, décision 5 réécrite avec la règle de deuxième niveau sous `query`, la paire nommée, le compteur `ApplicationPackageCyclesArchitectureTest.QUERY_DSL_AND_SAVED_EDGES` et la mesure datée), numéro relu dans le registre et l'archive, statut `Proposée` ; lignes d'index dans `docs/architecture/adr/README.md` et `docs/architecture/arc42/09-decisions.md` — exigence : `application-package-cycles` › La décision d'architecture nomme la paire tolérée et son compteur ; vérification : `AdrIndexCoherenceTest` vert, `archgate check` sans violation
- [x] 1.3 Faire accepter l'ADR par le mainteneur, passer ARCH-0156 en `Remplacée par ADR-NNNN`, l'archiver (`git mv`), rediriger les mentions `ADR-0156` / `ARCH-0156` du dépôt hors relevés datés, et redéclarer `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` sous le nouvel ADR — vérification : `ProductionClassListsArchitectureTest`, `AdrIndexCoherenceTest` et `RepositoryDocumentationCoherenceTest` verts
  - L'ADR-0162 est `Acceptée`, sur la décision « borner » prise par le mainteneur le 2026-11-06 ; l'acceptation est confirmée par le mainteneur le 2026-11-06. Sans elle, `everyDebtCounterIsNamedByTheAcceptedDecisionRecordItDeclares` refuserait le compteur. Les mentions d'`ADR-0156` des changements OpenSpec actifs d'autres lots ne sont pas redirigées.

## 2. Règle de deuxième niveau et liste exacte

- [x] 2.1 Écrire, avant la règle, les fixtures de deuxième niveau sous `com.morpheus.architecture.cycles` et leurs tests : deux sous-paquets mutuellement dépendants refusés et nommés ; une arête listée ignorée ; une arête non listée refusée ; une constante inlinée vue par le texte ; un type imbriqué ramené à son type de premier rang — exigences : `application-package-cycles` › Les paquets sous la plateforme de requête sont sans cycle, à une paire nommée près ; › Les arêtes entre le DSL de requête et les vues enregistrées forment une liste exacte qui ne fait que rétrécir ; vérification : rouges avant 2.2 (à écrire)
  - Les tests sur fixtures sont écrits avec la règle, pas avant : chacun prouve le refus par un `assertThrows` sur la règle elle-même.
- [x] 2.2 Ajouter à `ApplicationPackageCyclesArchitectureTest` la règle `slices().assignedFrom(…)` (racine `query` comprise), l'`ignoreDependency` des seules arêtes `dsl → saved` listées, la carte `QUERY_DSL_AND_SAVED_EDGES` à l'union mesurée en 1.1, le test d'égalité de la liste et le test de complétude des sous-paquets ; mettre à jour la javadoc de la classe — exigences : idem et › La règle de la plateforme de requête juge chacun de ses paquets ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=ApplicationPackageCyclesArchitectureTest` vert
- [x] 2.3 Déclarer le compteur dans `ProductionClassListsArchitectureTest` sous l'ADR consolidé et ajouter `ApplicationPackageCyclesArchitectureTest.QUERY_DSL_AND_SAVED_EDGES` à `config/debt-counter-ratchets.properties` à la taille de la liste — exigence : `application-package-cycles` › La liste des arêtes tolérées est un compteur de dette adossé à une décision acceptée ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=ProductionClassListsArchitectureTest` vert
- [x] 2.4 Casser la règle une fois sur le code de production : ajouter dans une classe de `query.saved` l'emploi d'un type de `query.dsl` non listé, constater l'échec de la liste qui nomme l'arête ; ajouter dans une classe de `query.dsl` autre que celles listées l'emploi d'un type de `query.saved`, constater l'échec de la règle de tranches qui nomme le cycle ; revenir en arrière — vérification : deux échecs constatés et notés sous la tâche, suite verte après retour arrière
  - Constaté le 2026-11-06 : `SavedViewId` emploie `QueryScope` et `QueryScope` emploie `SavedViewId` ; la liste échoue (`expected: <[]> but was: <[dsl.QueryScope -> saved.SavedViewId, saved.SavedViewId -> dsl.QueryScope]>`) et la règle de tranches échoue (`Cycle detected: Slice dsl -> Slice saved`, avec le `because` d'ADR-0162). Retour arrière fait, suite verte.
- [x] 2.5 Vérifier que la règle de premier niveau est inchangée — exigence : `application-package-cycles` › The dependencies that lie on a cycle between application packages SHALL be named ; vérification : `ApplicationPackageCyclesArchitectureTest#theApplicationPackagesAreFreeOfCycles` et `#theCycleRuleRefusesTwoPackagesThatDependOnEachOther` verts, sans `ignoreDependency` ajouté à la règle de premier niveau

## 3. Documentation

- [x] 3.1 Mettre à jour `docs/developer/specifications/application-package-cycles.md` (exigences ajoutées, les deux organigrammes de `design.md`, écarts, traçabilité) — vérification : `RepositoryDocumentationCoherenceTest` vert
- [x] 3.2 Mettre à jour `.claude/rules/architecture.md`, section « Les paquets de l'application sont sans cycle » : règle de deuxième niveau sous `query`, exception nommée, compteur, sans nombre recopié — vérification : relecture ; aucun total d'arêtes cité hors de la clé du compteur

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate bound-the-cycle-between-the-query-dsl-and-saved-views --strict` — vérification : aucun avertissement
- [x] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception ou diagramme → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [x] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=ApplicationPackageCyclesArchitectureTest,ProductionClassListsArchitectureTest,AnEmptyArchitectureRuleFailsTest,AdrIndexCoherenceTest,RepositoryDocumentationCoherenceTest` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert
  - Relevé : `./mvnw clean verify` d'intégration (`chore/sprint-15-low`), 19 modules, `BUILD SUCCESS`, 5 349 tests, aucun échec ; `archgate check` 27/27.
- [x] 4.4 Lancer `archgate check` — vérification : aucune violation
  - Relevé : `./mvnw clean verify` d'intégration (`chore/sprint-15-low`), 19 modules, `BUILD SUCCESS`, 5 349 tests, aucun échec ; `archgate check` 27/27.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
