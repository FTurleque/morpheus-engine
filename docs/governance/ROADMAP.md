# Feuille de route — MORPHEUS

Statut : **C0 à M24 + D0 + D1 intégrés — MORPHEUS 1.0.0 publié — M25 prochain jalon actif**

Dernière mise à jour : 28 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire active 1.x est [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 1. Baseline intégrée et publiée

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
M24           ✅ validé et intégré
```

Références :

```text
M20 merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge          2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge          67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge          88355b69c493677c8689eecad214fb00d283359b
M24 executable     be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 PR head        863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
M24 merge          2b483ded10c783fff22c25035db89475c5c9fdaf
Version            1.0.0
Tag stable         v1.0.0
```

## 2. Référence M24 intégrée

```text
Issue                #105 CLOSED / completed
PR                   #106 MERGED
Baseline             main@f70eaa1ad58633ee59874ab44f70963ab51152c6
Head exécutable      be69e47da0ae209d2246df9c67bc08caeafb2bb0
Head PR docs-only    863c2fa8f1fd7dcb40ef437c7fe6b8da016c0f58
Merge                2b483ded10c783fff22c25035db89475c5c9fdaf
Windows reactor      17/17 SUCCESS
Linux reactor        17/17 SUCCESS
Tests                543 PASS Windows + Linux
Architecture         221 PASS Windows + Linux
Windows JaCoCo       44.2936% line / 38.1166% branch
Linux JaCoCo         44.3037% line / 38.1166% branch
Query DSL            PASS
Saved views          PASS
SQLite V014          PASS
Canonical JSON       PASS
CSV                  PASS
Markdown             PASS
Query/export budgets PASS
CycloneDX            PASS JSON/XML
Provenance           PASS Windows + Linux
Portable             PASS Windows + Linux
CLI/MCP/HTTP         query/view/export convergence PASS
Executable delta     NONE Windows + Linux
ADR-0092             Acceptée — M24
```

Preuve : [`VALIDATION_M24.md`](../validation/VALIDATION_M24.md).

## 3. Capacités acquises

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
Provider SDK v1 + plugins externes
portfolio registry provider-neutral
cross-project references + traversal bornée
Memory + SQLite V013 portfolio
Query DSL provider-neutral typé
filter/sort/projection/pagination déterministes
saved views versionnées + CAS
Memory + SQLite V014 saved views
canonical JSON + CSV + Markdown reporting
query/export/saved-view budgets
CLI / MCP STDIO / HTTP API
setup Windows per-user
archives portables Windows/Linux
runtime Java embarqué
CycloneDX + build provenance
public surface manifest READ/WRITE
```

## 4. Responsabilités non négociables

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts
           + portfolio specification facts
           + provider-neutral query/view/reporting contracts

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
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
plugin discovery != plugin activation
probe != read
cross-project identity != source path
project identity != workspace path
portfolio membership != source ownership
cross-project reference != traceability proof
traversal is bounded and explainable
DSL != SQL passthrough
saved view != materialized truth
export != mutation
bounded query != silently truncated semantics
surface parity != same transport shape
facts != inference
```

## 5. Trajectoire active 1.x

### DONE

| Sujet | Statut | Résultat |
|---|---|---|
| **D1** | ✅ TERMINÉ / INTÉGRÉ — #94 / PR #95 | Baseline documentaire 1.x consolidée |
| **R1** | ✅ PUBLIÉ — #96 | Tag `v1.0.0` + GitHub Release |
| **M21** | ✅ TERMINÉ / INTÉGRÉ — #98 / PR #99 | Production Integrity & Surface Convergence |
| **M22** | ✅ TERMINÉ / INTÉGRÉ — #100 / PR #101 | Provider SDK & Plugin Discovery Platform |
| **M23** | ✅ TERMINÉ / INTÉGRÉ — #103 / PR #104 | Multi-project / Portfolio Specification Intelligence |
| **M24** | ✅ TERMINÉ / INTÉGRÉ — #105 / PR #106 | Query DSL, Saved Views & Export/Reporting |

### NOW

| Jalon | Sujet | Question centrale |
|---|---|---|
| **M25** | Policy Packs & Governance Automation | Distribuer des politiques versionnées, explicables et auditables sans transformer recommandations en mutations silencieuses ? |

### LATER

| Jalon | Sujet | Direction |
|---|---|---|
| **M26** | Optional Team/Remote Server Mode | Usage équipe sans abandonner local-first |
| **M27** | Evidence-backed Assisted Reasoning | Inférences optionnelles séparées des faits |

Détail : [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 6. Résultat de sortie M24

Question :

> Les utilisateurs peuvent-ils exprimer, sauvegarder et exporter des vues métier complexes sans dépendre d’un transport ou d’un format provider particulier ?

**Réponse : oui.** M24 prouve sur le même SHA exécutable Windows/Linux un AST provider-neutral, des scopes projet/portfolio, un ordre stable, des saved views versionnées avec CAS, Memory/SQLite V014, des exports JSON/CSV/Markdown bornés et une convergence CLI/MCP/HTTP.

**Prochain jalon : M25 — Policy Packs & Governance Automation.**