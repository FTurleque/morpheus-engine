# Conception : gérer dans le POM racine tout plugin Maven qu'un workflow appelle

<!-- Conception allégée : POM racine, un workflow et un test de cohérence, sans code de production. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `.github/workflows/ci.yml`, job `sonar` :
  `org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar` ; `pom.xml` ne nomme pas `sonar`.
- [EXISTANT VÉRIFIÉ] `AuditHardeningWorkflowContractTest#sonarAnalysisNeverRunsOnPullRequestsAndStaysOutOfTheRequiredVerifyJob`
  exige cette chaîne littérale.
- [EXISTANT VÉRIFIÉ] Appels par coordonnées versionnées relevés le 17/11/2026 hors `uses:` : `ci.yml` (Sonar),
  `security.yml` et `scripts/validate-d2.sh`/`.ps1` (`org.owasp:dependency-check-maven:13.0.0:…`). Le plugin OWASP est
  déclaré, avec la propriété `dependency-check.maven.plugin.version`, dans les profils `d2-security` et
  `d2-security-tests` du POM racine, pas dans `<pluginManagement>` ; `RepositoryDocumentationCoherenceTest` compare déjà
  la version des validateurs à cette propriété, et `D2RepositoryHardeningArchitectureTest` et
  `DependencyCheckWorkflowContractTest` épinglent la chaîne de `security.yml`.
- [EXISTANT VÉRIFIÉ] `D2RepositoryHardeningArchitectureTest#noModulePomStatesAVersionTheRootPomShouldDecide` tient les
  POM de module ; aucun test ne lit les appels de plugins des workflows.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `dependency-version-management` | Un plugin Maven qu'un workflow ou un validateur appelle par coordonnées DOIT avoir sa version décidée par le POM racine | D1, D2 |

## Décisions

### D1 : le scanner entre dans le `<pluginManagement>` racine et s'appelle sans version
- **Nature** : [DÉCISION]
- **Exigences servies** : `dependency-version-management` › Un plugin Maven qu'un workflow ou un validateur appelle par coordonnées DOIT avoir sa version décidée par le POM racine
- **Choix** : propriété `sonar.maven.plugin.version` (valeur de départ : celle d'aujourd'hui, pour que le lot ne mêle pas
  montée et déplacement), entrée `org.sonarsource.scanner.maven:sonar-maven-plugin` dans `<pluginManagement>`, appel
  `org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` sans version dans `ci.yml`. Le test de contrat lit la
  propriété dans le POM au lieu de la chaîne littérale. La table des versions de `.claude/rules/build.md` reçoit la
  propriété (`GovernanceSurfaceCoherenceTest` la compare au POM).
- **Alternatives écartées** : garder la version dans `ci.yml` et ajouter une règle Dependabot (Dependabot ne lit pas les
  lignes de commande) ; une entrée `<plugins>` du build (le plugin s'attacherait au cycle de chaque module).
- **Compromis** : la résolution d'un appel sans version par la gestion du POM est à confirmer par une exécution du job
  (proposition à valider dans proposal.md).
- **ADR** : non concerné.

### D2 : la règle générale vit dans un test de cohérence de la famille `d2`
- **Nature** : [PROPOSITION]
- **Exigences servies** : même exigence
- **Choix** : un test de `morpheus-architecture-tests` (paquet `d2`, `@CoherenceCheck`) parcourt `.github/workflows/*.yml`
  et `scripts/*.sh`/`*.ps1`, extrait les appels `groupe:artefact[:version]:but` hors lignes `uses:`, cherche le plugin
  dans le POM racine (gestion, build, profils) et compare la version écrite à la valeur résolue de sa propriété.
  Assertion textuelle : les cibles sont des `.yml`, `.sh`, `.ps1`, où le texte est la seule prise
  (`.claude/rules/architecture.md`). Non-vacuité : au moins un appel trouvé.
- **Alternatives écartées** : étendre `noModulePomStatesAVersionTheRootPomShouldDecide` (il juge les POM, pas les
  scripts) ; retirer aussi la version des appels OWASP (trois tests l'épinglent et le gate de fraîcheur dépend de
  l'appel exact ; la règle « version égale à celle du POM » suffit à empêcher la dérive).
- **Compromis** : l'expression d'extraction doit ignorer les références d'action (`uses: owner/repo@sha`) et les
  coordonnées de dépendances lues ailleurs ; le test liste ce qu'il a jugé dans son message d'échec.
- **ADR** : non concerné.

### D3 : pas de montée dans ce lot
- **Nature** : [DÉCISION]
- **Choix** : la première montée du scanner arrive par la pull request Dependabot que le déplacement rend possible.
- **Alternatives écartées** : monter à la dernière version dans le même lot (deux causes d'échec dans un lot).
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| La règle « tout plugin dans son `<pluginManagement>` » ne vaut pas pour le plugin OWASP, déclaré dans des profils | `.claude/rules/build.md`, TOUJOURS | `pom.xml`, profils `d2-security*` | l'exigence admet toute déclaration avec version du POM racine ; préciser la phrase de `build.md` dans le même lot |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Un plugin Maven qu'un workflow ou un validateur appelle par coordonnées DOIT avoir sa version décidée par le POM racine | Le scanner Sonar est visible du robot de mises à jour | test de D2 et `AuditHardeningWorkflowContractTest` adapté | à écrire |
| idem | Un plugin appelé hors du POM fait échouer le build | test de D2, violation plantée (arbre courant : appel Sonar) | à écrire |
| idem | Une version d'appel restée en arrière fait échouer le build | test de D2, version OWASP modifiée en local | à écrire |
| idem | Un appel avec la version gérée est admis | test de D2 sur l'arbre courant | à écrire |
| idem | Le contrôle a trouvé des appels à juger | assertion de non-vacuité | à écrire |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/dependency-version-management.md` (nouvelle exigence, traçabilité) ;
`.claude/rules/build.md` (table des versions, phrase sur la déclaration des plugins).

## Questions ouvertes

Aucune.
