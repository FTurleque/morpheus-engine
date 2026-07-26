# Build, tests et validation

Ce guide décrit l’environnement de développement, le reactor Maven, les tests ciblés, le gate autoritatif, le packaging portable et la manière de diagnostiquer un build local sur la baseline **M18 intégrée**.

## 1. Toolchain

Le parent Maven impose :

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
```

Le dépôt fournit Maven Wrapper 3.9.16.

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

## 2. Reactor Maven M18

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
```

Le gate M18 rapporte **14/14 modules Maven SUCCESS** dans le reactor complet, parent inclus.

## 3. Gate local développeur

### Windows

```powershell
.\mvnw.cmd clean test
```

### Unix/Linux

```bash
./mvnw clean test
```

Ce gate repart d’un `target/` propre. Les tests ciblés ne le remplacent pas pour une validation finale.

## 4. Validateur M18 mono-commande

Windows :

```powershell
.\validate-m18.cmd
```

Le validateur M18 contrôle notamment :

```text
workspace / SHA
toolchain
clean test reactor complet
architecture tests
packaging Windows
packaged smokes
API health smoke
failure summary automatique
```

Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).

## 5. Gate M18 autoritatif

Head de code réellement testé :

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

Packaging :

```text
Windows packaging     PASS
Packaged smokes       PASS
API health smoke      PASS
Portable ZIP          33,919,431 bytes
```

L’environnement de ce gate était Windows 10 amd64, OpenJDK 24.0.1, Maven Wrapper 3.9.16, compilation `release 21`.

Le merge ultérieur M18 est `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`. Il ne remplace pas le SHA réellement exécuté par le gate.

## 6. Tests ciblés

Module seul :

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

Plusieurs modules :

```powershell
.\mvnw.cmd -pl morpheus-domain,morpheus-application,morpheus-api -am test
```

## 7. Ordre de test recommandé

```text
modification
  ↓
tests unitaires ciblés
  ↓
module -pl
  ↓
-pl ... -am si dépendances
  ↓
clean test reactor complet
  ↓
architecture
  ↓
packaging/smokes si concernés
  ↓
preuve enregistrable
```

Exemples :

| Changement | Tests ciblés avant gate complet |
|---|---|
| value object domaine | `-pl morpheus-domain test` |
| composition application | `-pl morpheus-application -am test` |
| provider Markdown | `-pl morpheus-provider-markdown -am test` |
| SQLite/migration | `-pl morpheus-store-sqlite -am test` |
| endpoint HTTP | `-pl morpheus-api -am test` |
| tool MCP | `-pl morpheus-mcp -am test` |
| frontière | `-pl morpheus-architecture-tests -am test` |

## 8. Compilation sans tests

Diagnostic uniquement :

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -pl morpheus-api -am -DskipTests compile
```

Ce n’est pas une preuve fonctionnelle.

## 9. Packaging portable Windows

```powershell
.\distribution\build-portable.ps1
```

Artefact :

```text
dist/morpheus-<version>-windows-x64.zip
```

Le packaging embarque le runtime Java, CLI/MCP/API, le provider Structured Markdown et les migrations jusqu’à V012.

L’utilisateur final n’a pas besoin de JDK.

## 10. Packaging portable Linux

```bash
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

**Une preuve Windows ne constitue pas une preuve Linux.** Toute qualification M19 devra distinguer les deux environnements.

## 11. Contraintes de packaging

La distribution peut embarquer les adapters clients MINOS/NEXUS, mais jamais leurs implémentations ni JARVIS.

Absences obligatoires :

```text
com/minos/*
com/nexus/*
com/jarvis/*
```

Le provider Markdown MORPHEUS, lui, fait partie du runtime M18.

## 12. Tests d’architecture

`morpheus-architecture-tests` protège notamment :

```text
domain -X-> adapters
application -X-> adapters
provider-specific types -X-> domain/application contracts
api -X-> cli/mcp/integration
MORPHEUS -X-> com.jarvis.*
MINOS adapter -X-> com.minos.*
NEXUS adapter -X-> com.nexus.*
```

Dernier gate : **170/170 PASS**.

## 13. SQLite et migrations

M18 introduit **V012** pour l’état de composition multi-provider.

Avant validation d’une modification SQLite :

```text
migration forward compatible
store Memory/SQLite contract parity
transaction boundaries correctes
close/reopen exact
no partial published ACTIVE state
```

M18 valide notamment la restauration du mode auto-commit après erreur de sauvegarde de composition.

## 14. Warnings connus

Le gate M18 a observé des warnings non bloquants concernant :

```text
accès natif SQLite sous Java 24
absence de provider SLF4J
APIs dépréciées dans certaines fixtures MCP
ressources/classes chevauchantes lors du shading
```

Un warning nouveau doit être évalué ; il ne devient pas « historique » par défaut.

## 15. Règle de preuve

```text
1. documenter invariant / contrat
2. implémenter
3. tests ciblés
4. reactor complet
5. architecture
6. packaging/smokes si concernés
7. enregistrer le SHA réellement testé
8. accepter ADR seulement après preuve
9. mettre à jour roadmap/validation
10. merger uniquement selon la gouvernance
```

Historique : [`../governance/ROADMAP.md`](../governance/ROADMAP.md) et [`../validation/`](../validation/).

## 16. M19

M19 introduira `scripts/validate-m19.ps1` et `validate-m19.cmd` avec benchmarks/gates reproductibles, robustesse, packaging et failure summary. Les budgets devront être fixés avant optimisation et les preuves Windows/Linux resteront explicitement séparées.