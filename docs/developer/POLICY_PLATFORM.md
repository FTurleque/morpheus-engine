# M25 — Policy Platform Architecture

M25 ajoute une plateforme de policy packs provider-neutral sans créer un moteur parallèle de vérité métier.

## Frontière

```text
CLI / MCP / HTTP
      |
      v
PolicyPackService ---------> PolicyPackStore ---------> Memory / SQLite V015
      |
      +-- configuration/versioning/CAS/audit

PolicyEvaluationService
      |
      v
PolicyFactResolver
      |
      +--> ConstraintEvaluationQueryService (M16)
      +--> ChangeTransitionEvaluationService (M14/M17 read-only)
      +--> QualityReportService
      +--> QueryExecutionService (M24)
```

Le moteur policy compose des services applicatifs existants. Il ne lit pas directement des tables métier et n’interprète pas le texte des contraintes.

## Packages

Application :

```text
com.morpheus.application.policy
  PolicyIds
  PolicyScope
  PolicyBudgets
  PolicyRule
  PolicyPack
  PolicyConfiguration
  PolicyEvaluation
  PolicyFactResolver
  DefaultPolicyFactResolver
  PolicyPackCodec
  PolicyPackService
  PolicyEvaluationService
  PolicyPublicViews
```

Port :

```text
com.morpheus.application.store.PolicyPackStore
```

Adapters :

```text
MemoryPolicyPackStore
SqlitePolicyPackStore
MorpheusPolicyCli
MorpheusPolicyMcpTools
MorpheusPolicyApiService
MorpheusPolicyHttpRoutes
```

## Identités

```text
PolicyPackId        stable
PolicyPackVersionId immutable version occurrence
PolicyRuleId        stable rule identity
```

Les identités utilisent le `DomainIdentity` UUIDv7 canonique MORPHEUS.

`PolicyPackId != name`.

## Versioning

`PolicyPack.Definition` représente le head de configuration : nom, revision CAS, dernier numéro de version.

`PolicyPack.Version` est immuable :

```text
packId
versionId
versionNumber
name
rules[]
createdAt
```

Une update CAS réussie :

```text
revision n -> n+1
version  n -> n+1
```

et ajoute une version. Elle ne modifie aucune version historique.

## Règles

`PolicyRule.Config` est sealed :

```text
ConstraintGuard
LifecycleGuard
QualityThreshold
QueryAssertion
```

`PolicyRule.Kind` vérifie que le payload correspond exactement au kind.

Aucun :

```text
ScriptEngine
Class.forName
ProcessBuilder
Runtime.exec
SQL passthrough
provider-specific payload
```

n’appartient au modèle application policy.

## Fact resolver

`PolicyFactResolver` est read-only :

```java
PolicyEvaluation.Fact resolve(PolicyScope scope, PolicyRule rule);
```

`DefaultPolicyFactResolver` réutilise :

- M16 pour les contraintes ;
- M14/M17 pour transition-check ;
- les services qualité ;
- M24 pour `QUERY_ASSERTION`.

Il n’a pas de référence au `ChangeLifecycleMutationStore` ni au `ControlledChangeLifecycleMutationService`.

## Unknown semantics

```text
missing fact -> Fact.UNKNOWN
Fact.UNKNOWN -> Decision.UNKNOWN
```

Aucun severity ne transforme UNKNOWN en BLOCK.

Un `FORCE_BLOCK` peut explicitement produire `effectiveDecision=BLOCK`, mais le résultat conserve :

```text
originalDecision=UNKNOWN
override.mode=FORCE_BLOCK
override.actor
override.reason
```

## Applicability

```text
APPLICABLE
NOT_APPLICABLE
UNKNOWN
```

Les guards contraintes/lifecycle/quality sont project-only en M25. Au scope portfolio, ils produisent `NOT_APPLICABLE`, donc n’inventent pas de fait agrégé.

Une QueryAssertion est applicable si le scope de sa `QueryDefinition` correspond exactement au `PolicyScope`.

## Evaluation

`PolicyEvaluationService` ne dépend que de :

```text
PolicyPackStore
PolicyFactResolver
```

Il ne possède aucune opération write.

Agrégation :

```text
BLOCK > UNKNOWN > WARN > PASS
```

Cette priorité rend un manque de preuve observable même si d’autres règles ne sont que warnings.

## Dry-run

`dryRun(scope, packId, versionId)` :

- charge une version immuable ;
- résout les faits ;
- applique les overrides correspondants lorsqu’ils existent ;
- produit un `Report(dryRun=true)` ;
- n’appelle aucune méthode de mutation du store.

Le test de contrat compare audit/activation/override avant/après pour verrouiller cette propriété.

## Overrides

Clé logique :

```text
scope + packId + ruleId
```

Configuration :

```text
mode
reason
actor
revision
updatedAt
```

CAS : la création attend revision `0`, les updates attendent la revision courante.

Un override n’efface jamais la décision source.

## Audit

`PolicyConfiguration.AuditRecord` est append-only et distinct de l’audit lifecycle M17.

Actions :

```text
CREATE
UPDATE
ACTIVATE
DEACTIVATE
PUT_OVERRIDE
REMOVE_OVERRIDE
```

L’audit décrit la configuration de gouvernance, pas une mutation métier.

## Persistence Memory

`MemoryPolicyPackStore` utilise des structures triées et des méthodes synchronisées. Versions et audit sont append-only.

## Persistence SQLite V015

Tables :

```text
policy_packs
policy_pack_versions
policy_pack_activations
policy_overrides
policy_audit
```

Les payloads de versions sont encodés par `PolicyPackCodec`, codec binaire versionné + Base64. Il n’utilise ni Java serialization ni JSON arbitraire.

`QUERY_ASSERTION` délègue l’encodage de `QueryDefinition` au codec M24.

Chaque mutation SQLite :

1. mémorise `autoCommit` ;
2. ouvre une transaction ;
3. effectue le CAS ;
4. ajoute version/audit si CAS réussi ;
5. commit ou rollback ;
6. restaure `autoCommit`.

## Surface architecture

CLI : grammaire compacte vers les records typés.

MCP : JSON Schema 2020-12, `additionalProperties=false`, kinds/enums/budgets fermés.

HTTP : records request stricts Jackson, unknown/trailing tokens rejetés, CAS stale en HTTP 409.

Les trois adapters produisent `PolicyPublicViews`.

## Contrats machine

```text
contracts/public-surfaces.tsv
docs/openapi/morpheus-v1-policy-m25.yaml
```

Le manifeste définit READ/WRITE par intention. `policy.evaluate`, `policy.dry-run` et `policy.audit` sont READ.

## Budgets

```text
MAX_RULES_PER_PACK           128
MAX_ACTIVE_PACKS_PER_SCOPE    32
MAX_OVERRIDES_PER_SCOPE      256
MAX_PACK_NAME                160
MAX_RULE_DESCRIPTION         512
MAX_DRY_RUN_EVALUATIONS     4096
```

Les budgets sont vérifiés avant persistance/exécution partielle.

## Tests M25

Architecture module :

```text
PolicyPackContractTest
PolicyPersistenceParityTest
PolicyCodecAndBudgetContractTest
PolicyPlatformArchitectureTest
```

Adapters :

```text
MorpheusPolicyCliTest
MorpheusPolicyMcpToolsTest
MorpheusPolicyApiContractTest
```

Les tests couvrent versioning/CAS, UNKNOWN, overrides, dry-run no-write, codec déterministe, budget, Memory/SQLite parity/reopen, surface strictness et HTTP réel.

## Gate

Windows :

```powershell
.\validate-m25.cmd 1.0.0
```

Linux :

```bash
bash ./scripts/validate-m25.sh 1.0.0
```

Le gate M25 utilise `develop` comme base et exige le même SHA exécutable sur Windows + Linux avant acceptation d’ADR-0093 et merge dans `develop`.
