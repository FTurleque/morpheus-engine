---
applyTo: "pom.xml,**/pom.xml,.mvn/**,mvnw,mvnw.cmd,.github/workflows/**,.github/dependabot.yml"
---

# Build & Dépendances

Détail complet : `.claude/rules/build.md` (source partagée avec Claude Code).

## Versions pinnées — assertées textuellement

`D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` exige la
présence littérale dans `pom.xml` racine de plusieurs propriétés de version et flags
(`failOnWarning`, profil `d2-security`, seuil CVSS). **Ne jamais citer ces valeurs de
mémoire** — les lire dans `pom.xml` avant toute réponse qui les mentionne ; bumper une
version sans mettre à jour le test correspondant casse le gate.

## TOUJOURS

- Utiliser `./mvnw` (Maven Wrapper) — jamais `mvn` nu
- Déclarer toute dépendance dans le `<dependencyManagement>` du POM racine, puis la
  référencer **sans version** dans le module
- Vérifier l'hygiène après ajout : `./mvnw dependency:analyze` — `failOnWarning` actif,
  **0 warning** ou le build casse
- Construire `morpheus-provider-reference` avant les tests d'architecture

## JAMAIS

- Jamais de version de dépendance en dur dans un POM de module
- Jamais de dépendance `unused declared` ni `used undeclared`
- Jamais ajouter `morpheus-provider-sdk`/`morpheus-provider-reference` aux POMs de
  `domain`/`application`, ni `morpheus-provider-reference` dans `morpheus-cli`
- Jamais introduire une CVE au-dessus du seuil configuré sans suppression approuvée et
  commentée dans `config/dependency-check-suppressions.xml`
- Jamais de tag mutable `uses: actions/<x>@v...` dans les workflows — pinning SHA 40
  caractères obligatoire

## Stack imposée

`jdk.httpserver` (HTTP) · `io.modelcontextprotocol.sdk` (MCP) · SQLite JDBC (persistance)
· Jackson 3 (`tools.jackson`) · `jpackage` + Inno Setup (distribution, **pas Docker**) ·
Maven multi-module. Voir les ADR référencés dans `.claude/rules/build.md` pour le
*pourquoi* de chaque choix.

