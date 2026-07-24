# Gouvernance et preuves

Les documents de ce groupe servent à piloter et auditer le projet. Ils ne sont pas le parcours principal pour utiliser ou développer MORPHEUS au quotidien.

## Roadmap

- [`../ROADMAP.md`](../ROADMAP.md) — état global et gates validés ;
- [`../roadmap/`](../roadmap/) — plans d’exécution détaillés par jalon.

La roadmap opérationnelle d’un jalon reste la source de vérité pendant son exécution.

## Architecture Decision Records

- [`../adr/README.md`](../adr/README.md) — index des ADR ;
- `ADR-0077` à `ADR-0080` couvrent le contrat d’orchestration M14.

Une ADR dépendante d’une hypothèse technique ne doit être acceptée qu’après preuve reproductible.

## Validations

Les fichiers `../VALIDATION_M*.md` enregistrent :

- le SHA réellement testé ;
- les commandes exécutées ;
- le nombre exact de tests ;
- les preuves de packaging ;
- les décisions de sortie du jalon.

Dernière preuve intégrée : M14, **357/357 PASS**, Architecture **160/160 PASS**, packaging Windows PASS et preuve JARVIS cross-repo verte.

## Documentation active

Pour le contenu maintenu comme documentation courante :

- [utilisateur](../user/README.md) ;
- [développeur](../developer/README.md) ;
- [références](../reference/README.md).
