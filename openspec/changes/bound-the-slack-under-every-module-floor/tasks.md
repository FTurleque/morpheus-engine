# Tâches : borner le mou sous chaque seuil de couverture d'un module

## 1. Décision

- [x] 1.1 Écrire `GEN-0167`, ADR consolidé qui reprend `GEN-0166` (archivé) avec la borne basse dans sa décision 5,
  accepté par le mainteneur — exigence : `per-module-coverage-floor` › Le seuil d'un module reste dans sa fenêtre
  qualifiée ; vérification : `archgate check`, `AdrIndexCoherenceTest`
  - Acceptation du mainteneur le 12/11/2026 (« Clés + borne + GEN-0167 »). `archgate check` : 6/6, aucun
    `briefingWarnings` pour GEN-0167 (Decision 1 769 caractères).
- [x] 1.2 Archiver l'ADR remplacé (`git mv` vers `archive/0166-…`, bandeau, statut « Remplacée par ADR-0167 »), mettre
  à jour l'index et `09-decisions.md`, repointer chaque lien vers son ancien chemin — vérification :
  `RepositoryDocumentationCoherenceTest`
  - Liens repointés dans les ADR 0089 et 0150, l'archive 0163, `.claude/rules/testing.md`, la skill `coverage-ratchet`,
    le fichier de seuils, les deux gates m21, `BUILD_AND_TEST.md` et quatre pages de `docs/developer/specifications/`.

## 2. Borne basse

- [x] 2.1 Écrire les tests sur résumés synthétiques (à écrire) : un seuil qui laisse plus de neuf dispersions est
  refusé et le refus nomme le plus petit seuil admis ; un seuil au plancher D2 est refusé quand la bande est au-dessus
  et admis quand la fenêtre est vide — même exigence ; vérification : rouges avant 2.2
  - `aFloorAbandonedFarUnderItsMeasurementIsRefusedAndNamesTheLowestAdmittedFloor`,
    `aFloorAtTheD2FloorIsRefusedWhenTheBandLiesAboveItAndAdmittedWhenTheWindowIsEmpty` ; rouges avant 2.2
    (« expected: <1> but was: <0> »).
- [x] 2.2 Ajouter la borne basse à `CoverageQualificationEvidenceTest#floorWindows` et `#windowProblem`, plancher D2
  compris pour un module que la preuve porte — même exigence ; vérification : tests de 2.1 verts
  - `MAXIMUM_HEADROOM_SPREADS = 9L` ; la marge des ratchets d'échelle (`margins`) n'est pas touchée.
- [x] 2.3 Adapter les tests de fenêtre existants dont un seuil de module tombait hors de la bande — même exigence ;
  vérification : suite `CoverageQualificationEvidenceTest` verte
  - Le double `floors` ne pose plus `morpheus-domain` au plancher ; les seuils de branches de `morpheus-cli` à 0,35
    passent à 0,748 et un seuil de lignes à 0,40 passe à 0,498, dans la bande de leur preuve synthétique.

## 3. Seuils

- [x] 3.1 Relever les vingt-trois clés au plus petit millième de leur bande, relu sur la preuve conservée — même
  exigence ; vérification : `everyModuleFloorStaysInsideItsQualifiedWindow` vert
  - Valeurs prises dans les refus du gate (`raise it to at least …`), toutes en hausse ; les neuf autres clés sont
    déjà dans leur bande, ou au plancher avec une fenêtre vide (branches de `morpheus-provider-reference`).
- [x] 3.2 Planter la violation : `morpheus-integration-nexus.lineCoverageMinimum` remis à 0,473, constater le refus
  qui nomme le plus petit seuil admis, revenir en arrière ; consigner ici et dans la PR — vérification : trace consignée
  - Refus obtenu avec la valeur d'origine : « morpheus-integration-nexus.lineCoverageMinimum=0.473 leaves 72 covered
    line units under the lowest kept measurement of morpheus-integration-nexus (154 of 173); a floor leaves at most 9,
    9x the reference spread of 1, … -- raise it to at least 0.839 ».
- [ ] 3.3 Prouver que les seuils relevés tiennent sur un réacteur complet — exigence : `per-module-coverage-floor` ›
  Chaque module de la population est tenu par un seuil qui lui est propre ; vérification : `CoverageQualityGateTest`
  vert sur la CI Linux et Windows de la PR
  - Contre-mesure faite : l'artefact `m21-integrity-Windows` du run `43270298003` (`12cd8874`) met chacun des
    vingt-trois modules au-dessus de son nouveau seuil. La CI des deux plateformes de la PR reste à constater.

## 4. Documentation

- [x] 4.1 Mettre à jour `.claude/rules/testing.md` (puce **Fenêtre**), la skill `coverage-ratchet`, l'en-tête de
  `config/m21-per-module-coverage-floors.properties` et `docs/developer/specifications/per-module-coverage-floor.md`
  — vérification : pages relues, `GovernanceSurfaceCoherenceTest` vert

## 5. Vérification d'ensemble

- [x] 5.1 Lancer `openspec validate bound-the-slack-under-every-module-floor --strict` — vérification : aucun
  avertissement
- [x] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve
- [x] 5.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verdicts vides
  - `./mvnw -o test -pl morpheus-architecture-tests -am` local sans `clean verify` préalable : `architectureViolations=0` ;
    la seule dérive documentaire (citation de l'ADR archivé dans ce changement) est corrigée et relancée verte.
- [ ] 5.4 Justifier dans la PR la modification du fichier de seuils et du test d'architecture — vérification : section
  de gouvernance remplie

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Noter `AUD-TST-26` comme corrigé à la prochaine revérification de l'audit.
