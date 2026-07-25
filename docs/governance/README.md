# Gouvernance et preuves

Les documents de ce groupe servent à piloter et auditer le projet. Ils ne sont pas le parcours principal pour utiliser ou développer MORPHEUS au quotidien.

## Pilotage

- [`ROADMAP.md`](ROADMAP.md) — état global courant, baseline C0→M14 et jalons post-M14 ;
- [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md) — autorité des documents actifs, ADR et preuves historiques ;
- [`../roadmap/README.md`](../roadmap/README.md) — index des plans d’exécution ;
- [`../roadmap/POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md) — roadmap détaillée D0 + M15→M20 ;
- [`../roadmap/D0_EXECUTION.md`](../roadmap/D0_EXECUTION.md) — plan opérationnel de la réconciliation documentaire ;
- [`PLAN.md`](PLAN.md) — plan de cadrage C0/M0 historique ;
- [`AUDIT_COHERENCE_C0.md`](AUDIT_COHERENCE_C0.md) — audit de cohérence C0.

La roadmap opérationnelle d’un jalon reste la source de vérité pendant son exécution. Pour l’état d’intégration courant des jalons déjà livrés, `ROADMAP.md` prévaut sur une instruction de merge conservée dans une preuve historique.

Baseline livrée : **C0 à M14 validés et intégrés**. D0 est la porte documentaire avant M15.

## Architecture Decision Records

- [`../adr/README.md`](../adr/README.md) — index des ADR ;
- `ADR-0077` à `ADR-0080` couvrent le contrat d’orchestration M14.

Une ADR dépendante d’une hypothèse technique ne doit être acceptée qu’après preuve reproductible. Une ADR acceptée reste normative pour la décision qu’elle porte jusqu’à remplacement ou amendement explicite.

## Validations

Les preuves de gates sont regroupées dans [`../validation/`](../validation/) :

```text
VALIDATION_C0.md
VALIDATION_M0.md
...
VALIDATION_M14.md
```

Elles enregistrent notamment :

- le SHA réellement testé ;
- les commandes exécutées ;
- le nombre exact de tests ;
- les preuves de packaging ;
- les décisions de sortie du jalon.

Ces fichiers sont des **preuves historiques**. D0 ne réécrit pas leurs compteurs, SHA, dates ou décisions de gate.

Dernière preuve intégrée avant D0 : M14, **357/357 PASS**, Architecture **160/160 PASS**, packaging Windows PASS et preuve JARVIS cross-repo verte.

## Documentation active

Pour le contenu maintenu comme documentation courante :

- [utilisateur](../user/README.md) ;
- [développeur](../developer/README.md) ;
- [références machine](../reference/README.md) ;
- [roadmap courante](ROADMAP.md) ;
- [politique documentaire](DOCUMENTATION_STATUS.md).

Le dossier [`../product/`](../product/) conserve la baseline produit et les documents de cadrage C0 ; leur statut exact est expliqué par `DOCUMENTATION_STATUS.md`.
