# Tâches : fermer une connexion remote qui s'arrête avant l'authentification

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md (propriétés de `jdk.httpserver` posées et vérifiées, terminateur exigé, ou les deux) et les valeurs des deux bornes — vérification : décision écrite dans la PR et reportée dans design.md
- [ ] 1.2 Si D1 retient l'option A : proposer l'ADR consolidé qui remplace ADR-0099 (`Proposée`, jamais `Acceptée` sans le mainteneur), avec sa ligne d'index — vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest`

## 2. Reproduction

- [ ] 2.1 Écrire `MorpheusRemotePreAuthenticationBoundsIntegrationTest` : poignée TLS partielle, en-têtes partiels, connexions au-delà de la borne, sur un vrai serveur remote démarré par le bootstrap — exigences : `remote-pre-authentication-bounds` › A connection that stalls before its request is read is closed in bounded time ; › The number of open connections of the remote server is bounded ; vérification : rouge avant 3.1 (la connexion reste ouverte au-delà de l'échéance attendue)

## 3. Correctif

- [ ] 3.1 Mettre en place le mécanisme retenu en 1.1 (bootstrap remote, lancement CLI et `distribution/` si les propriétés sont posées au lancement ; refus nommé au démarrage si elles manquent) — vérification : 2.1 vert, `MorpheusRemoteHttpServerTest` et `MorpheusRemoteAdversarialClientTest` verts
- [ ] 3.2 Si une classe de la CLI lit ou pose une propriété du processus, la nommer avec sa raison dans `CliTechnologyBoundaryArchitectureTest` (table de faits ; justification de PR : fichier de gouvernance) — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=CliTechnologyBoundaryArchitectureTest`

## 4. Documentation durable

- [ ] 4.1 Écrire dans `SECURITY.md` et `docs/user/TEAM_REMOTE_SERVER.md` la borne, son réglage et son résiduel ; le mécanisme dans `docs/developer/REMOTE_SERVER_PLATFORM.md` ; compléter A-10 de `docs/architecture/risks/register.md` — exigence : `remote-pre-authentication-bounds` › The pre-authentication bounds are stated to the operator ; vérification : `UserDocumentationSpeaksOfTheInstalledProductTest`, `RepositoryDocumentationCoherenceTest`
- [ ] 4.2 Créer `docs/developer/specifications/remote-pre-authentication-bounds.md` (gabarit de `docs/developer/specifications/README.md`, diagramme de séquence de design.md) et l'ajouter à l'index — vérification : `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate close-a-remote-connection-that-stalls-before-authentication --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-api`, `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Si un ADR consolidé est proposé, son acceptation est une décision du mainteneur, séparée de l'archivage.
