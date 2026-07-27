# Build, tests et validation

Ce guide décrit l’environnement de développement, le reactor Maven, les gates autoritatifs et le packaging sur la baseline **M20 / MORPHEUS 1.0.0 intégrée**.

## 1. Toolchain

Le parent Maven impose :

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
```

Le dépôt fournit Maven Wrapper 3.9.16.

Windows :

```powershell
java -version
.\mvnw.cmd --version
```

Linux :

```bash
java -version
./mvnw --version
```

## 2. Reactor Maven

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

Le gate M20 rapporte **14/14 modules SUCCESS**, parent inclus.

## 3. Gate local développeur

Windows :

```powershell
.\mvnw.cmd clean test
```

Linux :

```bash
./mvnw clean test
```

Les tests ciblés ne remplacent jamais le reactor complet pour une qualification finale.

## 4. Validateurs M20

Windows :

```powershell
.\validate-m20.cmd
```

Le gate couvre notamment :

```text
workspace / SHA / version
clean test reactor complet
architecture tests
installer contract
release tag exact
portable ZIP + SHA-256
setup EXE + SHA-256
release manifest
install per-user
PATH option
runtime sans JDK utilisateur
API health/readiness/metrics
upgrade preservation
uninstall preservation
reinstall preservation
exact-head stability
```

Linux :

```bash
bash scripts/validate-m20.sh
```

Le gate couvre :

```text
workspace / SHA / version
clean test reactor complet
architecture tests
release tag exact
portable tar.gz + SHA-256
release manifest
runtime sans JDK utilisateur
XDG data/config/state
SQLite smoke
MINOS/NEXUS opt-in defaults
exact-head stability
```

Preuve : [`../validation/VALIDATION_M20.md`](../validation/VALIDATION_M20.md).

## 5. Gate M20 autoritatif

```text
Code qualifié   9199ed43c4bd8596a97db055eeff17ae31399eb8
Version         1.0.0
Windows         PASS
Linux ext4      PASS via WSL2
Tests           454/454 PASS
Architecture    182/182 PASS
Failures        0
Errors          0
Skipped         0
Reactor         14/14 SUCCESS
```

Le merge ultérieur M20 est :

```text
75d0b82ab0c960692db2fee1ced146fa6547fd4a
```

Le SHA de merge ne remplace pas le SHA réellement exécuté par les gates.

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
tests ciblés
  ↓
module -pl / -am
  ↓
clean test reactor complet
  ↓
architecture
  ↓
packaging/smokes si concernés
  ↓
preuve enregistrable exact-head
```

## 8. Compilation sans tests

Diagnostic uniquement :

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -pl morpheus-api -am -DskipTests compile
```

Ce n’est pas une preuve fonctionnelle.

## 9. Packaging Windows

Portable :

```powershell
.\distribution\build-portable.ps1 -Version 1.0.0
```

Setup :

```powershell
.\distribution\build-installer.ps1 -Version 1.0.0
```

Release depuis un tag exact :

```powershell
.\distribution\build-release.ps1 -Version 1.0.0 -ExpectedTag v1.0.0
```

Artefacts :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
MORPHEUS-1.0.0-windows-x64-setup.exe.sha256
morpheus-1.0.0-windows-x64.zip
morpheus-1.0.0-windows-x64.zip.sha256
```

## 10. Packaging Linux

Portable :

```bash
bash distribution/build-portable.sh 1.0.0
```

Release depuis un tag exact :

```bash
bash distribution/build-release.sh 1.0.0 v1.0.0
```

Artefacts :

```text
morpheus-1.0.0-linux-x64.tar.gz
morpheus-1.0.0-linux-x64.tar.gz.sha256
```

Une preuve Windows ne constitue jamais une preuve Linux.

## 11. Contraintes de packaging

Les distributions peuvent embarquer les adapters clients MINOS/NEXUS, mais jamais leurs implémentations ni JARVIS.

```text
com/minos/*   absent
com/nexus/*   absent
com/jarvis/*  absent
```

Le runtime Java est embarqué ; aucun JDK utilisateur n’est requis.

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

Gate M20 : **182/182 PASS Windows + Linux**.

## 13. Consolidation D1

D1 est documentaire uniquement. Son gate local doit prouver :

```text
diff limité à README.md + docs/**
git diff --check PASS
full Maven reactor PASS
architecture PASS
workspace propre
```

Preuve en cours : [`../validation/VALIDATION_D1.md`](../validation/VALIDATION_D1.md).