# Gouvernance et preuves

Les documents de ce groupe servent à piloter et auditer le projet. Ils ne sont pas le parcours principal pour utiliser ou développer MORPHEUS au quotidien.

## Pilotage

- [`ROADMAP.md`](ROADMAP.md) — état global courant, baseline C0→M18 et jalons post-M18 ;
- [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md) — autorité des documents actifs, ADR et preuves historiques ;
- [`../roadmap/README.md`](../roadmap/README.md) — index des plans d’exécution ;
- [`../roadmap/POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — roadmap détaillée D0 + M15→M20 ;
- [`../roadmap/M18_EXECUTION.md`](../roadmap/M18_EXECUTION.md) — dernier jalon intégré ;
- [`PLAN.md`](PLAN.md) — plan de cadrage C0/M0 historique ;
- [`AUDIT_COHERENCE_C0.md`](AUDIT_COHERENCE_C0.md) — audit de cohérence C0.

La roadmap opérationnelle d’un jalon reste la source de vérité pendant son exécution. Pour l’état d’intégration courant des jalons déjà livrés, `ROADMAP.md` prévaut sur une instruction de merge conservée dans une preuve historique.

Baseline livrée : **C0 à M18 validés et intégrés**. **M19 — Production Hardening, Scale & Operability — est le prochain jalon.**

## Architecture Decision Records

- [`../adr/README.md`](../adr/README.md) — index des ADR ;
- `ADR-0077` à `ADR-0080` couvrent le contrat d’orchestration M14 ;
- `ADR-0081` couvre acceptance/verification M15 ;
- `ADR-0082` couvre la politique de contraintes M16 ;
- `ADR-0083` couvre les mutations lifecycle contrôlées M17 ;
- `ADR-0084` couvre la composition multi-provider provider-neutral, déterministe et explicable M18.

Une ADR dépendante d’une hypothèse technique ne doit être acceptée qu’après preuve reproductible. Une ADR acceptée reste normative pour la décision qu’elle porte jusqu’à remplacement ou amendement explicite.

## Validations

Les preuves de gates sont regroupées dans [`../validation/`](../validation/) :

```text
VALIDATION_C0.md
VALIDATION_M0.md
...
VALIDATION_M18.md
```

Elles enregistrent notamment :

- le SHA réellement testé ;
- les commandes exécutées ;
- le nombre exact de tests ;
- les preuves de packaging ;
- les décisions de sortie du jalon.

Ces fichiers sont des **preuves historiques**. Ils ne sont pas réécrits après merge pour modifier artificiellement le SHA ou le gate exécuté ; les roadmaps et index actifs enregistrent l’état d’intégration courant. Une note post-merge peut être ajoutée explicitement sans prétendre que le merge existait au moment du gate.

Dernière preuve intégrée : M18, **418/418 PASS**, Architecture **170/170 PASS**, packaging Windows + smokes PASS, code validé `7e8caacff567f51354fcb88bd7505a6d135071c0`, merge `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

## Documentation active

Pour le contenu maintenu comme documentation courante :

- [utilisateur](../user/README.md) ;
- [développeur](../developer/README.md) ;
- [références machine](../reference/README.md) ;
- [roadmap courante](ROADMAP.md) ;
- [politique documentaire](DOCUMENTATION_STATUS.md).

Le dossier [`../product/`](../product/) conserve la baseline produit et les documents de cadrage C0 ; leur statut exact est expliqué par `DOCUMENTATION_STATUS.md`.