# Proposition : gérer dans le POM racine tout plugin Maven qu'un workflow appelle (AUD-DEP-36)

## Why

Le job `sonar` de `.github/workflows/ci.yml` appelle `org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar`
avec sa version en ligne de commande ; le plugin est absent du POM racine, et `AuditHardeningWorkflowContractTest`
recopie la même chaîne. Ni l'écosystème `maven` de `.github/dependabot.yml` (il lit les POM) ni `github-actions` (il
lit les `uses:`) ne voient cette version : le scanner, qui tourne avec `SONAR_TOKEN`, n'est jamais proposé à la mise à
jour. La 5.5.0.6356 date du 05/12/2025 ; quatre versions l'ont suivie, la dernière (5.9.0.7382) le 16/11/2026.
`.claude/rules/build.md` exige de déclarer tout plugin dans le `<pluginManagement>` racine, et la capacité
`dependency-version-management` ne le tient que pour les POM de module.

Preuve : constat `AUD-DEP-36` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, revérifié le 17/11/2026 sur `1a539c22` (`ci.yml`, test de contrat, `pom.xml`, et métadonnées
`sonar-maven-plugin` relues sur Maven Central).

## What Changes

- `sonar-maven-plugin` est déclaré dans le `<pluginManagement>` du POM racine, sa version portée par une propriété ;
  `ci.yml` l'appelle sans version ; le test de contrat lit la propriété au lieu d'un littéral.
- Le build refuse un appel de plugin Maven par coordonnées (`groupe:artefact[:version]:but`) dans un workflow ou un
  validateur de `scripts/` quand le POM racine ne déclare pas ce plugin avec sa version (`<pluginManagement>`, ou
  entrée `<plugins>` du build ou d'un profil), ou quand la version écrite dans l'appel diffère de celle que le POM
  déclare.
- L'appel de `dependency-check-maven`, déclaré dans les profils `d2-security` et `d2-security-tests` du POM racine et
  écrit avec la version qu'ils déclarent dans `security.yml` et `scripts/validate-d2.*`, satisfait la règle tel quel.

## Capabilities

### New Capabilities

### Modified Capabilities
- `dependency-version-management` : exigence ajoutée — tout plugin qu'un workflow ou un validateur appelle par
  coordonnées a sa version décidée par le POM racine.

## Périmètre et exclusions

**Inclus :** `pom.xml` (propriété et entrée de `<pluginManagement>`), `.github/workflows/ci.yml`,
`AuditHardeningWorkflowContractTest`, un test de `morpheus-architecture-tests` qui parcourt `.github/workflows/*.yml`
et les validateurs `scripts/*.sh`/`*.ps1`, `.claude/rules/build.md` (table des versions), la page durable de la capacité.

**Exclus :** la montée elle-même vers une version plus récente du scanner, laissée à Dependabot une fois le plugin
visible (design.md, D3) ; la destination du jeton Sonar (`AUD-DEP-37`, change
`send-the-sonar-token-only-to-the-configured-host`).

## Acteurs et surfaces concernés

Dependabot ; le mainteneur ; le job `sonar` de la CI, qui porte `SONAR_TOKEN`.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La version du scanner n'est écrite qu'en ligne de commande | existant vérifié | `ci.yml`, job `sonar` ; `pom.xml` sans entrée `sonar` |
| Dependabot ne la voit pas | existant vérifié | `.github/dependabot.yml`, écosystèmes `maven` et `github-actions` |
| La règle de gestion ne couvre que les POM de module | existant vérifié | `openspec/specs/dependency-version-management/spec.md` |
| `dependency-check-maven` est appelé avec la version que déclarent les profils du POM | existant vérifié | `security.yml`, `scripts/validate-d2.*`, profils `d2-security` et `d2-security-tests`, propriété `dependency-check.maven.plugin.version` |
| Appeler le plugin sans version résout celle du `<pluginManagement>` | proposition à valider | comportement de Maven pour un préfixe ou des coordonnées sans version, vérifié par une exécution du job |

## Impact

`pom.xml`, `ci.yml`, `morpheus-architecture-tests` (`AuditHardeningWorkflowContractTest` et un test de cohérence,
fichiers de gouvernance : justification de PR), `.claude/rules/build.md`, page durable `dependency-version-management`.
Aucune surface publique, aucun code de production.

## Questions ouvertes

Aucune.
