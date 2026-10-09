# Commande /validate

Exécute le script de validation du milestone spécifié.

## Usage
- `/validate` → valide le dernier milestone livré
- `/validate m26` → valide le milestone M26
- `/validate all` → valide tous les milestones disponibles

## Ce que tu dois faire

1. **Identifier le milestone** depuis $ARGUMENTS (ex: "m26", "26", "M26" → tous valident M26). Sans
   argument, lire le dernier milestone livré dans `.claude/CLAUDE.md` (section Milestones) — ne jamais
   le supposer.

2. **Trouver le script de validation** dans `scripts/` :
   ```
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*
   ```
   Et si le script existe : `scripts/validate-m<N>.ps1` (Windows) / `scripts/validate-m<N>.sh` (Linux)

3. **Exécuter la validation** :
   - `./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>*`
   - Si un script spécifique existe pour ce milestone, l'exécuter aussi en passant la version courante
     lue dans `ProductMetadata` : les validateurs ont une version par défaut datée de leur milestone
   - Le verdict et les écarts arrivent filtrés par `.rtk/filters.toml` ; le relevé complet reste
     écrit dans `validation-output/m<N>/validation-summary.txt` et ses échecs dans
     `failure-summary.txt`. Lire le relevé plutôt que de rejouer la commande sans filtre

4. **Analyser les résultats** :
   - Lister les tests passants et échouants
   - Pour chaque échec, afficher le message d'erreur ArchUnit complet
   - Identifier la règle architecturale violée et le fichier concerné

5. **Pour `/validate all`** : itérer sur tous les répertoires
   `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m*/` existants (les découvrir par
   `glob`, ne pas supposer une plage figée) et rapporter le statut de chacun en tableau.

## Rapport

```
Milestone M<N> — Validation
├── ArchUnit gate:     ✅/❌ (<N constaté> tests)
├── API contracts:     ✅/❌
└── Dependency hygiene: ✅/❌ (0 warning attendu)
```

Les valeurs de ce gabarit sont des emplacements à remplir en live, jamais des valeurs à recopier
(cf. `.claude/rules/meta.md`).

Si des violations sont trouvées, fournir le chemin exact vers le test ArchUnit correspondant dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/`.
