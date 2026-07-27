# Feuille de route — MORPHEUS

Statut : **C0 à M20 + D0 validés et intégrés — D1 en cours — M21 prochain jalon**

Dernière mise à jour : 27 juillet 2026

MORPHEUS est piloté par des preuves : contrats stables, ADR cohérentes, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

La trajectoire active 1.x est [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md). La trajectoire [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) est désormais historique et couvre D0 + M15→M20.

## 1. Baseline intégrée

```text
C0 → M14      ✅ validés et intégrés
D0            ✅ validé et intégré
M15           ✅ validé et intégré
M16           ✅ validé et intégré
M17           ✅ validé et intégré
M18           ✅ validé et intégré
M19           ✅ validé et intégré
M20           ✅ validé et intégré
```

Référence M20 :

```text
Issue          #92 CLOSED / completed
PR             #93 MERGED
Code qualifié  9199ed43c4bd8596a97db055eeff17ae31399eb8
Merge          75d0b82ab0c960692db2fee1ced146fa6547fd4a
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

Preuve : [`VALIDATION_M20.md`](../validation/VALIDATION_M20.md).
Plan : [`M20_EXECUTION.md`](../roadmap/M20_EXECUTION.md).
ADR-0088 : **Acceptée — M20**.

## 2. Capacités acquises au niveau 1.0

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
MINOS optionnel
NEXUS optionnel
JARVIS orchestration boundary
CLI / MCP STDIO / HTTP API
Memory + SQLite
setup Windows per-user
archives portables Windows/Linux
runtime Java embarqué
release builders exact-tag + SHA-256 + manifests
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
optional provider absence != project failure when optional
optional engine absence != MORPHEUS failure
MORPHEUS rules != JARVIS action sequencing
```

## 4. Publication MORPHEUS 1.0.0

M20 prouve que les artefacts peuvent être construits, installés et exécutés. La publication GitHub officielle reste une opération distincte :

```text
R1 = créer tag stable v1.0.0 sur le commit intégré retenu
     + reconstruire depuis ce tag exact
     + publier setup/ZIP/tar.gz + checksums + manifests
     + publier release notes
```

M20 intégré != GitHub Release déjà publiée.

## 5. Trajectoire active 1.x

### NOW

| Sujet | Statut | But |
|---|---|---|
| **R1** | ⏭ À FAIRE | Publication officielle MORPHEUS `v1.0.0` |
| **D1** | 🚧 EN COURS — #94 | Consolidation post-M20 et bascule vers roadmap 1.x |
| **M21** | ⏭ PROCHAIN JALON | Production Integrity & Surface Convergence |

### NEXT

| Jalon | Sujet | Question centrale |
|---|---|---|
| **M22** | Provider SDK & Plugin Discovery Platform | Ajouter des providers sans modifier le core ? |
| **M23** | Multi-project / Portfolio Specification Intelligence | Raisonner entre projets sans confondre identité et source ? |
| **M24** | Query DSL, Saved Views & Export/Reporting | Exprimer et partager des vues complexes provider-neutral ? |

### LATER

| Jalon | Sujet | Direction |
|---|---|---|
| **M25** | Policy Packs & Governance Automation | Politiques versionnées, explicables et auditables |
| **M26** | Optional Team/Remote Server Mode | Usage équipe sans abandonner local-first |
| **M27** | Evidence-backed Assisted Reasoning | Inférences optionnelles séparées des faits |

Détail : [`POST_M20_EVOLUTION.md`](../roadmap/POST_M20_EVOLUTION.md).

## 6. M21 — Production Integrity & Surface Convergence

Question de sortie proposée :

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

Axes prévus :

```text
CI reproductible et durable
quality gates / coverage
nettoyage Maven / dépendances / warnings
convergence CLI / MCP / HTTP
documentation single-source-of-truth
SBOM / provenance / signatures / trust policy
update channel / version discovery sans auto-mutation
qualification exact-head Windows + Linux
```

M21 ne doit pas introduire de logique métier nouvelle avant d’avoir stabilisé ces surfaces et gates.

## 7. Règle de pilotage

```text
1. issue canonique
2. plan Mxx_EXECUTION avec NOW / NEXT / LATER et slices
3. question de sortie + invariants
4. ADR avant décision structurante
5. vertical slices
6. tests backend/adapters réels pertinents
7. gate exact-head
8. VALIDATION_Mxx avec SHA et résultats réels
9. ADR acceptée seulement après preuve
10. PR Ready seulement après gate vert
11. merge uniquement après autorisation explicite
12. réconciliation roadmap/index après merge
```

Les preuves de validation restent historiques. Les roadmaps actives reflètent l’état GitHub courant sans réécrire la chronologie des gates.