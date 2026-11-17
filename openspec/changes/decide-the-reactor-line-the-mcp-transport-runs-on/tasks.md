# Tâches : décider de la ligne Reactor sur laquelle tourne le transport MCP

## 1. Décision du mainteneur

- [ ] 1.1 Lire les sources du SDK MCP à la version de `mcp-sdk.version` (`mcp-core`, `mcp-json-jackson3`) et consigner
  dans design.md, rubrique Contexte, s'il appelle `bufferTimeout` ou `windowTimeout` avec `fairBackpressure` —
  vérification : recherche relevée avec la version lue et le résultat
- [ ] 1.2 Faire trancher D1 par le mainteneur (option A ou B) et l'inscrire dans design.md — vérification : D1 passe de
  [QUESTION] à [DÉCISION] avec la date et l'option
- [ ] 1.3 Si l'option B est retenue : réécrire le spec delta (exigence d'acceptation écrite des deux CVE à la place de
  la ligne qui change), puis remplacer les groupes 2 et 3 par l'entrée datée du registre des risques et son test —
  vérification : `openspec validate decide-the-reactor-line-the-mcp-transport-runs-on --strict` vert sur le delta réécrit

## 2. Ligne maintenue (option A)

- [ ] 2.1 Écrire dans `DependencyLineCurrencyTest` la méthode qui refuse une ligne non déclarée maintenue, et constater
  son échec sur `2024.0` avant tout changement du POM — exigence : `dependency-line-currency` › Reactor SHALL be on a
  patched release of a maintained line the MCP SDK is qualified on ; vérification : test à écrire, rouge avant 2.2
  (violation plantée consignée dans la Javadoc de la méthode). Fichier de gouvernance : justification dans la PR
- [ ] 2.2 Changer `reactor-bom.version` vers une version 2025.0.x dont le `reactor-core` est hors des plages des deux
  avis, réécrire le commentaire de l'import (ligne qualifiée, et non ligne de construction du SDK) et la constante de
  ligne du test (D2) — vérification : tests de 2.1 verts, `./mvnw dependency:tree -pl morpheus-mcp-transport` relu
- [ ] 2.3 Lancer les tests réels du transport et des pairs sans les modifier — exigence : même exigence, scénario « The
  transport still works on it » ; vérification : `./mvnw test -pl morpheus-mcp-transport,morpheus-mcp,morpheus-cli -am`
  vert
- [ ] 2.4 Lancer `./mvnw dependency:analyze` et le scan `-Pd2-security` — vérification : zéro avertissement, aucune
  suppression ajoutée pour les deux CVE

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/dependency-line-currency.md` (valeur tenue, ligne déclarée,
  diagramme, traçabilité) et la table des versions de `.claude/rules/build.md` — vérification :
  `RepositoryDocumentationCoherenceTest`, `GovernanceSurfaceCoherenceTest`
- [ ] 3.2 Clarifier la conséquence d'ARCH-0062 sur la ligne Reactor, ou proposer l'ADR consolidé si le mainteneur le
  demande (D1) — vérification : `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate decide-the-reactor-line-the-mcp-transport-runs-on --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de la page durable (exigence → scénario → D1/D2 → tâche → preuve) —
  vérification : chaque scénario a sa preuve ou est marqué « preuve manquante »
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=DependencyLineCurrencyTest` puis `./mvnw clean
  verify` — vérification : verts

## Workflow follow-up

- Le change `tie-the-mcp-sdk-version-to-the-reactor-line-it-is-qualified-on` (AUD-DEP-31) s'applique après celui-ci, sur
  la ligne retenue.
- Archiver le changement après la revue, sur demande explicite.
