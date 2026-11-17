# Tâches : paginer les références d'un portfolio dans le store

Prérequis : le changement `budget-orchestration-quality-policy-and-portfolio-reads` (`AUD-PRF-30`) a déclaré et mesuré
le budget de la page de références d'un portfolio sur le code non corrigé ; le sprint 5 de l'audit du 17/11/2026
(`AUD-PRF-21` à `AUD-PRF-26`) est appliqué, il modifie les mêmes fichiers de `morpheus-store-sqlite`.

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md (ordre d'identité partout, recommandé ; ordre sémantique avec clé persistée ; ordre
  propre à chaque portée) — vérification : décision consignée dans design.md ; si l'ordre retenu n'est pas l'ordre
  d'identité, la spec delta (`portfolio-reference-reads` › Une page de références suit un ordre déclaré et total) et
  les tâches 3.x sont révisées avant toute autre tâche

## 2. Reproduction

- [ ] 2.1 Écrire dans `PortfolioQueryServiceTest` les tests de coût : un double de store qui refuse `listReferences`,
  pour la page de portfolio, la page de projet et la vue d'ensemble (tests à écrire) — exigences :
  `portfolio-reference-reads` › Une page de références d'un portfolio est découpée par le store ;
  `portfolio-reference-reads` › Le compte et les conflits d'un portfolio sont calculés par le store ; vérification :
  ne compilent pas, ou échouent, avant 3.2
- [ ] 2.2 Compléter `PortfolioRegistryServiceTest` (observation identique avec un double qui refuse `listReferences` ;
  observation d'une autre preuve) — exigence : `portfolio-reference-reads` › Un ajout de référence se dédoublonne par
  une recherche ciblée ; vérification : rouge avant 3.3
- [ ] 2.3 Compléter `QuerySourceBudgetTest` d'un cas qui relève la limite demandée au store pour les références —
  exigence : `portfolio-reference-reads` › La requête de portée portfolio sur les références lit au plus son budget plus
  une ligne ; vérification : rouge avant 3.4
- [ ] 2.4 Écrire le test de parité des deux stores (types d'entité et relations préfixes les uns des autres, un conflit,
  dernière page partielle, pages bout à bout, décalage au-delà du total), à côté de `PortfolioPersistenceParityTest`
  (test à écrire) — exigences : `portfolio-reference-reads` › Chaque store livré répond identiquement aux lectures de
  références ; `portfolio-reference-reads` › Une page de références suit un ordre déclaré et total ; vérification : ne
  compile pas avant 3.1

## 3. Correctif

- [ ] 3.1 Ajouter les lectures au port `PortfolioStore` et les implémenter dans `MemoryPortfolioStore` et
  `SqlitePortfolioStore` (requêtes en constantes nommées), compléter les doubles de test de `morpheus-application` (D2,
  D3, D4, D5) — exigence : `portfolio-reference-reads` › Chaque store livré répond identiquement aux lectures de
  références ; vérification : 2.4 vert
- [ ] 3.2 Faire passer `PortfolioQueryService` (page, page de projet, vue d'ensemble, conflits) par ces lectures —
  exigences : page découpée par le store ; compte et conflits ; vérification : 2.1 vert, `PortfolioIntelligenceContractTest`
  et les tests des routes de portfolio de `morpheus-api` verts
- [ ] 3.3 Faire dédoublonner `PortfolioRegistryService.addReference` par la recherche ciblée (D4) — exigence :
  `portfolio-reference-reads` › Un ajout de référence se dédoublonne par une recherche ciblée ; vérification : 2.2 vert
- [ ] 3.4 Faire lire à `QueryExecutionService` la page de portfolio limitée à `QueryBudgets.MAX_SOURCE_ROWS + 1` (D6) —
  exigence : `portfolio-reference-reads` › La requête de portée portfolio sur les références lit au plus son budget plus
  une ligne ; vérification : 2.3 vert, `QueryExecutionContractTest` vert

## 4. Schéma et garde de plan

- [ ] 4.1 Ajouter la migration de l'index `(portfolio_id, id)` au numéro suivant la dernière migration présente, son
  entrée dans `SqliteSchemaManager.MIGRATIONS`, l'avancée de `SUPPORTED_SCHEMA_VERSION`, et épingler son empreinte dans
  `SqliteMigrationChecksumGoldenTest` — exigence : `portfolio-reference-reads` › Une page de références d'un portfolio
  est découpée par le store ; vérification : `SqliteSchemaMigrationTest`, `SqliteIndexRedundancyTest`,
  `ReleasedSchemaUpgradeTest` verts
- [ ] 4.2 Ajouter à `SqliteQueryPlanGuardTest.GUARDED` la page de portfolio et le compte (servis), la page de projet
  (tri excusé avec sa raison), les conflits et la recherche de dédoublonnage — exigence : même exigence, scénario « Le
  plan de la page SQLite » ; vérification : garde verte, et échec nommé quand l'index ajouté est retiré d'une base de
  test (violation plantée consignée dans la PR, puis retirée)

## 5. Contrats et documentation durable

- [ ] 5.1 Ajouter à l'opération `listPortfolioReferences` de `docs/developer/reference/openapi/morpheus-v1-portfolio-m23.yaml`
  la description de l'ordre retenu (justification de gouvernance dans la PR) — exigence :
  `portfolio-reference-reads` › Une page de références suit un ordre déclaré et total ; vérification :
  `PublicHttpRouteConvergenceTest` vert, aucun paramètre ni borne modifiés
- [ ] 5.2 Ajouter la migration et le schéma atteint au guide de mise à niveau le plus récent de `docs/user/` —
  exigence : `schema-upgrade-guidance` › The latest upgrade guide SHALL state the schema version the code supports ;
  vérification : `UpgradeGuideCoherenceTest`, `UserDocumentationSpeaksOfTheInstalledProductTest`
- [ ] 5.3 Créer `docs/developer/specifications/portfolio-reference-reads.md` selon le gabarit de
  `docs/developer/specifications/README.md` (exigences, diagrammes de design.md, écarts, traçabilité) et l'indexer dans
  la section « Persistance SQLite et stores » du README — vérification : `RepositoryDocumentationCoherenceTest`

## 6. Vérification d'ensemble

- [ ] 6.1 Lancer `openspec validate page-portfolio-references-in-the-store --strict` — vérification : aucun
  avertissement
- [ ] 6.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1 à D6 → tâche → test) —
  vérification : chaque scénario a sa preuve
- [ ] 6.3 Lancer `./mvnw clean verify` puis `scripts\validate.cmd m19` (budget de la page de références déclaré par
  `AUD-PRF-30`) — vérification : vert ; budget M19 tenu sur les deux plateformes ; comptes d'exécution non inférieurs aux
  clés de `config/m21-quality-ratchets.properties`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire `openspec/specs/portfolio-reference-reads/spec.md` et sa page durable ; annoncer le
  changement d'ordre de la portée projet dans les notes de la version qui le porte.
