# Tâches : faire vérifier à chaque test ce que son nom promet

## 1. Tests MCP

- [ ] 1.1 Réécrire les cinq tests MCP par `assertDoesNotThrow` nommé, et renommer
  `serverCatalogContainsPortfolioTools` (D1) — exigence : aucune (`skip_specs: true`) ; vérification :
  `./mvnw test -pl morpheus-mcp` vert, recherche `assertTrue(server != null)` sans résultat

## 2. Tests d'adaptateur et de transport

- [ ] 2.1 Remplacer `assertTrue(direct.getHandler() != null)` selon D2, après relecture de
  `LoopbackRequestProtectedHttpServer` — vérification : `./mvnw test -pl morpheus-api -Dtest=LoopbackRequestProtectedHttpServerTest`
  vert
- [ ] 2.2 Faire vérifier à `acceptsCustomStderrHandler` qu'un gestionnaire installé reçoit une ligne du pair de fixture
  bavard ; constater d'abord qu'il échoue si l'appel de `setStdErrorHandler` est retiré — vérification : rouge sans
  l'appel, vert avec ; `./mvnw test -pl morpheus-mcp-transport` vert

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate make-every-test-assert-what-its-name-promises --strict` — vérification : aucun
  avertissement
- [ ] 3.2 Vérifier que le nombre de méthodes de test des trois modules est inchangé (rapports Surefire avant et après)
  — vérification : mêmes comptes ; aucune clé de `config/m21-quality-ratchets.properties` touchée
- [ ] 3.3 Lancer `./mvnw clean verify` — vérification : vert, `RefusalTestEvidenceArchitectureTest` compris

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
