# M25 — Policy Packs & Governance Automation

Statut : **EN COURS — M25-S0→S8 implémentés ; qualification S9 à exécuter**

Issue : #107 — **OPEN**
PR : #108 — **DRAFT vers `develop`**
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

## Modèle livré

```text
PolicyIds.PackId
PolicyIds.VersionId
PolicyIds.RuleId
PolicyPack.Definition
PolicyPack.Version
PolicyRule
PolicyScope
PolicyEvaluation
PolicyConfiguration.Override
PolicyConfiguration.AuditRecord
```

Une identité de pack reste stable pendant que ses versions sont immuables. Une activation sélectionne explicitement une version ; elle ne réécrit jamais une version historique.

## Types de règles M25

Le contrat est fermé et typé :

```text
CONSTRAINT_GUARD
LIFECYCLE_GUARD
QUALITY_THRESHOLD
QUERY_ASSERTION
```

- `CONSTRAINT_GUARD` consomme les évaluations M16 et observe les contraintes explicitement BLOCKING/UNKNOWN pour une cible lifecycle déclarée.
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
scope
packId
ruleId
mode        DISABLE | FORCE_WARN | FORCE_BLOCK
reason
actor
revision
updatedAt
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
- n'écrit aucun résultat métier ni audit de configuration.

## Budgets M25

```text
rules per pack              <= 128
active packs per scope      <= 32
overrides per scope         <= 256
pack name                   <= 160 chars
rule description            <= 512 chars
dry-run evaluations         <= 4096 rules
```

Les `QUERY_ASSERTION` réutilisent les budgets du Query DSL M24. Tout dépassement échoue explicitement avant exécution partielle silencieuse.

## Persistance

Port application `PolicyPackStore` avec adapters Memory et SQLite.

SQLite : migration additive `V015__policy_packs.sql` après V014. Aucune migration historique n'est réécrite.

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
- [x] Draft PR #108 ouverte vers `develop`

### M25-S1 — modèle et validation

- [x] identité pack/version/rule
- [x] scope projet/portfolio
- [x] kinds fermés et payloads typés
- [x] budgets
- [x] validation déterministe

### M25-S2 — évaluation et explainability

- [x] décisions PASS/WARN/BLOCK/UNKNOWN
- [x] applicability APPLICABLE/NOT_APPLICABLE/UNKNOWN
- [x] constraint/lifecycle integration read-only
- [x] quality threshold
- [x] query assertion M24
- [x] résultat transport-safe, canonique et ordonné

### M25-S3 — overrides / precedence / conflicts

- [x] override first-class `scope + packId + ruleId`
- [x] raison/acteur obligatoires
- [x] décision originale conservée séparément de la décision effective
- [x] ordre stable
- [x] stale revisions / conflits explicites

### M25-S4 — registry/versioning/Memory

- [x] create/update/version history
- [x] CAS revision
- [x] activate/deactivate explicit version
- [x] Memory contract
- [x] audit config append-only

### M25-S5 — SQLite V015

- [x] migration additive
- [x] `SqlitePolicyPackStore`
- [x] reopen
- [x] parity Memory/SQLite
- [x] transactions/autocommit restoration
- [x] codec versionné déterministe, sans Java serialization

### M25-S6 — governance dry-run

- [x] dry-run read-only
- [x] deterministic report
- [x] no mutation proof
- [x] UNKNOWN preservation
- [x] budget proof

### M25-S7 — CLI/MCP/HTTP

- [x] CLI policy commands
- [x] MCP 12 tools + schemas stricts
- [x] HTTP routes + strict JSON
- [x] OpenAPI M25
- [x] public-surfaces manifest

### M25-S8 — architecture / packaging / docs / validator

- [x] architecture contracts
- [x] packaged class/migration proof dans validateurs
- [x] guide utilisateur `POLICY_PACKS.md`
- [x] architecture développeur `POLICY_PLATFORM.md`
- [x] `validate-m25.cmd`
- [x] `scripts/validate-m25.ps1`
- [x] `scripts/validate-m25.sh`

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

## Tests M25 dédiés

```text
PolicyPackContractTest
PolicyPersistenceParityTest
PolicyCodecAndBudgetContractTest
PolicyPlatformArchitectureTest
MorpheusPolicyCliTest
MorpheusPolicyMcpToolsTest
MorpheusPolicyApiContractTest
```

Ils verrouillent identité/versioning/CAS, UNKNOWN, overrides, dry-run no-write, codec/budgets, Memory/SQLite V015/reopen et convergence de surfaces.

## Gate canonique

Windows :

```powershell
.\validate-m25.cmd 1.0.0
```

Linux :

```bash
bash ./scripts/validate-m25.sh 1.0.0
```

Les deux doivent exécuter exactement le même SHA de code.

## Règle de qualification

Toute modification après un PASS de code produit, POM, contrat runtime, migration, manifeste public, OpenAPI, packaging ou validateur invalide le PASS et impose un nouveau replay Windows + Linux du même SHA. Les consolidations post-gate doivent être documentaires uniquement.

En juillet 2026, GitHub Actions / CI n'est pas utilisé comme preuve M25.