# Feuille de route — MORPHEUS

Statut : **C0 à M23 + D0 + D1 intégrés — MORPHEUS 1.0.0 publié — M24 prochain jalon actif**

Dernière mise à jour : 28 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire active 1.x est [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md). La trajectoire [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) est historique et couvre D0 + M15→M20.

## 1. Baseline intégrée et publiée

```text
C0 → M14      ✅ validés et intégrés
D0            ✅ validé et intégré
M15           ✅ validé et intégré
M16           ✅ validé et intégré
M17           ✅ validé et intégré
M18           ✅ validé et intégré
M19           ✅ validé et intégré
M20           ✅ validé et intégré
D1            ✅ validé et intégré
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
```

### Références d’intégration

```text
M20 merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge          2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge          67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge          88355b69c493677c8689eecad214fb00d283359b
M23 executable     04a906e9d5858292ed0f0f1bec65246fef91ed63
Version            1.0.0
Tag stable         v1.0.0
```

### Référence M21

```text
Issue              #98 CLOSED / completed
PR                 #99 MERGED
Head exécutable    239d99657fbf193761767f382489dd637e642fe9
Tests              473 PASS Windows + Linux
Architecture       187 PASS Windows + Linux
CycloneDX          PASS JSON/XML
Provenance         PASS Windows + Linux
Portable           PASS Windows + Linux
CLI/MCP/HTTP       convergence PASS
Executable delta   NONE Windows + Linux
ADR-0089           Acceptée — M21
```

### Référence M22

```text
Issue              #100 CLOSED / completed
PR                 #101 MERGED
Merge              67c587057e287d57b0733f9e425a57b26cc38ae4
Head exécutable    e42bc31384831e56592b11a3509b49a3fdf61773
Tests              494 PASS Windows + Linux
Architecture       190 PASS Windows + Linux
SDK API            1
External provider  PASS
CycloneDX          PASS JSON/XML
Provenance         PASS Windows + Linux
Portable           PASS Windows + Linux
Executable delta   NONE Windows + Linux
ADR-0090           Acceptée — M22
```

### Référence M23 intégrée

```text
Issue              #103 CLOSED / completed
PR                 #104 MERGED
Merge              88355b69c493677c8689eecad214fb00d283359b
Baseline           main@67c587057e287d57b0733f9e425a57b26cc38ae4
Head exécutable    04a906e9d5858292ed0f0f1bec65246fef91ed63
Windows reactor    17/17 SUCCESS
Linux reactor      17/17 SUCCESS
Tests              507 PASS Windows + Linux
Architecture       195 PASS Windows + Linux
Windows JaCoCo     46.7034% line / 40.9099% branch
Linux JaCoCo       46.6979% line / 40.9099% branch
Portfolio identity PASS
Cross-project refs PASS
Bounded traversal  PASS
SQLite V013        PASS
CycloneDX          PASS JSON/XML
Provenance         PASS Windows + Linux
Portable           PASS Windows + Linux
CLI/MCP/HTTP       portfolio convergence PASS
Executable delta   NONE Windows + Linux
ADR-0091           Acceptée — M23
```

Preuves :

- [`VALIDATION_M20.md`](../validation/VALIDATION_M20.md) ;
- [`VALIDATION_D1.md`](../validation/VALIDATION_D1.md) ;
- [`VALIDATION_R1.md`](../validation/VALIDATION_R1.md) ;
- [`VALIDATION_M21.md`](../validation/VALIDATION_M21.md) ;
- [`VALIDATION_M22.md`](../validation/VALIDATION_M22.md) ;
- [`VALIDATION_M23.md`](../validation/VALIDATION_M23.md).

## 2. Capacités acquises

```text
modèle de domaine provider-neutral
identité persistante stable
CURRENT / PROPOSED / HISTORICAL
KnowledgeSnapshot + SpecificationVersion
published history + rollback logique
RequirementDelta apply/promote/activate explicites
traçabilité typée + traversal bornée
requêtes métier + vues compactes
qualité / couverture / diagnostics
synchronisation incrémentale + freshness
change analysis
acceptance / verification / evidence
constraint semantics + blocking policy
controlled lifecycle write + CAS/idempotency/audit
OpenSpec + Structured Markdown providers réels
composition multi-provider + provenance + conflits
Provider SDK v1
plugin discovery metadata-only sans classloading
activation explicite dans classloader dédié
capability probe distinct de normalized read
provider externe de référence + test kit
portfolio registry provider-neutral
membership projet indépendante des localisations techniques
cross-project references avec provenance/evidence
project-scoped + portfolio-scoped queries
cross-project conflicts explicites
BFS inter-projets déterministe, bornée et explicable
freshness portfolio incrémentale
Memory + SQLite V013 pour portfolio
MINOS optionnel
NEXUS optionnel
JARVIS orchestration boundary
CLI / MCP STDIO / HTTP API
setup Windows per-user
archives portables Windows/Linux
runtime Java embarqué
release builders exact-tag + SHA-256 + manifests
GitHub Release stable v1.0.0
CI durable Windows/Linux
JaCoCo quality floors
public surface manifest READ/WRITE
CycloneDX + build provenance
product metadata/version convergence
explicit read-only update discovery
```

## 3. Responsabilités non négociables

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts

MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

Invariants structurants :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
provider identifier != DomainIdentity
source path != identity
PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
applicable != blocking
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
capability declaration != capability implementation proof
probe != read
classloader isolation != security sandbox
optional provider absence != project failure when optional
optional engine absence != MORPHEUS failure
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
freshness != full destructive rescan
MORPHEUS rules != JARVIS action sequencing
surface parity != same transport shape
update discovery != automatic update
checksum != signature
facts != inference
```

## 4. Trajectoire active 1.x

### DONE

| Sujet | Statut | Résultat |
|---|---|---|
| **D1** | ✅ TERMINÉ / INTÉGRÉ — #94 / PR #95 | Baseline documentaire 1.x consolidée |
| **R1** | ✅ PUBLIÉ — #96 | Tag `v1.0.0` + GitHub Release + 8 assets |
| **M21** | ✅ TERMINÉ / INTÉGRÉ — #98 / PR #99 | Production Integrity & Surface Convergence |
| **M22** | ✅ TERMINÉ / INTÉGRÉ — #100 / PR #101 | Provider SDK & Plugin Discovery Platform |
| **M23** | ✅ TERMINÉ / INTÉGRÉ — #103 / PR #104 | Multi-project / Portfolio Specification Intelligence |

### NOW

| Jalon | Sujet | Question centrale |
|---|---|---|
| **M24** | Query DSL, Saved Views & Export/Reporting | Exprimer, sauvegarder et exporter des vues complexes provider-neutral ? |

### LATER

| Jalon | Sujet | Direction |
|---|---|---|
| **M25** | Policy Packs & Governance Automation | Politiques versionnées, explicables et auditables |
| **M26** | Optional Team/Remote Server Mode | Usage équipe sans abandonner local-first |
| **M27** | Evidence-backed Assisted Reasoning | Inférences optionnelles séparées des faits |

Détail : [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 5. M23 — résultat de sortie

Question :

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

**Réponse : oui.** La preuve Windows + Linux est portée par `VALIDATION_M23.md` sur le même head exécutable `04a906e9d5858292ed0f0f1bec65246fef91ed63`, puis intégrée par le merge `88355b69c493677c8689eecad214fb00d283359b`.

M23 démontre :

- identité de portfolio stable et provider-neutral ;
- identité projet indépendante du workspace, repository et provider ;
- absence d'un membre non destructive ;
- références inter-projets conservant provenance et evidence ;
- conflits contradictoires visibles sans last-write-wins silencieux ;
- requêtes project-scoped et portfolio-scoped ;
- BFS déterministe bornée avec ordre de découverte préservé et troncature explicable ;
- fraîcheur incrémentale par projet ;
- parité Memory/SQLite via V013 ;
- convergence CLI/MCP/HTTP ;
- packaging Windows/Linux sur le même SHA exécutable.

**Prochain jalon : M24 — Query DSL, Saved Views & Export/Reporting.**
