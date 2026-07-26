# Gouvernance et preuves

Les documents de ce groupe servent à piloter et auditer le projet. Ils ne sont pas le parcours principal pour utiliser ou développer MORPHEUS au quotidien.

## Pilotage

- [`ROADMAP.md`](ROADMAP.md) — état global courant, baseline C0→M17 et jalons post-M17 ;
- [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md) — autorité des documents actifs, ADR et preuves historiques ;
- [`../roadmap/README.md`](../roadmap/README.md) — index des plans d’exécution ;
- [`../roadmap/POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — roadmap détaillée D0 + M15→M20 ;
- [`../roadmap/M17_EXECUTION.md`](../roadmap/M17_EXECUTION.md) — dernier jalon intégré ;
- [`PLAN.md`](PLAN.md) — plan de cadrage C0/M0 historique ;
- [`AUDIT_COHERENCE_C0.md`](AUDIT_COHERENCE_C0.md) — audit de cohérence C0.

La roadmap opérationnelle d’un jalon reste la source de vérité pendant son exécution. Pour l’état d’intégration courant des jalons déjà livrés, `ROADMAP.md` prévaut sur une instruction de merge conservée dans une preuve historique.

Baseline livrée : **C0 à M17 validés et intégrés**. **M18 — Real Providers & Multi-Provider Composition — est le prochain jalon.**

## Architecture Decision Records

- [`../adr/README.md`](../adr/README.md) — index des ADR ;
- `ADR-0077` à `ADR-0080` couvrent le contrat d’orchestration M14 ;
- `ADR-0081` couvre acceptance/verification M15 ;
- `ADR-0082` couvre la politique de contraintes M16 ;
- `ADR-0083` couvre les mutations lifecycle contrôlées M17.

Une ADR dépendante d’une hypothèse technique ne doit être acceptée qu’après preuve reproductible. Une ADR acceptée reste normative pour la décision qu’elle porte jusqu’à remplacement ou amendement explicite.

## Validations

Les preuves de gates sont regroupées dans [`../validation/`](../validation/) :

```text
VALIDATION_C0.md
VALIDATION_M0.md
...
VALIDATION_M17.md
```

Elles enregistrent notamment :

- le SHA réellement testé ;
- les commandes exécutées ;
- le nombre exact de tests ;
- les preuves de packaging ;
- les décisions de sortie du jalon.

Ces fichiers sont des **preuves historiques**. Ils ne sont pas réécrits après merge pour modifier artificiellement le SHA ou le gate exécuté ; les roadmaps et index actifs enregistrent l’état d’intégration courant.

Dernière preuve intégrée : M17, **410/410 PASS**, Architecture **167/167 PASS**, packaging Windows + smokes PASS, merge `02bdb38669efc85af17343d15e689743362d2e12`.

## Documentation active

Pour le contenu maintenu comme documentation courante :

- [utilisateur](../user/README.md) ;
- [développeur](../developer/README.md) ;
- [références machine](../reference/README.md) ;
- [roadmap courante](ROADMAP.md) ;
- [politique documentaire](DOCUMENTATION_STATUS.md).

Le dossier [`../product/`](../product/) conserve la baseline produit et les documents de cadrage C0 ; leur statut exact est expliqué par `DOCUMENTATION_STATUS.md`.
