# Règles — Build & Dépendances

## Versions pinnées — assertées textuellement dans `pom.xml`

`D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` exige la présence littérale de :

```xml
<jackson.version>3.2.4</jackson.version>
<sqlite-jdbc.version>3.54.1.0</sqlite-jdbc.version>
<dependency-check.maven.plugin.version>13.0.0</dependency-check.maven.plugin.version>
<failOnWarning>true</failOnWarning>
<id>d2-security</id>
<failBuildOnCVSS>7.0</failBuildOnCVSS>
```

Bumper une de ces versions **casse le gate** tant que le test n'est pas mis à jour en connaissance de cause.

## TOUJOURS

- Utiliser `./mvnw` (Maven Wrapper 3.10.0) — jamais `mvn` nu
- Déclarer toute dépendance dans le `<dependencyManagement>` du POM racine (ou la laisser à un BOM importé), et tout plugin dans son `<pluginManagement>`, puis les référencer **sans version** dans le module
- Vérifier l'hygiène après ajout : `./mvnw dependency:analyze` — `<failOnWarning>true</failOnWarning>` est actif, **0 warning** ou le build casse
- Construire `morpheus-provider-reference` avant les tests d'architecture (M22 lit son JAR depuis `target/`)
- Justifier toute suppression CVE de `config/dependency-check-suppressions.xml` dans ses `<notes>`, et toute exemption
  `base="true"` en y nommant `failBuildOnUnusedSuppressionRule` : `DependencyCheckSuppressionContractTest` tient la
  forme, la cible bornée, la justification et la liste exacte des exemptions de chaque règle, ainsi que l'étendue du
  fichier — la table exacte des détections éteintes, dont la taille égale la clé de
  `config/dependency-check-suppression-ratchets.properties`, qui ne fait que baisser : une suppression ajoutée relève
  cette clé et se justifie dans la PR

## JAMAIS

- Jamais de `<version>` de dépendance ou de plugin dans un POM de module, littérale **ou** par propriété : les deux seules formes admises sont la version du `<parent>` et `${project.version}` pour un module frère (`D2RepositoryHardeningArchitectureTest#noModulePomStatesAVersionTheRootPomShouldDecide`)
- Jamais de dépendance `unused declared` ni `used undeclared` — le CI échoue
- Jamais ajouter `morpheus-provider-sdk` ou `morpheus-provider-reference` aux POMs de `domain` / `application`
- Jamais embarquer `morpheus-provider-reference` dans `morpheus-cli/pom.xml`
- Jamais introduire une CVE ≥ 7.0 sans suppression approuvée
- Jamais Java < 21 (baseline ADR-0133) ni Maven < 3.9.16 (Enforcer actif)

## `dependencyManagement` — le mécanisme concret

Le POM racine importe des BOM (`junit-bom`, `mcp-bom`, `reactor-bom`, `jackson-bom`) et
gère lui-même, par propriété, les dépendances qu'aucun BOM ne couvre — relire son `<dependencyManagement>` plutôt que
cette phrase pour en avoir la liste :

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

Versions actuelles pilotées par propriété dans le POM racine : `junit.version=6.1.3`, `archunit.version=1.5.1`,
`sqlite-jdbc.version=3.54.1.0`, `mcp-sdk.version=2.1.0`, `reactor-bom.version=2024.0.18`,
`slf4j.version=2.0.20`, `jackson.version=3.2.4`, `jacoco.version=0.8.16`,
`dependency-check.maven.plugin.version=13.0.0`.
Toute version écrite sous la forme d'une propriété du POM est comparée à `pom.xml` : un bump qui ne touche que le POM
fait échouer le build jusqu'à ce que la page suive. Quatre formes sont lues — élément XML, affectation `nom=valeur`,
ligne de tableau dont une cellule est le span de code du nom et une cellule suivante le span de code de la valeur, et
deux spans de code joints par `=`. Sur une page de gouvernance IA
(`GovernanceSurfaceCoherenceTest#everyVersionAGovernancePageWritesAsAPomPropertyEqualsThePom`), une propriété que le
POM ne déclare pas est refusée. Sur une page Markdown suivie par Git sous `docs/`, hors relevés datés (`project/audits/`,
`project/validation/`, `project/roadmap/`, `user/release-notes/`) et hors blocs de code clôturés
(`#everyVersionALivingDocumentationPageWritesAsAPomPropertyEqualsThePom`), seule une propriété que le POM déclare est
comparée. Une version écrite en prose n'est comparée à rien : citer la propriété plutôt que sa valeur.
Un commentaire explique un couplage de versions non trivial quand il existe
(ex. `reactor-bom` doit rester sur la même ligne de versions que celle que fixe `mcp-sdk.version` — voir le
commentaire juste au-dessus de cette dépendance dans le POM).

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
./mvnw -Pd2-security -DautoUpdate=false org.owasp:dependency-check-maven:aggregate             # + scan OWASP CVE
./mvnw test -pl morpheus-architecture-tests
```

## Workflows CI

- `ci.yml` — `mvn clean verify` sur `ubuntu-latest` **et** `windows-latest`
- `security.yml` — OWASP **quotidien** (04:17 UTC), sur `main`, `timeout-minutes: 90`
  — la cadence est journalière parce que le refresh de confiance doit tourner plus souvent que
  `DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS` (72 h) ; un cron hebdomadaire violerait ce budget et est
  explicitement refusé par `D2RepositoryHardeningArchitectureTest` et `AuditHardeningWorkflowContractTest`
- `dependabot.yml` — écosystèmes `maven` + `github-actions`, pull requests vers la branche par défaut `main` (aucun `target-branch`)

Voir [security.md](security.md) pour les règles de pinning SHA des actions.
