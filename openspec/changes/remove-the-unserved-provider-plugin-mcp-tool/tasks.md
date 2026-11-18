# Tâches : retirer l'outil MCP de plugins provider que rien ne sert

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D4 de design.md : clarification de `BE-0102` §3 et de `SEC-0129` (proposée) ou ADR consolidé qui
  remplace `BE-0102` — vérification : décision consignée dans la PR ; aucun ADR déclaré accepté par l'agent

## 2. Contrôle d'abord, puis retrait

- [ ] 2.1 Écrire le contrôle textuel de D2 (sources principales de `morpheus-mcp`, littéraux `discover_provider_plugins`
  et `probe_provider_plugin`, au moins une source lue) — exigence : `public-surface-convergence` › Le module MCP NE DOIT
  déclarer aucun outil de plugin provider ; vérification : le test est rouge sur l'arbre courant (la classe déclare
  `discover_provider_plugins`) et nomme `MorpheusProviderPluginMcpTools.java` ; justification de PR (test
  d'architecture ajouté)
- [ ] 2.2 Retirer `MorpheusProviderPluginMcpTools`, son appel dans `MorpheusMcpServer`, la dépendance
  `morpheus-provider-sdk` de `morpheus-mcp/pom.xml`, `MorpheusProviderPluginMcpToolsTest` et
  `MorpheusProviderPluginMcpExposureTest`, la ligne `ProviderPlugin` de `McpStoreFailureInjectionTest` et les
  mentions des Javadoc de `GrowingCollectionToolsArePageableTest` et `McpToolFailure` — vérification : le test de 2.1
  passe ; `./mvnw -pl morpheus-mcp -am verify` vert ; `./mvnw dependency:analyze` sans avertissement
- [ ] 2.3 Remplacer dans `ProviderPluginPlatformContractTest` et `ProviderPluginTrustBoundaryContractTest` les
  assertions qui lisaient la classe par l'appel au contrôle de 2.1 ; retirer de `AuditRemediationContractTest` et de
  `SilentCatchArchitectureTest` les volets dont la classe était le sujet ; réécrire la Javadoc de
  `PublicSurfaceManifestCoversEveryServedToolTest` — exigence : idem ; vérification :
  `./mvnw test -pl morpheus-architecture-tests` vert ; comptes de `testsMinimum` et `architectureTestsMinimum`
  (`config/m21-quality-ratchets.properties`) non baissés, une méthode retirée étant compensée ; justification de PR
  (tests d'architecture modifiés)
- [ ] 2.4 Retirer `com/morpheus/mcp/MorpheusProviderPluginMcpTools.class` des entrées exigées de
  `distribution/build-portable.ps1` et `distribution/build-portable.sh` — vérification : `GateReachabilityArchitectureTest`
  vert ; build portable rejoué sur une plateforme, liste des classes vérifiée

## 3. Manifeste et documentation durable

- [ ] 3.1 Réécrire la note de `provider.plugins.discover` dans `contracts/public-surfaces.tsv` (D3), colonnes
  inchangées — exigence : idem ; vérification : `ProviderPluginPlatformContractTest`,
  `PublicSurfaceManifestCoversEveryServedToolTest` verts ; justification de PR (manifeste)
- [ ] 3.2 Réécrire la section MCP de `docs/user/PROVIDER_PLUGINS.md`, la phrase MCP de `docs/developer/PROVIDER_SDK.md`
  et retirer la ligne de `docs/developer/MCP.md` — vérification : `RepositoryDocumentationCoherenceTest`,
  `UserDocumentationSpeaksOfTheInstalledProductTest` verts
- [ ] 3.3 Appliquer la décision de 1.1 à `BE-0102` et `SEC-0129` — vérification : `AdrIndexCoherenceTest`,
  `AdrBriefingBudgetTest` verts
- [ ] 3.4 Ajouter l'exigence et sa ligne de matrice à `docs/developer/specifications/public-surface-convergence.md` —
  vérification : page relue, lien vers le test résolu

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate remove-the-unserved-provider-plugin-mcp-tool --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice exigence → scénario → D2 → tâche 2.1 → test — vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests -Dtest=*M22*,PublicSurfaceManifestCoversEveryServedToolTest`
  — vérification : verts ; `morpheus-architecture-tests/target/enforcement-verdict.txt` sans violation

## Workflow follow-up

- Appliquer ensuite `forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host` (`AUD-ARC-29`).
- Archiver le changement après la revue, sur demande explicite.
