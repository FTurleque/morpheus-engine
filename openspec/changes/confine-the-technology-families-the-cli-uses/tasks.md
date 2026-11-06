# Tâches : confiner les familles technologiques qu'emploie la CLI

## 1. Mesurer et partager les familles

- [x] 1.1 Mesurer sur le bytecode, par une règle ArchUnit jetable sur `com.morpheus.cli`, les classes qui atteignent chaque famille du vocabulaire (définitions d'`ApplicationTechnologyBoundaryArchitectureTest`), et comparer au relevé textuel de `design.md` ; consigner l'écart dans le compte rendu de la tâche — exigence : `cli-technology-boundary` › Une classe nommée pour une famille confinée DOIT encore l'atteindre ; vérification : table mesurée consignée, SHA cité ; aucune règle jetable laissée dans le dépôt
- [x] 1.2 Extraire le record `Family` et les prédicats des familles dans une déclaration partagée du paquet des tests d'architecture (D1), sans changer les noms, contenus ni tailles de `CONFINED_FAMILIES` et `REFLECTION_AND_LOADING` — vérification : `ApplicationTechnologyBoundaryArchitectureTest` et `ProductionClassListsArchitectureTest` verts, `config/debt-counter-ratchets.properties` inchangé

  Compte rendu 1.1 (bytecode, `8c038add`) : les ensembles mesurés égalent le relevé textuel de `design.md` pour le
  réseau sortant, les fils d'exécution, l'accès fichier, l'environnement du processus et l'horloge murale ; SQL,
  bibliothèque JSON, exécution de processus et réflexion ne sont atteints par aucune classe. Un écart : le flux standard
  n'est atteint que par `MorpheusMain` (`System.err`) ; `MorpheusCli` n'y figure pas, car `FileDescriptor.out` n'est pas
  un champ de `System` — son ouverture par descripteur est comptée en accès fichier (`FileOutputStream`).
  1.2 : la déclaration partagée est le record `TechnologyFamily` ; `ReflectionAndClassLoadingBoundaryArchitectureTest#theApplicationKeepsItsOwnConfinementOfReflectionAndLoading`,
  qui lisait la définition de la famille réflexion dans la source du gate de l'application, la lit dans
  `TechnologyFamily.java` et vérifie que le gate de l'application la confine toujours à `REFLECTION_AND_LOADING`.
  Le gate de l'application gagne `everyFamilyOfTheVocabularyHasARegimeInTheApplication`.

## 2. Le gate de la CLI

- [x] 2.1 Écrire `CliTechnologyBoundaryArchitectureTest` avec la méthode d'exhaustivité des régimes (D2) — exigence : `cli-technology-boundary` › Chaque famille technologique DOIT avoir un régime déclaré pour la CLI ; vérification : une famille factice ajoutée à la déclaration partagée le fait échouer en la nommant ; retour arrière
- [x] 2.2 Ajouter la règle des familles refusées sur `com.morpheus.cli..` — exigence : `cli-technology-boundary` › Une famille refusée DOIT l'être dans tout `com.morpheus.cli` ; vérification : casser la règle une fois (une classe de `morpheus-cli` qui dépend de `java.sql`), constater l'échec qui nomme la classe et la famille, revenir en arrière
- [x] 2.3 Ajouter la règle de confinement et l'égalité des ensembles nommés et mesurés, avec les tables classe → raison issues de la mesure 1.1 — exigences : `cli-technology-boundary` › Une famille confinée DOIT n'être atteinte que par les classes que le gate de la CLI nomme pour elle ; `cli-technology-boundary` › Une classe nommée pour une famille confinée DOIT encore l'atteindre ; vérification : casser chaque sens une fois — une commande non nommée qui lit `System.out`, puis un nom factice ajouté à la liste du réseau — constater les deux échecs, revenir en arrière
- [x] 2.4 Déclarer chaque liste du gate de la CLI `TABLE_OF_FACTS` dans `ProductionClassListsArchitectureTest.KINDS` — exigence : `cli-technology-boundary` › Les listes de classes du gate de la CLI DOIVENT être des tables de faits déclarées ; vérification : `everyListOfProductionClassesIsADebtCounterOrATableOfFacts` rouge avant la déclaration, vert après ; aucune clé ajoutée à `config/debt-counter-ratchets.properties`
- [x] 2.5 Laisser `CoherenceCheckMarkerTest` et `EnforcementMechanismAccountingTest` juger la classe ajoutée — vérification : verts sans exception ajoutée

## 3. Documentation

- [x] 3.1 Créer `docs/developer/specifications/cli-technology-boundary.md` depuis le gabarit de `docs/developer/specifications/README.md` (régimes, diagramme de classes de la cible, traçabilité), l'ajouter à l'index, puis transformer dans `specs/cli-technology-boundary/spec.md` (`## Purpose`) le chemin écrit en code en lien `/docs/developer/specifications/cli-technology-boundary.md` — vérification : `RepositoryDocumentationCoherenceTest` vert
- [x] 3.2 Mettre à jour `docs/developer/specifications/application-technology-boundary.md` (renvoi à la déclaration partagée) — vérification : page relue, liens contrôlés
- [x] 3.3 Compléter `.claude/rules/architecture.md`, section « Ce que l'application touche (ADR-0150) », par un paragraphe sur la CLI : régimes, tables de faits, renvoi au test, sans recopier aucune liste — vérification : relecture ; `GovernanceSurfaceCoherenceTest` vert
- [ ] 3.4 Ouvrir, ou proposer au mainteneur d'ouvrir, le changement de `morpheus-api` exclu par D4, et faire trancher la question des compteurs de dette ; si elle l'est en leur faveur, proposer l'ADR qu'ils exigent (D5) sans l'accepter — vérification : décision consignée dans le compte rendu de la tâche

  Compte rendu 3.4 : le volet `morpheus-api` est porté par ce changement (section 5, D6) plutôt que par un changement
  à part. La question des compteurs de dette reste au mainteneur, pour les listes de la CLI et pour celles de
  l'adaptateur HTTP : `[QUESTION]` ces listes ne doivent-elles que rétrécir ? Tables de faits par défaut, aucun ADR
  rédigé (D5) ; un « oui » exige un ADR accepté qui les nomme. La tâche reste ouverte tant que la question n'est pas
  tranchée.

## 4. Vérification d'ensemble

- [x] 4.1 `openspec validate confine-the-technology-families-the-cli-uses --strict` — vérification : aucun avertissement
- [x] 4.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve existante ou « preuve manquante »
- [x] 4.3 `./mvnw test -pl morpheus-architecture-tests -Dtest=CliTechnologyBoundaryArchitectureTest,ApplicationTechnologyBoundaryArchitectureTest,ReflectionAndClassLoadingBoundaryArchitectureTest,ProductionClassListsArchitectureTest,AnEmptyArchitectureRuleFailsTest,CoherenceCheckMarkerTest,EnforcementMechanismAccountingTest,RepositoryDocumentationCoherenceTest` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert ; le nombre d'exécutions ne baisse pas sous `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`)
  - Relevé : `./mvnw clean verify` d'intégration (`chore/sprint-15-low`), 19 modules, `BUILD SUCCESS`, 5 349 tests, aucun échec ; `archgate check` 27/27.
- [x] 4.4 `archgate check` — vérification : aucune violation

## 5. morpheus-api

- [x] 5.1 Mesurer sur le bytecode, par une règle jetable sur `com.morpheus.api`, les classes qui atteignent chaque famille de `TechnologyFamily.VOCABULARY` — exigence : `api-technology-boundary` › Une classe nommée pour une famille confinée de l'adaptateur HTTP DOIT encore l'atteindre ; vérification : table mesurée consignée (D6), SHA cité ; aucune règle jetable laissée dans le dépôt
- [x] 5.2 Écrire `ApiTechnologyBoundaryArchitectureTest` (exhaustivité des régimes, familles refusées, confinement, égalité des ensembles) avec les régimes de D6 — exigences : toutes celles de `api-technology-boundary` ; vérification : casser chaque règle une fois et revenir en arrière
- [x] 5.3 Déclarer `ApiTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` `TABLE_OF_FACTS` dans `ProductionClassListsArchitectureTest.KINDS` — exigence : `api-technology-boundary` › Les listes de classes du gate de l'adaptateur HTTP DOIVENT être des tables de faits déclarées ; vérification : `everyListOfProductionClassesIsADebtCounterOrATableOfFacts` rouge avant, vert après ; aucune clé ajoutée à `config/debt-counter-ratchets.properties`
- [x] 5.4 Créer `docs/developer/specifications/api-technology-boundary.md`, l'indexer, et renvoyer vers elle depuis les pages de la CLI et de l'application et depuis `.claude/rules/architecture.md` — vérification : `RepositoryDocumentationCoherenceTest`, `GovernanceSurfaceCoherenceTest` verts

  Compte rendu 5.2 (violations plantées puis retirées) : une classe de `com.morpheus.api` qui appelle `System.getenv`,
  dépend de `java.sql` et de `java.net.http` fait échouer la règle des familles refusées (« SQL », « process
  environment »), la règle de confinement (« outbound network ») et l'égalité des ensembles ; un nom ajouté à la liste
  de la bibliothèque JSON fait échouer l'égalité ; le retrait du régime de l'horloge fait échouer l'exhaustivité.
  `[QUESTION]` régime de l'horloge murale libre par défaut (D6), à confirmer.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
