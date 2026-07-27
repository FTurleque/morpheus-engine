# M21 — Production Integrity & Surface Convergence

Statut : **EN COURS** — issue #98 — branche `m21/production-integrity-surface-convergence`

Baseline : `main@83ad1dfc264a4797130ebd61353ce0e78552d88c` — MORPHEUS 1.0.0 publié.

## Question de sortie

> MORPHEUS 1.x possède-t-il une baseline de production durable où build, qualité, contrats publics, documentation et chaîne de release convergent sans divergence silencieuse entre CLI, MCP et HTTP ?

## Invariants

```text
surface parity != same transport shape
read surface != write capability
release metadata != runtime business state
update discovery != automatic update
security metadata != hidden network dependency
local-first remains default
no mandatory LLM in core
facts != inference
```

## Budgets / gates gelés avant implémentation

```text
Java                         21+
Maven                        3.9.16+
OS qualification             Windows + Linux
full reactor                 14/14 modules SUCCESS minimum
baseline tests               >= 454 PASS
architecture                 >= 182 PASS
JaCoCo line aggregate        >= 25%
JaCoCo branch aggregate      >= 20%
public surface manifest      100% entries verified
product version convergence  CLI = MCP = HTTP
update discovery             explicit invocation only
update auto-apply            forbidden
SBOM                         CycloneDX JSON + XML
release integrity            SHA-256 + provenance manifest
post-gate executable delta   NONE
```

Les seuils JaCoCo M21 sont des **floors de non-régression instrumentale**, pas des objectifs de couverture finaux. Ils doivent être relevés dans les jalons ultérieurs à partir de tendances réelles.

# NOW

## M21-S0 — cadrage / ADR

- [x] issue canonique #98 ;
- [x] plan M21 ;
- [x] ADR-0089 proposée avant changements structurants ;
- [x] budgets gelés.

## M21-S1 — CI durable

- [ ] workflow générique `ci.yml`, sans nom de milestone ;
- [ ] matrice Windows/Linux + JDK 21 ;
- [ ] Maven Wrapper ;
- [ ] reactor complet `verify` ;
- [ ] artefacts couverture/SBOM/provenance publiés.

## M21-S2 — couverture / quality gates

- [ ] instrumentation JaCoCo sur le reactor ;
- [ ] rapports XML/HTML par module ;
- [ ] gate aggregate line >= 25% ;
- [ ] gate aggregate branch >= 20% ;
- [ ] résumé machine lisible pour tendance.

## M21-S3 — Maven / reproductibilité

- [ ] versions plugins centralisées ;
- [ ] `project.build.outputTimestamp` stable ;
- [ ] manifestes JAR avec version produit ;
- [ ] analyse de dépendances non destructive ;
- [ ] aucun warning nouveau transformé silencieusement en dette invisible.

# NEXT

## M21-S4 — convergence CLI / MCP / HTTP

- [ ] manifeste machine `contracts/public-surfaces.tsv` ;
- [ ] capability intent explicite READ/WRITE ;
- [ ] asymétries de transport explicites ;
- [ ] version produit convergente ;
- [ ] tests empêchant une divergence silencieuse.

## M21-S5 — documentation single-source-of-truth

- [ ] `docs/reference/PUBLIC_SURFACES.md` pointe sur le manifeste ;
- [ ] `docs/developer/PRODUCTION_INTEGRITY.md` documente les gates ;
- [ ] test de cohérence documentation/version/surfaces ;
- [ ] absence de duplication normative non contrôlée.

## M21-S6 — supply chain

- [ ] CycloneDX SBOM ;
- [ ] provenance de build explicite ;
- [ ] SHA-256 des artefacts de release ;
- [ ] politique de confiance documentée ;
- [ ] signature cryptographique séparée des checksums et non simulée en l’absence de clé.

## M21-S7 — update channel / version discovery

- [ ] métadonnées produit centralisées ;
- [ ] manifest update explicite ;
- [ ] source `file:`, `http:` ou `https:` uniquement sur invocation ;
- [ ] aucune requête réseau au démarrage ;
- [ ] aucune installation/mutation automatique ;
- [ ] surfaces CLI/MCP/HTTP alignées sur le même service read-only.

# LATER — gate final

## M21-S8 — qualification exact-head

- [ ] `git diff --check` ;
- [ ] Windows exact-head PASS ;
- [ ] Linux exact-head PASS ;
- [ ] reactor complet PASS ;
- [ ] tests >= baseline ;
- [ ] architecture >= baseline ;
- [ ] coverage gate PASS ;
- [ ] public surfaces gate PASS ;
- [ ] SBOM/provenance PASS ;
- [ ] packaging/smokes pertinents PASS ;
- [ ] `VALIDATION_M21.md` finalisée avec SHA réel ;
- [ ] ADR-0089 acceptée seulement après preuve ;
- [ ] PR Ready seulement après gate vert ;
- [ ] merge uniquement après autorisation explicite du propriétaire.

## Fichiers attendus

```text
.github/workflows/ci.yml
contracts/public-surfaces.tsv
docs/adr/0089-production-integrity-surface-convergence.md
docs/developer/PRODUCTION_INTEGRITY.md
docs/reference/PUBLIC_SURFACES.md
docs/roadmap/M21_EXECUTION.md
docs/validation/VALIDATION_M21.md
```
