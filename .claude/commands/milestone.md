# Commande /milestone

Assistant pour travailler sur un milestone spécifique de Morpheus Engine.

## Usage
- `/milestone` → statut du milestone courant (M28)
- `/milestone m27` → analyse le milestone M27
- `/milestone next` → guide pour préparer le prochain milestone

## Ce que tu dois faire

### Identifier le milestone depuis $ARGUMENTS
- "m28", "28", "M28" → M28 (courant)
- "next" → identifier le prochain en lisant les ADRs et roadmap

### Pour un milestone existant (ex: M28)

1. **Lire le contexte du milestone** :
   - Tests ArchUnit : `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/`
   - Script de validation : `scripts/validate-m28*.ps1`
   - Workflow CI : `.github/workflows/` (chercher "m28")
   - Documentation : `docs/` (chercher les fichiers mentionnant M28)

2. **Exécuter le gate** :
   ```bash
   ./mvnw test -pl morpheus-architecture-tests -Dtest=*M28* 2>&1
   ```

3. **Synthétiser ce que valide ce milestone** :
   - Quelles règles architecturales sont enforced ?
   - Quelles nouvelles fonctionnalités sont testées ?
   - Quels modules sont couverts ?

4. **Rapport de statut** :
   ```
   Milestone M28
   ├── Gate: ✅ PASS (N tests)
   ├── Modules couverts: morpheus-api, morpheus-mcp, ...
   ├── Règles: [liste des règles ArchUnit enforced]
   └── Documentation: docs/validation/m28/ ✅/❌
   ```

### Pour `/milestone next`

1. Lire `docs/roadmap/` pour identifier les fonctionnalités prévues
2. Lire les ADRs récents dans `docs/adr/`
3. Lire les tests M28 pour comprendre ce qui est déjà en place
4. Proposer :
   - Les nouvelles règles ArchUnit à ajouter
   - Les modules à créer ou modifier
   - Le squelette de test `m29/` à créer
   - Les contrats publics à mettre à jour

### Pour `/milestone m<N>` (milestone historique)

Exécuter le gate et expliquer la progression architecturale entre M(N-1) et M(N).
