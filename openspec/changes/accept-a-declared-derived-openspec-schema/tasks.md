# Tâches : accepter un schéma OpenSpec qui déclare dériver de `spec-driven`

## 1. Décision et reproduction

- [x] 1.1 Rédiger l'ADR (D1, D2, D3) au statut « Proposée », numéro après relecture du registre et de l'archive, et
  l'indexer (ADR-0169, consolidée : elle remplace ADR-0002 (archivé), dont elle reprend toute la décision) —
  exigence : `openspec-provider-reading` › A project schema is read only when it declares that it derives from
  spec-driven ; vérification : `archgate check`, `AdrIndexCoherenceTest`
- [x] 1.2 Écrire les cinq tests de la stratégie de vérification dans `OpenSpecSpecificationProviderTest`, sur une copie
  temporaire d'`openspec-basic` dont `config.yaml` et `openspec/schemas/` sont réécrits (à écrire) — même exigence ;
  vérification : le test du schéma dérivé échoue avant 2.1, les quatre refus passent déjà ou échouent sur le motif

## 2. Correctif

- [x] 2.1 Lire `openspec/schemas/<schema>/schema.yaml` dans le probe, valider le nom de schéma comme segment unique,
  extraire `derives` et rendre les refus nommés — même exigence ; vérification : les cinq tests de 1.2 verts,
  `./mvnw test -pl morpheus-provider-openspec`
- [x] 2.2 Ajouter `derives: spec-driven` à `openspec/schemas/detailed-uml/schema.yaml` — vérification :
  `openspec schema validate detailed-uml`, `openspec validate --all --strict`
- [x] 2.3 Passer l'ADR « Acceptée », par le mainteneur, avant la fusion — vérification : statut de l'ADR et de sa ligne
  d'index (acceptée par le mainteneur le 16/11/2026, déclaration dans `schema.yaml`)

## 3. Documentation durable

- [x] 3.1 Mettre à jour `docs/developer/specifications/openspec-provider-reading.md` (R5, organigramme, traçabilité) —
  vérification : `RepositoryDocumentationCoherenceTest`, diagramme rendu
- [x] 3.2 Dire dans le guide utilisateur quels schémas sont lus et comment un schéma dérivé se déclare — vérification :
  `UserDocumentationSpeaksOfTheInstalledProductTest`

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate accept-a-declared-derived-openspec-schema --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Ingérer une copie jetable de `nexus-context-engine/openspec` **sans** réécrire son schéma, une fois
  `derives: spec-driven` ajouté à sa copie de `schema.yaml` — vérification : `published: true`, 0 diagnostic (relevé
  manuel, dans la PR)
- [x] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verts
  (`archgate check` : 10 règles sur 10 ; le module est vert hors des deux méthodes de `CoverageQualityGateTest`, qui
  refusent de conclure sans les rapports JaCoCo d'un `./mvnw clean verify` complet, à rejouer à l'intégration)

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Proposer `derives: spec-driven` aux dépôts `nexus-context-engine` et `minos-code-intelligence`, chacun par sa PR.
