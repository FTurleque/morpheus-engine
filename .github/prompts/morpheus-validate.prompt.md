---
mode: agent
description: "Exécute le script de validation d'un milestone Morpheus Engine (M<N>) ou de tous les milestones."
---

# Validation de milestone Morpheus Engine

Miroir du prompt Claude `.claude/commands/validate.md`. Préciser le milestone visé
(ex. "m26") ou "all" pour tous les valider.

## Procédure

1. Identifier le milestone demandé. Si aucun n'est précisé, lire `.claude/CLAUDE.md`
   (section Milestones) pour connaître le milestone courant — ne jamais le supposer.
2. Exécuter :
   ```bash
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*
   ```
   Si un script `scripts/validate-m<N>*.ps1`/`.sh` existe, l'exécuter aussi.
3. Analyser les résultats : lister les tests passants/échouants, afficher le message
   d'erreur ArchUnit complet pour chaque échec, identifier la règle et le fichier
   concernés.
4. Pour une validation "all" : itérer sur tous les répertoires
   `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m*/` existants
   (les découvrir par `glob`, ne pas supposer une plage figée) et rapporter en tableau.

## Rapport

```
Milestone M<N> — Validation
├── ArchUnit gate:      ✅/❌ (<N constaté> tests)
├── API contracts:      ✅/❌
└── Dependency hygiene: ✅/❌ (0 warning attendu)
```

Si des violations sont trouvées, fournir le chemin exact vers le test ArchUnit
correspondant.
