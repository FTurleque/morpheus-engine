# ADR-0093 — Provider-neutral policy packs and governance automation

Statut : **Acceptée — M25**

Date : 29 juillet 2026

## Contexte

MORPHEUS possède déjà des règles métier explicites pour les contraintes (M16), l'évaluation de transitions et les mutations lifecycle contrôlées (M14/M17), des diagnostics qualité et un moteur Query DSL provider-neutral (M24). Ces capacités sont toutefois codées comme contrats applicatifs individuels. M25 doit permettre de distribuer et versionner des ensembles de politiques de gouvernance sans introduire un deuxième moteur de vérité ni rendre du texte, SQL ou code arbitraire exécutable.

## Décision

M25 introduit des `PolicyPack` provider-neutral dans application/domain avec versions immuables, activation explicite, overrides auditables et évaluation read-only.

Invariants :

```text
constraint text != executable policy
UNKNOWN != BLOCKED
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
pack activation != domain truth mutation
```

## Identité et version

```text
PolicyPackId        identité stable du pack
PolicyPackVersionId identité immuable d'une version
PolicyRuleId        identité stable de règle dans le pack
revision            CAS de la configuration courante du pack
versionNumber       numéro monotone immuable de version publiée
```

Le nom d'un pack n'est pas son identité. Une mise à jour crée une nouvelle version ; elle ne modifie jamais le contenu d'une version historique.

## Règles fermées et typées

Le premier contrat M25 expose uniquement :

```text
CONSTRAINT_GUARD
LIFECYCLE_GUARD
QUALITY_THRESHOLD
QUERY_ASSERTION
```

Chaque kind possède un payload typé. Aucun moteur de script, expression libre, SQL passthrough, class name, plugin code ou template LLM n'est accepté comme policy rule.

### Constraint guard

Consomme les `ConstraintEvaluation` produites par le moteur M16. La règle peut tester la présence de décisions explicitement BLOCKING pour une cible lifecycle déclarée. Elle n'analyse jamais `Constraint.statement()`.

### Lifecycle guard

Consomme l'évaluation read-only de transition existante. Une décision policy peut recommander BLOCK/WARN/PASS/UNKNOWN mais ne déclenche aucune mutation.

### Quality threshold

Compare une métrique qualité déclarée dans un registre fermé à un seuil numérique explicite.

### Query assertion

Réutilise `QueryDefinition`/`QueryExecutionService` M24 et compare `totalMatches` à un opérateur de comptage fermé. Le DSL M24 reste la seule grammaire de sélection métier ; aucune syntaxe provider ou SQL n'est ajoutée.

## Scope

Une activation porte un scope explicite : projet ou portfolio. Les identités sont `ProjectSpecificationId` / `PortfolioId`, jamais workspace/repository/provider/path.

## Applicability et décisions

Applicability : `APPLICABLE`, `NOT_APPLICABLE`, `UNKNOWN`.

Décision : `PASS`, `WARN`, `BLOCK`, `UNKNOWN`.

Une absence d'information produit `UNKNOWN`. Seule une règle explicite ou un override explicite peut produire `BLOCK`.

## Overrides

Un `PolicyOverride` est first-class et auditable. Il précise règle, scope, mode, raison, acteur, révision et instant.

Modes initiaux :

```text
DISABLE
FORCE_WARN
FORCE_BLOCK
```

L'évaluation conserve toujours la décision originale et l'override appliqué. Un override ne supprime ni provenance, ni evidence, ni raison source.

## Activation

L'activation sélectionne explicitement `(PolicyPackId, PolicyPackVersionId)` sur un scope. Elle utilise CAS. Une activation n'est ni une publication de snapshot, ni une mutation lifecycle, ni un remplacement de vérité provider.

## Dry-run

Le dry-run peut évaluer une version non active sur un scope. Il est strictement read-only : aucune activation, override, audit de mutation métier ou write lifecycle n'est exécuté. Seule une réponse transport-safe est produite.

## Audit

Les opérations de configuration de gouvernance (create version, activate, deactivate, override) produisent des `PolicyAuditRecord` append-only. L'audit de policy config est distinct de l'audit de mutation lifecycle M17.

## Budgets

```text
rules per pack          <= 128
active packs per scope  <= 32
overrides per scope     <= 256
pack name               <= 160 chars
rule description        <= 512 chars
dry-run evaluations     <= 4096 rules
```

Tout dépassement est rejeté explicitement avant exécution partielle.

## Persistence

Port `PolicyPackStore`, adapters Memory + SQLite. Migration additive V015 avec versions et audit append-only. Aucune migration antérieure n'est réécrite.

## Surfaces

CLI, MCP et HTTP exposent les mêmes intentions applicatives de registry/versioning/activation/override/evaluation/audit. La parité d'intention n'impose pas la même forme transport.

## Conséquences

Positives : politiques partageables et versionnées, explication/reproductibilité, réutilisation des sémantiques existantes, séparation forte recommandation/mutation, dry-run sûr.

Coûts : nouveau modèle de configuration, store/versioning/audit, registre fermé de métriques/kinds, migration SQLite et surfaces supplémentaires.

## Validation d'acceptation

L'ADR est acceptée après qualification Windows + Linux/WSL du même SHA exact :

```text
Qualified SHA                         a392604fc9e8d00f4021351ab5ba53f8488ab920
policy identity/version immutability  PASS
rule validation/budgets               PASS
constraint/lifecycle composition      PASS
UNKNOWN preservation                  PASS
override provenance                   PASS
dry-run no mutation                   PASS
Memory/SQLite parity                  PASS
SQLite V015                           PASS
CLI/MCP/HTTP convergence              PASS
architecture contract                 PASS — 231 tests Windows + Linux
product tests                         PASS — 565 Windows + Linux
Windows coverage                      0.429925 / 0.363983
Linux coverage                        0.429945 / 0.363983
SBOM/provenance                       PASS Windows + Linux
portable Windows/Linux                PASS
postGateExecutableDelta               NONE
GitHub Actions / CI                    NOT USED — July 2026
```

Preuve finale : [`../validation/VALIDATION_M25.md`](../validation/VALIDATION_M25.md).