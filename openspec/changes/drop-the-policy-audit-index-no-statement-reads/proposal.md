# Proposition : retirer l'index du journal d'audit des policies qu'aucune requête ne lit (AUD-PRF-28)

## Why

L'index `idx_policy_audit_pack (pack_id, at, id)`, créé par `V015__policy_packs.sql`, n'est plus lu par aucune requête
depuis que `V021__policy_audit_order_key.sql` a créé `idx_policy_audit_pack_order (pack_id, at_key, id)` : la page du
journal (`SqlitePolicyPackStore.AUDIT_PAGE_SQL`) trie `ORDER BY at_key, id` et le compte filtre sur `pack_id` seul, deux
lectures que le nouvel index sert. L'ancien reste pourtant maintenu à chaque écriture d'une table en ajout seul, alors
que `V023__source_archive_order_key.sql` retire son jumeau des archives de synchronisation pour exactement cette raison.

Preuve : constat `AUD-PRF-28` (Faible, sprint 6) de `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22` : aucune requête de production ne filtre ni ne trie
sur `policy_audit.at` ; `SqliteSchemaMigrationTest` épingle l'existence de l'index (`assertTrue(indexExists(connection,
"idx_policy_audit_pack"))`) ; `SqliteIndexRedundancyTest` ne le voit pas, parce que ses colonnes ne sont pas le début
de celles d'un autre index.

## What Changes

- **Schéma SQLite** : une migration ajoutée retire `idx_policy_audit_pack`, avec le commentaire qui dit pourquoi, sur le
  patron de `V023`. Aucune donnée ne change ; la version de schéma supportée avance d'un cran.
- **Tests du store** : l'assertion de `SqliteSchemaMigrationTest` s'inverse ; la page et le compte du journal entrent
  sous la garde de plan de requête (`SqliteQueryPlanGuardTest`), qui ne les garde pas aujourd'hui ; l'empreinte de la
  migration est épinglée (`SqliteMigrationChecksumGoldenTest`).
- **Documentation** : le guide de mise à niveau le plus récent nomme la nouvelle migration et le schéma atteint ; les
  pages développeur qui listent les index du journal d'audit suivent.
- Aucune surface publique, aucun format de réponse, aucune requête du store ne change.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `sqlite-index-coverage` : exigence ajoutée — le journal d'audit des policies n'est indexé que par la clé d'ordre, et
  sa page comme son compte restent servis par cet index.

## Périmètre et exclusions

**Inclus :** une migration de `morpheus-store-sqlite`, `SqliteSchemaManager` (liste des migrations et version
supportée), `SqliteSchemaMigrationTest`, `SqliteQueryPlanGuardTest`, `SqliteMigrationChecksumGoldenTest`, le guide de
mise à niveau le plus récent de `docs/user/`, `docs/developer/POLICY_PLATFORM.md`, les pages durables
`docs/developer/specifications/sqlite-index-coverage.md` et `policy-audit-order.md`.

**Exclus :**
- une règle générale qui refuserait tout index qu'aucune requête ne lit : aucune mesure ne la rend décidable sur le
  schéma seul (design.md, D3) ;
- les autres index du schéma : aucun autre n'est relevé par l'audit ;
- le passage de version du produit : la version courante du réacteur porte déjà un schéma plus récent que la
  dernière version publiée (design.md, Compatibilité).

## Acteurs et surfaces concernés

L'opérateur qui met à niveau sa base : la première ouverture applique la migration. Aucun client CLI, MCP ou HTTP ne
voit de différence de réponse.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucune requête de production ne lit `policy_audit.at` | existant vérifié | `grep -rn "policy_audit" morpheus-store-sqlite/src/main/java` : `AUDIT_PAGE_SQL` (`ORDER BY at_key, id`), le compte `WHERE pack_id = ?`, l'insertion |
| `V023` retire son jumeau pour la même raison | existant vérifié | `V023__source_archive_order_key.sql`, dernier commentaire et `DROP INDEX idx_sync_source_archives_project_time` |
| La page et le compte du journal ne sont pas sous `SqliteQueryPlanGuardTest` | existant vérifié | liste `GUARDED` du test ; seul `SqlitePolicyAuditPageTest#theQueryFollowsTheOrderIndexAndSortsNothing` lit le plan de la page |
| Une migration entre dans la version courante sans en ouvrir une nouvelle | existant vérifié | `released-schema-levels.properties` (dernière version publiée déclarée à son niveau), `docs/user/UPGRADE_1_2_2.md` (schéma au-delà de la 1.2.1) |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : un index retiré est réversible par une migration, sans contrat ni donnée touchés |

## Impact

- `morpheus-store-sqlite` : une ressource `db/migration/V0NN__*.sql` ajoutée, `SqliteSchemaManager`, trois tests du
  module.
- Schéma persisté : un index de moins ; la constante `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION` avance, ce que
  `schema-upgrade-guidance` › The latest upgrade guide SHALL state the schema version the code supports exige de
  répercuter dans le guide.
- Ordre du sprint 6 : ce changement suit le sprint 5 de l'audit (`AUD-PRF-21` à `AUD-PRF-26`), qui modifie les mêmes
  fichiers de `morpheus-store-sqlite` — `AUD-PRF-22` touche `SqlitePolicyPackStore`. Le changement
  `page-portfolio-references-in-the-store`, du même sprint, ajoute aussi une migration : le numéro est celui qui suit
  la dernière migration présente au moment de l'application.
- Aucun test d'architecture, aucune ligne de `contracts/public-surfaces.tsv`, aucun OpenAPI.

## Questions ouvertes

Aucune.
