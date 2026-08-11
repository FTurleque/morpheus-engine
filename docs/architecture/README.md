# Documentation d'architecture — MORPHEUS ENGINE

> Documentation produite selon le cadre **arc42** (v8), le modèle **C4** et les
> conventions **ADR Markdown**. Tous les diagrammes sont en **Mermaid** avec
> stéréotypes UML explicites.
>
> Version du système documenté : **1.2.0** (Java 21, Maven multi-module)
> Gate qualifié : **M27**
> Date de production : 2026-08-06

---

## Structure

```
docs/architecture/
├── README.md                        ← ce fichier — index et navigation
├── arc42/
│   ├── 01-introduction-objectifs.md
│   ├── 02-contraintes.md
│   ├── 03-contexte-perimetre.md
│   ├── 04-strategie-solution.md
│   ├── 05-vue-blocs.md
│   ├── 06-vue-execution.md
│   ├── 07-vue-deploiement.md
│   ├── 08-concepts-transverses.md
│   ├── 09-decisions.md
│   ├── 10-exigences-qualite.md
│   ├── 11-risques-dette.md
│   └── 12-glossaire.md
├── adr/
│   ├── README.md                    ← index et mode d'emploi des nouveaux ADR
│   └── template.md                  ← gabarit ADR
├── diagrams/                        ← sources Mermaid exportables (vide — diagrammes inline dans arc42)
├── quality/
│   └── scenarios.md                 ← scénarios d'utilité architecturale (arc42 §10)
└── risks/
    └── register.md                  ← registre des risques et de la dette (arc42 §11)
```

---

## Navigation rapide

| Section | Contenu | Diagrammes |
|---------|---------|-----------|
| [§1 Introduction](arc42/01-introduction-objectifs.md) | Objectifs, parties prenantes | — |
| [§2 Contraintes](arc42/02-contraintes.md) | Métier, techniques, organisationnelles | — |
| [§3 Contexte](arc42/03-contexte-perimetre.md) | Frontière système, acteurs | C4 Context |
| [§4 Stratégie](arc42/04-strategie-solution.md) | Principes, technologies | — |
| [§5 Vue blocs](arc42/05-vue-blocs.md) | Modules, couches, interfaces | C4 Container, C4 Component |
| [§6 Vue exécution](arc42/06-vue-execution.md) | Scénarios runtime | Sequence diagrams |
| [§7 Vue déploiement](arc42/07-vue-deploiement.md) | Nœuds, artefacts, distribution | Deployment diagram |
| [§8 Concepts transverses](arc42/08-concepts-transverses.md) | Sécurité, données, observabilité | — |
| [§9 Décisions](arc42/09-decisions.md) | Index des 96 ADR | — |
| [§10 Qualité](arc42/10-exigences-qualite.md) | Arbre de qualité, scénarios | — |
| [§11 Risques et dette](arc42/11-risques-dette.md) | Registre priorisé | — |
| [§12 Glossaire](arc42/12-glossaire.md) | Termes, acronymes | — |

### ADR internes à cette documentation

> Les 96 ADR du projet se trouvent dans [`../../adr/`](../../adr/).
> Le répertoire [`adr/`](adr/) ci-dessous contient uniquement le gabarit
> et l'index de navigation de la présente documentation d'architecture.

---

## Sources d'observation

| Source | Chemin | Rôle |
|--------|--------|------|
| Manifests Maven | `pom.xml`, `morpheus-*/pom.xml` | Stack, versions, modules |
| Documentation développeur | `docs/developer/` | Architecture logique, API, MCP |
| ADR existants | `docs/adr/` (ADR-0001 à ADR-0096) | Décisions architecturales |
| Migrations SQLite | `morpheus-store-sqlite/src/main/resources/db/migration/` | Schéma de données |
| OpenAPI | `docs/openapi/morpheus-v1.yaml` et variations | Contrat HTTP |
| Scripts de distribution | `distribution/` | Packaging et release |
| Pipelines CI | `.github/workflows/` | Gates automatisés |
| Contrats de surfaces | `contracts/public-surfaces.tsv` | Surfaces publiques |
| Tests d'architecture | `morpheus-architecture-tests/` | Règles ArchUnit |
