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

Trois options ont été évaluées : un store mémoire, SQLite comme candidat persistant, et une graph database.

Les expérimentations ont montré sur SQLite : création locale sans service, persistance après redémarrage,
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

## Decision

SQLite est le **backend persistant de production** derrière le port `SpecificationKnowledgeStore` et les ports de
persistance de `morpheus-application`, par un adaptateur JDBC (`morpheus-store-sqlite`) et le driver Xerial SQLite
JDBC (`org.xerial:sqlite-jdbc`), dont la version est verrouillée dans le `pom.xml` racine (propriété
`sqlite-jdbc.version`) et suivie par la maintenance normale. Le driver peut être remplacé sans modifier le domaine.

SQLite n'est visible ni dans le domaine, ni dans les services applicatifs, ni dans les DTO publics, ni dans les
contrats MCP et API. Les stores concrets sont construits par les racines de composition (ADR-0109).

```text
Graph model = yes
Graph database mandatory = no
```

```text
Persistent backend = SQLite
Memory backend     = contract test / reference
Dedicated graph store = not required
Storage boundary   = SpecificationKnowledgeStore
```

### Schéma

Le payload JSON complet de l'expérimentation n'est pas le schéma de production. Le schéma de production distingue au
minimum les familles : projets, snapshots de connaissance, spécifications, requirements, changements, contraintes,
scénarios, décisions de conception, critères d'acceptation, tâches d'implémentation, liens de traçabilité, références
externes, provenance et preuves.

### Migrations

Le schéma de production est versionné : migration explicite, migration testée sur copie, version de schéma stockée,
rollback ou restauration documentés selon le type de migration, reconstruction depuis les sources toujours possible
pour les données dérivées. Le mécanisme relève d'ADR-0021 ; la copie conservée avant qu'un schéma avance relève
d'ADR-0110.

### Invariants

1. SQLite reste un adaptateur.
2. Aucune API SQL dans le domaine.
3. Le snapshot actif est atomique au niveau observable.
4. `DomainIdentity` est conservée exactement.
5. Les liens de traçabilité sont associés au snapshot.
6. Les références non résolues sont conservées.
7. Le store est reconstructible.
8. Les migrations sont explicites.
9. Le fonctionnement ne requiert pas de réseau.
10. Un graph store reste réévaluable sans refonte du domaine.

### Un seul backend, par choix

L'absence de second backend persistant est un choix, pas une dette : elle tient tant qu'aucun critère de réouverture
ci-dessous n'est rempli. `morpheus-store-memory` est le store de contrat et de test, pas un backend de production en
attente de promotion.

Critères de réouverture :

- volume ou latence au-delà des objectifs ;
- concurrence multi-utilisateur devenue prioritaire ;
- traversées complexes devenues dominantes ;
- distribution du driver devenue problématique ;
- mode serveur central devenu le déploiement principal.

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
- [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0016](GEN-0016-java-21-production-baseline.md) — baseline Java 21.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — fondation des migrations SQLite.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie conservée avant migration.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.
- [Registre des risques](../risks/register.md) — l'unique backend persistant y est tenu pour une position, pas
  une dette.
