# Tâches : donner un appelant de production à tout type public du produit (AUD-QUA-27)

## 1. Décision du mainteneur

- [ ] 1.1 Trancher le sort de chacune des dix classes (supprimer, câbler, ou garder avec une raison écrite), design.md
  D1, et l'inscrire dans la description de la PR — exigence : `dead-code-refusal` › A public type of a product module
  SHALL have a production caller or a written reason ; vérification : une ligne par classe dans la PR
- [ ] 1.2 Trancher le traitement de chaque ADR qui nomme une classe supprimée (DATA-0125, BE-0020, BE-0035, ARCH-0150,
  ARCH-0136, DATA-0141, DATA-0142), design.md D4 — vérification : décision écrite dans la PR ; aucun ADR déclaré accepté
  sans le mainteneur
- [ ] 1.3 Instruire `ExternalTraceabilityLinkFactory` : quelle classe livrée tient le point 4 de DATA-0142 — vérification :
  classe nommée dans design.md, ou décision de câblage

## 2. Porte des types publics

- [ ] 2.1 Écrire `UnreferencedPublicTypeArchitectureTest` (à écrire) avec ses tests de forme sur des sources plantées :
  type nommé seulement par ses tests, type déclaré par un `META-INF/services`, module d'outillage, entrée sans raison,
  entrée périmée — exigence : `dead-code-refusal` › A public type of a product module SHALL have a production caller or a
  written reason ; vérification : chaque test de forme échoue sur la violation plantée puis passe, trace dans la PR
- [ ] 2.2 Remplir la table d'exceptions de la porte avec les types du lot et de leur cascade (raison : « en attente de la
  décision AUD-QUA-27 »), design.md D5 — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=UnreferencedPublicTypeArchitectureTest` vert ; justification de PR (test d'architecture nouveau)
- [ ] 2.3 Prouver que la porte ne retient pas zéro type (garde de la forme « too few public types examined », comme la
  porte des méthodes) — vérification : la garde échoue sur un ensemble vide planté

## 3. Exécution de la décision, classe par classe

- [ ] 3.1 Pour chaque classe à supprimer : retirer la classe, les types de sa cascade (design.md, Contexte) et leurs
  tests, puis son entrée de la table de la porte — exigence : `dead-code-refusal` › A public type of a product module
  SHALL have a production caller or a written reason, scénario « No type of the audit remains without a production
  caller or a reason » ; vérification : la porte passe et l'entrée retirée n'y est plus
- [ ] 3.2 Si `LocalSourceWatcher`, `ProjectDiscoveryService` ou `WorkspaceRootResolver` partent : les retirer de
  `ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` et de l'énumération balisée d'ARCH-0150, et baisser
  d'autant la clé `ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` de
  `config/debt-counter-ratchets.properties` dans le même commit — vérification :
  `./mvnw test -pl morpheus-architecture-tests -Dtest=ApplicationTechnologyBoundaryArchitectureTest+ProductionClassListsArchitectureTest` vert ; justification de PR (fichier de ratchet, baisse d'un compteur de dette)
- [ ] 3.3 Si `LocalSourceWatcher` part : retirer `LocalSourceWatcher.java#IOException` de `SilentCatchArchitectureTest`,
  ajouter à ce change un delta MODIFIED (ou RENAMED) de `workspace-boundary-scan-cost` › What a scan remembers MUST NOT
  outlive that scan nor reach the watcher, et mettre à jour `docs/developer/specifications/workspace-boundary-scan-cost.md`
  — vérification : `openspec validate give-every-public-type-of-the-product-a-production-caller --strict`, `SilentCatchArchitectureTest` vert
- [ ] 3.4 Si `ExternalLinkPolicy` part : retirer `ExternalLinkPolicyTest` des `-Dtest` de `scripts/validate-m19.ps1` et
  `scripts/validate-m19.sh` (en ASCII pur pour le `.ps1`), et corriger `docs/developer/OPERABILITY.md` — vérification :
  `GateReachabilityArchitectureTest` vert, `scripts\validate.cmd m19` relu sur un run réel
- [ ] 3.5 Pour chaque classe à câbler : la surface qui la construit, son test d'intégration de bout en bout, et, si la
  surface est publique, sa ligne de `contracts/public-surfaces.tsv` et son opération OpenAPI bornée — vérification :
  `PublicSurfaceManifestCoversEveryServedCliCommandTest`, `PublicHttpRouteConvergenceTest`,
  `PublicSurfaceManifestCoversEveryServedToolTest` verts ; justification de PR (manifeste, OpenAPI)
- [ ] 3.6 Avant chaque suppression de méthodes `@Test`, comparer le compte d'exécutions déclarées restant à
  `testsMinimum` et `architectureTestsMinimum` de `config/m21-quality-ratchets.properties` — vérification : les
  validateurs `validate-m21` et `validate-d2` passent sans baisser de clé

## 4. Documentation et ADR

- [ ] 4.1 Corriger `docs/developer/REMOTE_SERVER_PLATFORM.md` (ligne du watcher) selon la décision — vérification :
  `RepositoryDocumentationCoherenceTest` vert
- [ ] 4.2 Rédiger les ADR consolidés ou les archivages décidés en 1.2 (gabarit `docs/architecture/adr/gabarit/template.md`,
  numéro suivant le plus haut du registre et de l'archive, ligne d'index et `docs/architecture/arc42/09-decisions.md`) —
  vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest`, `OpenSpecDecisionRecordCitationTest` verts
- [ ] 4.3 Mettre à jour `docs/developer/specifications/dead-code-refusal.md` : exigence R4, diagramme des portes, écarts
  et traçabilité — vérification : page relue, liens résolus par `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate give-every-public-type-of-the-product-a-production-caller --strict` — vérification :
  aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de `docs/developer/specifications/dead-code-refusal.md` (exigence →
  scénario → conception → tâche → preuve) — vérification : chaque scénario a une preuve ou « preuve manquante »
- [ ] 5.3 À l'archivage, éditer le `## Purpose` de `openspec/specs/dead-code-refusal/spec.md` pour nommer les types —
  vérification : `openspec validate --specs --strict`
- [ ] 5.4 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : verts, et
  `morpheus-architecture-tests/target/enforcement-verdict.txt` sans violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
- Débloque `count-only-production-sources-as-callers-of-a-public-method` (AUD-QUA-28) et
  `order-the-refusal-of-a-requirement-delta-application-plan` (AUD-QUA-38).
