# Proposition : vérifier le schéma SQLite sans relire ses migrations (AUD-PRF-27)

## Why

Chaque opération — requête HTTP, appel d'outil MCP, commande CLI — ouvre une portée de connexion et vérifie le schéma
une fois (ARCH-0162). Cette vérification relit et rehache, à chaque fois, toutes les ressources de migration depuis le
classpath : `SqliteSchemaManager.applyMigration` charge le texte canonique de la migration, puis `accepts` le recharge
et calcule son SHA-256 (et celui de sa variante CRLF quand le premier ne concorde pas), et une requête
`SELECT … FROM schema_migrations WHERE version = ?` part pour chaque migration. Le résultat est constant pendant toute
la vie du processus : seul le registre de la base peut changer.

Preuve : constat `AUD-PRF-27` (Faible, sprint 6) de `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` (24 ressources, 34 073 octets) et revérifié le 17/11/2026 sur `1a539c22` :
`SqliteSchemaManager.migrate` boucle sur `MIGRATIONS` ; `applyMigration` appelle `canonicalScript` et
`findAppliedMigration` ; `accepts` rappelle `canonicalScript` et `TextDigest.sha256` ; `loadScript` lit la ressource par
`getResourceAsStream(...).readAllBytes()`.

## What Changes

- Le texte canonique et les deux empreintes admises de chaque migration sont calculés une fois par processus, dans une
  valeur immuable, au premier besoin.
- La vérification d'une opération lit le registre `schema_migrations` en une requête, puis le compare à cette valeur ;
  les migrations absentes du registre s'appliquent comme aujourd'hui.
- Le refus d'un registre qui porte une version future, une empreinte inconnue ou un nom inconnu reste évalué à chaque
  opération, avec les mêmes messages. La vérification de la restauration hors ligne (`SqliteServerMaintenance`, qui
  appelle `accepts`) lit la même valeur.
- Aucun schéma, aucune migration, aucune surface publique ne change.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `sqlite-schema-history` : deux exigences ajoutées — la vérification d'une opération lit le registre en une requête
  sans relire les migrations, et elle refuse toujours, à chaque opération, un registre que le programme ne reconnaît
  pas.

## Périmètre et exclusions

**Inclus :** `SqliteSchemaManager` (paquet `com.morpheus.store.sqlite.runtime`), ses tests du module
`morpheus-store-sqlite`, la page durable `docs/developer/specifications/sqlite-schema-history.md`.

**Exclus :**
- la règle « une vérification de schéma par opération » (ARCH-0162, `sqlite-connection-policy`) : elle reste ; seul son
  coût baisse ;
- un cache du **verdict** entre deux opérations : un registre peut changer entre deux opérations d'un même processus
  (restauration hors ligne, autre programme), et le refus doit le voir (design.md, D1) ;
- un budget M19 de la vérification : coût de l'ordre de la milliseconde, non mesuré, sans exigence chiffrée
  (design.md, Performance).

## Acteurs et surfaces concernés

Aucun acteur externe ne voit de différence : CLI, MCP et HTTP gardent leurs réponses et leurs refus.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La vérification relit et rehache chaque ressource à chaque opération | existant vérifié | `SqliteSchemaManager` : `migrate`, `applyMigration`, `accepts`, `loadScript` |
| Une requête par migration sur `schema_migrations` | existant vérifié | `SqliteSchemaManager.findAppliedMigration`, appelée dans la boucle de `migrate` |
| Une vérification par opération est décidée | existant vérifié | ARCH-0162 ; `sqlite-connection-policy` › An operation SHALL open one physical connection whichever transport carries it |
| Le registre peut changer entre deux opérations d'un même processus | existant vérifié | restauration hors ligne (`SqliteServerMaintenance`), base partagée entre processus |
| Mémoriser les empreintes, pas le verdict | décision | design.md, D1 |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : optimisation interne, aucune décision durable ni contrat |

## Impact

- `morpheus-store-sqlite` : `SqliteSchemaManager` et ses tests. Aucun test d'architecture, aucun fichier de
  gouvernance.
- Ordre du sprint 6 : ce changement suit le sprint 5 de l'audit (`AUD-PRF-21` à `AUD-PRF-26`), qui modifie les mêmes
  fichiers de `morpheus-store-sqlite`. Les changements `drop-the-policy-audit-index-no-statement-reads` et
  `page-portfolio-references-in-the-store`, du même sprint, ajoutent chacun une migration à `SqliteSchemaManager` :
  l'ordre d'application entre eux est libre, le dernier appliqué rebase sa liste `MIGRATIONS`.

## Questions ouvertes

Aucune.
