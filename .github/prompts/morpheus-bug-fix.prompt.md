---
mode: agent
description: "Workflow structuré de correction de bug Morpheus Engine (triage → reproduction → cause → fix → vérification)."
---

# Bug-fix Morpheus Engine

Miroir du prompt Claude `.claude/commands/bug-fix.md`.

## Étape 1 — Triage
- Identifier le module affecté (domain / application / provider / store / api / mcp / cli)
- Déterminer si le bug touche un gate milestone (lire les milestones actifs dans
  `.claude/CLAUDE.md`, ne pas les supposer)
- Classifier la sévérité : critique (régression gate) / majeure (comportement incorrect)
  / mineure (edge case)

## Étape 2 — Reproduction
- Chercher un test existant : `grep -r "<symptôme>" src/test/`
- Sinon, écrire un test de reproduction minimal **avant** de corriger — il doit échouer de
  manière déterministe

## Étape 3 — Analyse causale
- Tracer le chemin d'exécution : de l'entrée (CLI/API/MCP) jusqu'au domaine
- Identifier le fichier et la ligne précise de la régression
- Vérifier si un ADR pertinent existe dans `docs/adr/`
- Contrôler les dépendances de module : le fix ne doit pas violer les règles ArchUnit
  (voir `.github/instructions/architecture.instructions.md`)

## Étape 4 — Correction minimale
- Le fix le plus petit possible, pas de refactoring opportuniste
- Si l'API publique change, mettre à jour `contracts/public-surfaces.tsv`
- Si un gate milestone est affecté, mettre à jour le test d'architecture correspondant

## Étape 5 — Vérification
```bash
./mvnw test -pl <module-affecté> 2>&1
./mvnw test -pl morpheus-architecture-tests 2>&1
./mvnw dependency:analyze -pl <module-affecté> 2>&1
```
Confirmer que le test de reproduction écrit à l'étape 2 passe désormais.

## Rapport final

```
Bug: <description>
Module: morpheus-<x>
Fichier: src/main/java/.../Foo.java:<ligne>
Cause: <une phrase sur le pourquoi>
Fix: <une phrase sur le quoi>
Tests: ✅ <N> tests passants, 0 régression
Gates: ✅ <milestone concerné lu en live> intact
```

