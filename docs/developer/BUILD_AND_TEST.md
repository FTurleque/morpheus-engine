# Build, tests et validation

Ce guide décrit l’environnement de développement, le reactor Maven, les tests ciblés, le gate autoritatif, le packaging portable et la manière de diagnostiquer un build local.

## 1. Toolchain

Le parent Maven impose :

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
```

Le dépôt fournit un Maven Wrapper configuré sur Maven 3.9.16. Utiliser le wrapper plutôt qu’un Maven système.

### Windows

```powershell
java -version
.\mvnw.cmd --version
```

### Unix/Linux

```bash
java -version
./mvnw --version
```

## 2. Reactor Maven

Le parent agrège 14 modules :

```text
morpheus-domain
morpheus-application
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
+ parent reactor
```

Le gate M18 a validé **14/14 modules Maven SUCCESS**.

## 3. Gate local Maven

Windows :

```powershell
.\mvnw.cmd clean test
```

Unix/Linux :

```bash
./mvnw clean test
```

Ce gate repart d’un `target/` propre et exécute le reactor complet.

## 4. Dernier gate intégré — M18

Validateur canonique Windows :

```powershell
.\validate-m18.cmd
```

Head de code réellement exécuté :

```text
7e8caacff567f51354fcb88bd7505a6d135071c0
```

Résultats :

```text
Domain                         40/40 PASS
Application                  104/104 PASS
OpenSpec                       26/26 PASS
Structured Markdown             2/2 PASS
Synthetic                        7/7 PASS
SQLite                           7/7 PASS
MINOS Integration                8/8 PASS
NEXUS Integration                7/7 PASS
MCP                              6/6 PASS
API                            12/12 PASS
CLI                            29/29 PASS
Architecture                 170/170 PASS
---------------------------------------
TOTAL                        418/418 PASS
Failures                           0
Errors                             0
Skipped                            0
BUILD SUCCESS
```

Packaging M18 :

```text
Windows portable packaging   PASS
Packaged smokes              PASS
API health smoke             PASS
Portable ZIP                 33,919,431 bytes
```

PR #86 a ensuite été fusionnée :

```text
merge 30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
```

Le SHA de code testé et le merge commit restent volontairement distincts. Voir [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).

## 5. Tests ciblés

Un module seul :

```powershell
.\mvnw.cmd -pl morpheus-domain test
.\mvnw.cmd -pl morpheus-application test
.\mvnw.cmd -pl morpheus-provider-markdown test
.\mvnw.cmd -pl morpheus-api test
.\mvnw.cmd -pl morpheus-mcp test
.\mvnw.cmd -pl morpheus-architecture-tests test
```

Module + dépendances :

```powershell
.\mvnw.cmd -pl morpheus-api -am test
```

`-am` signifie *also make*.

Les tests ciblés accélèrent la boucle locale, mais ne remplacent pas `clean test` ni le validateur de jalon avant validation finale.

## 6. Compilation sans tests

Diagnostic rapide :

```powershell
.\mvnw.cmd -DskipTests compile
```

Module ciblé :

```powershell
.\mvnw.cmd -pl morpheus-api -am -DskipTests compile
```

Cette commande n’est pas une preuve fonctionnelle.

## 7. Packaging portable Windows

```powershell
.\distribution\build-portable.ps1
```

Artefact :

```text
dist/morpheus-<version>-windows-x64.zip
```

Le script construit l’uber-JAR, produit un `jpackage app-image`, embarque le runtime Java, construit l’archive portable et exécute les smokes prévus.

Le packaging M18 vérifie notamment la présence des surfaces M14→M18, du provider Structured Markdown et de la migration SQLite V012.

## 8. Packaging portable Linux

```bash
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

Une preuve Windows ne constitue pas une preuve Linux. Les validations cross-platform doivent nommer explicitement la plateforme réellement exécutée.

## 9. Contraintes de packaging

La distribution MORPHEUS peut embarquer les **adapters clients** MINOS/NEXUS, mais jamais leurs implémentations ni JARVIS.

Le packaging vérifie l’absence de :

```text
com/minos/*
com/nexus/*
com/jarvis/*
```

Il vérifie aussi que les classes de composition M18 et `db/migration/V012__multi_provider_composition.sql` sont présentes dans l’artefact attendu.

## 10. Tests d’architecture

`morpheus-architecture-tests` utilise ArchUnit pour transformer certaines frontières en règles exécutables.

Il protège notamment :

```text
domain -X-> adapters
application -X-> adapters
api -X-> cli/mcp/integration
MORPHEUS -X-> com.jarvis.*
MINOS adapter -X-> com.minos.*
NEXUS adapter -X-> com.nexus.*
provider-specific types -X-> domain/application
```

Dernière preuve : **170/170 PASS** au gate M18.

## 11. SQLite et migrations

M18 porte la migration :

```text
V012__multi_provider_composition.sql
```

Avant validation d’un changement de persistance :

- vérifier l’ordre des migrations ;
- tester création depuis zéro ;
- tester upgrade depuis la baseline précédente lorsque pertinent ;
- tester close/reopen ;
- vérifier transactions et restauration de `autoCommit` en erreur ;
- ne jamais exposer un état publié partiellement construit.

## 12. Diagnostiquer un build qui échoue

### Enforcer Java/Maven

```powershell
java -version
.\mvnw.cmd --version
```

### Dépendance inter-module introuvable

```powershell
.\mvnw.cmd -pl morpheus-api -am test
```

### IntelliJ vert, Maven rouge

Maven est la source de vérité. Recharger le `pom.xml` racine comme projet Maven et vérifier le JDK du Maven Runner.

### Tests ciblés verts, reactor rouge

Corriger le reactor complet ; ne pas valider sur la seule base des tests ciblés.

## 13. Warnings connus

Le gate M18 a observé des warnings non bloquants concernant :

- accès natif SQLite sous Java 24 ;
- absence de provider SLF4J ;
- APIs dépréciées dans certaines fixtures MCP ;
- ressources/classes chevauchantes lors du shading.

Un warning nouveau doit être évalué ; il ne doit pas être classé automatiquement comme historique.

## 14. Règle de preuve

Pour une modification technique :

1. documenter l’invariant ou la décision ;
2. définir le contrat ;
3. implémenter ;
4. exécuter les tests ciblés utiles ;
5. auditer signatures Maven, dépendances, migrations et scripts ;
6. exécuter le gate complet ;
7. exécuter packaging/smokes lorsque concernés ;
8. enregistrer le SHA réellement testé ;
9. accepter l’ADR seulement après preuve lorsqu’elle dépend d’une hypothèse ;
10. fusionner uniquement selon la gouvernance du dépôt.

Historique : [`../governance/ROADMAP.md`](../governance/ROADMAP.md) et [`../validation/`](../validation/).
