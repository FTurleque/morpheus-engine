# Règles — Build & Dépendances

## Versions pinnées — assertées textuellement dans `pom.xml`

`D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` exige la présence littérale de :

```xml
<jackson.version>3.1.5</jackson.version>
<sqlite-jdbc.version>3.53.2.0</sqlite-jdbc.version>
<dependency-check.maven.plugin.version>12.2.2</dependency-check.maven.plugin.version>
<failOnWarning>true</failOnWarning>
<id>d2-security</id>
<failBuildOnCVSS>7.0</failBuildOnCVSS>
```

Bumper une de ces versions **casse le gate** tant que le test n'est pas mis à jour en connaissance de cause.

## TOUJOURS

- Utiliser `./mvnw` (Maven Wrapper 3.9.16) — jamais `mvn` nu
- Déclarer toute dépendance dans le `<dependencyManagement>` du POM racine, puis la référencer **sans version** dans le module
- Vérifier l'hygiène après ajout : `./mvnw dependency:analyze` — `<failOnWarning>true</failOnWarning>` est actif, **0 warning** ou le build casse
- Construire `morpheus-provider-reference` avant les tests d'architecture (M22 lit son JAR depuis `target/`)
- Justifier toute suppression CVE dans `config/dependency-check-suppressions.xml` avec un commentaire

## JAMAIS

- Jamais de version de dépendance en dur dans un POM de module
- Jamais de dépendance `unused declared` ni `used undeclared` — le CI échoue
- Jamais ajouter `morpheus-provider-sdk` ou `morpheus-provider-reference` aux POMs de `domain` / `application`
- Jamais embarquer `morpheus-provider-reference` dans `morpheus-cli/pom.xml`
- Jamais introduire une CVE ≥ 7.0 sans suppression approuvée
- Jamais Java < 21 (baseline ADR-0016) ni Maven < 3.9.16 (Enforcer actif)

## Stack imposée

| Domaine | Choix | ADR |
|---|---|---|
| HTTP | `jdk.httpserver` (JDK built-in) | 0065 |
| MCP | `io.modelcontextprotocol.sdk` + `mcp-json-jackson3` | 0062 |
| Persistance | SQLite (`sqlite-jdbc`) | 0018 |
| JSON | Jackson 3 (`tools.jackson`) | — |
| Distribution | `jpackage` natif + Inno Setup, **pas Docker** | 0027, 0061 |
| Build | Maven multi-module | 0017 |

## Commandes

```bash
./mvnw clean verify                      # build complet + coverage + gates
./mvnw dependency:analyze                # hygiène (0 warning exigé)
./mvnw verify -P d2-security             # + scan OWASP CVE
./mvnw test -pl morpheus-architecture-tests
```

## Workflows CI

- `ci.yml` — `mvn clean verify` sur `ubuntu-latest` **et** `windows-latest`
- `security.yml` — OWASP hebdomadaire (lundi 04:17), branches `[main, develop]`, `timeout-minutes: 90`
- `dependabot.yml` — écosystèmes `maven` + `github-actions`, `target-branch: develop`

Voir [security.md](security.md) pour les règles de pinning SHA des actions.
