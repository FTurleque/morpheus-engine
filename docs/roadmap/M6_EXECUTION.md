# M6 — Plan d'exécution détaillé

Statut : **M6 actif — 0/6 intégrés ; S1 implémenté, gate en attente**

Dernière mise à jour : 23 juillet 2026

Ce document complète [`../ROADMAP.md`](../ROADMAP.md) et pilote l'exécution de M6.

---

# 1. Baseline

```text
M5 = VALIDÉ ET INTÉGRÉ
M5 final merge = 6bbaf086cf1fed81e3517bb1cef5b643264fb836
M5 final gate  = 227/227 PASS
```

Issue de pilotage : **#43**.

---

# 2. Question de sortie M6

> **MORPHEUS peut-il détecter et expliquer les lacunes de qualité d'une spécification sur un snapshot publié, mesurer sa couverture, exposer les blocages et références cassées, tout en distinguant strictement les constats déterministes des heuristiques et sans inventer les relations absentes ?**

La porte finale doit démontrer :

```text
ACTIVE by default
ACTIVE/RETIRED explicit inspection
CURRENT isolation where applicable
quality findings machine-readable
DETERMINISTIC != HEURISTIC
heuristic confidence explicit
orphan detection
traceability coverage
implementation-task coverage
acceptance capability gap explicit
change completeness
lifecycle blockers
design-decision justification
broken/unresolved references
stable aggregate metrics/order
Memory == SQLite
SQLite reopen
no LLM/semantic dependency
```

---

# 3. Progression M6

```text
S1  🚧 requirement traceability coverage + orphan requirements — PR #44 — ADR-0048 proposée — gate attendu 234
S2  ⏳ implementation-task coverage + acceptance capability gap
S3  ⏳ change completeness + lifecycle blocking conditions
S4  ⏳ design-decision justification + broken/unresolved reference quality
S5  ⏳ aggregate quality report + stable metrics/order + compact exposure
S6  ⏳ validation finale VALIDATION_M6.md
```

```text
M6 : 0 / 6 slices intégrés
```

---

# 4. Principes

```text
quality finding != ingestion diagnostic
finding = résultat dérivé, pas entité persistée
snapshot-scoped
CURRENT only pour Requirement
absence de lien != lien inventé
Scenario != AcceptanceCriterion
DETERMINISTIC != HEURISTIC
heuristic finding => confidence obligatoire
provider-neutral
backend-neutral
```

Aucune heuristique n'est présentée comme certitude.

---

# 5. M6-S1 — IMPLÉMENTÉ / GATE EN ATTENTE

ADR : **ADR-0048 — Proposée — M6**  
PR : **#44 — Draft**  
Branche : `m6/requirement-quality-coverage`

Contrats :

```text
QualityFinding
QualityFindingCode
QualityEvidenceKind
RequirementTraceabilityCoverage
RequirementQualityService
```

Sémantique :

```text
ACTIVE by default
ACTIVE/RETIRED explicit only
CURRENT Requirement only
linked = >= 1 direct incoming/outgoing persisted TraceabilityLink
orphan = no direct incoming AND no direct outgoing link
coverage = linked / total CURRENT requirements
zero CURRENT requirements = coverage 1.0
ORPHAN_REQUIREMENT = WARNING + DETERMINISTIC
finding evidence = Requirement provenance evidence
```

Les relations ne sont pas filtrées en S1 : tout lien direct persisté constitue une couverture structurelle. Les références cassées ou faibles seront diagnostiquées séparément en S4 ; elles ne sont pas transformées en absence de trace.

Preuves ajoutées : **7 tests** dans `RequirementQualityContractTest` :

```text
Memory == SQLite
incoming link covers
outgoing link covers
PROPOSED excluded
orphan deterministic + evidence retained
RETIRED allowed / READY rejected
missing ACTIVE != empty ACTIVE population
zero requirements => 100 %
heuristic confidence contract
SQLite reopen
```

Baseline : **227/227 PASS**.  
Gate attendu : **234/234**, dont **107 tests d'architecture**.

---

# 6. M6-S2 — Task / acceptance coverage

Cibles :

```text
ImplementationTask sans Requirement relié
couverture task -> requirement
absence de sémantique AcceptanceCriterion explicite signalée sans convertir Scenario
```

`Scenario != AcceptanceCriterion` reste bloquant.

---

# 7. M6-S3 — Changement et lifecycle

Cibles :

```text
change incomplet
conditions de blocage de transition
état lifecycle explicite
faits structurels uniquement pour diagnostics déterministes
```

---

# 8. M6-S4 — Décisions et références

Cibles :

```text
decision sans justification/trace suffisante
external unresolved
external stale
broken reference
qualité de résolution explicite
```

---

# 9. M6-S5 — Rapport qualité agrégé

Stabiliser :

```text
QualityReport
metrics stables
findings triés
counts par code/severity/evidence kind
compact exposure
published snapshot metadata
```

Aucune persistance de rapport si elle n'est pas nécessaire.

---

# 10. M6-S6 — Validation finale

Créer :

```text
docs/VALIDATION_M6.md
```

Répondre explicitement à la question de sortie et prouver la parité des backends ainsi que la séparation déterministe/heuristique.

---

# 11. Gouvernance

```text
1. branche dédiée depuis le merge exact précédent
2. ADR proposée avant code si décision structurelle
3. PR Draft avant implémentation substantielle
4. tests contractuels ciblés
5. gate Windows .\mvnw.cmd clean test
6. ADR acceptée uniquement après preuve
7. PR Ready uniquement après gate vert
8. merge uniquement après signal explicite
9. issue #43 + roadmap mises à jour
```

**Prochaine porte : gate local M6-S1 attendu 234/234.**
