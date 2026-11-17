# Tâches : partager les refus de lecture complète et l'analyse des lecteurs OpenSpec (AUD-QUA-34)

## 1. Préalable

- [ ] 1.1 Vérifier qu'AUD-QUA-29 (clôtures de code du lecteur de deltas) et AUD-SEC-20 (motifs des lecteurs OpenSpec)
  sont appliqués sur la branche — vérification : leurs changes archivés ou leurs tâches cochées

## 2. Refus de lecture complète (synthetic et Markdown)

- [ ] 2.1 Écrire, avant le correctif, les cas « budget » et « source illisible » dans
  `SyntheticSpecificationContentReaderTest` et `StructuredMarkdownSpecificationContentReaderTest` (à écrire ou compléter :
  code, clés de détail, raison de catégorie, aucun contenu) — exigence : `provider-ingestion-budget` › The synthetic and
  structured Markdown providers word a refusal of a whole read the same way ; vérification : le cas « source illisible »
  synthetic est rouge avant 2.2
- [ ] 2.2 Écrire `WholeReadRefusal` dans `com.morpheus.application.read` avec `WholeReadRefusalTest` (à écrire), puis y
  déléguer les deux `budgetRefusal` et les deux branches `IOException` (design.md D1, D2) — vérification : tests de 2.1
  verts ; `RefusalTestEvidenceArchitectureTest` et `ApplicationTechnologyBoundaryArchitectureTest` verts
- [ ] 2.3 Mettre à jour `docs/developer/specifications/provider-ingestion-budget.md` (exigence R3, diagramme, traçabilité)
  — vérification : `RepositoryDocumentationCoherenceTest` vert

## 3. Lecteurs OpenSpec

- [ ] 3.1 Écrire `OpenSpecEvidence` (package-private) et y faire passer `provenance` et `evidence` des trois lecteurs,
  avec la borne du lecteur de deltas (D3), et un test unitaire de la borne (à écrire : intervalle hors document ramené au
  document) — vérification : tests existants des trois lecteurs verts, sans modification de leurs attentes
- [ ] 3.2 Écrire `OpenSpecScenarioSteps` (package-private) et y faire passer la boucle de `normalizeScenario` des deux
  lecteurs — vérification : `OpenSpecCurrentSpecificationReaderTest` et `OpenSpecRequirementDeltaReaderTest` verts sans
  modification de leurs attentes
- [ ] 3.3 Mettre à jour `docs/developer/specifications/openspec-provider-reading.md` (composants partagés) —
  vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate share-whole-read-refusals-and-openspec-evidence-between-readers --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de `docs/developer/specifications/provider-ingestion-budget.md` —
  vérification : chaque scénario a une preuve
- [ ] 4.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts, et
  `PublicPayloadConvergenceTest` inchangé

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
