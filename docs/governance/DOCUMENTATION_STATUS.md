# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — politique documentaire post-M18**

Dernière mise à jour : 26 juillet 2026

Ce document définit comment interpréter la documentation MORPHEUS après l’intégration de C0 à M18. Il évite de confondre l’état courant du produit avec les traces d’exécution et de décision conservées pour l’audit.

## 1. Hiérarchie d’autorité

Pour connaître l’état courant du projet :

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/POST_M14_EXECUTION.md
        ↓
plan d’exécution du jalon actuellement actif
```

Pour les contrats effectivement exposés :

```text
code + tests de contrat
        ↓
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
docs/roadmap/POST_M14_EXECUTION.md
plan du jalon actif (M19 lorsqu'il est ouvert)
```

Un document actif ne doit pas présenter un jalon déjà intégré comme encore en attente de merge.

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

## 4. Cahier des charges C0

`docs/product/CAHIER_DES_CHARGES.md` constitue la **baseline fonctionnelle et technique de haut niveau validée en C0**.

Il reste utile pour :

- la vision produit ;
- les frontières avec MINOS, NEXUS et JARVIS ;
- les principes local-first et provider-agnostic ;
- les invariants fondateurs ;
- la compréhension de l’intention initiale.

Il ne remplace pas les ADR, contrats machine, roadmaps et validations plus récents lorsqu’ils ont explicitement raffiné une décision après C0.

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

Une preuve historique peut recevoir une section **Post-merge** distincte qui indique le merge réellement survenu après le gate. Cette section ne remplace jamais le statut ni la conclusion enregistrés au moment de l’exécution.

## 6. Baseline actuelle

```text
C0 → M18       ✅ validés et intégrés
M15            ✅ 371/371 PASS | Architecture 157/157
M16            ✅ 393/393 PASS | Architecture 161/161
M17            ✅ 410/410 PASS | Architecture 167/167
M18            ✅ 418/418 PASS | Architecture 170/170
Packaging Win  ✅ PASS au gate M18
M18 code       ✅ 7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge      ✅ 30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
```

La trajectoire active définie par `POST_M14_EXECUTION.md` est :

```text
D0   Documentation reconciliation                 ✅ intégré
M15  Acceptance / Verification / Evidence         ✅ intégré
M16  Constraint semantics / blocking policy       ✅ intégré
M17  Controlled write / lifecycle mutations       ✅ intégré
M18  Real providers / multi-provider composition  ✅ intégré — PR #86
M19  Production hardening / scale / operability   ⏭ prochain
M20  Release engineering / installation PROD / MORPHEUS 1.0 ⏳
```
