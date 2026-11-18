# Tâches : tester le store SQLite dans son propre module, puis relever son seuil (AUD-TST-31)

## 1. Décision du mainteneur

- [ ] 1.1 Trancher où se fait la requalification (proposition, Questions ouvertes ; design.md, D3) : dans ce
  changement (groupe 4) ou dans un changement de requalification séparé — vérification : décision écrite dans la PR ;
  si séparée, le groupe 4 est retiré de cette liste avant l'implémentation.

## 2. Mesure de départ

- [ ] 2.1 Après un `./mvnw clean verify` complet, relire le rapport propre
  `morpheus-store-sqlite/target/site/jacoco/jacoco.xml` et dresser, par fichier, lignes et branches manquées (design.md,
  Contexte) ; noter les classes qui portent le crédit emprunté du module dans
  `morpheus-coverage-report/target/m21-borrowed-coverage-summary.txt` — exigence : aucune modifiée (constat
  `AUD-TST-31`) ; vérification : tableau daté joint à la PR.

## 3. Tests dans le module

- [ ] 3.1 Écrire les tests de `SqliteExternalReferenceStore` (à écrire) : résolution, historique, attributs, refus d'un
  argument, effet persisté relu sur une base temporaire (D1, D2) — vérification : rapport propre relu, lignes manquées
  du fichier en baisse ; chaque refus asserte son message (`RefusalTestEvidenceArchitectureTest`).
- [ ] 3.2 Écrire les tests des chemins d'échec et de conflit des stores à CAS : `SqliteVersionedRequirementStore`,
  `SqliteChangeLifecycleMutationStore`, `SqliteSavedViewStore`, `SqliteCompositionStateStore` (à écrire) : révision
  attendue périmée, entité absente, écrivain concurrent, avec l'état laissé — vérification : rapport propre relu ; les
  `*PersistenceParityTest` existants (module d'architecture) restent verts et ne reçoivent aucun cas nouveau : la
  preuve propre du store est écrite dans son module.
- [ ] 3.3 Poursuivre dans l'ordre du tableau de 2.1 tant que la marge des branches reste la plus serrée — vérification :
  `./mvnw test -pl morpheus-store-sqlite` vert ; aucun test ajouté sous `morpheus-architecture-tests` (il ferait monter
  le crédit emprunté).
- [ ] 3.4 Si un test révèle un défaut du store, ne pas le corriger ici : ouvrir un changement dédié avec son test de
  reproduction — vérification : aucune source principale modifiée par ce changement (`git diff --stat`).

## 4. Requalification (selon 1.1)

- [ ] 4.1 Mesurer à une même tête sur les runners Linux et Windows (deux runs), garder les résumés des deux échelles et
  du crédit emprunté dans `config/coverage-qualification/` à cette tête, comme l'exige
  `CoverageQualificationEvidenceTest` — exigence : `per-module-coverage-floor` › Le seuil d'un module reste dans sa
  fenêtre qualifiée (inchangée) ; vérification : `evidence.tsv` et résumés cohérents, test vert. Une session à une
  seule plateforme ne touche à aucun plafond (`.claude/rules/testing.md`).
- [ ] 4.2 Relever `morpheus-store-sqlite.lineCoverageMinimum` et `.branchCoverageMinimum` dans
  `config/m21-per-module-coverage-floors.properties` à la valeur que nomme le gate (D3), au-dessus de la mesure du
  17/11 — vérification : `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualificationEvidenceTest,CoverageQualityGateTest`
  vert. Fichier de gouvernance : justification dans la PR.
- [ ] 4.3 Si la mesure le permet, baisser `borrowedLineCreditMaximum` / `borrowedBranchCreditMaximum` de
  `config/m21-quality-ratchets.properties` et les constantes qualifiées de `BorrowedCoverageGateTest` — exigence :
  `coverage-attribution-gap` › The total borrowed credit SHALL NOT exceed a recorded maximum that is only lowered ;
  vérification : `./mvnw verify` (gate du crédit emprunté lié à `verify`) vert ; justification dans la PR.
- [ ] 4.4 Répercuter toute valeur qui a bougé dans les destinations que les gates de cohérence énumèrent
  (`.claude/rules/meta.md`) — vérification : `RepositoryDocumentationCoherenceTest`, `ProductionIntegrityContractTest`,
  `GovernanceSurfaceCoherenceTest` verts.

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate test-the-sqlite-store-in-its-own-module --strict` — vérification : aucun
  avertissement.
- [ ] 5.2 Matrice de design.md (Stratégie de vérification) complétée par les tests écrits et les mesures — vérification :
  chaque ligne nomme sa preuve.
- [ ] 5.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests` — vérification : 0 échec,
  `target/enforcement-verdict.txt` sans violation.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
