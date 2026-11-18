# Tâches : rendre chaque échec HTTP par une seule table de correspondance (AUD-QUA-30)

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D3 (texte du 500 du routeur de raisonnement : option A recommandée, ou B) et reporter la décision
  dans design.md — exigence : `http-failure-status-mapping` › Every handler of the local HTTP adapter SHALL answer a
  failure of the common family with the same status and code ; vérification : D3 passe en [DÉCISION]

## 2. Table de correspondance et tests de reproduction

- [ ] 2.1 Écrire `MorpheusHttpFailureResponsesTest` (à écrire, `morpheus-api`) : une méthode par branche de la famille
  commune (405 avec `Allow`, 400, 409 pour chacun des trois types, 500 tracé et sans texte), l'ordre « propre puis
  commun » avec un sous-type d'`IllegalStateException` et d'`IllegalArgumentException` — exigences :
  `http-failure-status-mapping` › Every handler of the local HTTP adapter SHALL answer a failure of the common family
  with the same status and code, › A router SHALL name its own failures before the common family, and only there ;
  vérification : ne compile pas avant 2.2
- [ ] 2.2 Créer `MorpheusHttpFailureResponses` dans `com.morpheus.api.transport` (D1, D2) — vérification : 2.1 vert,
  `ApiServicesAndTransportPackagesArchitectureTest` vert

## 3. Gestionnaires

- [ ] 3.1 Ajouter à `ExtensionRoutesResponseWritingParityTest` le cas d'un conflit d'historique publié sous un routeur
  (409 attendu) — exigence : `http-failure-status-mapping` › Every handler of the local HTTP adapter SHALL answer a
  failure of the common family with the same status and code, scénario « Un conflit d'historique publié sous un
  routeur » ; vérification : rouge avant 3.2 (500 rendu)
- [ ] 3.2 Faire rendre tout échec par la table à `MorpheusHttpServer.handle` et aux quatre routeurs de
  `com.morpheus.api.routes.owncontext`, chacun déclarant ses correspondances propres — vérification : 2.1 et 3.1 verts,
  `ExtensionRoutesResponseWritingParityTest` et `ExtensionRoutesRequestBoundaryParityTest` verts sans autre
  modification de leurs valeurs attendues, `HttpRoutesTransportBoundaryArchitectureTest` vert
- [ ] 3.3 Corriger la Javadoc d'`ExtensionRoutesResponseWritingParityTest` : les branches 409 et 500 sont exercées par
  le test de la table — vérification : relecture

## 4. Règle d'architecture

- [ ] 4.1 Écrire `HttpFailureStatusMappingArchitectureTest` (à écrire, `morpheus-architecture-tests`) selon D4, avec
  sa table de faits d'exemptions exacte dans les deux sens et la trace de la violation plantée dans sa Javadoc —
  exigence : `http-failure-status-mapping` › The build SHALL refuse a handler of the HTTP adapter that maps a failure
  to a status by itself ; vérification : la règle échoue sur un routeur de test qui attrape `IllegalStateException`,
  puis passe ; justification de PR (fichier de gouvernance)
- [ ] 4.2 Déclarer la table d'exemptions dans `ProductionClassListsArchitectureTest` comme table de faits —
  vérification : `everyListOfProductionClassesIsADebtCounterOrATableOfFacts` vert

## 5. Documentation durable

- [ ] 5.1 Créer `docs/developer/specifications/http-failure-status-mapping.md` (gabarit de
  `docs/developer/specifications/README.md`, diagramme de séquence de design.md, matrice de traçabilité) et l'ajouter à
  l'index, section « Frontière HTTP locale et distante » — vérification : `RepositoryDocumentationCoherenceTest`
- [ ] 5.2 Relire le tableau des codes d'erreur de `docs/developer/API.md` — vérification : aucun code ni statut
  contredit

## 6. Vérification d'ensemble

- [ ] 6.1 Lancer `openspec validate map-every-http-failure-to-its-status-through-one-table --strict` — vérification :
  aucun avertissement
- [ ] 6.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D4 → tâche → test) —
  vérification : chaque exigence a une preuve existante
- [ ] 6.3 Lancer `./mvnw test -pl morpheus-api` puis `./mvnw test -pl morpheus-architecture-tests` et lire
  `morpheus-architecture-tests/target/enforcement-verdict.txt` — vérification : verts ; `architectureTestsMinimum` de
  `config/m21-quality-ratchets.properties` n'est pas baissé
- [ ] 6.4 Lancer `./mvnw clean verify` — vérification : vert, gate de couverture des lignes modifiées compris

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
