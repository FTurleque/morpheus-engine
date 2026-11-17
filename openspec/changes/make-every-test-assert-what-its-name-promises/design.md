# Conception : faire vérifier à chaque test ce que son nom promet

## Contexte

- [EXISTANT VÉRIFIÉ] `MorpheusMcpServer.build(Path, InputStream, OutputStream)` rend un `McpSyncServer` ou lève ; le
  commentaire des tests MCP dit que la construction valide les schémas, l'unicité des noms et l'enregistrement
  complet. La présence des outils portfolio est vérifiée par le test voisin de `MorpheusPortfolioMcpToolsTest`
  (lignes 30-36, sur `MorpheusMcpServer.toolSpecifications`).
- [EXISTANT VÉRIFIÉ] `LoopbackRequestProtectedHttpServerTest#delegatesLifecycleAndProtectsDirectAndLateBoundContexts`
  crée un contexte avec un gestionnaire, puis teste chemin, serveur, attributs, filtres et authentificateur ; seule la
  ligne 44 est tautologique.
- [EXISTANT VÉRIFIÉ] `BoundedStdioClientTransportTest` a un pair de fixture qui écrit sur sa sortie d'erreur
  (`FixtureStderrChattyMcpServer`), utilisé par le test suivant pour un gestionnaire qui lève.
- [EXISTANT VÉRIFIÉ] `RefusalTestEvidenceArchitectureTest` ne vise que `assertThrows` ; aucune règle ne vise
  `assertTrue(… != null)`.

## Exigences couvertes

Sans objet : `skip_specs: true`, aucune exigence ne change. Les tests touchés servent des exigences existantes sans
les modifier.

## Composants et frontières

Sans objet : tests seulement, dans leur module.

## Modèle de domaine et invariants

Sans objet.

## Traitements et interactions

Sans objet.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet.

## Persistance et intégrité

Sans objet.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Sans objet.

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Le test de gestionnaire de sortie d'erreur démarre un vrai pair : il reprend le budget de démarrage du test voisin
(`PEER_START_UP_BUDGET`), sans budget nouveau.

## Compatibilité, migration et retour arrière

Le nombre de méthodes de test est inchangé ; seul un nom change (test portfolio).

## Décisions

### D1 : les tests MCP prouvent l'absence de collision, par leur nom et par `assertDoesNotThrow`
- **Nature** : [DÉCISION]
- **Choix** : `assertDoesNotThrow(() -> MorpheusMcpServer.build(...), "<ce que la construction valide>")`, serveur
  fermé ensuite ; `serverCatalogContainsPortfolioTools` devient `serverBuildsWithThePortfolioToolsWithoutCollision`.
- **Alternatives écartées** : asserter le catalogue servi dans chaque test — le serveur du SDK n'est pas interrogé
  sans client, et `toolSpecifications` est déjà asserté par les tests voisins ; supprimer les tests — ferait baisser
  `testsMinimum` et retirerait la preuve que la construction complète réussit.
- **Compromis** : la preuve reste « la construction réussit », mais elle est nommée comme telle.
- **ADR** : non concerné.

### D2 : le gestionnaire du contexte direct
- **Nature** : [PROPOSITION]
- **Choix** : remplacer la ligne 44 par une assertion sur l'identité du gestionnaire : `assertNotSame` avec le
  gestionnaire passé si le serveur protégé l'enveloppe, `assertSame` sinon — à fixer en relisant
  `LoopbackRequestProtectedHttpServer` à l'application ; à défaut, supprimer la ligne, la protection étant prouvée
  par le reste du test.
- **Alternatives écartées** : garder `assertNotNull` — même tautologie.
- **Compromis** : aucun.
- **ADR** : non concerné.

### D3 : pas de règle de build
- **Nature** : [DÉCISION]
- **Choix** : corriger les sept sites, sans interdit textuel de `assertTrue(… != null)`.
- **Alternatives écartées** : une règle sur toutes les sources de test — `assertTrue(x != null)` sur une valeur qui peut
  être nulle est légitime ; la règle ne distinguerait pas la tautologie, comme `RefusalTestEvidenceArchitectureTest`
  ne prouve qu'une assertion a pu être écrite.
- **Compromis** : une nouvelle tautologie reste possible ; la revue la lit.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le nom du test portfolio promet ce qu'il ne vérifie pas | `MorpheusPortfolioMcpToolsTest.java:39` | son corps | D1 |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| (aucune) | construction sans collision | cinq tests MCP réécrits, verts | à écrire |
| (aucune) | gestionnaire du contexte direct | `LoopbackRequestProtectedHttpServerTest` vert | à écrire |
| (aucune) | gestionnaire de sortie d'erreur reçoit une ligne | `BoundedStdioClientTransportTest#acceptsCustomStderrHandler` vert, rouge si `setStdErrorHandler` est ignoré | à écrire |

## Documentation durable et diagrammes à mettre à jour

Aucune : aucune capacité ne change.

## Questions ouvertes

Aucune.
