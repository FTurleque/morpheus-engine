# Tâches : servir toute l'aide de la CLI par la branche de l'aide (AUD-ARC-31)

## 1. Tests de reproduction

- [ ] 1.1 Étendre `MorpheusCliTest` (à écrire : cas `--json help` et appel sans commande, appelés sur `MorpheusCli`
  directement) pour exiger les sections du bloc aujourd'hui écrit par le lanceur (`MCP:`, `API local:`, serveur
  distant) — exigence : `adapter-capability-decomposition` › The embedded help of the CLI SHALL be written by the help
  branch alone, scénario « L'aide complète est imprimée par une seule classe » ; vérification : rouge avant 2.1
- [ ] 1.2 Étendre `MorpheusMainTest` sur `--data-dir data help` aux mêmes sections — exigence : idem, scénario « Une
  option globale devant la commande » ; vérification : vert avant et après 2.1 (non-régression)

## 2. Déplacement

- [ ] 2.1 Déplacer le bloc de `MorpheusMain.run` dans `MorpheusCli.printHelp` et supprimer `isHelpRequest` (D1) —
  exigence : idem ; vérification : 1.1 vert, `CliCommandTranscriptTest` vert sans modification de `help.txt`,
  `CliCommandPathsTest`, `MorpheusHelpInvocationsParseTest` et `CliOptionRefusalTableTest` verts
- [ ] 2.2 Ajouter l'assertion de D3 (le lanceur n'écrit rien sur un flux), avec un message qui nomme le remède et la
  trace de sa violation plantée dans la Javadoc — exigence : idem, scénario « Le lanceur écrit une ligne d'aide » ;
  vérification : échoue sur une ligne `out.println` réintroduite dans `MorpheusMain`, puis passe ; justification de PR
  (test d'architecture modifié) et phrase d'intention dans la section « Intention de chaque assertion textuelle » du
  gabarit de PR
- [ ] 2.3 Vérifier que `DispatchersHoldNoUseCaseArchitectureTest#everyNameOfEachListIsStillUsed` reste vert avec
  `QueryBudgets` lu par `MorpheusCli` — vérification : vert

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/adapter-capability-decomposition.md` (exigence ajoutée, matrice
  de traçabilité) — vérification : `RepositoryDocumentationCoherenceTest`
- [ ] 3.2 Relire la section `morpheus-cli` et `morpheus-mcp` de `.claude/rules/architecture.md` et y citer l'assertion
  si elle s'ajoute aux contrôles nommés — vérification : `GovernanceSurfaceCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate serve-the-whole-cli-help-from-the-help-branch --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice de traçabilité (exigence → scénarios → D1 à D3 → tâches → tests) — vérification : chaque
  scénario a une preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-cli` puis `./mvnw test -pl morpheus-architecture-tests` — vérification :
  verts ; `architectureTestsMinimum` de `config/m21-quality-ratchets.properties` n'est pas baissé
- [ ] 4.4 Lancer `./mvnw clean verify` — vérification : vert

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Appliquer ensuite `split-the-longest-production-methods-along-their-refusals`, qui part de `MorpheusMain.run` réduit.
- Après l'archivage, relire la spec principale consolidée et la page durable.
