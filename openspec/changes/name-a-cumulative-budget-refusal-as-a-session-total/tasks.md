# Tâches : dire qu'un refus de budget cumulatif porte sur toute la lecture

## 1. Reproduction

- [ ] 1.1 Écrire `OpenSpecRefusedFileNameTest#anEvidenceTotalReachedOnASmallFileDoesNotAccuseIt` : un workspace
  temporaire dont l'évidence ne dépasse le budget qu'en somme, sur un dernier fichier plus petit que le budget (à
  écrire) — exigence : `provider-ingestion-budget` › A cumulative budget refusal names the whole read, not the file
  being read ; vérification : rouge avant 2.1 (le message dit `exceeds budget for <fichier>`)
- [ ] 1.2 Écrire `ProviderIngestionBudgetTest#everyCumulativeQuantityIsRefusedForTheWholeRead` (six grandeurs) et
  `ProviderIngestionBudgetPrecisionTest#aBoundedReadThatStopsEarlySaysMoreThanTheMaximum` (à écrire) — même exigence ;
  vérification : rouges avant 2.1

## 2. Correctif

- [ ] 2.1 Donner à `exceeded` la portée de la grandeur, écrire les deux formes de D1 et le « more than » de D2, libeller
  `document line count` la vérification par document — même exigence ; vérification : tests de 1.1 et 1.2 verts
- [ ] 2.2 Ajuster les tests existants qui lisent le texte d'un refus (`ProviderIngestionBudgetTest`,
  `ProviderIngestionBudgetPrecisionTest`, `OpenSpecReadChargesEachFileOnceTest`, `OpenSpecRefusedFileNameTest`,
  `StructuredMarkdownSpecificationContentReaderTest`) sans en affaiblir aucun — vérification : chaque assertion garde
  la grandeur, la valeur et le maximum

## 3. Documentation durable

- [ ] 3.1 Écrire `docs/developer/specifications/provider-ingestion-budget.md` et l'indexer — vérification :
  `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate name-a-cumulative-budget-refusal-as-a-session-total --strict` — vérification :
  aucun avertissement
- [ ] 4.2 Lancer `./mvnw test -pl morpheus-application,morpheus-provider-openspec,morpheus-provider-markdown -am`, puis
  `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verts

## Workflow follow-up

- Archiver ce changement **avant** `qualify-the-evidence-budget-on-real-corpora`, qui ajoute ses exigences à la
  capacité `provider-ingestion-budget` créée ici.
