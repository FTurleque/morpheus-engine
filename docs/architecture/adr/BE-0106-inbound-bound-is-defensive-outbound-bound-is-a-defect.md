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

## Decision

### 1. Les deux directions d'une même borne n'ont pas la même réaction

**Entrante**, la borne est défensive : un pair qui dépasse une limite déclarée est hostile ou cassé, et fermer la
session est la bonne réponse. `readLine(maxMessageBytes)` lève `MessageTooLargeException` et la boucle de lecture
échoue fermé, côté serveur comme côté client.

**Sortante**, la trame est celle de MORPHEUS : la dépasser est un défaut de MORPHEUS, et le client n'a pas à le payer
de sa session. La borne n'est pas relevée.

Côté serveur (`BoundedStdioServerTransportProvider`) :

- une **réponse** JSON-RPC portant un `id` est remplacée par une réponse d'erreur portant **le même `id`**, avec
  l'état de rejet nommé `MCP_RESPONSE_TOO_LARGE` (`RESPONSE_TOO_LARGE`), la taille produite et la borne. Le transport
  décrit ce qu'il a refusé et rien d'autre. L'erreur ne contient **aucun fragment** du contenu qui a débordé. La
  session survit et la requête suivante est servie ;
- un texte d'orientation peut être fourni à la construction par la couche qui connaît le catalogue : vide par défaut,
  borné à `MAX_GUIDANCE_CHARS` caractères pour que le substitut garde une taille fixe, jamais un fragment du contenu.
  `MorpheusMcpServer` fournit `OVERSIZED_RESPONSE_GUIDANCE`, générique : les outils de lecture paginés acceptent
  `offset` et `limit`, et le premier geste est de relancer avec un `limit` plus petit. Les chemins de construction du
  serveur passent par une seule fabrique, pour que le chemin de test et celui de production ne divergent pas ;
- une **notification**, une requête initiée par le serveur ou une réponse sans `id` n'a aucune requête en attente à
  laquelle répondre : elle est journalisée en `WARNING` (nature, taille, borne) et refusée à son émetteur local par
  `OutboundMessageRefusedException`. La session reste ouverte ; ce n'est pas une dégradation silencieuse ;
- l'échec fermé ne reste sur la sortie que si l'erreur de substitution **elle-même** ne tient pas dans la borne, ce
  qui n'arrive que si le pair a choisi un `id` proche de la borne.

Côté client (`BoundedStdioClientTransport`), seule la branche « sans `id` » a un sens : le client n'a aucune requête
du pair en attente à laquelle substituer une réponse. Une trame sortante hors borne est journalisée en `WARNING`
(nature, taille, borne, jamais le message d'une exception), puis refusée à son émetteur local par
`OutboundMessageRefusedException`, que `sendMessage` rattrape **avant** la branche `IOException` qui échoue fermé. Le
pair continue de tourner. Le refus couvre aussi une réponse à une requête initiée par le pair : le client MORPHEUS
n'enregistre aucune capacité (`roots`, `sampling`) qui en émettrait une ; si cela change, la substitution se décide
avec la capacité.

Aucun nom d'outil du catalogue serveur n'apparaît dans `morpheus-mcp-transport`.

`get_current_specification` est paginé par `offset` et `limit`, aux mêmes bornes que les autres outils de lecture
paginés ; la forme de sa page est décidée par ADR-0131 (§2). La page est ordonnée par identifiant de spécification et chaque
spécification garde sa `description`. Une page d'une seule spécification dont la description dépasse à elle seule la
borne reste refusée par `MCP_RESPONSE_TOO_LARGE`, et la session survit.

### 2. Le code de sortie distingue la fin propre de l'arrêt en échec

Le transport retient son issue terminale (`terminatedInFailure()`), sans exposer la cause : elle peut être contrôlée
par le pair et elle est déjà journalisée par `McpDiagnosticRedactor`.

| Issue | Constante |
|---|---|
| `stdin` a atteint EOF | `MorpheusMcpServer.EXIT_END_OF_INPUT` |
| échec fermé du transport | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |
| attente interrompue | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |
| borne d'un handler dépassée | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |

L'interruption rend le code d'échec : le serveur n'a pas atteint la fin de son flux d'entrée, ce que le code de fin
propre promet. Une réponse remplacée par `MCP_RESPONSE_TOO_LARGE` ne change pas le code de sortie.

`morpheus-mcp` ne peut pas importer `CliExitCode` : `morpheus-cli` dépend de `morpheus-mcp`, pas l'inverse. Les valeurs
convergent sans dépendance, `EXIT_END_OF_INPUT` sur `CliExitCode.SUCCESS` et `EXIT_TRANSPORT_FAILURE` sur
`CliExitCode.IO_ERROR`, et un test de `morpheus-architecture-tests`, seul module qui voit les deux, épingle l'égalité.

### 3. Une poignée de processus mort est libérée, une poignée vivante est retenue

Les descendants **vivants** d'un pair MCP restent retenus, selon la garantie documentée dans `SECURITY.md` (« MCP peer
descendants are best-effort »). Une poignée est libérée une fois son processus mort : un processus mort ne peut pas
être terminé, et ses enfants orphelins ne sont rattachables à lui par aucune poignée retenue. La racine n'est
jamais libérée : l'arrêt et la destruction de l'arbre partent d'elle. Le pas d'observation et
`destroyObservedDescendants` ne changent pas.

### 4. Un handler a une borne de sécurité, et la dépasser ferme la session

L'attente d'un handler est bornée par `BoundedStdioServerTransportProvider.DEFAULT_HANDLER_DEADLINE`. C'est une
**borne de sécurité**, pas un délai de service : elle n'existe que pour qu'un défaut se voie, n'est pas réglable par
le client, et se règle par le constructeur pour les tests, comme `maxFrameBytes`.

Au dépassement, le handler est annulé, la session **échoue fermé** avec une cause nommée
(`HandlerDeadlineExceededException`), la trace passe par `McpDiagnosticRedactor` comme tout échec de transport, et
`MorpheusMcpServer` rend `EXIT_TRANSPORT_FAILURE`.

Ce n'est pas une incohérence avec le §1. Une trame sortante hors borne est un défaut **connu et contenu** : MORPHEUS
sait ce qui a été refusé, rien ne tourne encore, et la substitution garde l'ordonnancement intact. Un handler hors
borne a rompu l'invariant de la boucle séquentielle : annuler libère le lecteur sans prouver que le travail s'est
arrêté, et la session a perdu sa garantie d'ordonnancement.

### 5. Une opération de pair a une échéance, et la borne du handler en est dérivée

`PeerOperationDeadline` (`morpheus-mcp-transport`) déclare l'enveloppe que toute intégration de pair respecte : un
délai de requête d'au plus `MAX_REQUEST_TIMEOUT` et au plus `MAX_SEQUENTIAL_REQUESTS` requêtes par opération.
L'échéance d'une opération vaut délai × requêtes + `START_UP_ALLOWANCE` (lancement du processus et travail local) ;
`PeerOperationDeadline.of` refuse tout ce qui sort de l'enveloppe.

Chaque passerelle déclare `REQUESTS_PER_OPERATION` (`MinosMcpCodeGateway`, `NexusMcpContextGateway`), arme l'échéance
à sa construction et la désarme à sa fermeture ; une passerelle ne sert qu'une opération. À l'échéance, une
sentinelle ferme le client du SDK, ce qui fait échouer immédiatement la requête en attente, et la passerelle répond
`… MCP operation exceeded its N ms deadline` au lieu d'une session rompue. La pagination de `tools/list` est bornée par
la même horloge.

La borne du handler est **calculée**, pas choisie :

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

### 6. Chaque outil MCP servi a sa ligne dans le manifeste de convergence

La règle d'une ligne de `contracts/public-surfaces.tsv` par outil MCP servi, et de colonnes `cli` et `http` comparées
aux commandes et routes réelles avec une sentinelle pour toute absence, est décidée par ADR-0089 (§5) ; elle vaut pour
chaque outil servi par le câblage par défaut de `MorpheusMcpServer`. Les outils qui lancent à la demande un pair
configuré par l'opérateur (`resolve_external_reference`, `get_augmented_requirement_context`,
`get_augmented_change_context`) nomment l'outil dans la colonne `mcp`, et leurs notes disent qu'ils lancent ce pair.

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

- `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedStdioServerTransportProviderFrameBoundTest.java` :
  réponse hors borne remplacée au même `id` et session qui sert la requête suivante, orientation fournie qui atteint
  le client, notification hors borne refusée sans fermer la session, substitut trop gros qui échoue fermé, trame
  entrante hors borne qui échoue fermé ;
  `theOversizedResponseErrorDoesNotNameAToolTheTransportCannotKnow`.
- `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedStdioClientTransportTest.java` :
  `anOversizedOutboundRequestIsRefusedWithoutKillingThePeer`, `anInboundFrameOverTheBoundStillFailsClosed`.
- `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedStdioClientTransportHandlePruningTest.java` :
  règle d'élagage des poignées.
- `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedStdioServerTransportProviderHandlerDeadlineTest.java` :
  handler sans fin qui ferme la session, handler dans sa borne servi, valeur par défaut épinglée.
- `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/PeerOperationDeadlineTest.java` : abandon à
  l'échéance, désarmement, formule, refus hors enveloppe.
- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosMcpOperationDeadlineTest.java` et
  `morpheus-integration-nexus/src/test/java/com/morpheus/integration/nexus/NexusMcpOperationDeadlineTest.java` : un
  vrai processus pair.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusMcpServerExitCodeTest.java` et
  `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusMcpServerOversizedResponseTest.java` : codes de sortie, et
  refus d'une spécification hors borne par le câblage réel, sans fragment, avec code de fin propre.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusMcpToolServiceTest.java` :
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
- [ADR-0131](BE-0131-pagination-des-collections-et-journaux.md) — vocabulaire de page.
- `SECURITY.md` — descendants d'un pair MCP.
- `contracts/public-surfaces.tsv` — manifeste de convergence.
