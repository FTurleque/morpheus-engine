# ADR-0106 — Une borne entrante est défensive, une borne sortante est un défaut interne

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 23 septembre 2026
- Dépend de : ADR-0062 (SDK MCP), ADR-0087 (diagnostics sûrs par défaut), ADR-0096 (client MCP natif
  conservateur), ADR-0103 (une règle n'est acceptée qu'après avoir été cassée)
- Portée : `BoundedStdioServerTransportProvider`, `MessageTooLargeException`, `MorpheusMcpServer`,
  `BoundedStdioClientTransport`

## Contexte

L'audit de code du 22/09/2026 a relevé trois constats sur le transport MCP STDIO, tous antérieurs à 1.2.1 :

- **MCP-1** — une trame **sortante** plus grosse que `DEFAULT_MAX_FRAME_BYTES` (1 Mio) levait la même
  `MessageTooLargeException` qu'une trame entrante, et déclenchait la même réaction : `failClosed`, session fermée,
  serveur arrêté. `get_current_specification` sérialise la spécification entière sans pagination : sur un projet
  assez gros, la même requête refaisait tomber le serveur à chaque essai.
- **MCP-2** — `MorpheusMcpServer.run` rendait `0` sur une fin propre de `stdin`, après un `failClosed`, et après
  une interruption. Un superviseur ne pouvait pas distinguer une fin de session d'un arrêt en échec.
- **MCP-3** — `BoundedStdioClientTransport` retenait toute `ProcessHandle` jamais observée. La boucle de
  surveillance (10 ms) recopiait la carte et interrogeait la vivacité de chaque poignée à chaque tick : un coût qui
  croît avec l'historique du pair, pas avec ses processus vivants.

## Décision

### 1. Les deux directions d'une même borne n'ont pas la même réaction

**Entrante**, la borne est défensive : un pair qui dépasse une limite déclarée est hostile ou cassé, et fermer la
session est la bonne réponse. Cet invariant ne change pas.

**Sortante**, la trame est celle de MORPHEUS : la dépasser est un défaut de MORPHEUS, et le client n'a pas à le
payer de sa session. Concrètement :

- une **réponse** JSON-RPC portant un `id` est remplacée par une réponse d'erreur portant **le même `id`**, avec
  l'état de rejet nommé `MCP_RESPONSE_TOO_LARGE`, la taille produite, la borne, et la surface bornée à utiliser à
  la place (`find_requirements`, qui porte déjà `offset` et `limit`). L'erreur ne contient **aucun fragment** du
  contenu qui a débordé. La session survit et la requête suivante est servie ;
- une **notification**, une requête initiée par le serveur ou une réponse sans `id` n'a aucune requête en attente
  à laquelle répondre : elle est journalisée en `WARNING` (nature, taille, borne) et refusée à son émetteur local
  par une erreur explicite. La session reste ouverte ; ce n'est pas une dégradation silencieuse ;
- `failClosed` ne reste sur la sortie que si l'erreur de substitution **elle-même** ne tient pas dans la borne —
  ce qui n'arrive que si le pair a choisi un `id` proche de la borne. C'est alors un échec réel.

La borne elle-même n'est **pas** relevée : relever 1 Mio déplacerait le seuil sans corriger le comportement au
seuil. Aucun schéma d'outil ne change, donc ni `contracts/public-surfaces.tsv` ni l'OpenAPI ne bougent.

### 2. Le code de sortie distingue la fin propre de l'arrêt en échec

Le transport retient son issue terminale (`terminatedInFailure()`), sans exposer la cause : elle peut être
contrôlée par le pair et a déjà été journalisée par `McpDiagnosticRedactor`.

| Issue | Code | Constante |
|---|---:|---|
| `stdin` a atteint EOF | 0 | `MorpheusMcpServer.EXIT_END_OF_INPUT` |
| `failClosed` | 5 | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |
| attente interrompue | 5 | `MorpheusMcpServer.EXIT_TRANSPORT_FAILURE` |

L'interruption rend le code d'échec : le serveur n'a pas atteint la fin de son flux d'entrée, ce que `0` promet.

`morpheus-mcp` ne peut pas importer `CliExitCode` : `morpheus-cli` dépend de `morpheus-mcp`, pas l'inverse. Les
valeurs **convergent sans dépendance**, sur `CliExitCode.SUCCESS` et `CliExitCode.IO_ERROR`, et un test de
`morpheus-architecture-tests` — seul module qui voit les deux — épingle l'égalité.

### 3. Une poignée de processus mort est libérée, une poignée vivante est retenue

La rétention des descendants **vivants** d'un pair MCP reste la garantie documentée (`SECURITY.md`,
« MCP peer descendants are best-effort »). Ce qui change est l'absence de borne : une poignée est libérée une fois
son processus mort. Un processus mort ne peut pas être terminé, et ses enfants orphelins ne sont déjà plus
rattachables à lui par aucune poignée retenue ; garder la poignée n'achetait rien et coûtait un appel système par
tick, pour toujours. La racine n'est jamais libérée : l'arrêt et la destruction de l'arbre partent d'elle. Ni le pas
de 10 ms, ni `destroyObservedDescendants` ne changent.

## Alternatives écartées

- **Relever `DEFAULT_MAX_FRAME_BYTES`.** Déplace le seuil, laisse le comportement au seuil intact.
- **Ajouter une pagination à `get_current_specification`.** Change un schéma d'outil, donc le manifeste de
  convergence et l'OpenAPI. Décision séparée ; la surface bornée existe déjà.
- **Tronquer la réponse.** Une réponse tronquée est une vérité partielle présentée comme complète.
- **Exposer la cause terminale comme `Throwable` public.** La cause peut porter du texte du pair ; le booléen suffit
  au superviseur.
- **Libérer aussi les poignées vivantes au-delà d'un plafond.** Casserait la garantie de terminaison des
  descendants vivants.

## Preuves exécutables

- `BoundedStdioServerTransportProviderFrameBoundTest` — réponse hors borne remplacée par une erreur au même `id` et
  session qui sert la requête suivante ; notification hors borne refusée sans fermer la session ; substitut trop
  gros qui échoue fermé ; trame **entrante** hors borne qui échoue toujours fermé.
- `MorpheusMcpServerExitCodeTest` — `0` sur EOF, `EXIT_TRANSPORT_FAILURE` après un échec de transport.
- `ProductionIntegrityContractTest#mcpServerExitCodesConvergeWithTheCliExitCodeTable` — convergence avec
  `CliExitCode`.
- `AuditHardeningWorkflowContractTest#mcpServerTransportAnswersAnOversizedResponseInsteadOfFailingClosed` —
  assertion textuelle (ADR-0103 : l'intention est « ce mécanisme ne doit pas être recâblé ») ; cassée en
  réintroduisant `failClosed(` dans le chemin d'encodage sortant, puis rétablie.
- `BoundedStdioClientTransportHandlePruningTest` — la règle d'élagage, sur la fonction pure qui la porte.

## Amendement du 23 septembre 2026 — le conseil sort du transport, la pagination n'est plus écartée, la règle vaut des deux côtés

La relecture de l'implémentation initiale (constats **MCP-6** et **MCP-7**) a relevé un même défaut à deux endroits :
la décision énonçait une règle plus large que ce qu'elle appliquait. Les paragraphes ci-dessus restent tels quels ;
cette section dit ce qui change et pourquoi.

### 1. Le conseil sort du transport

Le message `MCP_RESPONSE_TOO_LARGE` nommait `find_requirements` depuis `BoundedStdioServerTransportProvider`. Deux
défauts :

- **le conseil était faux là où il comptait.** Le débordement de `get_current_specification` venait de la liste des
  spécifications, chacune avec sa `description` ; `find_requirements` pagine les **exigences**, une autre collection.
  Pour les outils qui portaient déjà `offset` et `limit`, le bon geste était de baisser `limit`, pas de changer d'outil ;
- **le transport ne peut pas savoir.** `morpheus-mcp-transport` est partagé par le serveur (`morpheus-mcp`) et par
  les clients MINOS et NEXUS. Un nom d'outil du catalogue serveur écrit en dur dans ce module est une fuite de
  l'applicatif dans le transport, et côté client il ne désigne rien.

Le transport décrit désormais **ce qu'il a refusé** — l'état nommé, la taille produite, la borne — et rien d'autre.
Un texte d'orientation est fourni à la construction par la couche qui connaît le catalogue ; il est vide par défaut
(clients MINOS/NEXUS), borné à `MAX_GUIDANCE_CHARS` caractères pour que le substitut garde une taille fixe, et ne
contient jamais de fragment du contenu qui a débordé. `MorpheusMcpServer` fournit
`OVERSIZED_RESPONSE_GUIDANCE`, générique et vrai : les outils de lecture paginés acceptent `offset` et `limit`, et
le premier geste est de relancer avec un `limit` plus petit. Les quatre chemins de construction du serveur passent
par une seule fabrique, pour que le chemin de test et le chemin de production ne puissent pas diverger.

### 2. La pagination de `get_current_specification` n'est plus écartée — et le motif qui l'écartait était inexact

La section « Alternatives écartées » écarte la pagination parce qu'elle « change un schéma d'outil, donc le manifeste
de convergence et l'OpenAPI ». **C'est inexact.** Vérifié à la date de cet amendement : `contracts/public-surfaces.tsv`
ne porte `get_current_specification` dans aucune colonne, et aucun fichier de `docs/openapi/` ne mentionne un nom
d'outil MCP. Ajouter `offset` et `limit` à cet outil ne déplace ni le manifeste ni l'OpenAPI ; le coût réel est
interne à `morpheus-mcp` (catalogue, service, tests). Le paragraphe d'origine reste visible à dessein : une
alternative écartée pour un motif faux, corrigée à découvert, ne sera pas réécartée plus tard pour la même raison.
Pour la même raison, la phrase de la décision §1 « aucun schéma d'outil ne change » ne vaut plus que pour la
réponse à la borne elle-même ; le schéma de `get_current_specification` change, pas le manifeste.

`get_current_specification` accepte donc `offset` et `limit` aux **mêmes bornes** que ses voisins paginés
(`0..1 000 000`, `1..MAX_LIMIT`, `limit` par défaut `DEFAULT_LIMIT`) : huit outils sur les quatorze du catalogue de
lecture les portent désormais, contre sept. La réponse garde ses identifiants et ses compteurs, et porte une page de
spécifications ordonnée par identifiant — l'ordre que `GET /projects/{projectId}/specifications` applique déjà —,
avec `specificationCount`, `offset`, `limit` et `hasMore`. La projection par spécification ne perd rien :
`description` reste, parce que c'est le contenu que le modèle vient chercher et que la version HTTP le rend. Borner
n'est pas amputer. La description de l'outil, qui annonçait un *summary* pour une charge utile complète, dit
maintenant ce qui est rendu. Aucun outil n'est ajouté.

> **Vocabulaire remplacé par ADR-0107** (23/09/2026). La page n'est plus à plat : elle est l'objet porté par
> `specifications`, sous les clés canoniques `offset`, `limit`, `totalMatches`, `hasMore`, `items`, et
> `specificationCount` a disparu. Le paragraphe ci-dessus décrit la forme livrée par la #353.

Une page d'une seule spécification dont la description dépasse à elle seule la borne reste refusée par
`MCP_RESPONSE_TOO_LARGE` : c'est le cas que la borne existe pour refuser, et la session survit.

### 3. La règle vaut des deux côtés et pour les deux transports

La portée de cet ADR nomme `BoundedStdioClientTransport`, mais son chemin sortant levait encore
`MessageTooLargeException` et fermait la session du pair — exactement ce que la décision §1 déclare fautif. Le
risque pratique était faible (une requête MORPHEUS de plus de 4 Mio vers MINOS est peu plausible, et son contenu est
du code MORPHEUS) ; l'enjeu est qu'un ADR dont l'un des fichiers de sa portée fait l'inverse n'est plus exécutable.

Le client applique désormais la branche **« sans `id` »** du serveur, la seule qui ait un sens ici : il n'a aucune
requête du pair en attente à laquelle substituer une réponse. La trame est journalisée en `WARNING` (nature, taille,
borne — jamais le message d'une exception), puis refusée à son émetteur local par
`OutboundMessageRefusedException`, extraite du serveur vers son propre fichier pour que les deux transports la
partagent. `sendMessage` rattrape ce refus **avant** la branche `IOException` qui échoue fermé. Le pair continue de
tourner et la requête suivante est servie.

Côté client, le refus couvre aussi une réponse à une requête initiée par le pair : en substituer une exigerait une
forme d'erreur propre à chaque méthode serveur, et le client MORPHEUS n'enregistre aucune capacité (`roots`,
`sampling`) qui en émettrait une. Si cela change, la substitution se décidera avec la capacité.

**L'entrée ne change pas.** `readLine(maxMessageBytes)` lève toujours `MessageTooLargeException`, et la boucle de
lecture échoue toujours fermé, des deux côtés.

### Hors de cet amendement

Treize des quatorze outils du catalogue de lecture sont absents de `contracts/public-surfaces.tsv` : seul
`get_acceptance_criteria` y figure, dans la colonne `mcp` de `acceptance.criteria`. Treize outils publics hors
manifeste de convergence, c'est une question de gouvernance qui mérite sa propre décision ; elle n'est pas traitée ici.

### Preuves exécutables ajoutées

- `BoundedStdioServerTransportProviderFrameBoundTest#theOversizedResponseErrorDoesNotNameAToolTheTransportCannotKnow`
  — sans orientation, le transport ne nomme aucun outil et ne suggère aucune surface ;
  `aResponseLargerThanTheFrameBoundIsAnsweredWithAnErrorAndTheSessionSurvives` vérifie désormais que l'orientation
  fournie atteint le client, au lieu du nom d'outil.
- `BoundedStdioClientTransportTest#anOversizedOutboundRequestIsRefusedWithoutKillingThePeer` — refus nommé à
  l'émetteur, transport toujours `CONNECTED`, et un appel suivant servi par le **même** pair : la preuve qu'il est
  vivant, pas une supposition ; `anInboundFrameOverTheBoundStillFailsClosed` — non-régression de l'entrée.
- `MorpheusMcpServerOversizedResponseTest` — par le câblage réel de `MorpheusMcpServer.run`, une spécification
  hors borne est refusée avec l'orientation du serveur, sans fragment, et le code de sortie reste `0`.
- `MorpheusMcpToolServiceTest#aBoundedSliceOfSpecificationsIsReturnedForAnOffsetAndALimit` et
  `MorpheusMcpToolCatalogTest#theCurrentSpecificationToolDescribesWhatItActuallyReturns`.
- `AuditHardeningWorkflowContractTest#mcpClientTransportRefusesAnOversizedOutboundFrameInsteadOfFailingClosed` — la
  garde textuelle devient symétrique ; `mcpTransportNamesNoServerCatalogTool` interdit dans tout
  `morpheus-mcp-transport` un nom d'outil lu dans le catalogue. Toutes deux cassées avant acceptation, en
  réintroduisant `failClosed(` dans l'encodage client, en retirant la capture du refus, puis la constante
  `"find_requirements"` dans le transport serveur ; chaque violation a fait échouer sa règle, puis a été retirée.

## Amendement du 23 septembre 2026 (MCP-4) — un handler a une borne de sécurité, et la dépasser ferme la session

### 4. Le lecteur ne peut plus rester bloqué sur un handler

`BoundedStdioServerTransportProvider` traite les messages entrants **un par un**, dans la boucle qui lit `stdin` :
tant qu'un handler ne termine pas, le serveur ne lit plus rien, pas même une annulation. Jusqu'ici l'attente était
`future.get()` sans délai. Le risque était atténué — la fermeture annule le handler actif, et tout ce qui est câblé
est borné en amont (`MinosMcpCodeGateway` et `NexusMcpContextGateway` imposent un `requestTimeout`) — mais c'était
un contrat tenu par convention, que rien n'empêchait un futur outil de rompre.

L'attente est désormais bornée par `DEFAULT_HANDLER_DEADLINE` (deux minutes). C'est une **borne de sécurité**, pas
un délai de service : elle est choisie très au-dessus de tout handler sain, n'est pas réglable par le client, et
n'existe que pour qu'un défaut se voie. Elle se règle par le constructeur pour les tests, comme `maxFrameBytes`.

Au dépassement, le handler est annulé, la session **échoue fermé** avec une cause nommée
(`HandlerDeadlineExceededException`), la trace passe par `McpDiagnosticRedactor` comme tout échec de transport, et
`MorpheusMcpServer` rend `EXIT_TRANSPORT_FAILURE` : le superviseur l'apprend.

### Pourquoi échouer fermé ici, alors que §1 fait survivre la session à une réponse hors borne

Répondre une erreur au même `id` et reprendre la lecture semble plus aimable. C'est un piège :

- `future.cancel(true)` **ne garantit pas** que le travail s'arrête : il interrompt, et un handler qui ignore
  l'interruption continue. Le délai libère **le lecteur**, pas forcément l'ouvrier.
- Si la session continue, un handler zombie qui termine plus tard écrit **une seconde réponse pour le même `id`**.
  L'empêcher demanderait de retenir les `id` déjà répondus dans une structure bornée : soit de la mémoire non
  bornée, soit une fenêtre au-delà de laquelle le doublon repasse. On remplacerait un défaut par un plus discret.
- Un handler qui dépasse deux minutes a déjà rompu l'invariant de la boucle séquentielle. L'état de la session n'est
  plus celui qu'on croit ; continuer serait prétendre le contraire.

Ce n'est pas une incohérence avec §1. Là, la trame hors borne est un défaut **connu et contenu** : MORPHEUS sait
exactement ce qui a été refusé, rien ne tourne encore, et la substitution garde l'ordonnancement intact — la session
est réparable en vol. Ici, la session a perdu sa garantie d'ordonnancement et personne ne sait ce que fait le handler.
Deux situations différentes, deux réponses différentes.

### Preuves exécutables ajoutées

- `BoundedStdioServerTransportProviderHandlerDeadlineTest#aHandlerThatNeverCompletesDoesNotBlockTheReaderForever` —
  un handler qui ne rend jamais la main, borne réduite : la session se ferme, `terminatedInFailure()` est vrai et le
  handler est annulé. Rouge avant le changement (le lecteur restait bloqué au-delà des dix secondes d'attente du test).
- `aHandlerWithinItsDeadlineIsServedNormally` — non-régression, vert des deux côtés ;
  `theDeadlineMustBePositiveAndDefaultsToTheProductionBound`.
