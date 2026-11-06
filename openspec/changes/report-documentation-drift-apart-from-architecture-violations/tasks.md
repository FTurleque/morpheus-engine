# Tâches : rapporter une désynchronisation documentaire à part d'une violation d'architecture

## 1. Reproduire

- [ ] 1.1 Écrire le test de la fonction de classification (échec marqué, échec non marqué, les deux, aucun échec ; étiquette héritée de la classe) avant la fonction — exigence : `enforcement-mechanism-accounting` › Un échec d'un contrôle de cohérence est rapporté comme une désynchronisation documentaire ; vérification : à écrire, rouge avant la fonction
- [ ] 1.2 Relever, avant tout changement, le compte des exécutions déclarées et générées de `morpheus-architecture-tests/target/surefire-reports/` après `./mvnw test -pl morpheus-architecture-tests` — exigence : `enforcement-mechanism-accounting` › Le verdict distinct ne change ni la sélection ni le compte des exécutions ; vérification : comptes consignés dans cette tâche

## 2. Livrer le verdict

- [ ] 2.1 Ajouter l'écouteur et la fonction de classification dans les sources de test du module, l'enregistrement `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`, et la dépendance de test `junit-platform-launcher` sans version (gérée par `junit-bom`) — exigence : les deux exigences ajoutées ; vérification : le test de 1.1 passe ; `./mvnw dependency:analyze` sans avertissement
- [ ] 2.2 Écrire `morpheus-architecture-tests/target/enforcement-verdict.txt` en fin de plan (méthode et sélection en première ligne, comptes par nature, tests de chaque verdict triés) et deux lignes de verdict sur la sortie — exigence : désynchronisation documentaire ; vérification : fichier lu après un run vert, deux verdicts vides et un compte d'exécutions marquées supérieur à zéro
- [ ] 2.3 Planter une violation de chaque nature, une à la fois puis ensemble (phrase d'une page lue par une classe marquée ; dépendance interdite dans une classe de production), lire le verdict, revenir en arrière — exigence : idem ; vérification : trois relevés consignés dans cette tâche
- [ ] 2.4 Relever de nouveau les comptes de 1.2 ; rejouer `-Dtest=<une classe marquée>` et `-Dgroups=coherence` — exigence : invariance ; vérification : comptes égaux à ceux de 1.2, aucun rapport hors de `target/surefire-reports/`, les deux commandes réussissent, résultats consignés

## 3. CI et documentation

- [ ] 3.1 Ajouter `morpheus-architecture-tests/target/enforcement-verdict.txt` à l'étape « Upload M21 integrity evidence » de `.github/workflows/ci.yml` — exigence : désynchronisation documentaire ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=*WorkflowContractTest,GateReachabilityArchitectureTest` vert
- [ ] 3.2 Mettre à jour `docs/developer/specifications/enforcement-mechanism-accounting.md` (deux exigences, section du verdict, diagramme de séquence, écart 5, traçabilité) et le paragraphe `@CoherenceCheck` de `.claude/rules/architecture.md` — vérification : `RepositoryDocumentationCoherenceTest` et `GovernanceSurfaceCoherenceTest` verts

## 4. Vérification d'ensemble

- [ ] 4.1 `openspec validate report-documentation-drift-apart-from-architecture-violations --strict` — vérification : aucun avertissement
- [ ] 4.2 `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert, fichier de verdict présent
- [ ] 4.3 `./mvnw clean verify` — vérification : verdict vert ; `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`) non touché
- [ ] 4.4 `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
