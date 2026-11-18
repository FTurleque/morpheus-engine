# Tâches : borner les appels simultanés à chaque pair MCP

## 1. Admission dans le socle

- [ ] 1.1 Écrire `McpPeerSessionAdmissionTest` (ouverture refusée sans lancement ni copie, place rendue à la fermeture, à l'échec d'initialisation et sur `Error`) — exigence : `mcp-peer-gateway-lifecycle` › Les sessions simultanées d'un même pair sont bornées par processus ; vérification : ne compile pas avant 1.2
- [ ] 1.2 Ajouter l'admission de pair à `morpheus-mcp-transport` et la faire prendre par `McpPeerSession.open` avant tout lancement (design.md, D1) — vérification : 1.1 vert ; `./mvnw test -pl morpheus-architecture-tests -Dtest=AdapterCompositionRootArchitectureTest` vert (`theMcpTransportDependsOnNoOtherMorpheusPackage` : aucune dépendance vers un autre paquet MORPHEUS)

## 2. Passerelles MINOS et NEXUS

- [ ] 2.1 Écrire les cas de saturation et de bornes séparées dans `NexusMcpTechnicalContextProviderTest` et `MinosMcpExternalReferenceResolverTest` — exigence : idem ; vérification : rouges avant 2.2
- [ ] 2.2 Créer l'admission de chaque pair dans `MinosIntegrationRuntime` et `NexusIntegrationRuntime`, la remettre aux fabriques de passerelles, rendre le refus en observation `UNAVAILABLE` nommée (D2) — vérification : 2.1 vert, tests existants des deux modules verts
- [ ] 2.3 Écrire `RemotePeerCallAdmissionIntegrationTest` (rafale de lecteurs vers un pair factice lent) — exigence : idem, scénario « La borne tient sous une rafale de lecteurs » ; vérification : vert, durée des requêtes en surplus bien sous l'échéance du pair

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/mcp-peer-gateway-lifecycle.md` (exigence, diagramme, traçabilité) et `docs/user/INTEGRATIONS.md` — vérification : `RepositoryDocumentationCoherenceTest`, `UserDocumentationSpeaksOfTheInstalledProductTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate bound-concurrent-calls-to-each-mcp-peer --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-mcp-transport,morpheus-integration-minos,morpheus-integration-nexus,morpheus-api`, `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
