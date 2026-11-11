# Tâches : rapporter une désynchronisation documentaire à part d'une violation d'architecture

## 1. Reproduire

- [x] 1.1 Écrire le test de la fonction de classification (échec marqué, échec non marqué, les deux, aucun échec ; étiquette héritée de la classe) avant la fonction — exigence : `enforcement-mechanism-accounting` › Un échec d'un contrôle de cohérence est rapporté comme une désynchronisation documentaire ; vérification : à écrire, rouge avant la fonction
  - Relevé : `EnforcementVerdictTest` (six méthodes) écrit avant `EnforcementVerdict` ; rouge par compilation (type absent), vert après 2.1.
- [x] 1.2 Relever, avant tout changement, le compte des exécutions déclarées et générées de `morpheus-architecture-tests/target/surefire-reports/` après `./mvnw test -pl morpheus-architecture-tests` — exigence : `enforcement-mechanism-accounting` › Le verdict distinct ne change ni la sélection ni le compte des exécutions ; vérification : comptes consignés dans cette tâche
  - Relevé du 2026-11-06 sur `33acc9e0` (sélection Surefire par défaut du module, `-am`, sans `package`) : 210 rapports, 1001 exécutions déclarées, 0 générée. Échecs d'environnement, sans rapport avec le changement : `CoverageQualityGateTest` (aucun rapport JaCoCo sans `clean verify`) et deux méthodes de `ProviderPluginPlatformContractTest` (JAR de référence non empaqueté).

## 2. Livrer le verdict

- [x] 2.1 Ajouter l'écouteur et la fonction de classification dans les sources de test du module, l'enregistrement `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`, et la dépendance de test `junit-platform-launcher` sans version (gérée par `junit-bom`) — exigence : les deux exigences ajoutées ; vérification : le test de 1.1 passe ; `./mvnw dependency:analyze` sans avertissement
  - Écart : l'écouteur implémente une interface de `junit-platform-launcher` et lit des types de `junit-platform-engine` (`TestExecutionResult`, `TestTag`, `ClassSource`, `MethodSource`) ; les deux dépendances de test sont déclarées sans version, sinon `dependency:analyze` signale `junit-platform-engine` « used undeclared ». `dependency:analyze-only -DfailOnWarning=true -pl morpheus-architecture-tests -am` : « No dependency problems found ».
- [x] 2.2 Écrire `morpheus-architecture-tests/target/enforcement-verdict.txt` en fin de plan (méthode et sélection en première ligne, comptes par nature, tests de chaque verdict triés) et deux lignes de verdict sur la sortie — exigence : désynchronisation documentaire ; vérification : fichier lu après un run vert, deux verdicts vides et un compte d'exécutions marquées supérieur à zéro
  - Relevé (JAR de référence empaqueté) : `testClasses=211`, `executions.coherence=182`, `executions.code=825`, `documentationDrift=0` ; `architectureViolations=1`, `CoverageQualityGateTest`, qui exige les rapports JaCoCo d'un `clean verify` (4.3).
- [x] 2.3 Planter une violation de chaque nature, une à la fois puis ensemble (phrase d'une page lue par une classe marquée ; dépendance interdite dans une classe de production), lire le verdict, revenir en arrière — exigence : idem ; vérification : trois relevés consignés dans cette tâche
  - Violations plantées : statut d'ADR-0162 passé à « Proposée » dans `docs/architecture/adr/README.md` (lu par `AdrIndexCoherenceTest`, classe marquée) ; méthode `default` de `ReasoningAdapter` qui rend un type de `application.lifecycle.mutation` (`ReasoningPlatformArchitectureTest`). Sélection `-Dtest=AdrIndexCoherenceTest,ReasoningPlatformArchitectureTest`.
  - Documentation seule : `documentationDrift=1` (`AdrIndexCoherenceTest#theIndexAndTheRecordAgreeOnTheVerdictWhileTheRecordStaysFreeToQualifyIt()`), `architectureViolations=0`.
  - Code seul : `documentationDrift=0`, `architectureViolations=2` (`ReasoningPlatformArchitectureTest#applicationReasoningDoesNotDependOnAdaptersConcreteStoresOrMutationPackages()`, `#reasoningCoreContainsNoTransportPersistenceOrProcessExecution()`).
  - Les deux : `documentationDrift=1` et `architectureViolations=2`, chacun avec ses seuls tests. Les deux fichiers sont revenus à leur version commitée.
- [x] 2.4 Relever de nouveau les comptes de 1.2 ; rejouer `-Dtest=<une classe marquée>` et `-Dgroups=coherence` — exigence : invariance ; vérification : comptes égaux à ceux de 1.2, aucun rapport hors de `target/surefire-reports/`, les deux commandes réussissent, résultats consignés
  - Après le changement, même sélection : 211 rapports, 1007 exécutions déclarées, 0 générée ; les 210 rapports de 1.2 portent les mêmes méthodes, seul `EnforcementVerdictTest` (six exécutions) s'ajoute. Aucun rapport hors de `target/surefire-reports/`.
  - `-Dtest=AdrIndexCoherenceTest` : 8 exécutions, BUILD SUCCESS, verdict `testClasses=1`, `executions.coherence=8`, `executions.code=0`.
  - `-Dgroups=coherence` : 182 exécutions, BUILD SUCCESS, verdict `testClasses=26`, `executions.coherence=182`, `executions.code=0` — égal au compte des exécutions marquées de l'exécution complète.

## 3. CI et documentation

- [x] 3.1 Ajouter `morpheus-architecture-tests/target/enforcement-verdict.txt` à l'étape « Upload M21 integrity evidence » de `.github/workflows/ci.yml` — exigence : désynchronisation documentaire ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=*WorkflowContractTest,GateReachabilityArchitectureTest` vert
  - Relevé : `DependencyCheckWorkflowContractTest`, `AuditHardeningWorkflowContractTest`, `GateReachabilityArchitectureTest` verts.
- [x] 3.2 Mettre à jour `docs/developer/specifications/enforcement-mechanism-accounting.md` (deux exigences, section du verdict, diagramme de séquence, écart 5, traçabilité) et le paragraphe `@CoherenceCheck` de `.claude/rules/architecture.md` — vérification : `RepositoryDocumentationCoherenceTest` et `GovernanceSurfaceCoherenceTest` verts
  - Relevé : verts, avec `CoherenceCheckMarkerTest`, `EnforcementMechanismAccountingTest`, `ProductionClassListsArchitectureTest`, `ReactorModuleDocumentationTest`, `RepositoryTextHygieneContractTest`. `docs/developer/BUILD_AND_TEST.md` décrit aussi le fichier de verdict.

## 4. Vérification d'ensemble

- [x] 4.1 `openspec validate report-documentation-drift-apart-from-architecture-violations --strict` — vérification : aucun avertissement
- [x] 4.2 `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert, fichier de verdict présent
  - Relevé : fichier présent, `documentationDrift=0` ; seul échec, `CoverageQualityGateTest`, rangé en violation d'architecture, qui exige les rapports JaCoCo d'un `clean verify` (4.3) et non un `test` seul.
- [x] 4.3 `./mvnw clean verify` — vérification : verdict vert ; `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`) non touché — fait sur la branche d'intégration `chore/sprint-15-remainder` (Windows, JDK 21) : `./mvnw clean verify` BUILD SUCCESS ; module d'architecture 1068 exécutions déclarées, 0 générée, verdict `documentationDrift=0`, `architectureViolations=0` ; `architectureTestsMinimum` non touché
- [x] 4.4 `archgate check` — vérification : aucune violation
  - Relevé : 17 règles, 0 violation, 0 avertissement.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
