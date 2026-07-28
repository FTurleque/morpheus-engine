# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — politique documentaire MORPHEUS 1.x post-M23**

Dernière mise à jour : 28 juillet 2026

Ce document définit comment interpréter la documentation MORPHEUS après l’intégration de C0 à M23, D0/D1 et la publication R1. Il évite de confondre l’état courant du produit, les évolutions 1.x et les traces historiques d’exécution conservées pour l’audit.

## 1. Hiérarchie d’autorité

Pour connaître l’état courant du projet :

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M20_EVOLUTION.md
        ↓
plan d’exécution du jalon actuellement actif
```

M24 est le prochain jalon actif après l’intégration de M23.

Pour les contrats effectivement exposés :

```text
code + tests de contrat
        ↓
contracts/public-surfaces.tsv
docs/reference/
docs/openapi/
docs/developer/
docs/user/
```

Pour une décision d’architecture :

```text
ADR acceptée
```

Une ADR acceptée reste normative pour la décision qu’elle porte tant qu’elle n’est pas remplacée ou amendée explicitement.

## 2. Documentation active

Les documents suivants décrivent l’état actuellement maintenu :

```text
README.md
docs/README.md
docs/user/
docs/developer/
docs/reference/
docs/openapi/
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/POST_M20_EVOLUTION.md
plan du jalon actif (M24 lorsqu'il est ouvert)
```

Un document actif ne doit pas présenter un jalon déjà intégré comme encore en attente de merge.

Pour M23, les points d’entrée spécifiques sont :

```text
docs/roadmap/M23_EXECUTION.md
docs/validation/VALIDATION_M23.md
docs/adr/0091-multi-project-portfolio-intelligence.md
docs/user/PORTFOLIOS.md
docs/developer/PORTFOLIO_INTELLIGENCE.md
docs/openapi/morpheus-v1-portfolio-m23.yaml
```

## 3. Baselines et preuves historiques

Les familles suivantes sont conservées comme preuves de conception ou d’exécution :

```text
docs/roadmap/M*_EXECUTION.md       plans d’exécution des jalons livrés
docs/validation/                   preuves de gates
docs/research/                     études et expérimentations C0/M0
docs/product/                      cadrage produit et MVP historique
docs/domain/                       modèle de cadrage et décisions historiques
docs/contracts/                    contrats conceptuels de cadrage
docs/architecture/                 architecture de cadrage historique
docs/governance/PLAN.md            plan C0/M0 historique
```

Les plans d’exécution peuvent recevoir une **note ou section d’intégration finale** (PR mergée, merge commit, jalon suivant) tant que les faits historiques du gate ne sont pas altérés.

Les formulations telles que :

```text
PR à merger
Ready for review
merge soumis à autorisation
M(n+1) autorisé après merge
```

peuvent apparaître dans une preuve historique parce qu’elles décrivent le gate exact au moment où le document a été produit. Elles ne constituent pas l’état courant du dépôt.

L’état courant d’intégration est toujours celui de `docs/governance/ROADMAP.md`.

## 4. Cahier des charges C0 et version produit

`docs/product/CAHIER_DES_CHARGES.md` constitue la **baseline fonctionnelle et technique de haut niveau validée en C0**.

Il reste utile pour :

- la vision produit ;
- les frontières avec MINOS, NEXUS et JARVIS ;
- les principes local-first et provider-agnostic ;
- les invariants fondateurs ;
- la compréhension de l’intention initiale.

Il ne remplace pas les ADR, contrats machine, roadmaps et validations plus récents lorsqu’ils ont explicitement raffiné une décision après C0.

La version officiellement publiée reste `v1.0.0`. Les jalons M21→M23 sont des évolutions 1.x validées et intégrées sur cette baseline produit ; leur intégration ne réécrit pas rétroactivement la preuve R1.

## 5. Règle de non-réécriture des preuves

Les réconciliations documentaires ne réécrivent pas rétroactivement les résultats des gates.

On peut corriger :

- un lien cassé ;
- un statut documentaire ambigu ;
- un index ;
- une note de contextualisation ;
- l’état d’intégration final d’un plan de jalon.

On ne doit pas altérer :

- le SHA effectivement testé ;
- le nombre de tests ;
- la date du gate ;
- la décision qui était en attente au moment du gate ;
- la preuve technique enregistrée.

Pour M23 :

```text
code réellement testé Windows + Linux = 04a906e9d5858292ed0f0f1bec65246fef91ed63
PR head documentaire mergé            = 4f6bd7b4c66694fa7afc39a776a1e3622b73bd99
merge M23                              = 88355b69c493677c8689eecad214fb00d283359b
```

La preuve `VALIDATION_M23.md` conserve le SHA exécutable qualifié ; les mises à jour post-gate et post-merge sont documentaires uniquement.

## 6. Baseline actuelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21            ✅ validé et intégré
M22            ✅ validé et intégré
M23            ✅ validé et intégré
M23 tests      ✅ 507 PASS Windows + Linux
Architecture   ✅ 195 PASS Windows + Linux
M23 executable ✅ 04a906e9d5858292ed0f0f1bec65246fef91ed63
M23 merge      ✅ 88355b69c493677c8689eecad214fb00d283359b
ADR-0091       ✅ Acceptée — M23
```

La trajectoire active définie par `POST_M20_EVOLUTION.md` est :

```text
M21  Production Integrity & Surface Convergence          ✅ intégré
M22  Provider SDK & Plugin Discovery Platform            ✅ intégré
M23  Multi-project / Portfolio Specification Intelligence ✅ intégré
M24  Query DSL, Saved Views & Export/Reporting           ⏭ prochain jalon actif
M25  Policy Packs & Governance Automation                ⏳
M26  Optional Team/Remote Server Mode                     ⏳
M27  Evidence-backed Assisted Reasoning                   ⏳
```

M24 devient le prochain jalon actif après l’intégration M23.
