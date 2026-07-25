# D0 — Réconciliation documentaire post-M14

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #75 à intégrer avant M15**

Dernière mise à jour : 26 juillet 2026

Issue : **#74**  
PR : **#75**  
Validation : [`../validation/VALIDATION_D0.md`](../validation/VALIDATION_D0.md)

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

### D0-S1 — Politique et index ✅

- [x] créer `docs/governance/DOCUMENTATION_STATUS.md` ;
- [x] créer `docs/roadmap/README.md` ;
- [x] créer ce plan D0 ;
- [x] référencer ces documents depuis les portails actifs.

### D0-S2 — Cadrage C0 ✅

- [x] remplacer le statut pré-implémentation du cahier des charges par une baseline C0 validée ;
- [x] reclasser `docs/governance/PLAN.md` comme plan historique C0/M0 ;
- [x] conserver le contenu de cadrage sans le présenter comme une roadmap courante ;
- [x] réparer les liens relatifs du cahier déplacés lors du rangement de `docs/`.

### D0-S3 — Historique des jalons ✅

- [x] documenter explicitement que `M*_EXECUTION.md` et `VALIDATION_M*.md` sont des preuves historiques ;
- [x] ne pas modifier les SHA, compteurs de tests ou décisions de gate ;
- [x] faire prévaloir la roadmap globale lorsqu’un texte historique parle encore d’une fusion future.

### D0-S4 — Liens et parcours principaux ✅

Cibles vérifiées :

```text
README.md                              PASS
docs/README.md                         PASS
docs/governance/README.md              PASS
docs/user/README.md                    PASS
docs/user/QUICKSTART.md                PASS
docs/user/CLI.md                       PASS
docs/user/INTEGRATIONS.md              PASS
docs/developer/README.md               PASS
docs/developer/ARCHITECTURE.md         PASS
docs/developer/BUILD_AND_TEST.md       PASS
docs/developer/API.md                  PASS
docs/developer/MCP.md                  PASS
docs/developer/INTEGRATIONS.md         PASS
docs/reference/README.md               PASS
docs/openapi/morpheus-v1.yaml          PASS
distribution/README.md                 PASS
```

Cibles réparées du cahier :

```text
docs/domain/MODEL.md                              PASS
docs/domain/CHANGE_LIFECYCLE.md                   PASS
docs/contracts/SPECIFICATION_PROVIDER.md          PASS
docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md   PASS
docs/architecture/overview.md                     PASS
docs/research/openspec-provider-study.md           PASS
docs/research/M0_EXPERIMENT_MATRIX.md             PASS
```

### D0-S5 — Clôture ✅ gate / ⏳ intégration

- [x] mettre à jour `docs/governance/ROADMAP.md` ;
- [x] mettre à jour `POST_M14_EXECUTION.md` ;
- [x] créer `docs/validation/VALIDATION_D0.md` ;
- [x] préparer M15 comme prochain jalon autorisé après merge ;
- [ ] merger la PR #75 après autorisation explicite ;
- [ ] fermer l’issue #74 après merge.

## 5. Gate D0

```text
aucun document actif ne présente M3..M14 comme non intégrés          PASS
cahier des charges aligné avec la baseline livrée                    PASS
roadmap post-M14 référencée depuis la gouvernance                    PASS
aucun lien documentaire cassé sur les parcours principaux           PASS
preuves historiques de gates conservées sans réécriture              PASS
```

**Réponse D0 : OUI.**

## 6. Suite

Après intégration de la PR #75 :

```text
M15 — Acceptance Criteria, Verification & Evidence
```
