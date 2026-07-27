# Feuille de route — MORPHEUS

Statut : **C0 à M18 + D0 validés et intégrés — M19 qualifié techniquement, non mergé**

Dernière mise à jour : 27 juillet 2026

La roadmap MORPHEUS est pilotée par des preuves : contrats stables, ADR cohérentes, tests reproductibles et réponse explicite à chaque question de sortie.

La baseline C0→M18 est acquise. M19 a passé ses gates Windows et Linux sur le même SHA de code exact ; la PR #89 attend revue et autorisation de merge. La suite officielle est définie dans [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md). La politique documentaire est [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md).

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
| **M18** | **Real Providers & Multi-Provider Composition** | **✅ VALIDÉ / INTÉGRÉ — PR #86** | **418/418 + Architecture 170/170 + packaging PASS** |
| **M19** | **Production Hardening, Scale & Operability** | **✅ VALIDÉ TECHNIQUEMENT — PR #89 NON MERGÉE** | **449/449 + Architecture 178/178 + Windows/Linux + budgets + packaging PASS** |
| M20 | Release Engineering, Installation PROD & MORPHEUS 1.0 | ⏳ PLANIFIÉ / BLOQUÉ | démarre après merge M19 ; setup/release/upgrade/uninstall/Linux |

Plans :

- [`POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — trajectoire stratégique post-M14 ;
- [`M18_EXECUTION.md`](../roadmap/M18_EXECUTION.md) — exécution M18 terminée ;
- [`M19_EXECUTION.md`](../roadmap/M19_EXECUTION.md) — exécution M19 techniquement terminée ;
- [`VALIDATION_M19.md`](../validation/VALIDATION_M19.md) — preuves exact-head Windows et Linux.

## 2. Baseline M18 autoritative

```text
M18 issue       #85 CLOSED / completed
M18 PR          #86 MERGED
code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
tests           418/418 PASS
architecture    170/170 PASS
failures        0
errors          0
skipped         0
reactor         14/14 modules SUCCESS
packaging Win   PASS
packaged smokes PASS
API health      PASS
portable ZIP    33,919,431 bytes
OpenAPI         1.7.0
SQLite          V012
```

Providers réels validés dans le même projet :

```text
OpenSpec
+
Structured Markdown
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
        ↓
precedence explicite
provenance conservée
conflits explicites
        ↓
Memory / SQLite V012
        ↓
CLI / MCP / HTTP
```

Preuve : [`VALIDATION_M18.md`](../validation/VALIDATION_M18.md).  
ADR : [`ADR-0084`](../adr/0084-provider-neutral-multi-provider-composition.md) — **Acceptée — M18**.

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
```

## 7. Jalon qualifié — M19

Question de sortie :

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

Verdict : **OUI**, pour le profil gelé et les environnements enregistrés dans la preuve M19.

```text
Code SHA       dca27db969b426ad43941ccb8cee7e926efb931b
Windows        PASS
Linux          PASS sur ext4 / WSL2
Tests          449/449 PASS, 0 failure/error/skipped
Architecture   178/178 PASS
Reactor        14/14 SUCCESS
Budgets        PASS, seuils inchangés
Packaging      PASS Windows + Linux
```

Axes prouvés :

```text
performance / capacité
robustesse
observabilité locale-first
sécurité locale
reproductibilité Windows / Linux
```

Les budgets de performance et capacité ont été fixés **avant** toute optimisation. Le PASS Linux provient d'une exécution Linux réelle sur le même SHA de code ; il n'est pas inféré du gate Windows.

## 8. Direction M20

Après le merge explicitement autorisé de M19, M20 transformera le packaging existant en distribution produit : installation Windows par utilisateur, programme/données séparés, checksums, GitHub Releases, upgrade/uninstall et distribution Linux. M20 n'est pas démarré.

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
