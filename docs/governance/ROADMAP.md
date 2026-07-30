# Feuille de route — MORPHEUS

Statut : **C0 à M27 + D0 + D1 intégrés — MORPHEUS 1.1.0 publié — R3 / 1.2.0 planifié**

Dernière mise à jour : 30 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire fonctionnelle 1.x reste décrite dans [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md). La preuve de publication 1.1.0 est [`VALIDATION_R2.md`](../validation/VALIDATION_R2.md). Le chantier suivant est l’issue GitHub #115.

## 1. Politique de branches

La branche d’intégration de travail est **`develop`**.

```text
feature / milestone branch -> develop
release branch             -> main après qualification
main                       -> stabilisation / livraison
```

Les nouveaux jalons fonctionnels partent de `develop` et ciblent `develop`. Une branche de release consolide ensuite une version qualifiée vers `main`.

## 2. Baseline intégrée et publiée

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
M27           ✅ validé et intégré
R2            ✅ MORPHEUS 1.1.0 publié
R3            ⏭ MORPHEUS 1.2.0 — intégration clients MCP
```

Références :

```text
M20 merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
D1 / release SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
M21 merge          2fdce6601a07628c315fe03932750cd8ece3d777
M22 merge          67c587057e287d57b0733f9e425a57b26cc38ae4
M23 merge          88355b69c493677c8689eecad214fb00d283359b
M24 merge          2b483ded10c783fff22c25035db89475c5c9fdaf
M25 merge          62bf0ea37f732116e821df7d98ae89d36c6dd75d
M26 merge          49016a18c844a78ec864235c544d82d487da7c8a
M27 exact head     f97307c878125550693699124ca717f64f305a3a
M27 merge          f8810803bd5ae7d57c4858e1e384c6a0132e1a45
R2 qualified head  31212087ee5fab3c88b269d56f7f21402f31b683
R2 merge main      31506029ded1101f0571edeb0d79c59bbf3f68c6
stable version     1.1.0
stable tag         v1.1.0
GitHub Release     stable / 8 assets
```

## 3. R2 — MORPHEUS 1.1.0

Question de sortie :

> Les évolutions M21 à M27 peuvent-elles être consolidées dans `main` et publiées comme MORPHEUS 1.1.0 avec des artefacts reproductibles, une qualification exacte Windows/Linux et une traçabilité complète de release ?

Réponse : **OUI — R2 COMPLETE**.

```text
Issue                  #113 CLOSED / completed
PR                     #114 MERGED
Qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
Main merge commit      31506029ded1101f0571edeb0d79c59bbf3f68c6
Tag                    v1.1.0
Windows gate           603 tests / 238 architecture PASS
Linux/WSL gate         603 tests / 238 architecture PASS
same SHA               PASS
post-gate executable   NONE
exact-tag builds       PASS Windows + Linux
GitHub Release         stable / 8 assets
published parity       8/8 PASS
```

Références :

- plan final : [`R2_EXECUTION.md`](../roadmap/R2_EXECUTION.md) ;
- preuve : [`VALIDATION_R2.md`](../validation/VALIDATION_R2.md) ;
- notes : [`RELEASE_NOTES_1.1.0.md`](../release/RELEASE_NOTES_1.1.0.md) ;
- upgrade : [`UPGRADE_1_1.md`](../user/UPGRADE_1_1.md).

## 4. R3 — MORPHEUS 1.2.0

Issue : **#115 — MCP Client Integration & Installer Wiring**.

Objectif : connecter explicitement le serveur MCP STDIO natif de MORPHEUS aux principaux clients IA, sans Docker obligatoire et sans écraser leurs configurations existantes.

Clients cibles :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Périmètre prévu :

```text
integration/configure-mcp-clients.ps1
fusion JSON conservatrice
backup avant écriture
registre de propriété des entrées gérées
installation/désinstallation réversibles
cases opt-in dans le setup Windows
support distribution ZIP
configuration Linux documentée
tests profils utilisateurs temporaires
diagnostic et logs
catalogue MCP M25-M27 actualisé
qualification Windows + Linux/WSL exact-head
```

Invariants R3 :

```text
MCP local native-first
Docker not required
third-party configuration is explicit opt-in
existing foreign `morpheus` entry is never overwritten
manual user changes are preserved
stdout remains MCP JSON-RPC only
stderr remains diagnostics only
READ != WRITE
no implicit mutation
```

## 5. Capacités acquises

```text
modèle de domaine provider-neutral
identité persistante stable
CURRENT / PROPOSED / HISTORICAL
KnowledgeSnapshot + SpecificationVersion
RequirementDelta apply/promote/activate explicites
traçabilité typée + traversal bornée
synchronisation incrémentale + freshness
change analysis
acceptance / verification / evidence
controlled lifecycle write + CAS/idempotency/audit
composition multi-provider + provenance + conflits
Provider SDK v1 + plugins externes
portfolio multi-projets + références inter-projets
Query DSL + saved views + reporting
Policy Packs + overrides + dry-run + audit
serveur remote HTTPS optionnel
Bearer auth hash-only + RBAC READ/WRITE/ADMIN
backup / verify / restore offline SQLite
reasoning fondé sur preuves
faits séparés des inférences et suggestions
CLI / MCP STDIO / HTTP API
setup Windows per-user
archives portables Windows/Linux avec runtime Java
CycloneDX + provenance de build
```

## 6. Responsabilités et invariants

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
           + evidence-backed assisted claims separated from published facts

MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
provider identifier != DomainIdentity
source path != identity
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
stale revision != overwrite
idempotent retry != duplicate mutation/audit
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
dry-run != mutation
local mode remains first-class
remote mode is opt-in
authentication != authorization
facts != inference
inference != suggestion
reasoning execution != lifecycle mutation
```

## 7. Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate avant août 2026. Les qualifications locales Windows et Linux/WSL sur le même SHA exact restent la source de vérité.
