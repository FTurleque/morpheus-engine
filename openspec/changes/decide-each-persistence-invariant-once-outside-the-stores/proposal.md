# Proposition : décider chaque invariant de persistance une seule fois, hors des stores (AUD-QUA-33)

## Why

Les règles qui ne dépendent pas du stockage — pas de révision exact, budgets par portée, monotonie d'une observation,
cohérence d'identité d'un remplacement, bornes temporelles d'une synchronisation — sont écrites deux fois, dans
`morpheus-store-memory` et dans `morpheus-store-sqlite`. Revérifié sur l'arbre courant (`1a539c22`) : 66 littéraux
d'au moins 25 caractères sont communs à `morpheus-store-memory/src/main/java/com/morpheus/store/memory/*.java` et
`morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/adapter/*.java`, comme l'audit l'a mesuré ; l'un d'eux est
un fragment de concaténation et non un message. Exemples :

- `policy update must advance revision and version by exactly one` : même triple condition dans
  `MemoryPolicyPackStore.java:98-101` et `SqlitePolicyPackStore.java:751-754` ;
- `policy scope exceeds active pack budget: ` : `MemoryPolicyPackStore.java:154`, `SqlitePolicyPackStore.java:335` ;
- `freshness observation must not move backwards` : `MemoryPortfolioStore.java:102`, `SqlitePortfolioStore.java:270` ;
- `completedAt must not be before attemptedAt` : `MemorySyncStateStore.java:95`, `SqliteSyncStateStore.java:225`.

La parité n'est tenue qu'après coup, par quatre `*PersistenceParityTest` (politiques, portfolio, vues enregistrées, pages
d'exigences) qui ne couvrent pas chaque branche ; toute évolution d'une règle se fait en double, et un store tiers
devrait la réécrire une troisième fois.

Preuve : constat `AUD-QUA-33` (Moyenne, effort L, sprint 10), `docs/project/audits/2026-11-17/findings.md`, mesuré par
l'audit du 17/11/2026 sur `a04f32bd` (commande `comm -12` des littéraux) et revérifié sur `1a539c22`.

## What Changes

- Chaque contrôle indépendant du stockage est écrit une fois, sur le type qu'il concerne ou dans l'application
  (design.md, D1), et appelé par les deux stores, dans l'ordre de contrôle qu'ils suivent aujourd'hui.
- Les stores ne gardent que ce que seul le stockage voit : existence d'une ligne, conflit de révision concurrent,
  collision d'identité ou d'unicité.
- **Gate de build** : un test d'architecture compare les messages de refus communs aux deux stores à une table
  déclarée de refus propres au stockage ; un message commun absent de la table fait échouer le build, et une liste de
  messages encore à remonter, qui ne fait que rétrécir, borne la dette restante (design.md, D2).
- Aucun message, aucun type d'exception, aucun ordre de contrôle, aucune surface publique ni aucun format persisté ne
  change.

## Capabilities

### New Capabilities
- `persistence-invariant-ownership` : où une règle de persistance indépendante du stockage est décidée, ce que les
  stores gardent, et le gate qui refuse sa réécriture dans un store.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** les neuf stores mémoire qui partagent un message avec leur jumeau SQLite (politiques, vues enregistrées,
portfolio, état de synchronisation, mutations de cycle de vie, références externes, traçabilité, contenu métier de
snapshot, connaissance de spécification), leurs jumeaux de `com.morpheus.store.sqlite.adapter`, les types de
l'application et du domaine qui reçoivent les contrôles, un test d'architecture, les tests de parité et une page
durable.

**Exclus :**
- les contrôles que seul le stockage peut faire (existence, CAS concurrent, collision d'unicité) : ils restent dans
  chaque store, déclarés dans la table du gate ;
- les messages de moins de 25 caractères et les messages propres à un seul store : le gate mesure ce que la preuve de
  l'audit mesure ;
- toute modification du texte d'un refus : il est relayé par les surfaces et lu par les tests de parité existants.

## Acteurs et surfaces concernés

Le mainteneur qui fait évoluer une règle de persistance ; l'auteur d'un store tiers ; la CI, par le verdict du gate.
Aucune surface CLI, MCP ou HTTP ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| 66 littéraux ≥ 25 caractères communs aux deux stores | existant vérifié | `comm -12` relancé sur `1a539c22` ; un littéral est un fragment de concaténation |
| Répartition : connaissance de spécification 25, politiques 13, vues 7, cycle de vie 6, contenu métier 4 (+1 partagé), synchronisation 4, portfolio 2, références externes 2, traçabilité 2 | existant vérifié | store mémoire de chaque littéral commun, relevé le 17/11/2026 |
| `PolicyPack` et `PolicyBudgets` vivent dans l'application, `PortfolioFreshness` dans le domaine | existant vérifié | `com.morpheus.application.policy`, `com.morpheus.domain.portfolio` |
| La parité est tenue par quatre tests a posteriori | existant vérifié | `*PersistenceParityTest` du dépôt |
| Contrôles portés par le type concerné, appelés par les deux stores | proposition à valider | Q1, design.md D1 |
| Gate : table de faits des refus propres au stockage et liste de dette | proposition à valider | Q2, design.md D2 |
| Pas d'ADR | proposition à valider | Q4, design.md D4 |

## Impact

- Modules : `morpheus-domain`, `morpheus-application`, `morpheus-store-memory`, `morpheus-store-sqlite`,
  `morpheus-architecture-tests`.
- Tests d'architecture : un test ajouté (justification de PR). Si le mainteneur range la liste de dette parmi les
  compteurs de `config/debt-counter-ratchets.properties` (Q2), ce fichier de gouvernance change aussi.
- Règles du dépôt : `.claude/rules/architecture.md` (une ligne sur la propriété des invariants de persistance) et
  `.claude/rules/code-review.md` (zone SQLite) si Q2 est retenue telle quelle.
- ADR : aucun proposé (Q4) ; la règle est tenue par le test.
- Persistance : aucune migration ; la parité `store-memory` / `store-sqlite` est inchangée, et prouvée plus largement.
- Couverture : chaque contrôle remonté doit être testé dans son module (`morpheus-application` ou `morpheus-domain`),
  pas seulement par les tests de parité — sinon le crédit emprunté de ce module monte
  (`BorrowedCoverageGateTest`) ; les seuils se lisent dans `config/m21-per-module-coverage-floors.properties`.
- Dépendances entre constats : aucune (`depends_on` et `bloque` vides). Le change
  `publish-a-project-through-the-provider-it-was-registered-with` du même sprint touche aussi
  `MemorySpecificationKnowledgeStore` et `SqliteSpecificationKnowledgeStore` (`putProject`) : le lot « connaissance de
  spécification » de ce change se mène avant ou après lui, jamais en parallèle.

## Questions ouvertes

- **Q1 (mainteneur)** : où vit un contrôle remonté — méthode du type concerné (proposé), classe de garde de
  l'application par famille, ou contrôle dans le service applicatif avant l'appel au store ?
- **Q2 (mainteneur)** : quelle forme pour le gate — table de faits des refus propres au stockage plus liste de dette qui
  rétrécit (proposé), compteur seul, ou aucune règle exécutable (revue) ? La liste de dette entre-t-elle dans
  `config/debt-counter-ratchets.properties` ?
- **Q3 (mainteneur)** : la frontière entre « indépendant du stockage » et « propre au stockage » proposée dans design.md
  (D3) est-elle la bonne, notamment pour « belongs to another project » et « must be active before adding an
  override », qui demandent une lecture préalable ?
- **Q4 (mainteneur)** : la règle relève-t-elle d'un ADR (proposé : non, retour arrière sans coût de données ni de
  contrat) ?
