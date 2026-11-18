# Tâches : lire les deltas OpenSpec à travers les blocs de code, en temps linéaire

## 1. Clôtures de code (AUD-QUA-29)

- [ ] 1.1 Réécrire l'attente de `OpenSpecRequirementDeltaReaderTest#aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns` (énoncé avec le bloc entier) et écrire `aRequirementHeadingInsideACodeFenceOfADeltaSectionIsNotADelta` — exigence : `openspec-provider-reading` › A requirement delta follows the same Markdown boundaries as a current requirement ; vérification : les deux tests échouent avant 1.4
- [ ] 1.2 Écrire `OpenSpecDeltaAndCurrentStatementParityTest` (même texte lu par les deux lecteurs) — exigence : idem ; vérification : rouge avant 1.4
- [ ] 1.3 Écrire le cas « scénario en exemple dans un bloc de code » dans `OpenSpecCurrentSpecificationReaderTest` et `OpenSpecRequirementDeltaReaderTest` — exigence : `openspec-provider-reading` › A scenario heading inside a code fence does not start a scenario ; vérification : rouges avant 1.4
- [ ] 1.4 Passer `OpenSpecCodeFences` à `requirementEnd` du lecteur de deltas, tester la clôture avant l'en-tête d'exigence et dans les quatre recherches de scénario (design.md, D1) — vérification : tests de 1.1 à 1.3 verts, autres tests du module inchangés

## 2. Coût linéaire des motifs (AUD-SEC-20)

- [ ] 2.1 Écrire `OpenSpecLineReadingCostTest` : une ligne `### Requirement: a` + espaces + `b` à deux longueurs, pour chacun des trois lecteurs, et le rapport de temps attendu (design.md, Performance) — exigence : `openspec-provider-reading` › Reading a line costs time linear in its length ; vérification : rouge avant 2.2
- [ ] 2.2 Réécrire les neuf motifs sans capture paresseuse suivie de `\s*$` et rogner la capture dans le code (design.md, D2) — vérification : 2.1 vert
- [ ] 2.3 Ajouter un cas de titre suivi d'espaces par motif (titre, scénario, étape, décision, tâche, titre de proposition) — exigence : idem, scénario « A title with trailing spaces keeps its identity » ; vérification : verts, identités inchangées dans les tests existants

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/openspec-provider-reading.md` (exigences, frontières d'un delta, coût d'une ligne, écart `OpenSpecChangeMetadataReader.headingIndexes`, traçabilité) — vérification : `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate read-openspec-deltas-through-code-fences-in-linear-time --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1/D2 → tâche → test) — vérification : chaque exigence a une preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-provider-openspec` puis `./mvnw clean verify` — vérification : verts, aucun ratchet touché

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Ce changement débloque `AUD-QUA-34` (sprint 8), qui mutualise `requirementEnd` entre les lecteurs.
