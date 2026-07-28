# Feuille de route — MORPHEUS

Statut : **C0 à M21 + D0 + D1 intégrés — MORPHEUS 1.0.0 publié — M22 techniquement validé Windows + Linux, intégration différée pendant le gel CI avant août**

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
M22           ✅ techniquement validé Windows + Linux — intégration différée par gel CI
```

### Référence M20

```text
Issue          #92 CLOSED / completed
PR             #93 MERGED
Code qualifié  9199ed43c4bd8596a97db055eeff17ae31399eb8
Merge M20      75d0b82ab0c960692db2fee1ced146fa6547fd4a
Version        1.0.0
Tests          454/454 PASS Windows + Linux
Architecture   182/182 PASS Windows + Linux
Reactor        14/14 SUCCESS
Windows setup  PASS
Portable Win   PASS
Portable Linux PASS
No-user-JDK    PASS Windows + Linux
Upgrade        PASS
Uninstall      PASS
Checksums      PASS Windows + Linux
```

### Référence D1 / release

```text
Issue D1       #94 CLOSED / completed
PR D1          #95 MERGED
Merge D1       51f6a120f3461c8d8c24323f3db8211d28d6cb42
Issue R1       #96
Release SHA    51f6a120f3461c8d8c24323f3db8211d28d6cb42
Tag stable     v1.0.0
GitHub Release MORPHEUS 1.0.0
Assets         8/8 uploaded
Draft          false
Prerelease     false
```

### Référence M21 intégrée

```text
Issue              #98 CLOSED
PR                 #99 MERGED
Merge              2fdce6601a07628c315fe03932750cd8ece3d777
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

### Référence M22 qualifiée

```text
Issue              #100 OPEN
PR                 #101 CLOSED temporairement — gel CI
Head exécutable    e42bc31384831e56592b11a3509b49a3fdf61773
Windows reactor    17/17 SUCCESS
Linux reactor      17/17 SUCCESS
Tests              494 PASS Windows + Linux
Architecture       190 PASS Windows + Linux
Windows JaCoCo     47.0508% line / 41.8839% branch
Linux JaCoCo       47.0389% line / 41.8839% branch
SDK API            1
External provider  PASS
CycloneDX          PASS JSON/XML
Provenance         PASS Windows + Linux
Portable           PASS Windows + Linux
CLI/MCP/HTTP       provider platform convergence PASS
Executable delta   NONE Windows + Linux
ADR-0090           Acceptée — M22
```

Preuves :

- [`VALIDATION_M20.md`](../validation/VALIDATION_M20.md) ;
- [`VALIDATION_D1.md`](../validation/VALIDATION_D1.md) ;
- [`VALIDATION_R1.md`](../validation/VALIDATION_R1.md) ;
- [`VALIDATION_M21.md`](../validation/VALIDATION_M21.md) ;
- [`VALIDATION_M22.md`](../validation/VALIDATION_M22.md).

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
MINOS optionnel
NEXUS optionnel
JARVIS orchestration boundary
CLI / MCP STDIO / HTTP API
Memory + SQLite
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
MORPHEUS rules != JARVIS action sequencing
surface parity != same transport shape
update discovery != automatic update
checksum != signature
facts != inference
```

## 4. Publication MORPHEUS 1.0.0

R1 est terminée. La publication officielle respecte le contrat M20 :

```text
release SHA exact      51f6a120f3461c8d8c24323f3db8211d28d6cb42
tag stable             v1.0.0
Windows exact-tag      PASS
Linux exact-tag        PASS
SHA-256 staged         PASS
manifests exact-tag    PASS
GitHub tag exact SHA   PASS
GitHub Release         PASS
assets                 8/8 uploaded
```

M20 intégré reste distinct de R1 publié : qualification technique et publication ont des preuves séparées.

## 5. Trajectoire active 1.x

### DONE / QUALIFIED

| Sujet | Statut | Résultat |
|---|---|---|
| **D1** | ✅ TERMINÉ / INTÉGRÉ — #94 / PR #95 | Baseline documentaire 1.x consolidée |
| **R1** | ✅ PUBLIÉ — #96 | Tag `v1.0.0` + GitHub Release + 8 assets |
| **M21** | ✅ TERMINÉ / INTÉGRÉ — #98 / PR #99 | Production Integrity & Surface Convergence |
| **M22** | ✅ TECHNIQUEMENT QUALIFIÉ — #100 / PR #101 | Provider SDK & Plugin Discovery Platform, Windows + Linux exact-head PASS |

### NOW

| Sujet | Statut | Action |
|---|---|---|
| **M22 integration** | ⏸ GEL CI AVANT AOÛT | Réouvrir puis intégrer PR #101 sans delta exécutable supplémentaire |

### NEXT

| Jalon | Sujet | Question centrale |
|---|---|---|
| **M23** | Multi-project / Portfolio Specification Intelligence | Raisonner entre projets sans confondre identité et source ? |
| **M24** | Query DSL, Saved Views & Export/Reporting | Exprimer et partager des vues complexes provider-neutral ? |

### LATER

| Jalon | Sujet | Direction |
|---|---|---|
| **M25** | Policy Packs & Governance Automation | Politiques versionnées, explicables et auditables |
| **M26** | Optional Team/Remote Server Mode | Usage équipe sans abandonner local-first |
| **M27** | Evidence-backed Assisted Reasoning | Inférences optionnelles séparées des faits |

Détail : [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 6. M22 — résultat de sortie

Question :

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

**Réponse : oui.** La preuve Windows + Linux est portée par `VALIDATION_M22.md` sur le même head exécutable `e42bc31384831e56592b11a3509b49a3fdf61773`.

M22 démontre :

- découverte des métadonnées sans exécuter le plugin ;
- compatibilité SDK/MORPHEUS explicite ;
- activation explicite et isolée par JAR ;
- probe de capacités provider-neutral ;
- lecture normalisée distincte du probe ;
- vrai provider externe de référence ;
- test kit réutilisable ;
- surfaces CLI/MCP/HTTP explicites ;
- packaging Windows/Linux sans provider externe embarqué.

Les commits de consolidation postérieurs sont documentaires et restent distincts du SHA exécutable qualifié.