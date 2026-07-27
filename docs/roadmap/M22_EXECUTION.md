# M22 — Provider SDK & Plugin Discovery Platform

Statut : **S0→S8 IMPLÉMENTÉS — S9 qualification locale exact-head à exécuter — ADR-0090 proposée — PR #101 temporairement fermée pour respecter le gel CI avant août**

Issue : #100
PR : #101 — temporairement fermée, branche active
Branche : `m22/provider-sdk-plugin-platform`

Baseline : `main@b26833701b028ea3d09388ed87188fb1945b559d` après merge M21 `2fdce6601a07628c315fe03932750cd8ece3d777`.

## Question de sortie

> Peut-on ajouter un provider MORPHEUS réel sans modifier le core ni introduire de dépendance provider-specific dans domain/application ?

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
- [x] script Linux réellement exécutable dans Git ;
- [x] gate vérifie SDK packagé et provider de référence absent du launcher ;
- [x] gate exécute discovery + activation + probe du JAR externe ;
- [ ] Windows exact-head PASS ;
- [ ] Linux exact-head PASS ;
- [ ] `VALIDATION_M22.md` convertie en preuve finale ;
- [ ] ADR-0090 acceptée après preuve ;
- [ ] consolidation docs-only OpenAPI/index/roadmap après gel du SHA exécutable ;
- [ ] réouverture PR #101 après fin du gel CI ;
- [ ] merge uniquement après éligibilité et autorisation explicite.

## Qualification avant août

Conformément au gel CI demandé, **GitHub Actions n’est pas un gate M22 avant août**. Le workflow du dépôt reste identique à `main` et la PR #101 est temporairement fermée afin d’éviter de nouveaux déclenchements automatiques `pull_request` pendant la qualification.

Les seules preuves M22 autorisées maintenant sont les gates locaux exact-head :

```powershell
.\validate-m22.cmd -Version 1.0.0
```

```bash
./scripts/validate-m22.sh 1.0.0
```

La PR pourra être rouverte après la fin du gel CI, sans modifier le SHA exécutable qualifié autrement que par une consolidation documentaire explicitement séparée.
