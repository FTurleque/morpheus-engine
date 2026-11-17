# Proposition : ne compter que les sources de production comme appelants d'une méthode publique (AUD-QUA-28)

## Why

`UnreferencedPublicMethodArchitectureTest` accepte comme référence le nom d'une méthode écrit dans un test : une méthode
publique que seuls des tests appellent passe la porte. La règle du dépôt dit pourtant d'une fabrique de record qu'« une
fabrique qu'aucune classe de production n'appelle n'existe pas : un test passe par le constructeur canonique »
(`.claude/rules/code-style.md`), et la spec `record-construction` le pose en scénario (« No entry exists for tests
alone ») sans que rien ne le tienne : `RecordSingleConstructorArchitectureTest` ne juge que les constructeurs.

Conséquence mesurée : un contrat M24 prouve un budget que le chemin livré n'emploie pas.
`QueryExportBudgetPolicy.requireBytes` n'est appelé que par `QueryExportContractTest` (lignes 90 et 94), alors que
l'export réel est borné par `Utf8BoundedTextBuilder` dans `QueryReportFormatter` (lignes 41 et 136) : le contrat resterait
vert si le chemin livré perdait sa borne.

Preuve : constat `AUD-QUA-28` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd` (21 méthodes publiques et 8 fabriques de records). Revérifié le 17/11/2026 sur la tête de la branche par
une réplique de la porte réduite aux sources principales : 32 méthodes relevées, dont les 8 fabriques de l'audit, et 6
dans des types que `give-every-public-type-of-the-product-a-production-caller` (AUD-QUA-27) traite (design.md, Contexte).

## What Changes

- La porte des méthodes publiques ne compte plus que les sources principales comme appelants ; une méthode qu'un test
  seul appelle échoue, sauf entrée de sa liste d'exceptions avec sa raison.
- Les 8 fabriques de records sans appelant de production sont retirées ; leurs tests passent par le constructeur
  canonique.
- Les autres méthodes relevées sont retirées, câblées, ou listées avec leur raison (une porte d'architecture qui lit la
  table des routes, par exemple), selon le tableau de design.md D2.
- `QueryExportContractTest` prouve la borne d'export sur `QueryReportFormatter`, et `requireBytes` disparaît avec
  `QueryExportBudgetPolicy` s'il ne lui reste aucun appelant.

## Capabilities

### New Capabilities

### Modified Capabilities
- `dead-code-refusal` : l'exigence « A public method of a product module SHALL be referenced or excused by name » exige
  un appelant de production ; un appel de test ne suffit plus.

## Périmètre et exclusions

**Inclus :** `UnreferencedPublicMethodArchitectureTest`, les méthodes relevées et leurs tests, `QueryExportContractTest`.

**Exclus :** les types publics sans appelant (AUD-QUA-27, change `give-every-public-type-of-the-product-a-production-caller`,
qui le précède : ses suppressions retirent d'abord les méthodes des types morts, pour ne pas resserrer la porte deux
fois) ; le SDK publié et l'outillage de vérification, hors porte comme aujourd'hui.

## Acteurs et surfaces concernés

L'auteur d'une méthode publique de production et le relecteur ; la CI, qui porte le verdict. Aucune surface CLI, MCP ou
HTTP ne change : `MorpheusQueryApiService.executeProject` et les deux méthodes de `MorpheusMcpServer` relevées ne sont
pas servies.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La porte compte un appel de test comme une référence | existant vérifié | `UnreferencedPublicMethodArchitectureTest.javaSources` lit tout `src/`, tests compris |
| `requireBytes` n'a pour appelant que `QueryExportContractTest` | existant vérifié | `git grep -nw requireBytes`, 17/11/2026 |
| Huit fabriques de records sans appelant de production | existant vérifié | réplique de la porte, design.md, Contexte |
| La spec `record-construction` exige déjà l'absence d'une fabrique pour les seuls tests | existant vérifié | `openspec/specs/record-construction/spec.md`, scénario « No entry exists for tests alone » |
| Le sort de chaque méthode | proposition à valider | design.md D2 |

## Impact

- `morpheus-architecture-tests` : `UnreferencedPublicMethodArchitectureTest` (fichier de gouvernance, justification de
  PR), `QueryExportContractTest`.
- `morpheus-domain`, `morpheus-application`, `morpheus-api`, `morpheus-mcp`, `morpheus-mcp-transport` : méthodes
  retirées et tests réécrits.
- Aucun ADR : règle de dépôt réversible (`.claude/rules/adr.md`).
- Documentation : `docs/developer/specifications/dead-code-refusal.md`, `docs/developer/specifications/record-construction.md`.
- Dépendance : `give-every-public-type-of-the-product-a-production-caller` (AUD-QUA-27) s'applique avant.

## Questions ouvertes

Aucune.
