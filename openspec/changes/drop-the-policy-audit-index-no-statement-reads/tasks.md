# Tâches : retirer l'index du journal d'audit des policies qu'aucune requête ne lit

Prérequis : le sprint 5 de l'audit du 17/11/2026 (`AUD-PRF-21` à `AUD-PRF-26`) est appliqué ; il modifie les mêmes
fichiers de `morpheus-store-sqlite`, dont `SqlitePolicyPackStore` (`AUD-PRF-22`).

## 1. Garde et reproduction

- [ ] 1.1 Extraire le compte du journal d'audit en constante de `SqlitePolicyPackStore` et ajouter à
  `SqliteQueryPlanGuardTest.GUARDED` la page (`AUDIT_PAGE_SQL`, recherche `idx_policy_audit_pack_order`) et le compte
  (D2) — exigence : `sqlite-index-coverage` › Le journal d'audit des policies n'est indexé que par sa clé d'ordre ;
  vérification : la garde passe sur le schéma actuel, et échoue en nommant la requête quand
  `idx_policy_audit_pack_order` est retiré d'une base de test (violation plantée consignée dans la PR, puis retirée)
- [ ] 1.2 Inverser l'assertion de `SqliteSchemaMigrationTest` sur `idx_policy_audit_pack` (`assertFalse`) — exigence :
  même exigence, scénario « Une base neuve ne porte pas l'index retiré » ; vérification : le test échoue avant 2.1
- [ ] 1.3 Écrire dans `SqlitePolicyAuditPageTest` un cas qui ouvre une base au niveau de schéma précédant la
  migration, avec un journal sur plusieurs packs, et relit les pages et le compte après l'ouverture (test à écrire) —
  exigence : même exigence, scénario « Une base mise à niveau perd l'index et garde son journal » ; vérification :
  l'absence d'index échoue avant 2.1, les pages et le compte sont identiques avant et après
- [ ] 1.4 Écrire un test d'insertion d'un enregistrement d'audit pour un pack absent sur une base migrée (test à
  écrire) — exigence : même exigence, scénario « Un enregistrement sans pack reste refusé » ; vérification : refus
  constaté par le message de contrainte, journal inchangé

## 2. Migration

- [ ] 2.1 Ajouter la migration `DROP INDEX idx_policy_audit_pack;` au numéro suivant la dernière migration présente,
  avec le commentaire de `V023`, l'entrée de `SqliteSchemaManager.MIGRATIONS` et l'avancée de
  `SUPPORTED_SCHEMA_VERSION` (D1) — exigence : même exigence ; vérification : tests de 1.2 à 1.4 verts,
  `ReleasedSchemaUpgradeTest` vert
- [ ] 2.2 Épingler l'empreinte canonique de la migration dans `SqliteMigrationChecksumGoldenTest` — exigence :
  `sqlite-schema-history` › Released migrations are pinned ; vérification : le test, rouge après 2.1, repasse
- [ ] 2.3 Vérifier que `SqliteIndexRedundancyTest` et les tests de parité de persistance passent sans modification —
  exigence : `sqlite-index-coverage` › Removing a repeated index MUST NOT change what the store accepts, refuses or
  returns ; vérification : `./mvnw test -pl morpheus-store-sqlite`

## 3. Documentation durable

- [ ] 3.1 Ajouter la migration et le schéma atteint au guide de mise à niveau le plus récent de `docs/user/` —
  exigence : `schema-upgrade-guidance` › The latest upgrade guide SHALL state the schema version the code supports ;
  vérification : `UpgradeGuideCoherenceTest`, `UserDocumentationSpeaksOfTheInstalledProductTest`
- [ ] 3.2 Mettre à jour `docs/developer/POLICY_PLATFORM.md` (migrations), `docs/developer/specifications/sqlite-index-coverage.md`
  (exigence, traçabilité) et `docs/developer/specifications/policy-audit-order.md` (index du journal) — vérification :
  `RepositoryDocumentationCoherenceTest`

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate drop-the-policy-audit-index-no-statement-reads --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice de traçabilité de `docs/developer/specifications/sqlite-index-coverage.md` (exigence →
  scénario → D1/D2 → tâche → test) — vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw clean verify` (le module SQLite, `UpgradeGuideCoherenceTest` et les tests de cohérence
  documentaire en font partie) — vérification : vert, comptes d'exécution non inférieurs aux clés de
  `config/m21-quality-ratchets.properties`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire `openspec/specs/sqlite-index-coverage/spec.md` et sa page durable.
