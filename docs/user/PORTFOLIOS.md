# Portfolios multi-projets — M23

M23 ajoute une frontière explicite de **portfolio** pour raisonner sur plusieurs projets MORPHEUS sans dériver l'identité métier d'un chemin, d'une URL de repository ou d'un provider.

Baseline exécutable qualifiée : `04a906e9d5858292ed0f0f1bec65246fef91ed63` — Windows + Linux PASS.

## Principes

```text
PortfolioId != workspace path
ProjectSpecificationId != repository URL
ProjectSpecificationId != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
traversal is bounded and explainable
```

Un portfolio référence des projets existants par leur `ProjectSpecificationId`. Les informations `workspace`, `repository` et `providers` sont des observations attachées à l'adhésion ; elles ne deviennent jamais la clé métier du projet.

## Créer un portfolio

```bash
morpheus portfolio create --name "Payments Platform"
```

En JSON :

```bash
morpheus --json portfolio create --name "Payments Platform"
```

La réponse contient un `PortfolioId` UUIDv7 stable.

## Enregistrer un projet dans le portfolio

```bash
morpheus portfolio add-project \
  --portfolio <portfolioId> \
  --project <projectId> \
  --name "Billing API" \
  --workspace /workspace/billing \
  --repository git:https://example.invalid/billing.git \
  --providers openspec,markdown
```

`--workspace`, `--repository` et `--providers` sont optionnels. Le repository et les source locators utilisent la forme `scheme:value`.

## Marquer un projet absent sans supprimer son identité

```bash
morpheus portfolio missing \
  --portfolio <portfolioId> \
  --project <projectId>
```

Cette opération marque l'adhésion `MISSING`. Elle ne supprime ni le `ProjectSpecificationId`, ni les références inter-projets déjà connues.

## Enregistrer la fraîcheur d'un projet

```bash
morpheus portfolio freshness \
  --portfolio <portfolioId> \
  --project <projectId> \
  --state FRESH \
  --revision abc123 \
  --explanation "specifications synchronized"
```

La fraîcheur est project-scoped. Mettre à jour un membre ne réécrit pas les autres membres du portfolio.

## Ajouter une référence inter-projets

```bash
morpheus portfolio add-reference \
  --portfolio <portfolioId> \
  --source-project <projectA> \
  --source-type REQUIREMENT \
  --source-id <entityUuidA> \
  --target-project <projectB> \
  --target-type SPECIFICATION \
  --target-id <entityUuidB> \
  --relation depends-on \
  --provider openspec \
  --source-locator file:/workspace/spec.md \
  --evidence <evidenceUuid>
```

Une observation conserve explicitement :

- projet et entité source ;
- projet et entité cible ;
- relation ;
- provider ;
- source locator éventuel ;
- evidence éventuelle ;
- horodatage d'observation.

Deux observations contradictoires coexistent. MORPHEUS ne fait pas de last-write-wins silencieux.

## Requêtes portfolio

Lister les portfolios :

```bash
morpheus portfolio list --offset 0 --limit 100
```

Vue d'ensemble :

```bash
morpheus portfolio overview --portfolio <portfolioId>
```

Membres :

```bash
morpheus portfolio members --portfolio <portfolioId> --offset 0 --limit 100
```

Références portfolio-scoped :

```bash
morpheus portfolio references --portfolio <portfolioId>
```

Références project-scoped :

```bash
morpheus portfolio references \
  --portfolio <portfolioId> \
  --project <projectId>
```

Conflits explicites :

```bash
morpheus portfolio conflicts --portfolio <portfolioId>
```

## Traversal inter-projets

```bash
morpheus portfolio traverse \
  --portfolio <portfolioId> \
  --start-project <projectId> \
  --start-type REQUIREMENT \
  --start-id <entityUuid> \
  --direction BOTH \
  --depth 4 \
  --nodes 250 \
  --links 1000
```

Directions : `OUTBOUND`, `INBOUND` ou `BOTH`.

Budgets maximaux M23 :

```text
max depth   8
max nodes   1,000
max links   5,000
```

La traversal est une BFS déterministe. L'ordre des nœuds retournés est l'ordre de découverte BFS, pas l'ordre lexical des UUID. Si un budget est atteint, la réponse indique explicitement qu'elle est tronquée et fournit la raison de troncature.

## HTTP

Les routes M23 vivent sous `/api/v1/portfolios` :

```text
GET  /portfolios
POST /portfolios
GET  /portfolios/{portfolioId}
GET  /portfolios/{portfolioId}/members
POST /portfolios/{portfolioId}/projects
POST /portfolios/{portfolioId}/projects/{projectId}/missing
POST /portfolios/{portfolioId}/projects/{projectId}/freshness
GET  /portfolios/{portfolioId}/references
POST /portfolios/{portfolioId}/references
GET  /portfolios/{portfolioId}/conflicts
POST /portfolios/{portfolioId}/traverse
```

`GET /references` accepte `projectId`, `offset` et `limit` comme query parameters.

Référence machine M23 : [`../openapi/morpheus-v1-portfolio-m23.yaml`](../openapi/morpheus-v1-portfolio-m23.yaml).

## MCP

Outils M23 :

```text
create_portfolio
register_portfolio_project
mark_portfolio_project_missing
observe_portfolio_freshness
add_cross_project_reference
get_portfolio_overview
list_portfolio_references
traverse_portfolio
```

Les sorties CLI JSON, MCP et HTTP passent toutes par les projections transport-safe M23 : les identités et timestamps sont sérialisés comme chaînes, conformément au contrat JSON canonique.

## Limites et garanties

- un portfolio n'est pas propriétaire des sources d'un projet ;
- une référence inter-projets n'est pas automatiquement une preuve de traçabilité métier ;
- les conflits restent observables ;
- la provenance n'est pas effacée par une éventuelle précédence ;
- la traversal est toujours bornée ;
- le fonctionnement reste local-first ;
- aucun LLM n'est requis pour cette intelligence multi-projets.

Preuve technique : [`../validation/VALIDATION_M23.md`](../validation/VALIDATION_M23.md).
