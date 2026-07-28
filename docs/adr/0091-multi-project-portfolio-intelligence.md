# ADR-0091 — Multi-project portfolio intelligence

Statut : **Proposée — M23**

Date : 28 juillet 2026

## Contexte

MORPHEUS sait raisonner sur un projet et un snapshot, mais ne possède pas encore de frontière de portfolio explicite. Une agrégation naïve par chemin de workspace, URL de repository ou identifiant provider confondrait localisation technique et identité métier.

## Décision

M23 introduit un modèle provider-neutral :

```text
PortfolioId
PortfolioMembership(PortfolioId, ProjectSpecificationId, locations, providers, status)
PortfolioEntityRef(ProjectSpecificationId, entityType, DomainIdentity)
CrossProjectReference(source, target, relation, evidence, provenance)
PortfolioFreshness(projectId, observedAt, revision, state)
```

Invariants :

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
precedence != provenance erasure
traversal is bounded and explainable
freshness != full destructive rescan
```

### Registry

Le registry conserve une identité de portfolio stable et des adhésions par `ProjectSpecificationId`. Un workspace, un repository ou un provider est une observation attachée à l’adhésion, jamais sa clé métier.

Une observation absente marque le projet `MISSING` mais ne supprime ni l’identité du projet, ni ses références historiques.

### Cross-project references

Une référence relie deux `PortfolioEntityRef`. Elle conserve relation, provenance, evidence et timestamps. Plusieurs observations concurrentes restent visibles ; aucune stratégie last-write-wins silencieuse n’est autorisée.

### Queries

Les requêtes sont explicitement :

```text
project-scoped
portfolio-scoped
```

Un résultat portfolio conserve toujours le `ProjectSpecificationId` de chaque élément.

### Traversal

Le traversal inter-projets est BFS déterministe avec budgets explicites : profondeur, nombre de nœuds et nombre de liens. Le résultat expose les liens visités, les profondeurs et la raison de troncature.

### Freshness

La fraîcheur est enregistrée par projet. Mettre à jour un projet ne réécrit pas les autres adhésions. Une révision inchangée produit une mise à jour idempotente.

### Persistence

Un port `PortfolioStore` est implémenté en mémoire et SQLite. SQLite utilise une migration additive V013.

### Surfaces

CLI, MCP et HTTP exposent les mêmes intentions : registry, overview, references, traversal et freshness. Les transports peuvent différer de forme, pas de sémantique.

## Conséquences

Positives : identité inter-projets stable, absence non destructive, références explicables, traversal borné, local-first.

Coûts : nouveau store, migration, surfaces supplémentaires et nécessité de toujours conserver le scope projet.

## Validation requise

- tests domaine et application ;
- parité mémoire/SQLite ;
- absence non destructive ;
- conflits/provenance préservés ;
- traversal borné/déterministe ;
- fraîcheur incrémentale ;
- CLI/MCP/HTTP ;
- packaging ;
- qualification Windows + Linux exact-head.
