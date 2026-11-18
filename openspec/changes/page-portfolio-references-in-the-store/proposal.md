# Proposition : paginer les références d'un portfolio dans le store (AUD-PRF-25)

## Why

Toute lecture des références inter-projets d'un portfolio charge la table entière du portfolio, puis découpe, filtre
ou compte en mémoire. `PortfolioQueryService.references` pagine `store.listReferences(portfolioId)` par
`List.subList` ; `projectReferences` filtre la même liste en Java sur la source ou la cible ; `overview` et `conflicts`
la relisent pour un compte et un regroupement ; `PortfolioRegistryService.addReference` la relit à **chaque ajout** pour
dédoublonner, ce qui rend O(n²) le remplissage d'un portfolio de n références ; `QueryExecutionService` matérialise toute
la table avant de refuser au-delà du budget de lignes source, alors que les requirements sont lus avec
`QueryBudgets.MAX_SOURCE_ROWS + 1`. La table est en ajout seul, sans borne d'écriture : une page de 500 coûte la
lecture et le tri de toutes les références du portfolio.

Preuve : constat `AUD-PRF-25` (Moyenne, sprint 6) de `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` (plan de requête : `SEARCH … USING INDEX idx_portfolio_reference_target (portfolio_id=?)` puis
`USE TEMP B-TREE FOR ORDER BY`) et revérifié le 17/11/2026 sur `1a539c22` : `PortfolioQueryService.java` lignes 34,
50, 62 et 72, `PortfolioRegistryService.java` ligne 120, `QueryExecutionService.java` lignes 135-136,
`SqlitePortfolioStore.listReferences` (`WHERE portfolio_id = ? ORDER BY id`). Même motif que `AUD-PRF-05` de l'audit
du 09/10/2026, corrigé pour les requirements (`requirement-page-reads`) mais pas ici.

## What Changes

- Le port `PortfolioStore` offre : une page de références d'un portfolio (décalage, limite, ordre déclaré et total),
  la même page restreinte à un projet (source ou cible), le nombre de références, les seules références des groupes en
  conflit, et la recherche d'une observation identique pour le dédoublonnage. `listReferences` disparaît des chemins de
  production.
- Les services du portfolio et la requête DSL de portée portfolio passent par ces lectures ; la requête DSL lit au plus
  `QueryBudgets.MAX_SOURCE_ROWS + 1` références et garde son refus `QUERY_SOURCE_BUDGET_EXCEEDED`.
- **Schéma SQLite** : une migration ajoute l'index qui sert la page dans son ordre ; la version de schéma supportée
  avance d'un cran.
- **Ordre visible** (décision du mainteneur, design.md D1) : une page de références suit un ordre déclaré et total,
  identique dans les deux stores. Aujourd'hui le store SQLite rend l'ordre d'identité en portée portfolio, le store
  mémoire l'ordre sémantique, et la portée projet l'ordre sémantique dans les deux. La recommandation est l'ordre
  d'identité partout, ce qui change l'ordre de la portée projet sur les trois transports.
- Aucune route, aucun outil, aucune commande, aucun paramètre ni aucune borne ne change ; l'OpenAPI ne décrit pas
  l'ordre aujourd'hui, et une phrase de description l'énonce.

## Capabilities

### New Capabilities
- `portfolio-reference-reads` : comment les références inter-projets d'un portfolio sont lues — page découpée par le
  store, ordre déclaré, compte, conflits, dédoublonnage d'un ajout, lecture de la requête DSL, parité des stores.

### Modified Capabilities

Aucune.

## Périmètre et exclusions

**Inclus :** `PortfolioStore` (paquet `com.morpheus.application.store`), `PortfolioQueryService` et
`PortfolioRegistryService` (paquet `com.morpheus.application.portfolio`), `QueryExecutionService` (paquet
`com.morpheus.application.query.dsl`), `MemoryPortfolioStore`, `SqlitePortfolioStore`, une migration de
`morpheus-store-sqlite`, leurs tests, la garde de plan de requête, la description de la route de références dans
`docs/developer/reference/openapi/morpheus-v1-portfolio-m23.yaml` (texte seulement), le guide de mise à niveau le plus
récent et la page durable de la capacité nouvelle.

**Exclus :**
- une contrainte `UNIQUE` sur l'observation : une base existante peut porter des doublons écrits par deux écrivains
  concurrents, et la migration échouerait (design.md, D4) ;
- la traversée du portfolio (`outgoing`, `incoming`), déjà filtrée en SQL ;
- les listes des portfolios, des appartenances et de la fraîcheur, paginées en mémoire mais bornées par des tables
  qui ne croissent pas avec les références ;
- le budget M19 de la page de références : porté par le changement `budget-orchestration-quality-policy-and-portfolio-reads`
  (`AUD-PRF-30`), qui doit précéder celui-ci.

## Acteurs et surfaces concernés

Client HTTP local et distant, client MCP et utilisateur de la CLI qui listent ou ajoutent des références de portfolio
(`portfolio.references`, `portfolio.reference-add`, `portfolio.overview`, `portfolio.conflicts` de
`contracts/public-surfaces.tsv`), et auteur d'une requête DSL de portée portfolio (`query.execute`).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Toute lecture de références charge la table du portfolio | existant vérifié | `PortfolioQueryService`, `PortfolioRegistryService.addReference`, `QueryExecutionService` (portée portfolio) |
| Le tri SQL de la page passe par un B-tree temporaire | existant vérifié | audit du 17/11/2026, plan de `SqlitePortfolioStore.listReferences` |
| Les deux stores ne rendent pas `listReferences` dans le même ordre | existant vérifié | `SqlitePortfolioStore.listReferences` (`ORDER BY id`) ; `MemoryPortfolioStore.listReferences` (`.sorted()`, ordre de `CrossProjectReference.compareTo` : clé sémantique, provider, identité) |
| L'ordre sémantique ne se reproduit pas par un tri SQL colonne à colonne | existant vérifié | `CrossProjectReference.semanticKey` concatène des textes de longueur variable séparés par `|` |
| Ordre d'identité pour toute page de références | question ouverte | design.md, D1 |
| Le budget M19 précède la correction | existant vérifié | `performance-budget-coverage` › A cost a correction claims to bound SHALL have a budget declared before the correction ; changement `budget-orchestration-quality-policy-and-portfolio-reads` |

## Impact

- Modules : `morpheus-application`, `morpheus-store-memory`, `morpheus-store-sqlite` ; tests de ces modules, de
  `morpheus-api` (routes de portfolio) et des contrats M23/M24 de `morpheus-architecture-tests`.
- Schéma persisté : un index ajouté ; `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION` avance, à répercuter dans le
  guide de mise à niveau le plus récent (`schema-upgrade-guidance`).
- Contrats : aucune ligne de `contracts/public-surfaces.tsv` ; une phrase de description dans l'OpenAPI M23 (fichier de
  gouvernance : justification dans la PR).
- Dépendances : `budget-orchestration-quality-policy-and-portfolio-reads` (`AUD-PRF-30`) déclare et mesure le budget
  avant ce changement. Ce changement suit le sprint 5 de l'audit (`AUD-PRF-21` à `AUD-PRF-26`), qui modifie les mêmes
  fichiers de `morpheus-store-sqlite`. `drop-the-policy-audit-index-no-statement-reads`, du même sprint, ajoute aussi une
  migration : le numéro est celui qui suit la dernière migration présente au moment de l'application.
- Aucun ADR (design.md : chaque décision est réversible sans perte de données ni de contrat).

## Questions ouvertes

- Ordre d'une page de références (design.md, D1) : ordre d'identité partout (recommandé) ou maintien de l'ordre
  sémantique, qui exige une clé d'ordre persistée. À trancher par le mainteneur avant l'application.
- Faut-il un changement à part pour les doublons qu'un ajout concurrent peut créer (design.md, D4) ?
