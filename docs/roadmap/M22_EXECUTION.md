# M22 — Provider SDK & Plugin Discovery Platform

Statut : **TECHNIQUEMENT TERMINÉ / QUALIFIÉ — S0→S9 PASS — ADR-0090 acceptée — intégration différée pendant le gel CI avant août**

Issue : #100
PR : #101 — temporairement fermée, branche active
Branche : `m22/provider-sdk-plugin-platform`

Baseline : `main@b26833701b028ea3d09388ed87188fb1945b559d` après merge M21 `2fdce6601a07628c315fe03932750cd8ece3d777`.

Head exécutable qualifié Windows + Linux :

```text
e42bc31384831e56592b11a3509b49a3fdf61773
```

## Question de sortie

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

**Oui.** La preuve M22 couvre découverte sans exécution, compatibilité, activation isolée, capability negotiation et lecture normalisée réelle d’un JAR provider externe.

## Invariants

```text
provider plugin != domain dependency
plugin discovery != plugin activation
optional provider absence != project failure
incompatible provider != silently loaded provider
provider metadata != executable trust
plugin failure != core crash
capability declaration != capability implementation proof
probe != read
classloader isolation != security sandbox
local-first remains default
```

## Budgets / gates

```text
Java                         21+
Maven                        3.9.16+
SDK API                      v1
plugin scan                  non-recursive, *.jar only
plugin count                 <= 256 per explicit discovery
plugin jar size              <= 64 MiB
metadata size                <= 16 KiB
metadata entry               META-INF/morpheus-provider.properties
discovery                    zero plugin classloading
activation                   explicit only
classloader                  one URLClassLoader per activated JAR
ServiceLoader implementations exactly 1
provider id                  manifest == plugin == provider == contentReader
duplicate plugin.id          explicit ambiguity; never first-match silently
normalized read              SpecificationContentReader / ProviderReadResult
baseline tests               >= 473 PASS
architecture                 >= 187 PASS
JaCoCo line                  >= 25%
JaCoCo branch                >= 20%
Windows + Linux              local exact-head qualification
post-gate executable delta   NONE
CI                           frozen until August; not M22 evidence
```

## Slices

### M22-S0 — cadrage / ADR

- [x] issue #100 ;
- [x] roadmap active M22 ;
- [x] ADR-0090 proposée avant code ;
- [x] invariants et budgets gelés.

### M22-S1 — Provider SDK

- [x] module `morpheus-provider-sdk` ;
- [x] `MorpheusProviderPlugin` ;
- [x] `SpecificationProvider` pour probe/capabilities ;
- [x] `SpecificationContentReader` pour lecture normalisée ;
- [x] métadonnées immuables ;
- [x] version SDK stable `1` ;
- [x] aucune dépendance inverse depuis domain/application, couverte par architecture test.

### M22-S2 — metadata / compatibility

- [x] parser properties borné ;
- [x] validation identifiants/versions ;
- [x] intervalle de version MORPHEUS min/max ;
- [x] ordre SemVer des prereleases, identifiants numériques inclus ;
- [x] statuts `COMPATIBLE` / `INCOMPATIBLE` / `INVALID` ;
- [x] mismatch SDK/version couvert par tests.

### M22-S3 — discovery

- [x] scan explicite non récursif ;
- [x] ordre déterministe ;
- [x] zéro classloading pendant discovery ;
- [x] plugin directory absent = liste vide + diagnostic, pas erreur projet ;
- [x] erreurs par JAR isolées en diagnostics ;
- [x] bornes 256 JAR / 64 MiB / metadata 16 KiB.

### M22-S4 — activation / capability negotiation / read

- [x] activation explicite ;
- [x] `URLClassLoader` dédié par JAR ;
- [x] `ServiceLoader` exactement un plugin ;
- [x] contrôle manifeste == metadata runtime == provider id == reader provider id ;
- [x] metadata mismatch rejeté par test ;
- [x] duplicate `plugin.id` rejeté explicitement (`PLUGIN_ID_AMBIGUOUS`) ;
- [x] probe + capabilities issus du provider réel ;
- [x] lecture normalisée via `SpecificationContentReader` distincte du probe ;
- [x] failure/linkage bornés en diagnostic `PLUGIN_ACTIVATION_OR_PROBE_FAILED`.

### M22-S5 — isolation

- [x] isolation classloader appliquée ;
- [x] fermeture explicite via `ProviderPluginActivation.close()` ;
- [x] aucune prétention de sandbox sécurité ;
- [x] process isolation différée et documentée dans ADR-0090.

### M22-S6 — reference provider template

- [x] module externe `morpheus-provider-reference` ;
- [x] manifeste plugin ;
- [x] service descriptor ;
- [x] fixture minimale `morpheus-reference.spec` ;
- [x] provider expose réellement `DISCOVER_PROJECT` + `READ_CURRENT_SPECIFICATIONS` ;
- [x] reader produit une `Specification`, une `Evidence` et une `Provenance` normalisées ;
- [x] non déclaré comme dépendance du launcher.

### M22-S7 — contract test kit

- [x] module `morpheus-provider-testkit` ;
- [x] assertions metadata/provider/reader ;
- [x] déterminisme du probe ;
- [x] cohérence ID/version/remote/capabilities ;
- [x] test kit consommé par le provider de référence ;
- [x] lecture normalisée du provider de référence réellement testée.

### M22-S8 — surfaces publiques

- [x] CLI `provider-plugins discover/probe` ;
- [x] MCP `discover_provider_plugins` / `probe_provider_plugin` ;
- [x] HTTP `/api/v1/provider-plugins/discover` / `probe` ;
- [x] `contracts/public-surfaces.tsv` mis à jour ;
- [x] tests CLI/MCP/HTTP ;
- [x] aucune découverte automatique au startup ;
- [x] discovery séparée de l’activation/probe ;
- [x] lecture SDK distincte d’un remplacement implicite du flux `sync` historique.

### M22-S9 — packaging / qualification

- [x] documentation auteur `docs/developer/PROVIDER_SDK.md` ;
- [x] documentation utilisateur `docs/user/PROVIDER_PLUGINS.md` ;
- [x] architecture tests anti-couplage + vrai JAR externe + normalized read ;
- [x] validateurs exact-head Windows/Linux créés ;
- [x] validateur Linux mode-neutral ;
- [x] gate vérifie SDK packagé et provider de référence absent du launcher ;
- [x] gate exécute discovery + activation + probe du JAR externe ;
- [x] Windows exact-head PASS sur `e42bc31384831e56592b11a3509b49a3fdf61773` ;
- [x] Linux exact-head PASS sur le même SHA ;
- [x] Windows : 494 tests / 190 architecture / 47.0508% line / 41.8839% branch ;
- [x] Linux : 494 tests / 190 architecture / 47.0389% line / 41.8839% branch ;
- [x] SBOM/provenance PASS Windows + Linux ;
- [x] portable Windows + Linux PASS ;
- [x] `postGateExecutableDelta=NONE` Windows + Linux ;
- [x] `VALIDATION_M22.md` convertie en preuve finale ;
- [x] ADR-0090 acceptée après preuve ;
- [x] consolidation documentaire finale engagée sur un head séparé du SHA exécutable ;
- [ ] réouverture PR #101 après fin du gel CI ;
- [ ] merge après réouverture, sans modification exécutable supplémentaire.

## Qualification finale

```text
Executable SHA        e42bc31384831e56592b11a3509b49a3fdf61773
Windows               PASS
Linux WSL2            PASS
Tests                 494 PASS
Architecture          190 PASS
SDK API               1
External provider     PASS
SBOM/provenance       PASS Windows + Linux
Portable              PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0090              Acceptée — M22
```

Preuve : [`../validation/VALIDATION_M22.md`](../validation/VALIDATION_M22.md).

## Gel CI avant août

GitHub Actions n’est pas utilisé comme preuve M22 avant août. Le workflow du dépôt reste identique à `main` et la PR #101 reste temporairement fermée afin d’éviter des déclenchements `pull_request` pendant le gel demandé.

La branche peut recevoir uniquement la consolidation documentaire finale. Toute modification ultérieure de code, POM, packaging, contrat runtime ou validateur invaliderait le SHA exécutable qualifié et imposerait une nouvelle qualification Windows + Linux.