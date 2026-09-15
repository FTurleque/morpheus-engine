# Commande /milestone

Assistant pour travailler sur un milestone spécifique de Morpheus Engine.

## Usage
- `/milestone` → statut du dernier milestone livré
- `/milestone m27` → analyse le milestone M27
- `/milestone next` → guide pour préparer le prochain milestone

## Ce que tu dois faire

### Identifier le milestone depuis $ARGUMENTS
- "m27", "27", "M27" → M27
- sans argument → le dernier milestone livré, lu dans `.claude/CLAUDE.md` (section Milestones)
- "next" → identifier le prochain en lisant les ADRs et la roadmap

Jamais deviner le milestone courant ou le numéro du prochain — le lire dans `.claude/CLAUDE.md` ou dans
`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`.

### Pour un milestone existant (M<N>)

1. **Lire le contexte du milestone** :
   - Tests ArchUnit : `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m<N>/`
   - Script de validation : `scripts/validate-m<N>.ps1` / `scripts/validate-m<N>.sh`
   - Workflow CI : `.github/workflows/` (chercher "m<N>")
   - Documentation : `docs/` (chercher les fichiers mentionnant M<N>)

2. **Exécuter le gate** :
   ```bash
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M<N>* 2>&1
   ```

3. **Synthétiser ce que valide ce milestone** :
   - Quelles règles architecturales sont enforced ?
   - Quelles nouvelles fonctionnalités sont testées ?
   - Quels modules sont couverts ?

4. **Rapport de statut** :
   ```
   Milestone M<N>
   ├── Gate: ✅/❌ (<N constaté> tests)
   ├── Modules couverts: <liste constatée>
   ├── Règles: <liste des règles ArchUnit enforced>
   └── Documentation: docs/validation/VALIDATION_M<N>.md ✅/❌
   ```

### Pour `/milestone next`

1. Lire `docs/roadmap/` pour identifier les fonctionnalités prévues
2. Lire les ADRs récents dans `docs/adr/` (compter avec `glob`, ne pas citer un total)
3. Lire les tests du dernier milestone livré pour comprendre ce qui est déjà en place
4. Proposer :
   - Les nouvelles règles ArchUnit à ajouter
   - Les modules à créer ou modifier
   - Le squelette de test `m<N+1>/` à créer
   - Les contrats publics à mettre à jour

### Pour `/milestone m<N>` (milestone historique)

Exécuter le gate et expliquer la progression architecturale entre M(N-1) et M(N).
