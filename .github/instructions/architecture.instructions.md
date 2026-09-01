---
applyTo: "morpheus-domain/**,morpheus-application/**,morpheus-api/**,morpheus-cli/**,morpheus-mcp/**,morpheus-mcp-transport/**,morpheus-integration-*/**,morpheus-provider-*/**,morpheus-store-*/**,morpheus-architecture-tests/**"
---

# Architecture — ports & adapters

Source de vérité exécutable : `morpheus-architecture-tests/.../LayerDependencyTest.java` +
les `*ArchitectureTest` par milestone. Détail complet et exemples réels :
`.claude/rules/architecture.md` (source partagée avec Claude Code — la lire avant toute
décision structurelle, ne pas se contenter de ce résumé).

## Le modèle

`morpheus-application` **définit les ports** (interfaces) et ne dépend d'**aucun**
adaptateur. Les adaptateurs (`provider-*`, `store-*`, `api`, `mcp`, `integration-*`)
implémentent ces ports et dépendent vers l'intérieur (domain ← application ← adapters).
Les adaptateurs sont **frères** : ils ne s'appellent jamais entre eux. Seul `morpheus-cli`
fait le câblage explicite (composition root) — jamais de framework DI, jamais de
classpath scanning.

## Interdits enforced par ArchUnit (ne jamais deviner, lire le test)

- `com.morpheus.domain..` et `com.morpheus.application..` : aucune dépendance vers
  `provider..`, `store..`, `cli..`, `mcp..`, `api..`, `integration..`, `com.minos..`,
  `com.nexus..`, `com.jarvis..`
- `com.morpheus.api..` : interdits vers `cli..`, `mcp..`, `integration..`, `com.minos..`,
  `com.nexus..`, `com.jarvis..`
- `integration.minos` / `integration.nexus` : interdits vers `cli..`, `mcp..`, `api..`,
  `store..`, et l'implémentation `com.minos..`/`com.nexus..` elle-même — communication
  **MCP STDIO uniquement**
- `com.morpheus..` (tout) ne dépend jamais de `com.jarvis..`
- Sous-plateformes application (`query.{dsl,saved,export}`, `policy..`, `reasoning..`) ont
  des interdits supplémentaires listés dans `.claude/rules/architecture.md`
- `morpheus-domain/pom.xml` et `morpheus-application/pom.xml` : ni `morpheus-provider-sdk`
  ni `morpheus-provider-reference`. `morpheus-cli/pom.xml` : jamais
  `morpheus-provider-reference`

## Avant toute nouvelle dépendance inter-module

1. Lire `LayerDependencyTest.java` et le `*ArchitectureTest` du milestone concerné
2. Vérifier que le port existe déjà dans `application` avant d'en créer un nouveau
3. Si une surface publique change, mettre à jour `contracts/public-surfaces.tsv` **et**
   `docs/openapi/morpheus-v1-*.yaml` dans le même changement
4. Lancer `./mvnw test -pl morpheus-architecture-tests` avant de proposer le changement comme terminé
