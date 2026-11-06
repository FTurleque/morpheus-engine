# Proposition : tenir le fichier de suppressions Dependency-Check par un contrat

## Why

`config/dependency-check-suppressions.xml` désactive des détections du gate CVE, et aucune assertion ne lit son contenu.
Le constat `AUD-TST-21` (Faible) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur
`30520bd3`, le classe « aggravé » : pendant la vague de correctifs, une règle d'une forme inédite (`<filePath
regex="true">` + `<vulnerabilityName>`, `config/dependency-check-suppressions.xml:21-36`) et un second `base="true"`
(`:21`) sont entrés sans qu'aucun gate ne rougisse. Les lecteurs du fichier vérifient que son chemin est cité, pas ce
qu'il contient : `pom.xml:296` et `:339` (`<suppressionFile>`), `scripts/validate-d2.sh:89`, `scripts/validate-d2.ps1:113`.
La règle de dépôt (« Justifier toute suppression CVE … avec un commentaire », `.claude/rules/build.md:24`) n'est tenue
que par la revue. À l'inverse, le fichier d'indices voisin `config/dependency-check-hints.xml`, né de la même vague, est
parsé et épinglé par `DependencyCheckWorkflowContractTest#theSqliteEngineEmbeddedBySqliteJdbcIsIdentifiedWhateverTheHistoryOfTheDatabase`.
L'état actuel est sain ; ce qu'il faut empêcher, c'est la règle suivante, élargie sans que personne ne le voie.

## What Changes

- Gate de build ajouté (suite d'architecture, paquet `d2`) : chaque règle du fichier de suppressions prend une des deux
  formes admises, et toute autre forme fait échouer le build en nommant la règle.
- Une règle d'association (CPE) vise exactement un module MORPHEUS du réacteur, à la version du réacteur, et retire un
  seul CPE ; une règle de vulnérabilité nommée vise un seul identifiant CVE sur les seules bibliothèques natives
  extraites du pilote `sqlite-jdbc`.
- Chaque règle porte une note non vide ; la note d'une règle `base="true"` dit pourquoi elle est exemptée de
  `failBuildOnUnusedSuppressionRule`.
- La liste des règles `base="true"` est une table de faits exacte, tenue dans le test.
- Aucune surface publique, aucun format persisté, aucune ligne de `contracts/public-surfaces.tsv` ne change ; le
  contenu actuel du fichier satisfait déjà le contrat.

## Capabilities

### New Capabilities
- `dependency-check-suppression-contract` : forme, ancrage et justification de chaque règle du fichier de suppressions
  du gate CVE, et liste exacte des règles exemptées du contrôle des règles inutilisées.

### Modified Capabilities

Aucune. L'exigence `vulnerability-feed-freshness` › « A suppression on the embedded engine SHALL name one vulnerability
and the extracted libraries only » reste telle quelle ; ce changement lui donne sa preuve (voir `design.md`, D5).

## Périmètre et exclusions

**Inclus :** le contenu de `config/dependency-check-suppressions.xml` et sa concordance avec le POM racine (modules,
version du réacteur, `<sqlite-jdbc.version>`).

**Exclus :**
- le seuil CVSS, le câblage du fichier dans les profils `d2-security` et `d2-security-tests` (déjà tenus par
  `D2RepositoryHardeningArchitectureTest` et `scripts/validate-d2.*`) ;
- le fichier d'indices, déjà tenu par `DependencyCheckWorkflowContractTest` ;
- une date d'expiration (`until`) des règles : question ouverte, voir plus bas.

## Acteurs et surfaces concernés

Mainteneur qui ajoute ou modifie une suppression ; relecteur de PR ; CI (`ci.yml`, suite d'architecture). Aucun client
de MORPHEUS n'est concerné.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucune assertion ne lit le fichier de suppressions | existant vérifié | aucune occurrence de `dependency-check-suppressions` dans `morpheus-architecture-tests/src/test/java` à `30520bd3` |
| Le fichier porte trois règles de deux formes, dont deux `base="true"` | existant vérifié | `config/dependency-check-suppressions.xml:3`, `:21`, `:37` ; formes `:18-19`, `:34-35`, `:44-45` |
| Les lecteurs ne vérifient que la citation du chemin | existant vérifié | `pom.xml:296`, `:339` ; `scripts/validate-d2.sh:88-89` ; `scripts/validate-d2.ps1:112-113` |
| Le fichier d'indices voisin est parsé et épinglé | existant vérifié | `DependencyCheckWorkflowContractTest.java:726-790` |
| Deux formes seulement sont admises | décision | `design.md`, D1 |
| La liste des règles `base="true"` est une table de faits exacte | décision | `design.md`, D4 |
| Le contrat est une assertion XML, pas une règle ArchUnit | décision | fichier non Java (`.claude/rules/architecture.md`, « Règle ArchUnit ou assertion textuelle ») |

## Impact

- Tests : une classe ajoutée dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/`, marquée
  `@CoherenceCheck` (elle ne lit que des fichiers de configuration) ; `architectureTestsMinimum` ne baisse pas.
- Documentation : page durable nouvelle `docs/developer/specifications/dependency-check-suppression-contract.md`, son
  index ; preuve de R5 dans `docs/developer/specifications/vulnerability-feed-freshness.md` ; `.claude/rules/build.md`
  (la justification devient exécutable).
- ADR : non concerné (contrat de fichier, sans décision structurelle).

## Questions ouvertes

- Faut-il exiger un attribut `until` (date d'expiration) sur une règle de vulnérabilité nommée, que sa note dit
  provisoire (« Remove it when the NVD record or a later driver clears it ») ? À trancher par le mainteneur ; le contrat
  admet l'attribut sans l'exiger.
