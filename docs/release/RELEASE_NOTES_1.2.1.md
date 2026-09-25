# MORPHEUS 1.2.1 — Notes de version

Statut : **EN PRÉPARATION — NON PUBLIÉE**

`1.2.1` est la baseline corrective de développement. Elle ne devient une release publiée qu'après création du tag et
qualification de ses artefacts (suivi #185, critère de clôture dans
[`../validation/RELEASE_QUALIFICATION.md`](../validation/RELEASE_QUALIFICATION.md) ; version courante dans
[`../developer/PRODUCT_VERSION.md`](../developer/PRODUCT_VERSION.md)).
Cette page recense, au fil des corrections, les changements qu'un utilisateur de 1.2.0 doit connaître avant de migrer.
Elle ne revendique aucune publication.

## Ruptures

### Structured Markdown : un bloc `morpheus task` déclare son achèvement

Jusqu'à 1.2.0, un bloc `task` sans champ `completed` était publié comme **non achevé** : l'absence valait `false`, et
ce `false` était stocké puis servi par la CLI, le HTTP, le MCP et le contexte augmenté (`[pending]`) comme une
observation.

À partir de 1.2.1, `completed` est **obligatoire**, comme `key`, `change` et `title`, et vaut `true` ou `false`. Un bloc
qui l'omet est refusé à la lecture :

```text
Structured Markdown normalization failed: missing 'completed' in task block at line <N>
```

Le diagnostic est bloquant (`INVALID_SOURCE`, sévérité `ERROR`) et le provider Structured Markdown ne contribue alors
aucun contenu : dans un workspace où il est seul, la synchronisation échoue et n'active aucun nouveau snapshot ; en
composition, c'est le comportement de tout échec de lecture de ce provider.

**Migration.** Ajouter `completed=false` (ou `completed=true`) à chaque bloc `task` de `morpheus/specification.md` qui ne
le déclare pas. Aucune migration de base n'est nécessaire : les tâches déjà publiées ne changent pas tant que le projet
n'est pas resynchronisé.

Décision : [ADR-0108, amendement du 24 septembre 2026 (PRV-4)](../adr/0108-a-response-says-what-it-could-not-observe.md).
Format du bloc : [`../user/QUICKSTART.md`](../user/QUICKSTART.md).

### Qualité d'un change : l'absence d'observation n'est plus un fait

Jusqu'à 1.2.0, un change dont **aucune** contrainte n'avait été ingérée produisait le fait affirmatif
`criticalConstraintsKnown = TRUE` (`allMatch` est vrai d'un flux vide) et passait donc `PROPOSED → SPECIFIED`, alors qu'un change portant
une contrainte d'applicabilité `UNKNOWN` était bloqué. Inversement, un change dont les critères d'acceptation ne portent que
`requirementId` (sans `changeId`) produisait `acceptanceCriteriaDefined = FALSE` et se voyait refuser la même transition avec
`MISSING_ACCEPTANCE_CRITERIA`, alors que ces critères existent.

À partir de 1.2.1 :

- un change sans contrainte a `criticalConstraintsKnown = UNAVAILABLE` : l'évaluation de `PROPOSED → SPECIFIED` ne se conclut pas et
  rend le diagnostic `LIFECYCLE_REQUIRED_FACT_UNAVAILABLE` au lieu d'autoriser la transition ;
- les critères d'acceptation rattachés aux **exigences** du change comptent comme ceux rattachés au change ;
- `acceptanceCriteriaDefined` est `FALSE` seulement quand les exigences du change sont connues et qu'aucun critère n'existe ; sans lien
  vers une exigence courante, il est `UNAVAILABLE`.

**Migration.** Un change qui doit pouvoir passer `SPECIFIED` sans contrainte substantielle doit l'exprimer : ingérer ses contraintes, ou
déclarer explicitement une contrainte `NOT_APPLICABLE` (applicabilité et sévérité connues).

Décision : [ADR-0108, amendement du 25 septembre 2026 (QLT-1, QLT-2)](../adr/0108-a-response-says-what-it-could-not-observe.md).
