# Policy Packs & Governance Automation — guide utilisateur M25

M25 permet de définir, versionner, activer et évaluer des politiques de gouvernance sans transformer du texte libre en code exécutable et sans confondre recommandation avec mutation métier.

## Principes

```text
constraint text != executable policy
UNKNOWN != BLOCKED
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
```

Une policy MORPHEUS est une configuration déclarative provider-neutral. Elle consomme des faits déjà possédés par MORPHEUS : contraintes M16, transitions lifecycle read-only M14/M17, métriques qualité et Query DSL M24.

## Policy pack et versions

Un pack possède une identité stable `PolicyPackId`. Son nom peut changer sans changer son identité.

Chaque mise à jour produit une nouvelle version immuable :

```text
PolicyPackId
  ├── version 1 / PolicyPackVersionId A
  ├── version 2 / PolicyPackVersionId B
  └── version 3 / PolicyPackVersionId C
```

Une activation choisit explicitement une version pour un scope projet ou portfolio. Il n’existe pas de « latest mutable » implicitement appliqué.

## Types de règles

M25 accepte quatre types fermés :

| Kind | Source de faits | Effet |
|---|---|---|
| `CONSTRAINT_GUARD` | évaluations de contraintes M16 | observe blocking/unknown, sans lire le texte comme code |
| `LIFECYCLE_GUARD` | transition-check read-only | observe ALLOWED/BLOCKED/UNKNOWN/REQUIRES_INPUT |
| `QUALITY_THRESHOLD` | métriques qualité | compare une métrique déclarée à un seuil |
| `QUERY_ASSERTION` | Query DSL M24 | compare `totalMatches` à un seuil |

Il n’existe pas de règle JavaScript, Groovy, SQL, nom de classe ou script arbitraire.

## Severity et décision

Severity d’une règle :

```text
INFO
WARNING
BLOCKER
```

Résultat d’évaluation :

```text
PASS
WARN
BLOCK
UNKNOWN
```

Un manque d’information reste `UNKNOWN`. MORPHEUS ne le convertit pas automatiquement en `BLOCK`.

Un échec de règle `BLOCKER` produit `BLOCK`. Un échec de règle `INFO` ou `WARNING` produit `WARN`.

## Overrides

Un override est explicite, versionné par révision CAS et audité :

```text
DISABLE
FORCE_WARN
FORCE_BLOCK
```

Il exige un acteur et une raison. Le résultat conserve toujours :

```text
originalDecision
+ override appliqué
= effectiveDecision
```

Ainsi un `UNKNOWN` forcé en `BLOCK` reste visible comme `originalDecision=UNKNOWN`.

## Dry-run

`policy dry-run` évalue une version donnée sans l’activer.

Il ne modifie :

- aucun snapshot ;
- aucun lifecycle ;
- aucune saved view ;
- aucune activation policy ;
- aucun override ;
- aucun audit de configuration.

Le dry-run est donc adapté à la revue d’un pack avant activation.

## CLI

Créer un pack :

```bash
morpheus --json policy pack create \
  --name "Governance" \
  --rules "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0" \
  --actor alice \
  --reason "initial governance"
```

Lister :

```bash
morpheus --json policy pack list
```

Versions :

```bash
morpheus --json policy pack versions --id <policyPackId>
```

Mettre à jour avec CAS :

```bash
morpheus --json policy pack update \
  --id <policyPackId> \
  --expected-revision 1 \
  --name "Governance v2" \
  --rules "<ruleId>|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0" \
  --actor alice \
  --reason "tighten governance"
```

Une révision obsolète échoue explicitement. Il n’y a pas de last-write-wins silencieux.

Activer une version :

```bash
morpheus --json policy activate \
  --id <policyPackId> \
  --version <policyPackVersionId> \
  --project <projectId> \
  --expected-revision 0 \
  --actor alice \
  --reason "enable governance"
```

Évaluer :

```bash
morpheus --json policy evaluate --project <projectId>
```

Évaluer un seul pack actif :

```bash
morpheus --json policy evaluate --project <projectId> --id <policyPackId>
```

Dry-run :

```bash
morpheus --json policy dry-run \
  --id <policyPackId> \
  --version <policyPackVersionId> \
  --project <projectId>
```

Override :

```bash
morpheus --json policy override put \
  --id <policyPackId> \
  --rule <ruleId> \
  --mode FORCE_WARN \
  --project <projectId> \
  --expected-revision 0 \
  --actor security-owner \
  --reason "temporary waiver"
```

Audit :

```bash
morpheus --json policy audit --id <policyPackId>
```

## Grammaire CLI des règles

Les règles CLI sont séparées par `;;`.

Préfixe commun :

```text
id-or-new|description|KIND|SEVERITY|...
```

Exemples :

```text
new|No blocker|CONSTRAINT_GUARD|BLOCKER|<changeId>|IMPLEMENTING
new|Valid transition|LIFECYCLE_GUARD|BLOCKER|<changeId>|PROPOSED|SPECIFIED
new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0
new|No unresolved query rows|QUERY_ASSERTION|WARNING|<encodedQueryDefinition>|EQ|0
```

`QUERY_ASSERTION` utilise une `QueryDefinition` M24 encodée de façon déterministe. Ce champ n’accepte pas de SQL.

## Scopes

Une policy activation cible exactement :

```text
PROJECT(ProjectSpecificationId)
```

ou :

```text
PORTFOLIO(PortfolioId)
```

Les règles projet-only (`CONSTRAINT_GUARD`, `LIFECYCLE_GUARD`, `QUALITY_THRESHOLD`) deviennent `NOT_APPLICABLE` sur un scope portfolio. `QUERY_ASSERTION` peut être projet ou portfolio si sa QueryDefinition possède le même scope.

## Budgets

```text
rules per pack          <= 128
active packs per scope  <= 32
overrides per scope     <= 256
pack name               <= 160 chars
rule description        <= 512 chars
dry-run evaluations     <= 4096 rules
```

Tout dépassement est rejeté explicitement.

## Persistence

Les policy packs utilisent le même layout de données MORPHEUS. SQLite reçoit la migration additive V015.

Les versions et audits sont append-only ; activations et overrides utilisent une révision CAS.

## Surfaces

M25 expose les mêmes intentions via :

- CLI `policy ...` ;
- MCP tools `*_policy_*` / `evaluate_policies` ;
- HTTP `/api/v1/policy-packs`, `/api/v1/policies`, `/api/v1/policy-overrides`.

La forme transport peut différer, mais toutes les surfaces appellent les mêmes services applicatifs.

## Mutation lifecycle

Une policy `BLOCK` ou `PASS` n’applique jamais une transition.

Pour modifier réellement le lifecycle, il faut toujours passer par le contrat M17 séparé avec `WRITE_CHANGE`, confirmation, `expectedRevision`, `idempotencyKey` et audit de mutation.
