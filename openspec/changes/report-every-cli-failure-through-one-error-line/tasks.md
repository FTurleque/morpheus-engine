# Tâches : écrire chaque échec de la CLI par une seule ligne d'erreur

## 1. Reproduire

- [x] 1.1 Ajouter à `CliArgumentBaseArchitectureTest` la règle qui refuse un littéral `"MORPHEUS …error…` hors de `CliErrorLine.java`, et son test sur exemples — exigence : `cli-argument-base` › Aucune commande n'écrit son propre préfixe d'erreur ; vérification : la règle échoue sur le code actuel en nommant les fichiers
      *2026-11-03 : rouge avant le correctif ; la règle nomme `MorpheusMain`, `MorpheusProductCli`,
      `MorpheusProviderPluginCli`, `MorpheusReasoningCli` et `MorpheusServerCli`.*
- [x] 1.2 Réécrire les attentes de `CliOptionRefusalTableTest` au format unique et ajouter les tests des lanceurs, de `--json` et d'une `CliFailure` sans message — exigence : `cli-argument-base` › Every CLI failure SHALL be reported through one error line ; vérification : ces tests échouent sur le code actuel
      *2026-11-03 : rouge avant le correctif : 3 sur 3 dans `OneErrorLineForEveryFamilyTest`, 32 cellules de
      `CliOptionRefusalTableTest`.*

## 2. Unifier

- [x] 2.1 Ajouter `CliErrorLine.refuse` et y faire passer `MorpheusProductCli`, `MorpheusReasoningCli`, `MorpheusProviderPluginCli`, `MorpheusServerCli` et `MorpheusMain`, codes de sortie inchangés — exigence : idem ; vérification : les tests du groupe 1 passent
      *2026-11-03 : fait. Les familles de gouvernance (`acceptance-criteria`, `augmented-context`, `composition`,
      `constraints evaluate`, `lifecycle`, `external-references`, `change-orchestration`, `policy`, `portfolio`,
      `query`) écrivaient leur refus en texte même sous `--json`, par `CliErrorLine.report` : elles passent aussi par
      `refuse`, et `report`, sans appelant, est retiré.*
- [x] 2.2 Faire écrire `failureText` pour une `CliFailure` dans `CliCommandRun` — exigence : idem ; vérification : le test ajouté en 1.2 passe
      *2026-11-03 : `CliErrorLineTest#aCommandRefusalWithoutAMessageIsWordedByItsTypeName` vert.*
- [x] 2.3 Mettre à jour les autres tests qui attendaient un ancien préfixe — vérification : `./mvnw test -pl morpheus-cli` vert
      *2026-11-03 : `./mvnw -o install -pl morpheus-cli` : 1 995 tests, aucun échec. `MorpheusPortfolioCliTest`
      attend désormais la forme JSON, où les guillemets du message sont échappés.*

## 3. Documentation

- [x] 3.1 Réécrire le paragraphe des notes de version 1.2.2 et la section d'installation qui citent les anciens préfixes — vérification : `UserDocumentationSpeaksOfTheInstalledProductTest` vert
      *2026-11-03 : vert.*
- [x] 3.2 Mettre à jour `docs/developer/specifications/cli-argument-base.md` (écart résolu, traçabilité) — vérification : liens contrôlés par `RepositoryDocumentationCoherenceTest`
      *2026-11-03 : vert.*

## 4. Vérification d'ensemble

- [x] 4.1 `openspec validate report-every-cli-failure-through-one-error-line --strict` — vérification : aucun avertissement
      *2026-11-03 : valide.*
- [x] 4.2 `./mvnw test -pl morpheus-architecture-tests -Dtest=CliArgumentBaseArchitectureTest,CliOptionParsingRefusesUnknownOptionsTest,RepositoryDocumentationCoherenceTest,UserDocumentationSpeaksOfTheInstalledProductTest` — vérification : verdict vert
      *2026-11-03 : vert, avec en plus `DispatchersHoldNoUseCaseArchitectureTest`, `CliCommandDispatchIsExhaustiveTest`,
      `PublicSurfaceManifestCoversEveryServedCliCommandTest`, `EnforcementMechanismAccountingTest`,
      `CoherenceCheckMarkerTest`. La suite d'architecture complète et le réacteur complet n'ont pas été lancés.*
      *2026-11-04 : `./mvnw clean verify` vert (959 tests d'architecture, 1 997 dans `morpheus-cli`). Le gate des
      lignes modifiées (`scripts/check-diff-coverage.py`) était à 74 % : deux tests de `OneErrorLineForEveryFamilyTest`
      couvrent l'échec de démarrage d'`api` et d'`api --remote` sur un port occupé, il est à 82 %.*
- [x] 4.3 `archgate check` — vérification : aucune violation
      *2026-11-03 : 30 sur 30, aucun avertissement.*

## Workflow follow-up

- Archiver le changement sur demande explicite, puis relire la spec principale consolidée.
