---
mode: agent
description: "Analyse le statut ou le contenu d'un milestone Morpheus Engine (M<N> ou next)."
---

# Milestone Morpheus Engine

Miroir du prompt Claude `.claude/commands/milestone.md`. Préciser "m<N>" pour un milestone
existant, ou "next" pour préparer le suivant.

## Pour un milestone existant (ex. M<N>)

1. Lire le contexte : tests ArchUnit
   (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/`), script
   de validation (`scripts/validate-m<N>*`), workflows CI mentionnant ce milestone,
   documentation associée dans `docs/`.
2. Exécuter le gate :
   ```bash
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>* 2>&1
   ```
3. Synthétiser : quelles règles architecturales sont enforced, quelles fonctionnalités
   sont testées, quels modules sont couverts.
4. Rapport :
   ```
   Milestone M<N>
   ├── Gate: ✅/❌ (<N constaté> tests)
   ├── Modules couverts: <liste constatée>
   ├── Règles: <liste des règles ArchUnit enforced>
   └── Documentation: docs/validation/m<N>/ ✅/❌
   ```

## Pour "next"

1. Lire `docs/roadmap/` pour identifier les fonctionnalités prévues
2. Lire les ADRs récents dans `docs/adr/` (compter avec `glob`, ne pas citer un total)
3. Lire les tests du milestone courant pour comprendre ce qui est déjà en place
4. Proposer : nouvelles règles ArchUnit, modules à créer/modifier, squelette de test du
   prochain milestone, contrats publics à mettre à jour

Jamais deviner le milestone courant ou le numéro du prochain — le lire dans
`.claude/CLAUDE.md` ou dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`.

