# ADR-0084 — Structured Markdown comme deuxième provider réel

Statut : **Proposée — M18**

Date : 26 juillet 2026

## Contexte

M2 prouve l'anti-lock-in avec OpenSpec et un provider Synthetic. Cette preuve est architecturale mais le provider Synthetic n'est pas une source utilisateur réelle. M18 exige au moins deux providers réels et réutilisables.

## Décision

Introduire `morpheus-provider-markdown`, provider local read-only basé sur des fichiers Markdown structurés sous :

```text
.morpheus/specs/*.md
```

Le format est volontairement petit, déterministe et versionné :

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

Le provider :

- implémente les ports existants `SpecificationProvider` et `SpecificationContentReader` ;
- normalise `Specification`, `Requirement`, `Scenario`, `Evidence`, `Provenance` ;
- utilise `ProviderId("markdown")` ;
- ne dépend d'aucun type OpenSpec ;
- ne déduit pas de change, constraint, acceptance criterion ou référence externe non explicitement modélisés par ce format ;
- émet des diagnostics structurés pour tout fichier ou bloc invalide ;
- lit UTF-8 et trie les fichiers/éléments de façon déterministe.

## Pourquoi Markdown structuré

- source locale et réelle ;
- utilisable sans service tiers ;
- facile à adopter dans un dépôt existant ;
- suffisamment différent d'OpenSpec pour prouver le découplage ;
- permet de concentrer M18 sur la composition plutôt que sur une authentification API distante.

## Alternatives rejetées pour M18

### GitHub Issues / GitLab / Jira

Intéressants mais ils introduisent simultanément réseau, authentification, pagination et rate limiting. Ils restent candidats après la stabilisation du contrat de composition.

### Réutiliser Synthetic comme deuxième provider

Rejeté : il ne satisfait pas le gate « deux providers réels ».

## Conséquences

- nouveau module Maven `morpheus-provider-markdown` ;
- CLI/API/MCP packagés embarquent le provider ;
- OpenSpec reste supporté sans modification de format ;
- un workspace peut contenir OpenSpec, Markdown ou les deux.

## Validation attendue

ADR acceptée uniquement après :

```text
probe Markdown PASS
read specification/requirement/scenario PASS
provenance/evidence PASS
invalid format diagnostics PASS
OpenSpec + Markdown composition PASS
packaging provider Markdown PASS
```