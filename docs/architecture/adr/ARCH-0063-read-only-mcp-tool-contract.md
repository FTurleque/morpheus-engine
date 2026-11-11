---
id: ARCH-0063
title: "Catalogue MCP read-only et sémantique explicite"
domain: architecture
rules: false
---

# Catalogue MCP read-only et sémantique explicite

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

Le serveur MCP expose aux agents les lectures de MORPHEUS sur le snapshot publié : spécification courante,
exigences, changements, contraintes, critères d'acceptation, décisions, tâches, traçabilité, contexte de changement
et de spécification, statut et conditions bloquantes d'un changement, fraîcheur de synchronisation. Un agent lit ce
que l'outil rend comme un fait ; un outil qui comble une absence par une valeur inventée lui transmet un faux fait.

## Decision

La règle read-only vaut pour les outils MCP dont la ligne de `contracts/public-surfaces.tsv` porte l'intention `READ`.
La liste des outils servis et leur intention est celle du manifeste ; les outils `WRITE` ne relèvent pas de cet ADR
(les transitions de cycle de vie suivent ADR-0083 : capability d'écriture, confirmation, CAS).

Un handler d'outil `READ` fait uniquement :

```text
validate args
-> resolve ACTIVE/current state
-> call existing application service/store port
-> map deterministic response
```

Aucune règle métier essentielle n'est réimplémentée dans l'adaptateur MCP.

### L'absence est explicite

MORPHEUS ne transforme jamais un `Scenario` en `AcceptanceCriterion`, ni la présence d'un test en fait d'acceptance.
`get_acceptance_criteria` rend les seuls critères d'acceptation explicites persistés (ADR-0081), avec leur statut de
vérification et leurs preuves ; un changement sans critère rend une page vide.

Le contenu du snapshot publié ne porte pas d'état de cycle de vie explicite d'un changement, et MORPHEUS ne l'infère
pas :

```text
get_change_status -> status = UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT, lifecycleState = UNAVAILABLE
```

L'absence est une information explicite ; elle n'est jamais masquée par une valeur inventée.

### Résultats

Les réponses complexes réutilisent les vues compactes et le JSON canonique (ADR-0123) lorsque c'est possible. Les vues
propres au MCP restent déterministes, ordonnées et snapshot-scoped.

`get_specification_context` agrège uniquement les relations `AFFECTS` persistées. `get_blocking_conditions` réutilise
`ChangeCompletenessService` et les faits tri-état de qualité (ADR-0124).

## Do's and Don'ts

### Do

- Déclarer chaque outil MCP dans `contracts/public-surfaces.tsv` avec son intention.
- Résoudre un outil `READ` contre le snapshot `ACTIVE` et un service ou un port existant de l'application.
- Rendre une absence par un statut nommé ou une collection vide, jamais par une valeur inventée.
- Garder les réponses déterministes, ordonnées et snapshot-scoped.

### Don't

- Ne pas muter quoi que ce soit depuis un outil `READ`.
- Ne pas réimplémenter une règle métier dans un handler MCP.
- Ne pas présenter un `Scenario` ou la présence d'un test comme un critère d'acceptation.
- Ne pas inférer un état de cycle de vie que le snapshot publié ne porte pas.
- Ne pas recopier le total des outils servis : le manifeste en est la source.

## Consequences

### Positives

- Un agent distingue ce que MORPHEUS sait de ce qu'il ne sait pas.
- Les règles métier vivent dans l'application et sont partagées par CLI, MCP et HTTP.

### Négatives

- Un agent doit traiter les statuts d'indisponibilité au lieu de recevoir une valeur prête à l'emploi.

### Risques

- La justesse de la colonne `intent` du manifeste n'est vérifiée par aucun test : un outil qui écrit déclaré `READ`
  ne serait vu qu'en revue.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/mcp/PublicSurfaceManifestCoversEveryServedToolTest.java` :
  tout outil servi a sa ligne au manifeste, et toute ligne nomme un outil servi.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/CatalogueToolsAnswerTest.java`
  (`executesEntireReadOnlyCatalogWithoutInventingUnavailableSemantics`) : critères d'acceptation vides sans
  `UNAVAILABLE_IN_NORMALIZED_MODEL`, scénario jamais relabellisé, `lifecycleState` `UNAVAILABLE`.
- `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusMcpToolCatalogTest.java` : schémas d'entrée stricts et bornés.
- Revue : l'absence de mutation dans un handler `READ` et la justesse de l'intention déclarée.

## References

- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP Java officiel.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation explicites.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — mutations de cycle de vie contrôlées.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — findings de qualité et faits tri-état.
- `contracts/public-surfaces.tsv`
- [VALIDATION_M10](../../project/validation/VALIDATION_M10.md)
