# VALIDATION M22 — Provider SDK & Plugin Discovery Platform

Statut : **PASS — qualification Windows + Linux exact-head acquise sur le même SHA exécutable**

Date : 28 juillet 2026

Issue : #100
PR : #101 — temporairement fermée pour respecter le gel CI avant août
Branche : `m22/provider-sdk-plugin-platform`

Baseline : `main@b26833701b028ea3d09388ed87188fb1945b559d` après M21.

Head exécutable qualifié Windows + Linux :

```text
e42bc31384831e56592b11a3509b49a3fdf61773
```

## Question de sortie

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

**Réponse : oui.** M22 démontre un JAR provider externe découvrable sans exécution de code, compatible explicitement, activé uniquement à la demande dans un classloader dédié, sondé par `SpecificationProvider`, puis lu réellement via `SpecificationContentReader` pour produire du contenu provider-neutral normalisé.

## Contrats prouvés

```text
SDK API                         1
metadata path                   META-INF/morpheus-provider.properties
discovery                       explicit / metadata-only / zero classloading
activation                      explicit / compatible candidate only
classloader                     dedicated URLClassLoader per JAR
ServiceLoader                   exactly one MorpheusProviderPlugin
manifest/plugin/provider/reader id coherent
optional directory absent       non-fatal
duplicate plugin.id             explicit ambiguity
plugin failure                  bounded diagnostic, no core crash
probe                           SpecificationProvider.probe
normalized read                 SpecificationContentReader.read
reference provider              external to launcher
reference capabilities          DISCOVER_PROJECT + READ_CURRENT_SPECIFICATIONS
CLI/MCP/HTTP                    explicit discovery + probe
baseline tests                  >= 473
architecture                    >= 187
JaCoCo line/branch              >= 25% / 20%
SBOM/provenance                 PASS
Windows portable                PASS
Linux portable                  PASS
post-gate executable delta      NONE
```

## Gates canoniques exécutés

Windows :

```powershell
.\validate-m22.cmd -Version 1.0.0
```

Linux WSL2 :

```bash
bash ./scripts/validate-m22.sh 1.0.0
```

Les deux gates ont démarré et terminé sur :

```text
e42bc31384831e56592b11a3509b49a3fdf61773
```

## Preuve Windows

```text
M22 VALIDATION PASS
sha=e42bc31384831e56592b11a3509b49a3fdf61773
baseRef=origin/main
version=1.0.0
tests=494
architectureTests=190
lineCoverage=0.470508
branchCoverage=0.418839
sdkApiVersion=1
externalReferenceProvider=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Preuves complémentaires Windows :

- reactor 17/17 SUCCESS ;
- `git diff --check` PASS ;
- provider SDK présent dans le runtime packagé ;
- `ReferenceProviderPlugin` absent du launcher puis copié comme vrai plugin externe ;
- discovery + activation isolée + probe `SUPPORTED` PASS ;
- CLI/MCP/HTTP provider platform convergence PASS ;
- API packagée version/discovery PASS ;
- archive `morpheus-1.0.0-windows-x64.zip` créée ;
- workspace tracké inchangé après gate.

## Preuve Linux WSL2

```text
M22 VALIDATION PASS
sha=e42bc31384831e56592b11a3509b49a3fdf61773
baseRef=origin/main
version=1.0.0
tests=494
architectureTests=190
lineCoverage=0.470389
branchCoverage=0.418839
sdkApiVersion=1
externalReferenceProvider=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Le wrapper de qualification a ensuite confirmé :

```text
M22 LINUX EXIT CODE: 0
```

Preuves complémentaires Linux :

- OpenJDK 21.0.11 ;
- reactor 17/17 SUCCESS ;
- provider SDK présent dans le runtime packagé ;
- provider de référence non embarqué, chargé comme JAR externe ;
- discovery + activation isolée + probe PASS ;
- CLI/MCP/HTTP provider platform convergence PASS ;
- API health/readiness/metrics/version PASS ;
- runtime `jdk.httpserver + java.sql + java.net.http` PASS ;
- archive `morpheus-1.0.0-linux-x64.tar.gz` créée ;
- `git status --short` vide après le gate ;
- HEAD toujours exactement `e42bc31384831e56592b11a3509b49a3fdf61773`.

## Preuve architecture du vrai provider externe

Les tests d’architecture démontrent :

1. découverte du manifeste sans classloading ;
2. activation dans un `URLClassLoader` dédié ;
3. probe `SUPPORTED` avec `READ_CURRENT_SPECIFICATIONS` ;
4. lecture réelle via `SpecificationContentReader` ;
5. production d’une `Specification`, d’une `Evidence` et d’une `Provenance` normalisées ;
6. absence de dépendance SDK/plugin dans `morpheus-domain` et `morpheus-application`.

Ainsi :

```text
plugin discovery != plugin activation
capability declaration != capability implementation proof
probe != read
provider plugin != domain dependency
```

## Gel CI avant août

Conformément à la consigne du propriétaire, GitHub Actions n’est pas utilisé comme preuve M22 avant août.

- `.github/workflows/ci.yml` ne porte aucun delta M22 par rapport à `main` ;
- PR #101 reste temporairement fermée afin d’éviter de nouveaux déclenchements `pull_request` ;
- les preuves de sortie M22 sont les deux gates locaux exact-head ci-dessus.

## Conclusion

```text
Windows exact-head  PASS
Linux exact-head    PASS
Executable SHA      e42bc31384831e56592b11a3509b49a3fdf61773
Tests               494 PASS
Architecture        190 PASS
SDK API             1
External provider   PASS
SBOM/provenance     PASS Windows + Linux
Portable            PASS Windows + Linux
Executable delta    NONE Windows + Linux
ADR-0090            ACCEPTED — M22
```

Les commits ultérieurs de consolidation documentaire doivent rester distincts du SHA exécutable qualifié. Toute modification de code produit, POM, contrat runtime, packaging ou validateur invaliderait cette preuve et imposerait une nouvelle qualification Windows + Linux.