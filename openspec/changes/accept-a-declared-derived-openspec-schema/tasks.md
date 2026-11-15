# Tâches : accepter un schéma OpenSpec qui déclare dériver de `spec-driven`

## 1. Décision et reproduction

- [ ] 1.1 Rédiger l'ADR (D1, D2, D3) au statut « Proposée », numéro après relecture du registre et de l'archive, et
  l'indexer — exigence : `openspec-provider-reading` › A project schema is read only when it declares that it derives
  from spec-driven ; vérification : `archgate check`, `AdrIndexCoherenceTest`
- [ ] 1.2 Écrire les cinq tests de la stratégie de vérification dans `OpenSpecSpecificationProviderTest`, sur une copie
  temporaire d'`openspec-basic` dont `config.yaml` et `openspec/schemas/` sont réécrits (à écrire) — même exigence ;
  vérification : le test du schéma dérivé échoue avant 2.1, les quatre refus passent déjà ou échouent sur le motif

## 2. Correctif

- [ ] 2.1 Lire `openspec/schemas/<schema>/schema.yaml` dans le probe, valider le nom de schéma comme segment unique,
  extraire `derives` et rendre les refus nommés — même exigence ; vérification : les cinq tests de 1.2 verts,
  `./mvnw test -pl morpheus-provider-openspec`
- [ ] 2.2 Ajouter `derives: spec-driven` à `openspec/schemas/detailed-uml/schema.yaml` — vérification :
  `openspec schema validate detailed-uml`, `openspec validate --all --strict`
- [ ] 2.3 Passer l'ADR « Acceptée », par le mainteneur, avant la fusion — vérification : statut de l'ADR et de sa ligne
  d'index

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/openspec-provider-reading.md` (R5, organigramme, traçabilité) —
  vérification : `RepositoryDocumentationCoherenceTest`, diagramme rendu
- [ ] 3.2 Dire dans le guide utilisateur quels schémas sont lus et comment un schéma dérivé se déclare — vérification :
  `UserDocumentationSpeaksOfTheInstalledProductTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate accept-a-declared-derived-openspec-schema --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Ingérer une copie jetable de `nexus-context-engine/openspec` **sans** réécrire son schéma, une fois
  `derives: spec-driven` ajouté à sa copie de `schema.yaml` — vérification : `published: true`, 0 diagnostic (relevé
  manuel, dans la PR)
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Proposer `derives: spec-driven` aux dépôts `nexus-context-engine` et `minos-code-intelligence`, chacun par sa PR.
