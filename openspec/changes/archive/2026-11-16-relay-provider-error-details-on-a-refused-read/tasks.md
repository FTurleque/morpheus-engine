# Tâches : relayer les détails d'une erreur de provider sur une lecture refusée

## 1. Reproduction

- [x] 1.1 Faire relire le message par `OpenSpecSpecificationProviderTest` (les quatre motifs, le nom non recopié) et
  écrire `OpenSpecSchemaRefusalStaysRelayableTest` — exigence : `openspec-provider-reading` › The refusal of a project
  schema says what would make it readable ; vérification : rouges avant 2.1
- [x] 1.2 Écrire dans `ProjectContentIngestionTest` les trois scénarios des diagnostics portés — exigence :
  `project-ingestion-port` › A refused read SHALL carry the error diagnostics of the provider ; vérification : ne
  compile pas avant 2.2
- [x] 1.3 Écrire `MorpheusCliTest#aSyncRefusedForAnUnsupportedSchemaRelaysTheDetailsOfItsDiagnostic` (texte et
  `--json`) et les cas de `CliErrorLineTest` — exigence : `cli-argument-base` › The error line of a refused project
  read SHALL relay the provider's error diagnostics ; vérification : rouge avant 2.3

## 2. Correctif

- [x] 2.1 Composer le message de `unsupportedSchema` par motif (D1) — vérification : tests de 1.1 verts
- [x] 2.2 Faire porter `providerErrors()` à `IncompleteProjectReadException` (D2) — vérification : tests de 1.2 verts
- [x] 2.3 Écrire `CliDiagnosticLines`, l'écriture de `CliErrorLine` qui relaie des diagnostics, et la branche de
  `CliCommandRun` (D3) — vérification : tests de 1.3 verts, `MorpheusProjectCliTest` renommé `CliDiagnosticLinesTest`, inchangé dans ce qu'il affirme
- [x] 2.4 Écrire `MorpheusApiProjectSyncIntegrationTest#aSyncRefusedForAnUnsupportedSchemaSaysWhatWouldMakeItReadable`
  (D4) — exigence : `openspec-provider-reading`, scénario « The refused read stays relayable » ; vérification : vert

## 3. Documentation durable

- [x] 3.1 Mettre à jour `docs/developer/specifications/openspec-provider-reading.md`, `project-ingestion-port.md` et
  `cli-argument-base.md`, et `docs/user/QUICKSTART.md` — vérification : `RepositoryDocumentationCoherenceTest`,
  `UserDocumentationSpeaksOfTheInstalledProductTest`

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate relay-provider-error-details-on-a-refused-read --strict` — vérification : aucun
  avertissement
- [x] 4.2 Lancer `./mvnw clean verify` et `archgate check` — vérification : verts
- [x] 4.3 Rejouer le refus avec la CLI construite sur un workspace dont le schéma ne déclare pas de parent, en texte et
  sous `--json` — vérification : le schéma, le fichier et `derives: spec-driven` sont à l'écran
