# Tâches : borner les termes d'une recherche d'exigences

## 1. Reproduction

- [ ] 1.1 Écrire `MorpheusRequirementSearchBoundIntegrationTest` : une recherche de quelques milliers de termes `a+a+…` puis de termes distincts au-delà de la limite SQLite, sur un vrai serveur local ; consigner dans le test le statut rendu aujourd'hui — exigence : `requirement-page-reads` › The number of terms of a requirement search SHALL be bounded ; vérification : rouge avant 2.2 (statut autre que `400` nommé)
- [ ] 1.2 Écrire `RequirementSearchQueryTest#aSearchAtTheBoundIsAccepted`, `#repeatedTermsCountOnce`, `#aSearchBeyondTheBoundIsRefusedByName` — exigence : idem ; vérification : rouges avant 2.1

## 2. Plafond et dédoublonnage

- [ ] 2.1 Ajouter `RequirementSearchQuery.MAX_TERMS` et le refus nommé dans le constructeur compact, `terms()` distincts dans l'ordre de première apparition (design.md, D1, D2) — vérification : 1.2 vert
- [ ] 2.2 Vérifier le classement `400` du refus en HTTP, et la raison relayée par la CLI et le MCP (cas dans les tests de `MorpheusRequirementCli` et `MorpheusRequirementMcpTools`, à écrire) — vérification : 1.1 vert
- [ ] 2.3 Documenter le plafond et la réponse `400` dans `docs/developer/reference/openapi/morpheus-v1.yaml` (justification de PR : OpenAPI modifiée ; `contracts/public-surfaces.tsv` inchangé) — vérification : `PublicHttpRouteConvergenceTest`

## 3. Un corpus par ligne

- [ ] 3.1 Écrire `SqliteRequirementPageTest#theCorpusIsBuiltOncePerRow` (compte des appels de la fonction) — exigence : `requirement-page-reads` › The cost of matching a requirement SHALL NOT grow with each term beyond one corpus per row ; vérification : rouge avant 3.2
- [ ] 3.2 Réécrire `pageSql` pour projeter le corpus une fois par ligne (design.md, D3), sans migration — vérification : 3.1 vert, `*PersistenceParityTest` et tests de `sqlite-index-coverage` verts

## 4. Documentation durable

- [ ] 4.1 Mettre à jour `docs/developer/specifications/requirement-page-reads.md` (exigences, diagramme, traçabilité) et `docs/user/CLI.md` si `--query` y est décrit — vérification : `RepositoryDocumentationCoherenceTest`, `UserDocumentationSpeaksOfTheInstalledProductTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate bound-the-search-terms-of-a-requirement-page --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-application,morpheus-store-sqlite,morpheus-api,morpheus-cli,morpheus-mcp`, `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` ; relancer `scripts\validate.cmd m19` pour le budget de requête — vérification : verts, aucun budget relevé

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
