---
applyTo: "**/src/main/java/**,**/src/test/java/**"
---

# Style de code

Aucun linter n'est configuré. La cohérence vient du code existant et des tests
d'architecture. Détail complet, exemple annoté : `.claude/rules/code-style.md` (source
partagée avec Claude Code).

## TOUJOURS

- Records Java ou classes `final` à champs `final` dans `morpheus-domain`
- Passer par les types du domaine comme langage commun entre couches (`DomainIdentity`,
  `ProjectSpecificationId`, `ProviderId`…)
- Déléguer la version à `ProductMetadata.version()`/`.current()` — jamais de littéral
- Valider aux **frontières système** (entrée CLI, endpoint HTTP, outil MCP), faire
  confiance au code interne
- Rendre les échecs **explicites** : exception nommée plutôt que retour silencieux
- Nommer les états de rejet explicitement (`SCAN_INCOMPLETE`, `PLUGIN_SHA256_REQUIRED`…)
- Valider l'invariant dans le **constructeur compact** d'un record, pas dans une méthode
  `validate()` séparée qu'on pourrait oublier d'appeler

## JAMAIS

- Jamais de commentaire qui explique **quoi** fait le code — le nom s'en charge
- Jamais de commentaire référençant la tâche courante, un ticket, ou un appelant
- Jamais d'abstraction prématurée — trois lignes similaires ne justifient pas un helper
- Jamais de gestion d'erreur pour un cas impossible
- Jamais de feature flag ni de shim de compatibilité rétroactive
- Jamais de code mort (variables inutilisées, re-exports vides, blocs commentés)
- Jamais de `last-write-wins` silencieux — un conflit se signale
- Jamais de dégradation silencieuse — préférer l'échec explicite au fallback implicite

## Déterminisme

- Ordonner explicitement toute collection retournée par une requête
- Sérialiser en JSON canonique (`CanonicalJsonSerializer`) pour toute sortie comparable
- Ne jamais laisser un `HashMap`/`HashSet` déterminer un ordre observable
- Borner toute traversée (profondeur, nœuds, liens) et exposer une raison de troncature

## Sémantique tri-state

`UNKNOWN` n'est jamais silencieusement converti en `BLOCKED` ou en `PASS`. Si une
information manque, la réponse le dit.

