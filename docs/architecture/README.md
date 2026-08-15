# Documentation d'architecture — MORPHEUS ENGINE

> Documentation structurée selon **arc42**, complétée par des vues **C4**, des
> diagrammes Mermaid et des références vers les ADR du projet.
>
> Baseline produit documentée : **MORPHEUS 1.2.0** — Java 21 — Maven multi-module.
> État de référence : `develop` post-R3 / post-D2, avec hardening MRA poursuivi séparément.
> Dernière réconciliation : **2026-08-11**.

---

## Structure

```text
docs/architecture/
├── README.md
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
│   ├── README.md
│   └── template.md
├── quality/
│   └── scenarios.md
└── risks/
    └── register.md
```

---

## Navigation rapide

| Section | Contenu | Diagrammes |
|---------|---------|-----------|
| [§1 Introduction](arc42/01-introduction-objectifs.md) | Objectifs, parties prenantes | — |
| [§2 Contraintes](arc42/02-contraintes.md) | Contraintes métier, techniques et organisationnelles | — |
| [§3 Contexte](arc42/03-contexte-perimetre.md) | Frontière système, acteurs et systèmes externes | C4 Context |
| [§4 Stratégie](arc42/04-strategie-solution.md) | Principes et technologies structurantes | — |
| [§5 Vue blocs](arc42/05-vue-blocs.md) | Modules, couches et interfaces | C4 Container / Component, UML |
| [§6 Vue exécution](arc42/06-vue-execution.md) | Scénarios runtime | Séquences |
| [§7 Vue déploiement](arc42/07-vue-deploiement.md) | Nœuds, artefacts et distribution | Déploiement |
| [§8 Concepts transverses](arc42/08-concepts-transverses.md) | Sécurité, données, observabilité | — |
| [§9 Décisions](arc42/09-decisions.md) | Décisions structurantes et registre ADR | — |
| [§10 Qualité](arc42/10-exigences-qualite.md) | Arbre de qualité et scénarios | — |
| [§11 Risques et dette](arc42/11-risques-dette.md) | Risques et dette technique | — |
| [§12 Glossaire](arc42/12-glossaire.md) | Termes et acronymes | — |

### Registre ADR principal

Les décisions architecturales du produit sont conservées dans [`../adr/`](../adr/).
Le sous-répertoire [`adr/`](adr/) de cette documentation ne remplace pas ce
registre : il contient uniquement un index documentaire et un gabarit.

---

## Sources de vérité utilisées

| Source | Chemin | Rôle |
|--------|--------|------|
| Build et versions | `pom.xml`, `morpheus-*/pom.xml` | Modules, Java et versions de dépendances |
| ADR produit | `docs/adr/` | Décisions architecturales acceptées |
| Roadmap / validations | `docs/governance/`, `docs/roadmap/`, `docs/validation/` | État des milestones et preuves |
| Contrats HTTP | `docs/openapi/` | Surface HTTP `/api/v1` |
| Contrats de surfaces | `contracts/public-surfaces.tsv` | Convergence CLI / MCP / HTTP |
| Migrations SQLite | `morpheus-store-sqlite/src/main/resources/db/migration/` | Schéma persistant |
| Tests d'architecture | `morpheus-architecture-tests/` | Invariants exécutables |
| Pipelines | `.github/workflows/` | Qualification publique exact-head |
| Distribution | `distribution/` | Packaging et installation |

`docs/architecture/overview.md` et certains documents historiques restent des
sources de conception antérieures. Lorsqu'ils divergent du code, des ADR ou des
preuves de validation plus récentes, ces dernières priment.
