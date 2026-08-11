# Build, tests et validation

Ce guide décrit l’environnement de développement et les gates actifs sur la baseline **MORPHEUS 1.2.0** après D2.

## Toolchain

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
Maven Wrapper = 3.9.16
```

## Reactor Maven

Le dépôt contient 16 modules enfants, soit 17 projets Maven parent inclus :

```text
morpheus-domain
morpheus-application
morpheus-provider-sdk
morpheus-provider-testkit
morpheus-provider-reference
morpheus-provider-openspec
morpheus-provider-markdown
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
```

## Gate Maven canonique

Windows :

```powershell
.\mvnw.cmd clean verify
```

Linux :

```bash
./mvnw clean verify
```

`clean test` est utile pour le diagnostic mais n’est pas la qualification finale : les tests d’architecture dépendent des JARs et rapports produits à `package`/`verify`.

## Qualité et ratchet JaCoCo

```text
JaCoCo line ratchet        47%
JaCoCo branch ratchet      40%
D2 absolute line floor     40%
D2 absolute branch floor   35%
maven dependency analyze   failOnWarning=true
CycloneDX SBOM              JSON + XML
Jackson                     3.1.5 LTS
sqlite-jdbc                 3.53.2.0
```

MRA-12 remplace le simple floor D2 par un ratchet anti-régression. La baseline de référence a été mesurée sur le HEAD exact qualifié après MRA-11 : **47,2781% lignes / 40,4547% branches**. Les seuils exécutables sont volontairement arrondis vers le bas au point de pourcentage entier, soit **47% / 40%**, afin de conserver une petite marge déterministe sans autoriser un retour vers les anciens floors 40% / 35%.

Règle d’évolution :

1. une baisse sous 47% lignes ou 40% branches fait échouer le gate ;
2. les floors D2 40% / 35% restent des minima absolus et ne peuvent jamais affaiblir le ratchet ;
3. une amélioration de couverture ne relève le ratchet qu’après qualification du même SHA exact sur Windows et Linux ;
4. le nouveau ratchet est obtenu en arrondissant vers le bas la baseline qualifiée au point de pourcentage entier ;
5. le ratchet n’est jamais abaissé automatiquement : une baisse nécessite une décision d’audit explicite et motivée ;
6. la couverture ne justifie pas des tests artificiels : les tests doivent conserver une valeur fonctionnelle, de contrat, de sécurité ou d’architecture indépendante du chiffre.

`CoverageQualityGateTest` écrit dans `morpheus-architecture-tests/target/m21-coverage-summary.txt` la couverture observée, la baseline qualifiée, le ratchet actif et les minima D2. Un test de régression démontre explicitement qu’une couverture qui aurait satisfait les anciens floors D2 peut désormais être rejetée.

## SCA local D2

OWASP Dependency-Check est épinglé à `12.2.2` dans le profil Maven `d2-security`.

Le gate D2 lance :

```text
org.owasp:dependency-check-maven:12.2.2:aggregate
```

Politique :

```text
CVSS >= 7.0     FAIL
scan error       FAIL
test scope       skipped
report format    ALL
output            target/d2-security
```

La seule suppression locale est versionnée dans `config/dependency-check-suppressions.xml`. Elle retire l'association CPE erronée entre le module interne `io.github.fturleque:morpheus-store-sqlite:1.2.0` et SQLite 1.2.0 ; le véritable driver `org.xerial:sqlite-jdbc:3.53.2.0` reste analysé. Le scan échoue aussi si cette règle devient inutilisée, afin d'empêcher une suppression obsolète ou élargie silencieusement.

Cette étape est volontairement hors du `clean verify` développeur ordinaire car elle requiert un accès réseau aux données de vulnérabilité.

## Gate D2 Windows

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Options de diagnostic uniquement :

```powershell
-SkipSecurityScan
-SkipPortable
```

Une qualification finale D2 ne doit pas utiliser ces skips.

## Gate D2 Linux / WSL

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

Variables de diagnostic :

```text
MORPHEUS_D2_SKIP_SECURITY_SCAN=true
MORPHEUS_D2_SKIP_PORTABLE=true
```

Une qualification finale ne doit pas les activer.

## Ce que D2 et le ratchet MRA-12 prouvent

```text
workspace tracked clean
HEAD exact et stable
git diff --check
.github/workflows delta NONE
17 POMs en 1.2.0
versions de dépendances D2
clean verify
Surefire failures/errors = 0
baseline tests >= 613
baseline architecture >= 247
coverage >= 47% / 40%
absolute D2 floor >= 40% / 35%
dependency hygiene bloquante
CycloneDX SBOM
SCA local HIGH/CRITICAL
portable platform-native
product-info packagé = 1.2.0
workspace tracked clean en sortie
```

Les floors 613 / 247 incluent explicitement le test de régression Jackson D2 et les quatre contrats d’architecture D2 ; ils empêchent le gate de réussir si ces nouveaux tests ne sont pas exécutés.

Windows et Linux/WSL doivent qualifier exactement le même SHA.

## Politique CI D2

La qualification historique D2 reste fondée sur ses preuves locales. La CI publique actuelle exécute le gate durable M21 sur Windows et Linux pour les PR et protège notamment le ratchet de couverture.

## Tests ciblés

Exemples :

```powershell
.\mvnw.cmd -pl morpheus-domain test
.\mvnw.cmd -pl morpheus-api -am test
.\mvnw.cmd -pl morpheus-architecture-tests -am verify
```

Le test de sécurité Jackson D2 est dans :

```text
morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java
```

Le contrat repository D2 est dans :

```text
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java
```

## Packaging

Windows portable :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.0
```

Windows setup :

```powershell
.\distribution\build-installer.ps1 -Version 1.2.0
```

Linux portable :

```bash
bash distribution/build-portable.sh 1.2.0
```

La release stable publiée reste `v1.2.0`; D2/MRA ne déplacent ni ne recréent ce tag.

## Preuves

- R3 : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- D2 : [`../validation/VALIDATION_D2.md`](../validation/VALIDATION_D2.md)
- plan D2 : [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md)
