# Tâches : résoudre chaque rapport de gouvernance une fois par évaluation

## 1. Prérequis

- [ ] 1.1 Vérifier que `assess-the-orchestration-state-of-a-change-once` est livré (variante d'évaluation de transition qui reçoit snapshot, contenu et complétude) — exigence : `policy-evaluation-cost` › An evaluation SHALL compute each report its quality and lifecycle rules share once ; vérification : la variante existe sur la branche ; sinon s'arrêter
- [ ] 1.2 Vérifier que le budget M19 de l'évaluation de gouvernance déclaré par `AUD-PRF-30` est livré et relever sa métrique sur `HEAD` — vérification : ligne de `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` et métrique avant correction citées dans la PR ; sinon s'arrêter

## 2. Reproduire avant de corriger

- [ ] 2.1 Écrire `PolicyEvaluationCostTest` (à écrire, `morpheus-architecture-tests`, paquet `com.morpheus.architecture`) : stores mémoire et `MemoryPolicyPackStore` enveloppés dans des ports comptés ; un pack d'une règle `QUALITY_THRESHOLD` contre un pack de nombreuses règles de qualité ; plusieurs `LIFECYCLE_GUARD` sur un même changement ; deux packs avec overrides — exigences : `policy-evaluation-cost` › An evaluation SHALL read the overrides of its scope once, › An evaluation SHALL compute each report its quality and lifecycle rules share once ; vérification : rouge sur `HEAD` avec les deux comptes nommés ; justification de PR pour un test ajouté sous `morpheus-architecture-tests`
- [ ] 2.2 Dans le même test, l'égalité règle par règle (décision, valeur observée, preuves, raison) entre le pack nombreux et chaque règle évaluée seule — vérification : vert sur `HEAD`, référence que la correction ne doit pas déplacer
- [ ] 2.3 Compléter `PolicyEvaluationServiceTest` : deux overrides sur des règles de même identité dans deux packs ; et un pack mixte qualité + cycle de vie sans snapshot actif (`UNKNOWN`, agrégat `UNKNOWN`) — exigences : idem ; vérification : vert sur `HEAD` et après

## 3. La correction

- [ ] 3.1 Lire `listOverrides(scope)` une fois par appel public de `PolicyEvaluationService` et indexer par (pack, règle) — exigence : `policy-evaluation-cost` › An evaluation SHALL read the overrides of its scope once ; vérification : 2.1 (volet overrides) vert, 2.3 vert
- [ ] 3.2 Ajouter `PolicyFactResolver.forOneEvaluation()` (méthode `default`) et la session mémoïsante de `DefaultPolicyFactResolver` (rapport qualité, état de composition, snapshot actif, contenu et requirements, complétude par changement via la variante de `AUD-PRF-21`) — exigence : › An evaluation SHALL compute each report its quality and lifecycle rules share once ; vérification : 2.1 et 2.2 verts ; `PolicyPackContractTest`, `QualityThresholdFactTest`, `DuplicatedCompositionPolicyContractTest`, `EmptyPopulationQualityPolicyContractTest` verts sans modification
- [ ] 3.3 Casser la règle une fois : remettre `findOverride` dans la boucle, puis un `assessActive` par règle — vérification : 2.1 rouge à chaque fois en nommant les comptes, puis revert ; trace dans la Javadoc du test ou la PR

## 4. Documentation durable

- [ ] 4.1 Créer `docs/developer/specifications/policy-evaluation-cost.md` (gabarit de `docs/developer/specifications/README.md`, diagramme de séquence de design.md) et l'ajouter à la section « Exploitation, providers et coûts » de l'index — vérification : `RepositoryDocumentationCoherenceTest` vert

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate resolve-each-governance-report-once-per-evaluation --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=PolicyEvaluationCostTest,PolicyPackContractTest,PolicyPersistenceParityTest` puis `./mvnw clean verify` — vérification : vert ; aucun ratchet baissé

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
