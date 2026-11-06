# Proposition : comparer à sa source chaque document qui affirme un fait courant

## Why

Deux documents vivants affirment au présent un fait que le dépôt a changé, et aucun contrôle ne les confronte à leur
source. Constats de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifiés le 2026-11-06 sur `30520bd3` :

- `AUD-DEP-29` (Moyenne) : `.claude/rules/build.md`, chargé à chaque session d'agent par `.claude/CLAUDE.md`, déclare
  des versions de dépendances sous forme de propriétés du POM et annonce `jacoco.version=0.8.15` alors que `pom.xml`
  porte `<jacoco.version>0.8.16</jacoco.version>` ; le bump `6f19741a` n'a touché que `pom.xml`. La page est hors de
  `RepositoryDocumentationCoherenceTest.CURRENT_STACK_SURFACES`, et ce test ne compare de toute façon que trois
  dépendances (sqlite-jdbc, Dependency-Check, Jackson). `.claude/rules/testing.md` et `.claude/rules/governance.md`
  recopient les valeurs de `config/m21-quality-ratchets.properties` ; aucun test ne les lit.
- `AUD-QUA-21` (Faible) : le changement vivant `resolve-both-mcp-peers-through-one-settings-resolver` affirme dans son
  `design.md` (ligne 13) que `morpheus-mcp-transport` livre le paquet `com.morpheus.integration.mcp` (ancien nom),
  supprimé par le renommage en `com.morpheus.transport.mcp` (commit `681794cb`). Aucun test ne lit `openspec/changes/`.

`.claude/rules/meta.md` pose déjà la règle (« tout chiffre présent dans `.claude/rules/*.md` est une copie ») ; ce
changement la rend exécutable pour les faits qui ont une source lisible par un test.

## What Changes

- Gate de build : toute version qu'une page de gouvernance IA écrit sous la forme d'une propriété du POM racine
  (`<x.version>v</x.version>` ou `x.version=v`) est comparée à la valeur de cette propriété dans `pom.xml`.
- Gate de build : toute valeur de `config/m21-quality-ratchets.properties` qu'une page de `.claude/rules/` recopie sous
  le nom de sa clé est comparée à la clé ; `.claude/rules/governance.md` cesse de porter une seconde copie et renvoie à
  la source.
- Gate de build : tout nom de paquet `com.morpheus…` cité dans un changement OpenSpec actif (hors `archive/`) existe
  dans les sources du réacteur, sauf mention marquée « (ancien nom) » ou « (à créer) ».
- Corrections faites **après** que chaque contrôle a échoué une fois dessus : `jacoco.version` dans
  `.claude/rules/build.md`, la ligne 13 du `design.md` de `resolve-both-mcp-peers-through-one-settings-resolver`.

## Capabilities

### New Capabilities
- `stated-fact-currency` : un document vivant qui recopie un fait dont la source est lisible par un test — une valeur
  de cliquet, un nom de paquet — est comparé à cette source par le build.

### Modified Capabilities
- `dependency-version-management` : exigence ajoutée — une version déclarée par une page de gouvernance IA sous la
  forme d'une propriété du POM égale la valeur du POM racine.

## Périmètre et exclusions

**Inclus :** les pages de gouvernance IA que `GovernanceSurfaceCoherenceTest` parcourt déjà (`.claude/CLAUDE.md`,
`.github/AI_GOVERNANCE.md`, `.claude/rules/`, `.claude/commands/`, `.claude/agents/`, les `SKILL.md` de
`.claude/skills/`) pour les versions ; `.claude/rules/` pour les cliquets ; les fichiers Markdown de
`openspec/changes/` hors `archive/` pour les paquets.

**Exclus :**
- une version écrite en prose libre (« `mcp-sdk` 2.1.0 », « Maven Wrapper 3.10.0 ») : seule la forme propriété est
  comparée ; la prose de `build.md` qui recopie une version est réécrite pour citer la propriété (voir `design.md`, D2) ;
- le schéma SQLite, déjà refusé en littéral sur ces pages
  (`GovernanceSurfaceCoherenceTest#activeGovernanceDocumentationNeverPinsTheSqliteSchemaVersion`) ;
- la version produit écrite en prose sur `.claude/CLAUDE.md` et `.claude/rules/governance.md` : aucun test ne la
  compare à `ProductMetadata` (constat de ce changement, voir `design.md`, Écarts) ; la traiter demande de décider si
  ces pages doivent la porter, ce qui sort du périmètre des deux constats ;
- les mesures datées de `.claude/rules/testing.md` (historique des requalifications) : ce sont des relevés, pas des
  valeurs courantes ;
- les archives OpenSpec, relevés datés qui citent légitimement les anciens noms ;
- toute autre modification du changement `resolve-both-mcp-peers-through-one-settings-resolver` que la ligne 13.

## Acteurs et surfaces concernés

Agent qui lit `.claude/rules/` à chaque session ; contributeur qui reprend un changement OpenSpec actif ; Dependabot,
dont les bumps ne touchent que `pom.xml`. Aucune surface CLI, MCP ni HTTP ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `build.md` annonce `jacoco.version=0.8.15`, le POM `0.8.16` | existant vérifié | `.claude/rules/build.md:77`, `pom.xml:63` |
| `CURRENT_STACK_SURFACES` n'inclut pas `build.md` et ne compare que trois dépendances | existant vérifié | `RepositoryDocumentationCoherenceTest`, constante et `activeStackDocumentationAndD2ValidatorsFollowTheRootPomDependencyVersions` |
| Aucun test ne lit les valeurs de cliquet de `testing.md` ni de `governance.md` | existant vérifié | recherche des chemins `.claude/rules/*.md` dans `morpheus-architecture-tests/src/test/java` : seuls `build.md` (commande de scan) et `governance.md` (sentinelles) sont lus |
| Les valeurs de cliquet recopiées concordent aujourd'hui | existant vérifié | `.claude/rules/testing.md` (table), `.claude/rules/governance.md` (« Ratchets de présence qualifiée ») contre `config/m21-quality-ratchets.properties` |
| `design.md:13` du changement vivant nomme l'ancien paquet | existant vérifié | `openspec/changes/resolve-both-mcp-peers-through-one-settings-resolver/design.md:13` ; aucun répertoire `com/morpheus/integration/mcp` sous `src/main/java` |
| Comparer la forme propriété plutôt qu'ajouter `build.md` à `CURRENT_STACK_SURFACES` | décision | `design.md`, D1 |
| Marquer l'ancien nom et le paquet à créer dans le texte | décision | `design.md`, D4 |

## Impact

- Tests : `GovernanceSurfaceCoherenceTest` (versions et cliquets) ; un test ajouté dans `morpheus-architecture-tests`
  pour les paquets cités par `openspec/changes/`.
- Gouvernance : `.claude/rules/build.md`, `.claude/rules/governance.md`, `.claude/rules/meta.md` (liste des
  destinations d'une hausse de cliquet).
- OpenSpec : une ligne du `design.md` de `resolve-both-mcp-peers-through-one-settings-resolver`.
- Documentation : `docs/developer/specifications/dependency-version-management.md`, page nouvelle
  `docs/developer/specifications/stated-fact-currency.md` et l'index `docs/developer/specifications/README.md`.
- ADR : aucun (contrôles de cohérence documentaire, réversibles sans coût).

## Questions ouvertes

- La version produit recopiée sur `.claude/CLAUDE.md` et `.claude/rules/governance.md` doit-elle être comparée à
  `ProductMetadata` ou retirée de ces pages ? À trancher par le mainteneur ; hors de ce changement.
