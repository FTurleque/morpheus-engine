# Tâches : lier la version du SDK MCP à la ligne Reactor sur laquelle il est qualifié

## 1. Prérequis et décision du mainteneur

- [ ] 1.1 Vérifier que le change `decide-the-reactor-line-the-mcp-transport-runs-on` (`AUD-DEP-30`) est appliqué et
  relever la ligne Reactor retenue — vérification : `reactor-bom.version` et la constante de ligne de
  `DependencyLineCurrencyTest` relues
- [ ] 1.2 Faire trancher D2 (groupe Dependabot ou non) par le mainteneur — vérification : D2 passe à [DÉCISION] dans
  design.md

## 2. Gate SDK → ligne Reactor

- [ ] 2.1 Écrire dans `DependencyLineCurrencyTest` la méthode qui lit `mcp-sdk.version` et la compare à la table (D1),
  puis planter les deux violations (version absente, ligne différente) et constater l'échec — exigence :
  `dependency-line-currency` › Le build DOIT refuser une version du SDK MCP qu'aucune entrée ne lie à la ligne Reactor
  tenue ; vérification : test à écrire, rouge sur chaque violation plantée, vert sur l'arbre courant ; trace consignée
  dans la Javadoc. Fichier de gouvernance : justification dans la PR
- [ ] 2.2 Si D2 retient le groupe : ajouter le groupe à `.github/dependabot.yml` sans toucher à l'entrée `ignore` —
  vérification : `DependencyLineCurrencyTest#theUpdateBotProposesThePatchesOfTheHeldLineAndNothingElse` vert
- [ ] 2.3 Mettre à jour `docs/developer/specifications/dependency-line-currency.md` (exigence R3, comportement,
  traçabilité) — vérification : `RepositoryDocumentationCoherenceTest`

## 3. Vérification d'ensemble

- [ ] 3.1 Lancer `openspec validate tie-the-mcp-sdk-version-to-the-reactor-line-it-is-qualified-on --strict` —
  vérification : aucun avertissement
- [ ] 3.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque scénario a sa preuve
- [ ] 3.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=DependencyLineCurrencyTest` puis
  `./mvnw test -pl morpheus-architecture-tests` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
