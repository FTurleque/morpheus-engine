# Tâches : un seuil de non-régression de couverture pour chaque module

## 1. Décision d'architecture

- [ ] 1.1 Rédiger l'ADR consolidé qui remplace GEN-0154 (décision 1 étendue aux seuils par module, décisions 2 à 6 reprises, Do's and Don'ts et Compliance mis à jour), numéro relu dans le registre et l'archive, statut `Proposée` ; ajouter sa ligne à `docs/architecture/adr/README.md` et à `docs/architecture/arc42/09-decisions.md` — exigence : `per-module-coverage-floor` › Les seuils par module sont déclarés une fois, par module, dans la configuration de l'échelle par module ; vérification : `AdrIndexCoherenceTest` vert, `archgate check` sans violation
- [ ] 1.2 Faire accepter l'ADR par le mainteneur, passer GEN-0154 en `Remplacée par ADR-NNNN` et l'archiver selon `.claude/rules/documentation.md` (`git mv`, chemins cherchés dans tout le dépôt) — vérification : `AdrIndexCoherenceTest` et `RepositoryDocumentationCoherenceTest` verts

## 2. Contrôle par module et séparation des échelles

- [ ] 2.1 Écrire, avant le contrôle, le test sur réacteur assemblé dans un `@TempDir` : un module sous son seuil pendant que le total tient, un cas branches, un module sans clé, une clé orpheline, un module sans classe principale — exigences : `per-module-coverage-floor` › Chaque module de la population est tenu par un seuil qui lui est propre ; › Les seuils par module sont déclarés une fois, par module, dans la configuration de l'échelle par module ; vérification : le test échoue avant 2.2 (à écrire)
- [ ] 2.2 Garder des compteurs par module dans `CoverageQualityGateTest`, ajouter `everyModuleOfThePopulationHoldsItsOwnFloor` (refus groupé qui nomme module, grandeur, ratio, seuil et le remède « écrire des tests dans ce module »), écrire une ligne de compteurs par module dans `m21-per-module-coverage-summary.txt`, et réécrire la javadoc de la classe — exigences : idem et › Le seuil d'un module ne se baisse pas pour faire passer un build ; vérification : les tests de 2.1 passent
- [ ] 2.3 Créer `config/m21-per-module-coverage-floors.properties` avec les deux clés de chaque module de la population, toutes au plancher D2 — exigence : `per-module-coverage-floor` › Un module absent de la preuve conservée ne déclare que le plancher D2 ; vérification : `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest` vert
- [ ] 2.4 Casser le contrôle une fois : retirer la clé d'un module, constater l'échec qui le nomme ; poser un seuil de module au-dessus de sa mesure, constater l'échec ; revenir en arrière — vérification : deux échecs constatés et notés sous la tâche
- [ ] 2.5 Étendre `CoverageScaleSeparationTest` : le gate par module lit le fichier de seuils, le gate agrégé et `BorrowedCoverageGateTest` ne le nomment pas ; planter une fois la mention du fichier dans `AggregateCoverageGateTest`, constater l'échec, revenir en arrière — exigence : `per-module-coverage-floor` › Les seuils par module ne gouvernent ni l'échelle agrégée ni le crédit emprunté ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageScaleSeparationTest` vert après retour arrière
- [ ] 2.6 Vérifier que le contrôle total est inchangé — exigence : `per-module-coverage-floor` › Le cliquet total de l'échelle par module reste en vigueur à côté des seuils ; vérification : `CoverageQualityGateTest#ratchetRejectsARegressionThatTheOldD2FloorWouldHaveAccepted` et `#perModuleRatchetWindowRejectsAnUnqualifiedRaiseAndAReturnToTheD2Floor` verts

## 3. Preuve par module et qualification des seuils

- [ ] 3.1 Écrire, avant le contrôle, les tests de `CoverageQualificationEvidenceTest` sur résumés synthétiques : module manquant, sommes fausses, ensembles de modules différents, dispersion observée plus large que la référence, module stable, seuil au-dessus de la fenêtre, seuil sous le plancher D2, nouveau module au-dessus du plancher — exigences : `coverage-qualification-evidence` › La preuve de l'échelle par module porte les compteurs de chaque module ; › La dispersion d'un module se lit dans la preuve conservée ; `per-module-coverage-floor` › Le seuil d'un module reste dans sa fenêtre qualifiée ; › Un module absent de la preuve conservée ne déclare que le plancher D2 ; vérification : rouges avant 3.2 (à écrire)
- [ ] 3.2 Implémenter ces contrôles dans `CoverageQualificationEvidenceTest` (lignes `module.<artifactId>.*`, fenêtre [plancher D2, plus basse mesure − 3 × max(écart observé, `REFERENCE_*_SPREAD`)]) — vérification : tests de 3.1 verts ; sur la preuve conservée actuelle, sans lignes par module, tout seuil au plancher D2 passe
- [ ] 3.3 Trancher la voie d'amorçage avec le mainteneur (`design.md`, D5) — vérification : décision notée sous la tâche
- [ ] 3.4 Requalifier les deux échelles à une seule tête selon la skill `coverage-ratchet` : deux runs par plateforme, résumés gardés tels quels sous `config/coverage-qualification/`, `evidence.tsv` remplacé, quatre plafonds alignés sur la plus basse mesure — exigence : `coverage-qualification-evidence` › La preuve de l'échelle par module porte les compteurs de chaque module ; vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualificationEvidenceTest` vert ; un plafond qui baisse est signalé dans la PR, jamais compensé en baissant un cliquet
- [ ] 3.5 Poser chaque seuil de module au plus haut millième de sa fenêtre, valeur lue dans la preuve conservée, lignes et branches séparément — exigence : `per-module-coverage-floor` › Le seuil d'un module reste dans sa fenêtre qualifiée ; vérification : `CoverageQualificationEvidenceTest` et `CoverageQualityGateTest` verts après `./mvnw clean verify`

## 4. Documentation

- [ ] 4.1 Créer `docs/developer/specifications/per-module-coverage-floor.md` selon le gabarit (diagrammes de séquence et d'états de `design.md`, traçabilité), l'ajouter à `docs/developer/specifications/README.md`, et transformer le chemin en code du `## Purpose` de la spec delta en lien `/docs/developer/specifications/per-module-coverage-floor.md` — vérification : `RepositoryDocumentationCoherenceTest` vert
- [ ] 4.2 Mettre à jour `docs/developer/specifications/coverage-qualification-evidence.md` (exigences ajoutées, traçabilité) — vérification : liens contrôlés par `RepositoryDocumentationCoherenceTest`
- [ ] 4.3 Décrire le seuil par module dans `docs/developer/BUILD_AND_TEST.md`, `.claude/rules/testing.md` et `.claude/skills/coverage-ratchet/SKILL.md`, sans recopier de valeur — vérification : `RepositoryDocumentationCoherenceTest` et `ProductionIntegrityContractTest` verts

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate thresh-every-module-coverage-on-its-own-floor --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → conception ou diagramme → tâche → preuve) — vérification : chaque exigence a une preuve existante ou est marquée « preuve manquante »
- [ ] 5.3 Lancer `./mvnw clean verify`, puis `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest,CoverageQualificationEvidenceTest,CoverageScaleSeparationTest,AdrIndexCoherenceTest,RepositoryDocumentationCoherenceTest` — vérification : verdict vert sur Linux et Windows (CI)
- [ ] 5.4 Lancer `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
