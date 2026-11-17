# Tâches : conserver la preuve du crédit emprunté

## 1. Décision du mainteneur

- [ ] 1.1 Avant le 21/11/2026, télécharger `m21-borrowed-coverage-summary.txt` des artefacts `m21-integrity-Linux` et
  `m21-integrity-Windows` des runs `42306627340` et `42311778667` (`gh run download <run> -n m21-integrity-<OS>`, avec
  l'accord du mainteneur) — vérification : quatre fichiers, première ligne `coverageScope=borrowed-credit`,
  `populationModules=16` ; sinon, faire trancher Q2 de design.md
- [ ] 1.2 Faire trancher Q1 de design.md (maximum) — vérification : décision consignée dans la PR

## 2. Preuves et comparaison

- [ ] 2.1 Écrire dans `CoverageQualificationEvidenceTest` la méthode
  `theBorrowedCreditIsQualifiedOnKeptEvidenceAtTheHeadOfBothScales` (à écrire) et ses cas fabriqués (autre tête, autre
  portée, preuve incomplète, constante sous le plus haut, constante sur le plus bas) ; fichier de gouvernance,
  justification de PR — exigences : `coverage-attribution-gap` › The evidence of the qualified borrowed measurement
  SHALL be kept in the repository at the head of the coverage qualification, et › The qualified borrowed measurement
  SHALL equal the highest measurement of the kept evidence ; vérification : cas fabriqués refusés avec leurs noms ;
  méthode réelle rouge tant que 2.2 n'est pas fait
- [ ] 2.2 Recopier les quatre résumés tels quels sous `config/coverage-qualification/borrowed/` et les déclarer dans
  `evidence.tsv` (D1 ; fichier de gouvernance) — vérification : méthode réelle de 2.1 rouge sur l'écart 801/802
- [ ] 2.3 Requalifier `QUALIFIED_BORROWED_LINES`, `QUALIFIED_BORROWED_BRANCHES` et `QUALIFIED_BY_MODULE` de
  `BorrowedCoverageGateTest` sur le plus haut des résumés, commentaire réécrit sur les preuves conservées (D2, D3) ;
  appliquer Q1 à `config/m21-quality-ratchets.properties` seulement si une baisse est décidée — vérification : 2.1 vert,
  `CoverageScaleSeparationTest` vert, `BorrowedCoverageGateTest` vert sous `./mvnw verify`
- [ ] 2.4 Mettre à jour `docs/developer/specifications/coverage-attribution-gap.md` (exigences, diagramme, traçabilité)
  et `docs/developer/specifications/coverage-qualification-evidence.md`, puis signaler au mainteneur le paragraphe du
  crédit emprunté de `.claude/rules/testing.md` — vérification : `RepositoryDocumentationCoherenceTest` et
  `GovernanceSurfaceCoherenceTest` verts

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate keep-the-evidence-of-the-borrowed-credit --strict` — vérification : aucun
  avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a une preuve
  existante ou « preuve manquante »
- [ ] 3.3 Lancer `./mvnw clean verify` puis `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualificationEvidenceTest+CoverageScaleSeparationTest`
  — vérification : verts, aucun ratchet ni plafond des deux échelles modifié

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et les deux pages durables.
