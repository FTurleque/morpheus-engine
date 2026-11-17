# Tâches : typer les réponses des services de l'adaptateur HTTP (AUD-QUA-36)

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 (option A recommandée : tout typer et une règle sans exception ; B : façade d'abord et plafond ;
  C : sans règle) et reporter la décision dans design.md ; en B, réviser l'exigence et les tâches 2.x avant de
  continuer — exigence : `http-service-answer-types` › A public method of an HTTP adapter service SHALL declare the
  type of the answer it returns ; vérification : D1 passe en [DÉCISION]

## 2. Règle et typage

- [ ] 2.1 Écrire `HttpServiceAnswerTypesArchitectureTest` (à écrire, `morpheus-architecture-tests`) selon D3, avec la
  trace de sa violation plantée dans la Javadoc — exigence : `http-service-answer-types` › A public method of an HTTP
  adapter service SHALL declare the type of the answer it returns ; vérification : rouge sur l'arbre actuel, nommant
  les méthodes qui rendent `Object` ; justification de PR (test d'architecture ajouté)
- [ ] 2.2 Typer les méthodes de la façade `MorpheusApiService`, puis celles des autres services, chacune au type
  statique de l'expression qu'elle rend (D2), et typer les variables des appelants dans `com.morpheus.api.routes` et
  `com.morpheus.api.local` — exigence : `http-service-answer-types` › Typing the answer of an HTTP adapter service
  SHALL change no HTTP response ; vérification : 2.1 vert, `ExtensionRoutesResponseWritingParityTest`, tests
  d'intégration HTTP de `morpheus-api` et `PublicPayloadConvergenceTest` verts sans modification de leurs valeurs
  attendues, `SharedDomainProjectionArchitectureTest` vert

## 3. Documentation durable

- [ ] 3.1 Créer `docs/developer/specifications/http-service-answer-types.md` (gabarit de
  `docs/developer/specifications/README.md`, matrice de traçabilité) et l'ajouter à l'index, section « Frontière HTTP
  locale et distante » — vérification : `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate type-the-answers-of-the-http-adapter-services --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice de traçabilité (exigence → scénarios → D1 à D3 → tâches → tests) — vérification : chaque
  scénario a une preuve
- [ ] 4.3 Lancer `grep -n "public Object " morpheus-api/src/main/java/com/morpheus/api/*ApiService.java` — vérification
  : aucune ligne (option A)
- [ ] 4.4 Lancer `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` — vérification : verts ;
  `architectureTestsMinimum` de `config/m21-quality-ratchets.properties` n'est pas baissé

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
