# Feuille de route — MORPHEUS

Statut : **C0 à M26 + D0 + D1 intégrés — MORPHEUS 1.0.0 publié — M27 prochain jalon actif**

Dernière mise à jour : 29 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire active 1.x est [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## Politique de branches active

La branche d'intégration de travail est **`develop`**.

```text
feature / milestone branch -> develop
main                       -> branche de stabilisation / livraison, non utilisée pour le travail courant
```

Les nouveaux jalons, leurs branches et leurs pull requests doivent être basés sur `develop` et cibler `develop`, sauf décision explicite contraire du propriétaire du dépôt.

## 1. Baseline intégrée et publiée

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21           ✅ validé et intégré
M22           ✅ validé et intégré
M23           ✅ validé et intégré
M24           ✅ validé et intégré
M25           ✅ validé et intégré
M26           ✅ validé et intégré
```

Références :

```text
M20 merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge          2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge          67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge          88355b69c493677c8689eecad214fb00d283359b
M24 executable     be69e47da0ae209d2246df9c67bc08caeafb2bb0
M24 merge          2b483ded10c783fff22c25035db89475c5c9fdaf
M25 exact head     a392604fc9e8d00f4021351ab5ba53f8488ab920
M25 PR head        9239be641992f40a46f228e09cf6b34ad1cbb1a4
M25 merge          62bf0ea37f732116e821df7d98ae89d36c6dd75d
M26 exact head     bf481b24054c4577144b4cb2ede2bdbc4d9974a2
M26 PR head        36378842e3ef41e379ade17f869b0939d052bbbc
M26 merge          49016a18c844a78ec864235c544d82d487da7c8a
Version            1.0.0
Tag stable         v1.0.0
```

## 2. Référence M26 intégrée

```text
Issue                #109 CLOSED / completed
PR                   #110 MERGED dans develop
Baseline             develop@619237f5273d83ed70728c58e0b97f85803cb167
Head exact qualifié  bf481b24054c4577144b4cb2ede2bdbc4d9974a2
Head PR docs-only    36378842e3ef41e379ade17f869b0939d052bbbc
Merge                49016a18c844a78ec864235c544d82d487da7c8a
Windows reactor      17/17 SUCCESS
Linux reactor        17/17 SUCCESS
Tests                579 PASS Windows + Linux
Architecture         234 PASS Windows + Linux
Windows JaCoCo       44.3507% line / 37.8842% branch
Linux JaCoCo         44.3527% line / 37.8842% branch
Local-first          PASS
TLS/auth/RBAC        PASS
Bounded concurrency  PASS / HTTP 429
Secret disclosure    NONE
Backup/restore       PASS
SQLite               V015
CycloneDX            PASS JSON/XML
Provenance           PASS Windows + Linux
Portable             PASS Windows + Linux
Surface convergence  PASS
Executable delta     NONE Windows + Linux
ADR-0094             Acceptée — M26
CI / GitHub Actions  non utilisé — juillet 2026
```

Preuve : [`VALIDATION_M26.md`](../validation/VALIDATION_M26.md).

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
Policy Packs provider-neutral
policy identities + immutable versions
project / portfolio policy scopes
explicit applicability + PASS/WARN/BLOCK/UNKNOWN
policy overrides + provenance + CAS
policy dry-run read-only
policy audit append-only
Memory + SQLite V015 policies
local-first API loopback
optional remote HTTPS server
Bearer auth + hash-only credential persistence
READ / WRITE / ADMIN RBAC
bounded remote concurrency + HTTP 429
remote observability without secrets
SQLite backup / verify / offline restore
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
           + provider-neutral governance policy contracts
           + optional remote/team access boundary

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
constraint text != executable policy
severity != blocking policy
policy recommendation != applied mutation
policy version != mutable latest
policy override != provenance erasure
dry-run != mutation
policy evaluation != lifecycle mutation
pack activation != domain truth mutation
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
multi-client concurrency != unbounded concurrency
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
| **M25** | ✅ TERMINÉ / INTÉGRÉ — #107 / PR #108 | Policy Packs & Governance Automation |
| **M26** | ✅ TERMINÉ / INTÉGRÉ — #109 / PR #110 | Optional Team/Remote Server Mode |

### NOW

| Jalon | Sujet | Question centrale |
|---|---|---|
| **M27** | Evidence-backed Assisted Reasoning | Inférences optionnelles séparées des faits publiés ? |

Détail : [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 6. Résultat de sortie M26

Question :

> MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ?

**Réponse : oui.** M26 prouve sur le même SHA exact Windows/Linux que le mode local reste first-class, tandis que le mode remote opt-in impose HTTPS + authentification + RBAC, borne la concurrence avec 429, ne divulgue aucun secret et fournit backup/restore SQLite explicites avec restore offline seulement.

**Prochain jalon : M27 — Evidence-backed Assisted Reasoning, basé sur `develop`.**
