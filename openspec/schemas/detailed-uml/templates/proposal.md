# Proposition : <!-- titre court du changement -->

<!-- Garder les titres `## Why`, `## What Changes` et `## Capabilities` tels quels :
     l'archivage OpenSpec les exige. Les autres titres sont ceux du projet. -->

## Why

<!-- Le problème ou l'opportunité, en deux à cinq phrases. Citer l'état constaté qui le
     prouve : chemin de fichier, test, mesure ou audit daté. Dire pourquoi maintenant. -->

## What Changes

<!-- Liste précise : ajout, modification ou retrait, et sur quelle surface (CLI, outil MCP,
     route HTTP, schéma SQLite, format persisté, gate de build, documentation).
     Marquer une rupture par **BREAKING**. -->
-

## Capabilities

### New Capabilities
<!-- Une ligne par capacité nouvelle : elle devient specs/<capability-path>/spec.md.
     Nom de comportement durable, en kebab-case, sans nouveau niveau de dossier.
     Supprimer la ligne d'exemple s'il n'y en a aucune. -->
- `<capability-path>` : <!-- ce que la capacité couvre -->

### Modified Capabilities
<!-- Capacités existantes dont une EXIGENCE change (pas seulement l'implémentation).
     Chemin exact sous openspec/specs/. Laisser vide sinon. Sans aucune capacité,
     poser `skip_specs: true` dans .openspec.yaml. -->
- `<existing-capability-path>` : <!-- exigence qui change, et dans quel sens -->

## Périmètre et exclusions

**Inclus :**
<!-- Ce que le changement couvre. -->

**Exclus :**
<!-- Ce qu'il ne couvre pas, explicitement, et où cela sera traité le cas échéant. -->

## Acteurs et surfaces concernés

<!-- Qui déclenche ou subit le comportement : utilisateur de la CLI, client MCP, client HTTP
     local, client distant et son rôle, mainteneur, CI, pair MINOS/NEXUS, auteur de plugin.
     Supprimer la section si le changement n'a pas d'acteur externe. -->

## Nature des informations

<!-- Classer les affirmations structurantes de cette proposition. -->

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| <!-- énoncé --> | exigence · existant vérifié · décision · proposition à valider · question ouverte | <!-- fichier, test, audit, ADR --> |

## Impact

<!-- Modules, paquets, contrats (contracts/public-surfaces.tsv, OpenAPI), schéma de
     persistance, ADR concernés ou à proposer, documentation durable et diagrammes à
     mettre à jour, tests d'architecture touchés. -->

## Questions ouvertes

<!-- Ce qui doit être tranché, et par qui. Écrire « Aucune » s'il n'y en a pas. -->
