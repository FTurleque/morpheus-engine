# D0 — Réconciliation documentaire post-M14

Statut : **🚧 EN COURS**

Dernière mise à jour : 26 juillet 2026

Issue : **#74**

## 1. Objectif

Aligner la documentation active sur la baseline réellement livrée avant M15, sans falsifier les preuves historiques C0→M14.

## 2. Baseline d’entrée

```text
C0 → M14       ✅ validés et intégrés
M14            357/357 PASS
Architecture   160/160 PASS
Packaging Win  PASS
JARVIS         536 tests BUILD SUCCESS
Post-M14       roadmap intégrée via PR #73
```

## 3. Décision documentaire D0

Trois classes sont distinguées :

```text
ACTIVE
  état courant maintenu

NORMATIVE DECISION
  ADR acceptée applicable

HISTORICAL EVIDENCE
  cadrage, recherche, plan d’exécution ou validation conservé pour audit
```

L’état d’intégration des jalons est défini par :

```text
docs/governance/ROADMAP.md
```

Les instructions de merge présentes dans des plans/validations historiques restent interprétées dans leur contexte temporel et ne redéfinissent jamais l’état courant.

## 4. Travaux D0

### D0-S1 — Politique et index

- [x] créer `docs/governance/DOCUMENTATION_STATUS.md` ;
- [x] créer `docs/roadmap/README.md` ;
- [x] créer ce plan D0 ;
- [ ] référencer ces documents depuis les portails actifs.

### D0-S2 — Cadrage C0

- [ ] remplacer le statut pré-implémentation du cahier des charges par une baseline C0 validée ;
- [ ] reclasser `docs/governance/PLAN.md` comme plan historique C0/M0 ;
- [ ] conserver le contenu de cadrage sans le présenter comme une roadmap courante.

### D0-S3 — Historique des jalons

- [ ] documenter explicitement que `M*_EXECUTION.md` et `VALIDATION_M*.md` sont des preuves historiques ;
- [ ] ne pas modifier les SHA, compteurs de tests ou décisions de gate ;
- [ ] faire prévaloir la roadmap globale lorsqu’un texte historique parle encore d’une fusion future.

### D0-S4 — Liens et parcours principaux

Vérifier l’existence des cibles utilisées par :

```text
README.md
docs/README.md
docs/governance/README.md
docs/user/README.md
docs/developer/README.md
```

Portails critiques :

```text
docs/user/QUICKSTART.md
docs/user/CLI.md
docs/user/INTEGRATIONS.md
docs/developer/ARCHITECTURE.md
docs/developer/BUILD_AND_TEST.md
docs/developer/API.md
docs/developer/MCP.md
docs/developer/INTEGRATIONS.md
docs/reference/README.md
docs/openapi/morpheus-v1.yaml
distribution/README.md
```

### D0-S5 — Clôture

- [ ] mettre à jour `docs/governance/ROADMAP.md` ;
- [ ] mettre à jour `POST_M14_EXECUTION.md` ;
- [ ] créer `docs/validation/VALIDATION_D0.md` ;
- [ ] fermer l’issue #74 après merge ;
- [ ] autoriser M15.

## 5. Gate D0

```text
aucun document actif ne présente M3..M14 comme non intégrés
cahier des charges aligné avec la baseline livrée
roadmap post-M14 référencée depuis la gouvernance
aucun lien documentaire cassé sur les parcours principaux
preuves historiques de gates conservées sans réécriture
```

## 6. Suite

Après validation et intégration de D0 :

```text
M15 — Acceptance Criteria, Verification & Evidence
```
