# Commande /validate

Exécute le script de validation du milestone spécifié.

## Usage
- `/validate` → valide le milestone courant (M28)
- `/validate m26` → valide le milestone M26
- `/validate all` → valide tous les milestones disponibles

## Ce que tu dois faire

1. **Identifier le milestone** depuis $ARGUMENTS (ex: "m28", "28", "M28" → tous valident M28). Par défaut : M28.

2. **Trouver le script de validation** dans `scripts/` :
   ```
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*
   ```
   Et si le script PS1 existe : `scripts/validate-m<N>*.ps1`

3. **Exécuter la validation** :
   - Sur Windows : `./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*`
   - Si un script PS1 spécifique existe pour ce milestone, l'exécuter aussi

4. **Analyser les résultats** :
   - Lister les tests passants et échouants
   - Pour chaque échec, afficher le message d'erreur ArchUnit complet
   - Identifier la règle architecturale violée et le fichier concerné

5. **Pour `/validate all`** : itérer sur M19 à M28, rapporter le statut de chacun en tableau.

## Rapport

```
Milestone M28 — Validation
├── ArchUnit gate:     ✅ PASS (42 tests)
├── API contracts:     ✅ PASS
└── Dependency hygiene: ✅ PASS (0 warnings)
```

Si des violations sont trouvées, fournir le chemin exact vers le test ArchUnit correspondant dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/`.
