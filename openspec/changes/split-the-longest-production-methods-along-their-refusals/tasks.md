# Tâches : découper les plus longues méthodes de production selon leurs refus (AUD-QUA-35)

## 1. Préalable

- [ ] 1.1 Vérifier que `serve-the-whole-cli-help-from-the-help-branch` (constat `AUD-ARC-31`) est appliqué et que
  `MorpheusMain.run` ne porte plus l'aide — vérification : aucun `println` dans `MorpheusMain`

## 2. Scan d'inventaire

- [ ] 2.1 Écrire `LocalSourceInventoryScanBoundaryRefusalTest` (à écrire, `morpheus-application`) : pour chacun des
  quatre sites du refus de frontière (racine, répertoire, fichier avant lecture, fichier pendant l'empreinte), le code
  `WORKSPACE_BOUNDARY_ESCAPED`, le chemin affiché, le texte et l'épuisement du budget (aucune entrée lue après) —
  exigence (inchangée) : `workspace-boundary-scan-cost` › A source scan SHALL refuse every path that leaves the
  workspace physically ; vérification : vert avant 2.2 (il fige l'existant)
- [ ] 2.2 Extraire le visiteur en classe imbriquée nommée avec `boundaryEscaped` et `depthExceeded` (D1) —
  vérification : 2.1 et les tests du scan du Contexte de design.md verts sans modification,
  `ApplicationTechnologyBoundaryArchitectureTest` vert sans nom ajouté
- [ ] 2.3 Relire `docs/developer/specifications/workspace-boundary-scan-cost.md` et y remplacer toute mention du
  visiteur anonyme — vérification : `RepositoryDocumentationCoherenceTest`

## 3. Provider synthétique et complétude

- [ ] 3.1 Découper `SyntheticSpecificationContentReader.normalize` par catégorie (D2) — vérification :
  `SyntheticSpecificationContentReaderTest` et `SyntheticSpecificationProviderTest` verts sans modification
- [ ] 3.2 Découper `ChangeCompletenessService.assessChange` par volet (D2) — vérification : `QualityReportServiceTest`,
  `QualityThresholdFactTest` et les contrats de complétude du module d'architecture verts sans modification

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate split-the-longest-production-methods-along-their-refusals --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Relancer le scan des méthodes de plus de 60 lignes — vérification : `scanRoot`, `normalize` et `assessChange`
  n'y figurent plus ; le résultat est consigné dans la PR comme mesure datée
- [ ] 4.3 Lancer `./mvnw clean verify` — vérification : vert, `PublicPayloadConvergenceTest` et gate de couverture des
  lignes modifiées compris

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
