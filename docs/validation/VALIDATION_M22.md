# VALIDATION M22 — Provider SDK & Plugin Discovery Platform

Statut : **À QUALIFIER — implémentation S0→S8 présente ; aucun PASS exact-head déclaré avant exécution locale Windows + Linux**

Date : 27 juillet 2026

Issue : #100
PR : #101 — temporairement fermée pour respecter le gel CI avant août
Branche : `m22/provider-sdk-plugin-platform`

Baseline : `main@b26833701b028ea3d09388ed87188fb1945b559d` après M21.

## Question de sortie

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

Réponse technique candidate : **oui par architecture et contrats ajoutés, y compris une vraie lecture normalisée du JAR externe ; la réponse ne devient une preuve M22 qu’après gates exact-head Windows + Linux réels**.

## Contrats à prouver

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

## Gate canonique

Windows :

```powershell
.\validate-m22.cmd -Version 1.0.0
```

Linux :

```bash
./scripts/validate-m22.sh 1.0.0
```

Chaque gate exécute le reactor complet. Les tests d’architecture doivent en particulier démontrer un vrai JAR externe :

1. metadata discovery sans classloading ;
2. activation dans un classloader dédié ;
3. probe `SUPPORTED` avec `READ_CURRENT_SPECIFICATIONS` ;
4. lecture réelle via `SpecificationContentReader` produisant du contenu normalisé ;
5. absence de dépendance SDK/plugin dans `domain` et `application`.

Le gate packaging vérifie en plus :

1. `ProviderPluginService` présent dans le shaded/runtime MORPHEUS ;
2. `ReferenceProviderPlugin` absent du launcher ;
3. copie du JAR `morpheus-provider-reference` dans un répertoire externe ;
4. discovery packagée => exactement un candidat compatible ;
5. activation + probe packagés => `reference-plugin`, `SUPPORTED` ;
6. route HTTP packagée discovery => même candidat ;
7. HEAD inchangé et aucun delta tracké post-gate.

## Gel CI avant août

Conformément à la consigne du propriétaire, **aucune qualification M22 GitHub Actions n’est utilisée avant août**.

- `.github/workflows/ci.yml` ne porte aucun delta M22 par rapport à `main` ;
- PR #101 est temporairement fermée pour empêcher de nouveaux déclenchements `pull_request` pendant la qualification ;
- les seules preuves acceptables à ce stade sont les deux validateurs locaux exact-head.

Aucun run GitHub Actions observé pendant l’implémentation n’est retenu comme preuve M22.

## Preuves non encore acquises

```text
Windows exact-head  NOT EXECUTED / NOT PROVEN
Linux exact-head    NOT EXECUTED / NOT PROVEN
ADR-0090            PROPOSED
PR #101              TEMPORARILY CLOSED — CI FREEZE
Merge                NOT ELIGIBLE
```

Ce document sera complété uniquement avec les sorties réelles des deux validateurs. Les consolidations OpenAPI/index/roadmap postérieures devront rester docs-only afin de préserver le SHA exécutable qualifié.
