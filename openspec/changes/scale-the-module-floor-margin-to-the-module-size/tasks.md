# Tâches : proportionner à la taille du module la marge de son seuil de couverture

> Préalable : la décision D1 de `design.md` et l'ADR consolidé qui la porte sont acceptés par le mainteneur. Sans cette
> acceptation, aucune tâche ci-dessous n'est commencée.

## 1. Décision

- [ ] 1.1 Écrire l'ADR consolidé qui remplace `GEN-0163` (gabarit `docs/architecture/adr/gabarit/template.md`, numéro
  relu dans le registre et l'archive), au statut `Proposée`, et le faire accepter — exigence :
  `coverage-qualification-evidence` › La dispersion d'un module se lit dans la preuve conservée ; vérification :
  statut `Acceptée` posé par le mainteneur
- [ ] 1.2 Archiver `GEN-0163` (`git mv`, bandeau « Remplacée par »), mettre à jour l'index du registre et
  `docs/architecture/arc42/09-decisions.md`, chercher l'ancien chemin dans tout le dépôt — vérification :
  `AdrIndexCoherenceTest`, `RepositoryDocumentationCoherenceTest`, `archgate check` verts

## 2. Fenêtre proportionnelle

- [ ] 2.1 Écrire le test sur résumés synthétiques (à écrire) : un module stable de 63 lignes dans une population de
  29 900 a une marge de 3 lignes, et un seuil au-dessus de sa borne est refusé avec la dispersion proportionnelle et
  la part du module dans le message — même exigence ; vérification : échoue avant 2.2
- [ ] 2.2 Calculer la dispersion de référence d'un module en proportion de sa part dans
  `CoverageQualificationEvidenceTest`, sans toucher à la marge des ratchets d'échelle — même exigence ; vérification :
  test de 2.1 vert, `everyCoverageRatchetKeepsThreeTimesTheSpreadUnderTheLowestKeptMeasurement` vert et inchangé
- [ ] 2.3 Adapter le test existant de la fenêtre (module plus instable que la référence) à la référence proportionnelle
  — même exigence ; vérification : test vert

## 3. Seuils

- [ ] 3.1 Relire les bornes hautes sur la preuve conservée au moment d'appliquer, puis poser chacune des cinq clés
  dont la fenêtre s'ouvre au plus haut millième de sa fenêtre (`coverage-ratchet`, étape 5) ; laisser
  `morpheus-provider-reference.branchCoverageMinimum` au plancher — exigence : `per-module-coverage-floor` › Le seuil
  d'un module reste dans sa fenêtre qualifiée ; vérification : `everyModuleFloorStaysInsideItsQualifiedWindow` vert
- [ ] 3.2 Planter la violation : une clé un millième au-dessus de sa borne, constater l'échec qui nomme module, clé,
  valeur, mesure et marge, revenir en arrière ; consigner ici et dans la PR — vérification : trace consignée
- [ ] 3.3 Vérifier qu'un `./mvnw clean verify` complet passe le gate par module avec les seuils relevés (machine du
  mainteneur, ou CI des deux plateformes) — exigence : `per-module-coverage-floor` › Chaque module de la population
  est tenu par un seuil qui lui est propre ; vérification : `CoverageQualityGateTest` vert sur Linux et Windows

## 4. Documentation

- [ ] 4.1 Mettre à jour `.claude/rules/testing.md` (puce **Fenêtre**), la skill `coverage-ratchet` si elle décrit la
  dispersion d'un module, `docs/developer/specifications/coverage-qualification-evidence.md` et
  `per-module-coverage-floor.md` — vérification : pages relues, `GovernanceSurfaceCoherenceTest` vert

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate scale-the-module-floor-margin-to-the-module-size --strict` — vérification : aucun
  avertissement
- [ ] 5.2 Compléter la matrice de traçabilité — vérification : chaque scénario a sa preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-architecture-tests` après un `clean verify` — vérification : verdicts vides
- [ ] 5.4 Justifier dans la PR la modification de `config/m21-per-module-coverage-floors.properties` et du test
  d'architecture — vérification : section de gouvernance remplie

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Requalification suivante : couvrir une branche de plus dans `morpheus-provider-reference` pour ouvrir sa fenêtre de
  branches, puis noter `AUD-TRV-02` comme corrigé.
