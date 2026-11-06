# Tâches : demander dans la pull request l'intention de chaque assertion textuelle

## 1. Reproduire

- [x] 1.1 Écrire, avant le gabarit, la classe de cohérence (marquée `@CoherenceCheck`) qui exige `.github/pull_request_template.md` et sa section d'intention, une méthode par intention — exigence : `enforcement-mechanism-accounting` › Le gabarit de pull request demande l'intention de chaque assertion textuelle ajoutée ; vérification : à écrire, la méthode échoue sur `30520bd3` en nommant le chemin attendu
  - Fait : `PullRequestTemplateCoherenceTest` (`@CoherenceCheck`), méthodes `theTemplateAsksForTheIntentionOfEveryTextualAssertion` et, sur gabarits fabriqués, `aMissingTemplateIsRefusedByItsExpectedPath`, `aTemplateWithoutTheIntentionSectionIsRefusedBySectionName`. Rouge avant le gabarit : « .github/pull_request_template.md is missing: the pull request template asks for the intention of every textual assertion and the justification of every governance change ».
- [x] 1.2 Ajouter à la même classe la méthode qui exige la section de gouvernance et les chemins qu'elle nomme (manifeste, `config/`, OpenAPI, suite d'architecture) — exigence : `enforcement-mechanism-accounting` › Le gabarit de pull request demande la justification d'une modification de gouvernance ; vérification : à écrire, rouge avant le gabarit
  - Fait : `theTemplateAsksForTheJustificationOfAGovernanceChange` et `aTemplateWithoutTheGovernanceSectionOrOneOfItsFilesIsRefused` ; rouge avant le gabarit avec le même message d'absence.

## 2. Livrer le gabarit

- [x] 2.1 Créer `.github/pull_request_template.md` : section d'intention (par assertion textuelle ajoutée ou modifiée : fichier, phrase d'intention, mécanisme retenu, violation plantée pour une règle sur le bytecode) et section de gouvernance (liste de `.claude/rules/governance.md`, hausse d'une clé de `config/debt-counter-ratchets.properties`), chemins écrits en code — exigence : les deux exigences ajoutées ; vérification : les méthodes du groupe 1 passent
- [x] 2.2 Casser chaque méthode une fois : supprimer la section d'intention, puis la section de gouvernance, puis le fichier ; constater l'échec qui nomme le chemin et la section ; revenir en arrière — vérification : trois échecs consignés dans cette tâche
  - Section d'intention renommée : « .github/pull_request_template.md has no section "## Intention de chaque assertion textuelle" ». Section de gouvernance renommée : « … has no section "## Justification d'une modification de gouvernance" ». Manifeste retiré de la section : « … does not name `contracts/public-surfaces.tsv` ». Fichier supprimé : « .github/pull_request_template.md is missing: … » sur les deux méthodes. Retour arrière, puis vert.
- [x] 2.3 Vérifier qu'aucun workflow ne lit `github.event.pull_request.body` — exigence : scénario « Une pull request laisse la section vide » ; vérification : recherche dans `.github/workflows/`, résultat consigné
  - `grep -rn "pull_request.body" .github/workflows/` : aucun résultat ; seules occurrences de `github.event.pull_request` : `head.sha` dans `ci.yml`, `codeql.yml`, `security.yml`.

## 3. Règles et documentation

- [x] 3.1 Ajouter le renvoi au gabarit dans `.claude/rules/architecture.md` (après « écrire l'intention en une phrase »), `.claude/skills/enforcement-choice/SKILL.md` et `.claude/rules/governance.md` (puce « Justifier dans la description de la PR ») — vérification : `GovernanceSurfaceCoherenceTest` vert
- [x] 3.2 Mettre à jour `docs/developer/specifications/enforcement-mechanism-accounting.md` : deux exigences au tableau, section du gabarit, diagramme « Qui tient quoi », traçabilité (le scénario « Une pull request laisse la section vide » marqué « tenu par la revue ») — vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [x] 4.1 `openspec validate ask-for-the-intention-of-every-textual-assertion-in-the-pull-request --strict` — vérification : aucun avertissement
  - « Change 'ask-for-the-intention-of-every-textual-assertion-in-the-pull-request' is valid ».
- [x] 4.2 `./mvnw test -pl morpheus-architecture-tests -Dtest=<classe ajoutée>,CoherenceCheckMarkerTest,EnforcementMechanismAccountingTest,GovernanceSurfaceCoherenceTest,RepositoryDocumentationCoherenceTest` — vérification : verdict vert
  - 40 exécutions, 0 échec, `BUILD SUCCESS`.
- [ ] 4.3 `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert, le nombre d'exécutions déclarées du module monte (`architectureTestsMinimum` de `config/m21-quality-ratchets.properties` non touché)
  - Lancé par `test` (sans `package`) : 1 014 exécutions, quatre échecs étrangers au changement — `ProviderPluginPlatformContractTest` (deux méthodes, JAR de référence non empaqueté), `CoverageQualityGateTest` (aucun rapport JaCoCo sans `verify`) et `M19PerformanceGate` (budget de performance, hors sélection Surefire, retenu par le motif `-Dtest`). Reste à conclure par `./mvnw clean verify`.
- [x] 4.4 `archgate check` — vérification : aucune violation
  - `archgate check` : `pass: true`, aucune violation, aucun avertissement.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
