# VALIDATION M22 — Provider SDK & Plugin Discovery Platform

Statut : **À QUALIFIER — implémentation S0→S8 présente ; aucun PASS exact-head déclaré avant exécution locale Windows + Linux**

Date : 27 juillet 2026

Issue : #100  
PR : #101  
Branche : `m22/provider-sdk-plugin-platform`

Baseline : `main@b26833701b028ea3d09388ed87188fb1945b559d` après M21.

## Question de sortie

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

Réponse technique candidate : **oui par design et tests ajoutés, mais la réponse ne devient une preuve M22 qu’après gates exact-head Windows + Linux réels**.

## Contrats à prouver

```text
SDK API                         1
metadata path                   META-INF/morpheus-provider.properties
discovery                       explicit / metadata-only / zero classloading
activation                      explicit / compatible candidate only
classloader                     dedicated URLClassLoader per JAR
ServiceLoader                   exactly one MorpheusProviderPlugin
manifest/runtime/provider id    coherent
optional directory absent       non-fatal
duplicate plugin.id             explicit ambiguity
plugin failure                  bounded diagnostic, no core crash
reference provider              external to launcher
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

Chaque gate exécute le reactor complet puis vérifie en plus :

1. `ProviderPluginService` présent dans le shaded/runtime MORPHEUS ;
2. `ReferenceProviderPlugin` absent du launcher ;
3. copie du JAR `morpheus-provider-reference` dans un répertoire externe ;
4. discovery packagée => exactement un candidat compatible ;
5. activation + probe packagés => `reference-plugin`, `SUPPORTED` ;
6. route HTTP packagée discovery => même candidat ;
7. HEAD inchangé et aucun delta tracké post-gate.

## GitHub Actions

Run M22 observé : `30308835899`.

```text
exact-head (windows-latest)  failure | steps=None | logs_url=None
exact-head (ubuntu-latest)   failure | steps=None | logs_url=None
```

Les jobs ont échoué avant tout step. Aucun log Maven, compilation, test, JaCoCo ou packaging n’existe pour ce run. Il reste classé **runner startup / infrastructure unavailable**, et n’est pas interprété comme un échec du code M22.

## Preuves non encore acquises

```text
Windows exact-head  NOT EXECUTED / NOT PROVEN
Linux exact-head    NOT EXECUTED / NOT PROVEN
ADR-0090            PROPOSED
PR #101              DRAFT
Merge                NOT ELIGIBLE
```

Ce document sera complété uniquement avec les sorties réelles des deux validateurs.
