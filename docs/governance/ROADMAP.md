# Feuille de route — MORPHEUS

Statut : **C0 à M19 + D0 validés et intégrés — M20 validé techniquement, PR #93 non mergée**

Dernière mise à jour : 27 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

La baseline C0→M19 est acquise. M20 a passé ses gates Windows et Linux sur le même SHA de code exact ; la PR #93 est prête à être revue après contrôle du delta documentaire post-gate et reste soumise à une autorisation explicite de merge. La trajectoire post-M14 est détaillée dans [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md). La politique documentaire est [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md).

## 1. Vue globale

| Jalon | Sujet | Statut | Preuve / porte |
|---|---|---|---|
| C0 | Cadrage fonctionnel et architectural | ✅ VALIDÉ | [`VALIDATION_C0.md`](../validation/VALIDATION_C0.md) |
| M0 | Faisabilité technique | ✅ VALIDÉ | [`VALIDATION_M0.md`](../validation/VALIDATION_M0.md) |
| M1 | Discovery, providers et fondation store | ✅ VALIDÉ | 42/42 |
| M2 | Ingestion et modèle normalisé | ✅ VALIDÉ | 94/94 |
| M3 | Temporalité, lifecycle, snapshots, versions | ✅ VALIDÉ / INTÉGRÉ | 147/147 |
| M4 | Traçabilité typée | ✅ VALIDÉ / INTÉGRÉ | 189/189 |
| M5 | Requêtes et contexte compact | ✅ VALIDÉ / INTÉGRÉ | 227/227 |
| M6 | Qualité, couverture et diagnostics | ✅ VALIDÉ / INTÉGRÉ | 261/261 |
| M7 | Synchronisation incrémentale et fraîcheur | ✅ VALIDÉ / INTÉGRÉ | 282/282 |
| M8 | Analyse des changements | ✅ VALIDÉ / INTÉGRÉ | 289/289 |
| M9 | CLI stabilisée et distribution locale | ✅ VALIDÉ / INTÉGRÉ | 298/298 Windows + Linux |
| M10 | Serveur MCP STDIO natif | ✅ VALIDÉ / INTÉGRÉ | 307/307 |
| M11 | API HTTP headless | ✅ VALIDÉ / INTÉGRÉ | 314/314 |
| M12 | MINOS optionnel / intention → code | ✅ VALIDÉ / INTÉGRÉ | 331/331 |
| M13 | NEXUS optionnel / intention → contexte technique | ✅ VALIDÉ / INTÉGRÉ | 346/346 |
| M14 | JARVIS / orchestration read-only | ✅ VALIDÉ / INTÉGRÉ | 357/357 + JARVIS 536 tests |
| D0 | Réconciliation documentaire post-M14 | ✅ VALIDÉ / INTÉGRÉ — PR #75 | [`VALIDATION_D0.md`](../validation/VALIDATION_D0.md) |
| M15 | Acceptance Criteria, Verification & Evidence | ✅ VALIDÉ / INTÉGRÉ — PR #77 | 371/371 + packaging PASS |
| M16 | Constraint Semantics & Policy Enforcement | ✅ VALIDÉ / INTÉGRÉ — PR #79 | 393/393 + Architecture 161/161 + packaging PASS |
| M17 | Controlled Lifecycle & Write Operations | ✅ VALIDÉ / INTÉGRÉ — PR #81 | 410/410 + Architecture 167/167 + packaging PASS |
| M18 | Real Providers & Multi-Provider Composition | ✅ VALIDÉ / INTÉGRÉ — PR #86 | 418/418 + Architecture 170/170 + packaging PASS |
| M19 | Production Hardening, Scale & Operability | ✅ VALIDÉ / INTÉGRÉ — PR #89 | 449/449 + Architecture 178/178 + Windows/Linux + budgets + packaging PASS |
| **M20** | **Release Engineering, Installation PROD & MORPHEUS 1.0** | **✅ VALIDÉ TECHNIQUEMENT — PR #93 NON MERGÉE** | **454/454 + Architecture 182/182 + Windows/Linux + setup/portable/no-JDK/upgrade/uninstall PASS** |

Plans et preuves récentes :

- [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — trajectoire stratégique post-M14 ;
- [`M18_EXECUTION.md`](../roadmap/M18_EXECUTION.md) / [`VALIDATION_M18.md`](../validation/VALIDATION_M18.md) ;
- [`M19_EXECUTION.md`](../roadmap/M19_EXECUTION.md) / [`VALIDATION_M19.md`](../validation/VALIDATION_M19.md) ;
- [`M20_EXECUTION.md`](../roadmap/M20_EXECUTION.md) / [`VALIDATION_M20.md`](../validation/VALIDATION_M20.md).

## 2. Baseline intégrée jusqu’à M19

```text
M18 issue       #85 CLOSED / completed
M18 PR          #86 MERGED
M18 code        7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge       30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests       418/418 PASS
M18 architecture 170/170 PASS

M19 issue       #88 CLOSED / completed
M19 PR          #89 MERGED
M19 merge       762b6dedd0760f8e08722ef5ee5dcf5057309574
M19 Windows     PASS
M19 Linux       PASS ext4 / WSL2
M19 tests       449/449 PASS
M19 architecture 178/178 PASS
M19 reactor     14/14 SUCCESS
M19 packaging   PASS Windows + Linux
```

## 3. Merges actifs de la baseline

```text
M12 = 86dbb1d50e87ce354b7174156e9c8c5717722a17
M13 = 2f6d0df95d6e58d12a57a1ff2e31cdad636b5d8f
M14 MORPHEUS = 88e4e4d83c25035b9441e78d0ac8145db83306c4
M14 JARVIS   = 1bf2612e616f3323814caf60e76525b4808cd400
D0  = ec75d3963422d6281f2904c5ebd547124db92ad6
M15 = c37134439844cb088adff855c339a259bb908b6a
M16 = 97308005a63854c7cb08dc19cd3cdb02ac739404
M17 = 02bdb38669efc85af17343d15e689743362d2e12
M18 = 30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M19 = 762b6dedd0760f8e08722ef5ee5dcf5057309574
```

## 4. Responsabilités non négociables

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts

MINOS    = code intelligence

NEXUS    = context selection
           + ranking
           + fusion
           + compression

JARVIS   = sequencing
           + orchestration
           + action choice
```

## 5. Invariants structurants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot

PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE

Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion

UNKNOWN != FAILED
UNKNOWN != BLOCKED
applicable != blocking
warning != blocker
severity != blocking policy

transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit

provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
optional provider absence != project failure when optional

optional engine absence != MORPHEUS failure
MORPHEUS rules != JARVIS action sequencing

programme != données persistantes
update programme != reset knowledge store
uninstall programme != delete knowledge store
runtime utilisateur != JDK utilisateur
```

## 6. Gates validés récents

```text
M14 357/357 | Architecture 160/160 | packaging PASS | JARVIS 536 tests BUILD SUCCESS
D0  documentation authority PASS | primary links PASS | historical evidence preserved
M15 371/371 | Architecture 157/157 | packaging + smokes PASS
M16 393/393 | Architecture 161/161 | packaging + smokes PASS
M17 410/410 | Architecture 167/167 | packaging + smokes PASS
M18 418/418 | Architecture 170/170 | packaging + smokes PASS
M19 449/449 | Architecture 178/178 | Windows + Linux | budgets + packaging + smokes PASS
M20 454/454 | Architecture 182/182 | Windows + Linux | setup + portable + no-JDK + upgrade/uninstall PASS
```

## 7. Jalon qualifié — M20

Question de sortie :

> **MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant les archives portables pour l’automatisation ?**

Verdict : **OUI**, pour MORPHEUS 1.0.0 et les environnements enregistrés dans la preuve M20.

```text
Code SHA       9199ed43c4bd8596a97db055eeff17ae31399eb8
Windows        PASS
Linux          PASS sur ext4 / WSL2
Tests          454/454 PASS, 0 failure/error/skipped
Architecture   182/182 PASS
Reactor        14/14 SUCCESS
Setup Windows  PASS
Portable Win   PASS
Portable Linux PASS
SHA-256        PASS Windows + Linux
No-user-JDK    PASS Windows + Linux
PATH           PASS
Upgrade        PASS
Uninstall      PASS
Exact tag/SHA  PASS Windows + Linux
```

La preuve Linux provient d’une exécution Linux réelle sur le même SHA de code que Windows ; elle n’est pas inférée du gate Windows.

ADR : [`ADR-0088`](../adr/0088-product-release-installation-and-persistent-data-separation.md) — **Acceptée — M20**.  
Preuve : [`VALIDATION_M20.md`](../validation/VALIDATION_M20.md).

## 8. État de publication 1.0

M20 est techniquement terminé. Les commits post-gate servent uniquement à enregistrer les preuves et réconcilier la documentation. Avant passage de #93 en Ready, le diff entre `9199ed43c4bd8596a97db055eeff17ae31399eb8` et le head final doit confirmer l’absence de delta exécutable.

La création du tag stable `v1.0.0` et de la GitHub Release appartient à la phase d’intégration/publication après merge autorisé ; elle ne doit pas précéder la décision explicite du propriétaire.

## 9. Règle de pilotage

```text
1. documenter invariant / ADR
2. implémenter vertical slice cohérent
3. tester backend/adapters réels selon le contrat
4. lancer gate local complet
5. accepter ADR seulement après preuve
6. passer la PR Ready seulement après gate vert
7. merger uniquement après autorisation explicite
8. réconcilier immédiatement les roadmaps/index après merge
9. supprimer les branches de jalon devenues obsolètes
```

Les fichiers de validation restent des preuves historiques : ils ne sont jamais réécrits pour faire croire qu’un merge existait au moment d’un gate qui le précédait.
