---
id: BE-0106
title: "Une borne entrante est défensive, une borne sortante est un défaut interne"
domain: backend
rules: false
---

# Une borne entrante est défensive, une borne sortante est un défaut interne

- Statut : **Acceptée**
- Date : 2026-09-23

## Context

Le transport MCP STDIO (`morpheus-mcp-transport`) est partagé par le serveur MORPHEUS (`morpheus-mcp`) et par les
clients des pairs MINOS et NEXUS. Il borne la taille d'une trame (`BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES`)
et la durée d'un handler. Ces bornes posent quatre questions :

- traiter une trame **sortante** hors borne comme une trame entrante fermerait la session : une réponse trop grosse,
  rejouée, ferait tomber le serveur à chaque essai ;
- un superviseur doit distinguer une fin de session propre d'un arrêt en échec par le code de sortie du serveur ;
- retenir toute `ProcessHandle` jamais observée chez un pair coûterait un appel système par poignée et par tick,
  un coût qui croît avec l'historique du pair et non avec ses processus vivants ;
- un handler qui ne rend jamais la main bloque la boucle de lecture, qui traite les messages un par un ; et une
  opération vers un pair enchaîne plusieurs requêtes (`initialize`, `tools/list` paginé, puis un ou plusieurs
  `tools/call`), alors que le SDK ne borne que chaque requête.

Options écartées :

- **Relever `DEFAULT_MAX_FRAME_BYTES`** : déplace le seuil, laisse le comportement au seuil intact.
- **Tronquer la réponse** : une réponse tronquée est une vérité partielle présentée comme complète.
- **Nommer dans le transport l'outil à utiliser à la place** : le transport ne connaît aucun catalogue ; côté client
  un nom d'outil serveur ne désigne rien, et un outil nommé d'avance est faux pour une collection autre que la sienne.
- **Exposer la cause terminale comme `Throwable` public** : elle peut porter du texte du pair ; un booléen suffit au
  superviseur.
- **Libérer aussi les poignées vivantes au-delà d'un plafond** : casserait la terminaison des descendants vivants.
- **Répondre une erreur au même `id` quand un handler dépasse sa borne, et continuer** : `future.cancel(true)`
  n'arrête pas un handler qui ignore l'interruption ; s'il termine plus tard, il écrit une seconde réponse pour le
  même `id`, et l'empêcher demanderait de retenir les `id` répondus, en mémoire non bornée ou dans une fenêtre
  au-delà de laquelle le doublon repasse.
- **Abaisser le délai maximal configurable des pairs** : retirerait aux opérateurs un réglage publié, ferait échouer
  au démarrage une configuration valide, et laisserait la pagination de `tools/list` non bornée.
- **Borner chaque requête par le temps restant de l'opération** : impossible avec le client synchrone du SDK, dont
  le délai de requête est fixé à la construction.

Les deux directions d'une même borne n'ont pas la même réaction. Entrante, la borne est défensive : un pair qui
dépasse une limite déclarée est hostile ou cassé, et fermer la session est la bonne réponse ; `readLine(maxMessageBytes)`
lève l'exception et la boucle de lecture échoue fermé. Sortante, la trame est celle de MORPHEUS : la dépasser est un
défaut de MORPHEUS, et le client n'a pas à le payer de sa session. Le transport décrit ce qu'il a refusé et rien
d'autre, et la requête suivante est servie. Le texte d'orientation est borné pour que le substitut garde une taille
fixe ; celui de `MorpheusMcpServer` est générique : les outils de lecture paginés acceptent `offset` et `limit`, et le
premier geste est de relancer avec un `limit` plus petit. Une seule fabrique construit le serveur, pour que le chemin
de test et celui de production ne divergent pas. Une notification, une requête initiée par le serveur ou une réponse
sans `id` n'a aucune requête en attente à laquelle répondre : le refus journalisé n'est pas une dégradation
silencieuse. Un substitut qui dépasse lui-même la borne n'arrive que si le pair a choisi un `id` proche de la borne.

Côté client, seule la branche « sans `id` » a un sens : le client n'a aucune requête du pair en attente à laquelle
substituer une réponse. `sendMessage` rattrape `OutboundMessageRefusedException` **avant** la branche `IOException`
qui échoue fermé, et le pair continue de tourner. Le refus couvre aussi une réponse à une requête initiée par le pair :
le client MORPHEUS n'enregistre aucune capacité (`roots`, `sampling`) qui en émettrait une ; si cela change, la
substitution se décide avec la capacité. `get_current_specification` suit les mêmes bornes que les autres outils de
lecture paginés ; une page d'une seule spécification dont la description dépasse à elle seule la borne reste refusée
par `MCP_RESPONSE_TOO_LARGE`, et la session survit.

Le transport retient son issue terminale sans exposer la cause : elle peut être contrôlée par le pair et elle est déjà
journalisée par `McpDiagnosticRedactor`. Codes de sortie de `MorpheusMcpServer` :

| Issue | Constante |
|---|---|
| `stdin` a atteint EOF | `MorpheusMcpServer.EXIT_END_OF_INPUT` |
| échec fermé du transport | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |
| attente interrompue | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |
| borne d'un handler dépassée | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |

L'interruption rend le code d'échec : le serveur n'a pas atteint la fin de son flux d'entrée, ce que le code de fin
propre promet. `morpheus-mcp` ne peut pas importer `CliExitCode` : `morpheus-cli` dépend de `morpheus-mcp`, pas
l'inverse. Les valeurs convergent sans dépendance (`EXIT_TRANSPORT_FAILURE` sur `CliExitCode.IO_ERROR`), et un test de
`morpheus-architecture-tests`, seul module qui voit les deux, épingle l'égalité.

Les descendants vivants d'un pair MCP restent retenus selon la garantie de `SECURITY.md` (« MCP peer descendants are
best-effort »). Un processus mort ne peut pas être terminé, et ses enfants orphelins ne sont rattachables à lui par
aucune poignée retenue. L'arrêt et la destruction de l'arbre partent de la racine. La règle ne touche ni au pas
d'observation ni à `destroyObservedDescendants`.

`BoundedStdioServerTransportProvider.DEFAULT_HANDLER_DEADLINE` est une borne de sécurité, pas un délai de service :
elle n'existe que pour qu'un défaut se voie, et le constructeur la règle pour les tests, comme `maxFrameBytes`. Au
dépassement, `MorpheusMcpServer` rend `EXIT_TRANSPORT_FAILURE`. Ce n'est pas une incohérence avec la substitution
d'une trame sortante. Une trame sortante hors borne est un défaut **connu et contenu** : MORPHEUS sait ce qui a été
refusé, rien ne tourne encore, et la substitution garde l'ordonnancement intact. Un handler hors borne a rompu
l'invariant de la boucle séquentielle : annuler libère le lecteur sans prouver que le travail s'est arrêté, et la
session a perdu sa garantie d'ordonnancement.

`PeerOperationDeadline` (`morpheus-mcp-transport`) déclare l'enveloppe que toute intégration de pair respecte ;
`START_UP_ALLOWANCE` couvre le lancement du processus et le travail local. Les passerelles sont `MinosMcpCodeGateway`
et `NexusMcpContextGateway` ; chacune arme l'échéance à sa construction et la désarme à sa fermeture. À l'échéance,
une sentinelle ferme le client du SDK, ce qui fait échouer immédiatement la requête en attente, et la passerelle répond
par l'erreur bornée au lieu d'une session rompue. La borne du handler est **calculée**, pas choisie :

```text
DEFAULT_HANDLER_DEADLINE = PeerOperationDeadline.LONGEST_OPERATION
                         + PeerOperationDeadline.CLOSE_ALLOWANCE
                         + BoundedStdioServerTransportProvider.HANDLER_LOCAL_WORK_MARGIN
```

Ainsi un handler qui attend un pair dont chaque requête tient dans son délai configuré se termine par l'erreur bornée
de l'opération, jamais par cette borne. La relation est tenue dans `morpheus-architecture-tests`, seul module qui voit
le transport et les pairs : l'opération de chaque passerelle est calculée depuis son `MAX_TIMEOUT_SECONDS` et son
`REQUESTS_PER_OPERATION`, doit tenir dans l'enveloppe, et la borne doit dépasser la plus longue augmentée de sa
fermeture. Un pair ajouté, ou une requête de plus, repose la question.

La règle d'une ligne du manifeste par outil MCP servi, avec des colonnes `cli` et `http` comparées aux commandes et
routes réelles et une sentinelle pour toute absence, vaut pour chaque outil du câblage par défaut de
`MorpheusMcpServer`. Les outils qui lancent à la demande un pair configuré par l'opérateur
(`resolve_external_reference`, `get_augmented_requirement_context`, `get_augmented_change_context`) nomment l'outil
dans la colonne `mcp`.

## Decision

1. **Entrante** hors borne : `MessageTooLargeException`, échec fermé des deux côtés.
2. **Sortante** : borne non relevée. Serveur (`BoundedStdioServerTransportProvider`) : une réponse à `id` devient une
   erreur au même `id`, `MCP_RESPONSE_TOO_LARGE` (`RESPONSE_TOO_LARGE`), taille, borne, sans fragment ; la session
   survit, sauf substitut trop grand. Autre trame, serveur ou client (`BoundedStdioClientTransport`) : `WARNING`
   (nature, taille, borne, sans message d'exception) et `OutboundMessageRefusedException` à l'émetteur, session
   ouverte.
3. L'orientation vient du catalogue, vide par défaut, bornée à `MAX_GUIDANCE_CHARS` (`OVERSIZED_RESPONSE_GUIDANCE`,
   fabrique unique de `MorpheusMcpServer`). Le transport ne nomme aucun outil.
4. `get_current_specification` : `offset`/`limit` (ADR-0151 §2), tri par identifiant, `description` gardée.
5. Sortie (`terminatedInFailure()`, cause non exposée) : `EXIT_END_OF_INPUT` (`CliExitCode.SUCCESS`) à l'EOF ;
   `EXIT_TRANSPORT_FAILURE` (`IO_ERROR`) sur échec fermé, interruption ou borne de handler ; sans import ;
   substitution sans effet.
6. Poignées : descendant mort libéré ; vivant et racine retenus.
7. Handler : `DEFAULT_HANDLER_DEADLINE` = `LONGEST_OPERATION + CLOSE_ALLOWANCE + HANDLER_LOCAL_WORK_MARGIN`, borne
   de sécurité réglée au constructeur. Dépassée : annulation, échec fermé `HandlerDeadlineExceededException` via
   `McpDiagnosticRedactor`.
8. Pairs : `PeerOperationDeadline` admet ≤ `MAX_SEQUENTIAL_REQUESTS` requêtes de ≤ `MAX_REQUEST_TIMEOUT`,
   échéance délai × requêtes + `START_UP_ALLOWANCE`. Une passerelle (une opération) déclare
   `REQUESTS_PER_OPERATION`, arme son échéance (`tools/list` compris) et ferme à l'échéance le client du SDK :
   `… MCP operation exceeded its N ms deadline`.
9. Un outil servi a sa ligne de `contracts/public-surfaces.tsv` (ADR-0089, point 3) et dit en notes s'il lance un pair.

## Do's and Don'ts

### Do

- Échouer fermé sur toute trame **entrante** hors borne, des deux côtés.
- Remplacer une réponse sortante hors borne par une erreur `MCP_RESPONSE_TOO_LARGE` au même `id`, sans fragment du
  contenu.
- Refuser à son émetteur local, par `OutboundMessageRefusedException`, toute autre trame sortante hors borne, et
  garder la session ouverte.
- Fournir le texte d'orientation depuis la couche qui connaît le catalogue, borné à `MAX_GUIDANCE_CHARS`.
- Libérer la poignée d'un descendant mort ; retenir celle d'un descendant vivant et celle de la racine.
- Dériver `DEFAULT_HANDLER_DEADLINE` de l'enveloppe `PeerOperationDeadline` ; armer une échéance par opération de pair.
- Ajouter la ligne de `contracts/public-surfaces.tsv` de tout nouvel outil MCP servi.

### Don't

- Ne pas relever `DEFAULT_MAX_FRAME_BYTES` pour faire passer une réponse.
- Ne pas tronquer une réponse pour la faire tenir dans la borne.
- Ne pas écrire de nom d'outil du catalogue serveur dans `morpheus-mcp-transport`.
- Ne pas exposer la cause terminale du transport comme `Throwable` public.
- Ne pas faire survivre une session dont un handler a dépassé sa borne.
- Ne pas importer `CliExitCode` dans `morpheus-mcp`.
- Ne pas rendre la borne du handler réglable par le client MCP.

## Consequences

### Positives

- Une réponse trop grosse ne ferme ni la session ni le serveur ; le client apprend ce qui a été refusé et quoi faire.
- Un superviseur distingue une fin propre d'un arrêt en échec.
- Le coût de surveillance d'un pair suit ses processus vivants, pas son historique.
- Un handler bloqué ne bloque pas le lecteur indéfiniment, et un pair lent finit par sa propre erreur bornée.

### Négatives

- Un handler réellement bloqué n'est détecté qu'au terme de la plus longue opération de pair admise, augmentée des
  marges de fermeture et de travail local.
- La convergence des codes de sortie et la relation entre borne et pairs ne tiennent que par des tests du module
  d'architecture, faute de dépendance directe.

### Risques

- La terminaison des descendants d'un pair MCP reste best-effort (`SECURITY.md`).
- Les trois outils qui lancent un pair configuré à la demande du modèle laissent une question de produit ouverte,
  portée par ADR-0129 : doivent-ils être orientés modèle ? Un pair explicitement configuré est du code de confiance,
  et seul le probe de plugin relève de la garantie « le modèle ne peut pas déclencher l'exécution de code tiers ».
  Aucune exposition n'est changée tant que cette question n'est pas tranchée.

## Compliance and Enforcement

- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/BoundedStdioServerTransportProviderFrameBoundTest.java` :
  réponse hors borne remplacée au même `id` et session qui sert la requête suivante, orientation fournie qui atteint
  le client, notification hors borne refusée sans fermer la session, substitut trop gros qui échoue fermé, trame
  entrante hors borne qui échoue fermé ;
  `theOversizedResponseErrorDoesNotNameAToolTheTransportCannotKnow`.
- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/BoundedStdioClientTransportTest.java` :
  `anOversizedOutboundRequestIsRefusedWithoutKillingThePeer`, `anInboundFrameOverTheBoundStillFailsClosed`.
- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/BoundedStdioClientTransportHandlePruningTest.java` :
  règle d'élagage des poignées.
- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/BoundedStdioServerTransportProviderHandlerDeadlineTest.java` :
  handler sans fin qui ferme la session, handler dans sa borne servi, valeur par défaut épinglée.
- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/PeerOperationDeadlineTest.java` : abandon à
  l'échéance, désarmement, formule, refus hors enveloppe.
- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosMcpOperationDeadlineTest.java` et
  `morpheus-integration-nexus/src/test/java/com/morpheus/integration/nexus/NexusMcpOperationDeadlineTest.java` : un
  vrai processus pair.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/server/MorpheusMcpServerExitCodeTest.java` et
  `morpheus-mcp/src/test/java/com/morpheus/mcp/server/MorpheusMcpServerOversizedResponseTest.java` : codes de sortie, et
  refus d'une spécification hors borne par le câblage réel, sans fragment, avec code de fin propre.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/CatalogueToolsAnswerTest.java` :
  `aBoundedSliceOfSpecificationsIsReturnedForAnOffsetAndALimit` ;
  `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusMcpToolCatalogTest.java` :
  `theCurrentSpecificationToolDescribesWhatItActuallyReturns`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/ProductionIntegrityContractTest.java` :
  `mcpServerExitCodesConvergeWithTheCliExitCodeTable`, `theHandlerSafetyBoundOutlastsTheLongestPeerOperation`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/AuditHardeningWorkflowContractTest.java` :
  `mcpServerTransportAnswersAnOversizedResponseInsteadOfFailingClosed`,
  `mcpClientTransportRefusesAnOversizedOutboundFrameInsteadOfFailingClosed`, `mcpTransportNamesNoServerCatalogTool`
  (assertions textuelles).
- `morpheus-architecture-tests/src/test/java/com/morpheus/mcp/PublicSurfaceManifestCoversEveryServedToolTest.java` :
  les noms servis et la colonne `mcp` du manifeste, comparés dans les deux sens. Elle tient la **présence** d'une
  ligne, pas la justesse des colonnes `cli` et `http`.

## References

- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP officiel et transport STDIO.
- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — diagnostics sûrs par défaut.
- [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) — manifeste public de surfaces.
- [ADR-0096](GEN-0096-conservative-native-mcp-client-integration.md) — intégration conservatrice des clients MCP.
- [ADR-0102](BE-0102-mcp-failure-contract-is-one-rule.md) — contrat d'échec MCP.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — code externe de confiance.
- [ADR-0151](BE-0151-pagination-des-collections-et-journaux.md) — vocabulaire de page.
- `SECURITY.md` — descendants d'un pair MCP.
- `contracts/public-surfaces.tsv` — manifeste de convergence.
