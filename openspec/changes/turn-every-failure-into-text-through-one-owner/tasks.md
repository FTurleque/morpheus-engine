# Tâches : former le texte de tout échec par un seul propriétaire

## 1. Reproduire

- [ ] 1.1 Écrire `FailureTextOwnershipArchitectureTest` (règle par signature sur `com.morpheus..`, propriétaires `FailureText` et `ServerLocationDisclosure`, `OTHER_PURPOSES` vide au départ) avant tout correctif — exigence : `failure-text-ownership` › Une copie de la conversion d'un échec en texte est refusée par sa signature ; vérification : la règle échoue sur le code actuel et nomme les sept `safeMessage`, `CliErrorLine.failureText`, `BoundaryFailureMessage.safe`, `OpenSpecSourceAttribution.relayable`, les trois `failureType` et `McpDiagnosticRedactor.describe` (liste exacte relevée dans la tâche)
- [ ] 1.2 Ajouter à la même classe le scan textuel de `RepositoryTree.files(root)` (sources principales, commentaires et littéraux blanchis) et ses tests sur exemples : lambda, expression en ligne, commentaire, message lu sans repli sur le type — exigence : `failure-text-ownership` › Le corps de la conversion n'est écrit que chez ses propriétaires ; vérification : rouge sur le code actuel, nomme au moins les fichiers des sept copies, `SqliteSavedViewStore.java` et `McpDiagnosticRedactor.java`
- [ ] 1.3 Chercher les tests qui figent le texte d'une vue enregistrée illisible (`SqliteSavedViewStore`, `SavedViewEntry.Unreadable`) et les textes des sept sites — exigence : `failure-text-ownership` › Le texte d'un échec a un seul propriétaire ; vérification : liste écrite dans la tâche ; un test qui figerait un message blanc est signalé avant D6
- [ ] 1.4 Écrire `FailureTextTest` (`morpheus-application`) et le test de `ServerLocationDisclosure.relayableText` : message présent, `null`, vide, blanc ; message qui nomme un chemin ; message trop long pour `isSafeToRelay` — exigences : › Le texte d'un échec a un seul propriétaire ; › Le texte relayé d'un échec a un seul propriétaire ; vérification : ces tests ne compilent pas avant le groupe 2

## 2. Factoriser

- [ ] 2.1 Ajouter `FailureText` dans `com.morpheus.application.foundation` et `ServerLocationDisclosure.relayableText` (D1, D4) — exigences : idem ; vérification : les tests de 1.4 passent ; `ApplicationPackageCyclesArchitectureTest` et `ApplicationTechnologyBoundaryArchitectureTest` verts
- [ ] 2.2 Remplacer les sept `safeMessage` par `FailureText.of` (`ProviderPluginService`, `ProviderPluginDiscovery`, `LocalSourceInventoryScanner`, `ReasoningService`, `MinosIntegrationRuntime`, `NexusMcpTechnicalContextProvider`, `McpToolFailure`) — exigence : › Le texte d'un échec a un seul propriétaire ; vérification : les tests de ces modules passent sans attente modifiée ; `./mvnw dependency:analyze` sans avertissement
- [ ] 2.3 Retirer `CliErrorLine.failureText` ; faire appeler `FailureText.of` par `CliErrorLine.refuse`, `CliCommandRun` et `MorpheusMain` (D2) — exigence : idem ; vérification : `CliErrorLineTest` et `OneErrorLineForEveryFamilyTest` verts sans attente modifiée
- [ ] 2.4 Faire converger la forme filtrée : `OpenSpecSourceAttribution.relayable` retiré au profit de `ServerLocationDisclosure.relayableText`, `BoundaryFailureMessage.safe` réduit à cet appel (D4) — exigence : › Le texte relayé d'un échec a un seul propriétaire ; vérification : `HttpFailureDisclosureArchitectureTest`, `ExtensionRoutesFailureDisclosureTest` et les tests d'attribution de `morpheus-provider-openspec` verts
- [ ] 2.5 Faire appeler `FailureText.of` par `SqliteSavedViewStore` (D6), si le mainteneur l'accepte ; sinon nommer le fichier dans `OTHER_SPELLINGS` avec sa raison — exigence : › Le corps de la conversion n'est écrit que chez ses propriétaires ; vérification : tests de `morpheus-store-sqlite` et parité `*PersistenceParityTest` verts
- [ ] 2.6 Remplir `OTHER_PURPOSES` (trois `failureType`, `McpDiagnosticRedactor.describe`, `BoundaryFailureMessage.safe`, chacun avec son usage) et `OTHER_SPELLINGS` (`McpDiagnosticRedactor.java`), et les déclarer `Kind.TABLE_OF_FACTS` dans `ProductionClassListsArchitectureTest.KINDS` — exigences : › Une copie … refusée par sa signature ; › Le corps … chez ses propriétaires ; vérification : 1.1 et 1.2 verts ; `ProductionClassListsArchitectureTest` vert
- [ ] 2.7 Casser chaque règle une fois : planter `private static String describe(RuntimeException f)` dans une classe d'adaptateur, puis une lambda `f -> f.getMessage() == null ? f.getClass().getSimpleName() : f.getMessage()` dans une autre ; constater l'échec qui nomme la classe et le fichier ; revenir en arrière — vérification : sortie des deux échecs citée dans la tâche ; arbre propre ensuite

## 3. Réaligner les tests qui citaient la conversion

- [ ] 3.1 Retirer `noCliTypeButTheOwnerTurnsAFailureIntoText` et `TURNS_A_FAILURE_INTO_TEXT` de `CliArgumentBaseArchitectureTest` (contrôle de `OTHER_PURPOSES` compris), la règle étendue les remplaçant — exigence : `cli-argument-base` › A private copy of the argument base SHALL be refused (inchangée, preuve déplacée) ; vérification : `CliArgumentBaseArchitectureTest` et `FailureTextOwnershipArchitectureTest` verts ; le nombre de méthodes `@Test` du module ne baisse pas
- [ ] 3.2 Réécrire la classe « message d'un échec » de `CliOptionValueHasOneBlankDefinitionTest` : un test de vide sur le message d'un échec dans la CLI devient une violation, et l'assertion `scan.throwableMessages() > 0` est remplacée par l'exigence de zéro (D2) — vérification : vert sur le code corrigé ; rouge si l'on remet le corps de `failureText` dans `CliErrorLine`
- [ ] 3.3 Ajouter à `AuditRemediationContractTest` l'interdit de `FailureText.of` dans `MorpheusProviderPluginMcpTools`, à côté de celui de `McpToolFailure.safeMessage` devenu sans objet, avec son message — vérification : vert ; `EnforcementMechanismAccountingTest` vert (aucun interdit de type sans message)

## 4. Documentation

- [ ] 4.1 Créer `docs/developer/specifications/failure-text-ownership.md` (gabarit de `docs/developer/specifications/README.md`, deux diagrammes, écarts et questions, traçabilité), l'ajouter à l'index `docs/developer/specifications/README.md`, puis remplacer dans `## Purpose` de la spec delta le chemin en code par le lien `/docs/developer/specifications/failure-text-ownership.md` — exigences : les quatre de `failure-text-ownership` ; vérification : `RepositoryDocumentationCoherenceTest` vert
- [ ] 4.2 Mettre à jour `docs/developer/specifications/cli-argument-base.md` (texte d'un échec par `FailureText.of`, preuve de R5 pour la conversion) — vérification : liens contrôlés par `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 `openspec validate turn-every-failure-into-text-through-one-owner --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [ ] 5.3 `./mvnw test -pl morpheus-architecture-tests -Dtest=FailureTextOwnershipArchitectureTest,CliArgumentBaseArchitectureTest,CliOptionValueHasOneBlankDefinitionTest,AuditRemediationContractTest,HttpFailureDisclosureArchitectureTest,ProductionClassListsArchitectureTest,EnforcementMechanismAccountingTest,CoherenceCheckMarkerTest,ApplicationPackageCyclesArchitectureTest,RepositoryDocumentationCoherenceTest`, puis `./mvnw clean verify` et `./mvnw dependency:analyze` — vérification : verdicts verts, aucun avertissement ; ratchets de présence relus dans `config/m21-quality-ratchets.properties`, aucun baissé
- [ ] 5.4 `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
