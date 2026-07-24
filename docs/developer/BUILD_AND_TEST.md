# Build, tests et validation

## Toolchain

Le parent Maven impose :

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
```

Utiliser le Maven Wrapper du dépôt plutôt qu’un Maven système.

## Gate local autoritatif

### Windows

```powershell
.\mvnw.cmd clean test
```

### Unix / Linux

```bash
./mvnw clean test
```

Le gate doit être exécuté avant de déclarer un jalon ou une modification technique validée.

## Gate M14 de référence

Le dernier gate fonctionnel complet validé avant intégration de M14 a produit :

```text
Domain              21/21 PASS
Application         87/87 PASS
OpenSpec             26/26 PASS
Synthetic             7/7 PASS
SQLite                7/7 PASS
MINOS Integration     8/8 PASS
NEXUS Integration     7/7 PASS
MCP                    5/5 PASS
API                    9/9 PASS
CLI                  20/20 PASS
Architecture       160/160 PASS
--------------------------------
TOTAL              357/357 PASS
Failures                 0
Errors                   0
Skipped                  0
BUILD SUCCESS
```

Cette valeur est une preuve historique M14, pas une règle imposant que le nombre total de tests reste figé.

## Tests ciblés

Exécuter un module :

```powershell
.\mvnw.cmd -pl morpheus-application test
.\mvnw.cmd -pl morpheus-api test
.\mvnw.cmd -pl morpheus-mcp test
.\mvnw.cmd -pl morpheus-architecture-tests test
```

Avec dépendances de modules nécessaires :

```powershell
.\mvnw.cmd -pl morpheus-api -am test
```

Le gate final reste toutefois `clean test` sur le reactor complet.

## Packaging portable

### Windows

```powershell
.\distribution\build-portable.ps1
```

Artefact :

```text
dist/morpheus-<version>-windows-x64.zip
```

Le script construit l’uber-JAR, produit un `jpackage app-image`, embarque le runtime Java et exécute les smokes de launcher/API/intégrations optionnelles.

### Linux

```bash
chmod +x mvnw distribution/build-portable.sh
./distribution/build-portable.sh
```

Artefact :

```text
dist/morpheus-<version>-linux-x64.tar.gz
```

## Contraintes de packaging

La distribution MORPHEUS peut embarquer les adapters clients MINOS/NEXUS mais jamais leurs implémentations ni JARVIS.

Le packaging vérifie notamment l’absence de :

```text
com/minos/*
com/nexus/*
com/jarvis/*
```

## Smokes cross-repo complémentaires

```text
distribution/test-minos-compatibility.ps1
distribution/test-nexus-compatibility.ps1
```

Ils servent à prouver la compatibilité avec de vrais runtimes externes. Ils ne remplacent pas le gate autonome MORPHEUS.

## Warnings connus

Les validations M12-M14 ont observé des warnings non bloquants liés notamment à l’accès natif SQLite et à l’absence de provider SLF4J dans certains tests. Un warning nouveau doit être évalué ; il ne doit pas être automatiquement considéré comme historique.

## Règle de preuve

Pour une modification technique :

1. documenter l’invariant ou la décision ;
2. implémenter ;
3. exécuter les tests ciblés utiles ;
4. exécuter le gate complet ;
5. enregistrer le SHA réellement testé ;
6. accepter l’ADR seulement après preuve lorsqu’elle dépend d’une hypothèse ;
7. mettre à jour roadmap/validation ;
8. fusionner uniquement selon la gouvernance du dépôt.

Historique des preuves : [`../governance/ROADMAP.md`](../governance/ROADMAP.md) et [`../validation/`](../validation/).