# M25 — Policy Packs & Governance Automation

Statut : **QUALIFIÉ — M25-S0→S9 techniquement terminés ; intégration finale #108 vers `develop` en cours**

Issue : #107 — **OPEN jusqu'au merge**  
PR : #108 — **à rendre Ready puis merger vers `develop`**  
Branche : `m25/policy-packs-governance-automation`  
Baseline : `develop@5cdb26405fb9ae768964a24016fef89bdca97e88`

Head exact qualifié Windows + Linux/WSL :

```text
a392604fc9e8d00f4021351ab5ba53f8488ab920
```

Preuve : [`../validation/VALIDATION_M25.md`](../validation/VALIDATION_M25.md).

## Question de sortie

> Les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme politiques versionnées, explicables et auditables sans transformer recommandations, texte libre ou dry-run en mutation silencieuse ?

**Réponse : oui, démontré sur Windows et Linux/WSL sur le même SHA exact.**

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

```text
CONSTRAINT_GUARD
LIFECYCLE_GUARD
QUALITY_THRESHOLD
QUERY_ASSERTION
```

- `CONSTRAINT_GUARD` consomme les évaluations M16 et n'analyse jamais du texte libre comme code ;
- `LIFECYCLE_GUARD` consomme l'évaluation de transition M14/M17 sans appliquer de mutation ;
- `QUALITY_THRESHOLD` compare une métrique fermée à un seuil explicite ;
- `QUERY_ASSERTION` réutilise le Query DSL M24 et ses budgets.

## Scope, applicability et décision

Scopes :

```text
PROJECT(ProjectSpecificationId)
PORTFOLIO(PortfolioId)
```

Applicability :

```text
APPLICABLE
NOT_APPLICABLE
UNKNOWN
```

Décisions :

```text
PASS
WARN
BLOCK
UNKNOWN
```

`UNKNOWN` reste un manque de preuve et n'est jamais converti implicitement en `BLOCK`.

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

La décision originale reste présente dans l'explication même lorsque la décision effective est modifiée par gouvernance.

## Dry-run

Le dry-run :

- lit l'état publié courant ;
- évalue une version de pack ;
- produit décisions, raisons, provenance et evidence ;
- n'active aucun pack ;
- ne modifie aucun lifecycle ;
- ne publie aucun snapshot ;
- n'écrit aucun résultat métier ni audit de mutation.

## Budgets M25

```text
rules per pack              <= 128
active packs per scope      <= 32
overrides per scope         <= 256
pack name                   <= 160 chars
rule description            <= 512 chars
dry-run evaluations         <= 4096 rules
```

## Persistance

Port application `PolicyPackStore`, adapters Memory et SQLite.

Migration additive :

```text
V015__policy_packs.sql
```

Tables :

```text
policy_packs
policy_pack_versions
policy_pack_activations
policy_overrides
policy_audit
```

Versions et audit sont append-only. Activations et overrides utilisent CAS/révision explicite.

## Surfaces publiques

Intentions convergentes :

```text
policy.pack.create
policy.pack.list
policy.pack.get
policy.pack.versions
policy.pack.update
policy.pack.activate
policy.pack.deactivate
policy.activation.list
policy.override.put
policy.override.list
policy.override.remove
policy.evaluate
policy.dry-run
policy.audit
```

CLI, MCP et HTTP restent des adapters vers les mêmes services applicatifs.

## Slices

### M25-S0 — cadrage / ADR / roadmap / Draft PR

- [x] baseline `develop@5cdb264...`
- [x] issue #107
- [x] branche depuis `develop`
- [x] roadmap opérationnelle M25
- [x] ADR-0093 créée
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
- [x] MCP tools + schemas stricts
- [x] HTTP routes + strict JSON
- [x] OpenAPI M25
- [x] public-surfaces manifest

### M25-S8 — architecture / packaging / docs / validator

- [x] architecture contracts
- [x] packaged class/migration proof
- [x] guide utilisateur `POLICY_PACKS.md`
- [x] architecture développeur `POLICY_PLATFORM.md`
- [x] `validate-m25.cmd`
- [x] `scripts/validate-m25.ps1`
- [x] `scripts/validate-m25.sh`

### M25-S9 — qualification / intégration

- [x] Windows exact-head PASS
- [x] Linux/WSL exact-head PASS même SHA
- [x] 565 tests Windows + Linux
- [x] 231 architecture Windows + Linux
- [x] JaCoCo floors PASS
- [x] SBOM/provenance PASS
- [x] portable Windows/Linux PASS
- [x] policy versioning/CAS/dry-run/override explainability PASS
- [x] CLI/MCP/HTTP convergence PASS
- [x] SQLite V015 PASS
- [x] `postGateExecutableDelta=NONE`
- [x] ADR-0093 **Acceptée — M25**
- [x] preuve `VALIDATION_M25.md`
- [ ] PR #108 Ready puis merge dans `develop`
- [ ] issue #107 CLOSED / completed
- [ ] réconciliation post-merge : M25 DONE / M26 NOW

## Qualification exacte

Windows :

```text
sha=a392604fc9e8d00f4021351ab5ba53f8488ab920
tests=565
architectureTests=231
lineCoverage=0.429925
branchCoverage=0.363983
portable=True
postGateExecutableDelta=NONE
```

Linux / WSL :

```text
sha=a392604fc9e8d00f4021351ab5ba53f8488ab920
tests=565
architectureTests=231
lineCoverage=0.429945
branchCoverage=0.363983
portable=true
postGateExecutableDelta=NONE
```

Les deux gates incluent `policyPacks`, `policyVersioning`, `policyOverrides`, `policyDryRun`, `policyExplainability`, `surfaceConvergence`, `sqliteV015`, `sbom` et `provenance` à `PASS`.

## Incidents utiles découverts pendant S9

1. WSL a exposé une race réelle au démarrage MCP : le serveur pouvait devenir visible avant l'enregistrement complet des tools. `71e6eb7c...` assemble désormais tous les tools avant `.build()`.
2. Le packaging Linux exigeait `JAVA_HOME` alors que WSL disposait du JDK sans variable exportée. `a392604f...` dérive `JAVA_HOME` depuis `java` dans le harness M25.
3. Après ces corrections, Windows et Linux/WSL ont été rejoués intégralement sur `a392604f...` et ont tous deux terminé avec `M25 VALIDATION PASS`.

## Règle post-gate

À partir du SHA qualifié `a392604f...`, les changements de consolidation doivent rester exclusivement documentaires. Toute modification de code produit, POM, contrat runtime, migration, OpenAPI, packaging, manifeste public ou validateur invaliderait la qualification et imposerait un nouveau replay Windows + Linux.

En juillet 2026, **aucune GitHub Actions / CI n'est utilisée comme preuve M25**.