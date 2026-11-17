---
id: DATA-0018
title: "Utiliser SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore`"
domain: data
rules: true
files: ["morpheus-application/pom.xml", "morpheus-domain/pom.xml"]
---

# Utiliser SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore`

- Statut : **Acceptée avec contraintes**
- Date : 2026-07-22

## Context

MORPHEUS a besoin d'une persistance locale pour les snapshots, les spécifications normalisées, les identités, les
versions, les changements, les relations de traçabilité, les diagnostics et les métadonnées utiles. Le domaine ne doit
pas être couplé à un produit de stockage (ADR-0003).

Trois options sont en présence : un store mémoire, SQLite comme candidat persistant, et une graph database.

SQLite couvre les besoins du store : création locale sans service, persistance après redémarrage,
transactions, activation atomique de snapshots, rejet d'un prédécesseur obsolète, idempotence, comparaison de
snapshots, requêtes simples, persistance des relations de traçabilité, traversée directe et inverse à profondeur 3,
conservation des références non résolues. Aucun besoin mesuré n'impose une graph database : une traversée de
profondeur 3 sur un corpus synthétique de dizaines de milliers d'arêtes reste très peu coûteuse avec SQLite et des
index adaptés.

Pourquoi SQLite :

- **local-first** : un fichier local suffit, aucun serveur de base n'est requis ;
- **transactions** : les primitives nécessaires à une activation cohérente des snapshots ;
- **portabilité** : le driver Java fournit des binaires pour Windows, Linux et macOS ;
- **distribution** : ni Docker ni service séparé ;
- **reconstructibilité** : le store reste une projection reconstruisible depuis les sources ;
- **requêtes de graphe bornées** : les traversées nécessaires restent compatibles avec une modélisation relationnelle
  indexée.

Une graph database introduirait un service supplémentaire, son installation et sa configuration, un lifecycle
opérationnel, de nouvelles dépendances et de nouveaux problèmes de distribution Windows, sans valeur mesurée.

Alternatives écartées :

- **PostgreSQL** : non retenu pour le backend local ; excellent en serveur mais disproportionné pour le mode
  local-first ; pourrait devenir un adaptateur d'un mode serveur multi-utilisateur.
- **FalkorDB, Neo4j ou autre graph database** : non retenues ; à réévaluer uniquement sur limites mesurées.
- **Fichiers JSON seuls** : rejetés comme store principal, insuffisants pour les transactions, l'indexation, les
  requêtes structurées et les migrations.
- **Store mémoire seul** : rejeté comme backend utilisateur, conservé comme implémentation de référence et de test.

L'absence de second backend persistant est un choix, pas une dette. La répartition qui en résulte :

```text
Graph model = yes
Graph database mandatory = no

Persistent backend = SQLite
Memory backend     = contract test / reference
Dedicated graph store = not required
Storage boundary   = SpecificationKnowledgeStore
```

## Decision

SQLite est le **backend persistant de production** derrière `SpecificationKnowledgeStore` et les ports de
persistance de `morpheus-application`, par l'adaptateur JDBC `morpheus-store-sqlite` et le driver
`org.xerial:sqlite-jdbc`, version verrouillée dans le `pom.xml` racine (`sqlite-jdbc.version`), remplaçable sans
toucher au domaine.

1. **Frontière** : SQLite n'apparaît ni dans le domaine, ni dans les services applicatifs, ni dans les DTO publics,
   ni dans les contrats MCP et API ; les stores concrets sont construits par les racines de composition (ADR-0162).
   Le modèle est un graphe, sans graph database.
2. **Schéma** : pas de payload JSON monolithique ; au minimum projets, snapshots, spécifications, requirements,
   changements, contraintes, scénarios, décisions de conception, critères d'acceptation, tâches, liens de
   traçabilité, références externes, provenance et preuves.
3. **Migrations** : schéma versionné et version stockée, migration explicite testée sur copie, rollback ou
   restauration documentés, données dérivées reconstructibles depuis les sources (mécanisme : ADR-0021 ; copie avant
   migration : ADR-0110).
4. **Invariants**, outre ceux d'ADR-0021 et d'ADR-0003 : snapshot actif atomique au niveau observable ;
   `DomainIdentity` conservée exactement ; liens de traçabilité liés au snapshot ; références non résolues
   conservées ; aucun réseau requis ; un graph store reste réévaluable sans refonte du domaine.
5. **Un seul backend persistant** tant qu'aucun critère de réouverture n'est rempli : volume ou latence hors
   objectifs, concurrence multi-utilisateur prioritaire, traversées complexes dominantes, distribution du driver
   problématique, serveur central devenu le déploiement principal. `morpheus-store-memory` est un store de contrat et
   de test, pas un backend en attente de promotion.

## Do's and Don'ts

### Do

- Accéder à la persistance par les ports de `morpheus-application`.
- Verrouiller la version du driver dans le `pom.xml` racine.
- Versionner le schéma et appliquer des migrations explicites, testées sur copie.
- Garder le store reconstructible depuis les sources.
- Garder `morpheus-store-memory` comme implémentation de référence et de test.

### Don't

- Ne pas exposer SQLite, JDBC ou SQL dans le domaine, les services applicatifs, les DTO publics ou les contrats MCP
  et API.
- Ne pas imposer de graph database ni de serveur de base.
- Ne pas promettre un usage multi-writer serveur avec ce backend.
- Ne pas engager un second backend persistant sans qu'un critère de réouverture soit rempli et sans ADR dédiée.
- Ne pas stocker un payload JSON monolithique comme schéma de production.

## Consequences

### Positives

- Installation minimale, aucun service externe, coût opérationnel faible.
- Très bon fit local-first, Windows et Linux.
- Transactions, requêtes SQL et index.
- Fichier sauvegardable.
- Backend mémoire disponible pour les tests.

### Négatives

- Moins adapté à une forte concurrence multi-writer.
- Certaines traversées complexes seraient plus naturelles en graphe.
- Schéma relationnel à concevoir et à migrer.
- Driver JNI natif.
- Migration éventuelle si MORPHEUS devient un service collaboratif centralisé.

### Risques

- **Concurrence future** : ne pas promettre d'usage multi-writer serveur ; conserver le port de stockage.
- **Traversées complexes futures** : benchmarks et adaptateur graphe spécialisé si nécessaire.
- **Corruption du fichier local** : transactions, sauvegarde, reconstruction depuis les sources, vérifications
  d'intégrité.
- **Driver natif** : tests Windows et Linux, version explicitement verrouillée.

## Compliance and Enforcement

- Règle Archgate `DATA-0018-sqlite-initial-persistent-store.rules.ts` : `inner-layers-do-not-declare-sqlite`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : `domain` et
  `application` ne dépendent d'aucun paquet `com.morpheus.store..`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java` :
  dans `api` et `mcp`, seules les racines de composition nommées atteignent un store concret.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java` :
  version de `sqlite-jdbc` épinglée dans le `pom.xml` racine.
- `PortfolioPersistenceParityTest`, `SavedViewPersistenceParityTest`, `PolicyPersistenceParityTest`
  (`morpheus-architecture-tests`) : parité de comportement entre `store-memory` et `store-sqlite`.
- Revue : absence d'un second backend persistant et de graph database.

## References

- [ADR-0003](DATA-0003-specification-knowledge-store.md) — port `SpecificationKnowledgeStore`.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — fondation des migrations SQLite.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie conservée avant migration.
- [ADR-0162](ARCH-0162-racines-de-composition-nommees-paquets-sans-cycle-hors-une-paire-bornee-et-decisions-dans-le-domaine.md) — racines de composition nommées.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.
- [Registre des risques](../risks/register.md) — l'unique backend persistant y est tenu pour une position, pas
  une dette.
