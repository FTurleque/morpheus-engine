# Tâches : séparer le SPI des plugins provider de l'hôte de plugins

## 1. Décision du mainteneur

- [ ] 1.1 Vérifier que `state-that-the-launcher-carries-the-provider-sdk` (`AUD-ARC-26`) est tranché et appliqué —
  vérification : la décision en vigueur dit que le launcher porte le SDK
- [ ] 1.2 Trancher D1 (paquet hôte, proposé, ou module hôte) et D3 (version d'API gardée, proposée, ou relevée) de
  design.md ; décider si l'ADR consolidé qui remplace `SEC-0129` porte aussi `AUD-ARC-26` — vérification : décisions
  consignées dans la PR ; ADR rédigé `Proposée`, jamais déclaré accepté par l'agent

## 2. Règles d'abord

- [ ] 2.1 Écrire la règle d'énumération des types publics du SPI et sa table de faits (D2), déclarée dans
  `ProductionClassListsArchitectureTest` — exigence : `provider-sdk-dependency-boundary` › Le SPI publié aux auteurs de
  plugins NE DOIT contenir que les types destinés aux plugins ; vérification : rouge sur l'arbre courant (types hôtes
  présents dans le paquet) ; justification de PR (test d'architecture)
- [ ] 2.2 Écrire la règle « un plugin n'atteint pas l'hôte » (bytecode et texte, D4) — exigence :
  `provider-sdk-dependency-boundary` › Un plugin NE DOIT dépendre que du SPI, jamais de l'hôte de plugins ;
  vérification : violation plantée (le plugin de référence référence `ProviderPluginService`) refusée en nommant les deux
  classes, puis retirée ; trace dans la Javadoc
- [ ] 2.3 Restreindre `UnreferencedPublicMethodArchitectureTest.NOT_JUDGED` au seul SPI pour le SDK (D5) — exigence :
  `dead-code-refusal` › A public method of a product module SHALL be referenced or excused by name ; vérification :
  méthode publique hôte plantée sans appelant refusée ; justification de PR

## 3. Séparation

- [ ] 3.1 Déplacer par `git mv` les types hôtes vers le paquet (ou le module) retenu en 1.2 ; scinder `ProviderSdk`
  (D2) ; mettre à jour les imports de `morpheus-api`, `morpheus-cli` et, s'il existe encore, `morpheus-mcp` —
  vérification : règles de 2.1 et 2.2 vertes ; `./mvnw dependency:analyze` sans avertissement
- [ ] 3.2 Mettre à jour les tests d'architecture qui lisent les sources du SDK par chemin (relevé par recherche de
  `morpheus-provider-sdk/src/main/java` dans `morpheus-architecture-tests`), `AdapterSiblingArchitectureTest.SDK_SOURCES`
  et ses règles, et les entrées exigées de `distribution/build-portable.ps1` et `.sh` — vérification :
  `./mvnw test -pl morpheus-architecture-tests` vert ; aucune assertion affaiblie ; comptes de `architectureTestsMinimum`
  non baissés
- [ ] 3.3 Option module seulement : déclarer le module dans le POM racine, son paquet dans
  `ModulePackageCorrespondenceTest`, ses deux clés au plancher D2 dans `config/m21-per-module-coverage-floors.properties`,
  et le nommer dans les pages qui listent le réacteur — vérification : `ModulePackageCorrespondenceTest`,
  `CoverageQualityGateTest#theFloorFileDeclaresEveryModuleOfThePopulationAndNoOther`, `RepositoryDocumentationCoherenceTest`
  verts ; justification de PR (fichier de cliquet)
- [ ] 3.4 Traiter les méthodes publiques hôtes sans appelant que révèle 2.3 (retrait, ou raison listée) — vérification :
  `UnreferencedPublicMethodArchitectureTest` vert

## 4. Documentation durable

- [ ] 4.1 Mettre à jour `docs/developer/PROVIDER_SDK.md` (paquet supporté, exemple de dépendance) et
  `docs/architecture/arc42/05-vue-blocs.md` §5.2 et §5.4 — vérification : `RepositoryDocumentationCoherenceTest` vert
- [ ] 4.2 Ajouter les exigences et leur matrice à `docs/developer/specifications/provider-sdk-dependency-boundary.md` et
  modifier `docs/developer/specifications/dead-code-refusal.md` — vérification : pages relues, liens résolus
- [ ] 4.3 Mettre à jour `.claude/rules/architecture.md` (section du SDK) et l'ADR consolidé — vérification :
  `GovernanceSurfaceCoherenceTest`, `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest` verts

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate separate-the-provider-plugin-spi-from-the-plugin-host --strict` — vérification :
  aucun avertissement
- [ ] 5.2 Compléter la matrice exigence → scénario → D1-D5 → tâche → test — vérification : chaque scénario a sa preuve
- [ ] 5.3 Lancer `./mvnw clean verify`, puis `scripts\validate.cmd m22 -Version <version de ProductMetadata>` (ou
  `scripts/validate-m22.sh`) pour la découverte et le probe du launcher packagé — vérification : verts, statut
  `SUPPORTED` du probe

## Workflow follow-up

- Revoir le motif de la règle MCP → SDK de `forbid-the-mcp-server-from-reaching-an-integration-or-the-plugin-host`.
- Archiver le changement après la revue, sur demande explicite.
