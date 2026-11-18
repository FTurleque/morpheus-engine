# Tâches : interdire au serveur MCP d'atteindre une intégration ou l'hôte de plugins

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D3 de design.md : option A (SDK dans `CONCRETE_ADAPTERS`, ADR consolidé qui remplace `ARCH-0162`)
  ou option B (table de faits propre, proposée) — vérification : décision consignée dans la PR ; aucun ADR déclaré
  accepté par l'agent
- [ ] 1.2 Vérifier que `remove-the-unserved-provider-plugin-mcp-tool` (`AUD-ARC-27`) est appliqué — vérification :
  `morpheus-mcp/pom.xml` ne déclare plus `morpheus-provider-sdk`

## 2. Règle de frère du MCP

- [ ] 2.1 Planter une classe de `morpheus-mcp` qui référence une classe de `com.morpheus.integration.minos` (dépendance
  POM ajoutée localement) et une qui référence `ProviderPluginDiscovery` ; constater que la suite passe à tort —
  exigences : `provider-sdk-dependency-boundary` › Le serveur MCP NE DOIT dépendre d'aucune intégration MINOS ou NEXUS,
  › Le serveur MCP NE DOIT dépendre d'aucune classe du SDK provider ; vérification : verdict consigné, violation non
  committée
- [ ] 2.2 Ajouter `INTEGRATIONS` et `SDK` aux cibles de la règle de frère du MCP dans `AdapterSiblingArchitectureTest`,
  renommer constante et méthode sans en retirer, mettre à jour sa raison — vérification : la suite échoue sur les deux
  violations de 2.1 en nommant les classes, passe une fois retirées ; trace de la violation plantée dans la Javadoc ;
  justification de PR (test d'architecture modifié)
- [ ] 2.3 Vérifier qu'aucune classe d'intégration ne déclare de constante `static final` inlinable que le MCP pourrait
  lire ; sinon ajouter le doublon textuel avec message — vérification : `TypeBansAwaitingABytecodeRuleTest` et
  `EnforcementMechanismAccountingTest` verts

## 3. Accès de l'adaptateur HTTP au SDK

- [ ] 3.1 Mesurer avec ArchUnit les classes de `com.morpheus.api..` qui dépendent de `com.morpheus.sdk..` —
  exigence : `provider-sdk-dependency-boundary` › L'adaptateur HTTP N'atteint le SDK provider que par des classes
  nommées ; vérification : ensemble consigné dans la PR
- [ ] 3.2 Option retenue en 1.1 : B — écrire la liste exacte, sa règle et son test d'exactitude, et la déclarer table de
  faits dans `ProductionClassListsArchitectureTest` ; A — ajouter le SDK à `CONCRETE_ADAPTERS`, les classes mesurées à
  `COMPOSITION_ROOTS`, relever la clé de `config/debt-counter-ratchets.properties` et rédiger l'ADR consolidé
  `Proposée` — vérification : une classe plantée hors liste fait échouer la suite, une classe nommée sans dépendance
  aussi ; `everyListOfProductionClassesIsADebtCounterOrATableOfFacts` vert ; justification de PR (test d'architecture,
  et pour A fichier de cliquet)
- [ ] 3.3 Ajouter les trois exigences et leur matrice à `docs/developer/specifications/provider-sdk-dependency-boundary.md`,
  et la règle de frère du MCP à `.claude/rules/architecture.md` — vérification : `RepositoryDocumentationCoherenceTest`,
  `GovernanceSurfaceCoherenceTest` verts

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice exigence → scénario → D1/D2/D3 → tâche → test — vérification : chaque scénario a sa
  preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=AdapterSiblingArchitectureTest,AdapterCompositionRootArchitectureTest,ProductionClassListsArchitectureTest`
  puis `./mvnw clean verify` — vérification : verts ; comptes de `architectureTestsMinimum` non baissés

## Workflow follow-up

- Revoir le motif de la règle sur le SDK quand `separate-the-provider-plugin-spi-from-the-plugin-host` déplace l'hôte.
- Archiver le changement après la revue, sur demande explicite.
