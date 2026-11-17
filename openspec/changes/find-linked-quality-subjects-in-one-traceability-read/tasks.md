# Tâches : trouver les sujets liés du rapport qualité en lectures ensemblistes

## 1. Reproduire avant de corriger

- [ ] 1.0 Vérifier que le budget M19 du rapport qualité déclaré par `AUD-PRF-30` est livré et relever sa métrique sur `HEAD` — vérification : ligne de `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` et métrique avant correction citées dans la PR ; sinon s'arrêter
- [ ] 1.1 Écrire `QualityReportTraceabilityCostTest` (à écrire, `morpheus-architecture-tests`, paquet `com.morpheus.architecture`) : stores mémoire, port de traçabilité compté (toutes ses méthodes de lecture), deux snapshots de tailles différentes avec membres liés et non liés de chaque sorte — exigence : `quality-report-traceability-cost` › A quality report SHALL read the traceability of its snapshot a number of times that does not depend on its size ; vérification : rouge sur `HEAD` avec les deux comptes nommés ; justification de PR pour un test ajouté sous `morpheus-architecture-tests`
- [ ] 1.2 Relever sur `HEAD` le rapport qualité complet (constats ordonnés et métriques) des deux snapshots de 1.1 comme référence, dans le même test — exigence : `quality-report-traceability-cost` › The findings of a quality report SHALL be those the links of its snapshot establish ; vérification : vert sur `HEAD`

## 2. Le port et ses deux stores

- [ ] 2.1 Ajouter `identitiesWithAnyLink` et `linksBetweenKinds` à `TraceabilityStore` (design.md, D1), avec leur Javadoc de contrat (ensemble vide = tous, ordre, refus d'un snapshot inconnu) — exigence : › The findings of a quality report SHALL be those the links of its snapshot establish ; vérification : compilation des deux stores
- [ ] 2.2 Implémenter dans `MemoryTraceabilityStore` ; tests dans `MemoryTraceabilityStoreTest` (sens unique, autre snapshot, snapshot inconnu refusé par `snapshot not found for traceability link: `) — vérification : tests verts
- [ ] 2.3 Implémenter dans `SqliteTraceabilityStore` avec paramètres liés, sans requête par lien pour les preuves ; tests dans `SqliteTraceabilityStorePersistenceTest`, dont un `EXPLAIN QUERY PLAN` qui refuse un parcours de `traceability_links` sans index — vérification : tests verts
- [ ] 2.4 Écrire `TraceabilitySetReadParityTest` (à écrire, `morpheus-architecture-tests`) : mêmes liens dans les deux stores, comparaison des deux lectures et du rapport qualité — exigence : idem, scénario « Both stores give the same report » ; vérification : vert

## 3. Les services du rapport

- [ ] 3.1 `RequirementQualityService` : une lecture `identitiesWithAnyLink(REQUIREMENT)` — exigence : › A quality report SHALL read…; vérification : 1.2 inchangé
- [ ] 3.2 `TaskQualityService` et `ChangeCompletenessService` : une lecture `linksBetweenKinds({CHANGE}, {AFFECTS}, {REQUIREMENT})` indexée par changement — vérification : 1.2 inchangé ; `ChangeCompletenessService` garde aussi la forme mono-changement livrée par `assess-the-orchestration-state-of-a-change-once` si elle est déjà présente
- [ ] 3.3 `DecisionReferenceQualityService` : une lecture `DECIDED_BY` depuis `CHANGE`, une lecture vers `EXTERNAL_REFERENCE` filtrée en mémoire par les racines — vérification : 1.2 inchangé, 1.1 vert
- [ ] 3.4 Casser la règle une fois : rétablir la double lecture par requirement — vérification : 1.1 rouge en nommant les comptes, puis revert ; trace dans la Javadoc du test ou la PR

## 4. Documentation durable

- [ ] 4.1 Créer `docs/developer/specifications/quality-report-traceability-cost.md` (gabarit de `docs/developer/specifications/README.md`, flowchart de design.md) et l'ajouter à la section « Exploitation, providers et coûts » de l'index — vérification : `RepositoryDocumentationCoherenceTest` vert

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate find-linked-quality-subjects-in-one-traceability-read --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=QualityReportTraceabilityCostTest,TraceabilitySetReadParityTest,RequirementQualityContractTest,QualityReportSnapshotCoherenceTest,AggregateQualityReportContractTest` puis `./mvnw clean verify` — vérification : vert ; aucun ratchet baissé ; `./mvnw dependency:analyze` sans avertissement

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
