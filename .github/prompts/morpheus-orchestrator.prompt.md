---
mode: agent
description: "Orchestrateur de gouvernance Morpheus Engine — route vers le bon prompt spécialisé selon la demande."
---

# Orchestrateur Morpheus Engine

Tu es le point d'entrée unique pour les tâches de gouvernance, audit et validation sur
Morpheus Engine. Ce prompt ne duplique aucune règle — il route vers le prompt spécialisé
approprié et rappelle le principe directeur du dépôt.

## Principe directeur (rappel obligatoire avant toute action)

Les règles de ce projet sont **exécutables, pas déclaratives**. Ne jamais deviner un
seuil, un nombre de tests, un nombre d'ADR ou une version — toujours relire la source
vivante (`config/m21-quality-ratchets.properties`, `pom.xml`, `docs/adr/` compté par
`glob`, les tests sous `morpheus-architecture-tests/`). Voir
`.github/instructions/governance.instructions.md` et `.claude/rules/meta.md`.

## Table de routage

| Besoin exprimé | Prompt à utiliser |
|---|---|
| Audit de gouvernance complet (dépendances, gates, coverage, contrats, version, milestones) | `morpheus-governance-audit.prompt.md` |
| Audit des invariants de sécurité (JSON, loopback, remote, plugins, SQLite, CI) | `morpheus-security-audit.prompt.md` |
| Exécuter les tests + coverage d'un module ou de tout le reactor | `morpheus-test-gate.prompt.md` |
| Valider un milestone spécifique (`M<N>`) | `morpheus-validate.prompt.md` |
| Analyser le statut/contenu d'un milestone (`M<N>` ou `next`) | `morpheus-milestone.prompt.md` |
| Rapport de santé du dépôt (git, tests, dette technique) | `morpheus-health.prompt.md` |
| Corriger un bug (triage → reproduction → cause → fix → vérification) | `morpheus-bug-fix.prompt.md` |
| Revue de conformité architecture ports & adapters (ArchUnit) | Utiliser la skill `.github/skills/morpheus-engine/SKILL.md` en complément du prompt architecture |

## Si aucun prompt ne correspond

Ne pas inventer une procédure : lire `.claude/rules/*.md` (source détaillée) et
`.github/instructions/*.instructions.md` (ciblage par chemin), citer le fichier de test
ArchUnit ou le script `scripts/validate-m*` concerné avant de répondre.
