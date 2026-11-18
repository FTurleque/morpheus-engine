# Tâches : publier un projet par le provider avec lequel il est inscrit (AUD-ARC-25)

Les groupes 2 à 6 sont des lots livrables séparément, dans cet ordre ; chacun laisse le build vert. Ils supposent
l'option A de Q1 : si le mainteneur retient l'option B, ce changement est réécrit (ADR et règle seulement).

## 1. Décision du mainteneur

- [ ] 1.1 Trancher Q1 (neutralité ou OpenSpec seul), Q2 (provider sur `ProjectStoreEntry` ou sur le domaine), Q3
  (sélection explicite à l'inscription), Q4 (valeur des projets existants), Q5 (version qui reçoit la migration) et Q6
  (ADR qui porte la décision) — vérification : réponses écrites dans design.md (D1, D2, D3, D6, D8), [QUESTION]
  remplacées par [DÉCISION]
- [ ] 1.2 Rédiger l'ADR proposé (D8) au statut `Proposée`, numéro qui suit le plus haut du registre et de l'archive,
  ligne d'index — vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest` ; l'acceptation reste au mainteneur
- [ ] 1.3 Si Q3 retient le refus de l'ambiguïté ou une sélection explicite, réécrire l'exigence « Registration SHALL
  report the provider it retains among several supporting ones » ou en ajouter une à `specs/project-provider-binding/spec.md` —
  vérification : `openspec validate publish-a-project-through-the-provider-it-was-registered-with --strict`

## 2. Lot 1 — le projet porte son provider (persistance)

- [ ] 2.1 Écrire `ProjectRegistrationPersistenceParityTest` (à écrire) : même provider relu des deux stores, racine
  réinscrite inchangée — exigence : `project-provider-binding` › A registered project SHALL keep the provider it was
  registered with ; vérification : ne compile pas avant 2.2
- [ ] 2.2 Ajouter `providerId` à `ProjectStoreEntry`, l'écrire et le lire dans `MemorySpecificationKnowledgeStore` et
  `SqliteSpecificationKnowledgeStore`, comparer identité et racine seulement dans `putProject` (D4) — vérification :
  2.1 vert, tests existants des deux stores verts
- [ ] 2.3 Écrire la migration `provider_id TEXT NOT NULL DEFAULT 'openspec'` (D3) et étendre `ReleasedSchemaUpgradeTest`
  au provider relu — exigence : `project-provider-binding` › A project registered before its provider was recorded
  SHALL read back with the OpenSpec provider ; vérification : le cas étendu échoue avant la migration, passe après
- [ ] 2.4 Appliquer la règle « Une version de schéma est une version de produit » selon Q5 : guide de mise à niveau de
  la version ouverte, et passage de version en lot indivisible s'il faut l'ouvrir — vérification :
  `UpgradeGuideCoherenceTest`, `ReleasedSchemaLevelsDeclarationTest`
- [ ] 2.5 Mettre à jour `docs/developer/specifications/sqlite-schema-history.md` si elle liste les migrations —
  vérification : `RepositoryDocumentationCoherenceTest`

## 3. Lot 2 — le catalogue et l'inscription unique

- [ ] 3.1 Écrire `PublishableProviderTest` et `PublishableProviderCatalogTest` (à écrire) : lecteur d'un autre provider
  refusé, doublon refusé, recherche par identifiant, priorités déclarées — vérification : ne compile pas avant 3.2
- [ ] 3.2 Créer `PublishableProvider` et `PublishableProviderCatalog` (à créer) dans `com.morpheus.application.provider`,
  et `StructuredMarkdownSpecificationProvider.SOURCE_DIRECTORIES` (D5) — vérification : 3.1 vert,
  `LayerDependencyTest` vert
- [ ] 3.3 Étendre `LocalProjectRegistryTest` : refus `NO_PROVIDER_FOUND`, rien persisté ; `openspec` retenu et
  `markdown` rapporté sur une copie d'`openspec-basic` ; refus `MULTIPLE_PROVIDER_MATCHES` à priorité égale ; `markdown`
  retenu sur un workspace Markdown seul (copie réduite à `morpheus/`) — exigences : `project-provider-binding` ›
  Registration SHALL refuse a workspace that no packaged provider supports, › Registration SHALL report the provider it
  retains among several supporting ones ; vérification : rouges avant 3.4
- [ ] 3.4 Faire de `LocalProjectRegistry` la seule inscription, sondant le catalogue (D6), appelée par
  `MorpheusProjectCli` (`add`) et `MorpheusProjectRegistryApiService` — vérification : 3.3 vert, cas CLI dans
  `MorpheusCliTest` et cas HTTP dans `MorpheusProjectRegistryApiServiceContractTest` (à écrire) verts
- [ ] 3.5 Si Q3 retient la sélection explicite : option CLI, champ `provider` de `ProjectRegistrationRequest`
  (`maxLength`, `additionalProperties: false`), notes de `project.register` dans `contracts/public-surfaces.tsv`, aide
  CLI — justification de PR (gouvernance) ; vérification : `PublicHttpRouteConvergenceTest`,
  `PublicSurfaceManifestCoversEveryServedCliCommandTest`, `CliCommandPathsTest`
- [ ] 3.6 Documenter les refus et les membres ajoutés à la réponse de `POST /api/v1/projects`
  (`docs/developer/reference/openapi/morpheus-v1.yaml`) et dans `docs/user/QUICKSTART.md` — justification de PR ;
  vérification : `UserDocumentationSpeaksOfTheInstalledProductTest`, `RepositoryDocumentationCoherenceTest`

## 4. Lot 3 — publication et analyse par le provider inscrit

- [ ] 4.1 Écrire la preuve au transport dans `ProviderAntiLockInTest` (à écrire) : un projet Markdown inscrit puis
  synchronisé par la CLI publie — exigence : `project-ingestion-port` › A publication SHALL read a project through the
  reader of its registered provider ; vérification : rouge avant 4.3
- [ ] 4.2 Écrire les cas de `MorpheusApiProjectSyncIntegrationTest` (à écrire) : projet Markdown publié, provider non
  empaqueté refusé avec état de synchronisation en échec — vérification : rouges avant 4.3
- [ ] 4.3 Remettre le catalogue de `MorpheusMain` aux classes de capacité et au serveur HTTP (local et façade remote) ;
  `MorpheusProjectCli`, `MorpheusChangeCli` et `MorpheusProjectSyncApiService` lisent par le lecteur du provider inscrit
  et analysent ses `sourceDirectories` (D5) — exigences : `project-ingestion-port` › A publication SHALL read a project
  through the reader of its registered provider, › A sync SHALL scan the directories its registered provider states ;
  vérification : 4.1 et 4.2 verts, `TransportsReadAProjectThroughTheProviderPortTest` vert
- [ ] 4.4 Écrire le cas `sync` après modification de la source Markdown (à écrire) — exigence : `project-ingestion-port`
  › A sync SHALL scan the directories its registered provider states ; vérification : la synchronisation rapporte la
  source modifiée
- [ ] 4.5 Retirer `MorpheusProjectSyncApiService` de `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` et baisser
  la clé de `config/debt-counter-ratchets.properties` dans le même commit — justification de PR (compteur de dette
  d'ADR-0162, baisse) ; vérification : `AdapterCompositionRootArchitectureTest`, `ProductionClassListsArchitectureTest`
- [ ] 4.6 Retirer `morpheus-provider-openspec` de la portée de compilation de `morpheus-api` s'il n'y sert plus —
  vérification : `./mvnw dependency:analyze` sans avertissement
- [ ] 4.7 Mettre à jour les notes de `project.sync` et `change.analysis` dans `contracts/public-surfaces.tsv` —
  justification de PR ; vérification : `PublicHttpRouteConvergenceTest`
- [ ] 4.8 Mettre à jour `docs/developer/specifications/project-ingestion-port.md` (exigences, diagramme de
  synchronisation, traçabilité) — vérification : `RepositoryDocumentationCoherenceTest`

## 5. Lot 4 — écriture et composition

- [ ] 5.1 Étendre `RegisteredProjectWriteCapabilityResolverTest` : seul le provider inscrit est sondé, refus qui le nomme
  — exigence : `project-provider-binding` › The write capability of a project SHALL be resolved from its registered
  provider ; vérification : rouge avant 5.2
- [ ] 5.2 Faire lire le provider de l'entrée à `RegisteredProjectWriteCapabilityResolver` ; `CliProjectWriteCapabilityResolver`
  et `MorpheusControlledLifecycleCli` reçoivent le catalogue (D7) — vérification : 5.1 vert,
  `ControlledLifecycleMutationContractTest` vert
- [ ] 5.3 Faire prendre ses providers, ses lecteurs et leurs priorités au catalogue par `MorpheusCompositionCli`,
  valeurs inchangées —
  vérification : tests existants de `composition sync` verts

## 6. Lot 5 — le gate et la documentation durable

- [ ] 6.1 Ajouter à `TransportsReadAProjectThroughTheProviderPortTest` la règle sur le bytecode « aucune classe d'`api`,
  `cli`, `mcp` n'appelle le constructeur d'un type de `com.morpheus.provider` hors du câblage nommé », avec le contrôle
  que le câblage nommé construit encore un provider — exigence : `project-ingestion-port` › No transport class SHALL
  construct a specification provider ; justification de PR (test d'architecture) ; vérification : violation plantée
  (un `new OpenSpecSpecificationContentReader()` dans une commande) constatée rouge puis retirée, trace dans la Javadoc
- [ ] 6.2 Créer `docs/developer/specifications/project-provider-binding.md` (gabarit de
  `docs/developer/specifications/README.md`) avec diagrammes et traçabilité, et l'ajouter à l'index — vérification :
  `RepositoryDocumentationCoherenceTest`
- [ ] 6.3 Mettre à jour la vue d'architecture concernée sous `docs/architecture/arc42/` (câblage des providers) —
  vérification : diagramme rendu, liens relus

## 7. Vérification d'ensemble

- [ ] 7.1 Lancer `openspec validate publish-a-project-through-the-provider-it-was-registered-with --strict` —
  vérification : aucun avertissement
- [ ] 7.2 Compléter la matrice de traçabilité des deux pages durables (exigence → scénario → conception ou diagramme →
  tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [ ] 7.3 Lancer `./mvnw clean verify`, puis `./mvnw test -pl morpheus-architecture-tests` et `./mvnw dependency:analyze`
  — vérification : verts, `morpheus-architecture-tests/target/enforcement-verdict.txt` sans violation
- [ ] 7.4 Rejouer à la main avec la CLI construite : `projects add` puis `sync` sur une copie d'`openspec-basic` et sur
  une copie réduite à `morpheus/`, et `projects add` sur un répertoire vide — vérification : deux publications (providers
  `openspec` et `markdown`), un refus `NO_PROVIDER_FOUND`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs consolidées `project-provider-binding` et `project-ingestion-port` et leurs pages
  durables.
- Faire accepter l'ADR proposé par le mainteneur avant l'archivage.
