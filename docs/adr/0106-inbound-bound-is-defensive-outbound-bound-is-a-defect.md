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
