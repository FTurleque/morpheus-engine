# ADR-0030 — Introduire la persistance métier normalisée avec M3

- Statut : **Acceptée — M2**
- Date : 22 juillet 2026
- Dépend de : ADR-0003, ADR-0006, ADR-0012, ADR-0018, ADR-0021, ADR-0022, ADR-0023, ADR-0025
- Portée : frontière M2/M3, schéma SQLite métier, snapshots et versions

## Contexte

M2 a stabilisé les principaux contrats Java de contenu normalisé :

```text
Specification
Requirement
RequirementDelta
Scenario
ChangeProposal
Constraint
DesignDecision
ImplementationTask
Evidence
Provenance
ExternalReference
```

SQLite persiste déjà :

```text
projects
knowledge_snapshots (métadonnées)
entity_identity_bindings
schema_migrations
```

ADR-0021 a volontairement différé les tables métier tant que les contrats n'étaient pas stabilisés.

La question de sortie M2 était donc :

> Faut-il créer maintenant les tables métier normalisées, ou les introduire avec M3 lorsque `TemporalState`, `SpecificationVersion` et la projection par `KnowledgeSnapshot` deviennent des concepts de production complets ?

## Forces en présence

### Stabilité du modèle structurel

Les entités M2 sont désormais suffisamment stables pour être normalisées en mémoire.

### Temporalité encore différée

M2 interdit volontairement de projeter :

```text
CURRENT
PROPOSED
HISTORICAL
```

sur les premières entités de production.

### Versionnement

ADR-0012 impose un état de connaissance observable cohérent par snapshot, et distingue `SpecificationVersion` du `KnowledgeSnapshot` technique.

### Coût de migration

Créer des tables métier sans leurs dimensions de version/snapshot conduirait à une migration structurelle immédiate en M3.

## Décision

**Ne pas créer de nouvelles tables métier dans M2.**

Introduire la première persistance complète des entités normalisées pendant M3, conjointement avec :

```text
TemporalState
SpecificationVersion
KnowledgeSnapshot complet
snapshot membership / version ownership
activation atomique du contenu observable
```

M2 conserve uniquement les fondations persistantes déjà nécessaires indépendamment de cette projection :

```text
project registry
snapshot metadata
provider-scoped identity bindings
migration ledger
```

## Invariant de conception M3

Le schéma métier devra répondre explicitement à la question :

```text
quelle version / quel snapshot possède ou expose cette occurrence de contenu ?
```

avant de créer :

```text
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
external_references
provenance / evidence
```

## Pourquoi pas en M2

Créer ces tables en M2 obligerait soit :

1. à stocker un contenu sans ownership temporel/versionné ;
2. à inventer prématurément le modèle M3 ;
3. à introduire une association snapshot provisoire immédiatement remplacée ;
4. à transformer le modèle M2 structurel en modèle de persistance définitif sans preuve de projection.

Ces options contredisent le principe :

```text
Documenter d'abord
Décider ensuite
Implémenter après
Prouver avant de valider
```

## Conséquences positives

- aucune table métier jetable entre M2 et M3 ;
- le schéma persistant sera conçu avec la temporalité réelle ;
- les invariants `CURRENT / PROPOSED / HISTORICAL` pourront être testés dès la première migration métier ;
- snapshot/version membership ne sera pas ajouté rétroactivement ;
- SQLite reste simple et reconstructible pendant M2.

## Conséquences négatives

- à la fermeture M2, le contenu normalisé complet n'est pas encore persisté dans SQLite ;
- une réingestion est nécessaire pour reconstruire le contenu après redémarrage ;
- les requêtes métier persistantes attendent M3.

Ces limites sont acceptables car M2 valide l'ingestion et le modèle normalisé ; M3 possède explicitement la responsabilité de versions/snapshots et de l'état temporel.

## Ce qui reste persistant en M2

```text
DomainIdentity bindings     ✅
project registry            ✅
knowledge snapshot metadata ✅
normalized business content ❌ différé M3
```

La stabilité d'identité entre deux ingestions reste donc conservée avant M3.

## Critères d'acceptation

ADR-0030 est acceptée lorsque :

1. l'audit M2 confirme qu'aucune exigence de sortie n'impose la persistance complète des entités ;
2. ADR-0012 confirme que snapshot/version membership appartient à la projection de connaissance ;
3. ADR-0021 confirme que les tables métier étaient volontairement différées ;
4. `VALIDATION_M2.md` documente explicitement cette limite ;
5. M3 inscrit la persistance métier versionnée dans son périmètre d'ouverture ;
6. le gate complet M2 reste vert sans nouvelle table métier.

## Preuve d'acceptation — 22 juillet 2026

Gate final exécuté sur `m2/final-validation` :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultat :

```text
TOTAL      94/94 PASS
Failures       0
Errors         0
Skipped        0
BUILD SUCCESS
```

Le diff S8 ne modifie aucun fichier Java, POM ou migration SQL. La totalité des preuves M2 reste donc verte sans introduire de table métier prématurée.

Décision finale :

```text
M2 : normalisation structurelle validée
M3 : temporalité + versions + snapshots + premières tables métier versionnées
```
