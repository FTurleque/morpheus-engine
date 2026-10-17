# Règles — Revue de code

Complète `code-supervisor` (global), qui porte déjà les seuils génériques, la sécurité courante, la duplication et le
nommage. Les règles du dépôt sont exécutables : un constat qui contredit un test d'architecture qui passe est un faux
positif. Ce fichier est lu en premier par le relecteur (`.claude/supervisor.config.json`, `convention_files`), qui n'en lit
que le début : il reste court.

## À faire respecter

- **Couches** : `domain` et `application` ne connaissent aucun adaptateur ; les adaptateurs sont frères. `api` et `mcp`
  n'atteignent un store ou un provider concret que par les racines nommées d'ADR-0109 : tout autre `new` ou import est
  CRITICAL.
- **Sans framework** : ni Spring/Guice, ni réflexion, ni classpath scanning, ni annotation d'injection.
- **Style** : records dans le domaine, validation dans le constructeur compact, un seul constructeur public par record
  (fabriques nommées), pas de commentaire « quoi » ni de ticket, pas de code mort ni de shim.
- **Déterminisme** : collection retournée ordonnée explicitement, jamais un `HashMap`/`HashSet` qui fixe un ordre
  observable, traversée bornée avec raison de troncature, `UNKNOWN` jamais converti en `BLOCKED`/`PASS`, aucun
  last-write-wins silencieux.

## Zones critiques — à vérifier en priorité

- **HTTP et remote** (`MorpheusHttpServer`, `MorpheusRemote*`) : loopback imposé en local, chemin lu par
  `MorpheusHttpPathParser` seul (jamais `getRequestURI().getPath()`), pas de CORS, pas de forward d'`Authorization`,
  méthode amont préservée, en-têtes posés par `applySecurityHeaders`.
- **Secrets et JSON** : aucun secret en dur, en log ou en flag CLI (mot de passe TLS par variable d'environnement
  seulement) ; jamais `activateDefaultTyping`, mappers Jackson stricts.
- **Plugins et processus externes** (`ProviderPlugin*`, `BoundedStdioClientTransport`) : pin SHA-256 fail-closed,
  `NOFOLLOW_LINKS`, environnement hérité réduit à une allowlist, lanceur Java en chemin absolu.
- **SQLite** : connexions par `SqliteConnectionScope`/`SqliteOperationRuntime`, rollback sur `Error`, parité
  `store-memory`/`store-sqlite`.
- **Surfaces publiques** : toute route, tout outil MCP ou toute commande CLI qui bouge exige, dans le même diff, la ligne
  de `contracts/public-surfaces.tsv` et l'OpenAPI borné (`additionalProperties: false`, `maxLength`).

## Build et gouvernance

- Aucun `<version>` dans un POM de module ; test de reproduction avant correctif.
- Jamais baisser un ratchet, affaiblir un test d'architecture, ni `@Disabled` sans ticket.
- Les `.ps1` restent en ASCII pur ; tout chiffre périssable renvoie à sa source vivante (`rules/meta.md`).
- Le superviseur ignore `.claude/` et ne lance pas Maven : la preuve d'un changement est
  `./mvnw test -pl morpheus-architecture-tests`.
