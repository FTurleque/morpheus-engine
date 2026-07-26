# M18 — Real Providers & Multi-Provider Composition

Statut : **🚧 EN COURS — S1→S6 à implémenter, S7 gate final**

Dernière mise à jour : 26 juillet 2026

Issue : **#83**  
Branche : `m18/real-providers-multi-provider-composition`

## 1. Question de sortie

> **MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?**

## 2. Baseline d'entrée

```text
C0 -> M17 + D0  ✅ validés / intégrés
M17 merge        02bdb38669efc85af17343d15e689743362d2e12
M17 gate         410/410 PASS
Architecture     167/167 PASS
Packaging Win    PASS
main              5ff4f376f480252da71c1d5f88538a0396013b2d
```

## 3. Invariants M18

```text
provider identifier != DomainIdentity
source path != identity
provider-specific types never leak into domain/application contracts
conflict != silent last-write-wins
precedence must be explicit and deterministic
ambiguous continuity must be surfaced
optional provider absence != project failure
required provider failure != silently ignored
provider provenance survives snapshot persistence/reopen
one provider failure must not erase successful optional-provider reads
```

## 4. Architecture cible

```text
OpenSpec reader ───────┐
                       │
Structured Markdown ───┼─> SpecificationContentReader[]
                       │          ↓
future providers ──────┘   MultiProviderCompositionService
                                  ↓
                       explicit precedence + reconciliation
                                  ↓
                    NormalizedProjectContent + CompositionReport
                                  ↓
                       ProjectSnapshotImportService
                         ├─ business content
                         ├─ traceability
                         └─ composition report
                                  ↓
                              ACTIVE snapshot
```

Le rapport de composition est snapshot-scoped et séparé du contenu métier. Il décrit les contributions provider, les décisions de precedence et les conflits/résolutions sans injecter de type spécifique à OpenSpec ou Markdown dans le domaine.

## 5. Slices

### M18-S1 — Contrats provider-neutral

- [ ] `ProviderCompositionSource` : reader + precedence + required/optional ;
- [ ] `ProviderContribution` ;
- [ ] `ProviderCompositionConflict` ;
- [ ] `ProviderCompositionReport` ;
- [ ] `ComposedProjectContent` ;
- [ ] invariants de canonical ordering / duplicate rejection ;
- [ ] ADR-0085.

### M18-S2 — Structured Markdown réel

Convention source :

```text
.morpheus/specs/*.md
```

Format minimal versionné :

```markdown
---
morpheus-format: 1
spec: payments
title: Payments
---

# Requirements

## REQ-PAY-001 — Refuser les paiements invalides
Le système refuse un paiement dont la validation échoue.

### Scenario — Carte expirée
Given: une carte expirée
When: le paiement est soumis
Then: le paiement est refusé
```

- [ ] probe réel / capabilities ;
- [ ] lecture déterministe UTF-8 ;
- [ ] specifications/requirements/scenarios normalisés ;
- [ ] provenance/evidence provider `markdown` ;
- [ ] diagnostics structurels explicites ;
- [ ] tests provider ;
- [ ] ADR-0084.

### M18-S3 — Composition déterministe

Réconciliation explicite :

```text
Specification logical key = normalized specification.key
Requirement logical key   = normalized requirement.key lorsqu'elle existe
Scenario logical key      = requirement logical key + normalized scenario.title
```

Règles :

```text
higher precedence wins
same precedence -> ProviderId lexical tie-breaker uniquement si contenu équivalent
same precedence + contenu différent -> conflit non résolu / diagnostic ERROR
lower-precedence difference -> conflit RESOLVED_BY_PRECEDENCE, jamais silencieux
missing logical key -> pas de fusion inter-provider
references requirement/specification sont remappées vers l'identité retenue
all source evidence remains observable
```

- [ ] composition OpenSpec + Markdown ;
- [ ] optionnel absent toléré ;
- [ ] required failure bloquant ;
- [ ] provenance du gagnant conservée ;
- [ ] conflit explicable/queryable.

### M18-S4 — Persistance / reopen

- [ ] `ProviderCompositionReportStore` ;
- [ ] Memory adapter ;
- [ ] SQLite **V012** ;
- [ ] contribution provider + precedence + outcome ;
- [ ] conflicts + contenders + resolution ;
- [ ] close/reopen exact ;
- [ ] import atomiquement cohérent avant activation ;
- [ ] ADR-0086.

### M18-S5 — Composition root production

- [ ] OpenSpec et Markdown enregistrés comme vrais readers ;
- [ ] policy precedence explicite ;
- [ ] sync CLI/API utilise la composition lorsque plusieurs providers sont supportés ;
- [ ] workspace OpenSpec-only reste compatible ;
- [ ] workspace Markdown-only devient supporté ;
- [ ] workspace mixte produit un seul snapshot cohérent.

### M18-S6 — Surfaces

- [ ] query service snapshot/ACTIVE ;
- [ ] CLI `providers composition --project ID` ;
- [ ] MCP `get_provider_composition` read-only ;
- [ ] HTTP `GET /api/v1/projects/{projectId}/provider-composition` ;
- [ ] OpenAPI **1.7.0** ;
- [ ] docs utilisateur/développeur ;
- [ ] packaging inclut le provider Markdown, V012 et les surfaces M18.

### M18-S7 — Gate final

```text
at least two real providers validated                         MUST PASS
same project can consume OpenSpec + Markdown                  MUST PASS
precedence deterministic                                      MUST PASS
conflicts explicit and queryable                              MUST PASS
ambiguous same-precedence conflict blocks publication         MUST PASS
optional provider absence does not fail project               MUST PASS
Memory == SQLite composition report                           MUST PASS
SQLite close/reopen preserves provider provenance/report      MUST PASS
no provider-specific type leaks into domain/application       MUST PASS
CLI/MCP/HTTP coherent                                         MUST PASS
full Maven reactor                                            MUST PASS
Windows packaging + smokes                                    MUST PASS
```

## 6. Gouvernance

ADR M18 restent **Proposées** jusqu'au gate vert.  
La PR M18 reste Draft jusqu'à S7.  
Aucune fusion sans autorisation explicite.