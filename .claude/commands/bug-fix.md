# Commande /bug-fix

Workflow structuré de correction de bug pour Morpheus Engine.

## Usage
- `/bug-fix` → guide interactif de triage
- `/bug-fix <description>` → démarre avec la description du bug en $ARGUMENTS

## Workflow en 5 étapes

### Étape 1 — Triage
- Identifier le module affecté (domain / application / provider / store / api / mcp / cli)
- Déterminer si le bug touche un gate milestone (M19–M28)
- Classifier la sévérité : critique (régression gate) / majeure (comportement incorrect) / mineure (edge case)

### Étape 2 — Reproduction
- Chercher un test existant qui couvre le comportement : `grep -r "<symptôme>" src/test/`
- Si aucun test n'existe, écrire un test de reproduction minimal **avant** de corriger
- Le test doit échouer de manière déterministe avant le fix

### Étape 3 — Analyse causale
- Tracer le chemin d'exécution : de l'entrée (CLI/API/MCP) jusqu'au domaine
- Identifier le fichier et la ligne précise de la régression
- Vérifier si l'ADR pertinent existe : `docs/adr/`
- Contrôler les dépendances de module : le fix ne doit pas violer les règles ArchUnit

### Étape 4 — Correction minimale
- Appliquer le fix le plus petit possible
- Pas de refactoring opportuniste dans le même commit
- Si le fix modifie l'API publique, mettre à jour `contracts/*.tsv`
- Si le fix modifie le comportement d'un gate, mettre à jour le test d'architecture correspondant

### Étape 5 — Vérification
- Exécuter les tests du module affecté : `./mvnw test -pl <module>`
- Exécuter les tests d'architecture : `./mvnw test -pl morpheus-architecture-tests`
- Vérifier les dépendances : `./mvnw dependency:analyze -pl <module>`
- Confirmer que le test de reproduction écrit à l'étape 2 passe maintenant

## Rapport final
```
Bug: <description>
Module: morpheus-<x>
Fichier: src/main/java/.../Foo.java:42
Cause: <une phrase sur le pourquoi>
Fix: <une phrase sur le quoi>
Tests: ✅ <N> tests passants, 0 régression
Gates: ✅ M28 intact
```
