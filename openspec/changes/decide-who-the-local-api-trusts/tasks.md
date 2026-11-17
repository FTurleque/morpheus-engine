# Tâches : décider qui l'API locale croit

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md (documenter, capacité locale, allowlist obligatoire, ou combinaison), le sort des sondes et le point d'ADR-0105 sur un jeton éphémère — vérification : décision écrite dans la PR et reportée dans design.md
- [ ] 1.2 Ajuster le delta `specs/local-api-trust-boundary/spec.md` à la décision (retirer ou remplacer l'exigence de capacité si B n'est pas retenue) et relancer `openspec validate decide-who-the-local-api-trusts --strict` — vérification : aucun avertissement
- [ ] 1.3 Proposer l'ADR de la frontière de confiance de l'API locale (`Proposée`, jamais `Acceptée` sans le mainteneur), avec sa ligne d'index et `docs/architecture/arc42/09-decisions.md` — vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest`

## 2. Reproduction (option B)

- [ ] 2.1 Écrire `MorpheusLocalApiCapabilityIntegrationTest` (quatre scénarios du delta) sur un vrai serveur local — exigence : `local-api-trust-boundary` › The local API refuses a request that does not carry the local capability ; vérification : rouge avant 3.1 (le `POST /api/v1/projects` sans capacité enregistre le projet)

## 3. Correctif (option B)

- [ ] 3.1 Envelopper le serveur local par `CapabilityProtectedHttpServer` avec une capacité par processus et un code de refus propre (D3), sondes exemptées selon 1.1 — vérification : 2.1 vert
- [ ] 3.2 Écrire et durcir le fichier de capacité dans le répertoire de données, refuser le démarrage s'il ne peut l'être ; nommer la classe qui l'écrit dans `ApiTechnologyBoundaryArchitectureTest` ou `CliTechnologyBoundaryArchitectureTest` avec sa raison (justification de PR : test d'architecture) — vérification : 2.1 vert, `./mvnw test -pl morpheus-architecture-tests`
- [ ] 3.3 Faire présenter la capacité par les clients locaux livrés et par le serveur de test de `PublicHttpRouteConvergenceTest` — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=PublicHttpRouteConvergenceTest`
- [ ] 3.4 Déclarer le refus dans `docs/developer/reference/openapi/morpheus-v1.yaml` (réponse commune, bornée), sans ligne nouvelle dans `contracts/public-surfaces.tsv` (justification de PR : OpenAPI modifiée) — vérification : `PublicHttpRouteConvergenceTest`

## 4. Documentation durable

- [ ] 4.1 Écrire la frontière dans `SECURITY.md` et `docs/user/CLI.md` (et, pour B, les notes de version et le guide de mise à niveau de la version qui livre) — exigence : `local-api-trust-boundary` › The trust boundary of the local API is stated to the operator ; vérification : `UserDocumentationSpeaksOfTheInstalledProductTest`, `RepositoryDocumentationCoherenceTest`
- [ ] 4.2 Créer `docs/developer/specifications/local-api-trust-boundary.md` (gabarit de `docs/developer/specifications/README.md`, diagramme de séquence de design.md) et l'ajouter à l'index — vérification : `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate decide-who-the-local-api-trusts --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-api`, `./mvnw test -pl morpheus-architecture-tests` puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- L'acceptation de l'ADR proposé est une décision du mainteneur, séparée de l'archivage.
