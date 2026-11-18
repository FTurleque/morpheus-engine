# Conception : servir toute l'aide de la CLI par la branche de l'aide (AUD-ARC-31)

<!-- Conception allégée : déplacement de texte entre deux classes d'un même paquet, sortie inchangée. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `MorpheusMain.run` (`com.morpheus.cli`) choisit un gestionnaire dans sa table de capacités, avec
  `MorpheusCli` comme branche par défaut, puis, si le code de sortie est `0` et que `isHelpRequest(args)` reconnaît une
  demande d'aide, écrit 113 lignes par `out.println` (l. 229-343 sur `1a539c22`). Ce sont ses seuls appels d'écriture.
- [EXISTANT VÉRIFIÉ] `MorpheusCli.printHelp` écrit la première partie par `CliCommandRun.printText`, en texte même
  sous `--json` ; il est appelé pour `help`, `--help`, `-h` et pour un appel sans commande.
- [EXISTANT VÉRIFIÉ] `isHelpRequest` relit les options globales (`--json`, `--data-dir`, `--config-dir`, `--db`)
  indépendamment de `GlobalArgs`, qui les lit pour `CliCommandRun` : deux lectures du même jeton.
- [EXISTANT VÉRIFIÉ] Une ligne du bloc lit `QueryBudgets.MAX_EXPORT_ROWS`, constante inlinée que
  `DispatchersHoldNoUseCaseArchitectureTest` autorise aux classes de `com.morpheus.cli` (`CLI_ALLOWANCE`) ; `MorpheusCli`
  en est une, et `everyNameOfEachListIsStillUsed` reste satisfait.
- [EXISTANT VÉRIFIÉ] `CliCommandTranscriptTest` compare la sortie complète de `help` à
  `morpheus-cli/src/test/resources/com/morpheus/cli/transcripts/help.txt`, par `MorpheusMain.run` ;
  `CliCommandPathsTest#everyDeclaredPathIsInAUsageLineOfTheEmbeddedHelp` et `MorpheusHelpInvocationsParseTest` lisent la
  même aide.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `adapter-capability-decomposition` | The embedded help of the CLI SHALL be written by the help branch alone | D1, D2, D3 |

## Composants et frontières

`MorpheusCli` et `MorpheusMain`, tous deux dans `com.morpheus.cli` (ARCH-0149, décision 1). Aucune dépendance
nouvelle ; aucun paquet ne change.

Sans objet : modèle de domaine, états, persistance, transactions, sécurité (texte statique déjà public), performance.

## Décisions

### D1 : le texte déplacé suit le texte actuel de `MorpheusCli.printHelp`
- **Nature** : [DÉCISION]
- **Exigences servies** : `adapter-capability-decomposition` › The embedded help of the CLI SHALL be written by the help branch alone
- **Choix** : `printHelp` écrit son bloc de texte actuel puis le bloc déplacé, dans un seul bloc de texte ou deux
  écritures successives, en reproduisant la ligne vide qui ouvrait le bloc du lanceur. `isHelpRequest` est supprimé.
- **Alternatives écartées** : une ressource texte embarquée (la ligne qui imprime `QueryBudgets.MAX_EXPORT_ROWS` exige
  un calcul, et la lecture d'une ressource ajouterait un accès fichier au paquet de distribution, que
  `CliTechnologyBoundaryArchitectureTest` borne) ; garder `isHelpRequest` (deux lectures des options globales).
- **Compromis** : `MorpheusCli` grandit d'autant que `MorpheusMain` diminue.
- **ADR** : couvert par ARCH-0149 (décision 2), qu'il exécute.

### D2 : pas de répartition de l'aide générale entre classes de capacité
- **Nature** : [DÉCISION]
- **Exigences servies** : `adapter-capability-decomposition` › The embedded help of the CLI SHALL be written by the help branch alone
- **Choix** : l'aide générale reste un texte unique de la branche de l'aide.
- **Alternatives écartées** : faire déclarer à chaque classe de capacité ses lignes d'usage, assemblées par
  `MorpheusCli` (option de l'audit) : les sections MCP, API locale et serveur distant décrivent des modes que le
  lanceur démarre (`runMcp`, `runApi`, `runRemoteApi`), qu'aucune classe de capacité ne sert ; l'ordre des sections
  deviendrait celui de la table de distribution ; et `CliCommandPathsTest` tient déjà la couverture de chaque chemin.
- **Compromis** : une commande ajoutée écrit sa ligne dans `MorpheusCli`, loin de sa classe de capacité, mais dans la
  classe qui possède l'aide selon ARCH-0149.
- **ADR** : non concerné.

### D3 : l'assertion qui garde l'aide hors du lanceur
- **Nature** : [DÉCISION]
- **Exigences servies** : `adapter-capability-decomposition` › The embedded help of the CLI SHALL be written by the help branch alone
- **Choix** : une assertion textuelle de `DispatchersHoldNoUseCaseArchitectureTest` (ou d'une classe voisine) lit la
  source de `MorpheusMain`, sans commentaires, et refuse tout appel `print`, `println` ou `printf` sur un flux, avec un
  message qui nomme le remède. Intention : « le lanceur n'écrit rien sur la sortie standard ». Mécanisme textuel retenu
  parce qu'aucune dépendance compilée ne distingue une écriture d'aide d'une autre utilisation de `PrintStream`, que le
  lanceur reçoit et transmet légitimement.
- **Alternatives écartées** : une règle ArchUnit sur les appels de `PrintStream.println` (vraie aujourd'hui, mais le
  lanceur passe `out` aux serveurs et pourrait légitimement y écrire un jour un message de démarrage : l'interdit doit
  rester lisible et local) ; aucune assertion (la recopie reviendrait).
- **Compromis** : l'assertion vise une classe nommée ; un autre lanceur futur n'est pas couvert.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| « L'aide reste à `MorpheusCli` » | ARCH-0149, décision 2 ; `.claude/rules/architecture.md` (« `MorpheusCli` ne sert que l'aide ») | `MorpheusMain.run`, l. 229-343 | D1 |
| Deux lectures des options globales | `MorpheusMain.isHelpRequest` | `GlobalArgs` | supprimée avec D1 |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| The embedded help … help branch alone | L'aide complète est imprimée par une seule classe | `CliCommandTranscriptTest` (`help.txt` inchangé) ; `MorpheusCliTest` étendu à `--json help` et à l'appel sans commande | existant / à écrire |
| idem | Une option globale devant la commande | `MorpheusMainTest` (`--data-dir data help`, assertions étendues aux sections du bloc déplacé) | à écrire |
| idem | Le lanceur écrit une ligne d'aide | assertion de D3, avec sa violation plantée | à écrire |
| idem | Une demande d'aide refusée | `CliOptionRefusalTableTest` | existant |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/adapter-capability-decomposition.md` (exigence ajoutée, traçabilité) ;
`.claude/rules/architecture.md`, section `morpheus-cli` et `morpheus-mcp`, si l'assertion y est citée.

## Questions ouvertes

Aucune.
