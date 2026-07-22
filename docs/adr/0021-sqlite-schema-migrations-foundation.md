# ADR-0021 — Versionner le schéma SQLite par migrations explicites et minimales

- Statut : **Acceptée — M1**
- Date : 22 juillet 2026
- Dépend de : ADR-0003, ADR-0012, ADR-0015, ADR-0018
- Portée : persistance locale, migrations, identité, métadonnées de snapshots

---

## 1. Contexte

ADR-0018 retient SQLite derrière `SpecificationKnowledgeStore` et rejette explicitement le payload JSON du spike E08 comme schéma de production.

M1 doit maintenant fournir une première fondation persistante réellement versionnée, sans figer prématurément les tables métier qui appartiennent à M2 et aux phases suivantes.

Le domaine actuellement suffisamment stable pour être persisté comprend :

```text
ProjectSpecificationId
SourceLocator
KnowledgeSnapshotId
KnowledgeSnapshotState
métadonnées de snapshot
```

Le modèle complet de `Requirement`, `ChangeProposal`, `Constraint`, `Scenario`, etc. n'est pas encore un contrat Java de production stabilisé.

---

## 2. Problème

Deux erreurs doivent être évitées :

1. réutiliser le blob JSON de M0 et créer une dette de schéma ;
2. créer dès M1 toutes les tables métier à partir de concepts encore incomplets et transformer des hypothèses en contraintes persistantes difficiles à migrer.

MORPHEUS a besoin d'un mécanisme de migration durable **avant** d'avoir besoin du schéma complet.

---

## 3. Décision

Adopter des migrations SQL explicites, ordonnées et immuables après application.

### 3.1 Ledger de migrations

SQLite conserve :

```text
schema_migrations
- version INTEGER PRIMARY KEY
- name TEXT NOT NULL
- checksum TEXT NOT NULL
- applied_at TEXT NOT NULL
```

Le checksum est SHA-256 du script appliqué.

Une migration déjà enregistrée avec un checksum différent provoque un échec explicite : MORPHEUS ne réécrit jamais silencieusement l'histoire du schéma.

### 3.2 Migration V1

La migration initiale crée uniquement :

```text
projects
knowledge_snapshots
```

avec les index et contraintes nécessaires aux invariants M0/M1.

Elle ne crée aucun payload JSON métier générique.

Les familles suivantes seront ajoutées par migrations dédiées lorsque leurs contrats seront suffisamment stabilisés :

```text
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
traceability_links
external_references
provenance / evidence
```

### 3.3 Identités

Les identités MORPHEUS sont stockées comme représentation texte canonique UUIDv7.

Invariant :

```text
DomainIdentity != SourceLocator
```

Un locator de source est donc persisté dans des colonnes distinctes de l'identité.

### 3.4 Snapshots

V1 persiste uniquement les **métadonnées** nécessaires au contrat de store :

```text
snapshot id
project id
predecessor id optionnel
state
source revision optionnelle
created_at
```

Aucun contenu métier M2 n'est inventé dans cette migration.

### 3.5 Stratégie de migration

Le chemin normal est **forward-only**.

Pour les données dérivées/reconstructibles, la reconstruction depuis les sources reste la stratégie de secours privilégiée plutôt qu'un système complexe de down-migrations.

Toute migration destructive future devra documenter explicitement sauvegarde/restauration et stratégie de reconstruction.

---

## 4. Invariants

1. SQLite reste un adapter ;
2. aucune API SQL ne fuit dans `com.morpheus.domain` ou les services applicatifs ;
3. toute évolution de schéma passe par une migration versionnée ;
4. une migration appliquée devient immuable par checksum ;
5. rejouer les migrations est idempotent ;
6. le schéma V1 ne contient aucun blob JSON générique représentant le domaine ;
7. les identités UUIDv7 et les locators restent séparés ;
8. une seule version `ACTIVE` est observable par projet ;
9. les foreign keys SQLite sont activées par l'adapter ;
10. le store reste reconstructible depuis les sources.

---

## 5. Port `SpecificationKnowledgeStore`

M1 implémente uniquement un sous-ensemble de fondation commun mémoire/SQLite :

```text
putProject / findProject
putSnapshot / findSnapshot
activeSnapshot
activateSnapshot(expectedPredecessor)
```

Ce sous-ensemble sert à valider la frontière, l'idempotence et l'activation atomique.

Il ne prétend pas constituer l'API finale des requêtes M2-M5.

---

## 6. Backend mémoire

Le backend mémoire reste la référence des tests contractuels.

Les mêmes scénarios de contrat sont exécutés sur mémoire et SQLite pour éviter qu'une sémantique spécifique à JDBC devienne implicitement la sémantique MORPHEUS.

---

## 7. Alternatives

### A. Blob JSON du spike E08

**Rejeté.**

Il a rempli son rôle expérimental mais ne fournit pas le schéma relationnel de production recherché.

### B. Créer toutes les tables métier dès M1

**Rejeté.**

Cela figerait prématurément M2 avant que les contrats Java correspondants soient stabilisés.

### C. ORM + génération automatique de schéma

**Non retenu.**

MORPHEUS a besoin d'un historique de migration explicite et contrôlable ; aucun besoin actuel ne justifie l'ajout d'un ORM.

### D. Framework de migration externe

**Différé.**

Le besoin V1 est suffisamment petit pour un runner SQL déterministe. Flyway/Liquibase ou équivalent pourra être réévalué si le nombre ou la complexité des migrations rend le runner interne disproportionné.

---

## 8. Risques et mitigations

### Évolution rapide du modèle

Mitigation : V1 ne persiste que les concepts déjà justifiés par M0/M1.

### Divergence mémoire / SQLite

Mitigation : suite commune de tests contractuels.

### Modification accidentelle d'une migration appliquée

Mitigation : checksum SHA-256 enregistré et vérifié.

### Migration destructive future

Mitigation : backup/reconstruction documentés avant application.

---

## 9. Validation

Validation locale Windows effectuée le 22 juillet 2026 avec :

```text
Windows 10 x64
Apache Maven 3.9.16
JDK de build 24.0.1
javac release 21
```

Gate exécuté :

```text
.\mvnw.cmd clean test
```

Résultats :

```text
DomainIdentityTest                         4/4 PASS
ProjectDiscoveryServiceTest               6/6 PASS
WorkspaceRootResolverTest                 3/3 PASS
ProviderSelectionPolicyTest               6/6 PASS
OpenSpecDiscoveryIntegrationTest          5/5 PASS
OpenSpecSpecificationProviderTest         4/4 PASS
SqliteDriverSmokeTest                     1/1 PASS
SqliteSchemaMigrationTest                 4/4 PASS
LayerDependencyTest                       2/2 PASS
SpecificationKnowledgeStoreContractTest   4/4 PASS

TOTAL                                    39/39 PASS
BUILD SUCCESS
```

La preuve démontre :

```text
UUIDv7 valide et opaque
store mémoire conforme
store SQLite conforme
migration V1 appliquée
rejeu migration idempotent
checksum de migration immuable
historique de migration falsifié rejeté
persistance après réouverture SQLite
activation atomique d'un snapshot
predecessor obsolète rejeté
snapshot non ACTIVE invisible via activeSnapshot
aucun payload JSON générique
```

Le warning JDK 24 relatif à `System::load` / `--enable-native-access=ALL-UNNAMED` est non bloquant pour M1 et reste à traiter dans la stratégie runtime/packaging avant stabilisation CLI.

---

## 10. Critère d'acceptation

Le critère d'acceptation est **satisfait**.

ADR-0021 est **Acceptée — M1** : la suite commune mémoire/SQLite, les tests de migration V1, les tests UUIDv7 et le build complet sont verts.

---

## 11. Impact

Cette décision ferme la fondation de persistance M1 sans anticiper l'ingestion M2 ni les snapshots métier complets M3.

Les migrations suivantes pourront étendre le schéma en conservant :

```text
provider -> normalization -> MORPHEUS domain -> SpecificationKnowledgeStore -> adapters
```
