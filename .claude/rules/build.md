# Règles — Build & Dépendances

## Versions pinnées — assertées textuellement dans `pom.xml`

`D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` exige la présence littérale de :

```xml
<jackson.version>3.2.2</jackson.version>
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

## `dependencyManagement` — le mécanisme concret

Le POM racine importe des BOM (`junit-bom`, `mcp-bom`, `reactor-bom`, `jackson-bom`) et
gère `slf4j-api` en version fixe :

```xml
<!-- pom.xml racine -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit</groupId><artifactId>junit-bom</artifactId>
            <version>${junit.version}</version><type>pom</type><scope>import</scope>
        </dependency>
        <!-- ... mcp-bom, reactor-bom, jackson-bom, slf4j-api ... -->
    </dependencies>
</dependencyManagement>
```

Un module référence ensuite la dépendance **sans version** :

```xml
<!-- morpheus-application/pom.xml -->
<dependency>
    <groupId>io.github.fturleque</groupId>
    <artifactId>morpheus-domain</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <scope>test</scope>
</dependency>
```

`morpheus-domain` porte `${project.version}` parce que c'est un module interne du même
reactor, pas une exception au principe « pas de version en dur » ; `junit-jupiter-api`
n'a pas de `<version>` du tout, elle vient du `junit-bom` importé plus haut.

Versions actuelles pilotées par propriété dans le POM racine (à revérifier avant de citer,
cf. `rules/meta.md`) : `junit.version=6.1.3`, `archunit.version=1.5.0`,
`sqlite-jdbc.version=3.53.2.0`, `mcp-sdk.version=2.0.1`, `reactor-bom.version=2024.0.0`,
`slf4j.version=2.0.18`, `jackson.version=3.2.2`, `jacoco.version=0.8.15`.
Un commentaire explique un couplage de versions non trivial quand il existe
(ex. `reactor-bom` doit rester sur la même ligne que `mcp-sdk` 2.0.1 — voir le commentaire
juste au-dessus de cette dépendance dans le POM).

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
