# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — politique documentaire post-M14**

Dernière mise à jour : 26 juillet 2026

Ce document définit comment interpréter la documentation MORPHEUS après l’intégration de C0 à M14. Il évite de confondre l’état courant du produit avec les traces d’exécution et de décision conservées pour l’audit.

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
plan du jalon post-M14 en cours
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

D0 ne réécrit pas rétroactivement les résultats des gates M0→M14.

On peut corriger :

- un lien cassé ;
- un statut documentaire ambigu ;
- un index ;
- une note de contextualisation.

On ne doit pas altérer :

- le SHA effectivement testé ;
- le nombre de tests ;
- la date du gate ;
- la décision qui était en attente au moment du gate ;
- la preuve technique enregistrée.

## 6. Baseline actuelle

```text
C0 → M14       ✅ validés et intégrés
M14            ✅ 357/357 PASS
Architecture   ✅ 160/160 PASS
Packaging Win  ✅ PASS
JARVIS         ✅ 536 tests BUILD SUCCESS
```

La trajectoire suivante est définie par `POST_M14_EXECUTION.md` :

```text
D0   Documentation reconciliation
M15  Acceptance / Verification / Evidence
M16  Constraint semantics / blocking policy
M17  Controlled write / lifecycle mutations
M18  Real providers / multi-provider composition
M19  Production hardening / scale / operability
M20  Release engineering / installation PROD / MORPHEUS 1.0
```
