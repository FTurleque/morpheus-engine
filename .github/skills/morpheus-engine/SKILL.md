---
name: morpheus-engine
description: "Connaissances spécifiques au dépôt Morpheus Engine — architecture ports & adapters, gates ArchUnit, ratchets de qualité vivants, invariants de sécurité, convergence des contrats CLI/MCP/HTTP. USE FOR: revue de diff Morpheus, question sur un gate milestone (M19-M28, D2), audit de gouvernance/sécurité du dépôt, question sur contracts/public-surfaces.tsv, question sur les ratchets de coverage. DO NOT USE FOR: questions Azure/Foundry génériques, autres dépôts."
---

# Skill Morpheus Engine

Cette skill encapsule le *comment naviguer* le savoir du dépôt `morpheus-engine`, pas le
savoir lui-même — les valeurs concrètes (seuils, comptes, versions) ne sont **jamais**
recopiées ici : elles vivent dans le code et doivent être relues à chaque usage.

## Principe directeur du dépôt

Les règles de Morpheus Engine sont **exécutables, pas déclaratives**. Toute règle
architecturale, de sécurité ou de gouvernance est vérifiée par un test ArchUnit ou un
script sous `morpheus-architecture-tests/` / `scripts/validate-m*`. Avant de répondre à
une question structurelle : lire le test concerné, puis l'ADR associé dans `docs/adr/`.

## Où trouver quoi (carte du dépôt, pas de contenu dupliqué)

| Question | Fichier à lire en premier |
|---|---|
| Seuils de coverage / nombre de tests minimum | `config/m21-quality-ratchets.properties` |
| Règle de dépendance entre modules | `morpheus-architecture-tests/.../LayerDependencyTest.java` |
| Invariant de sécurité exact (chaîne attendue) | `morpheus-architecture-tests/.../d2/D2RepositoryHardeningArchitectureTest.java` |
| Version produit courante | `pom.xml` (racine, balise `<version>`) puis `ProductMetadata` |
| Convergence CLI/MCP/HTTP d'une capacité | `contracts/public-surfaces.tsv` |
| Contrat OpenAPI d'un milestone | `docs/openapi/morpheus-v1-*.yaml` |
| Pourquoi une décision structurelle a été prise | `docs/adr/` (chercher par mot-clé, compter par `glob`) |
| Quadruplet d'un milestone (tests/scripts/plan/preuve) | `docs/roadmap/M<N>_EXECUTION.md` + `docs/validation/VALIDATION_M<N>.md` |
| Détail complet règle par règle (exemples réels annotés) | `.claude/rules/*.md` — source partagée Claude Code/Copilot |
| Ciblage Copilot par chemin de fichier | `.github/instructions/*.instructions.md` |
| Workflows guidés (audit, validation, bug-fix) | `.github/prompts/*.prompt.md`, point d'entrée `morpheus-orchestrator.prompt.md` |

## Anti-dérive — règle non négociable de cette skill

Un audit a déjà détecté trois valeurs différentes pour le même ratchet de coverage citées
dans trois fichiers de documentation distincts, alors que `config/m21-quality-ratchets.properties`
donnait une quatrième valeur. En conséquence :

1. **Ne jamais citer un chiffre périssable** (seuil de coverage, nombre de tests, nombre
   d'ADR, nombre de modules, version) sans l'avoir relu dans sa source vivante durant la
   session courante.
2. Si un chiffre trouvé dans le code diverge d'un chiffre écrit dans une documentation du
   dépôt (y compris cette skill), **le signaler explicitement** et proposer la correction
   du fichier concerné dans le même changement.
3. Ne jamais supposer qu'un fichier de documentation est plus récent que le code — en cas
   de doute, le code et les scripts `scripts/validate-m*` font foi.

## Modèle architectural (rappel structurel, pas de chiffres)

`morpheus-application` définit les **ports** et ne dépend d'aucun adaptateur.
Les adaptateurs (`provider-*`, `store-*`, `api`, `mcp`, `integration-*`) implémentent ces
ports et dépendent vers l'intérieur ; ils sont **frères** entre eux et ne s'appellent
jamais directement. Seul `morpheus-cli` fait le câblage explicite (composition root).
Aucun framework applicatif (pas de Spring/Micronaut), aucun Docker.

## Procédure recommandée pour toute tâche Morpheus

1. Identifier le ou les modules touchés
2. Lire le test ArchUnit ou le fichier de configuration source qui gouverne ce domaine
3. Si la tâche a un impact décisionnel (gate, PR, seuil), relire la source vivante
   correspondante avant de conclure — ne jamais recopier une valeur de cette skill ou
   d'une autre page de documentation
4. Vérifier la convergence des contrats si une surface publique change
5. Valider avec les commandes Maven listées dans `.github/instructions/build.instructions.md`


