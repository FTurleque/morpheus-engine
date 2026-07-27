# M22 — Provider SDK & Plugin Discovery Platform

Statut : **EN COURS — S0 cadré, ADR-0090 proposée**

Issue : #100  
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
metadata entry               META-INF/morpheus-provider.properties
discovery                    zero plugin classloading
activation                   explicit only
classloader                  one URLClassLoader per activated JAR
ServiceLoader implementations exactly 1
provider id                  manifest == plugin metadata == provider.id
full reactor                 >= M21 14 modules + M22 modules
baseline tests               >= 473 PASS
architecture                 >= 187 PASS
JaCoCo line                  >= 25%
JaCoCo branch                >= 20%
Windows + Linux              exact-head qualification
post-gate executable delta   NONE
```

## Slices

### M22-S0 — cadrage / ADR

- [x] issue #100 ;
- [x] roadmap active M22 ;
- [x] ADR-0090 proposée avant code ;
- [x] invariants et budgets gelés.

### M22-S1 — Provider SDK

- [ ] module `morpheus-provider-sdk` ;
- [ ] `MorpheusProviderPlugin` ;
- [ ] métadonnées immuables ;
- [ ] version SDK stable `1` ;
- [ ] aucune dépendance inverse depuis domain/application.

### M22-S2 — metadata / compatibility

- [ ] parser properties borné ;
- [ ] validation identifiants/versions ;
- [ ] intervalle de version MORPHEUS ;
- [ ] statuts compatibles/incompatibles explicites.

### M22-S3 — discovery

- [ ] scan explicite non récursif ;
- [ ] ordre déterministe ;
- [ ] zéro classloading pendant discovery ;
- [ ] plugin absent = liste vide, pas erreur projet ;
- [ ] erreurs par JAR isolées en diagnostics.

### M22-S4 — activation / capability negotiation

- [ ] activation explicite ;
- [ ] URLClassLoader dédié par JAR ;
- [ ] ServiceLoader exactement un plugin ;
- [ ] contrôle métadonnées/provider id ;
- [ ] probe + capabilities issus du provider réel.

### M22-S5 — isolation

- [ ] isolation classloader appliquée ;
- [ ] fermeture explicite ;
- [ ] aucune prétention de sandbox sécurité ;
- [ ] process isolation différée et documentée.

### M22-S6 — reference provider template

- [ ] module externe `morpheus-provider-reference` ;
- [ ] manifeste plugin ;
- [ ] service descriptor ;
- [ ] fixture minimale supportée ;
- [ ] non embarqué comme provider built-in du launcher.

### M22-S7 — contract test kit

- [ ] module `morpheus-provider-testkit` ;
- [ ] assertions metadata/provider ;
- [ ] determinism probe ;
- [ ] capability sanity ;
- [ ] exemple utilisé par provider de référence.

### M22-S8 — surfaces publiques

- [ ] CLI discovery/probe ;
- [ ] MCP discovery/probe ;
- [ ] HTTP discovery/probe ;
- [ ] manifeste public M21 mis à jour ;
- [ ] aucune découverte automatique au startup.

### M22-S9 — packaging / qualification

- [ ] documentation SDK/auteur provider ;
- [ ] architecture tests anti-couplage ;
- [ ] intégration JAR de référence via vrai discovery/activation ;
- [ ] packaging contient SDK, pas provider référence intégré ;
- [ ] Windows exact-head PASS ;
- [ ] Linux exact-head PASS ;
- [ ] `VALIDATION_M22.md` ;
- [ ] ADR-0090 acceptée après preuve ;
- [ ] PR Ready puis merge après gates.
