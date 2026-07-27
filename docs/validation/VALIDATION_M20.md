# Validation M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **EN ATTENTE DE PREUVE RÉELLE**

Issue : #92  
PR : #93  
Branche : `m20-release-engineering-prod-installation-1.0`

## Règle de preuve

Ce document ne déclare aucun PASS par anticipation.

La preuve M20 doit provenir des validateurs versionnés exécutés sur un workspace Git propre :

```text
Windows : validate-m20.cmd -> scripts/validate-m20.ps1
Linux   : scripts/validate-m20.sh
```

GitHub Actions n’est pas la source de vérité du jalon.

## Gate Windows attendu

```text
workspace/SHA/version 1.0.0
full Maven reactor
installer contract
release construite depuis un tag pointant exactement sur HEAD
portable ZIP
setup EXE
SHA-256 ZIP + setup vérifiés
manifest lié au SHA/tag exacts
installation per-user/non-admin
option PATH utilisateur
runtime packagé sans JDK utilisateur
layout PROD programme/données
MINOS/NEXUS désactivés par défaut
API health/readiness/metrics
upgrade conservant DB/config
uninstall conservant DB/config
suppression de l’entrée PATH par uninstall
réinstallation retrouvant les données
exact-head stability
```

## Gate Linux attendu

```text
workspace/SHA/version 1.0.0
full Maven reactor
release construite depuis un tag pointant exactement sur HEAD
portable tar.gz
SHA-256 vérifié
manifest lié au SHA/tag exacts
runtime packagé sans JDK utilisateur
layout XDG data/config/state
SQLite reopen/create smoke
MINOS/NEXUS désactivés par défaut
exact-head stability
```

## Environnements

À remplir uniquement depuis les exécutions réelles.

### Windows

```text
SHA        PENDING
OS         PENDING
Java build PENDING
Maven      PENDING
Inno Setup PENDING
Result     PENDING
```

### Linux

```text
SHA        PENDING
OS/fs      PENDING
Java build PENDING
Maven      PENDING
Result     PENDING
```

## Résultats

```text
Tests                     PENDING
Architecture              PENDING
Windows setup             PENDING
Windows portable          PENDING
Linux portable            PENDING
SHA-256                    PENDING
No-user-JDK                PENDING
PATH option                PENDING
Program/data separation   PENDING
Upgrade preservation      PENDING
Uninstall preservation    PENDING
Integrations opt-in       PENDING
Release from exact tag    PENDING
```

ADR-0088 reste **Proposée** tant que ces preuves ne sont pas réellement obtenues.
