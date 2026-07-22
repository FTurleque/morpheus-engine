# ADR-0029 — Prouver l'anti-lock-in avec un second provider synthétique

- Statut : **Acceptée — M2**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0002, ADR-0009, ADR-0011, ADR-0023, ADR-0028
- Portée : provider neutrality, anti-corruption boundary, preuve anti-lock-in M2

## Contexte

OpenSpec est le provider de référence, mais MORPHEUS ne doit pas devenir un modèle OpenSpec déguisé.

M2 doit prouver en code de production que deux formats distincts peuvent traverser les mêmes ports applicatifs et produire le même domaine MORPHEUS.

La fixture M0 `synthetic-basic/morpheus-spec.json` constitue l'oracle du second format.

## Décision

Introduire un module :

```text
morpheus-provider-synthetic
```

Ce module est **verification-only** : il existe pour démontrer l'architecture extensible, pas comme provider produit à supporter publiquement.

Il implémente les mêmes ports que le provider OpenSpec :

```text
SpecificationProvider
SpecificationContentReader
```

Architecture :

```text
OpenSpec source ──> OpenSpec adapter ────┐
                                         │
                                         ├──> SpecificationContentReader
                                         │        ↓
Synthetic JSON ─> Synthetic adapter ─────┘    ProviderReadResult
                                                  ↓
                                         NormalizedProjectContent
```

## Invariants

```text
provider format != MORPHEUS domain
OpenSpec type -X-> application contract
Synthetic JSON type -X-> application contract
consumer does not branch on provider id
the same ReadCategory vocabulary applies to both providers
external identity is provider-scoped
```

Une external key identique dans deux providers ne constitue pas la même identité MORPHEUS :

```text
(openspec, requirement, X) != (synthetic-json, requirement, X)
```

## Provider synthétique

Le provider synthétique lit :

```text
morpheus-spec.json
```

Capacités effectivement implémentées :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_REQUIREMENTS
READ_SCENARIOS
READ_CHANGES
```

Il ne revendique pas les catégories qu'il ne normalise pas réellement.

Le parser JSON reste encapsulé dans `com.morpheus.provider.synthetic` et n'ajoute aucune dépendance externe.

## Fixture

`experiments/m0/fixtures/synthetic-basic/morpheus-spec.json` expose explicitement :

```text
Specification : billing / Billing
Requirement   : billing/invoice-retention
Scenario      : Retain invoice
Change        : extend-retention
```

La fixture est enrichie de métadonnées déjà compatibles avec le spike M0 afin que le reader n'invente ni titre de specification ni action de scénario.

## Preuves attendues

1. le provider synthétique est détecté via `SpecificationProvider` ;
2. le reader synthétique implémente `SpecificationContentReader` ;
3. il produit `Specification`, `Requirement`, `Scenario`, `ChangeProposal`, `Evidence` et `Provenance` MORPHEUS ;
4. OpenSpec et Synthetic peuvent être consommés par la même fonction sans branche provider-specific ;
5. les deux résultats exposent le même ensemble de `ReadCategory` demandé ;
6. une même external key produit des identités distinctes entre OpenSpec et Synthetic ;
7. `com.morpheus.domain..` et `com.morpheus.application..` restent indépendants de `com.morpheus.provider..` ;
8. aucun SDK/provider type ne fuit dans `NormalizedProjectContent` ou `ProviderReadResult` ;
9. `.\mvnw.cmd clean test` est vert.

## Hors périmètre

- publication du provider synthétique comme fonctionnalité utilisateur ;
- compatibilité ascendante de son schéma JSON ;
- écriture du format synthétique ;
- enrichissement fonctionnel au niveau OpenSpec ;
- temporalité M3 ;
- traçabilité M4.

## Critère d'acceptation

ADR-0029 passe à **Acceptée — M2** lorsque le gate complet démontre les preuves ci-dessus et qu'aucune modification du domaine n'est nécessaire pour accueillir le second provider.

## Preuve d'acceptation — 22 juillet 2026

Gate exécuté sous Windows :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats observés :

```text
SyntheticSpecificationContentReaderTest   4/4 PASS
SyntheticSpecificationProviderTest        3/3 PASS
ProviderAntiLockInTest                     3/3 PASS

Domain module                              4 tests
Application module                        38 tests
OpenSpec provider                         26 tests
Synthetic provider                         7 tests
SQLite store                               6 tests
Architecture tests                        13 tests

TOTAL                                     94/94 PASS
Failures                                      0
Errors                                        0
Skipped                                       0
BUILD SUCCESS
```

La preuve S7 confirme qu'un second format compilé traverse exactement les mêmes ports `SpecificationProvider` et `SpecificationContentReader`, produit les mêmes concepts MORPHEUS et peut être consommé sans branche provider-specific.

Une même external key est résolue dans deux namespaces provider distincts et produit donc deux `DomainIdentity` distinctes. Les règles ArchUnit restent génériques sur `com.morpheus.provider..` : aucune dépendance du domaine ou de l'application vers OpenSpec ou Synthetic n'est introduite.

L'accueil du second provider n'a nécessité aucune modification de `morpheus-domain` ni de `morpheus-application`.

Les warnings JDK 24 `--enable-native-access=ALL-UNNAMED` du driver SQLite et SLF4J NOP dans les tests d'architecture restent non bloquants.
