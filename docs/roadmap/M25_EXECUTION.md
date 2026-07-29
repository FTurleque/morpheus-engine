# M25 — Policy Packs & Governance Automation

Statut : **EN COURS — M25-S0 cadré**

Issue : #107 — **OPEN**
PR : à ouvrir en Draft vers `develop`
Branche : `m25/policy-packs-governance-automation`
Baseline : `develop@5cdb26405fb9ae768964a24016fef89bdca97e88`

## Question de sortie

> Les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme politiques versionnées, explicables et auditables sans transformer recommandations, texte libre ou dry-run en mutation silencieuse ?

## Principes

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
surface parity != same transport shape
```

M25 ne crée pas de langage de script ni de moteur d'exécution arbitraire. Les policy packs décrivent des règles déclaratives bornées qui composent des sémantiques déjà possédées par MORPHEUS : qualité, contraintes, lifecycle et requêtes normalisées.

## Modèle cible

```text
PolicyPackId
PolicyPackVersionId
PolicyRuleId
PolicyPackDefinition
PolicyPackVersion
PolicyRule
PolicyRuleKind
PolicyScope
PolicySeverity
PolicyDecision
PolicyEvaluation
PolicyOverride
PolicyAuditRecord
```

Une identité de pack reste stable pendant que ses versions sont immuables. Une activation sélectionne explicitement une version ; elle ne réécrit jamais une version historique.

## Types de règles M25

Le premier contrat reste fermé et typé :

```text
CONSTRAINT_GUARD
LIFECYCLE_GUARD
QUALITY_THRESHOLD
QUERY_ASSERTION
```

- `CONSTRAINT_GUARD` consomme les évaluations M16 et peut exiger qu'aucune contrainte explicitement BLOCKING ne soit observée pour une cible lifecycle donnée.
- `LIFECYCLE_GUARD` consomme l'évaluation de transition M14/M17 sans appliquer de mutation.
- `QUALITY_THRESHOLD` applique un seuil explicite à une métrique qualité déclarée.
- `QUERY_ASSERTION` consomme le moteur Query DSL M24 et compare `totalMatches` à un opérateur/seuil borné.

Aucun type ne compile ni n'interprète du code, du SQL ou du texte de contrainte.

## Scope

Un pack est activé explicitement sur un scope :

```text
PROJECT(ProjectSpecificationId)
PORTFOLIO(PortfolioId)
```

L'identité du scope ne provient jamais d'un workspace, repository, provider ou chemin.

## Applicability et décision

```text
APPLICABLE
NOT_APPLICABLE
UNKNOWN
```

Décision d'une règle :

```text
PASS
WARN
BLOCK
UNKNOWN
```

`UNKNOWN` reste une information de manque de preuve et n'est jamais convertie implicitement en `BLOCK`.

## Overrides

Un override est une configuration first-class :

```text
ruleId
scope
mode        DISABLE | FORCE_WARN | FORCE_BLOCK
reason
actor
createdAt
revision
```

L'override conserve toujours la décision d'origine dans l'explication. `FORCE_BLOCK` est une décision de gouvernance explicite, pas une transformation de UNKNOWN par défaut.

## Dry-run

Le dry-run :

- lit l'état publié courant ;
- exécute les règles applicables ;
- produit décisions, raisons, provenance et evidence ;
- n'active aucun pack ;
- ne modifie aucun lifecycle ;
- ne publie aucun snapshot ;
- n'écrit aucun résultat métier.

## Budgets M25

```text
rules per pack              <= 128
active packs per scope      <= 32
overrides per scope         <= 256
pack name                   <= 160 chars
rule description            <= 512 chars
query assertion page size   <= M24 QueryBudgets.MAX_PAGE_SIZE
dry-run evaluations         <= 4096 rules
```

Tout dépassement échoue explicitement avant exécution partielle silencieuse.

## Persistance

Port application `PolicyPackStore` avec adapters Memory et SQLite.

SQLite : migration additive `V015__policy_packs.sql` après V014. Aucune migration historique n'est réécrite.

Tables cibles :

```text
policy_packs
policy_pack_versions
policy_pack_activations
policy_overrides
policy_audit
```

Les versions de pack et l'audit sont append-only. Activation et overrides utilisent CAS/révision explicite.

## Surfaces

Intentions convergentes :

```text
policy.pack.create
policy.pack.get
policy.pack.list
policy.pack.versions
policy.pack.update
policy.pack.activate
policy.pack.deactivate
policy.override.put
policy.override.list
policy.dry-run
policy.evaluate
policy.audit
```

CLI, MCP et HTTP restent des adapters vers les mêmes services applicatifs.

## Slices

### M25-S0 — cadrage / ADR / roadmap / Draft PR

- [x] baseline `develop@5cdb264...`
- [x] issue #107
- [x] branche depuis develop
- [x] roadmap opérationnelle M25
- [x] ADR-0093 proposée
- [ ] Draft PR ouverte vers develop

### M25-S1 — modèle et validation

- [ ] identité pack/version/rule
- [ ] scope projet/portfolio
- [ ] kinds fermés et payloads typés
- [ ] budgets
- [ ] validation déterministe

### M25-S2 — évaluation et explainability

- [ ] décisions PASS/WARN/BLOCK/UNKNOWN
- [ ] applicability
- [ ] constraint/lifecycle integration read-only
- [ ] quality threshold
- [ ] query assertion M24
- [ ] résultat canonique et ordonné

### M25-S3 — overrides / precedence / conflicts

- [ ] override first-class
- [ ] raison/acteur obligatoires
- [ ] décision originale conservée
- [ ] ordre stable
- [ ] conflits explicites

### M25-S4 — registry/versioning/Memory

- [ ] create/update/version history
- [ ] CAS revision
- [ ] activate/deactivate explicit version
- [ ] Memory parity contract
- [ ] audit config

### M25-S5 — SQLite V015

- [ ] migration additive
- [ ] SqlitePolicyPackStore
- [ ] reopen
- [ ] parity Memory/SQLite
- [ ] transactions/autocommit restoration

### M25-S6 — governance dry-run

- [ ] dry-run read-only
- [ ] deterministic report
- [ ] no mutation proof
- [ ] budget proof

### M25-S7 — CLI/MCP/HTTP

- [ ] CLI policy commands
- [ ] MCP strict schemas
- [ ] HTTP routes + strict JSON
- [ ] OpenAPI M25
- [ ] public-surfaces manifest

### M25-S8 — architecture / packaging / docs / validator

- [ ] architecture contracts
- [ ] packaged class/migration proof
- [ ] user/developer docs
- [ ] `validate-m25.cmd`
- [ ] `scripts/validate-m25.ps1`
- [ ] `scripts/validate-m25.sh`

### M25-S9 — qualification / intégration

- [ ] Windows exact-head PASS
- [ ] Linux exact-head PASS même SHA
- [ ] 543 tests M24 minimum sans régression
- [ ] 221 architecture minimum sans régression
- [ ] JaCoCo floors PASS
- [ ] SBOM/provenance PASS
- [ ] portable Windows/Linux PASS
- [ ] `postGateExecutableDelta=NONE`
- [ ] ADR-0093 Acceptée
- [ ] PR Ready puis merge dans `develop`
- [ ] issue #107 CLOSED / completed
- [ ] M26 passe NOW

## Règle de qualification

Toute modification après un PASS de code produit, POM, contrat runtime, migration, manifeste public, OpenAPI, packaging ou validateur invalide le PASS et impose un nouveau replay Windows + Linux du même SHA. Les consolidations post-gate doivent être documentaires uniquement.

En juillet 2026, GitHub Actions / CI n'est pas utilisé comme preuve M25.