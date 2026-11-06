# Tâches : partager un seul cycle de vie entre les deux passerelles MCP

## 1. Reproduire

- [ ] 1.1 Rejouer la comparaison de `design.md` sur la tête de travail (`diff` des deux passerelles après normalisation du nom du pair) — exigence : `mcp-peer-gateway-lifecycle` › Le cycle de vie d'un client de pair MCP existe une fois ; vérification : le tableau de comparaison est vrai à la tête, ou la tâche consigne l'écart avant d'aller plus loin
- [ ] 1.2 Écrire `McpPeerGatewayLifecycleArchitectureTest` (D5) avant le correctif : interdit de `McpClient`, `McpSyncClient`, `BoundedStdioClientTransport`, `PeerOperationDeadline.arm`, `Files.deleteIfExists` et `Throwable.addSuppressed` depuis `com.morpheus.integration..`, et passage obligé par le socle pour toute classe qui dépend de `McpPeerLaunch` — exigence : › Une passerelle ne refait pas le cycle de vie ; vérification : la règle échoue sur le code actuel et nomme `MinosMcpCodeGateway` et `NexusMcpContextGateway`
- [ ] 1.3 Écrire `McpPeerSessionTest` dans `morpheus-mcp-transport`, sur ses pairs de test : ouverture, outil requis absent (client fermé, copie supprimée), échéance, résultat en erreur, charge vide, réponse trop grande, plus d'un contenu, fermeture, résidu compté, abandon compté, processus non signalé compté — exigences : › Le cycle de vie … existe une fois ; › Le socle du cycle de vie ne connaît aucun type MORPHEUS ; › Le comportement observable des deux passerelles est conservé ; vérification : ne compile pas avant le groupe 2

## 2. Extraire le socle

- [ ] 2.1 Ajouter `McpPeerSession` à `com.morpheus.transport.mcp` (D1, D2) : ouverture, `call`, `close`, suppression comptée de la copie, abandon compté, `Launch` sans enregistreur — exigences : idem ; vérification : 1.3 vert ; `AdapterCompositionRootArchitectureTest#theMcpTransportDependsOnNoOtherMorpheusPackage` vert
- [ ] 2.2 Réduire `MinosMcpCodeGateway` et `NexusMcpContextGateway` aux membres « passerelle » du tableau (D3) ; la préparation de la copie reste dans `launch`, son nettoyage sur échec appelle le socle — exigence : › Le comportement observable des deux passerelles est conservé ; vérification : `MinosMcpOperationDeadlineTest`, `NexusMcpOperationDeadlineTest`, `MinosMcpTransportIntegrationTest`, `NexusMcpTransportIntegrationTest` verts, attentes de message et de type inchangées
- [ ] 2.3 Retargeter `MinosSuppressedFailureTraceTest` et `NexusSuppressedFailureTraceTest` sur le câblage (observateurs remis au socle), et les tests de `requireBoundedResponse` sur le socle, sans retirer une exécution — exigence : › Le socle du cycle de vie ne connaît aucun type MORPHEUS ; vérification : verts ; comptes de `config/m21-quality-ratchets.properties` relus, aucun ratchet de présence baissé
- [ ] 2.4 Réaligner les épinglages textuels sans les affaiblir : `SilentCatchArchitectureTest#theModulesThatCannotCountAreHandedWhatDoes` (le littéral du câblage de `processTreeFailures` suit la nouvelle forme, celui de `MCP_PEER_PROCESS_TREE` reste), la raison de l'entrée `PeerOperationDeadline.java#RuntimeException` d'`EXPLAINED` relue ; `ExternalCodeTrustBoundaryArchitectureTest#noMcpLaunchPathTakesThePeerEnvironmentFromTheSdkParameters` étendu au fichier du socle et à sa classe — vérification : verts ; chaque littéral retiré est remplacé par un littéral de même intention
- [ ] 2.5 Casser la règle une fois : remettre `McpClient.sync(...)` dans une passerelle, puis un `Files.deleteIfExists` ; constater l'échec qui nomme la classe ; revenir en arrière — vérification : sorties citées dans la tâche ; 1.2 vert ensuite
- [ ] 2.6 Relire ADR-0128, décision 3 ; le clarifier sans changer son sens s'il laisse croire que la passerelle vérifie elle-même les outils (D1) — vérification : `AdrIndexCoherenceTest` vert ; `archgate check` sans violation

## 3. Documentation

- [ ] 3.1 Créer `docs/developer/specifications/mcp-peer-gateway-lifecycle.md` (gabarit de `docs/developer/specifications/README.md` : répartition finale, deux diagrammes, écarts et questions, traçabilité), l'ajouter à l'index `docs/developer/specifications/README.md`, puis remplacer dans `## Purpose` de la spec delta le chemin en code par le lien `/docs/developer/specifications/mcp-peer-gateway-lifecycle.md` — exigences : les quatre de `mcp-peer-gateway-lifecycle` ; vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 `openspec validate share-one-lifecycle-between-the-two-mcp-gateways --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [ ] 4.3 `./mvnw test -pl morpheus-architecture-tests -Dtest=McpPeerGatewayLifecycleArchitectureTest,AdapterCompositionRootArchitectureTest,AdapterSiblingArchitectureTest,SilentCatchArchitectureTest,ExternalCodeTrustBoundaryArchitectureTest,UnreachableSecurityBranchArchitectureTest,ProductionIntegrityContractTest,McpPeerSettingsResolutionArchitectureTest,RepositoryDocumentationCoherenceTest`, puis `./mvnw clean verify` et `./mvnw dependency:analyze` — vérification : verdicts verts, aucun avertissement ; le gate des lignes modifiées (`scripts/check-diff-coverage.py`) passe grâce aux tests du socle écrits dans son propre module
- [ ] 4.4 Scripts de compatibilité avec les vrais pairs sous `distribution/`, sur les deux plateformes si elles sont disponibles — vérification : MINOS et NEXUS démarrent, répondent et se ferment ; sinon la tâche dit ce qui n'a pas été lancé
- [ ] 4.5 `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
