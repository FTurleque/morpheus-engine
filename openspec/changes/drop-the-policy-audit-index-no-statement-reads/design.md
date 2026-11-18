# Conception : retirer l'index du journal d'audit des policies qu'aucune requête ne lit

Conception allégée : une migration qui retire un index, sans changement de requête ni de surface. Motivation : voir
proposal.md, section Why.

## Contexte

- [EXISTANT VÉRIFIÉ] `V015__policy_packs.sql` crée `idx_policy_audit_pack ON policy_audit(pack_id, at, id)`.
  `V021__policy_audit_order_key.sql` ajoute la colonne `at_key` et `idx_policy_audit_pack_order ON policy_audit(pack_id,
  at_key, id)`, sans retirer l'ancien index.
- [EXISTANT VÉRIFIÉ] `SqlitePolicyPackStore` n'adresse `policy_audit` que par trois instructions : `AUDIT_PAGE_SQL`
  (`WHERE pack_id = ? ORDER BY at_key, id LIMIT ? OFFSET ?`), le compte écrit en ligne dans `listAudit`
  (`SELECT COUNT(*) FROM policy_audit WHERE pack_id = ?`) et l'insertion. Aucune ne lit `at` comme critère ni comme
  ordre.
- [EXISTANT VÉRIFIÉ] `V023__source_archive_order_key.sql` retire `idx_sync_source_archives_project_time` avec le
  commentaire « No statement searches the archives by the text of archived_at… » : même situation, même remède.
- [EXISTANT VÉRIFIÉ] `SqliteQueryPlanGuardTest.GUARDED` ne contient ni la page ni le compte du journal d'audit des
  policies ; seul `SqlitePolicyAuditPageTest#theQueryFollowsTheOrderIndexAndSortsNothing` lit le plan de la page.
  Le compte n'a pas de constante nommée, donc la garde ne peut pas le citer tel quel.
- [EXISTANT VÉRIFIÉ] `SqliteSchemaMigrationTest` (méthode de rejeu) affirme `indexExists(connection,
  "idx_policy_audit_pack")`.
- [EXISTANT VÉRIFIÉ] La clé étrangère `policy_audit.pack_id → policy_packs(id)` est vérifiée (`PRAGMA foreign_keys = ON`
  dans `SqliteDatabaseSecurity`) ; `idx_policy_audit_pack_order` a `pack_id` en tête et sert la recherche des enfants.
- [EXISTANT VÉRIFIÉ] Le sprint 5 de l'audit (`AUD-PRF-22`, lecture des overrides) modifie aussi `SqlitePolicyPackStore` ;
  ce changement s'applique après lui pour ne pas se recouvrir dans le même fichier.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `sqlite-index-coverage` | Le journal d'audit des policies n'est indexé que par sa clé d'ordre | D1, D2, Persistance, Stratégie de vérification |

## Composants et frontières

Sans objet : seuls une ressource de migration, `SqliteSchemaManager` (paquet `com.morpheus.store.sqlite.runtime`) et
une constante de `SqlitePolicyPackStore` (paquet `com.morpheus.store.sqlite.adapter`) changent ; aucune frontière.

## Modèle de domaine et invariants

Sans objet : aucun type du domaine ne change.

## Traitements et interactions

Sans objet : aucune requête ne change de texte, hors l'extraction du compte en constante (D2).

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet : aucune ligne de `contracts/public-surfaces.tsv` ni d'OpenAPI.

## Persistance et intégrité

- Migration ajoutée, au numéro qui suit la dernière migration présente lors de l'application (le nom de fichier suit
  le motif `V0NN__drop_unread_policy_audit_index.sql`, à confirmer à l'application). Contenu : un commentaire sur le
  patron de `V023`, puis `DROP INDEX idx_policy_audit_pack;` sans `IF EXISTS`, comme `V022` et `V023` le font : une base
  qui atteint ce script a rejoué celui qui crée l'index.
- `SqliteSchemaManager.MIGRATIONS` gagne l'entrée, et `SUPPORTED_SCHEMA_VERSION` avance d'un cran. Aucune valeur n'est
  recopiée ailleurs : les tests lisent la constante (`sqlite-schema-history` › Store tests derive the expected schema
  version from the declaring constant).
- Parité `store-memory` : sans objet, le store mémoire n'a pas d'index.

## Transactions, concurrence et idempotence

Sans objet : la migration s'exécute dans la transaction de migration existante (`SqliteSchemaManager.migrate`).

## Sécurité et isolation

Sans objet.

## Gestion des erreurs et reprise

Sans objet : une migration qui échoue laisse la base à son schéma de départ, avec la copie de pré-migration
(`sqlite-schema-history` › The store SHALL keep a verified copy of an existing database before advancing its schema).

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Le gain est un B-tree de moins maintenu à chaque écriture d'audit ; il n'est pas chiffré et ne justifie aucun budget.
La preuve exigée est négative : le plan de la page et du compte n'empire pas (scénario « La page et le compte restent
servis par l'index de la clé d'ordre »).

## Compatibilité, migration et retour arrière

- [EXISTANT VÉRIFIÉ] La version courante du réacteur porte déjà un schéma plus récent que la dernière version publiée
  (`morpheus-store-sqlite/src/test/resources/released-schema-levels.properties` déclare les versions publiées à leur
  niveau ; `docs/user/UPGRADE_1_2_2.md` décrit le passage au-delà du niveau de la 1.2.1). La migration entre donc dans
  la version ouverte, sans passage de version (`schema-upgrade-guidance` › The reactor version SHALL follow the schema
  past the last release).
- [PROPOSITION] Si la version courante a été publiée avant l'application de ce changement, la migration ouvre la
  version suivante et son guide, comme la règle de `.claude/rules/governance.md` l'exige ; c'est un lot indivisible
  hors de ce changement.
- Retour arrière : aucune migration descendante n'existe ; le guide le plus récent décrit le retour par la copie de
  pré-migration. Perdre l'index ne perd aucune donnée ; le recréer serait une migration de plus.

## Décisions

### D1 : retirer l'index par une migration, sans toucher aux requêtes
- **Nature** : [DÉCISION]
- **Exigences servies** : `sqlite-index-coverage` › Le journal d'audit des policies n'est indexé que par sa clé d'ordre
- **Choix** : une migration `DROP INDEX idx_policy_audit_pack`, commentée comme `V023`.
- **Alternatives écartées** : réécrire `V015` ou `V021` — refusé, une migration publiée est épinglée
  (`sqlite-schema-history` › Released migrations are pinned) ; garder l'index « au cas où » — il est maintenu à chaque
  écriture d'une table qui ne fait que croître, et rien ne le lit.
- **Compromis** : une version de schéma de plus à décrire dans le guide de mise à niveau.
- **ADR** : non concerné (filtre de `.claude/rules/adr.md` : retour arrière par une migration, aucune donnée ni
  contrat touchés).

### D2 : mettre la page et le compte du journal sous la garde de plan
- **Nature** : [DÉCISION]
- **Exigences servies** : même exigence, scénario « La page et le compte restent servis par l'index de la clé
  d'ordre »
- **Choix** : extraire le compte en constante de `SqlitePolicyPackStore` (`AUDIT_COUNT_SQL`, nom à confirmer, sur le
  patron de `SqliteChangeLifecycleMutationStore.AUDIT_COUNT_SQL`), puis ajouter deux entrées à
  `SqliteQueryPlanGuardTest.GUARDED` : la page, cherchant `idx_policy_audit_pack_order`, et le compte.
- **Alternatives écartées** : se fier à `SqlitePolicyAuditPageTest` seul — il ne voit pas le compte ; garder le compte
  en ligne et le recopier dans la garde — une copie ne suit pas un changement du store, alors que la garde explique le
  texte exécuté (`sqlite-index-coverage` › A guarded read MUST fail the build when its plan gains a table scan or a
  temporary sort).
- **Compromis** : une constante de plus, visible du paquet de test.
- **ADR** : non concerné.

### D3 : ne pas écrire de règle générale « index lu par aucune requête »
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune ; elle borne le périmètre.
- **Choix** : l'exigence nomme l'index retiré. `SqliteIndexRedundancyTest` garde sa règle sur les colonnes de tête.
- **Alternatives écartées** : une règle qui expliquerait toutes les instructions du store et refuserait un index
  qu'aucun plan ne cite — les instructions sont construites à l'exécution dans plusieurs stores (`pageSql`,
  `endpointSql`), un index peut ne servir qu'une clé étrangère, et la règle produirait des faux positifs à lister.
- **Compromis** : un prochain index orphelin ne sera vu que par une revue ou un audit.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit demande de « vérifier que `SqliteQueryPlanGuardTest` garde le plan de `AUD_PAGE_SQL` et du COUNT » : il ne les garde pas, et la constante s'appelle `AUDIT_PAGE_SQL` | `docs/project/audits/2026-11-17/findings.md`, `AUD-PRF-28`, action | `SqliteQueryPlanGuardTest.GUARDED` | D2 ajoute les deux entrées |
| Les numéros de ligne cités par l'audit (`V015…:70`, `SqliteSchemaMigrationTest.java:126`) concordent sur `1a539c22` | audit | arbre courant | aucun |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Le journal d'audit des policies n'est indexé que par sa clé d'ordre | Une base neuve ne porte pas l'index retiré | `SqliteSchemaMigrationTest` (méthode de rejeu), assertion inversée | à écrire |
| idem | Une base mise à niveau perd l'index et garde son journal | `ReleasedSchemaUpgradeTest` (base au niveau publié avec données) et un cas ajouté à `SqlitePolicyAuditPageTest` qui relit les pages après mise à niveau | existant (premier) · à écrire (second) |
| idem | La page et le compte restent servis par l'index de la clé d'ordre | `SqliteQueryPlanGuardTest`, deux entrées ajoutées ; preuve d'échec : retirer `idx_policy_audit_pack_order` dans une base de test et constater l'échec nommé | à écrire |
| idem | Un enregistrement sans pack reste refusé | test ajouté au module (insertion d'un audit pour un pack absent, refus constaté) | à écrire |

## Documentation durable et diagrammes à mettre à jour

- `docs/user/UPGRADE_1_2_2.md` (ou le guide le plus récent au moment de l'application) : migration listée, schéma
  atteint (`schema-upgrade-guidance` › The latest upgrade guide SHALL state the schema version the code supports).
- `docs/developer/POLICY_PLATFORM.md`, section des migrations : la migration ajoutée.
- `docs/developer/specifications/sqlite-index-coverage.md` : exigence, traçabilité.
- `docs/developer/specifications/policy-audit-order.md` : l'index de la clé d'ordre est le seul index du journal.

## Questions ouvertes

Aucune.
